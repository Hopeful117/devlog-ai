package com.hopeful117.devlogai.projectstate.dto.inner;

import com.hopeful117.devlogai.story.entity.StoryStatus;

import java.util.UUID;

public record StorySummary(
        UUID id,
        Integer number,
        String title,
        StoryStatus status
) {
}
