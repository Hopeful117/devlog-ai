package com.hopeful117.devlogai.projectstate.dto.inner;

import com.hopeful117.devlogai.proposal.entity.ProposalStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record ProposalSummary(
        UUID id,
        String type,
        String insightType,
        String title,
        String summary,
        ProposalStatus status,
        BigDecimal confidence
) {
}
