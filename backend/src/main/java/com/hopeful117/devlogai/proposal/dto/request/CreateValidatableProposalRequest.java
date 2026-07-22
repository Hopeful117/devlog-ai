package com.hopeful117.devlogai.proposal.dto.request;

import com.hopeful117.devlogai.proposal.entity.ProposalType;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

public record CreateValidatableProposalRequest(

        @NotNull
        UUID projectId,

        @NotNull
        UUID analysisId,

        @NotNull
        ProposalType type,

        @NotNull
        Map<String, Object> payload
) {
}
