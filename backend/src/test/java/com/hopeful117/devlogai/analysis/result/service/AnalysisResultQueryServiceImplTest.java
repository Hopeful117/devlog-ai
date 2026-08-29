package com.hopeful117.devlogai.analysis.result.service;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.analysis.result.dto.AnalysisResultResponse;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse;
import com.hopeful117.devlogai.analysis.evidence.service.AiTaskSelectedEvidenceService;
import com.hopeful117.devlogai.analysis.diagnostics.service.AnalysisDiagnosticsService;
import com.hopeful117.devlogai.decision.entity.Decision;
import com.hopeful117.devlogai.decision.repository.DecisionRepository;
import com.hopeful117.devlogai.deliverable.repository.GeneratedDeliverableRepository;
import com.hopeful117.devlogai.engineeringevent.EngineeringEvent;
import com.hopeful117.devlogai.engineeringevent.EngineeringEventCategory;
import com.hopeful117.devlogai.engineeringevent.EngineeringEventRepository;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightStatus;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisResultQueryServiceImplTest {

    @Mock AnalysisRepository analysisRepository;
    @Mock AiTaskRepository aiTaskRepository;
    @Mock ValidatableProposalRepository proposalRepository;
    @Mock InsightRepository insightRepository;
    @Mock DecisionRepository decisionRepository;
    @Mock EngineeringEventRepository engineeringEventRepository;
    @Mock GeneratedDeliverableRepository deliverableRepository;
    @Mock SourceRepository sourceRepository;
    @Mock AiTaskSelectedEvidenceService selectedEvidenceService;
    @Mock AnalysisDiagnosticsService diagnosticsService;
    @Mock IntentCatalog intentCatalog;

    @InjectMocks AnalysisResultQueryServiceImpl service;

    @Test
    void resolvesAcceptedArtifactsAndPreservesProposalStatesWithoutNPlusOneQueries() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Analysis analysis = analysis(analysisId, projectId);

        ValidatableProposal proposed = proposal(UUID.randomUUID(), ProposalType.INSIGHT, ProposalStatus.PROPOSED);
        ValidatableProposal acceptedInsight = proposal(UUID.randomUUID(), ProposalType.INSIGHT, ProposalStatus.ACCEPTED);
        ValidatableProposal acceptedDecision = proposal(UUID.randomUUID(), ProposalType.ENGINEERING_DECISION, ProposalStatus.ACCEPTED);
        ValidatableProposal acceptedEvent = proposal(UUID.randomUUID(), ProposalType.ENGINEERING_EVENT, ProposalStatus.ACCEPTED);
        ValidatableProposal unresolvedDecision = proposal(UUID.randomUUID(), ProposalType.ENGINEERING_DECISION, ProposalStatus.ACCEPTED);
        ValidatableProposal rejected = proposal(UUID.randomUUID(), ProposalType.INSIGHT, ProposalStatus.REJECTED);

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(intentCatalog.resolve("architecture-overview", "v1"))
                .thenReturn(new IntentDefinition("architecture-overview", "v1", "Understand the system", List.of(), List.of(), Map.of(), "prompt"));
        when(proposalRepository.findByAnalysisId(analysisId)).thenReturn(new ArrayList<>(List.of(
                proposed, acceptedInsight, acceptedDecision, acceptedEvent, unresolvedDecision, rejected)));
        when(insightRepository.findByProposalIdIn(List.of(acceptedInsight.getId())))
                .thenReturn(List.of(Insight.builder().id(UUID.randomUUID()).proposal(acceptedInsight).status(InsightStatus.ACTIVE)
                        .type(InsightType.ARCHITECTURAL).severity(InsightSeverity.WARNING).title("Insight").content("content").build()));
        doReturn(List.of(Decision.builder().id(UUID.randomUUID()).proposal(acceptedDecision).title("Decision")
                .context("context").choice("choice").rationale("rationale").build()))
                .when(decisionRepository).findByProposalIdIn(anyCollection());
        when(engineeringEventRepository.findByProposalIdIn(List.of(acceptedEvent.getId())))
                .thenReturn(List.of(EngineeringEvent.builder().id(UUID.randomUUID()).proposal(acceptedEvent)
                        .category(EngineeringEventCategory.ENGINEERING_IMPROVEMENT).title("Event").summary("summary")
                        .significance("significance").baseCommit("a").targetCommit("b").occurredAt(Instant.now()).createdAt(Instant.now()).build()));
        when(insightRepository.findByAnalysisIdOrderByCreatedAtDesc(analysisId)).thenReturn(List.of());
        when(deliverableRepository.findByAnalysisIdOrderByGeneratedAtDesc(analysisId)).thenReturn(List.of());
        when(selectedEvidenceService.getSelectedEvidence(analysisId)).thenReturn(AiTaskSelectedEvidenceResponse.noAiTask(analysisId, projectId));
        when(sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(projectId)).thenReturn(List.of());

        AnalysisResultResponse result = service.getResult(analysisId);

        assertEquals(5, result.proposals().items().size());
        assertFalse(result.proposals().items().stream().anyMatch(item -> item.status() == ProposalStatus.REJECTED));

        AnalysisResultResponse.ProposalSummary proposedSummary = summaryFor(result, proposed.getId());
        assertNull(proposedSummary.trustedArtifact());

        AnalysisResultResponse.ProposalSummary insightSummary = summaryFor(result, acceptedInsight.getId());
        assertNotNull(insightSummary.trustedArtifact());
        assertEquals(AnalysisResultResponse.TrustedArtifactType.INSIGHT, insightSummary.trustedArtifact().type());
        assertEquals(AnalysisResultResponse.TrustedArtifactAvailability.AVAILABLE, insightSummary.trustedArtifact().availability());
        assertTrue(insightSummary.trustedArtifact().detailAvailable());

        AnalysisResultResponse.ProposalSummary decisionSummary = summaryFor(result, acceptedDecision.getId());
        assertEquals(AnalysisResultResponse.TrustedArtifactType.DECISION, decisionSummary.trustedArtifact().type());
        assertEquals(AnalysisResultResponse.TrustedArtifactAvailability.AVAILABLE, decisionSummary.trustedArtifact().availability());

        AnalysisResultResponse.ProposalSummary eventSummary = summaryFor(result, acceptedEvent.getId());
        assertEquals(AnalysisResultResponse.TrustedArtifactType.ENGINEERING_EVENT, eventSummary.trustedArtifact().type());
        assertEquals(AnalysisResultResponse.TrustedArtifactAvailability.AVAILABLE, eventSummary.trustedArtifact().availability());

        AnalysisResultResponse.ProposalSummary unresolvedSummary = summaryFor(result, unresolvedDecision.getId());
        assertEquals(AnalysisResultResponse.TrustedArtifactType.DECISION, unresolvedSummary.trustedArtifact().type());
        assertEquals(AnalysisResultResponse.TrustedArtifactAvailability.UNAVAILABLE, unresolvedSummary.trustedArtifact().availability());
        assertNull(unresolvedSummary.trustedArtifact().id());
        assertFalse(unresolvedSummary.trustedArtifact().detailAvailable());

        verify(insightRepository).findByProposalIdIn(List.of(acceptedInsight.getId()));
        verify(decisionRepository).findByProposalIdIn(argThat(proposalIds ->
                proposalIds.size() == 2
                        && proposalIds.contains(acceptedDecision.getId())
                        && proposalIds.contains(unresolvedDecision.getId())));
        verify(engineeringEventRepository).findByProposalIdIn(List.of(acceptedEvent.getId()));
        verify(decisionRepository, never()).findByProposalId(acceptedDecision.getId());
    }

    @Test
    void doesNotResolveTrustedArtifactsForInProgressAnalysisResult() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Analysis analysis = analysis(analysisId, projectId);
        analysis.setStatus(AnalysisStatus.IN_PROGRESS);
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(intentCatalog.resolve("architecture-overview", "v1"))
                .thenReturn(new IntentDefinition("architecture-overview", "v1", "Understand the system", List.of(), List.of(), Map.of(), "prompt"));
        when(sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(projectId)).thenReturn(List.of());

        AnalysisResultResponse result = service.getResult(analysisId);

        assertEquals(0, result.proposals().items().size());
        verify(proposalRepository, never()).findByAnalysisId(analysisId);
        verify(insightRepository, never()).findByProposalIdIn(anyCollection());
        verify(decisionRepository, never()).findByProposalIdIn(anyCollection());
        verify(engineeringEventRepository, never()).findByProposalIdIn(anyCollection());
    }

    private Analysis analysis(UUID analysisId, UUID projectId) {
        Project project = new Project();
        project.setId(projectId);
        Analysis analysis = new Analysis();
        analysis.setId(analysisId);
        analysis.setProject(project);
        analysis.setIntentId("architecture-overview");
        analysis.setIntentVersion("v1");
        analysis.setStatus(AnalysisStatus.COMPLETED);
        analysis.setStartedAt(Instant.parse("2026-07-22T10:00:00Z"));
        analysis.setCompletedAt(Instant.parse("2026-07-22T10:01:00Z"));
        return analysis;
    }

    private ValidatableProposal proposal(UUID id, ProposalType type, ProposalStatus status) {
        return ValidatableProposal.builder()
                .id(id)
                .type(type)
                .status(status)
                .payload(Map.of("title", type.name(), "summary", "summary"))
                .confidence(BigDecimal.valueOf(0.85))
                .createdAt(Instant.now())
                .build();
    }

    private AnalysisResultResponse.ProposalSummary summaryFor(AnalysisResultResponse response, UUID proposalId) {
        return response.proposals().items().stream()
                .filter(item -> item.proposalId().equals(proposalId))
                .findFirst()
                .orElseThrow();
    }
}
