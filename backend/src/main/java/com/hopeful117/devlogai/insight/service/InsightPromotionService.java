package com.hopeful117.devlogai.insight.service;

import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
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
        createEnrichmentRelationIfNeeded(proposal, savedInsight);
    }

    private void createEnrichmentRelationIfNeeded(ValidatableProposal proposal, Insight savedInsight) {
        if (!"ENRICHES".equals(InsightPayloadSupport.optionalText(proposal.getPayload(), "deltaType"))) {
            return;
        }
        UUID targetInsightId = InsightPayloadSupport.requiredUuid(proposal.getPayload(), "targetInsightId");
        Insight target = insightRepository.findById(targetInsightId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Accepted insight enrichment target does not exist: " + targetInsightId));
        if (!target.getProject().getId().equals(proposal.getProject().getId())) {
            throw new IllegalArgumentException(
                    "Accepted insight enrichment target belongs to another project: " + targetInsightId);
        }
        knowledgeRelationRepository.save(KnowledgeRelation.builder()
                .project(proposal.getProject())
                .sourceEntityType(EntityType.INSIGHT)
                .sourceEntityId(savedInsight.getId())
                .targetEntityType(EntityType.INSIGHT)
                .targetEntityId(targetInsightId)
                .relationType(KnowledgeRelationType.DERIVED_FROM)
                .description("Incremental architecture enrichment accepted from trusted knowledge")
                .build());
    }
}
