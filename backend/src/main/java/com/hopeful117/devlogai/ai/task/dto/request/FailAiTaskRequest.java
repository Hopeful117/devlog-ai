package com.hopeful117.devlogai.ai.task.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FailAiTaskRequest(
        @NotBlank @Size(max = 100) String failureCode,
        @NotBlank @Size(max = 5000) String failureMessage
) {
}
