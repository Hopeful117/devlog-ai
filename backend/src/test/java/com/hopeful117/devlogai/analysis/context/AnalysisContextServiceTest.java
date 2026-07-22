package com.hopeful117.devlogai.analysis.context;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.artifact.entity.Artifact;
import com.hopeful117.devlogai.artifact.entity.ArtifactType;
import com.hopeful117.devlogai.artifact.repository.ArtifactRepository;
import com.hopeful117.devlogai.decision.entity.Decision;
import com.hopeful117.devlogai.decision.repository.DecisionRepository;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.knowledge.entity.KnowledgeEvent;
import com.hopeful117.devlogai.knowledge.entity.KnowledgeEventType;
import com.hopeful117.devlogai.knowledge.repository.KnowledgeEventRepository;
import com.hopeful117.devlogai.milestone.entity.Milestone;
import com.hopeful117.devlogai.milestone.repository.MilestoneRepository;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.entity.ObservationType;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import com.hopeful117.devlogai.profile.service.ProjectProfileService;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
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
import java.util.Map;
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
    @Mock KnowledgeEventRepository knowledgeEventRepository;
    @Mock ValidatableProposalRepository proposalRepository;
    @Mock ArtifactRepository artifactRepository;
    @Mock DecisionRepository decisionRepository;
    @Mock MilestoneRepository milestoneRepository;

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
        KnowledgeEvent newestEvent = KnowledgeEvent.builder().id(UUID.randomUUID())
                .project(project).type(KnowledgeEventType.ARCHITECTURE)
                .title("Service split").createdAt(Instant.parse("2026-07-21T10:00:00Z"))
                .build();
        KnowledgeEvent oldestEvent = KnowledgeEvent.builder().id(UUID.randomUUID())
                .project(project).type(KnowledgeEventType.DEPENDENCY)
                .title("Dependency added").createdAt(Instant.parse("2026-07-20T10:00:00Z"))
                .build();
        Artifact artifact = Artifact.builder().id(UUID.randomUUID()).project(project)
                .type(ArtifactType.INFRASTRUCTURE).name("docker-compose.yml").build();
        Decision decision = Decision.builder().id(UUID.randomUUID()).project(project)
                .title("Use PostgreSQL").context("Persistence").choice("PostgreSQL")
                .rationale("Relational model").build();

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(projectProfileService.getByAnalysis(analysisId)).thenReturn(mock(ProjectProfileResponse.class));
        when(factRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc(eq(analysisId), any(Pageable.class)))
                .thenReturn(List.of(newestFact, oldestFact));
        when(observationRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(eq(analysisId), any(Pageable.class)))
                .thenReturn(List.of(observation));
        when(knowledgeEventRepository.findByProjectIdOrderByCreatedAtDescIdDesc(eq(projectId), any(Pageable.class)))
                .thenReturn(List.of(newestEvent, oldestEvent));
        when(proposalRepository.findByProjectIdAndStatusOrderByCreatedAtDescIdDesc(
                eq(projectId), eq(ProposalStatus.ACCEPTED), any(Pageable.class)))
                .thenReturn(List.of());
        when(analysisRepository.findByProjectIdAndIdNotOrderByCreatedAtDescIdDesc(
                eq(projectId), eq(analysisId), any(Pageable.class))).thenReturn(List.of());
        when(artifactRepository.findByProjectIdAndTypeInOrderByCreatedAtDescIdDesc(
                eq(projectId), anyList(), any(Pageable.class))).thenReturn(List.of(artifact));
        when(decisionRepository.findByProjectIdOrderByCreatedAtDescIdDesc(eq(projectId), any(Pageable.class)))
                .thenReturn(List.of(decision));

        AnalysisContext context = service.build(analysisId);

        assertEquals(projectId, context.project().id());
        assertEquals(analysisId, context.analysis().id());
        assertEquals(List.of(newestFact.getId(), oldestFact.getId()),
                context.facts().stream().map(AnalysisContext.FactSnapshot::id).toList());
        assertEquals(List.of("a", "b"), context.facts().getFirst().evidenceReferences());
        assertEquals("ARCHITECTURE_MODULARIZATION", context.observations().getFirst().ruleId());
        assertEquals("1", context.observations().getFirst().ruleVersion());
        assertEquals(List.of(newestEvent.getId(), oldestEvent.getId()),
                context.recentKnowledgeEvents().stream()
                        .map(AnalysisContext.KnowledgeEventSnapshot::id).toList());
        assertEquals(List.of(artifact.getId()), context.architectureArtifacts().stream()
                .map(AnalysisContext.ArtifactSnapshot::id).toList());
        assertEquals(List.of(decision.getId()), context.relatedDecisions().stream()
                .map(AnalysisContext.DecisionSnapshot::id).toList());
        assertTrue(context.recentMilestones().isEmpty());
        assertTrue(context.validatedProposals().isEmpty());

        verifyBoundedFactPage(AnalysisContextServiceImpl.MAX_FACTS);
        verify(knowledgeEventRepository).findByProjectIdOrderByCreatedAtDescIdDesc(
                eq(projectId), argThat(page -> page.getPageSize()
                        == AnalysisContextServiceImpl.MAX_RECENT_EVENTS));
        verify(proposalRepository).findByProjectIdAndStatusOrderByCreatedAtDescIdDesc(
                eq(projectId), eq(ProposalStatus.ACCEPTED), any(Pageable.class));
        verify(milestoneRepository, never()).findByProjectIdOrderByStartedAtDescIdDesc(
                any(), any(Pageable.class));
    }

    @Test
    void shouldBuildProjectEvolutionContextWithoutArchitectureKnowledge() {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        Project project = project(projectId);
        Analysis analysis = analysis(analysisId, project, AnalysisType.PROJECT_EVOLUTION);
        Analysis previous = analysis(UUID.randomUUID(), project, AnalysisType.PROJECT_EVOLUTION);
        Milestone milestone = Milestone.builder().id(UUID.randomUUID()).project(project)
                .name("MVP").build();

        commonContextData(analysis);
        when(analysisRepository.findByProjectIdAndIdNotOrderByCreatedAtDescIdDesc(
                eq(projectId), eq(analysisId), any(Pageable.class))).thenReturn(List.of(previous));
        when(milestoneRepository.findByProjectIdOrderByStartedAtDescIdDesc(eq(projectId), any(Pageable.class)))
                .thenReturn(List.of(milestone));

        AnalysisContext context = service.build(analysisId);

        assertEquals(List.of(previous.getId()), context.relatedAnalyses().stream()
                .map(AnalysisContext.AnalysisSnapshot::id).toList());
        assertEquals(List.of(milestone.getId()), context.recentMilestones().stream()
                .map(AnalysisContext.MilestoneSnapshot::id).toList());
        assertTrue(context.architectureArtifacts().isEmpty());
        assertTrue(context.relatedDecisions().isEmpty());
        verifyNoInteractions(artifactRepository, decisionRepository);
    }

    @Test
    void shouldKeepUnsupportedPoliciesOnTheCommonContextOnly() {
        UUID analysisId = UUID.randomUUID();
        Analysis analysis = analysis(analysisId, project(UUID.randomUUID()), AnalysisType.TECHNICAL_DEBT);

        commonContextData(analysis);

        AnalysisContext context = service.build(analysisId);

        assertTrue(context.relatedAnalyses().isEmpty());
        assertTrue(context.architectureArtifacts().isEmpty());
        assertTrue(context.relatedDecisions().isEmpty());
        assertTrue(context.recentMilestones().isEmpty());
        verifyNoInteractions(artifactRepository, decisionRepository, milestoneRepository);
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
        Map<String, Object> payload = Map.of("summary", "accepted");
        ValidatableProposal acceptedProposal = ValidatableProposal.builder()
                .id(UUID.randomUUID())
                .project(analysis.getProject())
                .analysis(analysis)
                .type(ProposalType.INSIGHT)
                .status(ProposalStatus.ACCEPTED)
                .payload(payload)
                .build();

        commonContextData(analysis, false);
        when(proposalRepository.findByProjectIdAndStatusOrderByCreatedAtDescIdDesc(
                eq(projectId), eq(ProposalStatus.ACCEPTED), any(Pageable.class)))
                .thenReturn(List.of(acceptedProposal));

        AnalysisContext context = service.build(analysisId);

        assertEquals(1, context.validatedProposals().size());
        assertEquals(acceptedProposal.getId(), context.validatedProposals().getFirst().id());
        assertEquals(Map.of("summary", "accepted"),
                context.validatedProposals().getFirst().payload());
        assertThrows(
                UnsupportedOperationException.class,
                () -> context.validatedProposals().add(null)
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> context.facts().add(null)
        );

        verify(proposalRepository).findByProjectIdAndStatusOrderByCreatedAtDescIdDesc(
                eq(projectId), eq(ProposalStatus.ACCEPTED),
                argThat(page -> page.getPageSize()
                        == AnalysisContextServiceImpl.MAX_VALIDATED_PROPOSALS)
        );
    }

    @Test
    void shouldFailWhenAnalysisDoesNotExist() {
        UUID analysisId = UUID.randomUUID();
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.build(analysisId));

        verifyNoInteractions(
                factRepository, observationRepository, knowledgeEventRepository,
                proposalRepository, artifactRepository, decisionRepository, milestoneRepository,
                projectProfileService
        );
    }

    private void commonContextData(Analysis analysis) {
        commonContextData(analysis, true);
    }

    private void commonContextData(Analysis analysis, boolean stubProposals) {
        UUID projectId = analysis.getProject().getId();
        when(analysisRepository.findById(analysis.getId())).thenReturn(Optional.of(analysis));
        when(projectProfileService.getByAnalysis(analysis.getId())).thenReturn(mock(ProjectProfileResponse.class));
        when(factRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc(eq(analysis.getId()), any(Pageable.class)))
                .thenReturn(List.of());
        when(observationRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(eq(analysis.getId()), any(Pageable.class)))
                .thenReturn(List.of());
        when(knowledgeEventRepository.findByProjectIdOrderByCreatedAtDescIdDesc(eq(projectId), any(Pageable.class)))
                .thenReturn(List.of());
        if (stubProposals) {
            when(proposalRepository.findByProjectIdAndStatusOrderByCreatedAtDescIdDesc(
                    eq(projectId), eq(ProposalStatus.ACCEPTED), any(Pageable.class)))
                    .thenReturn(List.of());
        }
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
}
