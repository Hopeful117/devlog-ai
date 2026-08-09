package com.hopeful117.devlogai.projectfreshness;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

final class GitCommitIdentity {
    private static final Pattern COMPLETE = Pattern.compile("(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})");

    private GitCommitIdentity() { }

    static Optional<String> normalize(Object value) {
        if (!(value instanceof String text)) return Optional.empty();
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return COMPLETE.matcher(normalized).matches()
                ? Optional.of(normalized) : Optional.empty();
    }
}
