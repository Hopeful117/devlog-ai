package com.hopeful117.devlogai.knowledge.selection;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.diagnostics.entity.AnalysisExecutionDiagnostic;
import com.hopeful117.devlogai.analysis.diagnostics.repository.AnalysisExecutionDiagnosticRepository;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.intent.model.InsightType;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.observation.entity.ObservationType;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextService;
import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;



import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KnowledgeSelectionServiceTest {

    @Test
    void shouldDeterministicallyRankDeduplicateBudgetAndDigestSelection() {
        var diagnostics = mock(AnalysisExecutionDiagnosticRepository.class);
        var insights = mock(InsightRepository.class);
        var mapper = mock(ObjectMapper.class);
        var repositoryContexts = mock(RepositoryContextService.class);
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).collectionComplete(true).warningCount(1).build();
        when(diagnostics.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(insights.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of());
        when(mapper.writeValueAsString(any())).thenReturn("stable-canonical-selection");

        List<AnalysisContext.FactSnapshot> facts = new ArrayList<>();
        facts.add(fact(FactType.OTHER, "duplicate"));
        facts.add(fact(FactType.OTHER, "duplicate"));
        for (int index = 0; index < 45; index++) {
            facts.add(fact(index == 0 ? FactType.DOCKERFILE_PRESENT : FactType.OTHER,
                    "fact-" + index));
        }
        var observations = List.of(
                observation(ObservationType.OTHER),
                observation(ObservationType.ARCHITECTURE_MODULARIZATION));
        AnalysisContext context = new AnalysisContext(
                new AnalysisContext.ProjectSnapshot(projectId, "Project", "project", null,
                        ProjectStatus.ACTIVE),
                new AnalysisContext.AnalysisSnapshot(analysisId, AnalysisType.ARCHITECTURE_REVIEW,
                        "architecture-overview", "v1", AnalysisStatus.IN_PROGRESS,
                        Instant.EPOCH, null, Instant.EPOCH),
                mock(ProjectProfileResponse.class), facts, observations,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        IntentDefinition intent = new IntentDefinition("architecture-overview", "v1", "Architecture",
                List.of(InsightType.ARCHITECTURE_DESCRIPTION), List.of("grounded"),
                Map.of("type", "object"), "architecture-overview-prompt-v1");
        RepositoryContext repositoryContext = new RepositoryContext(
                "repository-context-engine-v1", ContextProfile.ARCHITECTURE_REVIEW,
                List.of("architecture-v1", "history-v1"),
                "context-intelligence-v1", List.of("test"),
                List.of(), Map.of(),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                0, 0, 0, false, List.of(), List.of(), "b".repeat(64));
        when(repositoryContexts.build(eq(context), eq(intent), isNull(), anyList()))
                .thenReturn(repositoryContext);
        var service = new KnowledgeSelectionServiceImpl(
                diagnostics, insights, mapper, repositoryContexts);

        SelectedKnowledge first = service.select(context, intent, null);
        SelectedKnowledge second = service.select(context, intent, null);

        assertEquals(first, second);
        assertEquals(40, first.selectedFacts().size());
        assertEquals(FactType.DOCKERFILE_PRESENT, first.selectedFacts().getFirst().type());
        assertEquals(1, first.selectedFacts().stream()
                .filter(value -> value.content().equals("duplicate")).count());
        assertEquals(ObservationType.ARCHITECTURE_MODULARIZATION,
                first.selectedObservations().getFirst().type());
        assertEquals("knowledge-selection-v3", first.selectionMetadata().selectionVersion());
        assertTrue(first.selectionMetadata().discardedKnowledgeCount() > 0);
        assertTrue(first.selectionDigest().matches("[0-9a-f]{64}"));
        assertEquals(repositoryContext, first.repositoryContext());
        assertTrue(first.selectionMetadata().appliedRules()
                .contains("REPOSITORY_FIRST_LAYERING"));
    }

    private AnalysisContext.FactSnapshot fact(FactType type, String content) {
        return new AnalysisContext.FactSnapshot(UUID.randomUUID(), type, content, "test",
                List.of("README.md"), Instant.EPOCH);
    }

    private AnalysisContext.ObservationSnapshot observation(ObservationType type) {
        return new AnalysisContext.ObservationSnapshot(UUID.randomUUID(), type, type.name(),
                "rule", "v1", List.of(), Instant.EPOCH);
    }

    @Test
    void shouldRankCommitScopedFactsHighestForEngineeringEventIntent() {
        var diagnostics = mock(AnalysisExecutionDiagnosticRepository.class);
        var insights = mock(InsightRepository.class);
        var mapper = mock(ObjectMapper.class);
        var repositoryContexts = mock(RepositoryContextService.class);
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).collectionComplete(true).warningCount(0).build();
        when(diagnostics.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(insights.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of());
        when(mapper.writeValueAsString(any())).thenReturn("stable-canonical-selection");

        List<AnalysisContext.FactSnapshot> facts = new ArrayList<>();
        // Non-commit facts (should score lower)
        facts.add(fact(FactType.SPRING_BOOT_DETECTED, "Spring Boot application"));
        facts.add(fact(FactType.DOCKERFILE_PRESENT, "Docker configuration"));
        facts.add(fact(FactType.BUILD_SYSTEM_DETECTED, "Maven build"));
        // Commit-scoped facts (should score higher)
        facts.add(fact(FactType.COMMIT_DIFF_SUMMARY, "5 commits: 12 files changed, +450/-120 lines"));
        facts.add(fact(FactType.COMMIT_CHANGES_MODULE, "Module backend/src: 8 files changed"));
        facts.add(fact(FactType.COMMIT_ADDS_FEATURE, "Feature: add login flow"));
        facts.add(fact(FactType.COMMIT_FIXES_BUG, "Bug fix: resolve NPE"));
        facts.add(fact(FactType.COMMIT_REFACTORS_CODE, "Refactoring: extract service"));

        var observations = List.of(
                observation(ObservationType.OTHER));
        AnalysisContext context = new AnalysisContext(
                new AnalysisContext.ProjectSnapshot(projectId, "Project", "project", null,
                        ProjectStatus.ACTIVE),
                new AnalysisContext.AnalysisSnapshot(analysisId, AnalysisType.ARCHITECTURE_REVIEW,
                        "analyze-engineering-event", "v1", AnalysisStatus.IN_PROGRESS,
                        Instant.EPOCH, null, Instant.EPOCH),
                mock(ProjectProfileResponse.class), facts, observations,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        IntentDefinition intent = new IntentDefinition(
                "analyze-engineering-event", "v1",
                "Proposer des Engineering Events",
                List.of(), List.of(), Map.of(),
                "analyze-engineering-event-prompt-v1",
                List.of());
        RepositoryContext repositoryContext = new RepositoryContext(
                "repository-context-engine-v1", ContextProfile.ARCHITECTURE_REVIEW,
                List.of("architecture-v1", "history-v1"),
                "context-intelligence-v1", List.of("test"),
                List.of(), Map.of(),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                0, 0, 0, false, List.of(), List.of(), "b".repeat(64));
        when(repositoryContexts.build(eq(context), eq(intent), isNull(), anyList()))
                .thenReturn(repositoryContext);
        var service = new KnowledgeSelectionServiceImpl(
                diagnostics, insights, mapper, repositoryContexts);

        SelectedKnowledge result = service.select(context, intent, null);

        // Commit-scoped facts should appear before non-commit facts
        List<FactType> selectedTypes = result.selectedFacts().stream()
                .map(AnalysisContext.FactSnapshot::type)
                .toList();
        boolean allCommitFirst = true;
        boolean seenNonCommit = false;
        for (FactType t : selectedTypes) {
            boolean isCommit = t.name().startsWith("COMMIT_");
            if (!isCommit) {
                seenNonCommit = true;
            } else if (seenNonCommit) {
                allCommitFirst = false;
                break;
            }
        }
        assertTrue(allCommitFirst,
                "Commit-scoped facts should rank before non-commit facts. Got: " + selectedTypes);
    }
}
