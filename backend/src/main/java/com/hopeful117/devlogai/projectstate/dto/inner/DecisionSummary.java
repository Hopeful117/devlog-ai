package com.hopeful117.devlogai.projectstate.dto.inner;

import java.time.Instant;
import java.util.UUID;

public record DecisionSummary(
        UUID id,
        String title,
        String choice,
        Instant createdAt
) {
}
