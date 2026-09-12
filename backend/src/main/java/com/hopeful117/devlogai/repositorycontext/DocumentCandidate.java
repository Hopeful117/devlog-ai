package com.hopeful117.devlogai.repositorycontext;

import java.time.Instant;

/**
 * Document candidate for budget prioritization.
 * Immutable record capturing all metadata needed for deterministic prioritization.
 */
public record DocumentCandidate(
        String kind,
        String path,
        String label,
        RepositoryContextLayer layer,
        String evidenceRef,
        Instant occurredAt,
        String sourceId,
        String relativePath,
        String adrNumber,      // for ADR_DOCUMENT
        DocumentStatus adrStatus, // for ADR_DOCUMENT
        int storyNumber,       // for STORY_DOCUMENT (referenced)
        boolean isMainStory,   // for STORY_DOCUMENT
        DocumentStatus adrStatusRaw // raw status for parsing
) {
    public int parsedAdrNumber() {
        if (adrNumber == null || adrNumber.isBlank()) return Integer.MAX_VALUE;
        try {
            return Integer.parseInt(adrNumber);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    public int storyNumber() {
        return storyNumber;
    }

    public boolean isMainStory() {
        return isMainStory;
    }

    public DocumentStatus adrStatus() {
        return adrStatus != null ? adrStatus : DocumentStatus.UNKNOWN;
    }
}