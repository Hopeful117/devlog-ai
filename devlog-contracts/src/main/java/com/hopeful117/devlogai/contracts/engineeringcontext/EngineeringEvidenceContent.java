package com.hopeful117.devlogai.contracts.engineeringcontext;

/**
 * Bounded file content attached to a selected evidence item by the repository
 * context content enricher. Mirrors the internal enrichment result: the text is
 * the repository file content read at {@code revision}.
 */
public record EngineeringEvidenceContent(

        String status,
        String text,
        String reason,
        String revision
) {
}
