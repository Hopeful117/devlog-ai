package com.hopeful117.devlogai.ai.engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PromptExecutionMetadata(
        @NotBlank @Size(max = 100) String promptVersion,
        @NotBlank @Size(max = 100) String provider,
        @NotBlank @Size(max = 255) String modelIdentifier,
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String promptContentDigest,
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String contextDigest
) {}
