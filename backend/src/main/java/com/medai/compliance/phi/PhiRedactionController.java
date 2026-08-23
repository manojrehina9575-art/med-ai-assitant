package com.medai.compliance.phi;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/compliance/phi")
@RequiredArgsConstructor
@Tag(name = "PHI Redaction & De-identification", description = "HIPAA Safe Harbor PHI Redaction Sandbox")
public class PhiRedactionController {

    private final PhiRedactionService phiRedactionService;

    @Data
    public static class RedactionTestRequest {
        private String text;
    }

    @PostMapping("/sandbox")
    @Operation(summary = "Test PHI redaction on sample medical text")
    public ResponseEntity<PhiRedactionService.RedactionResult> redactText(@RequestBody RedactionTestRequest request) {
        return ResponseEntity.ok(phiRedactionService.redact(request.getText()));
    }

    @Data
    public static class RestoreRequest {
        private String redactedText;
        private Map<String, String> tokenMap;
    }

    @PostMapping("/restore")
    @Operation(summary = "Restore pseudonymized tokens with mapping")
    public ResponseEntity<Map<String, String>> restoreText(@RequestBody RestoreRequest request) {
        String restored = phiRedactionService.restore(request.getRedactedText(), request.getTokenMap());
        return ResponseEntity.ok(Map.of("restoredText", restored));
    }
}
