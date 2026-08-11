package com.hopeful117.devlogai.timeline.dto;

import java.util.List;
import java.util.UUID;

public record TimelineResponse(
        UUID projectId,
        String projectName,
        List<TimelineEntry> entries
) {
}