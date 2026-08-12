package com.hopeful117.devlogai.insight.dto.response;

import java.util.List;
import java.util.UUID;

public record InsightDuplicateAuditResponse(
        UUID projectId,
        int totalInsights,
        int clusterCount,
        List<InsightDuplicateClusterResponse> clusters
) {
}
