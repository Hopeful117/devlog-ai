package com.hopeful117.devlogai.projectcontext;

import java.time.Instant;
import java.util.UUID;

public record EngineeringStoryContext(
        ProjectContextSnapshot projectContext,
        Instant generatedAt,
        UUID projectId
) {
}