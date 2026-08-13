package com.hopeful117.devlogai.projectstate.dto.inner;

import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputStatus;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputType;

import java.time.Instant;
import java.util.UUID;

public record HumanContextInputSummary(
        UUID id,
        ProjectHumanContextInputType type,
        String title,
        String contentMarkdown,
        ProjectHumanContextInputStatus status,
        Instant updatedAt
) {
}
