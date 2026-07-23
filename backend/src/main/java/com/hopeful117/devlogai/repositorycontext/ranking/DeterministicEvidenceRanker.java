package com.hopeful117.devlogai.repositorycontext.ranking;

import com.hopeful117.devlogai.intent.model.UserGuidance;
import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class DeterministicEvidenceRanker implements EvidenceRanker {
    @Override
    public List<RepositoryEvidence> rank(
            List<RepositoryEvidence> evidence,
            ContextRequest request
    ) {
        return evidence.stream().map(value -> rank(value, request))
                .sorted(Comparator.comparingInt(RepositoryEvidence::relevanceScore).reversed()
                        .thenComparing(value -> value.layer().ordinal())
                        .thenComparing(RepositoryEvidence::reference))
                .toList();
    }

    private RepositoryEvidence rank(
            RepositoryEvidence evidence,
            ContextRequest request
    ) {
        int score = evidence.relevanceScore();
        List<String> reasons = new ArrayList<>(evidence.rankingReasons());
        int profileBoost = profileBoost(request.profile(), evidence.layer());
        if (profileBoost > 0) {
            score += profileBoost;
            reasons.add("CONTEXT_PROFILE_PRIORITY");
        }
        int intentBoost = keywordBoost(request.intent().id(), evidence.summary());
        if (intentBoost > 0) {
            score += intentBoost;
            reasons.add("INTENT_RELEVANCE");
        }
        int guidanceBoost = guidanceBoost(request.guidance(), evidence.summary());
        if (guidanceBoost > 0) {
            score += guidanceBoost;
            reasons.add("USER_GUIDANCE_KEYWORD");
        }
        if (evidence.layer() == RepositoryContextLayer.VALIDATED_INSIGHT) {
            score += 100;
            reasons.add("VALIDATED_KNOWLEDGE");
        }
        if (recent(evidence.occurredAt(),
                request.analysisContext().analysis().createdAt())) {
            score += 20;
            reasons.add("RECENCY");
        }
        return evidence.withRanking(score, reasons);
    }

    private int profileBoost(ContextProfile profile, RepositoryContextLayer layer) {
        return switch (profile) {
            case ARCHITECTURE_REVIEW -> switch (layer) {
                case ADR, RELATED_SOURCE_CODE, VALIDATED_INSIGHT -> 180;
                case GIT_HISTORY, COMMIT_DIFF -> 80;
                default -> 20;
            };
            case README_GENERATION, DOCUMENTATION -> switch (layer) {
                case PROJECT_DOCUMENTATION, RELATED_SOURCE_CODE, VALIDATED_INSIGHT -> 180;
                case ADR -> 60;
                default -> 20;
            };
            case HISTORY_ANALYSIS, RELEASE_SUMMARY -> switch (layer) {
                case GIT_HISTORY, COMMIT_DIFF, ROADMAP -> 180;
                case PREVIOUS_ANALYSIS -> 100;
                default -> 20;
            };
            case PROJECT_STATE -> switch (layer) {
                case CURRENT_ANALYSIS, RELATED_SOURCE_CODE, VALIDATED_INSIGHT -> 140;
                default -> 30;
            };
            case KNOWLEDGE_EXTRACTION -> 50;
        };
    }

    private int keywordBoost(String intentId, String summary) {
        String value = summary.toUpperCase(Locale.ROOT);
        if ("architecture-overview".equals(intentId)
                && containsAny(value, "ARCHITECTURE", "ADR", "MODULE", "API")) return 120;
        if ("generate-readme".equals(intentId)
                && containsAny(value, "README", "DOCUMENTATION", "BUILD", "USAGE")) return 120;
        return 0;
    }

    private int guidanceBoost(UserGuidance guidance, String summary) {
        if (guidance == null || guidance.isEmpty()) return 0;
        String terms = String.join(" ", guidance.priorities()) + " "
                + Objects.toString(guidance.focus(), "");
        String candidate = summary.toLowerCase(Locale.ROOT);
        return (int) Arrays.stream(terms.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(term -> term.length() >= 3).distinct()
                .filter(candidate::contains).count() * 5;
    }

    private boolean recent(Instant value, Instant analysisCreatedAt) {
        return value != null && analysisCreatedAt != null
                && value.isAfter(analysisCreatedAt.minus(365, ChronoUnit.DAYS))
                && !value.isAfter(analysisCreatedAt);
    }

    private boolean containsAny(String value, String... terms) {
        return Arrays.stream(terms).anyMatch(value::contains);
    }
}
