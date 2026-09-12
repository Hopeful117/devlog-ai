package com.hopeful117.devlogai.knowledge.selection;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.diagnostics.entity.AnalysisExecutionDiagnostic;
import com.hopeful117.devlogai.analysis.diagnostics.repository.AnalysisExecutionDiagnosticRepository;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.insight.entity.InsightStatus;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.InsightType;
import com.hopeful117.devlogai.knowledge.selection.KnowledgeSelectionServiceImpl;
import com.hopeful117.devlogai.observation.entity.ObservationType;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.profile.model.ProfileCompletenessStatus;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextService;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence.EvidenceProvenance;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceScore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeSelectionServicePromoteCommitDiffTest {

    @Mock private AnalysisExecutionDiagnosticRepository diagnosticRepository;
    @Mock private InsightRepository insightRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private RepositoryContextService repositoryContextService;

    private KnowledgeSelectionServiceImpl createService() {
        return new KnowledgeSelectionServiceImpl(diagnosticRepository, insightRepository,
                objectMapper, repositoryContextService, 15);
    }

    private IntentDefinition architectureIntent() {
        return new IntentDefinition("architecture-overview", "v1", "Architecture",
                List.of(InsightType.ARCHITECTURE_DESCRIPTION), List.of("grounded"),
                Map.of("type", "object"), "architecture-overview-prompt-v1");
    }

    private AnalysisContext.AnalysisSnapshot testAnalysis() {
        return new AnalysisContext.AnalysisSnapshot(
                UUID.randomUUID(), AnalysisType.ARCHITECTURE_REVIEW, "architecture-overview", "v1",
                AnalysisStatus.IN_PROGRESS, Instant.EPOCH, null, Instant.EPOCH);
    }

    private ProjectProfileResponse testProfile() {
        return new ProjectProfileResponse(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "v1", "r1", Instant.now(), null, Map.of(),
                new ProjectProfileResponse.Completeness(ProfileCompletenessStatus.COMPLETE, true, false, 0, 0, 1, 0, 0),
                List.of(), "summary", List.of(), 0);
    }

    private AnalysisContext createMinimalContext(AnalysisContext.AnalysisSnapshot analysis) {
        return new AnalysisContext(
                new AnalysisContext.ProjectSnapshot(UUID.randomUUID(), "Test", "test", "desc", ProjectStatus.ACTIVE),
                analysis, testProfile(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private void mockRepositories(AnalysisContext context) throws Exception {
        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(context.analysis().id())
                .collectionComplete(true).truncated(false)
                .warningCount(0).errorCount(0)
                .build();
        when(diagnosticRepository.findById(context.analysis().id())).thenReturn(Optional.of(diagnostic));
        when(insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                context.project().id(), List.of(InsightStatus.ACTIVE))).thenReturn(List.of());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    private RepositoryEvidence commitDiffEvidence(String reference, int score) {
        return new RepositoryEvidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                reference, "Summary for " + reference,
                Instant.EPOCH,
                new EvidenceScore("test", Map.of(), Map.of(), score, List.of()),
                List.of(),
                new EvidenceProvenance("DETERMINISTIC_EXTRACTION", "repository", reference, reference),
                Map.of(), 100, List.of());
    }

    private RepositoryContext emptyRepositoryContext() {
        return new RepositoryContext(
                "v1", ContextProfile.ARCHITECTURE_REVIEW, List.of(), "v1", List.of(),
                List.of(), Map.of(), new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                0, 0, 0, false, List.of(), List.of(), "b".repeat(64));
    }

    @Test
    void promoteCommitDiffCandidatesFiltersForCommitDiffLayer() throws Exception {
        var service = createService();
        var context = createMinimalContext(testAnalysis());
        mockRepositories(context);

        RepositoryEvidence commitDiff = commitDiffEvidence("diff:abc123:src/App.java", 85);
        RepositoryEvidence gitHistory = new RepositoryEvidence(
                RepositoryContextLayer.GIT_HISTORY, "GIT_COMMIT",
                "git:abc123", "Git history", Instant.EPOCH,
                new EvidenceScore("test", Map.of(), Map.of(), 70, List.of()),
                List.of(),
                new EvidenceProvenance("GIT", "repository", "git:abc123", "git:abc123"),
                Map.of(), 50, List.of());

        RepositoryContext repoContextWithBoth = new RepositoryContext(
                "v1", ContextProfile.ARCHITECTURE_REVIEW, List.of(), "v1", List.of(),
                List.of(commitDiff, gitHistory), Map.of(),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                100, 2, 0, false, List.of(), List.of(), "digest");

        when(repositoryContextService.build(eq(context), eq(architectureIntent()), isNull(), anyList(), anyList(), any()))
                .thenReturn(repoContextWithBoth);
        when(repositoryContextService.retrieveCandidates(eq(context), eq(architectureIntent()), isNull(), anyList()))
                .thenReturn(List.of(commitDiff, gitHistory));

        SelectedKnowledge result = service.select(context, architectureIntent(), null);

        // Only COMMIT_DIFF evidence should be promoted
        ArgumentCaptor<List<RepositoryEvidence>> promotedCaptor = ArgumentCaptor.forClass(List.class);
        verify(repositoryContextService).build(eq(context), eq(architectureIntent()), isNull(), anyList(),
                promotedCaptor.capture(), any());
        List<RepositoryEvidence> promoted = promotedCaptor.getValue();
        assertEquals(1, promoted.size());
        assertEquals("diff:abc123:src/App.java", promoted.getFirst().reference());
    }

    @Test
    void promoteCommitDiffCandidatesRespectsMaximumItems() throws Exception {
        var service = createService();
        var context = createMinimalContext(testAnalysis());
        mockRepositories(context);

        // Create more COMMIT_DIFF candidates than max (15)
        List<RepositoryEvidence> manyCommitDiffs = List.of(
                commitDiffEvidence("diff:1:src/A.java", 90),
                commitDiffEvidence("diff:2:src/B.java", 85),
                commitDiffEvidence("diff:3:src/C.java", 80),
                commitDiffEvidence("diff:4:src/D.java", 75),
                commitDiffEvidence("diff:5:src/E.java", 70),
                commitDiffEvidence("diff:6:src/F.java", 65),
                commitDiffEvidence("diff:7:src/G.java", 60),
                commitDiffEvidence("diff:8:src/H.java", 55),
                commitDiffEvidence("diff:9:src/I.java", 50),
                commitDiffEvidence("diff:10:src/J.java", 45),
                commitDiffEvidence("diff:11:src/K.java", 40),
                commitDiffEvidence("diff:12:src/L.java", 35),
                commitDiffEvidence("diff:13:src/M.java", 30),
                commitDiffEvidence("diff:14:src/N.java", 25),
                commitDiffEvidence("diff:15:src/O.java", 20),
                commitDiffEvidence("diff:16:src/P.java", 15)
        );

        RepositoryContext repoContextWithMany = new RepositoryContext(
                "v1", ContextProfile.ARCHITECTURE_REVIEW, List.of(), "v1", List.of(),
                manyCommitDiffs, Map.of(),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                100, manyCommitDiffs.size(), 0, false, List.of(), List.of(), "digest");

        when(repositoryContextService.build(eq(context), eq(architectureIntent()), isNull(), anyList(), anyList(), any()))
                .thenReturn(repoContextWithMany);
        when(repositoryContextService.retrieveCandidates(eq(context), eq(architectureIntent()), isNull(), anyList()))
                .thenReturn(manyCommitDiffs);

        SelectedKnowledge result = service.select(context, architectureIntent(), null);

        ArgumentCaptor<List<RepositoryEvidence>> promotedCaptor = ArgumentCaptor.forClass(List.class);
        verify(repositoryContextService).build(eq(context), eq(architectureIntent()), isNull(), anyList(),
                promotedCaptor.capture(), any());
        List<RepositoryEvidence> promoted = promotedCaptor.getValue();
        assertEquals(15, promoted.size(),
                "Should respect maximumPromotedCommitDiffCandidates=15");
    }

    @Test
    void promoteCommitDiffCandidatesDeduplicatesByReference() throws Exception {
        var service = createService();
        var context = createMinimalContext(testAnalysis());
        mockRepositories(context);

        RepositoryEvidence commitDiff1 = commitDiffEvidence("diff:abc123:src/App.java", 90);
        RepositoryEvidence commitDiff2 = commitDiffEvidence("diff:abc123:src/App.java", 85); // duplicate reference

        RepositoryContext repoContextWithDuplicates = new RepositoryContext(
                "v1", ContextProfile.ARCHITECTURE_REVIEW, List.of(), "v1", List.of(),
                List.of(commitDiff1, commitDiff2), Map.of(),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                100, 2, 0, false, List.of(), List.of(), "digest");

        when(repositoryContextService.build(eq(context), eq(architectureIntent()), isNull(), anyList(), anyList(), any()))
                .thenReturn(repoContextWithDuplicates);
        when(repositoryContextService.retrieveCandidates(eq(context), eq(architectureIntent()), isNull(), anyList()))
                .thenReturn(List.of(commitDiff1, commitDiff2));

        SelectedKnowledge result = service.select(context, architectureIntent(), null);

        ArgumentCaptor<List<RepositoryEvidence>> promotedCaptor = ArgumentCaptor.forClass(List.class);
        verify(repositoryContextService).build(eq(context), eq(architectureIntent()), isNull(), anyList(),
                promotedCaptor.capture(), any());
        List<RepositoryEvidence> promoted = promotedCaptor.getValue();
        assertEquals(1, promoted.size(),
                "Duplicate references must be deduplicated");
    }

    @Test
    void promoteCommitDiffCandidatesPreservesRetrieveCandidatesOrder() throws Exception {
        var service = createService();
        var context = createMinimalContext(testAnalysis());
        mockRepositories(context);

        RepositoryEvidence lowScore = commitDiffEvidence("diff:low:src/App.java", 30);
        RepositoryEvidence highScore = commitDiffEvidence("diff:high:src/Service.java", 90);
        RepositoryEvidence midScore = commitDiffEvidence("diff:mid:src/Other.java", 60);

        // The order in retrieveCandidates determines promotion order (no re-sorting)
        List<RepositoryEvidence> retrieveOrder = List.of(lowScore, highScore, midScore);

        RepositoryContext repoContext = new RepositoryContext(
                "v1", ContextProfile.ARCHITECTURE_REVIEW, List.of(), "v1", List.of(),
                retrieveOrder, Map.of(),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                100, 3, 0, false, List.of(), List.of(), "digest");

        when(repositoryContextService.build(eq(context), eq(architectureIntent()), isNull(), anyList(), anyList(), any()))
                .thenReturn(repoContext);
        when(repositoryContextService.retrieveCandidates(eq(context), eq(architectureIntent()), isNull(), anyList()))
                .thenReturn(retrieveOrder);

        SelectedKnowledge result = service.select(context, architectureIntent(), null);

        ArgumentCaptor<List<RepositoryEvidence>> promotedCaptor = ArgumentCaptor.forClass(List.class);
        verify(repositoryContextService).build(eq(context), eq(architectureIntent()), isNull(), anyList(),
                promotedCaptor.capture(), any());
        List<RepositoryEvidence> promoted = promotedCaptor.getValue();
        // Should preserve the order from retrieveCandidates
        assertEquals("diff:low:src/App.java", promoted.getFirst().reference());
        assertEquals("diff:high:src/Service.java", promoted.get(1).reference());
        assertEquals("diff:mid:src/Other.java", promoted.get(2).reference());
    }

    @Test
    void promoteCommitDiffCandidatesEmptyWhenNoCommitDiffInCandidates() throws Exception {
        var service = createService();
        var context = createMinimalContext(testAnalysis());
        mockRepositories(context);

        RepositoryEvidence gitHistory = new RepositoryEvidence(
                RepositoryContextLayer.GIT_HISTORY, "GIT_COMMIT",
                "git:abc123", "Git history", Instant.EPOCH,
                new EvidenceScore("test", Map.of(), Map.of(), 70, List.of()),
                List.of(),
                new EvidenceProvenance("GIT", "repository", "git:abc123", "git:abc123"),
                Map.of(), 50, List.of());

        RepositoryContext repoContextNoCommitDiff = new RepositoryContext(
                "v1", ContextProfile.ARCHITECTURE_REVIEW, List.of(), "v1", List.of(),
                List.of(gitHistory), Map.of(),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                100, 1, 0, false, List.of(), List.of(), "digest");

        when(repositoryContextService.build(eq(context), eq(architectureIntent()), isNull(), anyList(), anyList(), any()))
                .thenReturn(repoContextNoCommitDiff);
        when(repositoryContextService.retrieveCandidates(eq(context), eq(architectureIntent()), isNull(), anyList()))
                .thenReturn(List.of(gitHistory));

        SelectedKnowledge result = service.select(context, architectureIntent(), null);

        ArgumentCaptor<List<RepositoryEvidence>> promotedCaptor = ArgumentCaptor.forClass(List.class);
        verify(repositoryContextService).build(eq(context), eq(architectureIntent()), isNull(), anyList(),
                promotedCaptor.capture(), any());
        List<RepositoryEvidence> promoted = promotedCaptor.getValue();
        assertTrue(promoted.isEmpty(),
                "Should return empty list when no COMMIT_DIFF candidates");
    }

    // ──────────────────────────────────────────────────────────────
    // History → Evidence Contract Audit (WP3)
    // ──────────────────────────────────────────────────────────────

    @Test
    void promoteCommitDiffPreservesSourceIdentity() throws Exception {
        var service = createService();
        var context = createMinimalContext(testAnalysis());
        mockRepositories(context);

        String sourceId = "source-123";
        RepositoryEvidence commitDiff = new RepositoryEvidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:abc123:src/App.java", "Summary",
                Instant.EPOCH,
                new EvidenceScore("test", Map.of(), Map.of(), 85, List.of()),
                List.of("diff:parent:src/App.java"),
                new EvidenceProvenance("DETERMINISTIC_EXTRACTION", sourceId, "src/App.java", "abc123"),
                Map.of("collectorId", "commit-diff", "collectorVersion", "v1"),
                100, List.of());

        RepositoryContext repoContext = new RepositoryContext(
                "v1", ContextProfile.ARCHITECTURE_REVIEW, List.of(), "v1", List.of(),
                List.of(commitDiff), Map.of(),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                100, 1, 0, false, List.of(), List.of(), "digest");

        when(repositoryContextService.build(eq(context), eq(architectureIntent()), isNull(), anyList(), anyList(), any()))
                .thenReturn(repoContext);
        when(repositoryContextService.retrieveCandidates(eq(context), eq(architectureIntent()), isNull(), anyList()))
                .thenReturn(List.of(commitDiff));

        SelectedKnowledge result = service.select(context, architectureIntent(), null);

        ArgumentCaptor<List<RepositoryEvidence>> promotedCaptor = ArgumentCaptor.forClass(List.class);
        verify(repositoryContextService).build(eq(context), eq(architectureIntent()), isNull(), anyList(),
                promotedCaptor.capture(), any());
        List<RepositoryEvidence> promoted = promotedCaptor.getValue();

        // Source identity should be preserved in provenance
        assertEquals(sourceId, promoted.getFirst().provenance().repositoryLocation());
        assertEquals("DETERMINISTIC_EXTRACTION", promoted.getFirst().provenance().sourceType());
    }

    @Test
    void promoteCommitDiffPreservesCommitHashInReference() throws Exception {
        var service = createService();
        var context = createMinimalContext(testAnalysis());
        mockRepositories(context);

        String commitHash = "abc123def456";
        String filePath = "src/main/java/com/Feature.java";
        String reference = "diff:" + commitHash + ":" + filePath;

        RepositoryEvidence commitDiff = new RepositoryEvidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                reference, "Summary",
                Instant.EPOCH,
                new EvidenceScore("test", Map.of(), Map.of(), 85, List.of()),
                List.of(),
                new EvidenceProvenance("DETERMINISTIC_EXTRACTION", "repository", filePath, commitHash),
                Map.of(), 100, List.of());

        RepositoryContext repoContext = new RepositoryContext(
                "v1", ContextProfile.ARCHITECTURE_REVIEW, List.of(), "v1", List.of(),
                List.of(commitDiff), Map.of(),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                100, 1, 0, false, List.of(), List.of(), "digest");

        when(repositoryContextService.build(eq(context), eq(architectureIntent()), isNull(), anyList(), anyList(), any()))
                .thenReturn(repoContext);
        when(repositoryContextService.retrieveCandidates(eq(context), eq(architectureIntent()), isNull(), anyList()))
                .thenReturn(List.of(commitDiff));

        SelectedKnowledge result = service.select(context, architectureIntent(), null);

        ArgumentCaptor<List<RepositoryEvidence>> promotedCaptor = ArgumentCaptor.forClass(List.class);
        verify(repositoryContextService).build(eq(context), eq(architectureIntent()), isNull(), anyList(),
                promotedCaptor.capture(), any());
        List<RepositoryEvidence> promoted = promotedCaptor.getValue();

        // Reference should preserve commit hash and file path
        assertEquals(reference, promoted.getFirst().reference());
        // Identifier in provenance should be commit hash
        assertEquals(commitHash, promoted.getFirst().provenance().identifier());
    }

    @Test
    void promoteCommitDiffPreservesTimestamp() throws Exception {
        var service = createService();
        var context = createMinimalContext(testAnalysis());
        mockRepositories(context);

        Instant commitTime = Instant.parse("2026-07-01T10:00:00Z");
        RepositoryEvidence commitDiff = new RepositoryEvidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:abc123:src/App.java", "Summary",
                commitTime,
                new EvidenceScore("test", Map.of(), Map.of(), 85, List.of()),
                List.of(),
                new EvidenceProvenance("DETERMINISTIC_EXTRACTION", "repository", "src/App.java", "abc123"),
                Map.of(), 100, List.of());

        RepositoryContext repoContext = new RepositoryContext(
                "v1", ContextProfile.ARCHITECTURE_REVIEW, List.of(), "v1", List.of(),
                List.of(commitDiff), Map.of(),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                100, 1, 0, false, List.of(), List.of(), "digest");

        when(repositoryContextService.build(eq(context), eq(architectureIntent()), isNull(), anyList(), anyList(), any()))
                .thenReturn(repoContext);
        when(repositoryContextService.retrieveCandidates(eq(context), eq(architectureIntent()), isNull(), anyList()))
                .thenReturn(List.of(commitDiff));

        SelectedKnowledge result = service.select(context, architectureIntent(), null);

        ArgumentCaptor<List<RepositoryEvidence>> promotedCaptor = ArgumentCaptor.forClass(List.class);
        verify(repositoryContextService).build(eq(context), eq(architectureIntent()), isNull(), anyList(),
                promotedCaptor.capture(), any());
        List<RepositoryEvidence> promoted = promotedCaptor.getValue();

        // Timestamp should be preserved
        assertEquals(commitTime, promoted.getFirst().occurredAt());
    }

    @Test
    void promoteCommitDiffPreservesParentReferences() throws Exception {
        var service = createService();
        var context = createMinimalContext(testAnalysis());
        mockRepositories(context);

        RepositoryEvidence commitDiff = new RepositoryEvidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:abc123:src/App.java", "Summary",
                Instant.EPOCH,
                new EvidenceScore("test", Map.of(), Map.of(), 85, List.of()),
                List.of("diff:parent1:src/App.java", "diff:parent2:src/App.java"),
                new EvidenceProvenance("DETERMINISTIC_EXTRACTION", "repository", "src/App.java", "abc123"),
                Map.of(), 100, List.of());

        RepositoryContext repoContext = new RepositoryContext(
                "v1", ContextProfile.ARCHITECTURE_REVIEW, List.of(), "v1", List.of(),
                List.of(commitDiff), Map.of(),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                100, 1, 0, false, List.of(), List.of(), "digest");

        when(repositoryContextService.build(eq(context), eq(architectureIntent()), isNull(), anyList(), anyList(), any()))
                .thenReturn(repoContext);
        when(repositoryContextService.retrieveCandidates(eq(context), eq(architectureIntent()), isNull(), anyList()))
                .thenReturn(List.of(commitDiff));

        SelectedKnowledge result = service.select(context, architectureIntent(), null);

        ArgumentCaptor<List<RepositoryEvidence>> promotedCaptor = ArgumentCaptor.forClass(List.class);
        verify(repositoryContextService).build(eq(context), eq(architectureIntent()), isNull(), anyList(),
                promotedCaptor.capture(), any());
        List<RepositoryEvidence> promoted = promotedCaptor.getValue();

        // Parent references should be preserved
        assertEquals(2, promoted.getFirst().relatedReferences().size());
        assertTrue(promoted.getFirst().relatedReferences().contains("diff:parent1:src/App.java"));
        assertTrue(promoted.getFirst().relatedReferences().contains("diff:parent2:src/App.java"));
    }

    @Test
    void promoteCommitDiffPreservesLayerAndKind() throws Exception {
        var service = createService();
        var context = createMinimalContext(testAnalysis());
        mockRepositories(context);

        RepositoryEvidence commitDiff = new RepositoryEvidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:abc123:src/App.java", "Summary",
                Instant.EPOCH,
                new EvidenceScore("test", Map.of(), Map.of(), 85, List.of()),
                List.of(),
                new EvidenceProvenance("DETERMINISTIC_EXTRACTION", "repository", "src/App.java", "abc123"),
                Map.of(), 100, List.of());

        RepositoryContext repoContext = new RepositoryContext(
                "v1", ContextProfile.ARCHITECTURE_REVIEW, List.of(), "v1", List.of(),
                List.of(commitDiff), Map.of(),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                100, 1, 0, false, List.of(), List.of(), "digest");

        when(repositoryContextService.build(eq(context), eq(architectureIntent()), isNull(), anyList(), anyList(), any()))
                .thenReturn(repoContext);
        when(repositoryContextService.retrieveCandidates(eq(context), eq(architectureIntent()), isNull(), anyList()))
                .thenReturn(List.of(commitDiff));

        SelectedKnowledge result = service.select(context, architectureIntent(), null);

        ArgumentCaptor<List<RepositoryEvidence>> promotedCaptor = ArgumentCaptor.forClass(List.class);
        verify(repositoryContextService).build(eq(context), eq(architectureIntent()), isNull(), anyList(),
                promotedCaptor.capture(), any());
        List<RepositoryEvidence> promoted = promotedCaptor.getValue();

        assertEquals(RepositoryContextLayer.COMMIT_DIFF, promoted.getFirst().layer());
        assertEquals("CHANGED_FILE", promoted.getFirst().kind());
    }

    @Test
    void promoteCommitDiffPreservesProvenance() throws Exception {
        var service = createService();
        var context = createMinimalContext(testAnalysis());
        mockRepositories(context);

        RepositoryEvidence commitDiff = new RepositoryEvidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:abc123:src/App.java", "Summary",
                Instant.EPOCH,
                new EvidenceScore("test", Map.of(), Map.of(), 85, List.of()),
                List.of(),
                new EvidenceProvenance("DETERMINISTIC_EXTRACTION", "repository", "src/App.java", "abc123"),
                Map.of(), 100, List.of());

        RepositoryContext repoContext = new RepositoryContext(
                "v1", ContextProfile.ARCHITECTURE_REVIEW, List.of(), "v1", List.of(),
                List.of(commitDiff), Map.of(),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                100, 1, 0, false, List.of(), List.of(), "digest");

        when(repositoryContextService.build(eq(context), eq(architectureIntent()), isNull(), anyList(), anyList(), any()))
                .thenReturn(repoContext);
        when(repositoryContextService.retrieveCandidates(eq(context), eq(architectureIntent()), isNull(), anyList()))
                .thenReturn(List.of(commitDiff));

        SelectedKnowledge result = service.select(context, architectureIntent(), null);

        ArgumentCaptor<List<RepositoryEvidence>> promotedCaptor = ArgumentCaptor.forClass(List.class);
        verify(repositoryContextService).build(eq(context), eq(architectureIntent()), isNull(), anyList(),
                promotedCaptor.capture(), any());
        List<RepositoryEvidence> promoted = promotedCaptor.getValue();

        // Provenance fields should be preserved
        assertEquals("DETERMINISTIC_EXTRACTION", promoted.getFirst().provenance().sourceType());
        assertEquals("repository", promoted.getFirst().provenance().repositoryLocation());
        assertEquals("src/App.java", promoted.getFirst().provenance().originatingFile());
        assertEquals("abc123", promoted.getFirst().provenance().identifier());
    }

    @Test
    void promoteCommitDiffPreservesExtractionMetadata() throws Exception {
        var service = createService();
        var context = createMinimalContext(testAnalysis());
        mockRepositories(context);

        Map<String, String> extractionMeta = Map.of(
                "collectorId", "commit-diff",
                "collectorVersion", "v1",
                "customKey", "customValue");

        RepositoryEvidence commitDiff = new RepositoryEvidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:abc123:src/App.java", "Summary",
                Instant.EPOCH,
                new EvidenceScore("test", Map.of(), Map.of(), 85, List.of()),
                List.of(),
                new EvidenceProvenance("DETERMINISTIC_EXTRACTION", "repository", "src/App.java", "abc123"),
                extractionMeta, 100, List.of());

        RepositoryContext repoContext = new RepositoryContext(
                "v1", ContextProfile.ARCHITECTURE_REVIEW, List.of(), "v1", List.of(),
                List.of(commitDiff), Map.of(),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                100, 1, 0, false, List.of(), List.of(), "digest");

        when(repositoryContextService.build(eq(context), eq(architectureIntent()), isNull(), anyList(), anyList(), any()))
                .thenReturn(repoContext);
        when(repositoryContextService.retrieveCandidates(eq(context), eq(architectureIntent()), isNull(), anyList()))
                .thenReturn(List.of(commitDiff));

        SelectedKnowledge result = service.select(context, architectureIntent(), null);

        ArgumentCaptor<List<RepositoryEvidence>> promotedCaptor = ArgumentCaptor.forClass(List.class);
        verify(repositoryContextService).build(eq(context), eq(architectureIntent()), isNull(), anyList(),
                promotedCaptor.capture(), any());
        List<RepositoryEvidence> promoted = promotedCaptor.getValue();

        // Extraction metadata should be preserved
        assertEquals("commit-diff", promoted.getFirst().extractionMetadata().get("collectorId"));
        assertEquals("v1", promoted.getFirst().extractionMetadata().get("collectorVersion"));
        assertEquals("customValue", promoted.getFirst().extractionMetadata().get("customKey"));
    }

    @Test
    void promoteCommitDiffSurvivesSelectionIntoRepositoryContext() throws Exception {
        // Verify that promoted commit-diff evidence survives the full selection
        // pipeline and appears in the final RepositoryContext
        var service = createService();
        var context = createMinimalContext(testAnalysis());
        mockRepositories(context);

        RepositoryEvidence commitDiff = commitDiffEvidence("diff:abc123:src/Promoted.java", 95);

        RepositoryContext repoContext = new RepositoryContext(
                "v1", ContextProfile.ARCHITECTURE_REVIEW, List.of(), "v1", List.of(),
                List.of(commitDiff), Map.of(),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                100, 1, 0, false, List.of(), List.of(), "digest");

        when(repositoryContextService.build(eq(context), eq(architectureIntent()), isNull(), anyList(), anyList(), any()))
                .thenReturn(repoContext);
        when(repositoryContextService.retrieveCandidates(eq(context), eq(architectureIntent()), isNull(), anyList()))
                .thenReturn(List.of(commitDiff));

        SelectedKnowledge result = service.select(context, architectureIntent(), null);

        // The promoted evidence should appear in the final RepositoryContext
        assertNotNull(result.repositoryContext());
        assertTrue(result.repositoryContext().evidence().stream()
                .anyMatch(e -> e.layer() == RepositoryContextLayer.COMMIT_DIFF
                        && e.reference().equals("diff:abc123:src/Promoted.java")));
    }
}