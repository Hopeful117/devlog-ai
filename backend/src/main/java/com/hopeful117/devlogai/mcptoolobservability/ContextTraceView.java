package com.hopeful117.devlogai.mcptoolobservability;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only projection of a DevLog context trace correlated to an MCP operation.
 * Contains metadata only — no raw prompt content or LLM output.
 */
public record ContextTraceView(
        // ── Operation metadata ───────────────────────────────────────────
        String instanceId,
        String operationId,
        String toolName,
        String status,
        Instant startedAt,
        Instant completedAt,
        Integer durationMs,
        // ── Correlation ───────────────────────────────────────────────────
        UUID aiTaskId,
        // ── Request ───────────────────────────────────────────────────────
        String projectSlug,
        UUID storyId,
        String intent,
        List<String> files,
        // ── Context metadata ──────────────────────────────────────────────
        String contextDigest,
        String projectionDigest,
        String freshnessState,
        String freshnessRevision,
        // ── Sources ───────────────────────────────────────────────────────
        List<SourceRef> sources,
        // ── Accounting ────────────────────────────────────────────────────
        Map<String, Object> accounting,
        // ── Diagnostics ───────────────────────────────────────────────────
        String failureCode,
        String failureMessage,
        String investigationStopReason,
        boolean contextAvailable
) {
    public record SourceRef(String type, String id, String title, String summary) {}
}
