package com.hopeful117.devlogai.historicalrelationship;

import com.hopeful117.devlogai.engineeringevent.EngineeringEventCategory;
import com.hopeful117.devlogai.engineeringevent.EngineeringEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventCommitReferenceLookup {
    static final int MAX_RESULTS = 100;
    static final int FETCH_LIMIT = MAX_RESULTS + 1;

    private final EngineeringEventRepository eventRepository;

    EventCommitLookupResult findReferences(HistoricalCommitLookup lookup) {
        List<EngineeringEventRepository.EventCommitReferenceProjection> rows =
                eventRepository.findCommitReferences(
                        lookup.projectId(), lookup.commitHash().value(), FETCH_LIMIT);

        boolean truncated = rows.size() > MAX_RESULTS;
        List<EventCommitReference> references = rows.stream()
                .limit(MAX_RESULTS)
                .map(this::toReference)
                .toList();

        return new EventCommitLookupResult(references, truncated);
    }

    private EventCommitReference toReference(
            EngineeringEventRepository.EventCommitReferenceProjection row) {
        return new EventCommitReference(
                row.getEventId(),
                row.getSourceId(),
                EngineeringEventCategory.valueOf(row.getCategory()),
                row.getTitle(),
                row.getOccurredAt(),
                CommitReferenceRole.valueOf(row.getRole()));
    }
}
