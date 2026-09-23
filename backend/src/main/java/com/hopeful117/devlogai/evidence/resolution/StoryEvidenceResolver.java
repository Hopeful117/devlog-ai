package com.hopeful117.devlogai.evidence.resolution;

import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class StoryEvidenceResolver implements EvidenceFamilyResolver {
    private final EngineeringStoryRepository repository;

    public StoryEvidenceResolver(EngineeringStoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public EvidenceResolutionFamily family() {
        return EvidenceResolutionFamily.STORY;
    }

    @Override
    public EvidenceResolutionResult resolve(
            ParsedEvidenceReference reference, EvidenceResolutionRequest request) {
        PersistedEvidenceResolutionSupport.rejectTaskSnapshot(reference, request, "Story");
        EngineeringStory story = repository.findDetailedById(UUID.fromString(reference.pathOrIdentity()))
                .orElseThrow(() -> PersistedEvidenceResolutionSupport.notFound(reference, "Story"));
        StoryResolutionPayload payload = new StoryResolutionPayload(
                story.getId(), story.getProject().getId(), story.getStoryNumber(), story.getTitle(),
                story.getStoryPath(), story.getBaseCommit(), story.getTargetCommit(), story.getStatus(),
                story.getCreatedAt(), story.getUpdatedAt(), story.getCompletedAt());
        return new EvidenceResolutionResult(
                PersistedEvidenceResolutionSupport.metadata(reference, request, "story"), payload);
    }
}
