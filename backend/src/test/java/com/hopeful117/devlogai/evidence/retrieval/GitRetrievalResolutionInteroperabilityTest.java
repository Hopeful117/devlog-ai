package com.hopeful117.devlogai.evidence.retrieval;

import com.hopeful117.devlogai.evidence.resolution.CanonicalEvidenceReferenceParser;
import com.hopeful117.devlogai.evidence.resolution.EvidenceResolutionFacade;
import com.hopeful117.devlogai.evidence.resolution.EvidenceResolutionFailureCode;
import com.hopeful117.devlogai.evidence.resolution.EvidenceResolutionRequest;
import com.hopeful117.devlogai.evidence.resolution.EvidenceResolutionMode;
import com.hopeful117.devlogai.evidence.resolution.GitCommitEvidenceResolver;
import com.hopeful117.devlogai.evidence.resolution.GitCommitResolutionPayload;
import com.hopeful117.devlogai.history.entity.ChangedFile;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.history.service.ProjectHistorySearchServiceImpl;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import com.hopeful117.devlogai.history.model.FileChangeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GitRetrievalResolutionInteroperabilityTest {
    private static final UUID PROJECT_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID SOURCE_A = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");
    private static final UUID SOURCE_B = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectCommitRepository historyCommitRepository = mock(ProjectCommitRepository.class);
    private final ProjectCommitRepository resolutionCommitRepository = mock(ProjectCommitRepository.class);
    private final SourceRepository sourceRepository = mock(SourceRepository.class);
    private final Project project = Project.builder().id(PROJECT_ID).slug("project").build();
    private final Source sourceA = Source.builder().id(SOURCE_A).project(project).build();
    private final Source sourceB = Source.builder().id(SOURCE_B).project(project).build();
    private final ProjectHistorySearchServiceImpl history =
            new ProjectHistorySearchServiceImpl(historyCommitRepository, projectRepository);
    private final SharedEvidenceRetrievalBoundary retrieval =
            new SharedEvidenceRetrievalBoundaryImpl(new GitCommitRetrievalAdapter(history));
    private final EvidenceResolutionFacade resolution = new EvidenceResolutionFacade(
            new CanonicalEvidenceReferenceParser(),
            List.of(new GitCommitEvidenceResolver(resolutionCommitRepository, sourceRepository)));

    @BeforeEach
    void setUp() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(sourceRepository.findById(SOURCE_A)).thenReturn(Optional.of(sourceA));
        when(sourceRepository.findById(SOURCE_B)).thenReturn(Optional.of(sourceB));
    }

    @Test
    void resolvesCanonicalReferencesFromRealGitRetrieval() {
        ProjectCommit commitA = commit(SOURCE_A, "a".repeat(40), "needle A");
        ProjectCommit commitB = commit(SOURCE_B, "b".repeat(40), "needle B");
        givenHistory(commitA, commitB);
        givenResolution(commitA, commitB);

        EvidencePage page = retrieve(null, "needle");

        assertThat(page.candidates()).hasSize(2);
        for (EvidenceCandidate candidate : page.candidates()) {
            assertThat(candidate.family()).isEqualTo(EvidenceRetrievalFamily.GIT_COMMIT);
            assertThat(candidate.projectId()).isEqualTo(PROJECT_ID);
            assertThat(candidate.canonicalReference())
                    .isEqualTo("git:" + candidate.sourceId() + ":" + shaFor(candidate));

            var result = resolution.resolve(new EvidenceResolutionRequest(
                    candidate.canonicalReference(), EvidenceResolutionMode.CURRENT));
            var payload = (GitCommitResolutionPayload) result.payload();
            assertThat(result.metadata().canonicalReference())
                    .isEqualTo(candidate.canonicalReference());
            assertThat(result.metadata().sourceId()).isEqualTo(candidate.sourceId());
            assertThat(payload.commitHash()).isEqualTo(shaFor(candidate));
        }
    }

    @Test
    void preservesIndependentMultiSourceIdentityAndResolution() {
        ProjectCommit commitA = commit(SOURCE_A, "a".repeat(40), "needle");
        ProjectCommit commitB = commit(SOURCE_B, "b".repeat(40), "needle");
        givenHistory(commitA, commitB);
        givenResolution(commitA, commitB);

        EvidencePage page = retrieve(null, "needle");

        assertThat(page.candidates()).extracting(EvidenceCandidate::canonicalReference)
                .containsExactly(
                        "git:" + SOURCE_A + ":" + commitA.getCommitHash(),
                        "git:" + SOURCE_B + ":" + commitB.getCommitHash());
        assertThat(page.candidates()).allSatisfy(candidate -> {
            var result = resolution.resolve(new EvidenceResolutionRequest(
                    candidate.canonicalReference(), EvidenceResolutionMode.CURRENT));
            assertThat(result.metadata().sourceId()).isEqualTo(candidate.sourceId());
        });
    }

    @Test
    void explicitSourceRetrievalDoesNotFallbackToAnotherSource() {
        ProjectCommit commitA = commit(SOURCE_A, "a".repeat(40), "needle A");
        ProjectCommit commitB = commit(SOURCE_B, "b".repeat(40), "needle B");
        givenHistory(commitA, commitB);
        givenResolution(commitA, commitB);

        EvidencePage page = retrieve(SOURCE_A, "needle");

        assertThat(page.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.sourceId()).isEqualTo(SOURCE_A);
            assertThat(candidate.canonicalReference())
                    .isEqualTo("git:" + SOURCE_A + ":" + commitA.getCommitHash());
            var result = resolution.resolve(new EvidenceResolutionRequest(
                    candidate.canonicalReference(), EvidenceResolutionMode.CURRENT));
            assertThat(result.metadata().sourceId()).isEqualTo(SOURCE_A);
        });
    }

    @Test
    void emptyRetrievalSucceedsWithoutInvokingResolution() {
        givenHistory();

        EvidencePage page = retrieve(null, "missing");

        assertThat(page.candidates()).isEmpty();
        assertThat(page.totalMatches()).isZero();
        assertThat(page.hasNext()).isFalse();
        verifyNoInteractions(resolutionCommitRepository, sourceRepository);
    }

    @Test
    void aStaleRetrievedReferenceFailsOnlyWhenExplicitlyResolved() {
        ProjectCommit commitA = commit(SOURCE_A, "a".repeat(40), "needle");
        givenHistory(commitA);
        when(sourceRepository.findById(SOURCE_A)).thenReturn(Optional.of(sourceA));
        when(resolutionCommitRepository.findBySourceIdAndCommitHash(
                SOURCE_A, commitA.getCommitHash())).thenReturn(Optional.empty());

        EvidenceCandidate candidate = retrieve(SOURCE_A, "needle").candidates().getFirst();

        assertThatThrownBy(() -> resolution.resolve(new EvidenceResolutionRequest(
                candidate.canonicalReference(), EvidenceResolutionMode.CURRENT)))
                .isInstanceOf(com.hopeful117.devlogai.evidence.resolution.EvidenceResolutionException.class)
                .extracting("code")
                .isEqualTo(EvidenceResolutionFailureCode.EVIDENCE_NOT_FOUND);
        verify(resolutionCommitRepository).findBySourceIdAndCommitHash(
                SOURCE_A, commitA.getCommitHash());
    }

    @Test
    void retrievalAdapterHasNoResolutionDependency() {
        assertThat(GitCommitRetrievalAdapter.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getName().contains("EvidenceResolution"));
    }

    private EvidencePage retrieve(UUID sourceId, String lexicalQuery) {
        return retrieval.retrieve(new EvidenceRetrievalQuery(
                new EvidenceRetrievalScope(PROJECT_ID, sourceId),
                EvidenceRetrievalFamily.GIT_COMMIT, lexicalQuery, 0, 100));
    }

    private void givenHistory(ProjectCommit... commits) {
        when(historyCommitRepository.findByProjectIdOrderByCommittedAtAscCommitHashAsc(PROJECT_ID))
                .thenReturn(List.of(commits));
    }

    private void givenResolution(ProjectCommit... commits) {
        for (ProjectCommit commit : commits) {
            when(resolutionCommitRepository.findBySourceIdAndCommitHash(
                    commit.getSource().getId(), commit.getCommitHash())).thenReturn(Optional.of(commit));
            when(resolutionCommitRepository.findWithChangedFilesBySourceIdAndCommitHash(
                    commit.getSource().getId(), commit.getCommitHash())).thenReturn(Optional.of(commit));
        }
    }

    private ProjectCommit commit(UUID sourceId, String sha, String subject) {
        Source source = sourceId.equals(SOURCE_A) ? sourceA : sourceB;
        ProjectCommit commit = ProjectCommit.builder()
                .project(project)
                .source(source)
                .commitHash(sha)
                .subject(subject)
                .fullMessage(subject + "\n\nfull authoritative body")
                .authorName("author")
                .authorEmail("author@example.test")
                .authoredAt(Instant.parse("2026-09-21T10:00:00Z"))
                .committedAt(Instant.parse("2026-09-21T10:00:00Z"))
                .rootCommit(false)
                .mergeCommit(false)
                .build();
        commit.addParent(0, "d".repeat(40));
        commit.addChangedFile(ChangedFile.builder()
                .changeType(FileChangeType.MODIFIED)
                .newPath("src/" + sourceId + ".java")
                .insertions(2)
                .deletions(1)
                .build());
        return commit;
    }

    private String shaFor(EvidenceCandidate candidate) {
        return candidate.canonicalReference().substring(
                candidate.canonicalReference().lastIndexOf(':') + 1);
    }
}
