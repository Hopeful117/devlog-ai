package com.hopeful117.devlogai.evidence.resolution;

import com.hopeful117.devlogai.challenge.entity.Challenge;
import com.hopeful117.devlogai.challenge.repository.ChallengeRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class ChallengeEvidenceResolver implements EvidenceFamilyResolver {
    private final ChallengeRepository repository;

    public ChallengeEvidenceResolver(ChallengeRepository repository) {
        this.repository = repository;
    }

    @Override
    public EvidenceResolutionFamily family() {
        return EvidenceResolutionFamily.CHALLENGE;
    }

    @Override
    public EvidenceResolutionResult resolve(
            ParsedEvidenceReference reference, EvidenceResolutionRequest request) {
        PersistedEvidenceResolutionSupport.rejectTaskSnapshot(reference, request, "Challenge");
        Challenge challenge = repository.findDetailedById(UUID.fromString(reference.pathOrIdentity()))
                .orElseThrow(() -> PersistedEvidenceResolutionSupport.notFound(reference, "Challenge"));
        ChallengeResolutionPayload payload = new ChallengeResolutionPayload(
                challenge.getId(), challenge.getProject().getId(), challenge.getTitle(),
                challenge.getDescription(), challenge.getImpact(), challenge.getStatus(),
                challenge.getResolution(), challenge.getCreatedAt(), challenge.getUpdatedAt());
        return new EvidenceResolutionResult(
                PersistedEvidenceResolutionSupport.metadata(reference, request, "challenge"), payload);
    }
}
