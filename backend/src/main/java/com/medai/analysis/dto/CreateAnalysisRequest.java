package com.medai.analysis.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateAnalysisRequest {

    @NotNull(message = "Patient ID is required")
    private UUID patientId;

    @NotNull(message = "Medical file ID is required")
    private UUID medicalFileId;

    private String clinicalNotes;
}
