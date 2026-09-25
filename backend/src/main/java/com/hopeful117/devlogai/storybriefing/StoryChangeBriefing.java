package com.hopeful117.devlogai.storybriefing;

import com.hopeful117.devlogai.ai.reference.AiReference;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A deterministic, descriptive view of one canonical story context. */
public record StoryChangeBriefing(
        String contractVersion, UUID storyId, String contextDigest, String projectionDigest,
        String groundingStatus, String description, List<Change> changes, List<String> warnings,
        Map<String, Object> snapshot) {
    public StoryChangeBriefing {
        if (contractVersion == null || contractVersion.isBlank() || storyId == null
                || contextDigest == null || contextDigest.isBlank()
                || projectionDigest == null || projectionDigest.isBlank()) {
            throw new IllegalArgumentException("briefing identity is required");
        }
        if (!"NOT_ESTABLISHED".equals(groundingStatus)) {
            throw new IllegalArgumentException("briefing must remain non-causal");
        }
        changes = changes == null ? List.of() : List.copyOf(changes);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        snapshot = snapshot == null ? Map.of() : Map.copyOf(snapshot);
    }
    public record Change(AiReference reference, String kind, String summary,
                         String occurredAt, Map<String, String> provenance) {
        public Change {
            if (reference == null || kind == null || kind.isBlank()) {
                throw new IllegalArgumentException("typed change reference and kind are required");
            }
            provenance = provenance == null ? Map.of() : Map.copyOf(provenance);
        }
    }
}
