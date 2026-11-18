package com.hopeful117.devlogai.repositorycontext.collector;

import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceFactoryTest {

    private final EvidenceFactory factory = new EvidenceFactory();

    private EvidenceFactory.ContextRequestMetadata metadata() {
        return new EvidenceFactory.ContextRequestMetadata("test-collector", "v1", "GIT");
    }

    private EvidenceFactory.EvidenceInput input(String summary) {
        return new EvidenceFactory.EvidenceInput(
                RepositoryContextLayer.GIT_HISTORY, "COMMIT", "ref-1", summary,
                Instant.EPOCH, List.of(), "location", "file.java", "id-1");
    }

    @Test
    void summaryWithinLimit_noTruncatedFlag() {
        RepositoryEvidence evidence = factory.create(metadata(), input("short summary"), 500);

        assertThat(evidence.extractionMetadata()).doesNotContainKey("summaryTruncated");
        assertThat(evidence.summary()).isEqualTo("short summary");
    }

    @Test
    void summaryExceedsLimit_truncatedFlagSet() {
        String longSummary = "a".repeat(1000);

        RepositoryEvidence evidence = factory.create(metadata(), input(longSummary), 100);

        assertThat(evidence.extractionMetadata()).containsEntry("summaryTruncated", "true");
        assertThat(evidence.summary()).endsWith("...");
        assertThat(evidence.summary()).hasSize(100);
    }

    @Test
    void nullSummary_noTruncatedFlag() {
        RepositoryEvidence evidence = factory.create(metadata(), input(null), 500);

        assertThat(evidence.extractionMetadata()).doesNotContainKey("summaryTruncated");
        assertThat(evidence.summary()).isEmpty();
    }

    @Test
    void alwaysContainsCollectorMetadata() {
        RepositoryEvidence evidence = factory.create(metadata(), input("summary"), 500);

        assertThat(evidence.extractionMetadata())
                .containsEntry("collectorId", "test-collector")
                .containsEntry("collectorVersion", "v1");
    }
}
