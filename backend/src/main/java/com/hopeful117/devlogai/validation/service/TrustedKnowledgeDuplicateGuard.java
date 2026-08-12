package com.hopeful117.devlogai.validation.service;

import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.insight.service.InsightPayloadSupport;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrustedKnowledgeDuplicateGuard {
    private final InsightRepository insightRepository;

    public void assertCanAccept(ValidatableProposal proposal) {
        if (proposal.getType() != ProposalType.INSIGHT) {
            return;
        }

        CandidateFingerprint candidate = CandidateFingerprint.fromProposal(proposal.getPayload());
        List<Insight> existingInsights = insightRepository.findByProjectIdOrderByCreatedAtDescIdDesc(
                proposal.getProject().getId());

        for (Insight insight : existingInsights) {
            if (!proposal.getProject().getId().equals(insight.getProject().getId())) {
                continue;
            }
            if (candidate.matches(insight)) {
                throw new ConflictException("Accepted insight would create duplicate trusted knowledge");
            }
        }
    }

    private record CandidateFingerprint(
            InsightType type,
            String sourceType,
            String title,
            String content,
            String rationale
    ) {
        static CandidateFingerprint fromProposal(Map<String, Object> payload) {
            String sourceType = InsightPayloadSupport.requiredText(payload, "insightType");
            return new CandidateFingerprint(
                    InsightPayloadSupport.toDomainType(sourceType),
                    sourceType,
                    InsightPayloadSupport.normalize(InsightPayloadSupport.requiredText(payload, "title")),
                    InsightPayloadSupport.normalize(InsightPayloadSupport.requiredText(payload, "summary")),
                    InsightPayloadSupport.normalize(InsightPayloadSupport.optionalText(payload, "rationale"))
            );
        }

        boolean matches(Insight insight) {
            return type == insight.getType()
                    && Objects.equals(normalizedSourceType(insight), sourceType)
                    && Objects.equals(InsightPayloadSupport.normalize(insight.getTitle()), title)
                    && Objects.equals(InsightPayloadSupport.normalize(insight.getContent()), content)
                    && Objects.equals(InsightPayloadSupport.normalize(insight.getRationale()), rationale);
        }

        private String normalizedSourceType(Insight insight) {
            String existing = insight.getSourceType();
            if (existing != null && !existing.isBlank()) {
                return existing;
            }
            return fallbackSourceType(insight.getType());
        }

        private String fallbackSourceType(InsightType type) {
            return switch (type) {
                case ARCHITECTURAL -> "ARCHITECTURE_DESCRIPTION";
                case TECHNOLOGY -> "TECHNOLOGY_DESCRIPTION";
                case DOCUMENTATION -> "PROJECT_PRESENTATION";
                default -> type.name();
            };
        }
    }
}
