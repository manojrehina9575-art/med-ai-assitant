package com.medai.fhir.controller;

import ca.uhn.fhir.parser.IParser;
import com.medai.auth.security.UserPrincipal;
import com.medai.common.exception.ResourceNotFoundException;
import com.medai.fhir.service.FhirResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * FHIR R4 read-only facade.
 *
 * <p>Serves {@code application/fhir+json}, the media type a FHIR client negotiates; returning
 * plain {@code application/json} is a common way to be almost-interoperable. Responses are
 * serialised by HAPI rather than by Jackson, because the R4 model's choice types
 * ({@code value[x]}) and primitive extensions do not survive bean serialisation.
 *
 * <p><strong>Read-only, and deliberately.</strong> Write support means reconciling an external
 * system's assertions against the clinical record, which is a data-governance decision rather than
 * a mapping exercise. Read gets the product into an EHR's field of view; write can follow once
 * there is a customer whose workflow needs it.
 *
 * <p>Same JWT and the same tenant scoping as every other endpoint. An interop surface is exactly
 * where a second, laxer access path tends to appear by accident, so there is not one here.
 *
 * <p><strong>India / ABDM.</strong> ABDM is FHIR R4, so this is the layer its HIP linkage builds
 * on. What is missing for ABDM specifically is profile conformance ({@code meta.profile} claiming
 * the NDHM profiles), the ABHA-number identifier on Patient, and the consent-artefact exchange —
 * none of which changes the mappers, all of which need HIP registration first.
 */
@RestController
@RequestMapping(value = "/fhir", produces = {"application/fhir+json", "application/json"})
@RequiredArgsConstructor
@Tag(name = "FHIR R4", description = "Read-only FHIR R4 facade over patients, reports and studies")
public class FhirController {

    /** FHIR's default page size. Capped so a search cannot be used to pull the whole record set. */
    private static final int DEFAULT_COUNT = 50;
    private static final int MAX_COUNT = 200;

    private final FhirResourceService fhirService;
    private final IParser fhirJsonParser;

    // ── Conformance ──────────────────────────────────────────────────────────

    /**
     * The CapabilityStatement. A FHIR client fetches this first to discover what is supported;
     * without it, the endpoint is not discoverable and most tooling will refuse to proceed.
     */
    @GetMapping("/metadata")
    @Operation(summary = "FHIR CapabilityStatement")
    public ResponseEntity<String> metadata() {
        CapabilityStatement statement = new CapabilityStatement();
        statement.setStatus(Enumerations.PublicationStatus.ACTIVE);
        statement.setDate(new Date());
        statement.setPublisher("Med-AI Assistant");
        statement.setKind(CapabilityStatement.CapabilityStatementKind.INSTANCE);
        statement.setFhirVersion(Enumerations.FHIRVersion._4_0_1);
        statement.setFormat(List.of(new CodeType("application/fhir+json")));

        statement.setSoftware(new CapabilityStatement.CapabilityStatementSoftwareComponent()
                .setName("Med-AI Assistant FHIR facade"));

        CapabilityStatement.CapabilityStatementRestComponent rest =
                statement.addRest().setMode(CapabilityStatement.RestfulCapabilityMode.SERVER);

        rest.setSecurity(new CapabilityStatement.CapabilityStatementRestSecurityComponent()
                .addService(new CodeableConcept().addCoding(new Coding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/restful-security-service")
                        .setCode("OAuth")
                        .setDisplay("OAuth2 Bearer token"))));

        resource(rest, "Patient", "identifier");
        resource(rest, "AllergyIntolerance", "patient");
        resource(rest, "DiagnosticReport", "patient");
        resource(rest, "Observation", "patient");
        resource(rest, "ImagingStudy", "patient");

        return fhirResponse(statement, HttpStatus.OK);
    }

    private void resource(CapabilityStatement.CapabilityStatementRestComponent rest,
                          String type, String searchParam) {
        rest.addResource()
                .setType(type)
                .addInteraction().setCode(CapabilityStatement.TypeRestfulInteraction.READ);
        rest.getResource().get(rest.getResource().size() - 1)
                .addInteraction().setCode(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE);
        rest.getResource().get(rest.getResource().size() - 1)
                .addSearchParam().setName(searchParam)
                .setType(Enumerations.SearchParamType.TOKEN);
    }

    // ── Patient ──────────────────────────────────────────────────────────────

    @GetMapping("/Patient/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Read a Patient by id")
    public ResponseEntity<String> readPatient(@PathVariable UUID id,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return fhirResponse(fhirService.readPatient(principal.tenantId(), id), HttpStatus.OK);
    }

    @GetMapping("/Patient")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Search Patients by MRN")
    public ResponseEntity<String> searchPatients(
            @RequestParam(required = false) String identifier,
            @RequestParam(name = "_count", required = false) Integer count,
            @AuthenticationPrincipal UserPrincipal principal) {
        return fhirResponse(
                fhirService.searchPatients(principal.tenantId(), identifier, pageSize(count)), HttpStatus.OK);
    }

    @GetMapping("/AllergyIntolerance")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Search a patient's documented allergies")
    public ResponseEntity<String> searchAllergies(@RequestParam UUID patient,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return fhirResponse(fhirService.searchAllergies(principal.tenantId(), patient), HttpStatus.OK);
    }

    // ── DiagnosticReport ─────────────────────────────────────────────────────

    @GetMapping("/DiagnosticReport/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Read a DiagnosticReport by analysis id")
    public ResponseEntity<String> readDiagnosticReport(@PathVariable UUID id,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        return fhirResponse(fhirService.readDiagnosticReport(principal.tenantId(), id), HttpStatus.OK);
    }

    @GetMapping("/DiagnosticReport")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Search DiagnosticReports, optionally by patient")
    public ResponseEntity<String> searchDiagnosticReports(
            @RequestParam(required = false) UUID patient,
            @RequestParam(name = "_count", required = false) Integer count,
            @AuthenticationPrincipal UserPrincipal principal) {
        return fhirResponse(
                fhirService.searchDiagnosticReports(principal.tenantId(), patient, pageSize(count)),
                HttpStatus.OK);
    }

    // ── Observation and ImagingStudy ─────────────────────────────────────────

    @GetMapping("/Observation")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Search a patient's lab Observations")
    public ResponseEntity<String> searchObservations(
            @RequestParam UUID patient,
            @RequestParam(name = "_count", required = false) Integer count,
            @AuthenticationPrincipal UserPrincipal principal) {
        return fhirResponse(
                fhirService.searchObservations(principal.tenantId(), patient, pageSize(count)), HttpStatus.OK);
    }

    @GetMapping("/ImagingStudy")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Search a patient's imaging studies")
    public ResponseEntity<String> searchImagingStudies(
            @RequestParam UUID patient,
            @RequestParam(name = "_count", required = false) Integer count,
            @AuthenticationPrincipal UserPrincipal principal) {
        return fhirResponse(
                fhirService.searchImagingStudies(principal.tenantId(), patient, pageSize(count)), HttpStatus.OK);
    }

    // ── Errors ───────────────────────────────────────────────────────────────

    /**
     * FHIR errors are OperationOutcome resources, not the application's own error envelope.
     *
     * <p>Scoped to this controller so the rest of the API keeps its existing shape: a FHIR client
     * cannot parse {@code ApiResponse}, and a non-FHIR client should not start receiving
     * OperationOutcomes.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<String> handleNotFound(ResourceNotFoundException e) {
        return fhirResponse(outcome(OperationOutcome.IssueSeverity.ERROR,
                OperationOutcome.IssueType.NOTFOUND, e.getMessage()), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleFailure(Exception e) {
        return fhirResponse(outcome(OperationOutcome.IssueSeverity.ERROR,
                OperationOutcome.IssueType.EXCEPTION,
                "The request could not be completed."), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private OperationOutcome outcome(OperationOutcome.IssueSeverity severity,
                                     OperationOutcome.IssueType type, String detail) {
        OperationOutcome result = new OperationOutcome();
        result.addIssue().setSeverity(severity).setCode(type)
                .setDetails(new CodeableConcept().setText(detail));
        return result;
    }

    private int pageSize(Integer requested) {
        if (requested == null || requested < 1) {
            return DEFAULT_COUNT;
        }
        return Math.min(requested, MAX_COUNT);
    }

    /** Serialises through HAPI, not Jackson: R4 choice types do not survive bean serialisation. */
    private ResponseEntity<String> fhirResponse(IBaseResource resource, HttpStatus status) {
        return ResponseEntity.status(status)
                .header("Content-Type", "application/fhir+json;charset=UTF-8")
                .body(fhirJsonParser.encodeResourceToString(resource));
    }
}
