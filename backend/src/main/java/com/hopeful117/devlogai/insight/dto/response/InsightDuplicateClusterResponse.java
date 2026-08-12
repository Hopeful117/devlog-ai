package com.hopeful117.devlogai.insight.dto.response;

import java.util.List;

public record InsightDuplicateClusterResponse(
        String clusterKey,
        InsightDuplicateClusterCategory category,
        InsightDuplicateRecommendation recommendation,
        String rationale,
        List<InsightDuplicateMemberResponse> members
) {
}
