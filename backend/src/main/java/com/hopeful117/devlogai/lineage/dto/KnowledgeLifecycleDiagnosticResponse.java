package com.hopeful117.devlogai.lineage.dto;

import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ProposalType;

import java.util.List;
import java.util.UUID;

public record KnowledgeLifecycleDiagnosticResponse(
        UUID proposalId,
        ProposalType type,
        ProposalStatus proposalStatus,
        KnowledgeLifecycleStatus lifecycleStatus,
        List<KnowledgeLifecycleStageResponse> stages,
        List<String> findings
) {
}