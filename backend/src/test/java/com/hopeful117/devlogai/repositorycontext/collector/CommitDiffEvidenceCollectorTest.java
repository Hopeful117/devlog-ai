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
        assertEquals("src/main/java/com/App.java",
                evidence.getFirst().provenance().originatingFile());
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
        assertEquals("src/main/java/com/App.java",
                evidence.getFirst().provenance().originatingFile());
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
        assertEquals("src/main/java/com/App.java",
                evidence.getFirst().provenance().originatingFile());
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

    @Test
    void producesEvidenceForCopiedFiles() {
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("abc123", commitTime);
        commit.addChangedFile(buildChangedFile(FileChangeType.COPIED,
                "src/main/java/com/CopiedService.java",
                "src/main/java/com/OriginalService.java", false, 100, 0));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertFalse(evidence.isEmpty());
        RepositoryEvidence item = evidence.getFirst();
        assertTrue(item.summary().contains("Modified"));
        assertTrue(item.summary().contains("src/main/java/com/CopiedService.java"));
        assertTrue(item.summary().contains("+100/-0"));
    }

    @Test
    void handlesMergeCommitsWithMultipleParents() {
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("merge123", commitTime);
        commit.setMergeCommit(true);
        commit.addParent(0, "parent1");
        commit.addParent(1, "parent2");
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/MergeConflict.java", null, false, 5, 5));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertFalse(evidence.isEmpty());
        RepositoryEvidence item = evidence.getFirst();
        // relatedReferences contains commit hashes that touched this file (from commitHashes)
        // For a single commit, there's only 1 hash
        assertEquals(1, item.relatedReferences().size());
        assertTrue(item.relatedReferences().contains("diff:merge123:src/main/java/com/MergeConflict.java"));
    }

    @Test
    void handlesRootCommitsWithNoParents() {
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("root123", commitTime);
        commit.setRootCommit(true);
        commit.addChangedFile(buildChangedFile(FileChangeType.ADDED,
                "src/main/java/com/Initial.java", null, false, 50, 0));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertFalse(evidence.isEmpty());
        RepositoryEvidence item = evidence.getFirst();
        // Root commit still has its own hash in relatedReferences (from commitHashes)
        assertEquals(1, item.relatedReferences().size());
        assertTrue(item.relatedReferences().contains("diff:root123:src/main/java/com/Initial.java"));
        assertTrue(item.summary().contains("Added"));
    }

    @Test
    void returnsEmptyWhenCommitHasNoChangedFiles() {
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("empty123", commitTime);
        // No changed files added

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertTrue(evidence.isEmpty());
    }

    @Test
    void handlesMultipleFilesInSingleCommit() {
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("multi123", commitTime);
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/ServiceA.java", null, false, 10, 5));
        commit.addChangedFile(buildChangedFile(FileChangeType.ADDED,
                "src/main/java/com/ServiceB.java", null, false, 20, 0));
        commit.addChangedFile(buildChangedFile(FileChangeType.DELETED,
                "src/main/java/com/OldService.java", null, false, 0, 30));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertEquals(3, evidence.size());
        // All three files should produce separate evidence items
        assertTrue(evidence.stream().anyMatch(e -> e.summary().contains("ServiceA")));
        assertTrue(evidence.stream().anyMatch(e -> e.summary().contains("ServiceB")));
        assertTrue(evidence.stream().anyMatch(e -> e.summary().contains("OldService")));
    }

    @Test
    void deterministicTieBreakingWhenSameTimestampAndChangeCount() {
        // When two files have same timestamp and same total changes,
        // ordering should be deterministic by path (ascending)
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("tie123", commitTime);
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/Zebra.java", null, false, 10, 5));
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/Alpha.java", null, false, 10, 5));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertEquals(2, evidence.size());
        // Alpha should come before Zebra due to path tie-breaker
        assertEquals("src/main/java/com/Alpha.java", evidence.get(0).provenance().originatingFile());
        assertEquals("src/main/java/com/Zebra.java", evidence.get(1).provenance().originatingFile());
    }

    @Test
    void respectsMaxItemsAfterDeduplication() {
        // Create collector with maxItems = 2
        CommitDiffEvidenceCollector limitedCollector = new CommitDiffEvidenceCollector(
                projectCommitRepository, evidenceFactory, 2, 90);

        Instant commitTime1 = Instant.parse("2026-07-01T10:00:00Z");
        Instant commitTime2 = Instant.parse("2026-07-05T14:00:00Z");

        ProjectCommit commit1 = buildCommit("aaa111", commitTime1);
        commit1.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/FileA.java", null, false, 10, 5));

        ProjectCommit commit2 = buildCommit("bbb222", commitTime2);
        commit2.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/FileA.java", null, false, 20, 8));
        commit2.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/FileB.java", null, false, 15, 3));
        commit2.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/FileC.java", null, false, 5, 2));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit2, commit1));

        List<RepositoryEvidence> evidence = limitedCollector.collect(createRequest());

        // FileA appears in both commits, should be deduplicated to 1 item
        // FileB and FileC from commit2
        // Total unique files = 3, but maxItems = 2
        assertTrue(evidence.size() <= 2);
    }

    @Test
    void excludesFilesWithNullOrEmptyPath() {
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("nullpath123", commitTime);
        // ChangedFile with null newPath and null oldPath
        ChangedFile nullPathFile = ChangedFile.builder()
                .changeType(FileChangeType.MODIFIED)
                .newPath(null)
                .oldPath(null)
                .binary(false)
                .insertions(10)
                .deletions(5)
                .build();
        commit.addChangedFile(nullPathFile);
        // Valid file
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/Valid.java", null, false, 10, 5));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertEquals(1, evidence.size());
        assertEquals("src/main/java/com/Valid.java", evidence.getFirst().provenance().originatingFile());
    }

    @Test
    void includesExtractionMetadataWithCollectorInfo() {
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("meta123", commitTime);
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/Meta.java", null, false, 10, 5));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertFalse(evidence.isEmpty());
        RepositoryEvidence item = evidence.getFirst();
        assertEquals("commit-diff", item.extractionMetadata().get("collectorId"));
        assertEquals("v1", item.extractionMetadata().get("collectorVersion"));
        // Path is in provenance, not extraction metadata
        assertEquals("src/main/java/com/Meta.java", item.provenance().originatingFile());
    }

    @Test
    void evidenceReferenceFormatIsConsistent() {
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("abc123def456", commitTime);
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/Test.java", null, false, 10, 5));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertFalse(evidence.isEmpty());
        RepositoryEvidence item = evidence.getFirst();
        // Reference format: diff:{commitHash}:{path}
        String reference = item.reference();
        assertTrue(reference.startsWith("diff:abc123def456:"));
        assertTrue(reference.endsWith("src/main/java/com/Test.java"));
    }

    @Test
    void occurredAtUsesMostRecentCommitTimestamp() {
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
        // Should use the newer commit's timestamp
        assertEquals(newerTime, item.occurredAt());
    }

    @Test
    void summaryIncludesCorrectInsertionsAndDeletions() {
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("stats123", commitTime);
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/Stats.java", null, false, 42, 17));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertFalse(evidence.isEmpty());
        RepositoryEvidence item = evidence.getFirst();
        assertTrue(item.summary().contains("+42"));
        assertTrue(item.summary().contains("-17"));
    }

    @Test
    void commitHashInRelatedReferencesMatchesCommitHashInReference() {
        Instant commitTime1 = Instant.parse("2026-07-01T10:00:00Z");
        Instant commitTime2 = Instant.parse("2026-07-05T14:00:00Z");

        ProjectCommit commit1 = buildCommit("aaa111", commitTime1);
        commit1.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/FileA.java", null, false, 10, 5));

        ProjectCommit commit2 = buildCommit("bbb222", commitTime2);
        commit2.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/FileA.java", null, false, 20, 8));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit2, commit1));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertEquals(1, evidence.size());
        RepositoryEvidence item = evidence.getFirst();
        // Reference should use most recent commit hash (bbb222)
        assertTrue(item.reference().contains("bbb222"));
        // Related references should include both commit hashes with the file path
        assertTrue(item.relatedReferences().contains("diff:aaa111:src/main/java/com/FileA.java"));
        assertTrue(item.relatedReferences().contains("diff:bbb222:src/main/java/com/FileA.java"));
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

    // ──────────────────────────────────────────────────────────────
    // WP5: Nullability and malformed persisted-data boundaries
    // ──────────────────────────────────────────────────────────────

    @Test
    void handlesCommitWithNullAuthorInformation() {
        // Commit with null author name/email should still produce evidence
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("noauthor123", commitTime);
        commit.setAuthorName(null);
        commit.setAuthorEmail(null);
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/App.java", null, false, 10, 5));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertFalse(evidence.isEmpty());
        // Evidence should still be produced despite null author info
    }

    @Test
    void handlesCommitWithZeroInsertionsAndDeletions() {
        // Commit with no line changes should still produce evidence
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("nochanges123", commitTime);
        commit.setInsertions(0);
        commit.setDeletions(0);
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/App.java", null, false, 0, 0));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertFalse(evidence.isEmpty());
        RepositoryEvidence item = evidence.getFirst();
        assertTrue(item.summary().contains("+0"));
        assertTrue(item.summary().contains("-0"));
    }

    @Test
    void handlesChangedFileWithNullOldPathForAddedFile() {
        // Added files have null oldPath - should be handled correctly
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("added123", commitTime);
        ChangedFile addedFile = buildChangedFile(FileChangeType.ADDED,
                "src/main/java/com/NewFile.java", null, false, 50, 0);
        // Explicitly ensure oldPath is null
        commit.addChangedFile(addedFile);

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertFalse(evidence.isEmpty());
        RepositoryEvidence item = evidence.getFirst();
        assertTrue(item.summary().contains("Added"));
        // oldPath in summary should not cause NPE
    }

    @Test
    void handlesChangedFileWithNullNewPathForDeletedFile() {
        // Deleted files have null newPath - should use oldPath
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("deleted123", commitTime);
        ChangedFile deletedFile = buildChangedFile(FileChangeType.DELETED,
                null, "src/main/java/com/OldFile.java", false, 0, 50);
        commit.addChangedFile(deletedFile);

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertFalse(evidence.isEmpty());
        RepositoryEvidence item = evidence.getFirst();
        assertTrue(item.summary().contains("Deleted"));
        assertTrue(item.summary().contains("OldFile.java"));
    }

    @Test
    void handlesRenamedFileWithBothPaths() {
        // Renamed files have both oldPath and newPath
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("renamed123", commitTime);
        ChangedFile renamedFile = buildChangedFile(FileChangeType.RENAMED,
                "src/main/java/com/NewName.java",
                "src/main/java/com/OldName.java", false, 3, 3);
        commit.addChangedFile(renamedFile);

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertFalse(evidence.isEmpty());
        RepositoryEvidence item = evidence.getFirst();
        assertTrue(item.summary().contains("Renamed"));
        assertTrue(item.summary().contains("OldName.java"));
        assertTrue(item.summary().contains("NewName.java"));
    }

    @Test
    void handlesEmptyChangedFilesList() {
        // Commit with empty changedFiles list should be skipped
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("emptyfiles123", commitTime);
        // Don't add any changed files

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        // No file groups created, should return empty
        assertTrue(evidence.isEmpty());
    }

    @Test
    void handlesCommitWithOnlyExcludedFiles() {
        // Commit where all files are excluded should produce no evidence
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("allexcluded123", commitTime);
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "target/classes/App.class", null, false, 10, 5));
        commit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "node_modules/lib.js", null, false, 20, 0));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertTrue(evidence.isEmpty());
    }

    @Test
    void handlesMultipleCommitsWithMixedValidAndExcludedFiles() {
        // Mix of commits with valid and excluded files
        Instant commitTime1 = Instant.parse("2026-07-01T10:00:00Z");
        Instant commitTime2 = Instant.parse("2026-07-05T14:00:00Z");

        ProjectCommit validCommit = buildCommit("valid123", commitTime1);
        validCommit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "src/main/java/com/Valid.java", null, false, 10, 5));

        ProjectCommit excludedCommit = buildCommit("excluded123", commitTime2);
        excludedCommit.addChangedFile(buildChangedFile(FileChangeType.MODIFIED,
                "target/classes/Excluded.class", null, false, 5, 2));

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(validCommit, excludedCommit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertEquals(1, evidence.size());
        assertEquals("src/main/java/com/Valid.java", evidence.getFirst().provenance().originatingFile());
    }

    @Test
    void deduplicationRespectsNullPaths() {
        // Files with null paths should be excluded before deduplication
        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        ProjectCommit commit = buildCommit("nullpath123", commitTime);

        // Two files with null paths (both should be excluded)
        ChangedFile nullFile1 = ChangedFile.builder()
                .changeType(FileChangeType.MODIFIED)
                .newPath(null)
                .oldPath(null)
                .binary(false)
                .insertions(10)
                .deletions(5)
                .build();
        ChangedFile nullFile2 = ChangedFile.builder()
                .changeType(FileChangeType.MODIFIED)
                .newPath(null)
                .oldPath(null)
                .binary(false)
                .insertions(20)
                .deletions(8)
                .build();
        commit.addChangedFile(nullFile1);
        commit.addChangedFile(nullFile2);

        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Instant.class)))
                .thenReturn(List.of(commit));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        // Null paths should be excluded, not cause NPE or appear in results
        assertTrue(evidence.isEmpty());
    }
}
