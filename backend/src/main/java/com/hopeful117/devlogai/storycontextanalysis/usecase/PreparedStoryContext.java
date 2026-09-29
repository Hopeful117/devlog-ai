package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.engineeringcontext.CanonicalEngineeringContext;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.story.entity.EngineeringStory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Immutable Core-owned preparation reused by projection and task submission. */
public record PreparedStoryContext(
        String projectSlug,
        UUID storyId,
        String intent,
        List<String> files,
        Project project,
        EngineeringStory story,
        CanonicalEngineeringContext canonicalContext,
        Map<String, Object> projection,
        String projectionDigest) {
    public PreparedStoryContext {
        if (projectSlug == null || projectSlug.isBlank()) {
            throw new IllegalArgumentException("projectSlug is required");
        }
        if (intent == null || intent.isBlank()) {
            throw new IllegalArgumentException("intent is required");
        }
        if (files == null || project == null || canonicalContext == null || projection == null
                || projectionDigest == null || projectionDigest.isBlank()) {
            throw new IllegalArgumentException("prepared Story Context is incomplete");
        }
        files = List.copyOf(files);
        projection = Map.copyOf(projection);
    }
}
