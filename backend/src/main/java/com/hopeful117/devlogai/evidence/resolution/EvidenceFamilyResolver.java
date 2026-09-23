package com.hopeful117.devlogai.evidence.resolution;

public interface EvidenceFamilyResolver {
    EvidenceResolutionFamily family();

    EvidenceResolutionResult resolve(
            ParsedEvidenceReference reference,
            EvidenceResolutionRequest request
    );
}
