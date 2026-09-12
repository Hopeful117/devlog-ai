package com.hopeful117.devlogai.repositorycontext;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentPriorityComparatorTest {

    private final DocumentPriorityComparator comparator = new DocumentPriorityComparator();

    @Test
    void ordersMainStoryThenAdrsThenReferencedStoriesThenRoadmapDeterministically() {
        List<DocumentCandidate> candidates = new ArrayList<>(List.of(
                roadmap(),
                story(12, false),
                adr(20, DocumentStatus.UNKNOWN),
                adr(30, DocumentStatus.ACCEPTED),
                story(119, true),
                adr(5, DocumentStatus.PROPOSED),
                story(2, false),
                adr(11, DocumentStatus.SUPERSEDED),
                adr(7, DocumentStatus.ACCEPTED)));

        candidates.sort(comparator);

        assertEquals(List.of(
                        "main-story",
                        "adr-7-ACCEPTED",
                        "adr-30-ACCEPTED",
                        "adr-11-SUPERSEDED",
                        "adr-5-PROPOSED",
                        "adr-20-UNKNOWN",
                        "story-2",
                        "story-12",
                        "roadmap"),
                candidates.stream().map(DocumentCandidate::label).toList());
    }

    private DocumentCandidate adr(int number, DocumentStatus status) {
        return candidate(
                "ADR_DOCUMENT", "adr-" + number + "-" + status,
                Integer.toString(number), status, 0, false,
                RepositoryContextLayer.ADR);
    }

    private DocumentCandidate story(int number, boolean main) {
        return candidate(
                "STORY_DOCUMENT", main ? "main-story" : "story-" + number,
                null, null, number, main,
                RepositoryContextLayer.PROJECT_DOCUMENTATION);
    }

    private DocumentCandidate roadmap() {
        return candidate(
                "ROADMAP_DOCUMENT", "roadmap", null, null, 0, false,
                RepositoryContextLayer.ROADMAP);
    }

    private DocumentCandidate candidate(
            String kind,
            String label,
            String adrNumber,
            DocumentStatus adrStatus,
            int storyNumber,
            boolean mainStory,
            RepositoryContextLayer layer
    ) {
        return new DocumentCandidate(
                kind, label + ".md", label, layer, "ref:" + label,
                Instant.EPOCH, "source", label + ".md", adrNumber,
                adrStatus, storyNumber, mainStory, adrStatus);
    }
}
