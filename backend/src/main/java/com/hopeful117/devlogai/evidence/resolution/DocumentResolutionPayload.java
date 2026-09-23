package com.hopeful117.devlogai.evidence.resolution;

import java.util.Objects;

public record DocumentResolutionPayload(
        String path,
        String revision,
        String content
) implements EvidenceResolutionPayload {
    public DocumentResolutionPayload {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(content, "content");
    }
}
