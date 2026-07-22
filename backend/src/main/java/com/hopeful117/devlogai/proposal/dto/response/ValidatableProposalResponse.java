package com.hopeful117.devlogai.proposal.dto.response;

import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.List;

public record ValidatableProposalResponse(

        UUID id,

        UUID projectId,

        UUID analysisId,

        ProposalType type,

        ProposalStatus status,

        Map<String, Object> payload,

        InsightProposalPayloadResponse insight,

        BigDecimal confidence,

        List<UUID> supportingFactIds,

        List<UUID> supportingObservationIds,

        List<String> evidenceReferences,

        Instant createdAt,

        Instant decidedAt



) {
    public ValidatableProposalResponse(UUID id, UUID projectId, UUID analysisId,
                                       ProposalType type, ProposalStatus status,
                                       Map<String, Object> payload, Instant createdAt,
                                       Instant decidedAt) {
        this(id, projectId, analysisId, type, status, payload, null, null,
                List.of(), List.of(), List.of(), createdAt, decidedAt);
    }
}
