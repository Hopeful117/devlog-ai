package com.hopeful117.devlogai.story.dto.response;

import com.hopeful117.devlogai.story.entity.StoryStatus;

import java.time.Instant;
import java.util.UUID;

public record EngineeringStoryResponse(
        UUID id,
        UUID projectId,
        Integer storyNumber,
        String title,
        String storyPath,
        String baseCommit,
        String targetCommit,
        StoryStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {}