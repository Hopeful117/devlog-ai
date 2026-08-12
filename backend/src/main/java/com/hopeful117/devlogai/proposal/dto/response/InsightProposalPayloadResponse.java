package com.hopeful117.devlogai.proposal.dto.response;

import java.util.UUID;

public record InsightProposalPayloadResponse(
        String insightType,
        String title,
        String summary,
        String rationale,
        String deltaType,
        UUID targetInsightId
) { }
