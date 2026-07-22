package com.hopeful117.devlogai.insight.service;

import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.validation.entity.Validation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class InsightPromotionService {
    private final InsightRepository insightRepository;

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
                .build();
        insightRepository.save(insight);
    }

    private String requiredText(Map<String, Object> payload, String field) {
        Object value = payload == null ? null : payload.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Accepted insight proposal is missing payload field: " + field);
        }
        return text;
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
