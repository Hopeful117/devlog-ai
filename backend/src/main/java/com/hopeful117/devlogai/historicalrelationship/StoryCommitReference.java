package com.hopeful117.devlogai.historicalrelationship;

import com.hopeful117.devlogai.story.entity.StoryStatus;

import java.util.Objects;
import java.util.UUID;

public record StoryCommitReference(
        UUID storyId,
        Integer storyNumber,
        String storyPath,
        StoryStatus status,
        CommitReferenceRole role
) {
    public StoryCommitReference {
        Objects.requireNonNull(storyId, "storyId");
        Objects.requireNonNull(storyNumber, "storyNumber");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(role, "role");
    }
}
