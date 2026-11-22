package com.hopeful117.devlogai.ai.task.entity;

public enum AiTaskStatus {
    CREATED,
    SUBMITTED,
    PROCESSING,
    COMPLETED,
    FAILED,
    TIMED_OUT;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == TIMED_OUT;
    }
}
