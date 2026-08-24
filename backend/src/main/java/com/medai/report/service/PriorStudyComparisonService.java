package com.medai.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.analysis.dto.BloodReportResultDto;
import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.enums.AnalysisStatus;
import com.medai.analysis.repository.AnalysisRequestRepository;
import com.medai.common.exception.ResourceNotFoundException;
import com.medai.patient.entity.Patient;
import com.medai.patient.repository.PatientRepository;
import com.medai.report.dto.ReportDtos.*;
import com.medai.terminology.service.LoincMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Trends a patient's studies against their priors.
 *
 * <p>"Compare to prior" is the most-requested capability in radiology tool surveys, and the data
 * to do it has been sitting here all along: analyses are already stored per patient with
 * timestamps. What was missing is the alignment — two reports side by side is not a comparison,
 * because the reader has to do the matching themselves.
 *
 * <p>Analytes are aligned by LOINC rather than by label, which is the whole reason the terminology
 * work came first. A lab that writes "Hb" one month and "Haemoglobin" the next produces two rows
 * in a name-matched view and one correct trend line here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PriorStudyComparisonService {

    /** Beyond this a "trend" is a research question, not something read at a workstation. */
    private static final int MAX_STUDIES = 10;

    /**
     * Below this, a change is measurement noise rather than a trend. Reported as STABLE so a
     * clinician's attention goes to the analytes that actually moved.
     */
    private static final double MATERIAL_CHANGE_FRACTION = 0.10;

    private final AnalysisRequestRepository analysisRepository;
    private final PatientRepository patientRepository;
    private final LoincMapper loincMapper;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ComparisonView compare(UUID tenantId, UUID patientId, int studyCount) {
        Patient patient = patientRepository.findByIdAndTenantId(patientId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", patientId.toString()));

        int limit = Math.min(Math.max(studyCount, 2), MAX_STUDIES);

        // Newest first from the repository, then reversed: a trend reads left to right in time.
        List<AnalysisRequest> studies = new ArrayList<>(analysisRepository
                .findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId, PageRequest.of(0, limit))
                .getContent().stream()
                .filter(a -> a.getStatus() == AnalysisStatus.COMPLETED)
                .filter(a -> !Boolean.TRUE.equals(a.getAbstained()))
                .toList());
        Collections.reverse(studies);

        List<StudyRef> studyRefs = studies.stream()
                .map(a -> new StudyRef(a.getId(), a.getAnalysisType().name(),
                        a.getCreatedAt(), a.getStatus().name()))
                .toList();

        return new ComparisonView(patientId, patient.getFullName(), studyRefs, trends(studies));
    }

    /**
     * Builds one row per analyte across every study.
     *
     * <p>Keyed by LOINC where the analyte resolves, and by normalised name where it does not — so
     * an unmapped analyte still trends against itself instead of being dropped, and never merges
     * with a different unmapped one.
     */
    private List<AnalyteTrend> trends(List<AnalysisRequest> studies) {
        Map<String, Accumulator> byAnalyte = new LinkedHashMap<>();

        for (AnalysisRequest study : studies) {
            BloodReportResultDto blood = parse(study);
            if (blood == null || blood.getParameters() == null) {
                continue;
            }

            for (BloodReportResultDto.Parameter parameter : blood.getParameters()) {
                if (parameter.getName() == null) {
                    continue;
                }

                Optional<LoincMapper.Loinc> loinc = loincMapper.resolve(parameter.getName());
                String key = loinc.map(LoincMapper.Loinc::code)
                        .orElseGet(() -> "text:" + parameter.getName().trim().toLowerCase(Locale.ROOT));

                Accumulator accumulator = byAnalyte.computeIfAbsent(key, k -> new Accumulator(
                        loinc.map(LoincMapper.Loinc::display).orElse(parameter.getName()),
                        loinc.map(LoincMapper.Loinc::code).orElse(null),
                        parameter.getUnit(),
                        parameter.getReferenceRange()));

                accumulator.points.add(new AnalytePoint(
                        study.getId(), study.getCreatedAt(), parameter.getValue(), parameter.getFlag()));
            }
        }

        return byAnalyte.values().stream().map(this::toTrend).toList();
    }

    private AnalyteTrend toTrend(Accumulator accumulator) {
        List<AnalytePoint> points = accumulator.points;

        Double delta = null;
        String direction = "NEW";

        List<AnalytePoint> withValues = points.stream().filter(p -> p.value() != null).toList();
        if (withValues.size() >= 2) {
            double latest = withValues.get(withValues.size() - 1).value();
            double previous = withValues.get(withValues.size() - 2).value();
            delta = latest - previous;

            // Proportional, not absolute: 0.3 is noise on a sodium and a doubling on a creatinine.
            double magnitude = previous == 0 ? Math.abs(delta) : Math.abs(delta / previous);
            if (magnitude < MATERIAL_CHANGE_FRACTION) {
                direction = "STABLE";
            } else {
                direction = delta > 0 ? "RISING" : "FALLING";
            }
        }

        return new AnalyteTrend(accumulator.display, accumulator.loincCode, accumulator.unit,
                accumulator.referenceRange, points, delta, direction);
    }

    private BloodReportResultDto parse(AnalysisRequest analysis) {
        if (analysis.getResult() == null || analysis.getResult().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(analysis.getResult(), BloodReportResultDto.class);
        } catch (Exception e) {
            // An imaging result, or a shape we cannot read. Either way there is nothing to trend.
            return null;
        }
    }

    private static final class Accumulator {
        private final String display;
        private final String loincCode;
        private final String unit;
        private final String referenceRange;
        private final List<AnalytePoint> points = new ArrayList<>();

        Accumulator(String display, String loincCode, String unit, String referenceRange) {
            this.display = display;
            this.loincCode = loincCode;
            this.unit = unit;
            this.referenceRange = referenceRange;
        }
    }
}
