package com.hopeful117.devlogai.ai.engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiTaskResultError(
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 5000) String message
) {
}
