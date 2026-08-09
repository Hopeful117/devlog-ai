package com.hopeful117.devlogai.projectunderstanding;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.analysis.workflow.AnalysisWorkflowService;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.projectunderstanding.dto.ProjectUnderstandingOutcome;
import com.hopeful117.devlogai.projectunderstanding.dto.ProjectUnderstandingRequest;
import com.hopeful117.devlogai.source.entity.Source;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectUnderstandingServiceTest {
    @Mock ProjectUnderstandingPreparationService preparation;
    @Mock ProjectUnderstandingClaimService claims;
    @Mock AnalysisWorkflowService workflow;
    @Mock AnalysisRepository analyses;
    private ProjectUnderstandingService service;
    private UUID projectId;
    private PreparedProjectUnderstanding prepared;
    private Analysis analysis;

    @BeforeEach
    void setUp() {
        service = new ProjectUnderstandingService(preparation, claims, workflow, analyses);
        projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        IntentDefinition intent = new IntentDefinition(
                "describe-project", "v1", "describe", List.of(), List.of(), Map.of(), "prompt");
        prepared = new PreparedProjectUnderstanding(projectId, sourceId, null, "abc", null,
                intent, Map.of("id", sourceId.toString(), "name", "Core"));
        analysis = Analysis.builder().id(UUID.randomUUID()).selectedSource(
                        Source.builder().id(sourceId).build())
                .selectedSourceSnapshot(prepared.sourceSnapshot()).status(AnalysisStatus.PENDING)
                .intentId(intent.id()).intentVersion(intent.version()).build();
        when(preparation.prepare(eq(projectId), any())).thenReturn(prepared);
    }

    @Test
    void startsANewClaimAndReturnsCreatedOutcome() {
        when(claims.claim(prepared)).thenReturn(
                new ProjectUnderstandingClaim(analysis, ProjectUnderstandingOutcome.CREATED));
        analysis.setStatus(AnalysisStatus.IN_PROGRESS);
        when(analyses.findById(analysis.getId())).thenReturn(Optional.of(analysis));

        var response = service.execute(projectId,
                new ProjectUnderstandingRequest(prepared.sourceId(), null, null));

        assertThat(response.outcome()).isEqualTo(ProjectUnderstandingOutcome.CREATED);
        assertThat(response.analysisId()).isEqualTo(analysis.getId());
        verify(workflow).start(analysis.getId());
    }

    @Test
    void reusesAnEquivalentActiveClaimWithoutRestartingIt() {
        when(claims.claim(prepared)).thenReturn(
                new ProjectUnderstandingClaim(analysis, ProjectUnderstandingOutcome.REUSED));

        var response = service.execute(projectId,
                new ProjectUnderstandingRequest(prepared.sourceId(), null, null));

        assertThat(response.outcome()).isEqualTo(ProjectUnderstandingOutcome.REUSED);
        verifyNoInteractions(workflow);
    }

    @Test
    void reloadsTheWinnerAfterAConcurrentUniqueKeyRace() {
        when(claims.claim(prepared)).thenThrow(new DataIntegrityViolationException("race"));
        when(claims.findWinner(prepared)).thenReturn(Optional.of(
                new ProjectUnderstandingClaim(analysis, ProjectUnderstandingOutcome.REUSED)));

        assertThat(service.execute(projectId,
                new ProjectUnderstandingRequest(prepared.sourceId(), null, null)).outcome())
                .isEqualTo(ProjectUnderstandingOutcome.REUSED);
        verifyNoInteractions(workflow);
    }

    @Test
    void marksANewPendingClaimFailedWhenWorkflowCannotStart() {
        when(claims.claim(prepared)).thenReturn(
                new ProjectUnderstandingClaim(analysis, ProjectUnderstandingOutcome.CREATED));
        doThrow(new IllegalStateException("start failed")).when(workflow).start(analysis.getId());

        assertThatThrownBy(() -> service.execute(projectId,
                new ProjectUnderstandingRequest(prepared.sourceId(), null, null)))
                .isInstanceOf(IllegalStateException.class);
        verify(claims).failPending(analysis.getId());
    }
}
