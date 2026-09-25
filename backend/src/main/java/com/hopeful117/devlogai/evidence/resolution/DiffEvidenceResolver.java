package com.hopeful117.devlogai.evidence.resolution;

import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.entity.ChangedFile;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import org.springframework.stereotype.Component;

/** Resolves a source/commit/path diff reference against persisted commit history. */
@Component
public final class DiffEvidenceResolver implements EvidenceFamilyResolver {
    private final ProjectCommitRepository commits;
    private final SourceRepository sources;

    public DiffEvidenceResolver(ProjectCommitRepository commits, SourceRepository sources) {
        this.commits = commits;
        this.sources = sources;
    }

    @Override public EvidenceResolutionFamily family() { return EvidenceResolutionFamily.DIFF; }

    @Override
    public EvidenceResolutionResult resolve(ParsedEvidenceReference reference, EvidenceResolutionRequest request) {
        if (reference == null || request == null || reference.sourceId() == null
                || reference.revision() == null || reference.pathOrIdentity() == null
                || reference.pathOrIdentity().isBlank()) {
            throw new IllegalArgumentException("Malformed diff resolution request");
        }
        if (sources.findById(reference.sourceId()).isEmpty()) {
            throw failure(EvidenceResolutionFailureCode.SOURCE_UNAVAILABLE, reference, "Diff source is unavailable");
        }
        ProjectCommit commit = commits.findBySourceIdAndCommitHash(reference.sourceId(), reference.revision())
                .orElseThrow(() -> failure(EvidenceResolutionFailureCode.EVIDENCE_NOT_FOUND, reference,
                        "Diff commit is not present for the requested source"));
        ProjectCommit files = commits.findWithChangedFilesBySourceIdAndCommitHash(
                reference.sourceId(), reference.revision()).orElse(null);
        if (files == null) throw failure(EvidenceResolutionFailureCode.EVIDENCE_NOT_FOUND, reference,
                "Diff commit file history is unavailable");
        commit.setChangedFiles(files.getChangedFiles());
        if (blank(commit.getCommitHash()) || blank(commit.getSubject()) || blank(commit.getFullMessage())
                || commit.getCommittedAt() == null || commit.getParents() == null || commit.getChangedFiles() == null
                || commit.getParents().stream().anyMatch(parent -> parent == null || blank(parent.getParentHash()))
                || commit.getChangedFiles().stream().anyMatch(this::malformedFile)) {
            throw failure(EvidenceResolutionFailureCode.EVIDENCE_NOT_FOUND, reference,
                    "Diff commit contains malformed persisted history data");
        }
        boolean present = commit.getChangedFiles().stream().anyMatch(file -> {
            String oldPath = file.getOldPath() == null ? null : file.getOldPath().replace('\\', '/');
            String newPath = file.getNewPath() == null ? null : file.getNewPath().replace('\\', '/');
            return reference.pathOrIdentity().equals(oldPath) || reference.pathOrIdentity().equals(newPath);
        });
        if (!present) throw failure(EvidenceResolutionFailureCode.EVIDENCE_NOT_FOUND, reference,
                "Diff path is not present in the requested commit");
        var payload = new GitCommitResolutionPayload(commit.getCommitHash(), commit.getSubject(),
                commit.getFullMessage(), commit.getAuthorName(), commit.getAuthorEmail(), commit.getAuthoredAt(),
                commit.getCommittedAt(), commit.isRootCommit(), commit.isMergeCommit(),
                commit.getParents().stream().map(parent -> parent.getParentHash()).toList(),
                commit.getChangedFiles().stream().map(file -> new GitCommitResolutionPayload.ChangedFileResolution(
                        file.getChangeType().name(), file.getOldPath(), file.getNewPath(), file.isBinary(),
                        file.getInsertions(), file.getDeletions())).toList());
        return new EvidenceResolutionResult(new EvidenceResolutionMetadata(reference.canonicalReference(),
                family(), reference.sourceId(), "project-commit-diff", reference.revision(), request.mode(), true), payload);
    }

    private boolean malformedFile(ChangedFile file) {
        if (file == null || file.getChangeType() == null) return true;
        String oldPath = file.getOldPath() == null ? null : file.getOldPath().replace('\\', '/');
        String newPath = file.getNewPath() == null ? null : file.getNewPath().replace('\\', '/');
        return (oldPath == null && newPath == null)
                || (oldPath != null && (oldPath.isBlank() || oldPath.startsWith("/") || oldPath.contains("..")))
                || (newPath != null && (newPath.isBlank() || newPath.startsWith("/") || newPath.contains("..")));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private EvidenceResolutionException failure(EvidenceResolutionFailureCode code, ParsedEvidenceReference reference,
            String message) {
        return new EvidenceResolutionException(code, reference.canonicalReference(), reference.sourceId(), message);
    }
}
