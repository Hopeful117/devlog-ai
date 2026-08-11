package com.hopeful117.devlogai.timeline.dto;

import java.time.Instant;
import java.util.UUID;

public record TimelineEntry(
        UUID id,
        TimelineEntryType type,
        Instant timestamp,
        String title,
        String detail
) {
}