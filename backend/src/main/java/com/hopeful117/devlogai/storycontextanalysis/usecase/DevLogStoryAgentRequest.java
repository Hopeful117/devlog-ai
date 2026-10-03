package com.hopeful117.devlogai.storycontextanalysis.usecase;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DevLogStoryAgentRequest(
    @NotBlank
    String projectSlug,

    @Nullable
    UUID storyId,

    @NotBlank
    String intent,

    @NotBlank
    String question,

    @NotNull
    List<@NotBlank String> files,

    @Nullable
    Map<String,Object>guidance,

    @Nullable
    String idempotencyKey
) {
    public DevLogStoryAgentRequest {
        requireNonBlank(projectSlug, "projectSlug");
        requireNonBlank(intent, "intent");
        requireNonBlank(question, "question");
        if (question.length() > 2000) {
            throw new IllegalArgumentException("question must not exceed 2000 characters");
        }
        question = question.trim();
        Objects.requireNonNull(files, "files");

        files = List.copyOf(files);
        files.forEach(file -> requireNonBlank(file, "files must not contain blank paths"));
        guidance = guidance == null ? Map.of() : Map.copyOf(guidance);
        idempotencyKey = idempotencyKey == null || idempotencyKey.isBlank()
                ? null
                : idempotencyKey;
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
