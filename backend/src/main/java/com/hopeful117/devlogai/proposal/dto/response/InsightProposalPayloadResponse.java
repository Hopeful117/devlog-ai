package com.hopeful117.devlogai.proposal.dto.response;

public record InsightProposalPayloadResponse(
        String insightType,
        String title,
        String summary,
        String rationale
) { }
