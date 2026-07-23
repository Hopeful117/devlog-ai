package com.hopeful117.devlogai.repositorycontext.collector;

import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class EvidenceFactory {
    public RepositoryEvidence create(
            ContextRequestMetadata metadata,
            RepositoryContextLayer layer,
            String kind,
            String reference,
            String summary,
            Instant occurredAt,
            int baseRelevance,
            List<String> relatedReferences,
            String repositoryLocation,
            String originatingFile,
            String identifier,
            int maximumSummaryCharacters
    ) {
        String bounded = bound(summary, maximumSummaryCharacters);
        int estimatedTokens = Math.max(1,
                (bounded.length() + reference.length() + 3) / 4);
        return new RepositoryEvidence(layer, kind, reference, bounded, occurredAt,
                baseRelevance, relatedReferences,
                new RepositoryEvidence.EvidenceProvenance(
                        metadata.sourceType(), repositoryLocation, originatingFile, identifier),
                Map.of("collectorId", metadata.collectorId(),
                        "collectorVersion", metadata.collectorVersion()),
                estimatedTokens, List.of("COLLECTOR_BASE_SCORE"));
    }

    private String bound(String value, int maximum) {
        String normalized = Objects.toString(value, "")
                .replaceAll("\\s+", " ").strip();
        return normalized.length() <= maximum ? normalized
                : normalized.substring(0, maximum - 3) + "...";
    }

    public record ContextRequestMetadata(
            String collectorId,
            String collectorVersion,
            String sourceType
    ) {
    }
}
