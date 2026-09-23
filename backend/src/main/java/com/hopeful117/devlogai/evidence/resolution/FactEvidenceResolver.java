package com.hopeful117.devlogai.evidence.resolution;

import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Resolves persisted Facts only inside an explicitly supplied analysis scope. */
@Component
public final class FactEvidenceResolver implements EvidenceFamilyResolver {
    private final FactRepository factRepository;

    public FactEvidenceResolver(FactRepository factRepository) {
        this.factRepository = factRepository;
    }

    @Override
    public EvidenceResolutionFamily family() {
        return EvidenceResolutionFamily.FACT;
    }

    @Override
    public EvidenceResolutionResult resolve(
            ParsedEvidenceReference reference,
            EvidenceResolutionRequest request
    ) {
        rejectTaskSnapshot(reference, request);
        if (request.analysisId() == null) {
            throw failure(EvidenceResolutionFailureCode.UNAUTHORIZED, reference,
                    "Fact resolution requires an explicit analysis scope");
        }

        UUID factId = UUID.fromString(reference.pathOrIdentity());
        Fact fact = factRepository.findWithAnalysisById(factId)
                .orElseThrow(() -> failure(EvidenceResolutionFailureCode.EVIDENCE_NOT_FOUND,
                        reference, "Fact is not present"));
        if (fact.getAnalysis() == null
                || !request.analysisId().equals(fact.getAnalysis().getId())) {
            throw failure(EvidenceResolutionFailureCode.UNAUTHORIZED, reference,
                    "Fact is outside the requested analysis scope");
        }

        var payload = new FactResolutionPayload(
                fact.getId(), fact.getAnalysis().getId(), fact.getType(), fact.getContent(),
                fact.getSource(), fact.getFingerprint(), fact.getEvidenceReferences(),
                fact.getDetectedAt());
        var metadata = new EvidenceResolutionMetadata(
                reference.canonicalReference(), reference.family(), null, "fact", null,
                request.mode(), false);
        return new EvidenceResolutionResult(metadata, payload);
    }

    private void rejectTaskSnapshot(
            ParsedEvidenceReference reference,
            EvidenceResolutionRequest request
    ) {
        if (request.mode() == EvidenceResolutionMode.TASK_SNAPSHOT) {
            throw failure(EvidenceResolutionFailureCode.UNAUTHORIZED, reference,
                    "Fact resolution requires the originating task snapshot resolver");
        }
    }

    private EvidenceResolutionException failure(
            EvidenceResolutionFailureCode code,
            ParsedEvidenceReference reference,
            String message
    ) {
        return new EvidenceResolutionException(code, reference.canonicalReference(),
                null, message);
    }
}
