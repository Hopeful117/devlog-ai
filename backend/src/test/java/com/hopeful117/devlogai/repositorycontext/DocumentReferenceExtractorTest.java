package com.hopeful117.devlogai.repositorycontext;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentReferenceExtractorTest {

    private final DocumentReferenceExtractor extractor = new DocumentReferenceExtractor();

    @Test
    void extractsAdrReferences() {
        String content = """
                # Story 0118

                This implements the design from ADR-063 and uses ADR-006 for trust.
                """;

        DocumentReferenceExtractor.ExtractedReferences result = extractor.extract(content);

        Set<String> adrIds = result.adrNumbers();
        assertTrue(adrIds.contains("063"));
        assertTrue(adrIds.contains("006"));
        assertEquals(2, adrIds.size());
    }

    @Test
    void extractsStoryReferences() {
        String content = """
                # Story 0118

                Depends on Story 0117 and builds on Story 0116.
                """;

        DocumentReferenceExtractor.ExtractedReferences result = extractor.extract(content);

        Set<String> storyIds = result.storyNumbers();
        assertTrue(storyIds.contains("0118"));
        assertTrue(storyIds.contains("0117"));
        assertTrue(storyIds.contains("0116"));
        assertEquals(3, storyIds.size());
    }

    @Test
    void extractsMarkdownLinks() {
        String content = """
                # Story 0118

                See [ADR-063](docs/decisions/ADR-063.md) for architecture.
                Also [Story 0117](docs/stories/0117-story/story.md).
                """;

        DocumentReferenceExtractor.ExtractedReferences result = extractor.extract(content);

        assertTrue(result.markdownLinks().contains("docs/decisions/ADR-063.md"));
        assertTrue(result.markdownLinks().contains("docs/stories/0117-story/story.md"));
    }

    @Test
    void extractsRoadmapReferences() {
        String content = """
                # Story 0118

                This is part of the [Phase 2](docs/roadmap.md) roadmap.
                """;

        DocumentReferenceExtractor.ExtractedReferences result = extractor.extract(content);

        assertTrue(result.hasRoadmapReference());
    }

    @Test
    void returnsEmptyForNullInput() {
        DocumentReferenceExtractor.ExtractedReferences result = extractor.extract(null);
        assertTrue(result.adrNumbers().isEmpty());
        assertTrue(result.storyNumbers().isEmpty());
    }

    @Test
    void returnsEmptyForEmptyInput() {
        DocumentReferenceExtractor.ExtractedReferences result = extractor.extract("");
        assertTrue(result.adrNumbers().isEmpty());
        assertTrue(result.storyNumbers().isEmpty());
    }

    @Test
    void deduplicatesReferences() {
        String content = """
                ADR-063 is mentioned. ADR-063 again.
                """;

        DocumentReferenceExtractor.ExtractedReferences result = extractor.extract(content);

        assertEquals(1, result.adrNumbers().size());
    }

    @Test
    void handlesCaseSensitiveAdr() {
        String content = """
                ADR-063 and ADR-006 and ADR-063.
                """;

        DocumentReferenceExtractor.ExtractedReferences result = extractor.extract(content);

        assertEquals(2, result.adrNumbers().size());
    }
}
