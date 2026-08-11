package com.hopeful117.devlogai.projectstate.dto.inner;

import java.time.Instant;
import java.util.UUID;

public record CommitSummary(
        UUID id,
        String commitHash,
        String subject,
        Instant committedAt,
        int filesChanged
) {
}
