package com.hopeful117.devlogai.engineeringevent;

import java.util.Locale;
import java.util.Optional;

public final class GitCommitIdentity {
    private GitCommitIdentity() { }

    public static Optional<String> normalize(String value) {
        if (value == null) return Optional.empty();
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ((normalized.length() != 40 && normalized.length() != 64)
                || !normalized.matches("[0-9a-f]+")) return Optional.empty();
        return Optional.of(normalized);
    }
}
