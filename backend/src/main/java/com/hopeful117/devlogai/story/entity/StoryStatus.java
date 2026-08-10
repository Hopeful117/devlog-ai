package com.hopeful117.devlogai.story.entity;

/**
 * Lifecycle status for an Engineering Story.
 * The status transitions linearly: REGISTERED → IN_PROGRESS → COMPLETED.
 */
public enum StoryStatus {
    REGISTERED,
    IN_PROGRESS,
    COMPLETED
}