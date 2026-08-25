package com.medai.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.BaseIntegrationTest;
import com.medai.analysis.dto.AnalysisResultDto;
import com.medai.analysis.dto.BloodReportResultDto;
import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.enums.AnalysisStatus;
import com.medai.analysis.enums.AnalysisType;
import com.medai.analysis.repository.AnalysisRequestRepository;
import com.medai.fhir.service.FhirResourceService;
import com.medai.patient.entity.Patient;
import com.medai.patient.enums.Gender;
import com.medai.patient.repository.PatientRepository;
import com.medai.tenant.TenantContext;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The FHIR facade exists to be consumed by someone else's system, so these check conformance and
 * clinical honesty rather than just "a resource came back".
 */
class FhirFacadeTest extends BaseIntegrationTest {

    @Autowired private FhirResourceService fhirService;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AnalysisRequestRepository analysisRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    private static final FhirContext FHIR = FhirContext.forR4();
    private final IParser parser = FHIR.newJsonParser();

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO tenants (id, name, subdomain, contact_email)
                VALUES (?, 'FHIR Hospital', ?, 'f@example.test')
                """, tenantId, "fhir-" + tenantId.toString().substring(0, 8));
        TenantContext.setCurrentTenantId(tenantId);
        return tenantId;
    }

    private Patient seedPatient(UUID tenantId) {
        Patient patient = Patient.builder()
                .medicalRecordNumber("MRN-" + UUID.randomUUID().toString().substring(0, 6))
                .firstName("Asha").lastName("Menon")
                .dateOfBirth(LocalDate.of(1979, 4, 12))
                .gender(Gender.FEMALE)
                .phone("+919876543210")
                .allergies(List.of("Penicillin", "Sulfa"))
                .isActive(true)
                .build();
        patient.setTenantId(tenantId);
        return patientRepository.save(patient);
    }

    @Test
    @DisplayName("Patient maps to a conformant FHIR Patient with an MRN identifier")
    void patientMapsConformantly() throws Exception {
        UUID tenantId = seedTenant();
        Patient source = seedPatient(tenantId);

        org.hl7.fhir.r4.model.Patient fhir = fhirService.readPatient(tenantId, source.getId());

        // Round-tripping through the parser is the real conformance check: HAPI rejects a resource
        // it cannot serialise and re-read.
        String json = parser.encodeResourceToString(fhir);
        org.hl7.fhir.r4.model.Patient reparsed =
                parser.parseResource(org.hl7.fhir.r4.model.Patient.class, json);

        assertThat(reparsed.getIdentifierFirstRep().getSystem()).isEqualTo(FhirConstants.MRN_SYSTEM);
        assertThat(reparsed.getIdentifierFirstRep().getValue()).isEqualTo(source.getMedicalRecordNumber());
        assertThat(reparsed.getNameFirstRep().getFamily()).isEqualTo("Menon");
        assertThat(reparsed.getGender()).isEqualTo(Enumerations.AdministrativeGender.FEMALE);
        assertThat(reparsed.getBirthDate()).isNotNull();
    }

    /**
     * Allergies are free text on the patient row. Coding them by string match would turn a note
     * into a false clinical assertion, so they go out uncoded and unconfirmed.
     */
    @Test
    @DisplayName("Allergies become AllergyIntolerance resources, uncoded and unconfirmed")
    void allergiesBecomeSeparateResources() {
        UUID tenantId = seedTenant();
        Patient source = seedPatient(tenantId);

        Bundle bundle = fhirService.searchAllergies(tenantId, source.getId());

        assertThat(bundle.getTotal()).isEqualTo(2);
        AllergyIntolerance first = (AllergyIntolerance) bundle.getEntryFirstRep().getResource();
        assertThat(first.getCode().getText()).isEqualTo("Penicillin");
        assertThat(first.getCode().getCoding()).isEmpty();
        assertThat(first.getVerificationStatus().getCodingFirstRep().getCode()).isEqualTo("unconfirmed");
    }

    @Test
    @DisplayName("Lab parameters become LOINC-coded Observations")
    void observationsCarryLoinc() throws Exception {
        UUID tenantId = seedTenant();
        Patient patient = seedPatient(tenantId);

        BloodReportResultDto blood = BloodReportResultDto.builder()
                .testName("Complete Blood Count")
                .parameters(List.of(
                        new BloodReportResultDto.Parameter("Hemoglobin", 9.1, "g/dL", "12.0-15.0", "LOW"),
                        new BloodReportResultDto.Parameter("Zyxocount", 4.0, "u/L", "1-5", "NORMAL")))
                .build();

        seedAnalysis(tenantId, patient.getId(), AnalysisType.BLOOD_REPORT,
                objectMapper.writeValueAsString(blood));

        Bundle bundle = fhirService.searchObservations(tenantId, patient.getId(), 50);
        assertThat(bundle.getTotal()).isEqualTo(2);

        Observation haemoglobin = (Observation) bundle.getEntry().get(0).getResource();
        assertThat(haemoglobin.getCode().getCodingFirstRep().getSystem()).isEqualTo(FhirConstants.LOINC_SYSTEM);
        assertThat(haemoglobin.getCode().getCodingFirstRep().getCode()).isEqualTo("718-7");
        assertThat(haemoglobin.getInterpretationFirstRep().getCodingFirstRep().getCode()).isEqualTo("L");
        assertThat(haemoglobin.getReferenceRangeFirstRep().getLow().getValue().doubleValue()).isEqualTo(12.0);
        assertThat(haemoglobin.getReferenceRangeFirstRep().getHigh().getValue().doubleValue()).isEqualTo(15.0);

        // An unmapped analyte is uncoded rather than guessed at.
        Observation unknown = (Observation) bundle.getEntry().get(1).getResource();
        assertThat(unknown.getCode().getCoding()).isEmpty();
        assertThat(unknown.getCode().getText()).isEqualTo("Zyxocount");
    }

    /**
     * The regulatory position depends on this. Marking AI output {@code final} would assert a
     * clinical sign-off that has not happened.
     */
    @Test
    @DisplayName("A DiagnosticReport is never final, and says it is AI-generated")
    void reportIsNeverFinal() throws Exception {
        UUID tenantId = seedTenant();
        Patient patient = seedPatient(tenantId);

        AnalysisResultDto result = AnalysisResultDto.builder()
                .impression("Right lower lobe consolidation.")
                .icd10Codes(List.of("J18.9"))
                .findings(List.of(new AnalysisResultDto.Finding("RLL", "Consolidation", "MODERATE", 0.88)))
                .build();

        AnalysisRequest analysis = seedAnalysis(tenantId, patient.getId(),
                AnalysisType.IMAGE_ANALYSIS, objectMapper.writeValueAsString(result));

        DiagnosticReport report = fhirService.readDiagnosticReport(tenantId, analysis.getId());

        assertThat(report.getStatus()).isEqualTo(DiagnosticReport.DiagnosticReportStatus.PARTIAL);
        assertThat(report.getExtensionByUrl(FhirConstants.BASE_NAMESPACE + "/ai-generated")).isNotNull();
        assertThat(report.getConclusion()).contains("consolidation");
    }

    @Test
    @DisplayName("A confirmed ICD-10 code is coded; an unconfirmed one is text only")
    void conclusionCodesAreValidated() throws Exception {
        UUID tenantId = seedTenant();
        Patient patient = seedPatient(tenantId);

        AnalysisResultDto result = AnalysisResultDto.builder()
                .impression("Findings.")
                .icd10Codes(List.of("J18.9", "J18.7"))
                .build();

        AnalysisRequest analysis = seedAnalysis(tenantId, patient.getId(),
                AnalysisType.IMAGE_ANALYSIS, objectMapper.writeValueAsString(result));

        DiagnosticReport report = fhirService.readDiagnosticReport(tenantId, analysis.getId());
        List<CodeableConcept> codes = report.getConclusionCode();

        assertThat(codes).hasSize(2);
        assertThat(codes.get(0).getCodingFirstRep().getCode()).isEqualTo("J18.9");

        // Carried, so nothing the model said is hidden — but never as a coded assertion.
        assertThat(codes.get(1).getCoding()).isEmpty();
        assertThat(codes.get(1).getText()).contains("unverified");
    }

    /**
     * A blood report has a medical_files row like any other and is emphatically not an imaging
     * study. Emitting one with no modality and hoping the consumer notices is not good enough.
     */
    @Test
    @DisplayName("Only imaging files become ImagingStudy resources, with a real modality")
    void onlyImagingBecomesImagingStudy() {
        UUID tenantId = seedTenant();
        Patient patient = seedPatient(tenantId);
        UUID doctorId = seedUser(tenantId);

        seedMedicalFile(tenantId, patient.getId(), doctorId, "XRAY");
        seedMedicalFile(tenantId, patient.getId(), doctorId, "BLOOD_REPORT");

        Bundle bundle = fhirService.searchImagingStudies(tenantId, patient.getId(), 50);

        assertThat(bundle.getTotal()).isEqualTo(1);
        ImagingStudy study = (ImagingStudy) bundle.getEntryFirstRep().getResource();
        assertThat(study.getModalityFirstRep().getCode()).isEqualTo("CR");
    }

    @Test
    @DisplayName("The FHIR facade is tenant-scoped like everything else")
    void facadeIsTenantScoped() {
        UUID tenantA = seedTenant();
        Patient patientA = seedPatient(tenantA);

        UUID tenantB = seedTenant();

        Bundle visibleToB = fhirService.searchPatients(tenantB, patientA.getMedicalRecordNumber(), 50);
        assertThat(visibleToB.getTotal())
                .as("an interop surface must not become a second, laxer access path")
                .isZero();
    }

    /** analysis_requests has FKs to users and medical_files, so both have to exist first. */
    private UUID seedUser(UUID tenantId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (id, tenant_id, email, password_hash, first_name, last_name, role)
                VALUES (?, ?, ?, 'x', 'Test', 'Doctor', 'DOCTOR')
                """, id, tenantId, "doc-" + id + "@fhir.test");
        return id;
    }

    private UUID seedMedicalFile(UUID tenantId, UUID patientId, UUID uploadedBy, String fileType) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO medical_files
                    (id, tenant_id, patient_id, uploaded_by, file_name, original_file_name,
                     file_type, mime_type, file_size_bytes, storage_path)
                VALUES (?, ?, ?, ?, 'f.dcm', 'chest.dcm', ?, 'application/dicom', 1024, 'x/y/f.dcm')
                """, id, tenantId, patientId, uploadedBy, fileType);
        return id;
    }

    private AnalysisRequest seedAnalysis(UUID tenantId, UUID patientId, AnalysisType type, String result) {
        UUID doctorId = seedUser(tenantId);
        UUID fileId = seedMedicalFile(tenantId, patientId, doctorId,
                type == AnalysisType.BLOOD_REPORT ? "BLOOD_REPORT" : "XRAY");

        AnalysisRequest analysis = AnalysisRequest.builder()
                .patientId(patientId)
                .medicalFileId(fileId)
                .requestedBy(doctorId)
                .analysisType(type)
                .status(AnalysisStatus.COMPLETED)
                .result(result)
                .retryCount(0)
                .maxRetries(3)
                .build();
        analysis.setTenantId(tenantId);
        return analysisRepository.save(analysis);
    }
}
