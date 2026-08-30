package com.hopeful117.devlogai.analysis.result.service;

import com.hopeful117.devlogai.analysis.result.dto.AnalysisResultResponse;
import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProposalSummaryMapper {

    public AnalysisResultResponse.ProposalSummary mapToSummary(
            ValidatableProposal proposal,
            AnalysisResultResponse.TrustedArtifact trustedArtifact
    ) {
        Map<String, Object> payload = proposal.getPayload();
        String title = extractTitle(payload);
        String summary = extractSummary(payload);
        List<String> evidencePreview = buildEvidencePreview(proposal);

        TypeSpecificFields fields = extractTypeSpecificFields(proposal);

        return new AnalysisResultResponse.ProposalSummary(
                proposal.getId(),
                proposal.getType(),
                proposal.getStatus(),
                proposal.getConfidence() != null ? proposal.getConfidence().doubleValue() : null,
                title,
                summary,
                evidencePreview,
                proposal.getId(),
                trustedArtifact,
                fields.rationale,
                fields.insightType,
                fields.deltaType,
                fields.context,
                fields.choice,
                fields.consequences,
                fields.category,
                fields.significance,
                List.copyOf(proposal.getSupportingFactIds() != null ? proposal.getSupportingFactIds() : List.of()),
                List.copyOf(proposal.getSupportingObservationIds() != null ? proposal.getSupportingObservationIds() : List.of())
        );
    }

    private TypeSpecificFields extractTypeSpecificFields(ValidatableProposal proposal) {
        Map<String, Object> payload = proposal.getPayload();
        return switch (proposal.getType()) {
            case INSIGHT -> extractInsightFields(payload);
            case ENGINEERING_DECISION -> extractDecisionFields(payload);
            case ENGINEERING_EVENT -> extractEventFields(payload);
            default -> new TypeSpecificFields(null, null, null, null, null, null, null, null);
        };
    }

    private TypeSpecificFields extractInsightFields(Map<String, Object> payload) {
        return new TypeSpecificFields(
                extractString(payload, "rationale"),
                extractString(payload, "insightType"),
                extractString(payload, "deltaType"),
                null,
                null,
                null,
                null,
                null
        );
    }

    private TypeSpecificFields extractDecisionFields(Map<String, Object> payload) {
        return new TypeSpecificFields(
                extractString(payload, "rationale"),
                null,
                null,
                extractString(payload, "context"),
                extractString(payload, "choice"),
                extractString(payload, "consequences"),
                null,
                null
        );
    }

    private TypeSpecificFields extractEventFields(Map<String, Object> payload) {
        return new TypeSpecificFields(
                null,
                null,
                null,
                null,
                null,
                null,
                extractString(payload, "category"),
                extractString(payload, "significance")
        );
    }

    private String extractTitle(Map<String, Object> payload) {
        if (payload == null) return "Untitled proposal";
        Object title = payload.get("title");
        return title instanceof String ? (String) title : "Untitled proposal";
    }

    private String extractSummary(Map<String, Object> payload) {
        if (payload == null) return "";
        Object summary = payload.get("summary");
        return summary instanceof String ? (String) summary : "";
    }

    private String extractString(Map<String, Object> payload, String key) {
        if (payload == null) return null;
        Object value = payload.get(key);
        return value instanceof String ? (String) value : null;
    }

    private List<String> buildEvidencePreview(ValidatableProposal proposal) {
        List<String> preview = new ArrayList<>();
        if (proposal.getSupportingFactIds() != null) {
            for (UUID factId : proposal.getSupportingFactIds().stream().limit(3).toList()) {
                preview.add("Fact#" + factId.toString().substring(0, 8));
            }
        }
        if (proposal.getSupportingObservationIds() != null) {
            for (UUID obsId : proposal.getSupportingObservationIds().stream().limit(2).toList()) {
                preview.add("Observation#" + obsId.toString().substring(0, 8));
            }
        }
        return preview;
    }

    private record TypeSpecificFields(
            String rationale,
            String insightType,
            String deltaType,
            String context,
            String choice,
            String consequences,
            String category,
            String significance
    ) {}
}
