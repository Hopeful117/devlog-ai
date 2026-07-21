package com.hopeful117.devlogai.ai.task.dto.request;

import jakarta.validation.constraints.Size;

public record SubmitAiTaskRequest(
        @Size(max = 255) String externalJobId
) {
}
