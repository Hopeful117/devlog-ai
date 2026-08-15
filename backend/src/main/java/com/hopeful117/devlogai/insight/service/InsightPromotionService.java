package com.hopeful117.devlogai.insight.service;

import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.entity.InsightStatus;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.knowledge.relation.entity.EntityType;
import com.hopeful117.devlogai.knowledge.relation.entity.KnowledgeRelation;
import com.hopeful117.devlogai.knowledge.relation.entity.KnowledgeRelationType;
import com.hopeful117.devlogai.knowledge.relation.repository.KnowledgeRelationRepository;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.validation.entity.Validation;
import com.hopeful117.devlogai.insight.service.InsightSimilarityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InsightPromotionService {
    private final InsightRepository insightRepository;
    private final KnowledgeRelationRepository knowledgeRelationRepository;
    private final InsightSimilarityService similarityService;

    public PromotionResult promote(ValidatableProposal proposal, Validation validation, InsightSeverity severity) {
        if (proposal.getType() != ProposalType.INSIGHT) {
            return new PromotionResult(
                    null,
                    new SimilarityAssessment(false, null, null, 0.0)
            );
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

        SimilarityAssessment assessment = assessSemanticSimilarity(savedInsight);

        return new PromotionResult(savedInsight, assessment);
    }

    private SimilarityAssessment assessSemanticSimilarity(Insight candidate) {
        var existingInsights = insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                candidate.getProject().getId(),
                List.of(InsightStatus.ACTIVE)
        );

        if (existingInsights.isEmpty()) {
            return new SimilarityAssessment(false, null, null, 0.0);
        }

        double bestScore = 0.0;
        UUID bestId = null;
        String bestTitle = null;

        for (Insight existing : existingInsights) {
            double score = similarityService.computeSimilarity(
                    candidate.getContent(),
                    existing.getContent(),
                    List.of(candidate.getContent(), existing.getContent())
            );

            if (score > bestScore) {
                bestScore = score;
                bestId = existing.getId();
                bestTitle = existing.getTitle();
            }
        }

        return new SimilarityAssessment(true, bestId, bestTitle, bestScore);
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
