package com.medai.finetuning.model.controller;

import com.medai.finetuning.model.dto.ModelRegisterRequest;
import com.medai.finetuning.model.entity.AiModelRegistry;
import com.medai.finetuning.model.service.ModelRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
@Tag(name = "Model Registry", description = "AI Models, LoRA Adapters & Fine-Tuned Weights")
public class ModelRegistryController {

    private final ModelRegistryService modelRegistryService;

    @GetMapping
    @Operation(summary = "List all available AI models and adapters for current tenant")
    public ResponseEntity<List<AiModelRegistry>> listModels() {
        return ResponseEntity.ok(modelRegistryService.getAvailableModels());
    }

    @PostMapping
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    @Operation(summary = "Register or update a model/LoRA adapter")
    public ResponseEntity<AiModelRegistry> registerModel(@Valid @RequestBody ModelRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(modelRegistryService.registerModel(request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    @Operation(summary = "Update model deployment status")
    public ResponseEntity<AiModelRegistry> updateStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        return ResponseEntity.ok(modelRegistryService.updateStatus(id, status));
    }
}
