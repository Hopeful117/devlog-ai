package com.hopeful117.devlogai.proposal.dto.response;

import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ValidatableProposalResponse(

        UUID id,

        UUID projectId,

        UUID analysisId,

        ProposalType type,

        ProposalStatus status,

        Map<String, Object> payload,

        Instant createdAt,

        Instant decidedAt



) {
}
