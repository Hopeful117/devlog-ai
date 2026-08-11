package com.hopeful117.devlogai.projectstate.mapper;

import com.hopeful117.devlogai.challenge.entity.Challenge;
import com.hopeful117.devlogai.decision.entity.Decision;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.milestone.entity.Milestone;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.projectstate.dto.inner.ChallengeSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.CommitSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.DecisionSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.MilestoneSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.ProposalSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.StorySummary;
import com.hopeful117.devlogai.projectstate.dto.response.ActiveWorkSection;
import com.hopeful117.devlogai.projectstate.dto.response.ObjectiveSection;
import com.hopeful117.devlogai.projectstate.dto.response.PendingActionsSection;
import com.hopeful117.devlogai.projectstate.dto.response.ProjectStateResponse;
import com.hopeful117.devlogai.projectstate.dto.response.RecentChangesSection;
import com.hopeful117.devlogai.projectstate.dto.response.RoadmapProgressSection;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ProjectStateMapper {

    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "projectName", source = "project.name")
    ProjectStateResponse toResponse(
            Project project,
            ObjectiveSection objective,
            ActiveWorkSection activeWork,
            RecentChangesSection recentChanges,
            RoadmapProgressSection roadmapProgress,
            PendingActionsSection pendingActions
    );

    default ObjectiveSection toObjectiveSection(
            String description,
            Milestone currentMilestone,
            EngineeringStory activeStory,
            List<Challenge> openChallenges
    ) {
        return new ObjectiveSection(
                description,
                currentMilestone != null ? toMilestoneSummary(currentMilestone) : null,
                activeStory != null ? toStorySummary(activeStory) : null,
                toChallengeSummaries(openChallenges)
        );
    }

    default ActiveWorkSection toActiveWorkSection(
            List<EngineeringStory> inProgressStories,
            List<Challenge> openChallenges,
            List<ValidatableProposal> proposedProposals
    ) {
        return new ActiveWorkSection(
                toStorySummaries(inProgressStories),
                toChallengeSummaries(openChallenges),
                toProposalSummaries(proposedProposals)
        );
    }

    default RecentChangesSection toRecentChangesSection(
            List<StorySummary> completedStories,
            List<Decision> recentDecisions,
            List<ProjectCommit> recentCommits
    ) {
        return new RecentChangesSection(
                completedStories,
                toDecisionSummaries(recentDecisions),
                toCommitSummaries(recentCommits)
        );
    }

    default RoadmapProgressSection toRoadmapProgressSection(
            List<Milestone> plannedMilestones,
            List<EngineeringStory> registeredStories
    ) {
        return new RoadmapProgressSection(
                toMilestoneSummaries(plannedMilestones),
                toStorySummaries(registeredStories)
        );
    }

    default PendingActionsSection toPendingActionsSection(
            List<ValidatableProposal> proposedProposals,
            List<Challenge> openChallenges,
            List<EngineeringStory> unstartedStories
    ) {
        return new PendingActionsSection(
                toProposalSummaries(proposedProposals),
                toChallengeSummaries(openChallenges),
                toStorySummaries(unstartedStories)
        );
    }

    @Mapping(target = "number", source = "storyNumber")
    StorySummary toStorySummary(EngineeringStory story);

    default List<StorySummary> toStorySummaries(List<EngineeringStory> stories) {
        if (stories == null || stories.isEmpty()) {
            return Collections.emptyList();
        }
        return stories.stream().map(this::toStorySummary).toList();
    }

    ChallengeSummary toChallengeSummary(Challenge challenge);

    default List<ChallengeSummary> toChallengeSummaries(List<Challenge> challenges) {
        if (challenges == null || challenges.isEmpty()) {
            return Collections.emptyList();
        }
        return challenges.stream().map(this::toChallengeSummary).toList();
    }

    ProposalSummary toProposalSummary(ValidatableProposal proposal);

    default List<ProposalSummary> toProposalSummaries(List<ValidatableProposal> proposals) {
        if (proposals == null || proposals.isEmpty()) {
            return Collections.emptyList();
        }
        return proposals.stream().map(this::toProposalSummary).toList();
    }

    MilestoneSummary toMilestoneSummary(Milestone milestone);

    default List<MilestoneSummary> toMilestoneSummaries(List<Milestone> milestones) {
        if (milestones == null || milestones.isEmpty()) {
            return Collections.emptyList();
        }
        return milestones.stream().map(this::toMilestoneSummary).toList();
    }

    DecisionSummary toDecisionSummary(Decision decision);

    default List<DecisionSummary> toDecisionSummaries(List<Decision> decisions) {
        if (decisions == null || decisions.isEmpty()) {
            return Collections.emptyList();
        }
        return decisions.stream().map(this::toDecisionSummary).toList();
    }

    CommitSummary toCommitSummary(ProjectCommit commit);

    default List<CommitSummary> toCommitSummaries(List<ProjectCommit> commits) {
        if (commits == null || commits.isEmpty()) {
            return Collections.emptyList();
        }
        return commits.stream().map(this::toCommitSummary).toList();
    }
}
