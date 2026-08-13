package com.hopeful117.devlogai.projectstate;

import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;

@Component
public class ProjectStateProposalNoiseReducer {
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "be", "both", "by", "for", "from",
            "has", "have", "in", "into", "is", "it", "its", "of", "on", "or",
            "that", "the", "their", "this", "to", "up", "using", "with"
    );

    public List<ValidatableProposal> reduce(List<ValidatableProposal> proposals) {
        if (proposals == null || proposals.isEmpty()) {
            return List.of();
        }

        List<Cluster> clusters = new ArrayList<>();
        for (ValidatableProposal proposal : proposals) {
            Cluster matchingCluster = clusters.stream()
                    .filter(cluster -> cluster.matches(proposal))
                    .findFirst()
                    .orElse(null);
            if (matchingCluster == null) {
                clusters.add(new Cluster(proposal));
                continue;
            }
            clusters.set(clusters.indexOf(matchingCluster),
                    matchingCluster.merge(proposal, this::preferRepresentative));
        }

        List<ValidatableProposal> reduced = clusters.stream()
                .map(Cluster::representative)
                .sorted(Comparator.comparing(this::sortTitle)
                        .thenComparing(ValidatableProposal::getId))
                .toList();

        Map<StrictTitleKey, ValidatableProposal> exactTitles = new LinkedHashMap<>();
        for (ValidatableProposal proposal : reduced) {
            StrictTitleKey key = StrictTitleKey.from(proposal);
            exactTitles.merge(key, proposal, this::preferRepresentative);
        }

        return exactTitles.values().stream()
                .sorted(Comparator.comparing(this::sortTitle)
                        .thenComparing(ValidatableProposal::getId))
                .toList();
    }

    private ValidatableProposal preferRepresentative(
            ValidatableProposal current,
            ValidatableProposal candidate
    ) {
        int confidenceComparison = confidence(candidate).compareTo(confidence(current));
        if (confidenceComparison != 0) {
            return confidenceComparison > 0 ? candidate : current;
        }

        int currentScore = representativeScore(current);
        int candidateScore = representativeScore(candidate);
        if (candidateScore != currentScore) {
            return candidateScore > currentScore ? candidate : current;
        }

        return candidate.getId().compareTo(current.getId()) < 0 ? candidate : current;
    }

    private int representativeScore(ValidatableProposal proposal) {
        int score = 0;
        if (!text(proposal, "title").isBlank()) {
            score += 1000;
        }
        score += Math.min(text(proposal, "summary").length(), 500);
        return score;
    }

    private BigDecimal confidence(ValidatableProposal proposal) {
        return proposal.getConfidence() == null ? BigDecimal.ZERO : proposal.getConfidence();
    }

    private String sortTitle(ValidatableProposal proposal) {
        String title = text(proposal, "title");
        if (!title.isBlank()) {
            return title;
        }
        String insightType = text(proposal, "insightType");
        if (!insightType.isBlank()) {
            return insightType;
        }
        return proposal.getType().name();
    }

    private static String text(ValidatableProposal proposal, String key) {
        if (proposal.getPayload() == null) {
            return "";
        }
        Object value = proposal.getPayload().get(key);
        return value instanceof String text ? normalize(text) : "";
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private record Cluster(
            ReductionKey key,
            Set<String> titleTokens,
            Set<String> contentTokens,
            ValidatableProposal representative
    ) {
        private Cluster(ValidatableProposal proposal) {
            this(ReductionKey.from(proposal),
                    tokens(text(proposal, "title")),
                    tokens(text(proposal, "title") + " " + text(proposal, "summary")),
                    proposal);
        }

        private boolean matches(ValidatableProposal proposal) {
            ReductionKey candidateKey = ReductionKey.from(proposal);
            if (!key.type.equals(candidateKey.type)
                    || !key.insightType.equals(candidateKey.insightType)) {
                return false;
            }
            if (key.title.equals(candidateKey.title) || key.summary.equals(candidateKey.summary)) {
                return true;
            }

            Set<String> candidateTitleTokens = tokens(candidateKey.title);
            if (sharedTokens(titleTokens, candidateTitleTokens) >= 2
                    && overlapRatio(titleTokens, candidateTitleTokens) >= 0.8d) {
                return true;
            }

            Set<String> candidateContentTokens =
                    tokens(candidateKey.title + " " + candidateKey.summary);
            return sharedTokens(contentTokens, candidateContentTokens) >= 4
                    && overlapRatio(contentTokens, candidateContentTokens) >= 0.6d;
        }

        private Cluster merge(
                ValidatableProposal candidate,
                BinaryOperator<ValidatableProposal> chooser
        ) {
            return new Cluster(key, titleTokens, contentTokens,
                    chooser.apply(representative, candidate));
        }

        private static int sharedTokens(Set<String> left, Set<String> right) {
            if (left.isEmpty() || right.isEmpty()) {
                return 0;
            }
            Set<String> shared = new HashSet<>(left);
            shared.retainAll(right);
            return shared.size();
        }

        private static double overlapRatio(Set<String> left, Set<String> right) {
            int shared = sharedTokens(left, right);
            if (shared == 0) {
                return 0d;
            }
            return (double) shared / Math.min(left.size(), right.size());
        }

        private static Set<String> tokens(String value) {
            if (value == null || value.isBlank()) {
                return Set.of();
            }
            Set<String> tokens = new LinkedHashSet<>();
            Arrays.stream(normalize(value).split("[^a-z0-9]+"))
                    .filter(token -> token.length() >= 3)
                    .filter(token -> !STOP_WORDS.contains(token))
                    .forEach(tokens::add);
            return tokens;
        }
    }

    private record ReductionKey(String type, String insightType, String title, String summary) {
        private static ReductionKey from(ValidatableProposal proposal) {
            return new ReductionKey(proposal.getType().name(), text(proposal, "insightType"),
                    text(proposal, "title"), text(proposal, "summary"));
        }
    }

    private record StrictTitleKey(String type, String insightType, String title) {
        private static StrictTitleKey from(ValidatableProposal proposal) {
            return new StrictTitleKey(proposal.getType().name(), text(proposal, "insightType"),
                    text(proposal, "title"));
        }
    }
}
