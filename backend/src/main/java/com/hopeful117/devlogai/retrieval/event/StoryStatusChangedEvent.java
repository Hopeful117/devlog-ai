package com.hopeful117.devlogai.retrieval.event;

import java.util.UUID;

public record StoryStatusChangedEvent(UUID projectId, UUID storyId) {}
