package com.hopeful117.devlogai.evidence.resolution;

final class PersistedEvidenceResolutionSupport {
    private PersistedEvidenceResolutionSupport() {
    }

    static void rejectTaskSnapshot(
            ParsedEvidenceReference reference, EvidenceResolutionRequest request, String family) {
        if (request.mode() == EvidenceResolutionMode.TASK_SNAPSHOT) {
            throw failure(EvidenceResolutionFailureCode.UNAUTHORIZED, reference,
                    family + " resolution requires the originating task snapshot resolver");
        }
    }

    static EvidenceResolutionException notFound(
            ParsedEvidenceReference reference, String family) {
        return failure(EvidenceResolutionFailureCode.EVIDENCE_NOT_FOUND, reference,
                family + " is not present");
    }

    static EvidenceResolutionMetadata metadata(
            ParsedEvidenceReference reference, EvidenceResolutionRequest request, String provenance) {
        return new EvidenceResolutionMetadata(reference.canonicalReference(), reference.family(),
                null, provenance, null, request.mode(), false);
    }

    private static EvidenceResolutionException failure(
            EvidenceResolutionFailureCode code, ParsedEvidenceReference reference, String message) {
        return new EvidenceResolutionException(code, reference.canonicalReference(), null, message);
    }
}
