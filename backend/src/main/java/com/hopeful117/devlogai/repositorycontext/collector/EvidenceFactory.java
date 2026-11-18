package com.hopeful117.devlogai.repositorycontext.collector;

import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceScore;

@Component
public class EvidenceFactory {
    public RepositoryEvidence create(
            ContextRequestMetadata metadata,
            EvidenceInput input,
            int maximumSummaryCharacters
    ) {
        String normalized = Objects.toString(input.summary(), "")
                .replaceAll("\\s+", " ").strip();
        String bounded = normalized.length() <= maximumSummaryCharacters ? normalized
                : normalized.substring(0, maximumSummaryCharacters - 3) + "...";
        boolean summaryTruncated = bounded.endsWith("...")
                && normalized.length() > maximumSummaryCharacters;
        int estimatedTokens = Math.max(1,
                (bounded.length() + input.reference().length() + 3) / 4);
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("collectorId", metadata.collectorId());
        meta.put("collectorVersion", metadata.collectorVersion());
        if (summaryTruncated) meta.put("summaryTruncated", "true");
        return new RepositoryEvidence(input.layer(), input.kind(), input.reference(), bounded,
                input.occurredAt(), EvidenceScore.unscored(), input.relatedReferences(),
                new RepositoryEvidence.EvidenceProvenance(
                        metadata.sourceType(), input.repositoryLocation(),
                        input.originatingFile(), input.identifier()),
                Map.copyOf(meta),
                estimatedTokens, List.of("COLLECTED_NOT_RANKED"));
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

    public record EvidenceInput(
            RepositoryContextLayer layer,
            String kind,
            String reference,
            String summary,
            Instant occurredAt,
            List<String> relatedReferences,
            String repositoryLocation,
            String originatingFile,
            String identifier
    ) {
    }
}
