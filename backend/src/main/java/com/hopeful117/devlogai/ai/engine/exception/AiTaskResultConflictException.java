package com.hopeful117.devlogai.ai.engine.exception;

import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import lombok.Getter;

@Getter
public class AiTaskResultConflictException extends RuntimeException {

    private final String code;
    private final AiTaskStatus currentStatus;

    public AiTaskResultConflictException(
            String code,
            AiTaskStatus currentStatus,
            String message
    ) {
        super(message);
        this.code = code;
        this.currentStatus = currentStatus;
    }
}
