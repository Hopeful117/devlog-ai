package com.hopeful117.devlogai.proposal.dto.request;

import com.hopeful117.devlogai.proposal.entity.ProposalType;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

public record CreateValidatableProposalRequest(

        @NotNull
        UUID projectId,

        @NotNull
        UUID analysisId,

        @NotNull
        ProposalType type,

        @NotNull
        JsonNode payload
) {
}
