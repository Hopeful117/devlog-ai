package com.hopeful117.devlogai.ai.engine.dto;

import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;

import java.util.UUID;

public record AiTaskResultAcknowledgement(
        UUID correlationId,
        boolean acknowledged,
        boolean duplicate,
        AiTaskStatus taskStatus,
        long proposalCount
) {
}
