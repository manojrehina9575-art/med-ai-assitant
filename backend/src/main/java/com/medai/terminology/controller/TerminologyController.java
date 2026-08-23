package com.medai.terminology.controller;

import com.medai.terminology.client.RxNormClient;
import com.medai.terminology.dto.CodeValidation;
import com.medai.terminology.service.Icd10Validator;
import com.medai.terminology.service.LoincMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/terminology")
@RequiredArgsConstructor
@Tag(name = "Terminology", description = "ICD-10, LOINC and RxNorm validation and lookup")
public class TerminologyController {

    private final Icd10Validator icd10Validator;
    private final LoincMapper loincMapper;
    private final RxNormClient rxNormClient;

    @GetMapping("/icd10/validate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Validate an ICD-10 code",
               description = "Structural and chapter-range validation. VALID means confirmed "
                             + "against the bundled code set; UNKNOWN means well-formed and "
                             + "in-range but unconfirmed — verify before billing.")
    public ResponseEntity<CodeValidation> validateIcd10(@RequestParam String code) {
        return ResponseEntity.ok(icd10Validator.validate(code));
    }

    @PostMapping("/icd10/validate-batch")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Validate several ICD-10 codes at once")
    public ResponseEntity<List<CodeValidation>> validateIcd10Batch(@RequestBody List<String> codes) {
        return ResponseEntity.ok(icd10Validator.validateAll(codes));
    }

    @GetMapping("/loinc/resolve")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Resolve a lab analyte name to LOINC",
               description = "Returns 404 when the analyte is not in the mapping. An unmapped "
                             + "analyte yields no code rather than a guessed one.")
    public ResponseEntity<LoincMapper.Loinc> resolveLoinc(@RequestParam String analyte) {
        return loincMapper.resolve(analyte)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/rxnorm/resolve")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Resolve a drug name to an RxNorm concept")
    public ResponseEntity<RxNormClient.RxConcept> resolveRxNorm(@RequestParam String drug) {
        return rxNormClient.resolve(drug)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * States what each vocabulary can and cannot confirm.
     *
     * <p>The same reasoning as the PHI redactor's coverage endpoint: a caller who believes a code
     * was validated treats it as validated. Where the answer is "well-formed but unconfirmed",
     * that has to be legible rather than inferred from a green tick.
     */
    @GetMapping("/coverage")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "What each vocabulary can and cannot confirm")
    public ResponseEntity<Map<String, Object>> coverage() {
        Map<String, Object> icd10 = new LinkedHashMap<>();
        icd10.put("system", Icd10Validator.SYSTEM);
        icd10.put("validates", "Code format and WHO chapter range. A confirmed subset returns VALID "
                               + "with an official display name.");
        icd10.put("cannotConfirm", "That a well-formed, in-range code is a real leaf code meaning "
                                   + "what the report says. Needs the licensed tabular list.");

        Map<String, Object> loinc = new LinkedHashMap<>();
        loinc.put("system", LoincMapper.SYSTEM);
        loinc.put("synonymsMapped", loincMapper.knownSynonymCount());
        loinc.put("scope", "CBC, renal, liver, lipid, glycaemic, thyroid, cardiac, inflammatory and "
                           + "coagulation analytes, with Indian-lab abbreviations.");
        loinc.put("cannotConfirm", "Analytes outside that set — they are returned uncoded rather "
                                   + "than mapped to an approximate LOINC.");

        Map<String, Object> rxnorm = new LinkedHashMap<>();
        rxnorm.put("system", RxNormClient.SYSTEM);
        rxnorm.put("enabled", rxNormClient.isEnabled());
        rxnorm.put("scope", "Ingredient normalisation via the NLM RxNav service.");
        rxnorm.put("cannotConfirm", "Drug interactions — RxNav's interaction API was retired in "
                                    + "January 2024. Interaction checking uses the curated table in "
                                    + "DrugKnowledgeBase. RxNorm is also a US vocabulary and does "
                                    + "not carry most Indian brand names.");

        return ResponseEntity.ok(Map.of("icd10", icd10, "loinc", loinc, "rxnorm", rxnorm));
    }
}
