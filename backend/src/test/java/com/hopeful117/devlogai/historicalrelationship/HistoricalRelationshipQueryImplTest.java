package com.hopeful117.devlogai.historicalrelationship;

import com.hopeful117.devlogai.engineeringevent.EngineeringEventCategory;
import com.hopeful117.devlogai.story.entity.StoryStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricalRelationshipQueryImplTest {
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final CommitHash COMMIT_HASH = new CommitHash("a".repeat(40));

    @Mock
    private StoryCommitReferenceLookup storyLookup;

    @Mock
    private EventCommitReferenceLookup eventLookup;

    @Test
    void returnsEmptyFamiliesWithoutTruncation() {
        HistoricalCommitLookup lookup = lookup();
        when(storyLookup.findReferences(lookup)).thenReturn(new StoryCommitLookupResult(List.of(), false));
        when(eventLookup.findReferences(lookup)).thenReturn(new EventCommitLookupResult(List.of(), false));

        HistoricalCommitLookupResult result = newQuery().findReferences(lookup);

        assertEquals(lookup.projectId(), result.projectId());
        assertSame(lookup.commitHash(), result.commitHash());
        assertEquals(List.of(), result.stories());
        assertEquals(List.of(), result.events());
        assertEquals(false, result.storiesTruncated());
        assertEquals(false, result.eventsTruncated());
        verifyDelegation(lookup);
    }

    @Test
    void propagatesStoryOnlyResults() {
        HistoricalCommitLookup lookup = lookup();
        StoryCommitReference story = storyReference();
        when(storyLookup.findReferences(lookup)).thenReturn(new StoryCommitLookupResult(List.of(story), false));
        when(eventLookup.findReferences(lookup)).thenReturn(new EventCommitLookupResult(List.of(), false));

        HistoricalCommitLookupResult result = newQuery().findReferences(lookup);

        assertEquals(List.of(story), result.stories());
        assertEquals(List.of(), result.events());
        assertEquals(false, result.storiesTruncated());
        assertEquals(false, result.eventsTruncated());
    }

    @Test
    void propagatesEventOnlyResults() {
        HistoricalCommitLookup lookup = lookup();
        EventCommitReference event = eventReference();
        when(storyLookup.findReferences(lookup)).thenReturn(new StoryCommitLookupResult(List.of(), false));
        when(eventLookup.findReferences(lookup)).thenReturn(new EventCommitLookupResult(List.of(event), false));

        HistoricalCommitLookupResult result = newQuery().findReferences(lookup);

        assertEquals(List.of(), result.stories());
        assertEquals(List.of(event), result.events());
        assertEquals(false, result.storiesTruncated());
        assertEquals(false, result.eventsTruncated());
    }

    @Test
    void propagatesBothFamilyResultsUnchanged() {
        HistoricalCommitLookup lookup = lookup();
        StoryCommitReference story = storyReference();
        EventCommitReference event = eventReference();
        when(storyLookup.findReferences(lookup)).thenReturn(new StoryCommitLookupResult(List.of(story), true));
        when(eventLookup.findReferences(lookup)).thenReturn(new EventCommitLookupResult(List.of(event), true));

        HistoricalCommitLookupResult result = newQuery().findReferences(lookup);

        assertEquals(List.of(story), result.stories());
        assertEquals(List.of(event), result.events());
        assertEquals(true, result.storiesTruncated());
        assertEquals(true, result.eventsTruncated());
        assertSame(lookup.projectId(), result.projectId());
        assertSame(lookup.commitHash(), result.commitHash());
    }

    @ParameterizedTest
    @MethodSource("truncationCombinations")
    void preservesIndependentTruncationFlags(boolean storiesTruncated, boolean eventsTruncated) {
        HistoricalCommitLookup lookup = lookup();
        when(storyLookup.findReferences(lookup))
                .thenReturn(new StoryCommitLookupResult(List.of(), storiesTruncated));
        when(eventLookup.findReferences(lookup))
                .thenReturn(new EventCommitLookupResult(List.of(), eventsTruncated));

        HistoricalCommitLookupResult result = newQuery().findReferences(lookup);

        assertEquals(storiesTruncated, result.storiesTruncated());
        assertEquals(eventsTruncated, result.eventsTruncated());
    }

    private static Stream<Arguments> truncationCombinations() {
        return Stream.of(
                Arguments.of(false, false),
                Arguments.of(true, false),
                Arguments.of(false, true),
                Arguments.of(true, true));
    }

    private HistoricalRelationshipQueryImpl newQuery() {
        return new HistoricalRelationshipQueryImpl(storyLookup, eventLookup);
    }

    private HistoricalCommitLookup lookup() {
        return new HistoricalCommitLookup(PROJECT_ID, COMMIT_HASH);
    }

    private void verifyDelegation(HistoricalCommitLookup lookup) {
        verify(storyLookup).findReferences(lookup);
        verify(eventLookup).findReferences(lookup);
    }

    private StoryCommitReference storyReference() {
        return new StoryCommitReference(UUID.randomUUID(), 1, "docs/story.md",
                StoryStatus.COMPLETED, CommitReferenceRole.REFERENCES_AS_BASE);
    }

    private EventCommitReference eventReference() {
        return new EventCommitReference(UUID.randomUUID(), UUID.randomUUID(),
                EngineeringEventCategory.FEATURE_INTRODUCTION, "Event",
                Instant.EPOCH, CommitReferenceRole.REFERENCES_AS_TARGET);
    }
}
