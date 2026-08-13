package com.hopeful117.devlogai.projectcontextinput.dto.response;

import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputStatus;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputType;

import java.time.Instant;
import java.util.UUID;

public record ProjectHumanContextInputResponse(
        UUID id,
        UUID projectId,
        String title,
        String contentMarkdown,
        ProjectHumanContextInputType type,
        ProjectHumanContextInputStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
