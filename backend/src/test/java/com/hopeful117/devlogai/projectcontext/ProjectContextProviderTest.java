package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.artifact.entity.Artifact;
import com.hopeful117.devlogai.artifact.entity.ArtifactType;
import com.hopeful117.devlogai.artifact.repository.ArtifactRepository;
import com.hopeful117.devlogai.decision.entity.Decision;
import com.hopeful117.devlogai.decision.repository.DecisionRepository;
import com.hopeful117.devlogai.knowledge.entity.KnowledgeEvent;
import com.hopeful117.devlogai.knowledge.entity.KnowledgeEventType;
import com.hopeful117.devlogai.knowledge.repository.KnowledgeEventRepository;
import com.hopeful117.devlogai.milestone.entity.Milestone;
import com.hopeful117.devlogai.milestone.entity.MilestoneStatus;
import com.hopeful117.devlogai.milestone.repository.MilestoneRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.profile.service.ProjectProfileService;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectContextProviderTest {

    @Mock ProjectRepository projectRepository;
    @Mock ProjectProfileService projectProfileService;
    @Mock KnowledgeEventRepository knowledgeEventRepository;
    @Mock ValidatableProposalRepository proposalRepository;
    @Mock ArtifactRepository artifactRepository;
    @Mock DecisionRepository decisionRepository;
    @Mock MilestoneRepository milestoneRepository;
    @Mock AnalysisRepository analysisRepository;

    @InjectMocks ProjectContextProviderImpl provider;

    @Test
    void shouldBuildProjectContextWithAllData() {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder()
                .id(projectId).name("DevLog AI").slug("devlog-ai")
                .description("A dev logging tool").build();

        KnowledgeEvent event = KnowledgeEvent.builder()
                .id(UUID.randomUUID()).project(project)
                .type(KnowledgeEventType.ARCHITECTURE)
                .title("Service split").createdAt(Instant.parse("2026-07-21T10:00:00Z"))
                .build();
        Artifact artifact = Artifact.builder()
                .id(UUID.randomUUID()).project(project)
                .type(ArtifactType.INFRASTRUCTURE).name("docker-compose.yml")
                .createdAt(Instant.parse("2026-07-20T10:00:00Z")).build();
        Decision decision = Decision.builder()
                .id(UUID.randomUUID()).project(project)
                .title("Use PostgreSQL").context("Persistence")
                .choice("PostgreSQL").rationale("Relational model")
                .createdAt(Instant.parse("2026-07-19T10:00:00Z")).build();
        Milestone milestone = Milestone.builder()
                .id(UUID.randomUUID()).project(project)
                .name("MVP").status(MilestoneStatus.IN_PROGRESS)
                .startedAt(Instant.parse("2026-07-18T10:00:00Z")).build();
        Analysis analysis = Analysis.builder()
                .id(UUID.randomUUID()).project(project)
                .type(AnalysisType.ARCHITECTURE_REVIEW)
                .createdAt(Instant.parse("2026-07-17T10:00:00Z")).build();
        ValidatableProposal proposal = ValidatableProposal.builder()
                .id(UUID.randomUUID()).project(project)
                .analysis(analysis).type(ProposalType.INSIGHT)
                .status(ProposalStatus.ACCEPTED)
                .payload(Map.of("summary", "accepted"))
                .createdAt(Instant.parse("2026-07-16T10:00:00Z")).build();

        ProjectProfileResponse profile = mock(ProjectProfileResponse.class);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectProfileService.getLatestByProject(projectId)).thenReturn(profile);
        when(knowledgeEventRepository.findByProjectIdOrderByCreatedAtDescIdDesc(
                eq(projectId), any(Pageable.class))).thenReturn(List.of(event));
        when(proposalRepository.findByProjectIdAndStatusOrderByCreatedAtDescIdDesc(
                eq(projectId), eq(ProposalStatus.ACCEPTED), any(Pageable.class)))
                .thenReturn(List.of(proposal));
        when(artifactRepository.findByProjectIdAndTypeInOrderByCreatedAtDescIdDesc(
                eq(projectId), anyList(), any(Pageable.class)))
                .thenReturn(List.of(artifact));
        when(decisionRepository.findByProjectIdOrderByCreatedAtDescIdDesc(
                eq(projectId), any(Pageable.class)))
                .thenReturn(List.of(decision));
        when(milestoneRepository.findByProjectIdOrderByStartedAtDescIdDesc(
                eq(projectId), any(Pageable.class)))
                .thenReturn(List.of(milestone));
        when(analysisRepository.findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of(analysis));

        ProjectContextSnapshot snapshot = provider.build(projectId);

        assertEquals(projectId, snapshot.project().id());
        assertEquals("DevLog AI", snapshot.project().name());
        assertEquals(profile, snapshot.latestProjectProfile());
        assertEquals(1, snapshot.recentKnowledgeEvents().size());
        assertEquals(event.getId(), snapshot.recentKnowledgeEvents().getFirst().id());
        assertEquals(1, snapshot.validatedProposals().size());
        assertEquals(proposal.getId(), snapshot.validatedProposals().getFirst().id());
        assertEquals(1, snapshot.architectureArtifacts().size());
        assertEquals(artifact.getId(), snapshot.architectureArtifacts().getFirst().id());
        assertEquals(1, snapshot.relatedDecisions().size());
        assertEquals(decision.getId(), snapshot.relatedDecisions().getFirst().id());
        assertEquals(1, snapshot.recentMilestones().size());
        assertEquals(milestone.getId(), snapshot.recentMilestones().getFirst().id());
        assertEquals(1, snapshot.recentAnalyses().size());
        assertEquals(analysis.getId(), snapshot.recentAnalyses().getFirst().id());
    }

    @Test
    void shouldReturnEmptyListsWhenNoData() {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder()
                .id(projectId).name("Empty Project").slug("empty").build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectProfileService.getLatestByProject(projectId)).thenReturn(null);
        when(knowledgeEventRepository.findByProjectIdOrderByCreatedAtDescIdDesc(
                eq(projectId), any(Pageable.class))).thenReturn(List.of());
        when(proposalRepository.findByProjectIdAndStatusOrderByCreatedAtDescIdDesc(
                eq(projectId), eq(ProposalStatus.ACCEPTED), any(Pageable.class)))
                .thenReturn(List.of());
        when(artifactRepository.findByProjectIdAndTypeInOrderByCreatedAtDescIdDesc(
                eq(projectId), anyList(), any(Pageable.class)))
                .thenReturn(List.of());
        when(decisionRepository.findByProjectIdOrderByCreatedAtDescIdDesc(
                eq(projectId), any(Pageable.class)))
                .thenReturn(List.of());
        when(milestoneRepository.findByProjectIdOrderByStartedAtDescIdDesc(
                eq(projectId), any(Pageable.class)))
                .thenReturn(List.of());
        when(analysisRepository.findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of());

        ProjectContextSnapshot snapshot = provider.build(projectId);

        assertEquals(projectId, snapshot.project().id());
        assertNull(snapshot.latestProjectProfile());
        assertTrue(snapshot.recentKnowledgeEvents().isEmpty());
        assertTrue(snapshot.validatedProposals().isEmpty());
        assertTrue(snapshot.architectureArtifacts().isEmpty());
        assertTrue(snapshot.relatedDecisions().isEmpty());
        assertTrue(snapshot.recentMilestones().isEmpty());
        assertTrue(snapshot.recentAnalyses().isEmpty());
    }

    @Test
    void shouldHandleMissingProfileGracefully() {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder()
                .id(projectId).name("Test Project").slug("test").build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectProfileService.getLatestByProject(projectId)).thenReturn(null);
        when(knowledgeEventRepository.findByProjectIdOrderByCreatedAtDescIdDesc(
                eq(projectId), any(Pageable.class))).thenReturn(List.of());
        when(proposalRepository.findByProjectIdAndStatusOrderByCreatedAtDescIdDesc(
                eq(projectId), eq(ProposalStatus.ACCEPTED), any(Pageable.class)))
                .thenReturn(List.of());
        when(artifactRepository.findByProjectIdAndTypeInOrderByCreatedAtDescIdDesc(
                eq(projectId), anyList(), any(Pageable.class)))
                .thenReturn(List.of());
        when(decisionRepository.findByProjectIdOrderByCreatedAtDescIdDesc(
                eq(projectId), any(Pageable.class)))
                .thenReturn(List.of());
        when(milestoneRepository.findByProjectIdOrderByStartedAtDescIdDesc(
                eq(projectId), any(Pageable.class)))
                .thenReturn(List.of());
        when(analysisRepository.findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of());

        ProjectContextSnapshot snapshot = provider.build(projectId);

        assertNull(snapshot.latestProjectProfile());
    }

    @Test
    void shouldApplyPaginationLimits() {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder()
                .id(projectId).name("Test Project").slug("test").build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectProfileService.getLatestByProject(projectId)).thenReturn(null);
        when(knowledgeEventRepository.findByProjectIdOrderByCreatedAtDescIdDesc(
                eq(projectId), any(Pageable.class))).thenReturn(List.of());
        when(proposalRepository.findByProjectIdAndStatusOrderByCreatedAtDescIdDesc(
                eq(projectId), eq(ProposalStatus.ACCEPTED), any(Pageable.class)))
                .thenReturn(List.of());
        when(artifactRepository.findByProjectIdAndTypeInOrderByCreatedAtDescIdDesc(
                eq(projectId), anyList(), any(Pageable.class)))
                .thenReturn(List.of());
        when(decisionRepository.findByProjectIdOrderByCreatedAtDescIdDesc(
                eq(projectId), any(Pageable.class)))
                .thenReturn(List.of());
        when(milestoneRepository.findByProjectIdOrderByStartedAtDescIdDesc(
                eq(projectId), any(Pageable.class)))
                .thenReturn(List.of());
        when(analysisRepository.findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of());

        provider.build(projectId);

        verify(knowledgeEventRepository).findByProjectIdOrderByCreatedAtDescIdDesc(
                eq(projectId),
                argThat(page -> page.getPageSize() == ProjectContextProviderImpl.MAX_RECENT_EVENTS));
        verify(proposalRepository).findByProjectIdAndStatusOrderByCreatedAtDescIdDesc(
                eq(projectId), eq(ProposalStatus.ACCEPTED),
                argThat(page -> page.getPageSize() == ProjectContextProviderImpl.MAX_VALIDATED_PROPOSALS));
        verify(artifactRepository).findByProjectIdAndTypeInOrderByCreatedAtDescIdDesc(
                eq(projectId), anyList(),
                argThat(page -> page.getPageSize() == ProjectContextProviderImpl.MAX_ARCHITECTURE_ARTIFACTS));
        verify(decisionRepository).findByProjectIdOrderByCreatedAtDescIdDesc(
                eq(projectId),
                argThat(page -> page.getPageSize() == ProjectContextProviderImpl.MAX_ARCHITECTURE_DECISIONS));
        verify(milestoneRepository).findByProjectIdOrderByStartedAtDescIdDesc(
                eq(projectId),
                argThat(page -> page.getPageSize() == ProjectContextProviderImpl.MAX_RECENT_MILESTONES));
    }

    @Test
    void shouldReturnImmutableLists() {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder()
                .id(projectId).name("Test Project").slug("test").build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectProfileService.getLatestByProject(projectId)).thenReturn(null);
        when(knowledgeEventRepository.findByProjectIdOrderByCreatedAtDescIdDesc(
                eq(projectId), any(Pageable.class))).thenReturn(List.of());
        when(proposalRepository.findByProjectIdAndStatusOrderByCreatedAtDescIdDesc(
                eq(projectId), eq(ProposalStatus.ACCEPTED), any(Pageable.class)))
                .thenReturn(List.of());
        when(artifactRepository.findByProjectIdAndTypeInOrderByCreatedAtDescIdDesc(
                eq(projectId), anyList(), any(Pageable.class)))
                .thenReturn(List.of());
        when(decisionRepository.findByProjectIdOrderByCreatedAtDescIdDesc(
                eq(projectId), any(Pageable.class)))
                .thenReturn(List.of());
        when(milestoneRepository.findByProjectIdOrderByStartedAtDescIdDesc(
                eq(projectId), any(Pageable.class)))
                .thenReturn(List.of());
        when(analysisRepository.findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of());

        ProjectContextSnapshot snapshot = provider.build(projectId);

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.recentKnowledgeEvents().add(null));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.validatedProposals().add(null));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.architectureArtifacts().add(null));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.relatedDecisions().add(null));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.recentMilestones().add(null));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.recentAnalyses().add(null));
    }

    @Test
    void shouldReturnAllRecentAnalyses() {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder()
                .id(projectId).name("Test Project").slug("test").build();

        Analysis analysis1 = Analysis.builder()
                .id(UUID.randomUUID()).project(project)
                .type(AnalysisType.ARCHITECTURE_REVIEW)
                .createdAt(Instant.parse("2026-07-21T10:00:00Z")).build();
        Analysis analysis2 = Analysis.builder()
                .id(UUID.randomUUID()).project(project)
                .type(AnalysisType.PROJECT_EVOLUTION)
                .createdAt(Instant.parse("2026-07-20T10:00:00Z")).build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectProfileService.getLatestByProject(projectId)).thenReturn(null);
        when(knowledgeEventRepository.findByProjectIdOrderByCreatedAtDescIdDesc(
                eq(projectId), any(Pageable.class))).thenReturn(List.of());
        when(proposalRepository.findByProjectIdAndStatusOrderByCreatedAtDescIdDesc(
                eq(projectId), eq(ProposalStatus.ACCEPTED), any(Pageable.class)))
                .thenReturn(List.of());
        when(artifactRepository.findByProjectIdAndTypeInOrderByCreatedAtDescIdDesc(
                eq(projectId), anyList(), any(Pageable.class)))
                .thenReturn(List.of());
        when(decisionRepository.findByProjectIdOrderByCreatedAtDescIdDesc(
                eq(projectId), any(Pageable.class)))
                .thenReturn(List.of());
        when(milestoneRepository.findByProjectIdOrderByStartedAtDescIdDesc(
                eq(projectId), any(Pageable.class)))
                .thenReturn(List.of());
        when(analysisRepository.findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of(analysis1, analysis2));

        ProjectContextSnapshot snapshot = provider.build(projectId);

        assertEquals(2, snapshot.recentAnalyses().size());
        assertEquals(analysis1.getId(), snapshot.recentAnalyses().getFirst().id());
        assertEquals(analysis2.getId(), snapshot.recentAnalyses().getLast().id());
    }

    @Test
    void shouldFailWhenProjectDoesNotExist() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> provider.build(projectId));

        verifyNoInteractions(projectProfileService, knowledgeEventRepository,
                proposalRepository, artifactRepository, decisionRepository,
                milestoneRepository, analysisRepository);
    }
}
