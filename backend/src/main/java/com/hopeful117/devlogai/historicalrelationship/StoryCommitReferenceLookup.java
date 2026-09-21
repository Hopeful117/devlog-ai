package com.hopeful117.devlogai.historicalrelationship;

import com.hopeful117.devlogai.story.entity.StoryStatus;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StoryCommitReferenceLookup {
    static final int MAX_RESULTS = 100;
    static final int FETCH_LIMIT = MAX_RESULTS + 1;

    private final EngineeringStoryRepository storyRepository;

    StoryCommitLookupResult findReferences(HistoricalCommitLookup lookup) {
        List<EngineeringStoryRepository.StoryCommitReferenceProjection> rows =
                storyRepository.findCommitReferences(
                        lookup.projectId(), lookup.commitHash().value(), FETCH_LIMIT);

        boolean truncated = rows.size() > MAX_RESULTS;
        List<StoryCommitReference> references = rows.stream()
                .limit(MAX_RESULTS)
                .map(this::toReference)
                .toList();

        return new StoryCommitLookupResult(references, truncated);
    }

    private StoryCommitReference toReference(
            EngineeringStoryRepository.StoryCommitReferenceProjection row) {
        return new StoryCommitReference(
                row.getStoryId(),
                row.getStoryNumber(),
                row.getStoryPath(),
                StoryStatus.valueOf(row.getStatus()),
                CommitReferenceRole.valueOf(row.getRole()));
    }
}
