package com.hopeful117.devlogai.contracts.engineeringcontext;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Freshness declaration attached to an engineering context response
 * (ADR-062): which repository revision was observed, which revision the
 * DevLog knowledge baseline represents, and where those disagree. Purely
 * declarative — computed by the backend, never by MCP clients.
 */
public record EngineeringContextFreshness(
        String status,
        String repositoryRevision,
        String contextRevision,
        List<SourceFreshness> sources
) {

    public static final String STATUS_CURRENT = "CURRENT";
    public static final String STATUS_STALE = "STALE";
    public static final String STATUS_NO_BASELINE = "NO_BASELINE";
    public static final String STATUS_UNKNOWN = "UNKNOWN";
    public static final String STATUS_PARTIALLY_FRESH = "PARTIALLY_FRESH";

    public EngineeringContextFreshness {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    /**
     * Per-source persisted freshness state. {@code observedRevision} is the
     * latest repository revision actually observed for that source;
     * {@code contextRevision} is the revision represented by its latest
     * comparable knowledge baseline.
     */
    public record SourceFreshness(
            UUID sourceId,
            String name,
            String status,
            String guidance,
            String observedRevision,
            String contextRevision,
            Instant checkedAt
    ) {
    }
}
