package com.hopeful117.devlogai.engineeringcontext;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;

import java.util.List;
import java.util.UUID;

public interface EngineeringContextFacade {
    EngineeringContext getEngineeringContext(
            String projectSlug,
            String intent,
            List<String> files,
            UUID storyId
    );

    default CanonicalEngineeringContext getCanonicalEngineeringContext(
            String projectSlug, String intent, List<String> files, UUID storyId) {
        EngineeringContext context = getEngineeringContext(projectSlug, intent, files, storyId);
        var refs = context.evidence() == null ? List.<String>of() : context.evidence().stream()
                .map(e -> e.reference()).filter(java.util.Objects::nonNull).toList();
        String digest = context.metadata() == null ? null : context.metadata().contextDigest();
        return new CanonicalEngineeringContext(context, digest, "engineering-context-v1", storyId, refs);
    }
}