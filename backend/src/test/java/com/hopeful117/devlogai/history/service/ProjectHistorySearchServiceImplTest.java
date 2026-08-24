package com.hopeful117.devlogai.history.service;

import com.hopeful117.devlogai.contracts.projecthistory.ProjectHistoryMatchedOn;
import com.hopeful117.devlogai.contracts.projecthistory.ProjectHistorySearchResult;
import com.hopeful117.devlogai.history.entity.ChangedFile;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.shared.exception.InvalidParameterException;
import com.hopeful117.devlogai.source.entity.Source;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectHistorySearchServiceImplTest {

    private static final String SLUG = "devlog-ai";
    private static final UUID PROJECT_ID =
            UUID.fromString("f3d56247-aada-4a76-982b-e6802c0b309c");
    private static final UUID REPO_ID =
            UUID.fromString("dddd1111-2222-3333-4444-555555555555");

    private final ProjectCommitRepository commitRepository =
            mock(ProjectCommitRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectHistorySearchServiceImpl service =
            new ProjectHistorySearchServiceImpl(commitRepository, projectRepository);

    private Project project;

    @BeforeEach
    void setUp() {
        project = Project.builder().id(PROJECT_ID).slug(SLUG).name(SLUG).build();
        when(projectRepository.findById(PROJECT_ID)).thenReturn(java.util.Optional.of(project));
    }

    @Test
    void shouldFindCommitByPartialCaseInsensitiveMessage() {
        when(commitRepository.findByProjectIdOrderByCommittedAtAscCommitHashAsc(PROJECT_ID))
                .thenReturn(List.of(
                        commit(sha("aaa1"), 10, "fix project note markdown preview",
                                null, List.of()),
                        commit(sha("bbb2"), 11, "unrelated change", null, List.of())));

        var result = service.search(PROJECT_ID, "Markdown PREVIEW", null);

        assertThat(result.totalMatches()).isEqualTo(1);
        assertThat(result.truncated()).isFalse();
        var match = result.results().getFirst();
        assertThat(match.commitSha()).isEqualTo(sha("aaa1"));
        assertThat(match.subject()).contains("markdown");
        assertThat(match.matches())
                .anySatisfy(m -> {
                    assertThat(m.matchedOn()).isEqualTo(ProjectHistoryMatchedOn.COMMIT_MESSAGE);
                    assertThat(m.matchedValue()).containsIgnoringCase("markdown preview");
                });
    }

    @Test
    void shouldFindCommitsByChangedPathAndRankFilenameExactFirst() {
        Instant now = Instant.parse("2026-08-20T00:00:00Z");
        // recent weak message match on the same term, no path match
        ProjectCommit recentWeak = commit(sha("recent"), now,
                "touching unrelated files while mentioning RepositoryContextEngine once",
                null, List.of());
        // old strong matches: exact filename + path
        ProjectCommit introducer = commit(sha("aeca570"), now.minusSeconds(60 * 60 * 24 * 120),
                "feat(context): implement ADR-038 repository context engine", null,
                List.of(changed("backend/src/main/java/com/hopeful117/devlogai/"
                        + "repositorycontext/RepositoryContextEngine.java")));
        ProjectCommit evolver = commit(sha("fc99099"), now.minusSeconds(60 * 60 * 24 * 60),
                "engineering story evidence precision", null,
                List.of(changed("backend/src/test/java/com/hopeful117/devlogai/"
                        + "repositorycontext/RepositoryContextEngineTest.java"),
                        changed("docs/notes.md")));
        when(commitRepository.findByProjectIdOrderByCommittedAtAscCommitHashAsc(PROJECT_ID))
                .thenReturn(List.of(recentWeak, introducer, evolver));

        var result = service.search(PROJECT_ID, "RepositoryContextEngine", null);

        assertThat(result.totalMatches()).isEqualTo(3);
        // old exact-filename match outranks the recent weak message-only mention
        assertThat(result.results().get(0).commitSha()).isEqualTo(sha("aeca570"));
        assertThat(result.results().get(0).relevance())
                .isGreaterThan(result.results().get(2).relevance());
        // path-based matches expose their path; the message-only match exposes the subject
        assertThat(result.results().get(0).matches())
                .anySatisfy(m -> assertThat(m.matchedOn()).isEqualTo(ProjectHistoryMatchedOn.PATH));
        assertThat(result.results().get(1).matches())
                .anySatisfy(m -> assertThat(m.matchedOn()).isEqualTo(ProjectHistoryMatchedOn.PATH));
        assertThat(result.results().get(2).matches())
                .anySatisfy(m -> assertThat(m.matchedOn()).isEqualTo(ProjectHistoryMatchedOn.COMMIT_MESSAGE));
    }

    @Test
    void shouldRequireEveryTermWithAndSemantics() {
        when(commitRepository.findByProjectIdOrderByCommittedAtAscCommitHashAsc(PROJECT_ID))
                .thenReturn(List.of(
                        commit(sha("multi"), 5, "add markdown renderer for notes",
                                null, List.of(changed("frontend/src/app/notes/markdown-preview.ts"))),
                        commit(sha("onlymd"), 6, "update markdown docs", null, List.of())));

        var result = service.search(PROJECT_ID, "markdown renderer", null);

        assertThat(result.totalMatches()).isEqualTo(1);
        assertThat(result.results().getFirst().commitSha()).isEqualTo(sha("multi"));
        // both terms contributed distinct matches
        assertThat(result.results().getFirst().matches().size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldDeduplicatePathsAcrossTerms() {
        when(commitRepository.findByProjectIdOrderByCommittedAtAscCommitHashAsc(PROJECT_ID))
                .thenReturn(List.of(commit(sha("dup"), 3, "rename engine file",
                        null, List.of(changed(
                                "backend/src/main/java/com/hopeful117/devlogai/"
                                        + "repositorycontext/RepositoryContextEngine.java")))));

        var result = service.search(PROJECT_ID,
                "RepositoryContextEngine repositorycontext", null);

        var pathValues = result.results().getFirst().matches().stream()
                .filter(m -> m.matchedOn() == ProjectHistoryMatchedOn.PATH)
                .count();
        assertThat(pathValues).isEqualTo(1);
    }

    @Test
    void shouldBoundResultsAndReportTruncation() {
        var many = new java.util.ArrayList<ProjectCommit>();
        for (int i = 0; i < 25; i++) {
            many.add(commit(hex(i), 100 - i,
                    "markdown related change %d".formatted(i), null, List.of()));
        }
        when(commitRepository.findByProjectIdOrderByCommittedAtAscCommitHashAsc(PROJECT_ID))
                .thenReturn(many);

        var result = service.search(PROJECT_ID, "markdown", 10);

        assertThat(result.totalMatches()).isEqualTo(25);
        assertThat(result.truncated()).isTrue();
        assertThat(result.results()).hasSize(10);
    }

    @Test
    void shouldReturnEmptyResultWithoutErrorWhenNothingMatches() {
        when(commitRepository.findByProjectIdOrderByCommittedAtAscCommitHashAsc(PROJECT_ID))
                .thenReturn(List.of(commit(sha("x1"), 1, "totally different", null, List.of())));

        var result = service.search(PROJECT_ID, "quantumcomputing", null);

        assertThat(result.totalMatches()).isZero();
        assertThat(result.truncated()).isFalse();
        assertThat(result.results()).isEmpty();
    }

    @Test
    void shouldBuildResourceUriFromProjectSlug() {
        String sha = sha("markdownfix");
        when(commitRepository.findByProjectIdOrderByCommittedAtAscCommitHashAsc(PROJECT_ID))
                .thenReturn(List.of(commit(sha, 7, "anything markdown", null, List.of())));

        var result = service.search(PROJECT_ID, "markdown", null);

        assertThat(result.results().getFirst().resource())
                .isEqualTo("devlog://projects/devlog-ai/commits/" + sha);
        assertThat(result.results().getFirst().repositoryId()).isEqualTo(REPO_ID);
    }

    @Test
    void shouldRejectBlankQuery() {
        assertThatThrownBy(() -> service.search(PROJECT_ID, "   ", null))
                .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> service.search(PROJECT_ID, null, null))
                .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> service.search(PROJECT_ID, "!! ??", null))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void shouldRejectInvalidLimit() {
        assertThatThrownBy(() -> service.search(PROJECT_ID, "markdown", 0))
                .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> service.search(PROJECT_ID, "markdown", 101))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void shouldRejectUnknownProject() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.search(PROJECT_ID, "markdown", null))
                .isInstanceOf(EntityNotFoundException.class);
    }


    /** Deterministic full-length hexadecimal SHA-like fixture value. */
    private static String sha(String seed) {
        StringBuilder value = new StringBuilder("e");
        for (byte b : seed.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            value.append(String.format("%02x", b));
        }
        while (value.length() < 40) value.append('0');
        return value.substring(0, 40);
    }

    private static String hex(int i) {
        return ("aaaaaaaaaa" + Integer.toHexString(i)).repeat(6)
                .substring(0, 40);
    }
    private ProjectCommit commit(String sha, long committedAtEpochSecond, String subject,
                                 String fullMessage, List<ChangedFile> files) {
        return commit(sha, Instant.ofEpochSecond(committedAtEpochSecond), subject,
                fullMessage, files);
    }

    private ProjectCommit commit(String sha, Instant committedAt, String subject,
                                 String fullMessage, List<ChangedFile> files) {
        Source source = Source.builder().id(REPO_ID).build();
        ProjectCommit commit = ProjectCommit.builder()
                .commitHash(sha)
                .subject(subject)
                .fullMessage(fullMessage)
                .authorName("ludo")
                .committedAt(committedAt)
                .source(source)
                .build();
        files.forEach(commit::addChangedFile);
        return commit;
    }

    private ChangedFile changed(String newPath) {
        return ChangedFile.builder().newPath(newPath).insertions(10).deletions(2).build();
    }
}
