package com.hopeful117.devlogai.evidence.resolution;

import com.hopeful117.devlogai.engineeringevent.EngineeringEvent;
import com.hopeful117.devlogai.engineeringevent.EngineeringEventRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class EngineeringEventEvidenceResolver implements EvidenceFamilyResolver {
    private final EngineeringEventRepository repository;

    public EngineeringEventEvidenceResolver(EngineeringEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public EvidenceResolutionFamily family() {
        return EvidenceResolutionFamily.ENGINEERING_EVENT;
    }

    @Override
    public EvidenceResolutionResult resolve(
            ParsedEvidenceReference reference, EvidenceResolutionRequest request) {
        PersistedEvidenceResolutionSupport.rejectTaskSnapshot(reference, request,
                "Engineering Event");
        EngineeringEvent event = repository.findDetailedById(UUID.fromString(reference.pathOrIdentity()))
                .orElseThrow(() -> PersistedEvidenceResolutionSupport.notFound(
                        reference, "Engineering Event"));
        EngineeringEventResolutionPayload payload = new EngineeringEventResolutionPayload(
                event.getId(), event.getProject().getId(), event.getAnalysis().getId(),
                event.getProposal().getId(), event.getValidation().getId(), event.getSource().getId(),
                event.getCategory(), event.getTitle(), event.getSummary(), event.getSignificance(),
                event.getBaseCommit(), event.getTargetCommit(), event.getOccurredAt(), event.getCreatedAt());
        return new EvidenceResolutionResult(
                PersistedEvidenceResolutionSupport.metadata(reference, request, "engineering-event"),
                payload);
    }
}
