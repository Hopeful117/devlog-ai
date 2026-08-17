package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightStatus;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepositoryContextAdapterStatusExclusionTest {

    @Mock
    private ProjectContextProvider projectContextProvider;

    @Mock
    private RepositoryContextService repositoryContextService;

    @Mock
    private InsightRepository insightRepository;

    @InjectMocks
    private RepositoryContextAdapter adapter;

    private ProjectContextSnapshot snapshot(UUID projectId) {
        AnalysisContext.ProjectSnapshot project =
                new AnalysisContext.ProjectSnapshot(
                        projectId, "Test Project", "test-project",
                        null, ProjectStatus.ACTIVE);
        return new ProjectContextSnapshot(
                project, null, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void shouldForwardActiveInsightToRepositoryContext() {
        UUID projectId = UUID.randomUUID();
        Insight active = Insight.builder()
                .id(UUID.randomUUID())
                .analysis(com.hopeful117.devlogai.analysis.entity.Analysis.builder().id(UUID.randomUUID()).build())
                .proposal(com.hopeful117.devlogai.proposal.entity.ValidatableProposal.builder().id(UUID.randomUUID()).build())
                .type(InsightType.ARCHITECTURAL)
                .severity(InsightSeverity.INFO)
                .title("Active insight")
                .content("Current understanding")
                .sourceType("ARCHITECTURE_DESCRIPTION")
                .status(InsightStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();

        when(projectContextProvider.build(projectId)).thenReturn(snapshot(projectId));
        when(insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                projectId, List.of(InsightStatus.ACTIVE))).thenReturn(List.of(active));
        when(repositoryContextService.build(any(), any(), any(), anyList()))
                .thenReturn(mock(RepositoryContext.class));

        adapter.buildRepositoryContext(projectId, "Story description");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Insight>> captor = ArgumentCaptor.forClass(List.class);
        verify(repositoryContextService).build(any(), any(), any(), captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(InsightStatus.ACTIVE, captor.getValue().getFirst().getStatus());

        verify(insightRepository).findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                projectId, List.of(InsightStatus.ACTIVE));
        verify(insightRepository, never()).findByProjectIdOrderByCreatedAtDesc(any(UUID.class));
        verify(insightRepository, never()).findByProjectIdOrderByCreatedAtDescIdDesc(any(UUID.class));
    }

    @Test
    void shouldForwardEmptyWhenNoActiveInsightsAndNeverFallBackToUnfilteredQuery() {
        UUID projectId = UUID.randomUUID();

        when(projectContextProvider.build(projectId)).thenReturn(snapshot(projectId));
        when(insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                projectId, List.of(InsightStatus.ACTIVE))).thenReturn(List.of());
        when(repositoryContextService.build(any(), any(), any(), anyList()))
                .thenReturn(mock(RepositoryContext.class));

        adapter.buildRepositoryContext(projectId, "Story description");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Insight>> captor = ArgumentCaptor.forClass(List.class);
        verify(repositoryContextService).build(any(), any(), any(), captor.capture());
        assertTrue(captor.getValue().isEmpty(),
                "no ACTIVE insights — forwarded list must be empty");

        verify(insightRepository).findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                projectId, List.of(InsightStatus.ACTIVE));
        verify(insightRepository, never()).findByProjectIdOrderByCreatedAtDesc(any(UUID.class));
        verify(insightRepository, never()).findByProjectIdOrderByCreatedAtDescIdDesc(any(UUID.class));
    }
}
