package com.hopeful117.devlogai.knowledge.selection;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.diagnostics.entity.AnalysisExecutionDiagnostic;
import com.hopeful117.devlogai.analysis.diagnostics.repository.AnalysisExecutionDiagnosticRepository;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightStatus;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.profile.model.ProfileCompletenessStatus;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeSelectionServiceImplStatusExclusionTest {

    @Test
    void shouldIncludeActiveInsightInCurrentSelection() throws Exception {
        var diagnostics = mock(AnalysisExecutionDiagnosticRepository.class);
        var insights = mock(InsightRepository.class);
        var mapper = mock(ObjectMapper.class);
        var repositoryContexts = mock(RepositoryContextService.class);

        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).collectionComplete(true).warningCount(0).build();
        when(diagnostics.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(mapper.writeValueAsString(any())).thenReturn("stable");

        Insight active = Insight.builder()
                .id(UUID.randomUUID())
                .analysis(com.hopeful117.devlogai.analysis.entity.Analysis.builder().id(analysisId).build())
                .proposal(com.hopeful117.devlogai.proposal.entity.ValidatableProposal.builder().id(UUID.randomUUID()).build())
                .type(InsightType.ARCHITECTURAL)
                .severity(InsightSeverity.INFO)
                .title("Active architecture insight")
                .content("Current architecture understanding")
                .sourceType("ARCHITECTURE_DESCRIPTION")
                .status(InsightStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();
        when(insights.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                projectId, List.of(InsightStatus.ACTIVE))).thenReturn(List.of(active));

        RepositoryContext repoContext = new RepositoryContext(
                "v1", ContextProfile.ARCHITECTURE_REVIEW, List.of(), "v1", List.of(),
                List.of(), Map.of(), new RepositoryContext.ContextBudget(50, 200, 10, 10000),
                0, 0, 0, false, List.of(), List.of(), "digest");
        when(repositoryContexts.build(any(), any(), any(), anyList())).thenReturn(repoContext);

        var service = new KnowledgeSelectionServiceImpl(diagnostics, insights, mapper, repositoryContexts);
        var context = createContext(projectId, analysisId);
        var intent = new IntentDefinition("architecture-overview", "v1", "Architecture",
                List.of(), List.of(), Map.of(), "architecture-overview-prompt-v1");

        SelectedKnowledge result = service.select(context, intent, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Insight>> captor = ArgumentCaptor.forClass(List.class);
        verify(repositoryContexts).build(any(), any(), any(), captor.capture());

        assertEquals(1, result.selectedInsights().size());
        assertEquals(active.getId(), result.selectedInsights().getFirst().id());
        assertEquals(1, result.existingArchitectureKnowledge().size());
        assertEquals(1, captor.getValue().size());
        assertEquals(InsightStatus.ACTIVE, captor.getValue().getFirst().getStatus());

        verify(insights).findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                projectId, List.of(InsightStatus.ACTIVE));
        verify(insights, never()).findByProjectIdOrderByCreatedAtDesc(any(UUID.class));
        verify(insights, never()).findByProjectIdOrderByCreatedAtDescIdDesc(any(UUID.class));
    }

    @Test
    void shouldProduceEmptySelectionWhenNoActiveInsightsAndNeverFallBackToUnfilteredQuery() throws Exception {
        var diagnostics = mock(AnalysisExecutionDiagnosticRepository.class);
        var insights = mock(InsightRepository.class);
        var mapper = mock(ObjectMapper.class);
        var repositoryContexts = mock(RepositoryContextService.class);

        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).collectionComplete(true).warningCount(0).build();
        when(diagnostics.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(mapper.writeValueAsString(any())).thenReturn("stable");
        when(insights.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                projectId, List.of(InsightStatus.ACTIVE))).thenReturn(List.of());

        RepositoryContext repoContext = new RepositoryContext(
                "v1", ContextProfile.ARCHITECTURE_REVIEW, List.of(), "v1", List.of(),
                List.of(), Map.of(), new RepositoryContext.ContextBudget(50, 200, 10, 10000),
                0, 0, 0, false, List.of(), List.of(), "digest");
        when(repositoryContexts.build(any(), any(), any(), anyList())).thenReturn(repoContext);

        var service = new KnowledgeSelectionServiceImpl(diagnostics, insights, mapper, repositoryContexts);
        var context = createContext(projectId, analysisId);
        var intent = new IntentDefinition("architecture-overview", "v1", "Architecture",
                List.of(), List.of(), Map.of(), "architecture-overview-prompt-v1");

        SelectedKnowledge result = service.select(context, intent, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Insight>> captor = ArgumentCaptor.forClass(List.class);
        verify(repositoryContexts).build(any(), any(), any(), captor.capture());

        assertTrue(result.selectedInsights().isEmpty(),
                "no ACTIVE insights — selectedInsights must be empty");
        assertTrue(result.existingArchitectureKnowledge().isEmpty(),
                "no ACTIVE insights — existingArchitectureKnowledge must be empty");
        assertTrue(captor.getValue().isEmpty(),
                "no ACTIVE insights — forwarded list must be empty");
        assertNotNull(result.selectionDigest(),
                "SelectedKnowledge must still build with a digest even when empty");

        verify(insights).findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                projectId, List.of(InsightStatus.ACTIVE));
        verify(insights, never()).findByProjectIdOrderByCreatedAtDesc(any(UUID.class));
        verify(insights, never()).findByProjectIdOrderByCreatedAtDescIdDesc(any(UUID.class));
    }

    private static AnalysisContext createContext(UUID projectId, UUID analysisId) {
        return new AnalysisContext(
                new AnalysisContext.ProjectSnapshot(projectId, "Test", "test", "desc", ProjectStatus.ACTIVE),
                new AnalysisContext.AnalysisSnapshot(analysisId, AnalysisType.ARCHITECTURE_REVIEW,
                        "architecture-overview", "v1", AnalysisStatus.IN_PROGRESS,
                        Instant.EPOCH, null, Instant.EPOCH),
                new ProjectProfileResponse(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        "v1", "r1", Instant.now(), null, Map.of(),
                        new ProjectProfileResponse.Completeness(ProfileCompletenessStatus.COMPLETE, true, false, 0, 0, 1, 0, 0),
                        List.of(), "summary", List.of(), 0),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
