package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightStatus;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.knowledge.relation.entity.EntityType;
import com.hopeful117.devlogai.knowledge.relation.entity.KnowledgeRelationType;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositoryContextAdapterTest {

    @Mock
    ProjectContextProvider projectContextProvider;

    @Mock
    RepositoryContextService repositoryContextService;

    @Mock
    InsightRepository insightRepository;

    @InjectMocks
    RepositoryContextAdapter adapter;

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

    private ProjectContextSnapshot snapshotWithKnowledge(UUID projectId) {
        AnalysisContext.ProjectSnapshot project =
                new AnalysisContext.ProjectSnapshot(
                        projectId, "Test Project", "test-project",
                        null, ProjectStatus.ACTIVE);

        ProjectContextSnapshot.EngineeringEventSnapshot event =
                new ProjectContextSnapshot.EngineeringEventSnapshot(
                        UUID.randomUUID(), "FEATURE_INTRODUCTION", "Added auth",
                        "Authentication module", UUID.randomUUID(),
                        "abc123", "def456", Instant.now(), UUID.randomUUID());

        ProjectContextSnapshot.ChallengeSnapshot challenge =
                new ProjectContextSnapshot.ChallengeSnapshot(
                        UUID.randomUUID(), "Performance", "Slow queries",
                        "High", "OPEN", null, Instant.now());

        ProjectContextSnapshot.KnowledgeRelationSnapshot relation =
                new ProjectContextSnapshot.KnowledgeRelationSnapshot(
                        UUID.randomUUID(), EntityType.CHALLENGE, UUID.randomUUID(),
                        EntityType.DECISION, UUID.randomUUID(),
                        KnowledgeRelationType.INFORMED_BY, "Performance informed decision",
                        Instant.now());

        return new ProjectContextSnapshot(
                project, null, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(event), List.of(challenge), List.of(relation), List.of());
    }

    @Test
    void shouldBuildRepositoryContextWithStoryDescription() {
        UUID projectId = UUID.randomUUID();
        String description = "Add authentication module";
        RepositoryContext expected = mock(RepositoryContext.class);

        when(projectContextProvider.build(projectId))
                .thenReturn(snapshot(projectId));
        when(insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                projectId, List.of(InsightStatus.ACTIVE)))
                .thenReturn(List.of());
        when(repositoryContextService.build(
                any(), any(), any(), any())).thenReturn(expected);

        RepositoryContext result =
                adapter.buildRepositoryContext(projectId, description);

        assertNotNull(result);
        assertEquals(expected, result);

        ArgumentCaptor<AnalysisContext> contextCaptor =
                ArgumentCaptor.forClass(AnalysisContext.class);
        verify(repositoryContextService).build(
                contextCaptor.capture(), any(), any(), any());

        AnalysisContext ctx = contextCaptor.getValue();
        assertEquals(projectId, ctx.project().id());
        assertEquals("engineering-story-preparation",
                ctx.analysis().intentId());
        assertEquals("v1", ctx.analysis().intentVersion());
    }

    @Test
    void shouldBuildRepositoryContextWithNullDescription() {
        UUID projectId = UUID.randomUUID();
        RepositoryContext expected = mock(RepositoryContext.class);

        when(projectContextProvider.build(projectId))
                .thenReturn(snapshot(projectId));
        when(insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                projectId, List.of(InsightStatus.ACTIVE)))
                .thenReturn(List.of());
        when(repositoryContextService.build(
                any(), any(), isNull(), any())).thenReturn(expected);

        RepositoryContext result =
                adapter.buildRepositoryContext(projectId, null);

        assertNotNull(result);
        assertEquals(expected, result);
    }

    @Test
    void shouldLoadInsightsByProjectId() {
        UUID projectId = UUID.randomUUID();
        Insight insight1 = mock(Insight.class);
        Insight insight2 = mock(Insight.class);
        RepositoryContext expected = mock(RepositoryContext.class);

        when(projectContextProvider.build(projectId))
                .thenReturn(snapshot(projectId));
        when(insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                projectId, List.of(InsightStatus.ACTIVE)))
                .thenReturn(List.of(insight1, insight2));
        when(repositoryContextService.build(
                any(), any(), any(), any())).thenReturn(expected);

        adapter.buildRepositoryContext(projectId, "Test description");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Insight>> insightsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(repositoryContextService).build(
                any(), any(), any(), insightsCaptor.capture());
        assertEquals(2, insightsCaptor.getValue().size());
    }

    @Test
    void shouldPassIntentDefinitionWithEngineeringStoryProfile() {
        UUID projectId = UUID.randomUUID();
        RepositoryContext expected = mock(RepositoryContext.class);

        when(projectContextProvider.build(projectId))
                .thenReturn(snapshot(projectId));
        when(insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                projectId, List.of(InsightStatus.ACTIVE)))
                .thenReturn(List.of());
        when(repositoryContextService.build(
                any(), any(), any(), any())).thenReturn(expected);

        adapter.buildRepositoryContext(projectId, "Test");

        ArgumentCaptor<com.hopeful117.devlogai.intent.model.IntentDefinition>
                intentCaptor = ArgumentCaptor.forClass(
                com.hopeful117.devlogai.intent.model.IntentDefinition.class);
        verify(repositoryContextService).build(
                any(), intentCaptor.capture(), any(), any());

        com.hopeful117.devlogai.intent.model.IntentDefinition intent =
                intentCaptor.getValue();
        assertEquals("engineering-story-preparation", intent.id());
        assertEquals("v1", intent.version());
        assertEquals(List.of("engineering-story-v1"), intent.contextProfiles());
    }

    @Test
    void shouldPropagateValidatedEngineeringEventsToAnalysisContext() {
        UUID projectId = UUID.randomUUID();
        RepositoryContext expected = mock(RepositoryContext.class);

        when(projectContextProvider.build(projectId))
                .thenReturn(snapshotWithKnowledge(projectId));
        when(insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                projectId, List.of(InsightStatus.ACTIVE)))
                .thenReturn(List.of());
        when(repositoryContextService.build(
                any(), any(), any(), any())).thenReturn(expected);

        adapter.buildRepositoryContext(projectId, "Test");

        ArgumentCaptor<AnalysisContext> contextCaptor =
                ArgumentCaptor.forClass(AnalysisContext.class);
        verify(repositoryContextService).build(
                contextCaptor.capture(), any(), any(), any());

        AnalysisContext ctx = contextCaptor.getValue();
        assertEquals(1, ctx.validatedEngineeringEvents().size());
        assertEquals("FEATURE_INTRODUCTION",
                ctx.validatedEngineeringEvents().getFirst().category());
    }

    @Test
    void shouldPropagateOpenChallengesToAnalysisContext() {
        UUID projectId = UUID.randomUUID();
        RepositoryContext expected = mock(RepositoryContext.class);

        when(projectContextProvider.build(projectId))
                .thenReturn(snapshotWithKnowledge(projectId));
        when(insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                projectId, List.of(InsightStatus.ACTIVE)))
                .thenReturn(List.of());
        when(repositoryContextService.build(
                any(), any(), any(), any())).thenReturn(expected);

        adapter.buildRepositoryContext(projectId, "Test");

        ArgumentCaptor<AnalysisContext> contextCaptor =
                ArgumentCaptor.forClass(AnalysisContext.class);
        verify(repositoryContextService).build(
                contextCaptor.capture(), any(), any(), any());

        AnalysisContext ctx = contextCaptor.getValue();
        assertEquals(1, ctx.openChallenges().size());
        assertEquals("Performance", ctx.openChallenges().getFirst().title());
        assertEquals("OPEN", ctx.openChallenges().getFirst().status());
    }

    @Test
    void shouldPropagateKnowledgeRelationsToAnalysisContext() {
        UUID projectId = UUID.randomUUID();
        RepositoryContext expected = mock(RepositoryContext.class);

        when(projectContextProvider.build(projectId))
                .thenReturn(snapshotWithKnowledge(projectId));
        when(insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                projectId, List.of(InsightStatus.ACTIVE)))
                .thenReturn(List.of());
        when(repositoryContextService.build(
                any(), any(), any(), any())).thenReturn(expected);

        adapter.buildRepositoryContext(projectId, "Test");

        ArgumentCaptor<AnalysisContext> contextCaptor =
                ArgumentCaptor.forClass(AnalysisContext.class);
        verify(repositoryContextService).build(
                contextCaptor.capture(), any(), any(), any());

        AnalysisContext ctx = contextCaptor.getValue();
        assertEquals(1, ctx.knowledgeRelations().size());
        assertEquals(KnowledgeRelationType.INFORMED_BY,
                ctx.knowledgeRelations().getFirst().relationType());
        assertEquals(EntityType.CHALLENGE,
                ctx.knowledgeRelations().getFirst().sourceEntityType());
    }

    @Test
    void shouldForwardActiveInsightsToRepositoryContext() {
        UUID projectId = UUID.randomUUID();
        Insight active = Insight.builder()
                .id(UUID.randomUUID())
                .analysis(com.hopeful117.devlogai.analysis.entity.Analysis.builder().id(UUID.randomUUID()).build())
                .proposal(com.hopeful117.devlogai.proposal.entity.ValidatableProposal.builder().id(UUID.randomUUID()).build())
                .type(com.hopeful117.devlogai.insight.entity.InsightType.ARCHITECTURAL)
                .severity(com.hopeful117.devlogai.insight.entity.InsightSeverity.INFO)
                .title("Active insight")
                .content("Current understanding")
                .status(InsightStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();
        RepositoryContext expected = mock(RepositoryContext.class);

        when(projectContextProvider.build(projectId))
                .thenReturn(snapshot(projectId));
        when(insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                projectId, List.of(InsightStatus.ACTIVE)))
                .thenReturn(List.of(active));
        when(repositoryContextService.build(
                any(), any(), any(), any())).thenReturn(expected);

        adapter.buildRepositoryContext(projectId, "Test");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Insight>> insightsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(repositoryContextService).build(
                any(), any(), any(), insightsCaptor.capture());
        assertEquals(1, insightsCaptor.getValue().size());
        assertEquals(InsightStatus.ACTIVE, insightsCaptor.getValue().getFirst().getStatus());
    }

    @Test
    void shouldForwardEmptyActiveInsightsWithoutFallback() {
        UUID projectId = UUID.randomUUID();
        RepositoryContext expected = mock(RepositoryContext.class);

        when(projectContextProvider.build(projectId))
                .thenReturn(snapshot(projectId));
        when(insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                projectId, List.of(InsightStatus.ACTIVE)))
                .thenReturn(List.of());
        when(repositoryContextService.build(
                any(), any(), any(), any())).thenReturn(expected);

        adapter.buildRepositoryContext(projectId, "Test");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Insight>> insightsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(repositoryContextService).build(
                any(), any(), any(), insightsCaptor.capture());
        assertEquals(0, insightsCaptor.getValue().size());
    }
}
