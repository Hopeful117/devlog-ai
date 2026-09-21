package com.hopeful117.devlogai.historicalrelationship;

import com.hopeful117.devlogai.engineeringevent.EngineeringEventCategory;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EventCommitReference(
        UUID eventId,
        UUID sourceId,
        EngineeringEventCategory category,
        String title,
        Instant occurredAt,
        CommitReferenceRole role
) {
    public EventCommitReference {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(role, "role");
    }
}
