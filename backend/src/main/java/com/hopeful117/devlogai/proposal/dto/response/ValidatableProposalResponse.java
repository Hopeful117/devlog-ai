package com.hopeful117.devlogai.proposal.dto.response;

import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record ValidatableProposalResponse(

        UUID id,

        UUID projectId,

        UUID analysisId,

        ProposalType type,

        ProposalStatus status,

        JsonNode payload,

        Instant createdAt,

        Instant decidedAt



) {
}
