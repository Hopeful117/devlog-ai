package com.hopeful117.devlogai.analysis.result.service;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

    // --- Regression B RED tests: evidence composition through getResult() ---

    private void stubMinimalCompletedAnalysis(UUID analysisId, UUID projectId) {
        Analysis analysis = analysis(analysisId, projectId);
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(intentCatalog.resolve("architecture-overview", "v1"))
                .thenReturn(new IntentDefinition("architecture-overview", "v1", "Understand the system", List.of(), List.of(), Map.of(), "prompt"));
        when(proposalRepository.findByAnalysisId(analysisId)).thenReturn(new ArrayList<>(List.of()));
        when(insightRepository.findByAnalysisIdOrderByCreatedAtDesc(analysisId)).thenReturn(List.of());
        when(deliverableRepository.findByAnalysisIdOrderByGeneratedAtDesc(analysisId)).thenReturn(List.of());
        when(sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(projectId)).thenReturn(List.of());
    }

    private AiTaskSelectedEvidenceResponse.TaskIdentity minimalTaskIdentity() {
        return new AiTaskSelectedEvidenceResponse.TaskIdentity(
                UUID.randomUUID(), AiTaskType.INSIGHT_GENERATION, AiTaskStatus.COMPLETED, Instant.now());
    }

    private AiTaskSelectedEvidenceResponse.SnapshotMetadata minimalSnapshotMetadata() {
        return new AiTaskSelectedEvidenceResponse.SnapshotMetadata(
                null, null, null, null, null, null);
    }

    @Test
    void shouldMapFactItemsToEvidenceItemsWithCorrectSemanticFields() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        stubMinimalCompletedAnalysis(analysisId, projectId);

        UUID factId = UUID.randomUUID();
        Instant detectedAt = Instant.parse("2026-08-27T10:00:00Z");
        AiTaskSelectedEvidenceResponse.FactItem factItem = new AiTaskSelectedEvidenceResponse.FactItem(
                factId, "SOURCE_DIRECTORY_PRESENT", "Source directory src/main/java exists", "src/main/java",
                List.of("diff:abc123:src/main/java"), detectedAt);

        AiTaskSelectedEvidenceResponse available = AiTaskSelectedEvidenceResponse.available(
                analysisId, projectId, minimalTaskIdentity(), "v1", "digest",
                new AiTaskSelectedEvidenceResponse.ProjectedSnapshot(
                        minimalSnapshotMetadata(),
                        new AiTaskSelectedEvidenceResponse.Categories(
                                new AiTaskSelectedEvidenceResponse.FactsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.RECORDED, 1, List.of(factItem)),
                                new AiTaskSelectedEvidenceResponse.ObservationsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.PriorInsightsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.ArchitectureKnowledgeSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.EngineeringEventsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.HumanContextSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.EvolutionContextSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.RepositoryEvidenceSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of())
                        )
                )
        );
        when(selectedEvidenceService.getSelectedEvidence(analysisId)).thenReturn(available);

        AnalysisResultResponse result = service.getResult(analysisId);

        AnalysisResultResponse.EvidenceCategorySection facts = result.evidence().facts();
        assertEquals(1, facts.count());
        assertEquals(1, facts.items().size());

        AnalysisResultResponse.EvidenceItem item = facts.items().get(0);
        assertEquals("FACT", item.layer());
        assertEquals("SOURCE_DIRECTORY_PRESENT", item.kind());
        assertEquals("fact:" + factId, item.reference());
        assertEquals("Source directory src/main/java exists", item.summary());
        assertEquals(detectedAt, item.occurredAt());
        assertEquals(List.of("diff:abc123:src/main/java"), item.relatedReferences());
    }

    @Test
    void shouldMapObservationItemsToEvidenceItemsWithCorrectSemanticFields() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        stubMinimalCompletedAnalysis(analysisId, projectId);

        UUID obsId = UUID.randomUUID();
        UUID factId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-27T10:05:00Z");
        AiTaskSelectedEvidenceResponse.ObservationItem obsItem = new AiTaskSelectedEvidenceResponse.ObservationItem(
                obsId, "PATTERN_DETECTED", "Module coupling detected", "OBS-001", "1.0",
                List.of(factId), createdAt);

        AiTaskSelectedEvidenceResponse available = AiTaskSelectedEvidenceResponse.available(
                analysisId, projectId, minimalTaskIdentity(), "v1", "digest",
                new AiTaskSelectedEvidenceResponse.ProjectedSnapshot(
                        minimalSnapshotMetadata(),
                        new AiTaskSelectedEvidenceResponse.Categories(
                                new AiTaskSelectedEvidenceResponse.FactsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.ObservationsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.RECORDED, 1, List.of(obsItem)),
                                new AiTaskSelectedEvidenceResponse.PriorInsightsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.ArchitectureKnowledgeSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.EngineeringEventsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.HumanContextSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.EvolutionContextSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.RepositoryEvidenceSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of())
                        )
                )
        );
        when(selectedEvidenceService.getSelectedEvidence(analysisId)).thenReturn(available);

        AnalysisResultResponse result = service.getResult(analysisId);

        AnalysisResultResponse.EvidenceCategorySection observations = result.evidence().observations();
        assertEquals(1, observations.count());
        assertEquals(1, observations.items().size());

        AnalysisResultResponse.EvidenceItem item = observations.items().get(0);
        assertEquals("OBSERVATION", item.layer());
        assertEquals("OBS-001", item.kind());
        assertEquals("observation:" + obsId, item.reference());
        assertEquals("Module coupling detected", item.summary());
        assertEquals(createdAt, item.occurredAt());
        assertEquals(List.of("fact:" + factId), item.relatedReferences());
    }

    @Test
    void shouldMapPriorInsightItemsToEvidenceItemsWithCorrectSemanticFields() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        stubMinimalCompletedAnalysis(analysisId, projectId);

        AiTaskSelectedEvidenceResponse.PriorInsightItem insightItem =
                new AiTaskSelectedEvidenceResponse.PriorInsightItem(
                        "ARCHITECTURE", "WARNING", "Monolith scaling risk", "The monolith may face scaling issues");

        AiTaskSelectedEvidenceResponse available = AiTaskSelectedEvidenceResponse.available(
                analysisId, projectId, minimalTaskIdentity(), "v1", "digest",
                new AiTaskSelectedEvidenceResponse.ProjectedSnapshot(
                        minimalSnapshotMetadata(),
                        new AiTaskSelectedEvidenceResponse.Categories(
                                new AiTaskSelectedEvidenceResponse.FactsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.ObservationsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.PriorInsightsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.RECORDED, 1, List.of(insightItem)),
                                new AiTaskSelectedEvidenceResponse.ArchitectureKnowledgeSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.EngineeringEventsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.HumanContextSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.EvolutionContextSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.RepositoryEvidenceSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of())
                        )
                )
        );
        when(selectedEvidenceService.getSelectedEvidence(analysisId)).thenReturn(available);

        AnalysisResultResponse result = service.getResult(analysisId);

        AnalysisResultResponse.EvidenceCategorySection priorInsights = result.evidence().priorInsights();
        assertEquals(1, priorInsights.count());
        assertEquals(1, priorInsights.items().size());

        AnalysisResultResponse.EvidenceItem item = priorInsights.items().get(0);
        assertEquals("VALIDATED_INSIGHT", item.layer());
        assertEquals("WARNING", item.kind());
        assertEquals("ARCHITECTURE", item.reference());
        assertEquals("The monolith may face scaling issues", item.summary());
        assertNull(item.occurredAt());
        assertEquals(List.of(), item.relatedReferences());
    }

    @Test
    void shouldMapArchitectureKnowledgeItemsToEvidenceItemsWithCorrectSemanticFields() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        stubMinimalCompletedAnalysis(analysisId, projectId);

        UUID insightId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-27T09:00:00Z");
        AiTaskSelectedEvidenceResponse.ArchitectureKnowledgeItem akItem =
                new AiTaskSelectedEvidenceResponse.ArchitectureKnowledgeItem(
                        insightId, UUID.randomUUID(), "MODULE_BOUNDARY", "WARNING",
                        "SOURCE_ANALYSIS", "Module boundary weakened", "Content about module boundary",
                        "Rationale for the insight", List.of("diff:abc:file.java"), createdAt);

        AiTaskSelectedEvidenceResponse available = AiTaskSelectedEvidenceResponse.available(
                analysisId, projectId, minimalTaskIdentity(), "v1", "digest",
                new AiTaskSelectedEvidenceResponse.ProjectedSnapshot(
                        minimalSnapshotMetadata(),
                        new AiTaskSelectedEvidenceResponse.Categories(
                                new AiTaskSelectedEvidenceResponse.FactsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.ObservationsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.PriorInsightsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.ArchitectureKnowledgeSection(
                                        AiTaskSelectedEvidenceResponse.Availability.RECORDED, 1, List.of(akItem)),
                                new AiTaskSelectedEvidenceResponse.EngineeringEventsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.HumanContextSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.EvolutionContextSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.RepositoryEvidenceSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of())
                        )
                )
        );
        when(selectedEvidenceService.getSelectedEvidence(analysisId)).thenReturn(available);

        AnalysisResultResponse result = service.getResult(analysisId);

        AnalysisResultResponse.EvidenceCategorySection ak = result.evidence().architectureKnowledge();
        assertEquals(1, ak.count());
        assertEquals(1, ak.items().size());

        AnalysisResultResponse.EvidenceItem item = ak.items().get(0);
        assertEquals("VALIDATED_INSIGHT", item.layer());
        assertEquals("MODULE_BOUNDARY", item.kind());
        assertEquals("insight:" + insightId, item.reference());
        assertEquals("Module boundary weakened", item.summary());
        assertEquals(createdAt, item.occurredAt());
        assertEquals(List.of("diff:abc:file.java"), item.relatedReferences());
    }

    @Test
    void shouldMapEngineeringEventItemsToEvidenceItemsWithCorrectSemanticFields() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        stubMinimalCompletedAnalysis(analysisId, projectId);

        UUID eventId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-08-27T11:00:00Z");
        AiTaskSelectedEvidenceResponse.EngineeringEventItem eventItem =
                new AiTaskSelectedEvidenceResponse.EngineeringEventItem(
                        eventId, "FEATURE", "Added auth module", "Implemented JWT authentication",
                        sourceId, "abc123", "def456", occurredAt, null);

        AiTaskSelectedEvidenceResponse available = AiTaskSelectedEvidenceResponse.available(
                analysisId, projectId, minimalTaskIdentity(), "v1", "digest",
                new AiTaskSelectedEvidenceResponse.ProjectedSnapshot(
                        minimalSnapshotMetadata(),
                        new AiTaskSelectedEvidenceResponse.Categories(
                                new AiTaskSelectedEvidenceResponse.FactsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.ObservationsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.PriorInsightsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.ArchitectureKnowledgeSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.EngineeringEventsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.RECORDED, 1, List.of(eventItem)),
                                new AiTaskSelectedEvidenceResponse.HumanContextSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.EvolutionContextSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.RepositoryEvidenceSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of())
                        )
                )
        );
        when(selectedEvidenceService.getSelectedEvidence(analysisId)).thenReturn(available);

        AnalysisResultResponse result = service.getResult(analysisId);

        AnalysisResultResponse.EvidenceCategorySection events = result.evidence().engineeringEvents();
        assertEquals(1, events.count());
        assertEquals(1, events.items().size());

        AnalysisResultResponse.EvidenceItem item = events.items().get(0);
        assertEquals("COMMIT_DIFF", item.layer());
        assertEquals("FEATURE", item.kind());
        assertEquals("event:" + eventId, item.reference());
        assertEquals("Added auth module", item.summary());
        assertEquals(occurredAt, item.occurredAt());
        assertEquals(List.of(
                "git:" + sourceId + ":abc123",
                "git:" + sourceId + ":def456"), item.relatedReferences());
    }

    @Test
    void shouldMapHumanContextItemsToEvidenceItemsWithCorrectSemanticFields() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        stubMinimalCompletedAnalysis(analysisId, projectId);

        UUID humanId = UUID.randomUUID();
        Instant updatedAt = Instant.parse("2026-08-27T12:00:00Z");
        AiTaskSelectedEvidenceResponse.HumanContextItem humanItem =
                new AiTaskSelectedEvidenceResponse.HumanContextItem(
                        humanId, "ADR", "Use hexagonal architecture", "# ADR\nWe decided to use hexagonal...",
                        "ACCEPTED", updatedAt);

        AiTaskSelectedEvidenceResponse available = AiTaskSelectedEvidenceResponse.available(
                analysisId, projectId, minimalTaskIdentity(), "v1", "digest",
                new AiTaskSelectedEvidenceResponse.ProjectedSnapshot(
                        minimalSnapshotMetadata(),
                        new AiTaskSelectedEvidenceResponse.Categories(
                                new AiTaskSelectedEvidenceResponse.FactsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.ObservationsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.PriorInsightsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.ArchitectureKnowledgeSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.EngineeringEventsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.HumanContextSection(
                                        AiTaskSelectedEvidenceResponse.Availability.RECORDED, 1, List.of(humanItem)),
                                new AiTaskSelectedEvidenceResponse.EvolutionContextSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.RepositoryEvidenceSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of())
                        )
                )
        );
        when(selectedEvidenceService.getSelectedEvidence(analysisId)).thenReturn(available);

        AnalysisResultResponse result = service.getResult(analysisId);

        AnalysisResultResponse.EvidenceCategorySection humanCtx = result.evidence().humanContext();
        assertEquals(1, humanCtx.count());
        assertEquals(1, humanCtx.items().size());

        AnalysisResultResponse.EvidenceItem item = humanCtx.items().get(0);
        assertEquals("PROJECT_DOCUMENTATION", item.layer());
        assertEquals("ADR", item.kind());
        assertEquals("human:" + humanId, item.reference());
        assertEquals("Use hexagonal architecture", item.summary());
        assertEquals(updatedAt, item.occurredAt());
        assertEquals(List.of(), item.relatedReferences());
    }

    @Test
    void shouldMapEvolutionContextItemsToEvidenceItemsWithCorrectSemanticFields() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        stubMinimalCompletedAnalysis(analysisId, projectId);

        UUID sourceId = UUID.randomUUID();
        Instant targetCommittedAt = Instant.parse("2026-08-27T13:00:00Z");
        AiTaskSelectedEvidenceResponse.CommitDiff commitDiff = new AiTaskSelectedEvidenceResponse.CommitDiff(
                projectId, sourceId, "abc123def", null, List.of(), false, false,
                "Refactored service layer", targetCommittedAt,
                List.of(), new AiTaskSelectedEvidenceResponse.DiffStatistics(3, 50, 20, 0),
                List.of(), List.of(), List.of("diff:abc123def:src/Svc.java"), false, List.of());

        AiTaskSelectedEvidenceResponse.EvolutionContextItem evoItem =
                new AiTaskSelectedEvidenceResponse.EvolutionContextItem(
                        "1.0", projectId, sourceId, "base123", "abc123def",
                        "FULL", false, targetCommittedAt, commitDiff);

        AiTaskSelectedEvidenceResponse available = AiTaskSelectedEvidenceResponse.available(
                analysisId, projectId, minimalTaskIdentity(), "v1", "digest",
                new AiTaskSelectedEvidenceResponse.ProjectedSnapshot(
                        minimalSnapshotMetadata(),
                        new AiTaskSelectedEvidenceResponse.Categories(
                                new AiTaskSelectedEvidenceResponse.FactsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.ObservationsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.PriorInsightsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.ArchitectureKnowledgeSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.EngineeringEventsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.HumanContextSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.EvolutionContextSection(
                                        AiTaskSelectedEvidenceResponse.Availability.RECORDED, 1, List.of(evoItem)),
                                new AiTaskSelectedEvidenceResponse.RepositoryEvidenceSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of())
                        )
                )
        );
        when(selectedEvidenceService.getSelectedEvidence(analysisId)).thenReturn(available);

        AnalysisResultResponse result = service.getResult(analysisId);

        AnalysisResultResponse.EvidenceCategorySection evo = result.evidence().evolutionContext();
        assertEquals(1, evo.count());
        assertEquals(1, evo.items().size());

        AnalysisResultResponse.EvidenceItem item = evo.items().get(0);
        assertEquals("COMMIT_DIFF", item.layer());
        assertEquals("FULL", item.kind());
        assertEquals("evolution:" + sourceId, item.reference());
        assertEquals("Refactored service layer", item.summary());
        assertEquals(targetCommittedAt, item.occurredAt());
        assertEquals(List.of("diff:abc123def:src/Svc.java"), item.relatedReferences());
    }

    @Test
    void shouldMapRepositoryEvidenceItemsToEvidenceItemsWithCorrectSemanticFields() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        stubMinimalCompletedAnalysis(analysisId, projectId);

        Instant occurredAt = Instant.parse("2026-08-27T14:00:00Z");
        AiTaskSelectedEvidenceResponse.RepositoryEvidenceItem repoItem =
                new AiTaskSelectedEvidenceResponse.RepositoryEvidenceItem(
                        "COMMIT_DIFF", "CHANGED_FILE", "diff:abc:src/App.java",
                        "MODIFIED src/App.java (+10/-5)", occurredAt,
                        List.of("git:src1:abc"), null, null);

        AiTaskSelectedEvidenceResponse available = AiTaskSelectedEvidenceResponse.available(
                analysisId, projectId, minimalTaskIdentity(), "v1", "digest",
                new AiTaskSelectedEvidenceResponse.ProjectedSnapshot(
                        minimalSnapshotMetadata(),
                        new AiTaskSelectedEvidenceResponse.Categories(
                                new AiTaskSelectedEvidenceResponse.FactsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.ObservationsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.PriorInsightsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.ArchitectureKnowledgeSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.EngineeringEventsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.HumanContextSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.EvolutionContextSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.RepositoryEvidenceSection(
                                        AiTaskSelectedEvidenceResponse.Availability.RECORDED, 1, List.of(repoItem))
                        )
                )
        );
        when(selectedEvidenceService.getSelectedEvidence(analysisId)).thenReturn(available);

        AnalysisResultResponse result = service.getResult(analysisId);

        AnalysisResultResponse.EvidenceCategorySection repo = result.evidence().repositoryEvidence();
        assertEquals(1, repo.count());
        assertEquals(1, repo.items().size());

        AnalysisResultResponse.EvidenceItem item = repo.items().get(0);
        assertEquals("COMMIT_DIFF", item.layer());
        assertEquals("CHANGED_FILE", item.kind());
        assertEquals("diff:abc:src/App.java", item.reference());
        assertEquals("MODIFIED src/App.java (+10/-5)", item.summary());
        assertEquals(occurredAt, item.occurredAt());
        assertEquals(List.of("git:src1:abc"), item.relatedReferences());
    }

    @Test
    void shouldSerializeCanonicalResultWithFactItemsViaJackson() throws Exception {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        stubMinimalCompletedAnalysis(analysisId, projectId);

        UUID factId1 = UUID.randomUUID();
        UUID factId2 = UUID.randomUUID();
        UUID factId3 = UUID.randomUUID();
        Instant detectedAt = Instant.parse("2026-08-27T10:00:00Z");

        AiTaskSelectedEvidenceResponse.FactItem fact1 = new AiTaskSelectedEvidenceResponse.FactItem(
                factId1, "SOURCE_DIRECTORY_PRESENT", "Source directory src/main/java exists", "src/main/java",
                List.of("diff:abc:src/main/java"), detectedAt);
        AiTaskSelectedEvidenceResponse.FactItem fact2 = new AiTaskSelectedEvidenceResponse.FactItem(
                factId2, "CONFIG_FILE_FOUND", "Found pom.xml", "pom.xml",
                List.of(), detectedAt);
        AiTaskSelectedEvidenceResponse.FactItem fact3 = new AiTaskSelectedEvidenceResponse.FactItem(
                factId3, "TEST_DIRECTORY_PRESENT", "Test directory src/test/java exists", "src/test/java",
                List.of(), detectedAt);

        AiTaskSelectedEvidenceResponse available = AiTaskSelectedEvidenceResponse.available(
                analysisId, projectId, minimalTaskIdentity(), "v1", "digest",
                new AiTaskSelectedEvidenceResponse.ProjectedSnapshot(
                        minimalSnapshotMetadata(),
                        new AiTaskSelectedEvidenceResponse.Categories(
                                new AiTaskSelectedEvidenceResponse.FactsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.RECORDED, 3, List.of(fact1, fact2, fact3)),
                                new AiTaskSelectedEvidenceResponse.ObservationsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.PriorInsightsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.ArchitectureKnowledgeSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.EngineeringEventsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.HumanContextSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.EvolutionContextSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.RepositoryEvidenceSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of())
                        )
                )
        );
        when(selectedEvidenceService.getSelectedEvidence(analysisId)).thenReturn(available);

        AnalysisResultResponse result = service.getResult(analysisId);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        String json = objectMapper.writeValueAsString(result);

        assertNotNull(json);
        assertTrue(json.contains("SOURCE_DIRECTORY_PRESENT"));
        assertTrue(json.contains("CONFIG_FILE_FOUND"));
        assertTrue(json.contains("TEST_DIRECTORY_PRESENT"));

        AnalysisResultResponse deserialized = objectMapper.readValue(json, AnalysisResultResponse.class);
        assertEquals(3, deserialized.evidence().facts().count());
        assertEquals(3, deserialized.evidence().facts().items().size());
    }

    @Test
    void shouldRespectEvidencePreviewLimit() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        stubMinimalCompletedAnalysis(analysisId, projectId);

        List<AiTaskSelectedEvidenceResponse.FactItem> facts = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            facts.add(new AiTaskSelectedEvidenceResponse.FactItem(
                    UUID.randomUUID(), "TYPE_" + i, "Fact " + i, "source" + i,
                    List.of(), Instant.now()));
        }

        AiTaskSelectedEvidenceResponse available = AiTaskSelectedEvidenceResponse.available(
                analysisId, projectId, minimalTaskIdentity(), "v1", "digest",
                new AiTaskSelectedEvidenceResponse.ProjectedSnapshot(
                        minimalSnapshotMetadata(),
                        new AiTaskSelectedEvidenceResponse.Categories(
                                new AiTaskSelectedEvidenceResponse.FactsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.RECORDED, 8, facts),
                                new AiTaskSelectedEvidenceResponse.ObservationsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.PriorInsightsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.ArchitectureKnowledgeSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.EngineeringEventsSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.HumanContextSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.EvolutionContextSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of()),
                                new AiTaskSelectedEvidenceResponse.RepositoryEvidenceSection(
                                        AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED, 0, List.of())
                        )
                )
        );
        when(selectedEvidenceService.getSelectedEvidence(analysisId)).thenReturn(available);

        AnalysisResultResponse result = service.getResult(analysisId);

        AnalysisResultResponse.EvidenceCategorySection factsSection = result.evidence().facts();
        assertEquals(8, factsSection.count());
        assertEquals(5, factsSection.items().size());
    }

    // --- Canonical result scope projection test ---

    @Test
    void getResult_withGenerateReadmeIntent_setsRepositoryScope() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Analysis analysis = new Analysis();
        Project project = new Project();
        project.setId(projectId);
        analysis.setId(analysisId);
        analysis.setProject(project);
        analysis.setIntentId("generate-readme");
        analysis.setIntentVersion("v1");
        analysis.setStatus(AnalysisStatus.COMPLETED);
        analysis.setStartedAt(Instant.parse("2026-07-22T10:00:00Z"));
        analysis.setCompletedAt(Instant.parse("2026-07-22T10:01:00Z"));

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(intentCatalog.resolve("generate-readme", "v1"))
                .thenReturn(new IntentDefinition("generate-readme", "v1", "Generate README",
                        List.of(), List.of(), Map.of(), "generate-readme-prompt-v1"));
        when(proposalRepository.findByAnalysisId(analysisId)).thenReturn(new ArrayList<>());
        when(selectedEvidenceService.getSelectedEvidence(analysisId)).thenReturn(
                AiTaskSelectedEvidenceResponse.noAiTask(analysisId, projectId));

        AnalysisResultResponse result = service.getResult(analysisId);

        assertEquals("REPOSITORY_SCOPE", result.analysis().scope());
    }
}
