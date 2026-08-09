package com.hopeful117.devlogai.validation.service;

import com.hopeful117.devlogai.engineeringevent.*;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.service.InsightPromotionService;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.validation.entity.Validation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProposalPromotionService {
    private final InsightPromotionService insights;
    private final EngineeringEventRepository events;
    private final AnalysisEvolutionScopeRepository scopes;

    public void promote(ValidatableProposal proposal, Validation validation, InsightSeverity severity) {
        switch (proposal.getType()) {
            case INSIGHT -> insights.promote(proposal, validation, severity);
            case ENGINEERING_EVENT -> promoteEvent(proposal, validation, severity);
            default -> throw new IllegalArgumentException(
                    "Accepted proposal type has no promotion handler: " + proposal.getType());
        }
    }

    private void promoteEvent(ValidatableProposal proposal, Validation validation, InsightSeverity severity) {
        if (severity != null) throw new IllegalArgumentException(
                "insightSeverity is not applicable to Engineering Events");
        var scope = scopes.findById(proposal.getAnalysis().getId())
                .orElseThrow(() -> new IllegalStateException("Engineering Event Analysis has no evolution scope"));
        if (!scope.getProject().getId().equals(proposal.getProject().getId()))
            throw new IllegalStateException("Engineering Event scope belongs to another Project");
        Map<String, Object> payload = proposal.getPayload();
        events.save(EngineeringEvent.builder().id(UUID.randomUUID())
                .project(proposal.getProject()).analysis(proposal.getAnalysis())
                .proposal(proposal).validation(validation).source(scope.getSource())
                .category(EngineeringEventCategory.valueOf(text(payload, "category")))
                .title(text(payload, "title")).summary(text(payload, "summary"))
                .significance(text(payload, "significance"))
                .baseCommit(scope.getBaseCommit()).targetCommit(scope.getTargetCommit())
                .occurredAt(scope.getTargetCommittedAt()).createdAt(Instant.now()).build());
    }

    private String text(Map<String, Object> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        if (!(value instanceof String text) || text.isBlank())
            throw new IllegalArgumentException("Engineering Event proposal is missing " + key);
        return text.trim();
    }
}
