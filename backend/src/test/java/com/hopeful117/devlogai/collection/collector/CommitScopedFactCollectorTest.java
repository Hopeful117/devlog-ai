package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.history.entity.ChangedFile;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.model.FileChangeType;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.source.entity.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommitScopedFactCollectorTest {

    @TempDir
    Path workspace;

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID SOURCE_ID = UUID.randomUUID();
    private static final UUID ANALYSIS_ID = UUID.randomUUID();

    @Test
    void shouldProduceCommitDiffSummaryFromMultipleCommits() {
        ProjectCommitRepository repo = mock(ProjectCommitRepository.class);
        when(repo.findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                eq(PROJECT_ID), any()))
                .thenReturn(List.of(
                        commit("abc123", "feat: add login", 3, 100, 20,
                                changedFile("src/main/Login.java", FileChangeType.ADDED, 80, 0),
                                changedFile("src/main/ AuthService.java", FileChangeType.MODIFIED, 20, 20)),
                        commit("def456", "fix: resolve crash", 2, 30, 10,
                                changedFile("src/main/Handler.java", FileChangeType.MODIFIED, 30, 10))
                ));

        CommitScopedFactCollector collector = new CommitScopedFactCollector(repo, 90);
        CollectionContext context = new CollectionContext(
                ANALYSIS_ID, SOURCE_ID, PROJECT_ID, workspace,
                "abc123", SourceType.GIT_REPOSITORY, Instant.now());

        CollectionResult result = collector.collect(context);

        assertFalse(result.facts().isEmpty());
        assertEquals(CollectorType.COMMIT_SCOPED, result.collectorType());

        // Should have COMMIT_DIFF_SUMMARY
        assertTrue(result.facts().stream()
                .anyMatch(f -> f.type() == FactType.COMMIT_DIFF_SUMMARY));
        // Should have COMMIT_ADDS_FEATURE (feat: prefix)
        assertTrue(result.facts().stream()
                .anyMatch(f -> f.type() == FactType.COMMIT_ADDS_FEATURE));
        // Should have COMMIT_FIXES_BUG (fix: prefix)
        assertTrue(result.facts().stream()
                .anyMatch(f -> f.type() == FactType.COMMIT_FIXES_BUG));
    }

    @Test
    void shouldProduceModuleFactsGroupedByPath() {
        ProjectCommitRepository repo = mock(ProjectCommitRepository.class);
        when(repo.findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                eq(PROJECT_ID), any()))
                .thenReturn(List.of(
                        commit("abc123", "chore: update config", 3, 50, 10,
                                changedFile("backend/src/Main.java", FileChangeType.MODIFIED, 30, 5),
                                changedFile("backend/src/Service.java", FileChangeType.MODIFIED, 20, 5),
                                changedFile("frontend/src/App.js", FileChangeType.MODIFIED, 0, 0))
                ));

        CommitScopedFactCollector collector = new CommitScopedFactCollector(repo, 90);
        CollectionContext context = new CollectionContext(
                ANALYSIS_ID, SOURCE_ID, PROJECT_ID, workspace,
                "abc123", SourceType.GIT_REPOSITORY, Instant.now());

        CollectionResult result = collector.collect(context);

        assertTrue(result.facts().stream()
                .filter(f -> f.type() == FactType.COMMIT_CHANGES_MODULE)
                .anyMatch(f -> f.content().contains("backend")));
        assertTrue(result.facts().stream()
                .filter(f -> f.type() == FactType.COMMIT_CHANGES_MODULE)
                .anyMatch(f -> f.content().contains("frontend")));
    }

    @Test
    void shouldDetectRefactoringCommits() {
        ProjectCommitRepository repo = mock(ProjectCommitRepository.class);
        when(repo.findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                eq(PROJECT_ID), any()))
                .thenReturn(List.of(
                        commit("abc123", "refactor: extract service layer", 2, 40, 30,
                                changedFile("src/Service.java", FileChangeType.MODIFIED, 40, 30))
                ));

        CommitScopedFactCollector collector = new CommitScopedFactCollector(repo, 90);
        CollectionContext context = new CollectionContext(
                ANALYSIS_ID, SOURCE_ID, PROJECT_ID, workspace,
                "abc123", SourceType.GIT_REPOSITORY, Instant.now());

        CollectionResult result = collector.collect(context);

        assertTrue(result.facts().stream()
                .anyMatch(f -> f.type() == FactType.COMMIT_REFACTORS_CODE));
    }

    @Test
    void shouldReturnEmptyWhenNoCommits() {
        ProjectCommitRepository repo = mock(ProjectCommitRepository.class);
        when(repo.findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                eq(PROJECT_ID), any()))
                .thenReturn(List.of());

        CommitScopedFactCollector collector = new CommitScopedFactCollector(repo, 90);
        CollectionContext context = new CollectionContext(
                ANALYSIS_ID, SOURCE_ID, PROJECT_ID, workspace,
                "abc123", SourceType.GIT_REPOSITORY, Instant.now());

        CollectionResult result = collector.collect(context);

        assertTrue(result.facts().isEmpty());
    }

    @Test
    void shouldDeduplicateFactsByFingerprint() {
        ProjectCommitRepository repo = mock(ProjectCommitRepository.class);
        // Two identical commits should produce the same fact fingerprint
        ProjectCommit c1 = commit("abc123", "feat: login", 1, 50, 10,
                changedFile("src/Login.java", FileChangeType.ADDED, 50, 10));
        ProjectCommit c2 = commit("abc123", "feat: login", 1, 50, 10,
                changedFile("src/Login.java", FileChangeType.ADDED, 50, 10));
        when(repo.findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                eq(PROJECT_ID), any()))
                .thenReturn(List.of(c1, c2));

        CommitScopedFactCollector collector = new CommitScopedFactCollector(repo, 90);
        CollectionContext context = new CollectionContext(
                ANALYSIS_ID, SOURCE_ID, PROJECT_ID, workspace,
                "abc123", SourceType.GIT_REPOSITORY, Instant.now());

        CollectionResult result = collector.collect(context);

        // COMMIT_DIFF_SUMMARY should appear once (same content + same fingerprint)
        long summaryCount = result.facts().stream()
                .filter(f -> f.type() == FactType.COMMIT_DIFF_SUMMARY)
                .count();
        assertEquals(1, summaryCount);
    }

    @Test
    void shouldProduceDeduplicatedEvidenceReferences() {
        ProjectCommitRepository repo = mock(ProjectCommitRepository.class);
        when(repo.findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                eq(PROJECT_ID), any()))
                .thenReturn(List.of(
                        commit("abc123", "feat: login", 1, 50, 10,
                                changedFile("src/Login.java", FileChangeType.ADDED, 50, 10))
                ));

        CommitScopedFactCollector collector = new CommitScopedFactCollector(repo, 90);
        CollectionContext context = new CollectionContext(
                ANALYSIS_ID, SOURCE_ID, PROJECT_ID, workspace,
                "abc123", SourceType.GIT_REPOSITORY, Instant.now());

        CollectionResult result = collector.collect(context);

        assertFalse(result.facts().isEmpty());
        result.facts().forEach(fact -> {
            // Evidence references should be sorted and distinct
            List<String> refs = fact.evidenceReferences();
            assertEquals(refs.size(), refs.stream().distinct().count());
            for (int i = 1; i < refs.size(); i++) {
                assertTrue(refs.get(i - 1).compareTo(refs.get(i)) <= 0);
            }
        });
    }

    @Test
    void shouldNotProduceFactsForNonFeatureNonFixNonRefactorCommits() {
        ProjectCommitRepository repo = mock(ProjectCommitRepository.class);
        when(repo.findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                eq(PROJECT_ID), any()))
                .thenReturn(List.of(
                        commit("abc123", "docs: update README", 1, 10, 5,
                                changedFile("README.md", FileChangeType.MODIFIED, 10, 5))
                ));

        CommitScopedFactCollector collector = new CommitScopedFactCollector(repo, 90);
        CollectionContext context = new CollectionContext(
                ANALYSIS_ID, SOURCE_ID, PROJECT_ID, workspace,
                "abc123", SourceType.GIT_REPOSITORY, Instant.now());

        CollectionResult result = collector.collect(context);

        // Should have COMMIT_DIFF_SUMMARY but no feature/fix/refactor facts
        assertTrue(result.facts().stream()
                .anyMatch(f -> f.type() == FactType.COMMIT_DIFF_SUMMARY));
        assertFalse(result.facts().stream()
                .anyMatch(f -> f.type() == FactType.COMMIT_ADDS_FEATURE));
        assertFalse(result.facts().stream()
                .anyMatch(f -> f.type() == FactType.COMMIT_FIXES_BUG));
        assertFalse(result.facts().stream()
                .anyMatch(f -> f.type() == FactType.COMMIT_REFACTORS_CODE));
    }

    // --- helpers ---

    private ProjectCommit commit(String hash, String subject, int filesChanged,
            int insertions, int deletions, ChangedFile... changedFiles) {
        ProjectCommit commit = ProjectCommit.builder()
                .commitHash(hash)
                .subject(subject)
                .fullMessage(subject)
                .filesChanged(filesChanged)
                .insertions(insertions)
                .deletions(deletions)
                .committedAt(Instant.now())
                .importedAt(Instant.now())
                .rootCommit(false)
                .mergeCommit(false)
                .build();
        for (ChangedFile file : changedFiles) {
            commit.addChangedFile(file);
        }
        return commit;
    }

    private ChangedFile changedFile(String path, FileChangeType type, int insertions, int deletions) {
        return ChangedFile.builder()
                .changeType(type)
                .newPath(path)
                .insertions(insertions)
                .deletions(deletions)
                .binary(false)
                .build();
    }
}
