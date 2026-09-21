package com.hopeful117.devlogai.historicalrelationship;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HistoricalRelationshipQueryImpl implements HistoricalRelationshipQuery {
    private final StoryCommitReferenceLookup storyLookup;
    private final EventCommitReferenceLookup eventLookup;

    @Override
    public HistoricalCommitLookupResult findReferences(HistoricalCommitLookup lookup) {
        StoryCommitLookupResult stories = storyLookup.findReferences(lookup);
        EventCommitLookupResult events = eventLookup.findReferences(lookup);

        return new HistoricalCommitLookupResult(
                lookup.projectId(),
                lookup.commitHash(),
                stories.references(),
                events.references(),
                stories.truncated(),
                events.truncated());
    }
}
