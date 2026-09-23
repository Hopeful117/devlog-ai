package com.hopeful117.devlogai.evidence.resolution;

import com.hopeful117.devlogai.collection.collector.SecureRepositoryContentReader;
import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import org.springframework.stereotype.Component;

/** Resolves a document only from its explicit source and pinned revision. */
@Component
public final class PinnedDocumentEvidenceResolver implements EvidenceFamilyResolver {
    private final SourceRepository sourceRepository;
    private final WorkspaceManager workspaceManager;
    private final SecureRepositoryContentReader contentReader;
    public PinnedDocumentEvidenceResolver(
            SourceRepository sourceRepository,
            WorkspaceManager workspaceManager,
            SecureRepositoryContentReader contentReader
    ) {
        this.sourceRepository = sourceRepository;
        this.workspaceManager = workspaceManager;
        this.contentReader = contentReader;
    }

    @Override
    public EvidenceResolutionFamily family() {
        return EvidenceResolutionFamily.DOCUMENT;
    }

    @Override
    public EvidenceResolutionResult resolve(
            ParsedEvidenceReference reference,
            EvidenceResolutionRequest request
    ) {
        if (request.mode() == EvidenceResolutionMode.TASK_SNAPSHOT) {
            throw failure(EvidenceResolutionFailureCode.UNAUTHORIZED, reference,
                    "Document resolution requires the originating task snapshot resolver");
        }
        Source source = sourceRepository.findById(reference.sourceId())
                .orElseThrow(() -> failure(EvidenceResolutionFailureCode.SOURCE_UNAVAILABLE,
                        reference, "Document source is unavailable"));
        SynchronizedWorkspace workspace;
        try {
            workspace = workspaceManager.synchronize(source, reference.revision());
        } catch (RuntimeException exception) {
            throw failure(EvidenceResolutionFailureCode.REVISION_UNAVAILABLE, reference,
                    "Pinned document revision is unavailable");
        }
        if (!reference.revision().equals(workspace.resolvedRevision())) {
            throw failure(EvidenceResolutionFailureCode.REVISION_UNAVAILABLE, reference,
                    "Workspace resolved a different revision than requested");
        }

        SecureRepositoryContentReader.ReadResult result = contentReader.readComplete(
                workspace, reference.pathOrIdentity(), Integer.MAX_VALUE);
        if (result.status() != SecureRepositoryContentReader.ReadResult.Status.COMPLETE) {
            EvidenceResolutionFailureCode code = result.status()
                    == SecureRepositoryContentReader.ReadResult.Status.UNAVAILABLE
                    ? EvidenceResolutionFailureCode.EVIDENCE_NO_LONGER_RESOLVABLE
                    : EvidenceResolutionFailureCode.UNSUPPORTED_EXPANSION;
            throw failure(code, reference,
                    "Pinned document cannot be resolved: " + result.reason());
        }

        var metadata = new EvidenceResolutionMetadata(
                reference.canonicalReference(), reference.family(), reference.sourceId(),
                "repository-document", reference.revision(), request.mode(), true);
        return new EvidenceResolutionResult(metadata,
                new DocumentResolutionPayload(
                        reference.pathOrIdentity(), workspace.resolvedRevision(), result.text()));
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
