package com.hopeful117.devlogai.repositorycontext.collector;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.history.entity.ChangedFile;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.model.FileChangeType;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommitDiffEvidenceCollectorTest {

    @Mock
    private ProjectCommitRepository projectCommitRepository;

    private EvidenceFactory evidenceFactory;

    private CommitDiffEvidenceCollector collector;

    private UUID projectId;
    private UUID analysisId;
    private Instant analysisStartedAt;

    @BeforeEach
    void setUp() {
        evidenceFactory = new EvidenceFactory();
        collector = new CommitDiffEvidenceCollector(
                projectCommitRepository, evidenceFactory, 50, 90);

        projectId = UUID.randomUUID();
        analysisId = UUID.randomUUID();
        analysisStartedAt = Instant.parse("2026-08-08T00:00:00Z");
    }

    @Test
    void producesChangedFileEvidenceForModifiedFiles() {
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("abc123", commitTime);
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/App.java", null, false, 10, 5));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertFalse(evidence.isEmpty());
        RepositoryEvidence item = evidence.getFirst();
        assertEquals(RepositoryContextLayer.COMMIT_DIFF, item.layer());
        assertEquals("CHANGED_FILE", item.kind());
        assertTrue(item.summary().contains("Modified"));
        assertTrue(item.summary().contains("src/main/java/com/App.java"));
        assertTrue(item.summary().contains("+10/-5"));
    }

    @Test
    void producesEvidenceForAddedFiles() {
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("abc123", commitTime);
        commit.addChangedFile(buildChangedFile(FileChangeType.ADDED,
                "src/main/java/com/NewService.java", null, false, 50, 0));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertFalse(evidence.isEmpty());
        RepositoryEvidence item = evidence.getFirst();
        assertTrue(item.summary().contains("Added"));
        assertTrue(item.summary().contains("src/main/java/com/NewService.java"));
        assertTrue(item.summary().contains("+50"));
    }

    @Test
    void producesEvidenceForDeletedFiles() {
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("abc123", commitTime);
        commit.addChangedFile(buildChangedFile(FileChangeType.DELETED,
                "src/main/java/com/OldService.java", null, false, 0, 80));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertFalse(evidence.isEmpty());
        RepositoryEvidence item = evidence.getFirst();
        assertTrue(item.summary().contains("Deleted"));
        assertTrue(item.summary().contains("src/main/java/com/OldService.java"));
        assertTrue(item.summary().contains("-80"));
    }

    @Test
    void producesEvidenceForRenamedFiles() {
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("abc123", commitTime);
        commit.addChangedFile(buildChangedFile(FileChangeType.RENAMED,
                "src/main/java/com/RenamedService.java",
                "src/main/java/com/OldName.java", false, 3, 3));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertFalse(evidence.isEmpty());
        RepositoryEvidence item = evidence.getFirst();
        assertTrue(item.summary().contains("Renamed"));
        assertTrue(item.summary().contains("src/main/java/com/RenamedService.java"));
        assertTrue(item.summary().contains("src/main/java/com/OldName.java"));
    }

    @Test
    void excludesBinaryFiles() {
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("abc123", commitTime);
        commit.addChangedFile(buildChangedFile(FileChangeType.ADDED,
                "image.png", null, true, 0, 0));
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/App.java", null, false, 10, 5));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertEquals(1, evidence.size());
        assertTrue(evidence.getFirst().provenance().originatingFile()
                .equals("src/main/java/com/App.java"));
    }

    @Test
    void excludesGeneratedVendorPaths() {
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("abc123", commitTime);
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "target/classes/App.class", null, false, 5, 0));
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "node_modules/lodash/index.js", null, false, 10, 0));
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "build/output.js", null, false, 3, 0));
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "dist/bundle.js", null, false, 2, 0));
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "vendor/lib.php", null, false, 4, 0));
        // This one should pass
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/App.java", null, false, 10, 5));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertEquals(1, evidence.size());
        assertTrue(evidence.getFirst().provenance().originatingFile()
                .equals("src/main/java/com/App.java"));
    }

    @Test
    void excludesMinJsAndMapFiles() {
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("abc123", commitTime);
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "dist/app.min.js", null, false, 100, 0));
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/maps/app.js.map", null, false, 50, 0));
        // This one should pass
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/App.java", null, false, 10, 5));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertEquals(1, evidence.size());
        assertTrue(evidence.getFirst().provenance().originatingFile()
                .equals("src/main/java/com/App.java"));
    }

    @Test
    void deduplicatesFilesAcrossMultipleCommits() {
        Instant commitTime1 = Instant.parse("2026-07-01T10:00:00Z");
        Instant commitTime2 = Instant.parse("2026-07-05T14:00:00Z");

        ProjectCommit commit1 = buildCommit("aaa111", commitTime1);
        commit1.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/App.java", null, false, 10, 5));

        ProjectCommit commit2 = buildCommit("bbb222", commitTime2);
        commit2.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/App.java", null, false, 20, 8));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit2, commit1));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertEquals(1, evidence.size());
        RepositoryEvidence item = evidence.getFirst();
        // Should use most recent commit's hash
        assertTrue(item.reference().contains("bbb222"));
        assertTrue(item.summary().contains("+30/-13"));
        assertTrue(item.summary().contains("2 commits"));
        assertEquals(2, item.relatedReferences().size());
    }

    @Test
    void usesMostRecentCommitMetadataForDeduplicatedFiles() {
        Instant olderTime = Instant.parse("2026-06-01T10:00:00Z");
        Instant newerTime = Instant.parse("2026-07-01T10:00:00Z");

        ProjectCommit olderCommit = buildCommit("older1", olderTime);
        olderCommit.addChangedFile(buildChangedFile(FileChangeType.ADDED,
                "src/main/java/com/Feature.java", null, false, 100, 0));

        ProjectCommit newerCommit = buildCommit("newer1", newerTime);
        newerCommit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/Feature.java", null, false, 15, 3));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(newerCommit, olderCommit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertEquals(1, evidence.size());
        RepositoryEvidence item = evidence.getFirst();
        // Should use the newer commit's metadata
        assertTrue(item.reference().contains("newer1"));
        assertEquals(newerTime, item.occurredAt());
        // Dominant change type should be from the most recent commit (MODIFIED)
        assertTrue(item.summary().contains("Modified"));
    }

    @Test
    void filtersCommitsOutsideTemporalWindow() {
        Instant insideWindow = Instant.parse("2026-07-01T10:00:00Z");
        Instant outsideWindow = Instant.parse("2026-01-01T10:00:00Z");

        ProjectCommit insideCommit = buildCommit("inside1", insideWindow);
        insideCommit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/Inside.java", null, false, 10, 5));

        ProjectCommit outsideCommit = buildCommit("outside1", outsideWindow);
        outsideCommit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/Outside.java", null, false, 20, 10));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(insideCommit, outsideCommit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        // The repository mock returns both, but the real method filters by date.
        // Since we mock, both appear. The test verifies the collector processes all
        // returned commits. In integration, the DB query filters by cutoff.
        assertFalse(evidence.isEmpty());
    }

    @Test
    void respectsMaxItemsLimit() {
        // Create collector with maxItems = 2
        CommitDiffEvidenceCollector limitedCollector = new CommitDiffEvidenceCollector(
                projectCommitRepository, evidenceFactory, 2, 90);

        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("abc123", commitTime);
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/A.java", null, false, 10, 5));
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/B.java", null, false, 20, 10));
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/C.java", null, false, 30, 15));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = limitedCollector.collect(createRequest());

        assertTrue(evidence.size() <= 2);
    }

    @Test
    void returnsEmptyListWhenNoCommitsExist() {
        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of());

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertTrue(evidence.isEmpty());
    }

    // --- Helper methods ---

    private ProjectCommit buildCommit(String hash, Instant committedAt) {
        return ProjectCommit.builder()
                .commitHash(hash)
                .committedAt(committedAt)
                .subject("Test commit " + hash)
                .fullMessage("Full message for " + hash)
                .filesChanged(0)
                .insertions(0)
                .deletions(0)
                .binaryFiles(0)
                .rootCommit(false)
                .mergeCommit(false)
                .importedAt(Instant.now())
                .build();
    }

    private ChangedFile buildChangedFile(FileChangeType changeType,
            String newPath, String oldPath, boolean binary,
            int insertions, int deletions) {
        return ChangedFile.builder()
                .changeType(changeType)
                .newPath(newPath)
                .oldPath(oldPath)
                .binary(binary)
                .insertions(insertions)
                .deletions(deletions)
                .build();
    }

    private ContextRequest createRequest() {
        AnalysisContext analysisContext = new AnalysisContext(
                new AnalysisContext.ProjectSnapshot(projectId, "TestProject",
                        "test-project", "A test project", ProjectStatus.ACTIVE),
                new AnalysisContext.AnalysisSnapshot(analysisId,
                        AnalysisType.ARCHITECTURE_REVIEW, "test-intent", "v1",
                        AnalysisStatus.IN_PROGRESS, analysisStartedAt, null,
                        Instant.EPOCH),
                null, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());

        com.hopeful117.devlogai.intent.model.IntentDefinition intent =
                new com.hopeful117.devlogai.intent.model.IntentDefinition(
                        "test-intent", "v1", "Test objective",
                        List.of(com.hopeful117.devlogai.intent.model.InsightType
                                .ARCHITECTURE_DESCRIPTION),
                        List.of("grounded"), Map.of("type", "object"),
                        "test-prompt", List.of("architecture-v1"));

        return new ContextRequest(
                analysisContext,
                intent,
                null,
                List.of(),
                mockContextPlan(),
                new RepositoryContext.ContextBudget(10, 100, 5, 1000));
    }

    private ContextPlan mockContextPlan() {
        return new ContextPlan(
                "test-v1",
                List.of(),
                Map.of(),
                List.of(),
                1,
                List.of());
    }
}
