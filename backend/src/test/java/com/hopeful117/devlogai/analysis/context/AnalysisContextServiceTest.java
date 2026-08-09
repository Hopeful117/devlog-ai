package com.hopeful117.devlogai.analysis.context;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.engineeringevent.AnalysisEvolutionScopeRepository;
import com.hopeful117.devlogai.history.service.ProjectHistoryService;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.entity.ObservationType;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.projectcontext.ProjectContextProvider;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.profile.service.ProjectProfileService;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisContextServiceTest {

    @Mock AnalysisRepository analysisRepository;
    @Mock FactRepository factRepository;
    @Mock ObservationRepository observationRepository;
    @Mock ProjectProfileService projectProfileService;
    @Mock ProjectContextProvider projectContextProvider;
    @Mock AnalysisEvolutionScopeRepository evolutionScopes;
    @Mock ProjectHistoryService projectHistoryService;

    @InjectMocks AnalysisContextServiceImpl service;

    @Test
    void shouldBuildBoundedArchitectureContextForOneProject() {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        Project project = project(projectId);
        Analysis analysis = analysis(analysisId, project, AnalysisType.ARCHITECTURE_REVIEW);
        Fact newestFact = Fact.builder()
                .id(UUID.randomUUID()).analysis(analysis).type(FactType.TECHNOLOGY)
                .content("Uses PostgreSQL").source("pom.xml")
                .evidenceReferences(new LinkedHashSet<>(Set.of("b", "a")))
                .detectedAt(Instant.parse("2026-07-21T11:00:00Z")).build();
        Fact oldestFact = Fact.builder()
                .id(UUID.randomUUID()).analysis(analysis).type(FactType.FILE_CHANGE)
                .content("Changed configuration").source("application.properties")
                .evidenceReferences(Set.of("c"))
                .detectedAt(Instant.parse("2026-07-20T11:00:00Z")).build();
        Observation observation = Observation.builder()
                .id(UUID.randomUUID()).analysis(analysis)
                .type(ObservationType.ARCHITECTURE_MODULARIZATION)
                .content("Architecture is becoming more modular.")
                .ruleId("ARCHITECTURE_MODULARIZATION").ruleVersion("1")
                .supportingFacts(new LinkedHashSet<>(Set.of(oldestFact, newestFact)))
                .createdAt(Instant.parse("2026-07-21T12:00:00Z")).build();

        AnalysisContext.KnowledgeEventSnapshot ke1 = new AnalysisContext.KnowledgeEventSnapshot(
                UUID.randomUUID(), com.hopeful117.devlogai.knowledge.entity.KnowledgeEventType.ARCHITECTURE,
                "Service split", null, Instant.parse("2026-07-21T10:00:00Z"));
        AnalysisContext.KnowledgeEventSnapshot ke2 = new AnalysisContext.KnowledgeEventSnapshot(
                UUID.randomUUID(), com.hopeful117.devlogai.knowledge.entity.KnowledgeEventType.DEPENDENCY,
                "Dependency added", null, Instant.parse("2026-07-20T10:00:00Z"));
        AnalysisContext.ArtifactSnapshot artifactSnap = new AnalysisContext.ArtifactSnapshot(
                UUID.randomUUID(), com.hopeful117.devlogai.artifact.entity.ArtifactType.INFRASTRUCTURE,
                "docker-compose.yml", null, null, null);
        AnalysisContext.DecisionSnapshot decisionSnap = new AnalysisContext.DecisionSnapshot(
                UUID.randomUUID(), "Use PostgreSQL", "Persistence", "PostgreSQL", "Relational model",
                null, null);

        ProjectContextSnapshot projectContext = new ProjectContextSnapshot(
                toProjectSnapshot(project),
                null,
                List.of(ke1, ke2),
                List.of(),
                List.of(artifactSnap),
                List.of(decisionSnap),
                List.of(),
                List.of()
        );

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(projectProfileService.getByAnalysis(analysisId)).thenReturn(mock(ProjectProfileResponse.class));
        when(factRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc(eq(analysisId), any(Pageable.class)))
                .thenReturn(List.of(newestFact, oldestFact));
        when(observationRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(eq(analysisId), any(Pageable.class)))
                .thenReturn(List.of(observation));
        when(projectContextProvider.build(projectId)).thenReturn(projectContext);

        AnalysisContext context = service.build(analysisId);

        assertEquals(projectId, context.project().id());
        assertEquals(analysisId, context.analysis().id());
        assertEquals(List.of(newestFact.getId(), oldestFact.getId()),
                context.facts().stream().map(AnalysisContext.FactSnapshot::id).toList());
        assertEquals(List.of("a", "b"), context.facts().getFirst().evidenceReferences());
        assertEquals("ARCHITECTURE_MODULARIZATION", context.observations().getFirst().ruleId());
        assertEquals("1", context.observations().getFirst().ruleVersion());
        assertEquals(List.of(ke1.id(), ke2.id()),
                context.recentKnowledgeEvents().stream()
                        .map(AnalysisContext.KnowledgeEventSnapshot::id).toList());
        assertEquals(List.of(artifactSnap.id()), context.architectureArtifacts().stream()
                .map(AnalysisContext.ArtifactSnapshot::id).toList());
        assertEquals(List.of(decisionSnap.id()), context.relatedDecisions().stream()
                .map(AnalysisContext.DecisionSnapshot::id).toList());
        assertTrue(context.recentMilestones().isEmpty());
        assertTrue(context.validatedProposals().isEmpty());

        verifyBoundedFactPage(AnalysisContextServiceImpl.MAX_FACTS);
        verify(projectContextProvider).build(projectId);
    }

    @Test
    void shouldBuildProjectEvolutionContextWithoutArchitectureKnowledge() {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        Project project = project(projectId);
        Analysis analysis = analysis(analysisId, project, AnalysisType.PROJECT_EVOLUTION);
        Analysis previous = analysis(UUID.randomUUID(), project, AnalysisType.PROJECT_EVOLUTION);
        AnalysisContext.AnalysisSnapshot previousSnap = toAnalysisSnapshot(previous);

        AnalysisContext.MilestoneSnapshot milestoneSnap = new AnalysisContext.MilestoneSnapshot(
                UUID.randomUUID(), "MVP", null, null, null, null);

        ProjectContextSnapshot projectContext = new ProjectContextSnapshot(
                toProjectSnapshot(project),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(milestoneSnap),
                List.of(previousSnap)
        );

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(projectProfileService.getByAnalysis(analysisId)).thenReturn(mock(ProjectProfileResponse.class));
        when(factRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc(eq(analysisId), any(Pageable.class)))
                .thenReturn(List.of());
        when(observationRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(eq(analysisId), any(Pageable.class)))
                .thenReturn(List.of());
        when(projectContextProvider.build(projectId)).thenReturn(projectContext);

        AnalysisContext context = service.build(analysisId);

        assertEquals(List.of(previousSnap.id()), context.relatedAnalyses().stream()
                .map(AnalysisContext.AnalysisSnapshot::id).toList());
        assertEquals(List.of(milestoneSnap.id()), context.recentMilestones().stream()
                .map(AnalysisContext.MilestoneSnapshot::id).toList());
        assertTrue(context.architectureArtifacts().isEmpty());
        assertTrue(context.relatedDecisions().isEmpty());
    }

    @Test
    void shouldKeepUnsupportedPoliciesOnTheCommonContextOnly() {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        Analysis analysis = analysis(analysisId, project(projectId), AnalysisType.TECHNICAL_DEBT);

        ProjectContextSnapshot projectContext = new ProjectContextSnapshot(
                toProjectSnapshot(project(projectId)),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(projectProfileService.getByAnalysis(analysisId)).thenReturn(mock(ProjectProfileResponse.class));
        when(factRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc(eq(analysisId), any(Pageable.class)))
                .thenReturn(List.of());
        when(observationRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(eq(analysisId), any(Pageable.class)))
                .thenReturn(List.of());
        when(projectContextProvider.build(projectId)).thenReturn(projectContext);

        AnalysisContext context = service.build(analysisId);

        assertTrue(context.relatedAnalyses().isEmpty());
        assertTrue(context.architectureArtifacts().isEmpty());
        assertTrue(context.relatedDecisions().isEmpty());
        assertTrue(context.recentMilestones().isEmpty());
    }

    @Test
    void shouldExposeAcceptedProposalsAsImmutableSnapshotsOnly() {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        Analysis analysis = analysis(
                analysisId,
                project(projectId),
                AnalysisType.TECHNICAL_DEBT
        );

        AnalysisContext.ValidatedProposalSnapshot proposalSnap = new AnalysisContext.ValidatedProposalSnapshot(
                UUID.randomUUID(), com.hopeful117.devlogai.proposal.entity.ProposalType.INSIGHT,
                java.util.Map.of("summary", "accepted"), Instant.parse("2026-07-20T10:00:00Z"), null);

        ProjectContextSnapshot projectContext = new ProjectContextSnapshot(
                toProjectSnapshot(project(projectId)),
                null,
                List.of(),
                List.of(proposalSnap),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(projectProfileService.getByAnalysis(analysisId)).thenReturn(mock(ProjectProfileResponse.class));
        when(factRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc(eq(analysisId), any(Pageable.class)))
                .thenReturn(List.of());
        when(observationRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(eq(analysisId), any(Pageable.class)))
                .thenReturn(List.of());
        when(projectContextProvider.build(projectId)).thenReturn(projectContext);

        AnalysisContext context = service.build(analysisId);

        assertEquals(1, context.validatedProposals().size());
        assertEquals(proposalSnap.id(), context.validatedProposals().getFirst().id());
        assertEquals(java.util.Map.of("summary", "accepted"),
                context.validatedProposals().getFirst().payload());
        List<AnalysisContext.ValidatedProposalSnapshot> validatedProposals =
                context.validatedProposals();
        assertThrows(
                UnsupportedOperationException.class,
                () -> validatedProposals.add(null)
        );
        List<AnalysisContext.FactSnapshot> facts = context.facts();
        assertThrows(
                UnsupportedOperationException.class,
                () -> facts.add(null)
        );
    }

    @Test
    void shouldFailWhenAnalysisDoesNotExist() {
        UUID analysisId = UUID.randomUUID();
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.build(analysisId));

        verifyNoInteractions(factRepository, observationRepository, projectProfileService,
                projectContextProvider);
    }

    private Project project(UUID id) {
        return Project.builder().id(id).name("DevLog AI").slug("devlog-ai").build();
    }

    private Analysis analysis(UUID id, Project project, AnalysisType type) {
        return Analysis.builder().id(id).project(project).type(type).build();
    }

    private void verifyBoundedFactPage(int expectedPageSize) {
        ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(factRepository).findByAnalysisIdOrderByDetectedAtDescIdDesc(
                any(), pageCaptor.capture()
        );
        assertEquals(expectedPageSize, pageCaptor.getValue().getPageSize());
    }

    private AnalysisContext.ProjectSnapshot toProjectSnapshot(Project project) {
        return new AnalysisContext.ProjectSnapshot(
                project.getId(), project.getName(), project.getSlug(),
                project.getDescription(), project.getStatus()
        );
    }

    private AnalysisContext.AnalysisSnapshot toAnalysisSnapshot(Analysis analysis) {
        return new AnalysisContext.AnalysisSnapshot(
                analysis.getId(), analysis.getType(),
                analysis.getIntentId(), analysis.getIntentVersion(), analysis.getStatus(),
                analysis.getStartedAt(), analysis.getCompletedAt(), analysis.getCreatedAt()
        );
    }
}
