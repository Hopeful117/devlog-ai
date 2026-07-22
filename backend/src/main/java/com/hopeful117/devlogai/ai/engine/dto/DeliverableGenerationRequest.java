package com.hopeful117.devlogai.ai.engine.dto;

import com.hopeful117.devlogai.deliverable.entity.DeliverableType;

import java.util.List;
import java.util.UUID;

public record DeliverableGenerationRequest(
        UUID requestId, UUID projectId, UUID analysisId, DeliverableType type,
        String audience, String style, String language, String additionalGuidance,
        List<ValidatedInsightSnapshot> validatedInsights
) {
    public record ValidatedInsightSnapshot(
            UUID id, UUID analysisId, String type, String severity, String title, String content
    ) { }
}
