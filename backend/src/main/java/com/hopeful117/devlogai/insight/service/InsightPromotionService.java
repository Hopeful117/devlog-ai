package com.hopeful117.devlogai.insight.service;

import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightTrustState;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.knowledge.relation.entity.EntityType;
import com.hopeful117.devlogai.knowledge.relation.entity.KnowledgeRelation;
import com.hopeful117.devlogai.knowledge.relation.entity.KnowledgeRelationType;
import com.hopeful117.devlogai.knowledge.relation.repository.KnowledgeRelationRepository;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.validation.entity.Validation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InsightPromotionService {
    private final InsightRepository insightRepository;
    private final KnowledgeRelationRepository knowledgeRelationRepository;

    public void promote(ValidatableProposal proposal, Validation validation, InsightSeverity severity) {
        if (proposal.getType() != ProposalType.INSIGHT) {
            return;
        }
        if (severity == null) {
            throw new IllegalArgumentException("Severity is required when accepting an insight proposal");
        }
        Map<String, Object> payload = proposal.getPayload();
        Insight insight = Insight.builder()
                .project(proposal.getProject())
                .analysis(proposal.getAnalysis())
                .proposal(proposal)
                .validation(validation)
                .type(InsightPayloadSupport.toDomainType(InsightPayloadSupport.requiredText(payload, "insightType")))
                .severity(severity)
                .title(InsightPayloadSupport.requiredText(payload, "title"))
                .content(InsightPayloadSupport.requiredText(payload, "summary"))
                .rationale(InsightPayloadSupport.optionalText(payload, "rationale"))
                .confidence(proposal.getConfidence())
                .evidenceReferences(proposal.getEvidenceReferences())
                .sourceType(InsightPayloadSupport.optionalText(payload, "insightType"))
                .build();
        Insight savedInsight = insightRepository.save(insight);
        createDeltaRelationIfNeeded(proposal, savedInsight);
    }

    private void createDeltaRelationIfNeeded(ValidatableProposal proposal, Insight savedInsight) {
        String deltaType = InsightPayloadSupport.optionalText(proposal.getPayload(), "deltaType");
        if (!"ENRICHES".equals(deltaType) && !"SUPERSEDES".equals(deltaType)) {
            return;
        }
        UUID targetInsightId = InsightPayloadSupport.requiredUuid(proposal.getPayload(), "targetInsightId");
        Insight target = insightRepository.findById(targetInsightId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Accepted insight " + deltaType + " target does not exist: " + targetInsightId));
        if (!target.getProject().getId().equals(proposal.getProject().getId())) {
            throw new IllegalArgumentException(
                    "Accepted insight " + deltaType + " target belongs to another project: " + targetInsightId);
        }
        if (deltaType == null) {
            return;
        }
        if ("SUPERSEDES".equals(deltaType)) {
            if (target.getTrustState() != InsightTrustState.ACTIVE) {
                throw new IllegalArgumentException(
                        "Accepted insight supersession target is not active: " + targetInsightId);
            }
            target.setTrustState(InsightTrustState.SUPERSEDED);
            insightRepository.save(target);
        }
        String description = "SUPERSEDES".equals(deltaType)
                ? "Incremental architecture supersession accepted from trusted knowledge"
                : "Incremental architecture enrichment accepted from trusted knowledge";
        KnowledgeRelationType relationType = "SUPERSEDES".equals(deltaType)
                ? KnowledgeRelationType.SUPERSEDES
                : KnowledgeRelationType.DERIVED_FROM;
        knowledgeRelationRepository.save(KnowledgeRelation.builder()
                .project(proposal.getProject())
                .sourceEntityType(EntityType.INSIGHT)
                .sourceEntityId(savedInsight.getId())
                .targetEntityType(EntityType.INSIGHT)
                .targetEntityId(targetInsightId)
                .relationType(relationType)
                .description(description)
                .build());
    }
}
