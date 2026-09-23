package com.hopeful117.devlogai.evidence.resolution;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class AnalysisEvidenceResolver implements EvidenceFamilyResolver {
    private final AnalysisRepository repository;

    public AnalysisEvidenceResolver(AnalysisRepository repository) {
        this.repository = repository;
    }

    @Override
    public EvidenceResolutionFamily family() {
        return EvidenceResolutionFamily.ANALYSIS;
    }

    @Override
    public EvidenceResolutionResult resolve(
            ParsedEvidenceReference reference, EvidenceResolutionRequest request) {
        PersistedEvidenceResolutionSupport.rejectTaskSnapshot(reference, request, "Analysis");
        Analysis analysis = repository.findWithProjectById(UUID.fromString(reference.pathOrIdentity()))
                .orElseThrow(() -> PersistedEvidenceResolutionSupport.notFound(reference, "Analysis"));
        AnalysisResolutionPayload payload = new AnalysisResolutionPayload(
                analysis.getId(), analysis.getProject().getId(),
                analysis.getSelectedSource() == null ? null : analysis.getSelectedSource().getId(),
                analysis.getType(), analysis.getIntentId(), analysis.getIntentVersion(), analysis.getStatus(),
                analysis.getTargetRevision(), analysis.getStartedAt(), analysis.getCompletedAt(),
                analysis.getCreatedAt(), analysis.getUpdatedAt(), analysis.getUserGuidance(),
                analysis.getSelectedSourceSnapshot());
        return new EvidenceResolutionResult(
                PersistedEvidenceResolutionSupport.metadata(reference, request, "analysis"), payload);
    }
}
