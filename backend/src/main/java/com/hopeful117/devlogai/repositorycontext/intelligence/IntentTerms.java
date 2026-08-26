package com.hopeful117.devlogai.repositorycontext.intelligence;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Shared deterministic intent-term extraction (ADR-063 shared retrieval
 * primitive). Applies the same splitting rule the ranking family already uses
 * (lowercase alphanumeric tokens, minimum length 3) so bounded retrieval in
 * the adapter scores candidates against exactly the vocabulary the ranker
 * sees.
 */
public final class IntentTerms {

    private static final Pattern SPLIT = Pattern.compile("[^a-z0-9]+");
    private static final int MINIMUM_LENGTH = 3;

    private IntentTerms() {
    }

    public static List<String> extract(String text) {
        if (text == null || text.isBlank()) return List.of();
        return SPLIT.splitAsStream(text.toLowerCase(Locale.ROOT))
                .filter(token -> token.length() >= MINIMUM_LENGTH)
                .collect(Collectors.toSet())
                .stream()
                .sorted()
                .toList();
    }

    public static int matches(List<String> terms, String content) {
        if (terms.isEmpty() || content == null || content.isBlank()) return 0;
        String haystack = content.toLowerCase(Locale.ROOT);
        int matches = 0;
        for (String term : terms) {
            if (haystack.contains(term)) matches++;
        }
        return matches;
    }
}