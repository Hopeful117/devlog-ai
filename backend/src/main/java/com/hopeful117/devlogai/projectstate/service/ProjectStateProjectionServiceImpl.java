package com.hopeful117.devlogai.projectstate.service;

import com.hopeful117.devlogai.challenge.entity.ChallengeStatus;
import com.hopeful117.devlogai.challenge.repository.ChallengeRepository;
import com.hopeful117.devlogai.decision.repository.DecisionRepository;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.milestone.entity.MilestoneStatus;
import com.hopeful117.devlogai.milestone.repository.MilestoneRepository;
import com.hopeful117.devlogai.project.entity.Project;
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
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.story.entity.StoryStatus;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class ProjectStateProjectionServiceImpl implements ProjectStateProjectionService {

    private final ProjectRepository projectRepository;
    private final EngineeringStoryRepository storyRepository;
    private final ChallengeRepository challengeRepository;
    private final ValidatableProposalRepository proposalRepository;
    private final DecisionRepository decisionRepository;
    private final MilestoneRepository milestoneRepository;
    private final ProjectCommitRepository commitRepository;
    private final ProjectStateMapper mapper;

    @Override
    public ProjectStateResponse getProjectState(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project", projectId));

        ObjectiveSection objective = buildObjective(projectId, project.getDescription());
        ActiveWorkSection activeWork = buildActiveWork(projectId);
        RecentChangesSection recentChanges = buildRecentChanges(projectId);
        RoadmapProgressSection roadmapProgress = buildRoadmapProgress(projectId);
        PendingActionsSection pendingActions = buildPendingActions(projectId);

        return mapper.toResponse(
                project,
                objective,
                activeWork,
                recentChanges,
                roadmapProgress,
                pendingActions
        );
    }

    private ObjectiveSection buildObjective(UUID projectId, String description) {
        var currentMilestones = milestoneRepository
                .findByProjectIdAndStatusOrderByStartedAtDesc(projectId, MilestoneStatus.IN_PROGRESS);
        var currentMilestone = currentMilestones.isEmpty() ? null : currentMilestones.getFirst();

        var activeStories = storyRepository
                .findByProject_IdAndStatusOrderByCreatedAtDesc(projectId, StoryStatus.IN_PROGRESS);
        var activeStory = activeStories.isEmpty() ? null : activeStories.getFirst();

        var openChallenges = challengeRepository
                .findByProjectIdAndStatusOrderByCreatedAtDesc(projectId, ChallengeStatus.OPEN);

        return mapper.toObjectiveSection(description, currentMilestone, activeStory, openChallenges);
    }

    private ActiveWorkSection buildActiveWork(UUID projectId) {
        var inProgressStories = storyRepository
                .findByProject_IdAndStatusOrderByCreatedAtDesc(projectId, StoryStatus.IN_PROGRESS);
        var openChallenges = challengeRepository
                .findByProjectIdAndStatusOrderByCreatedAtDesc(projectId, ChallengeStatus.OPEN);
        var proposedProposals = proposalRepository
                .findByProjectIdAndStatus(projectId, ProposalStatus.PROPOSED);

        return mapper.toActiveWorkSection(inProgressStories, openChallenges, proposedProposals);
    }

    private RecentChangesSection buildRecentChanges(UUID projectId) {
        var allStories = storyRepository.findByProject_IdOrderByCreatedAtDesc(projectId);
        var completedStories = allStories.stream()
                .filter(s -> s.getStatus() == StoryStatus.COMPLETED)
                .limit(5)
                .map(mapper::toStorySummary)
                .toList();

        var recentDecisions = decisionRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId);
        var limitedDecisions = recentDecisions.size() > 5
                ? recentDecisions.subList(0, 5)
                : recentDecisions;

        var recentCommits = commitRepository
                .findByProjectIdOrderByCommittedAtDescCommitHashDesc(projectId, PageRequest.of(0, 10));

        return mapper.toRecentChangesSection(completedStories, limitedDecisions, recentCommits);
    }

    private RoadmapProgressSection buildRoadmapProgress(UUID projectId) {
        var plannedMilestones = milestoneRepository
                .findByProjectIdAndStatusOrderByStartedAtDesc(projectId, MilestoneStatus.PLANNED);
        var allStories = storyRepository.findByProject_IdOrderByCreatedAtDesc(projectId);
        var registeredStories = allStories.stream()
                .filter(s -> s.getStatus() == StoryStatus.REGISTERED)
                .collect(Collectors.toList());

        return mapper.toRoadmapProgressSection(plannedMilestones, registeredStories);
    }

    private PendingActionsSection buildPendingActions(UUID projectId) {
        var proposedProposals = proposalRepository
                .findByProjectIdAndStatus(projectId, ProposalStatus.PROPOSED);
        var openChallenges = challengeRepository
                .findByProjectIdAndStatusOrderByCreatedAtDesc(projectId, ChallengeStatus.OPEN);
        var allStories = storyRepository.findByProject_IdOrderByCreatedAtDesc(projectId);
        var unstartedStories = allStories.stream()
                .filter(s -> s.getStatus() == StoryStatus.REGISTERED)
                .limit(5)
                .collect(Collectors.toList());

        return mapper.toPendingActionsSection(proposedProposals, openChallenges, unstartedStories);
    }
}
