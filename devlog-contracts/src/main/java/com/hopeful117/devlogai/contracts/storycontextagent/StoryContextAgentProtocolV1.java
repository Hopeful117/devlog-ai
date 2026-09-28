package com.hopeful117.devlogai.contracts.storycontextagent;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Shared wire identities for the Story Context Agent protocol v1. */
public final class StoryContextAgentProtocolV1 {
    public static final String PROTOCOL_VERSION = "story-context-agent-protocol/v1";
    public static final String PROJECTION_VERSION = "sca/v1";
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private StoryContextAgentProtocolV1() { }

    public record Scope(String projectSlug, UUID storyId, String intent, java.util.List<String> files) {
        public Scope {
            if (projectSlug == null || projectSlug.isBlank() || intent == null || intent.isBlank()) {
                throw new IllegalArgumentException("projectSlug and intent are required");
            }
            files = files == null ? java.util.List.of() : java.util.List.copyOf(files);
            if (files.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("files must not contain null");
            }
        }
    }

    public record Accounting(int candidateCount, int selectedCount, int discardedCount,
                             int usedTokens, int budget, boolean truncated,
                             java.util.List<Map<String, Object>> warnings) {
        public Accounting {
            if (candidateCount < 0 || selectedCount < 0 || discardedCount < 0
                    || usedTokens < 0 || budget < 0 || usedTokens > budget) {
                throw new IllegalArgumentException("accounting values are outside protocol bounds");
            }
            warnings = warnings == null ? java.util.List.of() : java.util.List.copyOf(warnings);
        }
    }

    public record TaskIdentity(UUID aiTaskId, UUID snapshotId, String contextDigest,
                               String projectionDigest, String projectionVersion) {
        public TaskIdentity {
            if (aiTaskId == null || snapshotId == null || !aiTaskId.equals(snapshotId)) {
                throw new IllegalArgumentException("snapshotId must equal aiTaskId");
            }
            requireDigest(contextDigest, "contextDigest");
            requireDigest(projectionDigest, "projectionDigest");
            if (!PROJECTION_VERSION.equals(projectionVersion)) {
                throw new IllegalArgumentException("unsupported projection version");
            }
        }
    }

    public static void requireDigest(String digest, String name) {
        if (digest == null || !SHA256.matcher(digest).matches()) {
            throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        }
    }

    public static void requireSnapshotIdentity(Map<String, Object> snapshot, UUID aiTaskId,
                                               String contextDigest, String projectionDigest) {
        if (snapshot == null || !java.util.Objects.equals(aiTaskId, snapshot.get("aiTaskId"))
                || !java.util.Objects.equals(aiTaskId, snapshot.get("snapshotId"))) {
            throw new IllegalArgumentException("snapshotId must equal aiTaskId");
        }
        if (!java.util.Objects.equals(contextDigest, snapshot.get("contextDigest"))
                || !java.util.Objects.equals(projectionDigest, snapshot.get("projectionDigest"))) {
            throw new IllegalArgumentException("snapshot digest identities do not match the task");
        }
    }
}
