package com.hopeful117.devlogai.lineage.dto;

import java.util.UUID;

public record KnowledgeLifecycleStageResponse(
        String stage,
        LineageStageStatus status,
        UUID artifactId,
        String detail
) {
}