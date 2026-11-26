package com.hopeful117.devlogai.repositorycontext.collector;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Story 0243 (R5): deterministic relevance of an explicitly requested file to
 * the request objective, used to order files inside a broad requested scope.
 *
 * <p>Query terms are the objective words minus stop words, reduced to a short
 * prefix stem so that close forms match (analysis/analyze, persist/persisted).
 * A file scores on the stems found in its file name (strong signal) and on the
 * identifier stems found in its content (weaker signal). No embedding, no
 * external service: identical inputs always give identical scores.
 */
final class RequestedScopeRelevance {

    static final int MAX_SCORE = 100;
    private static final int STEM_LENGTH = 5;
    private static final int FILE_NAME_WEIGHT = 15;
    private static final int CONTENT_WEIGHT = 8;
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "from", "with", "that", "this", "which", "what", "when", "where",
            "how", "does", "did", "are", "was", "were", "each", "into", "onto", "over", "under",
            "between", "should", "would", "could", "can", "its", "their", "there", "these", "those",
            "who", "whom", "why", "has", "have", "had", "not", "but", "all", "any", "own", "side",
            "les", "des", "une", "est", "pour", "dans", "avec", "sur", "par", "qui", "que", "quoi",
            "comment", "pourquoi", "engineering", "src", "main",
            "java", "com", "app", "docs", "test", "tests");

    private RequestedScopeRelevance() {
    }

    static Set<String> queryStems(String objective) {
        if (objective == null) return new LinkedHashSet<>();
        // Requested paths are appended to the objective; they are scope, not intent.
        StringBuilder words = new StringBuilder();
        for (String token : objective.split("\\s+")) {
            if (!token.contains("/") && !token.contains("\\")) words.append(token).append(' ');
        }
        return stems(words.toString(), false);
    }

    static int score(String relativePath, Set<String> queryStems, String content) {
        if (queryStems == null || queryStems.isEmpty() || relativePath == null) return 0;
        String fileName = relativePath.contains("/")
                ? relativePath.substring(relativePath.lastIndexOf('/') + 1) : relativePath;
        int fileNameMatches = matches(stems(fileName, true), queryStems);
        int contentMatches = content == null ? 0 : matches(stems(content, true), queryStems);
        return Math.min(MAX_SCORE, fileNameMatches * FILE_NAME_WEIGHT + contentMatches * CONTENT_WEIGHT);
    }

    private static int matches(Set<String> candidate, Set<String> query) {
        int count = 0;
        for (String stem : query) {
            if (candidate.contains(stem)) count++;
        }
        return count;
    }

    /** Splits words and camelCase/snake_case identifiers into lower-case prefix stems. */
    static Set<String> stems(String text, boolean identifiers) {
        Set<String> result = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) return result;
        String normalized = identifiers
                ? text.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                : text;
        for (String raw : normalized.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (raw.length() < 4 || STOP_WORDS.contains(raw) || raw.chars().allMatch(Character::isDigit)) continue;
            result.add(raw.length() > STEM_LENGTH ? raw.substring(0, STEM_LENGTH) : raw);
        }
        return result;
    }
}
