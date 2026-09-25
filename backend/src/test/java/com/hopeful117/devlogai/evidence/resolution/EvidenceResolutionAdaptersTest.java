package com.hopeful117.devlogai.evidence.resolution;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.collection.collector.SecureRepositoryContentReader;
import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EvidenceResolutionAdaptersTest {
    private static final UUID SOURCE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ANALYSIS_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID OTHER_ANALYSIS_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String COMMIT = "a1b2c3d4";

    @Test
    void resolvesGitCommitAgainstTheExactSource() {
        var sourceRepository = mock(SourceRepository.class);
        var commitRepository = mock(ProjectCommitRepository.class);
        var source = Source.builder().id(SOURCE_ID).build();
        var commit = ProjectCommit.builder()
                .source(source)
                .commitHash(COMMIT)
                .subject("Add resolver")
                .fullMessage("Add resolver\n\nKeep source identity explicit")
                .committedAt(Instant.parse("2026-09-21T10:00:00Z"))
                .build();
        when(sourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(commitRepository.findBySourceIdAndCommitHash(SOURCE_ID, COMMIT))
                .thenReturn(Optional.of(commit));
        when(commitRepository.findWithChangedFilesBySourceIdAndCommitHash(SOURCE_ID, COMMIT))
                .thenReturn(Optional.of(commit));

        var result = new EvidenceResolutionFacade(
                new CanonicalEvidenceReferenceParser(),
                List.of(new GitCommitEvidenceResolver(commitRepository, sourceRepository)))
                .resolve(request("git:" + SOURCE_ID + ":" + COMMIT));

        var payload = (GitCommitResolutionPayload) result.payload();
        assertThat(result.metadata().sourceId()).isEqualTo(SOURCE_ID);
        assertThat(result.metadata().revision()).isEqualTo(COMMIT);
        assertThat(payload.subject()).isEqualTo("Add resolver");
        verify(commitRepository).findBySourceIdAndCommitHash(SOURCE_ID, COMMIT);
    }

    @Test
    void rejectsMissingGitCommitWithoutTryingAnotherSource() {
        var sourceRepository = mock(SourceRepository.class);
        var commitRepository = mock(ProjectCommitRepository.class);
        var source = Source.builder().id(SOURCE_ID).build();
        when(sourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(commitRepository.findBySourceIdAndCommitHash(SOURCE_ID, COMMIT))
                .thenReturn(Optional.empty());

        assertCode(new EvidenceResolutionFacade(
                        new CanonicalEvidenceReferenceParser(),
                        List.of(new GitCommitEvidenceResolver(commitRepository, sourceRepository))),
                request("git:" + SOURCE_ID + ":" + COMMIT),
                EvidenceResolutionFailureCode.EVIDENCE_NOT_FOUND);
    }

    @Test
    void resolvesPinnedDocumentWithoutReplacingItsRevision() {
        var sourceRepository = mock(SourceRepository.class);
        var workspaceManager = mock(WorkspaceManager.class);
        var contentReader = mock(SecureRepositoryContentReader.class);
        var source = Source.builder().id(SOURCE_ID).build();
        var workspace = new SynchronizedWorkspace(SOURCE_ID, Path.of("/workspace/source"), COMMIT);
        when(sourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(workspaceManager.synchronize(source, COMMIT)).thenReturn(workspace);
        when(contentReader.readComplete(workspace, "docs/adr.md", Integer.MAX_VALUE))
                .thenReturn(new SecureRepositoryContentReader.ReadResult(
                        SecureRepositoryContentReader.ReadResult.Status.COMPLETE,
                        "# ADR\n\nPinned content", null));

        var result = new EvidenceResolutionFacade(
                new CanonicalEvidenceReferenceParser(),
                List.of(new PinnedDocumentEvidenceResolver(
                        sourceRepository, workspaceManager, contentReader)))
                .resolve(request("document:" + SOURCE_ID + ":docs/adr.md@" + COMMIT));

        var payload = (DocumentResolutionPayload) result.payload();
        assertThat(payload.path()).isEqualTo("docs/adr.md");
        assertThat(payload.revision()).isEqualTo(COMMIT);
        assertThat(payload.content()).contains("Pinned content");
        verify(workspaceManager).synchronize(source, COMMIT);
    }

    @Test
    void doesNotResolveCurrentDocumentWhenTaskSnapshotModeIsRequested() {
        var sourceRepository = mock(SourceRepository.class);
        var workspaceManager = mock(WorkspaceManager.class);
        var contentReader = mock(SecureRepositoryContentReader.class);
        var resolver = new PinnedDocumentEvidenceResolver(
                sourceRepository, workspaceManager, contentReader);
        var facade = new EvidenceResolutionFacade(
                new CanonicalEvidenceReferenceParser(), List.of(resolver));

        assertCode(facade, new EvidenceResolutionRequest(
                        "document:" + SOURCE_ID + ":docs/adr.md@" + COMMIT,
                        EvidenceResolutionMode.TASK_SNAPSHOT),
                EvidenceResolutionFailureCode.UNAUTHORIZED);
        verifyNoInteractions(sourceRepository, workspaceManager, contentReader);
    }

    @Test
    void doesNotResolveCurrentGitCommitWhenTaskSnapshotModeIsRequested() {
        var sourceRepository = mock(SourceRepository.class);
        var commitRepository = mock(ProjectCommitRepository.class);
        var facade = new EvidenceResolutionFacade(
                new CanonicalEvidenceReferenceParser(),
                List.of(new GitCommitEvidenceResolver(commitRepository, sourceRepository)));

        assertCode(facade, new EvidenceResolutionRequest(
                        "git:" + SOURCE_ID + ":" + COMMIT,
                        EvidenceResolutionMode.TASK_SNAPSHOT),
                EvidenceResolutionFailureCode.UNAUTHORIZED);
        verifyNoInteractions(sourceRepository, commitRepository);
    }

    @Test
    void mapsUnavailablePinnedDocumentToNoLongerResolvable() {
        var sourceRepository = mock(SourceRepository.class);
        var workspaceManager = mock(WorkspaceManager.class);
        var contentReader = mock(SecureRepositoryContentReader.class);
        var source = Source.builder().id(SOURCE_ID).build();
        var workspace = new SynchronizedWorkspace(SOURCE_ID, Path.of("/workspace/source"), COMMIT);
        when(sourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(workspaceManager.synchronize(source, COMMIT)).thenReturn(workspace);
        when(contentReader.readComplete(workspace, "missing.md", Integer.MAX_VALUE))
                .thenReturn(new SecureRepositoryContentReader.ReadResult(
                        SecureRepositoryContentReader.ReadResult.Status.UNAVAILABLE,
                        null, "FILE_UNAVAILABLE"));

        assertCode(new EvidenceResolutionFacade(
                        new CanonicalEvidenceReferenceParser(),
                        List.of(new PinnedDocumentEvidenceResolver(
                                sourceRepository, workspaceManager, contentReader))),
                request("document:" + SOURCE_ID + ":missing.md@" + COMMIT),
                EvidenceResolutionFailureCode.EVIDENCE_NO_LONGER_RESOLVABLE);
    }

    @Test
    void rejectsWorkspaceRevisionFallback() {
        var sourceRepository = mock(SourceRepository.class);
        var workspaceManager = mock(WorkspaceManager.class);
        var contentReader = mock(SecureRepositoryContentReader.class);
        var source = Source.builder().id(SOURCE_ID).build();
        var workspace = new SynchronizedWorkspace(SOURCE_ID, Path.of("/workspace/source"), "head");
        when(sourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(workspaceManager.synchronize(source, COMMIT)).thenReturn(workspace);

        assertCode(new EvidenceResolutionFacade(
                        new CanonicalEvidenceReferenceParser(),
                        List.of(new PinnedDocumentEvidenceResolver(
                                sourceRepository, workspaceManager, contentReader))),
                request("document:" + SOURCE_ID + ":docs/adr.md@" + COMMIT),
                EvidenceResolutionFailureCode.REVISION_UNAVAILABLE);
        verifyNoInteractions(contentReader);
    }

    @Test
    void resolvesFactOnlyInsideTheExplicitAnalysisScope() {
        var factRepository = mock(FactRepository.class);
        var factId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        var analysis = Analysis.builder().id(ANALYSIS_ID).build();
        var fact = Fact.builder()
                .id(factId)
                .analysis(analysis)
                .type(FactType.REPOSITORY_STRUCTURE_SUMMARY)
                .content("The repository uses Java")
                .source("repository")
                .fingerprint("fingerprint")
                .evidenceReferences(Set.of("git:" + SOURCE_ID + ":" + COMMIT))
                .build();
        when(factRepository.findWithAnalysisById(factId)).thenReturn(Optional.of(fact));

        var result = new EvidenceResolutionFacade(
                new CanonicalEvidenceReferenceParser(),
                List.of(new FactEvidenceResolver(factRepository)))
                .resolve(new EvidenceResolutionRequest(
                        "fact:" + factId, EvidenceResolutionMode.CURRENT, ANALYSIS_ID));

        var payload = (FactResolutionPayload) result.payload();
        assertThat(payload.id()).isEqualTo(factId);
        assertThat(payload.analysisId()).isEqualTo(ANALYSIS_ID);
        assertThat(payload.content()).isEqualTo("The repository uses Java");
        assertThat(result.metadata().sourceId()).isNull();
    }

    @Test
    void rejectsFactOutsideTheExplicitAnalysisScope() {
        var factRepository = mock(FactRepository.class);
        var factId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        var fact = Fact.builder()
                .id(factId)
                .analysis(Analysis.builder().id(OTHER_ANALYSIS_ID).build())
                .type(FactType.REPOSITORY_STRUCTURE_SUMMARY)
                .content("Other analysis fact")
                .source("repository")
                .build();
        when(factRepository.findWithAnalysisById(factId)).thenReturn(Optional.of(fact));

        assertCode(new EvidenceResolutionFacade(
                        new CanonicalEvidenceReferenceParser(),
                        List.of(new FactEvidenceResolver(factRepository))),
                new EvidenceResolutionRequest(
                        "fact:" + factId, EvidenceResolutionMode.CURRENT, ANALYSIS_ID),
                EvidenceResolutionFailureCode.UNAUTHORIZED);
    }

    @Test
    void refusesFactResolutionWithoutAnalysisScope() {
        var factRepository = mock(FactRepository.class);
        var facade = new EvidenceResolutionFacade(
                new CanonicalEvidenceReferenceParser(),
                List.of(new FactEvidenceResolver(factRepository)));

        assertCode(facade, request("fact:aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                EvidenceResolutionFailureCode.UNAUTHORIZED);
        verifyNoInteractions(factRepository);
    }

    private EvidenceResolutionRequest request(String reference) {
        return new EvidenceResolutionRequest(reference, EvidenceResolutionMode.CURRENT);
    }

    private void assertCode(
            EvidenceResolutionFacade facade,
            EvidenceResolutionRequest request,
            EvidenceResolutionFailureCode code
    ) {
        assertThatThrownBy(() -> facade.resolve(request))
                .isInstanceOf(EvidenceResolutionException.class)
                .extracting("code")
                .isEqualTo(code);
    }
}
