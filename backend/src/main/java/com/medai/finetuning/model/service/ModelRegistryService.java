package com.medai.finetuning.model.service;

import com.medai.finetuning.model.dto.ModelRegisterRequest;
import com.medai.finetuning.model.entity.AiModelRegistry;
import com.medai.finetuning.model.repository.AiModelRegistryRepository;
import com.medai.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelRegistryService {

    private final AiModelRegistryRepository modelRegistryRepository;

    @Transactional(readOnly = true)
    public List<AiModelRegistry> getAvailableModels() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return modelRegistryRepository.findAllAvailableForTenant(tenantId);
    }

    @Transactional
    public AiModelRegistry registerModel(ModelRegisterRequest request) {
        UUID tenantId = request.isTenantPrivate() ? TenantContext.requireTenantId() : null;

        AiModelRegistry model = modelRegistryRepository.findByModelId(request.getModelId())
                .orElse(AiModelRegistry.builder()
                        .modelId(request.getModelId())
                        .build());

        model.setTenantId(tenantId);
        model.setDisplayName(request.getDisplayName());
        model.setBaseModel(request.getBaseModel());
        model.setAdapterType(request.getAdapterType() != null ? request.getAdapterType() : "LORA");
        model.setStatus(request.getStatus() != null ? request.getStatus() : "READY");
        model.setLoraRank(request.getLoraRank());
        model.setLoraAlpha(request.getLoraAlpha());
        model.setTrainingLoss(request.getTrainingLoss());
        model.setTrainingSamplesCount(request.getTrainingSamplesCount() != null ? request.getTrainingSamplesCount() : 0);
        model.setEndpointUrl(request.getEndpointUrl());
        model.setDescription(request.getDescription());
        model.setActive(true);

        AiModelRegistry saved = modelRegistryRepository.save(model);
        log.info("Registered/updated AI model: {} (adapter: {})", saved.getModelId(), saved.getAdapterType());
        return saved;
    }

    @Transactional
    public AiModelRegistry updateStatus(UUID modelUuid, String status) {
        AiModelRegistry model = modelRegistryRepository.findById(modelUuid)
                .orElseThrow(() -> new IllegalArgumentException("Model not found"));
        model.setStatus(status);
        return modelRegistryRepository.save(model);
    }
}
