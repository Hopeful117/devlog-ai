package com.hopeful117.devlogai.contextmaintenance.dto.request;

import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentConfidenceLevel;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentRecommendedAction;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentSemanticClassification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateMaintenanceAssessmentRequest(
        @NotNull UUID findingId,
        @NotNull MaintenanceAssessmentConfidenceLevel confidenceLevel,
        @NotNull MaintenanceAssessmentSemanticClassification semanticClassification,
        @NotNull MaintenanceAssessmentRecommendedAction recommendedAction,
        @NotBlank String rationale,
        String supportingSignals
) {}
