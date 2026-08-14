package com.hopeful117.devlogai.contextmaintenance.dto.response;

import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentConfidenceLevel;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentRecommendedAction;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentSemanticClassification;

import java.time.Instant;
import java.util.UUID;

public record MaintenanceAssessmentResponse(
        UUID id,
        UUID projectId,
        UUID findingId,
        MaintenanceAssessmentConfidenceLevel confidenceLevel,
        MaintenanceAssessmentSemanticClassification semanticClassification,
        MaintenanceAssessmentRecommendedAction recommendedAction,
        String rationale,
        String supportingSignals,
        Instant createdAt,
        Instant updatedAt
) {}
