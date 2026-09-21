package com.hopeful117.devlogai.historicalrelationship;

import com.hopeful117.devlogai.engineeringevent.EngineeringEventCategory;
import com.hopeful117.devlogai.story.entity.StoryStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalRelationshipContractTest {
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID STORY_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID SOURCE_ID = UUID.randomUUID();
    private static final CommitHash HASH = new CommitHash("a".repeat(40));

    @Test
    void normalizesValidCommitHashes() {
        assertEquals("a".repeat(40), new CommitHash("   " + "A".repeat(40) + "  ").value());
        assertEquals("b".repeat(64), new CommitHash("b".repeat(64)).value());
    }

    @Test
    void rejectsNullBlankAbbreviatedMalformedAndRefLikeCommitValues() {
        assertThrows(IllegalArgumentException.class, () -> new CommitHash(null));
        assertThrows(IllegalArgumentException.class, () -> new CommitHash(" "));
        assertThrows(IllegalArgumentException.class, () -> new CommitHash("a".repeat(7)));

        for (String value : List.of(
                "a".repeat(39), "a".repeat(41), "a".repeat(63), "a".repeat(65),
                "g".repeat(40), "main", "refs/heads/main", "refs/tags/v1.0")) {
            assertThrows(IllegalArgumentException.class, () -> new CommitHash(value), value);
        }
    }

    @Test
    void requiresLookupIdentityFields() {
        assertThrows(NullPointerException.class, () -> new HistoricalCommitLookup(null, HASH));
        assertThrows(NullPointerException.class, () -> new HistoricalCommitLookup(PROJECT_ID, null));
    }

    @Test
    void defensivelyCopiesResultCollectionsAndPreservesIndependentFlags() {
        StoryCommitReference story = storyReference();
        EventCommitReference event = eventReference();
        List<StoryCommitReference> stories = new ArrayList<>(List.of(story));
        List<EventCommitReference> events = new ArrayList<>(List.of(event));

        HistoricalCommitLookupResult result = new HistoricalCommitLookupResult(
                PROJECT_ID, HASH, stories, events, true, false);

        stories.clear();
        events.clear();

        assertEquals(List.of(story), result.stories());
        assertEquals(List.of(event), result.events());
        assertTrue(result.storiesTruncated());
        assertFalse(result.eventsTruncated());
        assertThrows(UnsupportedOperationException.class,
                () -> result.stories().add(story));
        assertThrows(UnsupportedOperationException.class,
                () -> result.events().add(event));
    }

    @Test
    void requiresMeaningfulStoryReferenceFields() {
        assertThrows(NullPointerException.class,
                () -> new StoryCommitReference(null, 1, null,
                        StoryStatus.REGISTERED, CommitReferenceRole.REFERENCES_AS_BASE));
        assertThrows(NullPointerException.class,
                () -> new StoryCommitReference(STORY_ID, null, null,
                        StoryStatus.REGISTERED, CommitReferenceRole.REFERENCES_AS_BASE));
        assertThrows(NullPointerException.class,
                () -> new StoryCommitReference(STORY_ID, 1, null,
                        null, CommitReferenceRole.REFERENCES_AS_BASE));
        assertThrows(NullPointerException.class,
                () -> new StoryCommitReference(STORY_ID, 1, null,
                        StoryStatus.REGISTERED, null));
    }

    @Test
    void requiresMeaningfulEventReferenceFields() {
        assertThrows(NullPointerException.class,
                () -> new EventCommitReference(null, SOURCE_ID,
                        EngineeringEventCategory.FEATURE_INTRODUCTION, "title",
                        Instant.EPOCH, CommitReferenceRole.REFERENCES_AS_TARGET));
        assertThrows(NullPointerException.class,
                () -> new EventCommitReference(EVENT_ID, null,
                        EngineeringEventCategory.FEATURE_INTRODUCTION, "title",
                        Instant.EPOCH, CommitReferenceRole.REFERENCES_AS_TARGET));
        assertThrows(NullPointerException.class,
                () -> new EventCommitReference(EVENT_ID, SOURCE_ID, null,
                        "title", Instant.EPOCH, CommitReferenceRole.REFERENCES_AS_TARGET));
        assertThrows(NullPointerException.class,
                () -> new EventCommitReference(EVENT_ID, SOURCE_ID,
                        EngineeringEventCategory.FEATURE_INTRODUCTION, null,
                        Instant.EPOCH, CommitReferenceRole.REFERENCES_AS_TARGET));
        assertThrows(NullPointerException.class,
                () -> new EventCommitReference(EVENT_ID, SOURCE_ID,
                        EngineeringEventCategory.FEATURE_INTRODUCTION, "title",
                        null, CommitReferenceRole.REFERENCES_AS_TARGET));
        assertThrows(NullPointerException.class,
                () -> new EventCommitReference(EVENT_ID, SOURCE_ID,
                        EngineeringEventCategory.FEATURE_INTRODUCTION, "title",
                        Instant.EPOCH, null));
    }

    private StoryCommitReference storyReference() {
        return new StoryCommitReference(STORY_ID, 1, "docs/story.md",
                StoryStatus.COMPLETED, CommitReferenceRole.REFERENCES_AS_TARGET);
    }

    private EventCommitReference eventReference() {
        return new EventCommitReference(EVENT_ID, SOURCE_ID,
                EngineeringEventCategory.FEATURE_INTRODUCTION, "title",
                Instant.EPOCH, CommitReferenceRole.REFERENCES_AS_BASE);
    }
}
