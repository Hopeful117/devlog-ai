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
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashMap;

@Component
public class DeterministicEvidenceRanker implements EvidenceRanker {
    static final String POLICY_VERSION = "multi-criteria-v2";

    @Override
    public List<RepositoryEvidence> rank(
            List<RepositoryEvidence> evidence,
            ContextRequest request
    ) {
        TermModel semanticTerms = termModel(
                request.intent().id() + " " + request.intent().objective(), evidence,
                request.contextPlan().precisionPolicy().maximumCommonTermPercentage());
        UserGuidance guidance = request.guidance();
        String guidanceQuery = guidance == null ? "" : String.join(" ", guidance.priorities())
                + " " + Objects.toString(guidance.focus(), "") + " "
                + Objects.toString(guidance.outputContext(), "");
        TermModel guidanceTerms = termModel(guidanceQuery, evidence,
                request.contextPlan().precisionPolicy().maximumCommonTermPercentage());
        return evidence.stream().map(value -> rank(
                        value, request, semanticTerms, guidanceTerms))
                .sorted(Comparator.comparingInt(RepositoryEvidence::relevanceScore).reversed()
                        .thenComparing(value -> value.layer().ordinal())
                        .thenComparing(RepositoryEvidence::reference))
                .toList();
    }

    private RepositoryEvidence rank(
            RepositoryEvidence evidence,
            ContextRequest request,
            TermModel semanticTerms,
            TermModel guidanceTerms
    ) {
        Map<EvidenceCriterion, Integer> criteria =
                new EnumMap<>(EvidenceCriterion.class);
        criteria.put(EvidenceCriterion.SEMANTIC_RELEVANCE,
                semanticRelevance(evidence, semanticTerms));
        criteria.put(EvidenceCriterion.ARCHITECTURAL_RELEVANCE,
                architecturalRelevance(evidence));
        criteria.put(EvidenceCriterion.HISTORICAL_RELEVANCE,
                historicalRelevance(evidence));
        criteria.put(EvidenceCriterion.RECENCY,
                recency(evidence.occurredAt(),
                        request.analysisContext().analysis().createdAt()));
        criteria.put(EvidenceCriterion.CONFIDENCE, confidence(evidence));
        criteria.put(EvidenceCriterion.USER_GUIDANCE_BOOST,
                guidanceRelevance(request.guidance(), evidence, guidanceTerms));
        int finalScore = weightedScore(criteria,
                request.contextPlan().composedWeights());
        List<String> explanations = new java.util.ArrayList<>(criteria.entrySet().stream()
                .map(entry -> entry.getKey().name() + "=" + entry.getValue()
                        + "@" + request.contextPlan().composedWeights()
                        .getOrDefault(entry.getKey(), 0))
                .toList());
        explanations.add("RANKING_POLICY:" + POLICY_VERSION);
        explanations.add("SEMANTIC_TERMS:" + semanticTerms.explain(evidence));
        if (request.guidance() != null && !request.guidance().isEmpty())
            explanations.add("GUIDANCE_TERMS:" + guidanceTerms.explain(evidence));
        EvidenceScore score = new EvidenceScore(POLICY_VERSION, criteria,
                request.contextPlan().composedWeights(), finalScore, explanations,
                new EvidenceScore.MatchStrength(
                        semanticTerms.strength(evidence),
                        request.guidance() == null || request.guidance().isEmpty()
                                ? 0 : guidanceTerms.strength(evidence)));
        return evidence.withRanking(score, explanations);
    }

    private int semanticRelevance(
            RepositoryEvidence evidence,
            TermModel terms
    ) {
        int score = terms.score(evidence);
        if (evidence.layer() == RepositoryContextLayer.CURRENT_ANALYSIS && score < 90)
            score = 90;
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

    private int guidanceRelevance(
            UserGuidance guidance,
            RepositoryEvidence evidence,
            TermModel terms
    ) {
        if (guidance == null || guidance.isEmpty()) return 0;
        return terms.score(evidence);
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

    private TermModel termModel(
            String query,
            List<RepositoryEvidence> evidence,
            int maximumCommonPercentage
    ) {
        Set<String> terms = normalizedTerms(query);
        Map<String, Integer> frequency = new LinkedHashMap<>();
        terms.forEach(term -> frequency.put(term, (int) evidence.stream()
                .map(this::searchableText).filter(value -> value.contains(term)).count()));
        return new TermModel(frequency, evidence.size(), maximumCommonPercentage);
    }

    private String searchableText(RepositoryEvidence evidence) {
        return (evidence.kind() + " " + evidence.summary() + " "
                + Objects.toString(evidence.provenance().originatingFile(), ""))
                .toLowerCase(Locale.ROOT);
    }

    private final class TermModel {
        private final Map<String, Integer> frequency;
        private final int candidateCount;
        private final int maximumCommonPercentage;

        private TermModel(Map<String, Integer> frequency, int candidateCount,
                int maximumCommonPercentage) {
            this.frequency = Map.copyOf(frequency);
            this.candidateCount = candidateCount;
            this.maximumCommonPercentage = maximumCommonPercentage;
        }

        private int score(RepositoryEvidence evidence) {
            return Math.min(100, strength(evidence));
        }

        private int strength(RepositoryEvidence evidence) {
            if (candidateCount == 0) return 0;
            String candidate = searchableText(evidence);
            return frequency.entrySet().stream()
                    .filter(entry -> candidate.contains(entry.getKey()))
                    .filter(entry -> isDiscriminating(entry.getValue()))
                    .mapToInt(entry -> contribution(entry.getValue()))
                    .sum();
        }

        private boolean isDiscriminating(int occurrences) {
            return maximumCommonPercentage == 100 || candidateCount <= 1
                    || occurrences * 100 < maximumCommonPercentage * candidateCount;
        }

        private int contribution(int occurrences) {
            if (maximumCommonPercentage == 100) return 25;
            return Math.max(1, (int) Math.round(
                    25.0 * (candidateCount - occurrences + 1) / candidateCount));
        }

        private String explain(RepositoryEvidence evidence) {
            String candidate = searchableText(evidence);
            String matched = frequency.entrySet().stream()
                    .filter(entry -> candidate.contains(entry.getKey()))
                    .filter(entry -> isDiscriminating(entry.getValue()))
                    .map(Map.Entry::getKey).sorted().collect(
                            java.util.stream.Collectors.joining(","));
            String common = frequency.entrySet().stream()
                    .filter(entry -> candidate.contains(entry.getKey()))
                    .filter(entry -> !isDiscriminating(entry.getValue()))
                    .map(Map.Entry::getKey).sorted().collect(
                            java.util.stream.Collectors.joining(","));
            return "matched=" + matched + ";common=" + common;
        }
    }

    private boolean containsAny(String value, String... terms) {
        return Arrays.stream(terms).anyMatch(value::contains);
    }
}
