package com.hopeful117.devlogai.contracts.engineeringcontext;

import java.util.UUID;

/**
 * Single source of truth for the DevLog MCP resource URI space exposed by the
 * mcp-server resource templates:
 *
 * <pre>
 * devlog://projects/{projectSlug}/decisions/{decisionId}
 * devlog://projects/{projectSlug}/insights/{insightId}
 * devlog://projects/{projectSlug}/stories/{storyId}
 * devlog://projects/{projectSlug}/engineering-events/{eventId}
 * devlog://projects/{projectSlug}/commits/{commitSha}
 * </pre>
 *
 * Pure deterministic string construction: no I/O, no business logic. Used by
 * the backend contract mapping layer; the mcp-server keeps its declarative
 * templates in sync through tests.
 */
public final class DevlogResourceUriFactory {

    private static final String PROJECTS = "devlog://projects";

    private DevlogResourceUriFactory() {
    }

    public static String projects() {
        return PROJECTS;
    }

    public static String decision(String projectSlug, UUID decisionId) {
        return artifact(projectSlug, "decisions", requireId(decisionId, "decision"));
    }

    public static String insight(String projectSlug, UUID insightId) {
        return artifact(projectSlug, "insights", requireId(insightId, "insight"));
    }

    public static String story(String projectSlug, UUID storyId) {
        return artifact(projectSlug, "stories", requireId(storyId, "story"));
    }

    public static String engineeringEvent(String projectSlug, UUID eventId) {
        return artifact(projectSlug, "engineering-events",
                requireId(eventId, "engineering event"));
    }

    /**
     * Commit context resource. The SHA must be a full 40 or 64 character
     * hexadecimal Git object identifier; it is normalized to lower case.
     */
    public static String commit(String projectSlug, String commitSha) {
        requireSlug(projectSlug);
        if (commitSha == null || !commitSha.matches("[0-9a-fA-F]{40}|[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException(
                    "Invalid commit SHA: '%s'".formatted(commitSha));
        }
        return PROJECTS + "/" + projectSlug.strip() + "/commits/"
                + commitSha.toLowerCase();
    }

    private static String artifact(String projectSlug, String segment, String id) {
        return PROJECTS + "/" + requireSlug(projectSlug) + "/" + segment + "/" + id;
    }

    private static String requireSlug(String projectSlug) {
        if (projectSlug == null || projectSlug.isBlank()
                || !projectSlug.strip().matches("[A-Za-z0-9._~\\-]+")) {
            throw new IllegalArgumentException(
                    "Invalid project slug: '%s'".formatted(projectSlug));
        }
        return projectSlug.strip();
    }

    private static String requireId(UUID id, String kind) {
        if (id == null) {
            throw new IllegalArgumentException("Invalid %s identifier: null"
                    .formatted(kind));
        }
        return id.toString();
    }
}
