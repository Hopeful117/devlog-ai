package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.contracts.engineeringcontext.TrustTier;
import com.hopeful117.devlogai.history.entity.ChangedFile;
import com.hopeful117.devlogai.history.entity.ProjectCommit;

import java.util.List;
import java.util.UUID;

/** Reconstructs bounded repository relationships from persisted history data. */
public class RepositoryRelationshipProjector {

    public List<EngineeringRelationship> project(ProjectCommit commit) {
        return commit.getChangedFiles().stream()
                .map(file -> project(commit, file))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private EngineeringRelationship project(ProjectCommit commit, ChangedFile file) {
        String path = file.getNewPath() != null ? file.getNewPath() : file.getOldPath();
        if (path == null || path.isBlank()) return null;
        UUID projectId = commit.getProject().getId();
        UUID sourceId = commit.getSource().getId();
        String revision = commit.getCommitHash();
        var source = new EngineeringRelationship.RepositoryCommitEndpoint(
                projectId, sourceId, revision);
        var target = new EngineeringRelationship.RepositoryFileEndpoint(
                projectId, sourceId, revision, path.replace('\\', '/'));
        return new EngineeringRelationship(
                "changes:" + source.canonicalIdentity() + ":" + target.canonicalIdentity(),
                source,
                "CHANGES",
                target,
                EngineeringRelationship.Origin.REPOSITORY_DERIVED,
                TrustTier.TECHNICAL_EVIDENCE,
                revision,
                List.of("git:" + sourceId + ":" + revision,
                        "diff:" + revision + ":" + path));
    }
}
