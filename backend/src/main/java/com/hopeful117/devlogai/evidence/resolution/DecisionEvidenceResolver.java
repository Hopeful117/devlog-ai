package com.hopeful117.devlogai.evidence.resolution;

import com.hopeful117.devlogai.decision.entity.Decision;
import com.hopeful117.devlogai.decision.repository.DecisionRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class DecisionEvidenceResolver implements EvidenceFamilyResolver {
    private final DecisionRepository repository;

    public DecisionEvidenceResolver(DecisionRepository repository) {
        this.repository = repository;
    }

    @Override
    public EvidenceResolutionFamily family() {
        return EvidenceResolutionFamily.DECISION;
    }

    @Override
    public EvidenceResolutionResult resolve(
            ParsedEvidenceReference reference, EvidenceResolutionRequest request) {
        PersistedEvidenceResolutionSupport.rejectTaskSnapshot(reference, request, "Decision");
        Decision decision = repository.findDetailedById(UUID.fromString(reference.pathOrIdentity()))
                .orElseThrow(() -> PersistedEvidenceResolutionSupport.notFound(reference, "Decision"));
        DecisionResolutionPayload payload = new DecisionResolutionPayload(
                decision.getId(), decision.getProject().getId(),
                decision.getProposal() == null ? null : decision.getProposal().getId(),
                decision.getTitle(), decision.getContext(), decision.getChoice(), decision.getRationale(),
                decision.getConsequences(), decision.getCreatedAt(), decision.getUpdatedAt());
        return new EvidenceResolutionResult(
                PersistedEvidenceResolutionSupport.metadata(reference, request, "decision"), payload);
    }
}
