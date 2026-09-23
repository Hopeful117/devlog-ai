package com.hopeful117.devlogai.evidence.resolution;

import com.hopeful117.devlogai.history.entity.ChangedFile;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/** Resolves source-scoped persisted Git commit metadata without selecting a source. */
@Component
public final class GitCommitEvidenceResolver implements EvidenceFamilyResolver {
    private final ProjectCommitRepository commitRepository;
    private final SourceRepository sourceRepository;

    public GitCommitEvidenceResolver(
            ProjectCommitRepository commitRepository,
            SourceRepository sourceRepository
    ) {
        this.commitRepository = commitRepository;
        this.sourceRepository = sourceRepository;
    }

    @Override
    public EvidenceResolutionFamily family() {
        return EvidenceResolutionFamily.GIT_COMMIT;
    }

    @Override
    public EvidenceResolutionResult resolve(
            ParsedEvidenceReference reference,
            EvidenceResolutionRequest request
    ) {
        rejectTaskSnapshot(reference, request);
        if (sourceRepository.findById(reference.sourceId()).isEmpty()) {
            throw failure(EvidenceResolutionFailureCode.SOURCE_UNAVAILABLE, reference,
                    "Git source is unavailable");
        }
        ProjectCommit commit = commitRepository
                .findBySourceIdAndCommitHash(reference.sourceId(), reference.revision())
                .orElseThrow(() -> failure(EvidenceResolutionFailureCode.EVIDENCE_NOT_FOUND,
                        reference, "Git commit is not present for the requested source"));

        var payload = new GitCommitResolutionPayload(
                commit.getCommitHash(), commit.getSubject(), commit.getFullMessage(),
                commit.getAuthorName(), commit.getAuthorEmail(), commit.getAuthoredAt(),
                commit.getCommittedAt(), commit.isRootCommit(), commit.isMergeCommit(),
                commit.getParents().stream().map(parent -> parent.getParentHash()).toList(),
                commit.getChangedFiles().stream().map(this::changedFile).toList());
        var metadata = new EvidenceResolutionMetadata(
                reference.canonicalReference(), reference.family(), reference.sourceId(),
                "project-commit", reference.revision(), request.mode(), true);
        return new EvidenceResolutionResult(metadata, payload);
    }

    private GitCommitResolutionPayload.ChangedFileResolution changedFile(ChangedFile file) {
        return new GitCommitResolutionPayload.ChangedFileResolution(
                file.getChangeType().name(), file.getOldPath(), file.getNewPath(),
                file.isBinary(), file.getInsertions(), file.getDeletions());
    }

    private void rejectTaskSnapshot(
            ParsedEvidenceReference reference,
            EvidenceResolutionRequest request
    ) {
        if (request.mode() == EvidenceResolutionMode.TASK_SNAPSHOT) {
            throw failure(EvidenceResolutionFailureCode.UNAUTHORIZED, reference,
                    "Git commit resolution requires the originating task snapshot resolver");
        }
    }

    private EvidenceResolutionException failure(
            EvidenceResolutionFailureCode code,
            ParsedEvidenceReference reference,
            String message
    ) {
        return new EvidenceResolutionException(code, reference.canonicalReference(),
                reference.sourceId(), message);
    }
}
