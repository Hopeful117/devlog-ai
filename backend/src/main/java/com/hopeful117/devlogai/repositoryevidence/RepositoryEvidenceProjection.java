package com.hopeful117.devlogai.repositoryevidence;

import com.hopeful117.devlogai.source.entity.Source;

import java.util.List;
import java.util.UUID;

public record RepositoryEvidenceProjection(
        Source source,
        String baselineRevision,
        List<ResolvedFileEvidence> resolvedFiles) {

    public RepositoryEvidenceProjection {
        resolvedFiles = List.copyOf(resolvedFiles);
    }
}