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
                .type(toDomainType(requiredText(payload, "insightType")))
                .severity(severity)
                .title(requiredText(payload, "title"))
                .content(requiredText(payload, "summary"))
                .rationale(optionalText(payload, "rationale"))
                .confidence(proposal.getConfidence())
                .evidenceReferences(proposal.getEvidenceReferences())
                .sourceType(optionalText(payload, "insightType"))
                .build();
        Insight savedInsight = insightRepository.save(insight);
        createEnrichmentRelationIfNeeded(proposal, savedInsight);
    }

    private String requiredText(Map<String, Object> payload, String field) {
        Object value = payload == null ? null : payload.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Accepted insight proposal is missing payload field: " + field);
        }
        return text;
    }

    private String optionalText(Map<String, Object> payload, String field) {
        Object value = payload == null ? null : payload.get(field);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private void createEnrichmentRelationIfNeeded(ValidatableProposal proposal, Insight savedInsight) {
        if (!"ENRICHES".equals(optionalText(proposal.getPayload(), "deltaType"))) {
            return;
        }
        UUID targetInsightId = requiredUuid(proposal.getPayload(), "targetInsightId");
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

    private UUID requiredUuid(Map<String, Object> payload, String field) {
        Object value = payload == null ? null : payload.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Accepted insight proposal is missing payload field: " + field);
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Accepted insight proposal has invalid UUID field: " + field);
        }
    }

    private InsightType toDomainType(String proposalType) {
        return switch (proposalType) {
            case "ARCHITECTURE_DESCRIPTION", "INFRASTRUCTURE_DESCRIPTION" -> InsightType.ARCHITECTURAL;
            case "TECHNOLOGY_DESCRIPTION" -> InsightType.TECHNOLOGY;
            case "PROJECT_PRESENTATION", "INSTALLATION", "USAGE", "REQUIREMENTS", "API_DESCRIPTION" ->
                    InsightType.DOCUMENTATION;
            default -> throw new IllegalArgumentException("Unsupported insight proposal type: " + proposalType);
        };
    }
}
