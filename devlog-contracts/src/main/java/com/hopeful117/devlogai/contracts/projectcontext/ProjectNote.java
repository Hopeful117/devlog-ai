package com.hopeful117.devlogai.contracts.projectcontext;

import java.time.Instant;
import java.util.UUID;

public record ProjectNote(
        UUID id,
        String type,
        String title,
        String contentMarkdown,
        String status,
        Instant updatedAt

) {
}
