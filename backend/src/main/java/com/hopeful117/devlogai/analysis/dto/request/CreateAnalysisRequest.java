package com.hopeful117.devlogai.analysis.dto.request;

import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import com.hopeful117.devlogai.intent.model.UserGuidance;
import jakarta.validation.Valid;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateAnalysisRequest {
    @NotNull
    private UUID projectId;

    // Made optional for legacy compatibility; must match derived type if present
    private AnalysisType type;

    @NotBlank
    private String intentId;

    @Size(max = 255)
    private String targetRevision;

    // Added for repository scope; conditional
    private UUID sourceId;

    @Valid
    private UserGuidance userGuidance;
}
