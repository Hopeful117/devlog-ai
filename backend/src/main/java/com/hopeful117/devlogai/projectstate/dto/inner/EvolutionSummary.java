package com.hopeful117.devlogai.projectstate.dto.inner;

import com.hopeful117.devlogai.engineeringevent.EngineeringEventCategory;

import java.time.Instant;
import java.util.UUID;

public record EvolutionSummary(
        UUID id,
        EngineeringEventCategory category,
        String title,
        String baseCommit,
        String targetCommit,
        Instant occurredAt
) {
}