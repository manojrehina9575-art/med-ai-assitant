package com.medai.report.service;

import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.repository.AnalysisRequestRepository;
import com.medai.auth.security.UserPrincipal;
import com.medai.common.exception.BadRequestException;
import com.medai.common.exception.ResourceNotFoundException;
import com.medai.notification.service.NotificationService;
import com.medai.patient.entity.Patient;
import com.medai.patient.repository.PatientRepository;
import com.medai.report.dto.ReportDtos.*;
import com.medai.report.entity.CriticalResultEscalation;
import com.medai.report.repository.CriticalResultEscalationRepository;
import com.medai.tenant.TenantContext;
import com.medai.tenant.TenantSession;
import com.medai.user.entity.User;
import com.medai.user.enums.UserRole;
import com.medai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Critical findings, and the duty to make sure someone saw them.
 *
 * <p>The guardrail already detected acute red flags and then rendered a banner. In most
 * jurisdictions a critical finding carries a documented notification-and-acknowledgement duty:
 * someone must be told, and the fact that they were told must be recorded. A banner on a screen
 * nobody was looking at discharges neither half of that.
 *
 * <p>Escalation widens rather than repeats. Level 0 notifies the clinician who requested the
 * study; if nobody acknowledges within the deadline it widens to every doctor in the hospital, and
 * then to administrators. Re-notifying the same person who has already not responded is how a
 * critical result goes unread for six hours with a full audit trail proving it was sent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CriticalResultService {

    private final CriticalResultEscalationRepository escalationRepository;
    private final AnalysisRequestRepository analysisRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final TenantSession tenantSession;

    /** How long a level has to be acknowledged before it widens. */
    @Value("${app.critical-results.acknowledge-within-minutes:15}")
    private int acknowledgeWithinMinutes;

    /** Beyond this, widening further adds nobody — everyone who can act has been told. */
    private static final short MAX_LEVEL = 2;

    /**
     * Raises an escalation for an urgent or critical finding.
     *
     * <p>Idempotent per analysis: re-running an analysis must not re-page the ward.
     */
    @Transactional
    public Optional<CriticalResultEscalation> raiseIfCritical(UUID tenantId, UUID analysisId,
                                                              String urgency, String findingSummary) {
        if (urgency == null || !(urgency.equals("CRITICAL") || urgency.equals("URGENT"))) {
            return Optional.empty();
        }

        Optional<CriticalResultEscalation> existing =
                escalationRepository.findByTenantIdAndAnalysisId(tenantId, analysisId);
        if (existing.isPresent()) {
            return existing;
        }

        AnalysisRequest analysis = analysisRepository.findByIdAndTenantId(analysisId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis", "id", analysisId.toString()));

        CriticalResultEscalation escalation = escalationRepository.save(CriticalResultEscalation.builder()
                .tenantId(tenantId)
                .analysisId(analysisId)
                .patientId(analysis.getPatientId())
                .urgency(urgency)
                .findingSummary(truncate(findingSummary))
                .status("OPEN")
                .escalationLevel((short) 0)
                .lastNotifiedAt(Instant.now())
                .build());

        notifyLevel(tenantId, escalation, analysis.getRequestedBy());

        log.warn("CRITICAL RESULT raised: escalation {} for analysis {} (patient {}, urgency {})",
                escalation.getId(), analysisId, analysis.getPatientId(), urgency);

        return Optional.of(escalation);
    }

    @Transactional(readOnly = true)
    public List<EscalationView> open() {
        UUID tenantId = TenantContext.requireTenantId();
        return escalationRepository.findByTenantIdAndStatusOrderByCreatedAtAsc(tenantId, "OPEN").stream()
                .map(e -> toView(tenantId, e))
                .toList();
    }

    @Transactional(readOnly = true)
    public long countOpen() {
        UUID tenantId = TenantContext.requireTenantId();
        return escalationRepository.findByTenantIdAndStatusOrderByCreatedAtAsc(tenantId, "OPEN").size();
    }

    /**
     * Records that a clinician saw the finding and what they did.
     *
     * <p>{@code actionTaken} is required. An acknowledgement with no action recorded is a click,
     * and a click is not what the duty asks for — it asks what happened to the patient.
     */
    @Transactional
    public EscalationView acknowledge(UUID escalationId, AcknowledgeRequest request, UserPrincipal principal) {
        CriticalResultEscalation escalation = escalationRepository
                .findByIdAndTenantId(escalationId, principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CriticalResultEscalation", "id", escalationId.toString()));

        if (!"OPEN".equals(escalation.getStatus())) {
            throw new BadRequestException("This escalation is already " + escalation.getStatus() + ".");
        }
        if (request.actionTaken() == null || request.actionTaken().isBlank()) {
            throw new BadRequestException(
                    "Record what was done. An acknowledgement without an action is a click, and the "
                    + "notification duty asks what happened to the patient.");
        }

        escalation.setStatus("ACKNOWLEDGED");
        escalation.setAcknowledgedBy(principal.userId());
        escalation.setAcknowledgedAt(Instant.now());
        escalation.setActionTaken(request.actionTaken());

        log.info("Escalation {} acknowledged by {} after {} minute(s) at level {}",
                escalationId, principal.userId(),
                Duration.between(escalation.getCreatedAt(), Instant.now()).toMinutes(),
                escalation.getEscalationLevel());

        return toView(principal.tenantId(), escalationRepository.save(escalation));
    }

    /**
     * Widens any escalation that has gone unacknowledged past its deadline.
     *
     * <p>Runs every minute. A fifteen-minute duty checked every five would be a twenty-minute duty.
     */
    @Scheduled(fixedDelayString = "${app.critical-results.sweep-interval-ms:60000}")
    public void escalateUnacknowledged() {
        Instant deadline = Instant.now().minus(Duration.ofMinutes(acknowledgeWithinMinutes));

        List<CriticalResultEscalation> overdue = findOverdue(deadline);
        if (overdue.isEmpty()) {
            return;
        }

        log.warn("{} critical result(s) unacknowledged past {} minutes", overdue.size(), acknowledgeWithinMinutes);

        for (CriticalResultEscalation escalation : overdue) {
            try {
                TenantContext.setCurrentTenantId(escalation.getTenantId());
                widen(escalation);
            } catch (Exception e) {
                log.error("Could not widen escalation {}: {}", escalation.getId(), e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    /** Cross-tenant scan, which row-level security forbids by default. */
    @Transactional(readOnly = true)
    public List<CriticalResultEscalation> findOverdue(Instant deadline) {
        tenantSession.beginMaintenance();
        return escalationRepository.findByStatusAndLastNotifiedAtBefore("OPEN", deadline);
    }

    @Transactional
    public void widen(CriticalResultEscalation escalation) {
        if (escalation.getEscalationLevel() >= MAX_LEVEL) {
            // Everyone who can act has been told. Continuing to page adds noise, not safety —
            // the record stays OPEN, which is the thing that should be visible on a dashboard.
            escalation.setLastNotifiedAt(Instant.now());
            escalationRepository.save(escalation);
            log.error("Escalation {} STILL UNACKNOWLEDGED at maximum level after {} minutes",
                    escalation.getId(),
                    Duration.between(escalation.getCreatedAt(), Instant.now()).toMinutes());
            return;
        }

        escalation.setEscalationLevel((short) (escalation.getEscalationLevel() + 1));
        escalation.setLastNotifiedAt(Instant.now());
        escalationRepository.save(escalation);

        notifyLevel(escalation.getTenantId(), escalation, null);

        log.warn("Escalation {} widened to level {}", escalation.getId(), escalation.getEscalationLevel());
    }

    /**
     * Notifies the audience for the current level.
     *
     * <p>Level 0 is the requesting clinician, 1 widens to every active doctor, 2 to administrators.
     * Widening rather than repeating is the point: re-notifying the person who has already not
     * responded is how a critical result goes unread with a full audit trail proving it was sent.
     */
    private void notifyLevel(UUID tenantId, CriticalResultEscalation escalation, UUID requestedBy) {
        String patientName = patientRepository.findByIdAndTenantId(escalation.getPatientId(), tenantId)
                .map(Patient::getFullName).orElse("patient");

        String title = escalation.getUrgency().equals("CRITICAL")
                ? "CRITICAL RESULT — immediate review required"
                : "Urgent result — review required";

        String message = String.format("%s: %s. Acknowledge within %d minutes.",
                patientName, escalation.getFindingSummary(), acknowledgeWithinMinutes);

        List<UUID> recipients = switch (escalation.getEscalationLevel()) {
            case 0 -> requestedBy != null ? List.of(requestedBy) : activeUserIds(tenantId, UserRole.DOCTOR);
            case 1 -> activeUserIds(tenantId, UserRole.DOCTOR);
            default -> activeUserIds(tenantId, UserRole.HOSPITAL_ADMIN);
        };

        for (UUID recipient : recipients) {
            notificationService.createNotification(recipient, "CRITICAL_RESULT", title, message,
                    escalation.getUrgency().equals("CRITICAL") ? "CRITICAL" : "WARNING",
                    "CriticalResultEscalation", escalation.getId());
        }

        if (recipients.isEmpty()) {
            // Worth an error: a critical finding with nobody to tell is a configuration failure
            // that will only be discovered when it matters.
            log.error("Escalation {} at level {} has no recipients — nobody was notified.",
                    escalation.getId(), escalation.getEscalationLevel());
        }
    }

    private List<UUID> activeUserIds(UUID tenantId, UserRole role) {
        return userRepository.findByTenantIdAndRoleAndIsActiveTrue(tenantId, role).stream()
                .map(User::getId)
                .toList();
    }

    private String truncate(String summary) {
        if (summary == null || summary.isBlank()) {
            return "Critical finding reported by automated analysis.";
        }
        return summary.length() > 1000 ? summary.substring(0, 1000) + "…" : summary;
    }

    private EscalationView toView(UUID tenantId, CriticalResultEscalation e) {
        String patientName = patientRepository.findByIdAndTenantId(e.getPatientId(), tenantId)
                .map(Patient::getFullName).orElse(null);

        return new EscalationView(e.getId(), e.getAnalysisId(), e.getPatientId(), patientName,
                e.getUrgency(), e.getFindingSummary(), e.getStatus(), e.getEscalationLevel(),
                e.getLastNotifiedAt(), e.getAcknowledgedBy(), e.getAcknowledgedAt(),
                e.getActionTaken(), e.getCreatedAt());
    }
}
