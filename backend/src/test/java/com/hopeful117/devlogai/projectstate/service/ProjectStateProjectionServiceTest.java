package com.hopeful117.devlogai.projectstate.service;

import com.hopeful117.devlogai.challenge.entity.Challenge;
import com.hopeful117.devlogai.challenge.entity.ChallengeStatus;
import com.hopeful117.devlogai.challenge.repository.ChallengeRepository;
import com.hopeful117.devlogai.decision.entity.Decision;
import com.hopeful117.devlogai.decision.repository.DecisionRepository;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.milestone.entity.Milestone;
import com.hopeful117.devlogai.milestone.entity.MilestoneStatus;
import com.hopeful117.devlogai.milestone.repository.MilestoneRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.projectstate.dto.inner.StorySummary;
import com.hopeful117.devlogai.projectstate.dto.response.ActiveWorkSection;
import com.hopeful117.devlogai.projectstate.dto.response.ObjectiveSection;
import com.hopeful117.devlogai.projectstate.dto.response.PendingActionsSection;
import com.hopeful117.devlogai.projectstate.dto.response.ProjectStateResponse;
import com.hopeful117.devlogai.projectstate.dto.response.RecentChangesSection;
import com.hopeful117.devlogai.projectstate.dto.response.RoadmapProgressSection;
import com.hopeful117.devlogai.projectstate.mapper.ProjectStateMapper;
import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.story.entity.StoryStatus;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectStateProjectionServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private EngineeringStoryRepository storyRepository;
    @Mock
    private ChallengeRepository challengeRepository;
    @Mock
    private ValidatableProposalRepository proposalRepository;
    @Mock
    private DecisionRepository decisionRepository;
    @Mock
    private MilestoneRepository milestoneRepository;
    @Mock
    private ProjectCommitRepository commitRepository;
    @Mock
    private ProjectStateMapper mapper;

    @InjectMocks
    private ProjectStateProjectionServiceImpl service;

    @Test
    void shouldReturnProjectStateWithAllSectionsPopulated() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project();
        project.setId(projectId);
        project.setName("Test Project");
        project.setDescription("Test description");
        project.setStatus(ProjectStatus.ACTIVE);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        // Objective section
        when(milestoneRepository.findByProjectIdAndStatusOrderByStartedAtDesc(projectId, MilestoneStatus.IN_PROGRESS))
                .thenReturn(List.of(new Milestone()));
        when(storyRepository.findByProject_IdAndStatusOrderByCreatedAtDesc(projectId, StoryStatus.IN_PROGRESS))
                .thenReturn(List.of(new EngineeringStory()));
        when(challengeRepository.findByProjectIdAndStatusOrderByCreatedAtDesc(projectId, ChallengeStatus.OPEN))
                .thenReturn(List.of(new Challenge()));

        // Active work section
        when(proposalRepository.findByProjectIdAndStatus(projectId, ProposalStatus.PROPOSED))
                .thenReturn(List.of(new ValidatableProposal()));

        // Recent changes section
        when(storyRepository.findByProject_IdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of(new EngineeringStory()));
        when(decisionRepository.findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of(new Decision()));
        when(commitRepository.findByProjectIdOrderByCommittedAtDescCommitHashDesc(eq(projectId), any(PageRequest.class)))
                .thenReturn(List.of(new ProjectCommit()));

        // Roadmap progress section
        when(milestoneRepository.findByProjectIdAndStatusOrderByStartedAtDesc(projectId, MilestoneStatus.PLANNED))
                .thenReturn(List.of(new Milestone()));

        // Mock mapper responses for sections
        when(mapper.toObjectiveSection(any(), any(), any(), any()))
                .thenReturn(new ObjectiveSection("description", null, null, Collections.emptyList()));
        when(mapper.toActiveWorkSection(any(), any(), any()))
                .thenReturn(new ActiveWorkSection(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
        when(mapper.toRecentChangesSection(any(), any(), any()))
                .thenReturn(new RecentChangesSection(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
        when(mapper.toRoadmapProgressSection(any(), any()))
                .thenReturn(new RoadmapProgressSection(Collections.emptyList(), Collections.emptyList()));
        when(mapper.toPendingActionsSection(any(), any(), any()))
                .thenReturn(new PendingActionsSection(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));

        ProjectStateResponse expectedResponse = new ProjectStateResponse(
                projectId,
                "Test Project",
                new ObjectiveSection("description", null, null, Collections.emptyList()),
                new ActiveWorkSection(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()),
                new RecentChangesSection(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()),
                new RoadmapProgressSection(Collections.emptyList(), Collections.emptyList()),
                new PendingActionsSection(Collections.emptyList(), Collections.emptyList(), Collections.emptyList())
        );
        when(mapper.toResponse(any(), any(), any(), any(), any(), any()))
                .thenReturn(expectedResponse);

        ProjectStateResponse response = service.getProjectState(projectId);

        assertNotNull(response);
        assertEquals(projectId, response.projectId());
        assertEquals("Test Project", response.projectName());
        verify(projectRepository).findById(projectId);
    }

    @Test
    void shouldReturnProjectStateWithEmptySections() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project();
        project.setId(projectId);
        project.setName("Empty Project");
        project.setDescription(null);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        // Return empty lists for all queries
        when(milestoneRepository.findByProjectIdAndStatusOrderByStartedAtDesc(any(), any()))
                .thenReturn(Collections.emptyList());
        when(storyRepository.findByProject_IdAndStatusOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Collections.emptyList());
        when(challengeRepository.findByProjectIdAndStatusOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Collections.emptyList());
        when(proposalRepository.findByProjectIdAndStatus(any(), any()))
                .thenReturn(Collections.emptyList());
        when(storyRepository.findByProject_IdOrderByCreatedAtDesc(any()))
                .thenReturn(Collections.emptyList());
        when(decisionRepository.findByProjectIdOrderByCreatedAtDesc(any()))
                .thenReturn(Collections.emptyList());
        when(commitRepository.findByProjectIdOrderByCommittedAtDescCommitHashDesc(any(), any()))
                .thenReturn(Collections.emptyList());

        when(mapper.toObjectiveSection(any(), any(), any(), any()))
                .thenReturn(new ObjectiveSection(null, null, null, Collections.emptyList()));
        when(mapper.toActiveWorkSection(any(), any(), any()))
                .thenReturn(new ActiveWorkSection(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
        when(mapper.toRecentChangesSection(any(), any(), any()))
                .thenReturn(new RecentChangesSection(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
        when(mapper.toRoadmapProgressSection(any(), any()))
                .thenReturn(new RoadmapProgressSection(Collections.emptyList(), Collections.emptyList()));
        when(mapper.toPendingActionsSection(any(), any(), any()))
                .thenReturn(new PendingActionsSection(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));

        ProjectStateResponse expectedResponse = new ProjectStateResponse(
                projectId,
                "Empty Project",
                new ObjectiveSection(null, null, null, Collections.emptyList()),
                new ActiveWorkSection(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()),
                new RecentChangesSection(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()),
                new RoadmapProgressSection(Collections.emptyList(), Collections.emptyList()),
                new PendingActionsSection(Collections.emptyList(), Collections.emptyList(), Collections.emptyList())
        );
        when(mapper.toResponse(any(), any(), any(), any(), any(), any()))
                .thenReturn(expectedResponse);

        ProjectStateResponse response = service.getProjectState(projectId);

        assertNotNull(response);
        assertEquals(projectId, response.projectId());
        assertEquals("Empty Project", response.projectName());
    }

    @Test
    void shouldThrowWhenProjectNotFound() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.getProjectState(projectId));
    }
}
