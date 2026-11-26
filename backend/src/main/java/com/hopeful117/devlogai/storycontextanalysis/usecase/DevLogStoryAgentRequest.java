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
    String idempotencyKey,

    @Nullable
    String revision
) {
    private static final java.util.regex.Pattern COMMIT = java.util.regex.Pattern.compile("[0-9a-fA-F]{40}|[0-9a-fA-F]{64}");

    /** Story 0242: a request without a pinned revision. */
    public DevLogStoryAgentRequest(String projectSlug, UUID storyId, String intent, String question,
                                   List<String> files, Map<String, Object> guidance, String idempotencyKey) {
        this(projectSlug, storyId, intent, question, files, guidance, idempotencyKey, null);
    }

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
        if (revision != null && revision.isBlank()) {
            revision = null;
        }
        if (revision != null) {
            revision = revision.strip();
            // Story 0242: only an exact commit identity can pin an execution.
            if (!COMMIT.matcher(revision).matches()) {
                throw new IllegalArgumentException("revision must be a full commit identity");
            }
            revision = revision.toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
