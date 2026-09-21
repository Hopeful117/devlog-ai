package com.hopeful117.devlogai.historicalrelationship;

import com.hopeful117.devlogai.engineeringevent.GitCommitIdentity;

public record CommitHash(String value) {
    public CommitHash {
        value = GitCommitIdentity.normalize(value)
                .orElseThrow(() -> new IllegalArgumentException("Invalid commit hash"));
    }
}
