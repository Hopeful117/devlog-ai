package com.hopeful117.devlogai.repositorycontext.ranking;

import com.hopeful117.devlogai.intent.model.UserGuidance;
import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceCriterion;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceScore;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class DeterministicEvidenceRanker implements EvidenceRanker {
    static final String POLICY_VERSION = "multi-criteria-v1";

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
        Map<EvidenceCriterion, Integer> criteria =
                new EnumMap<>(EvidenceCriterion.class);
        criteria.put(EvidenceCriterion.SEMANTIC_RELEVANCE,
                semanticRelevance(evidence, request));
        criteria.put(EvidenceCriterion.ARCHITECTURAL_RELEVANCE,
                architecturalRelevance(evidence));
        criteria.put(EvidenceCriterion.HISTORICAL_RELEVANCE,
                historicalRelevance(evidence));
        criteria.put(EvidenceCriterion.RECENCY,
                recency(evidence.occurredAt(),
                        request.analysisContext().analysis().createdAt()));
        criteria.put(EvidenceCriterion.CONFIDENCE, confidence(evidence));
        criteria.put(EvidenceCriterion.USER_GUIDANCE_BOOST,
                guidanceRelevance(request.guidance(), evidence.summary()));
        int finalScore = weightedScore(criteria,
                request.contextPlan().composedWeights());
        List<String> explanations = criteria.entrySet().stream()
                .map(entry -> entry.getKey().name() + "=" + entry.getValue()
                        + "@" + request.contextPlan().composedWeights()
                        .getOrDefault(entry.getKey(), 0))
                .toList();
        EvidenceScore score = new EvidenceScore(POLICY_VERSION, criteria,
                request.contextPlan().composedWeights(), finalScore, explanations);
        return evidence.withRanking(score, explanations);
    }

    private int semanticRelevance(
            RepositoryEvidence evidence,
            ContextRequest request
    ) {
        String query = request.intent().id() + " " + request.intent().objective();
        Set<String> terms = normalizedTerms(query);
        String candidate = evidence.kind() + " " + evidence.summary() + " "
                + Objects.toString(evidence.provenance().originatingFile(), "");
        long matches = terms.stream().filter(
                candidate.toLowerCase(Locale.ROOT)::contains).count();
        int score = (int) Math.min(100, matches * 25);
        if (evidence.layer() == RepositoryContextLayer.CURRENT_ANALYSIS)
            score = Math.max(score, 90);
        return score;
    }

    private int architecturalRelevance(RepositoryEvidence evidence) {
        int layerScore = switch (evidence.layer()) {
            case ADR -> 100;
            case RELATED_SOURCE_CODE, COMMIT_DIFF -> 80;
            case VALIDATED_INSIGHT -> 70;
            case GIT_HISTORY -> 50;
            default -> 20;
        };
        String value = evidence.summary().toUpperCase(Locale.ROOT);
        if (containsAny(value, "ARCHITECTURE", "MODULE", "API", "DEPENDENCY"))
            return Math.min(100, layerScore + 20);
        return layerScore;
    }

    private int historicalRelevance(RepositoryEvidence evidence) {
        return switch (evidence.layer()) {
            case GIT_HISTORY, COMMIT_DIFF -> 100;
            case ROADMAP, PREVIOUS_ANALYSIS -> 85;
            case ADR -> 65;
            case VALIDATED_INSIGHT -> 55;
            default -> 20;
        };
    }

    private int recency(Instant occurredAt, Instant analysisCreatedAt) {
        if (occurredAt == null || analysisCreatedAt == null
                || occurredAt.isAfter(analysisCreatedAt)) return 0;
        long days = Duration.between(occurredAt, analysisCreatedAt).toDays();
        if (days <= 7) return 100;
        if (days <= 30) return 80;
        if (days <= 90) return 60;
        if (days <= 365) return 30;
        return 10;
    }

    private int confidence(RepositoryEvidence evidence) {
        return switch (evidence.provenance().sourceType()) {
            case "GIT" -> 100;
            case "DETERMINISTIC_EXTRACTION" -> 95;
            case "CORE_ANALYSIS" -> 90;
            case "CORE_KNOWLEDGE" ->
                    evidence.layer() == RepositoryContextLayer.VALIDATED_INSIGHT
                            ? 100 : 85;
            default -> 60;
        };
    }

    private int guidanceRelevance(UserGuidance guidance, String candidate) {
        if (guidance == null || guidance.isEmpty()) return 0;
        String query = String.join(" ", guidance.priorities()) + " "
                + Objects.toString(guidance.focus(), "") + " "
                + Objects.toString(guidance.outputContext(), "");
        Set<String> terms = normalizedTerms(query);
        String normalized = candidate.toLowerCase(Locale.ROOT);
        return (int) Math.min(100,
                terms.stream().filter(normalized::contains).count() * 25);
    }

    private int weightedScore(
            Map<EvidenceCriterion, Integer> criteria,
            Map<EvidenceCriterion, Integer> weights
    ) {
        int totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();
        if (totalWeight <= 0) throw new IllegalArgumentException(
                "Context Profile must define positive Evidence weights");
        int weighted = criteria.entrySet().stream()
                .mapToInt(entry -> entry.getValue()
                        * weights.getOrDefault(entry.getKey(), 0)).sum();
        return (int) Math.round((double) weighted / totalWeight);
    }

    private Set<String> normalizedTerms(String value) {
        return Arrays.stream(value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(term -> term.length() >= 3)
                .collect(java.util.stream.Collectors.toCollection(
                        java.util.LinkedHashSet::new));
    }

    private boolean containsAny(String value, String... terms) {
        return Arrays.stream(terms).anyMatch(value::contains);
    }
}
