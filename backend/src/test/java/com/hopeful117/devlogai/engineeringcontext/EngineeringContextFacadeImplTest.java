package com.hopeful117.devlogai.engineeringcontext;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.engineeringcontext.mapper.EngineeringContextContractMapper;
import com.hopeful117.devlogai.project.dto.response.ProjectResponse;
import com.hopeful117.devlogai.project.service.ProjectService;
import com.hopeful117.devlogai.projectcontext.ProjectContextProvider;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.projectcontext.RepositoryContextAdapter;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessService;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessSummary;
import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextDiagnostics;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EngineeringContextFacadeImplTest {
    @Test
    void canonicalProductionPathIncludesRepositoryTokenBudget() {
        UUID projectId = UUID.randomUUID();
        ProjectResponse project = mock(ProjectResponse.class);
        ProjectContextSnapshot projectContext = mock(ProjectContextSnapshot.class);
        RepositoryContext repositoryContext = new RepositoryContext(
                "repository-context-engine-v1", ContextProfile.ENGINEERING_STORY,
                List.of(), "plan-v1", List.of(), List.of(), Map.of(),
                RepositoryContextDiagnostics.empty(),
                new RepositoryContext.ContextBudget(60, 500, 20, 4321),
                17, 2, 1, false, List.of(), List.of(), "repository-digest");
        EngineeringContext mapped = new EngineeringContext(null, "intent", List.of(), null, List.of(), null);

        ProjectService projectService = mock(ProjectService.class);
        ProjectContextProvider projectContextProvider = mock(ProjectContextProvider.class);
        RepositoryContextAdapter repositoryContextAdapter = mock(RepositoryContextAdapter.class);
        ProjectFreshnessService freshnessService = mock(ProjectFreshnessService.class);
        EngineeringContextContractMapper mapper = mock(EngineeringContextContractMapper.class);
        ProjectFreshnessSummary freshness = new ProjectFreshnessSummary(
                "freshness-v1", projectId, List.of(), 0, false);
        when(project.getId()).thenReturn(projectId);
        when(projectService.getBySlug("devlog-ai")).thenReturn(project);
        when(projectContextProvider.build(projectId)).thenReturn(projectContext);
        when(repositoryContextAdapter.buildRepositoryContext(
                projectId, "intent", projectContext, List.of(), null)).thenReturn(repositoryContext);
        when(freshnessService.summary(projectId)).thenReturn(freshness);
        when(mapper.toContract(projectContext, repositoryContext, "intent", List.of(), null,
                freshness)).thenReturn(mapped);

        EngineeringContextFacadeImpl facade = new EngineeringContextFacadeImpl(
                projectService, projectContextProvider, repositoryContextAdapter, freshnessService, mapper);

        CanonicalEngineeringContext canonical = facade.getCanonicalEngineeringContext(
                "devlog-ai", "intent", List.of(), null);

        assertEquals(4321, canonical.accounting().get("budget"));
        assertEquals(17, canonical.accounting().get("usedTokens"));
    }

    @Test
    void questionAwarePathForwardsQuestionToRepositorySelection() {
        UUID projectId = UUID.randomUUID();
        ProjectResponse project = mock(ProjectResponse.class);
        ProjectContextSnapshot projectContext = mock(ProjectContextSnapshot.class);
        RepositoryContext repositoryContext = new RepositoryContext(
                "repository-context-engine-v1", ContextProfile.ENGINEERING_STORY,
                List.of(), "plan-v1", List.of(), List.of(), Map.of(),
                RepositoryContextDiagnostics.empty(),
                new RepositoryContext.ContextBudget(60, 500, 20, 100),
                1, 0, 0, false, List.of(), List.of(), "repository-digest");
        EngineeringContext mapped = new EngineeringContext(null, "intent", List.of(), null, List.of(), null);
        ProjectService projectService = mock(ProjectService.class);
        ProjectContextProvider projectContextProvider = mock(ProjectContextProvider.class);
        RepositoryContextAdapter repositoryContextAdapter = mock(RepositoryContextAdapter.class);
        ProjectFreshnessService freshnessService = mock(ProjectFreshnessService.class);
        EngineeringContextContractMapper mapper = mock(EngineeringContextContractMapper.class);
        ProjectFreshnessSummary freshness = new ProjectFreshnessSummary("freshness-v1", projectId, List.of(), 0, false);
        when(project.getId()).thenReturn(projectId);
        when(projectService.getBySlug("devlog-ai")).thenReturn(project);
        when(projectContextProvider.build(projectId)).thenReturn(projectContext);
        when(repositoryContextAdapter.buildRepositoryContext(
                projectId, "intent", projectContext, List.of(), null, "question")).thenReturn(repositoryContext);
        when(freshnessService.summary(projectId)).thenReturn(freshness);
        when(mapper.toContract(projectContext, repositoryContext, "intent", List.of(), null, freshness))
                .thenReturn(mapped);

        EngineeringContextFacadeImpl facade = new EngineeringContextFacadeImpl(
                projectService, projectContextProvider, repositoryContextAdapter, freshnessService, mapper);

        facade.getCanonicalEngineeringContext("devlog-ai", "intent", List.of(), null, "question");

        verify(repositoryContextAdapter).buildRepositoryContext(
                projectId, "intent", projectContext, List.of(), null, "question");
    }
}
