package com.hopeful117.devlogai.evidence.resolution;

import com.hopeful117.devlogai.artifact.entity.Artifact;
import com.hopeful117.devlogai.artifact.repository.ArtifactRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class ArtifactEvidenceResolver implements EvidenceFamilyResolver {
    private final ArtifactRepository repository;

    public ArtifactEvidenceResolver(ArtifactRepository repository) {
        this.repository = repository;
    }

    @Override
    public EvidenceResolutionFamily family() {
        return EvidenceResolutionFamily.ARTIFACT;
    }

    @Override
    public EvidenceResolutionResult resolve(
            ParsedEvidenceReference reference, EvidenceResolutionRequest request) {
        PersistedEvidenceResolutionSupport.rejectTaskSnapshot(reference, request, "Artifact");
        Artifact artifact = repository.findDetailedById(UUID.fromString(reference.pathOrIdentity()))
                .orElseThrow(() -> PersistedEvidenceResolutionSupport.notFound(reference, "Artifact"));
        ArtifactResolutionPayload payload = new ArtifactResolutionPayload(
                artifact.getId(), artifact.getProject().getId(), artifact.getName(), artifact.getType(),
                artifact.getPath(), artifact.getDescription(), artifact.getCreatedAt(), artifact.getUpdatedAt());
        return new EvidenceResolutionResult(
                PersistedEvidenceResolutionSupport.metadata(reference, request, "artifact"), payload);
    }
}
