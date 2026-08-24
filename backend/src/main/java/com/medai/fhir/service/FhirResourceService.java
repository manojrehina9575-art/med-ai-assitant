package com.medai.fhir.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.analysis.dto.AnalysisResultDto;
import com.medai.analysis.dto.BloodReportResultDto;
import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.enums.AnalysisType;
import com.medai.analysis.repository.AnalysisRequestRepository;
import com.medai.common.exception.ResourceNotFoundException;
import com.medai.fhir.mapper.*;
import com.medai.patient.repository.PatientRepository;
import com.medai.report.service.ReportSignOffService;
import com.medai.upload.repository.MedicalFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds FHIR resources from the internal model.
 *
 * <p>Read-only, and tenant-scoped through the same repositories everything else uses — the FHIR
 * facade gets no privileged path to the data. That matters: an interop surface is exactly where a
 * second, laxer access path tends to appear by accident.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FhirResourceService {

    private final PatientRepository patientRepository;
    private final AnalysisRequestRepository analysisRepository;
    private final MedicalFileRepository medicalFileRepository;

    private final PatientResourceMapper patientMapper;
    private final ObservationResourceMapper observationMapper;
    private final DiagnosticReportResourceMapper diagnosticReportMapper;
    private final ImagingStudyResourceMapper imagingStudyMapper;

    private final ReportSignOffService signOffService;
    private final ObjectMapper objectMapper;

    // ── Patient ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Patient readPatient(UUID tenantId, UUID patientId) {
        return patientRepository.findByIdAndTenantId(patientId, tenantId)
                .map(patientMapper::toFhir)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", patientId.toString()));
    }

    @Transactional(readOnly = true)
    public Bundle searchPatients(UUID tenantId, String identifier, int count) {
        List<com.medai.patient.entity.Patient> matches = identifier == null || identifier.isBlank()
                ? patientRepository.findByTenantId(tenantId, PageRequest.of(0, count)).getContent()
                : patientRepository.findByTenantIdAndMedicalRecordNumber(tenantId, identifier)
                        .map(List::of).orElse(List.of());

        return searchSet(matches.stream().map(patientMapper::toFhir).map(Resource.class::cast).toList());
    }

    @Transactional(readOnly = true)
    public Bundle searchAllergies(UUID tenantId, UUID patientId) {
        com.medai.patient.entity.Patient patient = patientRepository
                .findByIdAndTenantId(patientId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", patientId.toString()));

        List<String> allergies = patient.getAllergies() == null ? List.of() : patient.getAllergies();

        List<Resource> resources = new ArrayList<>();
        for (int i = 0; i < allergies.size(); i++) {
            resources.add(patientMapper.toAllergyIntolerance(patient, allergies.get(i), i));
        }
        return searchSet(resources);
    }

    // ── DiagnosticReport ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DiagnosticReport readDiagnosticReport(UUID tenantId, UUID analysisId) {
        AnalysisRequest analysis = analysisRepository.findByIdAndTenantId(analysisId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("DiagnosticReport", "id", analysisId.toString()));
        return buildReport(analysis);
    }

    @Transactional(readOnly = true)
    public Bundle searchDiagnosticReports(UUID tenantId, UUID patientId, int count) {
        List<AnalysisRequest> analyses = patientId != null
                ? analysisRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(
                        tenantId, patientId, PageRequest.of(0, count)).getContent()
                : analysisRepository.findByTenantIdOrderByCreatedAtDesc(
                        tenantId, PageRequest.of(0, count)).getContent();

        return searchSet(analyses.stream()
                .map(this::buildReport)
                .map(Resource.class::cast)
                .toList());
    }

    private DiagnosticReport buildReport(AnalysisRequest analysis) {
        AnalysisResultDto result = parseImagingResult(analysis);
        List<Reference> observations = List.of();

        if (analysis.getAnalysisType() != AnalysisType.IMAGE_ANALYSIS) {
            BloodReportResultDto blood = parseBloodResult(analysis);
            if (blood != null && blood.getParameters() != null) {
                observations = observationMapper.referencesFor(analysis, blood.getParameters().size());
            }
        }

        // A signed review promotes the report from preliminary to final. Looking it up here rather
        // than in the mapper keeps the mapper a pure function of what it is handed.
        return diagnosticReportMapper.toFhir(analysis, result, observations,
                signOffService.signedReviewFor(analysis.getTenantId(), analysis.getId()).orElse(null));
    }

    // ── Observation ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Bundle searchObservations(UUID tenantId, UUID patientId, int count) {
        List<AnalysisRequest> analyses = analysisRepository
                .findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId, PageRequest.of(0, count))
                .getContent();

        List<Resource> observations = new ArrayList<>();
        for (AnalysisRequest analysis : analyses) {
            BloodReportResultDto blood = parseBloodResult(analysis);
            if (blood == null || blood.getParameters() == null) {
                continue;
            }
            for (int i = 0; i < blood.getParameters().size(); i++) {
                observations.add(observationMapper.toFhir(analysis, blood.getParameters().get(i), i));
            }
        }
        return searchSet(observations);
    }

    // ── ImagingStudy ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Bundle searchImagingStudies(UUID tenantId, UUID patientId, int count) {
        List<Resource> studies = medicalFileRepository
                .findByTenantIdAndPatientId(tenantId, patientId, PageRequest.of(0, count))
                .getContent().stream()
                .filter(imagingStudyMapper::isImaging)
                .map(imagingStudyMapper::toFhir)
                .map(Resource.class::cast)
                .toList();

        return searchSet(studies);
    }

    // ── Parsing ──────────────────────────────────────────────────────────────

    /**
     * Analysis results are stored as the model's raw JSON string, so a schema drift or a truncated
     * response surfaces here. A parse failure yields no resource rather than a half-populated one:
     * an interop consumer cannot tell the difference between "no findings" and "findings we could
     * not read", so it must not be shown something that looks like the former.
     */
    private AnalysisResultDto parseImagingResult(AnalysisRequest analysis) {
        if (analysis.getResult() == null || analysis.getResult().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(analysis.getResult(), AnalysisResultDto.class);
        } catch (Exception e) {
            log.debug("Analysis {} result is not an imaging result: {}", analysis.getId(), e.getMessage());
            return null;
        }
    }

    private BloodReportResultDto parseBloodResult(AnalysisRequest analysis) {
        if (analysis.getResult() == null || analysis.getResult().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(analysis.getResult(), BloodReportResultDto.class);
        } catch (Exception e) {
            log.debug("Analysis {} result is not a blood report: {}", analysis.getId(), e.getMessage());
            return null;
        }
    }

    private Bundle searchSet(List<Resource> resources) {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setTotal(resources.size());
        resources.forEach(resource -> bundle.addEntry()
                .setResource(resource)
                .setFullUrl(resource.fhirType() + "/" + resource.getIdElement().getIdPart()));
        return bundle;
    }
}
