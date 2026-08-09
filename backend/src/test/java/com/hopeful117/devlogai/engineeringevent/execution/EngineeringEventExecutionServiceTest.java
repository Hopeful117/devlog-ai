package com.hopeful117.devlogai.engineeringevent.execution;

import com.hopeful117.devlogai.analysis.entity.*;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.analysis.workflow.AnalysisWorkflowService;
import com.hopeful117.devlogai.engineeringevent.*;
import com.hopeful117.devlogai.source.entity.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EngineeringEventExecutionServiceTest {
    @Mock EngineeringEventExecutionPreparationService preparation;
    @Mock EngineeringEventExecutionClaimService claims;
    @Mock AnalysisWorkflowService workflow;
    @Mock AnalysisRepository analyses;
    @InjectMocks EngineeringEventExecutionService service;

    @Test
    void startsOnlyANewlyClaimedExecutionAndReturnsItsExactBoundary() {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        EngineeringEventExecutionRequest request =
                new EngineeringEventExecutionRequest(sourceId, "b".repeat(40), null);
        PreparedEngineeringEventExecution prepared = mock(PreparedEngineeringEventExecution.class);
        Analysis analysis = Analysis.builder().id(analysisId).status(AnalysisStatus.IN_PROGRESS)
                .intentId("analyze-engineering-event").intentVersion("v1").build();
        Source source = Source.builder().id(sourceId).build();
        AnalysisEvolutionScope scope = AnalysisEvolutionScope.builder().analysisId(analysisId)
                .source(source).baseCommit("a".repeat(40)).targetCommit("b".repeat(40))
                .comparisonPolicy(EvolutionComparisonPolicy.FIRST_PARENT).mergeCommit(false).build();
        when(preparation.prepare(projectId, request)).thenReturn(prepared);
        when(claims.claim(prepared)).thenReturn(
                new EngineeringEventExecutionClaimService.Claim(analysis, true, scope));
        when(analyses.findById(analysisId)).thenReturn(Optional.of(analysis));

        EngineeringEventExecutionResponse response = service.execute(projectId, request);

        verify(workflow).start(analysisId);
        assertEquals(EngineeringEventExecutionResponse.Outcome.CREATED, response.outcome());
        assertEquals("a".repeat(40), response.baseCommit());
        assertEquals("b".repeat(40), response.targetCommit());
    }

    @Test
    void reusesAnActiveExecutionWithoutRestartingItsWorkflow() {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        EngineeringEventExecutionRequest request =
                new EngineeringEventExecutionRequest(sourceId, "b".repeat(40), null);
        PreparedEngineeringEventExecution prepared = mock(PreparedEngineeringEventExecution.class);
        Analysis analysis = Analysis.builder().id(analysisId).status(AnalysisStatus.IN_PROGRESS)
                .intentId("analyze-engineering-event").intentVersion("v1").build();
        Source source = Source.builder().id(sourceId).build();
        AnalysisEvolutionScope scope = AnalysisEvolutionScope.builder().analysisId(analysisId)
                .source(source).baseCommit("a".repeat(40)).targetCommit("b".repeat(40))
                .comparisonPolicy(EvolutionComparisonPolicy.FIRST_PARENT).mergeCommit(false).build();
        when(preparation.prepare(projectId, request)).thenReturn(prepared);
        when(claims.claim(prepared)).thenReturn(
                new EngineeringEventExecutionClaimService.Claim(analysis, false, scope));
        when(analyses.findById(analysisId)).thenReturn(Optional.of(analysis));

        EngineeringEventExecutionResponse response = service.execute(projectId, request);

        verifyNoInteractions(workflow);
        assertEquals(EngineeringEventExecutionResponse.Outcome.REUSED, response.outcome());
    }

    @Test
    void resolvesAConcurrentClaimConflictToTheWinningActiveExecution() {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        EngineeringEventExecutionRequest request =
                new EngineeringEventExecutionRequest(sourceId, "b".repeat(40), null);
        PreparedEngineeringEventExecution prepared = mock(PreparedEngineeringEventExecution.class);
        Analysis analysis = Analysis.builder().id(analysisId).status(AnalysisStatus.PENDING)
                .intentId("analyze-engineering-event").intentVersion("v1").build();
        AnalysisEvolutionScope scope = AnalysisEvolutionScope.builder().analysisId(analysisId)
                .source(Source.builder().id(sourceId).build()).baseCommit("a".repeat(40))
                .targetCommit("b".repeat(40)).comparisonPolicy(EvolutionComparisonPolicy.FIRST_PARENT)
                .mergeCommit(false).build();
        var winner = new EngineeringEventExecutionClaimService.Claim(analysis, false, scope);
        when(preparation.prepare(projectId, request)).thenReturn(prepared);
        when(claims.claim(prepared)).thenThrow(new DataIntegrityViolationException("concurrent claim"));
        when(claims.winner(prepared)).thenReturn(Optional.of(winner));
        when(analyses.findById(analysisId)).thenReturn(Optional.of(analysis));

        EngineeringEventExecutionResponse response = service.execute(projectId, request);

        assertEquals(analysisId, response.analysisId());
        assertEquals(EngineeringEventExecutionResponse.Outcome.REUSED, response.outcome());
    }
}
