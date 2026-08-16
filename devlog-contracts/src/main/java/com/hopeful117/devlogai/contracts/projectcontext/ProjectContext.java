package com.hopeful117.devlogai.contracts.projectcontext;

import java.util.List;
import java.util.UUID;

public record ProjectContext(
        UUID id,
        String name,
        String slug,
        String description,
        String status,
        List<ProjectNote>notes
) {
}
