package com.hopeful117.devlogai.deliverable.dto;

import com.hopeful117.devlogai.deliverable.entity.DeliverableType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateDeliverableRequest(
        @NotNull UUID projectId,
        UUID analysisId,
        @NotNull DeliverableType type,
        @NotBlank @Size(max = 200) String audience,
        @NotBlank @Size(max = 100) String style,
        @NotBlank @Size(max = 20) String language,
        @Size(max = 1000) String additionalGuidance
) { }
