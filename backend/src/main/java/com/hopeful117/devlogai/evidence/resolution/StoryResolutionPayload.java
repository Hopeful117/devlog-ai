package com.hopeful117.devlogai.evidence.resolution;

import com.hopeful117.devlogai.story.entity.StoryStatus;
import java.time.Instant;
import java.util.UUID;

public record StoryResolutionPayload(
        UUID id, UUID projectId, Integer storyNumber, String title, String storyPath,
        String baseCommit, String targetCommit, StoryStatus status, Instant createdAt,
        Instant updatedAt, Instant completedAt
) implements EvidenceResolutionPayload {
}
