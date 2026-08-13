package com.hopeful117.devlogai.projectstate.mapper;

import com.hopeful117.devlogai.challenge.entity.Challenge;
import com.hopeful117.devlogai.decision.entity.Decision;
import com.hopeful117.devlogai.engineeringevent.EngineeringEvent;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.knowledge.entity.KnowledgeEvent;
import com.hopeful117.devlogai.milestone.entity.Milestone;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.projectstate.dto.inner.ChallengeSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.CommitSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.DecisionSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.EvolutionSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.KnowledgeSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.MilestoneSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.ProposalSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.StorySummary;
import com.hopeful117.devlogai.projectstate.dto.ProjectStateSections;
import com.hopeful117.devlogai.projectstate.dto.response.ActiveWorkSection;
import com.hopeful117.devlogai.projectstate.dto.response.ObjectiveSection;
import com.hopeful117.devlogai.projectstate.dto.response.PendingActionsSection;
import com.hopeful117.devlogai.projectstate.dto.response.ProjectStateResponse;
import com.hopeful117.devlogai.projectstate.dto.response.RecentChangesSection;
import com.hopeful117.devlogai.projectstate.dto.response.RecentEvolutionSection;
import com.hopeful117.devlogai.projectstate.dto.response.RecentKnowledgeSection;
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
    @Mapping(target = "recentKnowledge", source = "sections.recentKnowledge")
    @Mapping(target = "recentEvolution", source = "sections.recentEvolution")
    ProjectStateResponse toResponse(
            Project project,
            ProjectStateSections sections
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

    default ProposalSummary toProposalSummary(ValidatableProposal proposal) {
        if (proposal == null) {
            return null;
        }
        return new ProposalSummary(
                proposal.getId(),
                proposal.getType().name(),
                text(proposal, "insightType"),
                text(proposal, "title"),
                text(proposal, "summary"),
                proposal.getStatus(),
                proposal.getConfidence()
        );
    }

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

    KnowledgeSummary toKnowledgeSummary(KnowledgeEvent event);

    default List<KnowledgeSummary> toKnowledgeSummaries(List<KnowledgeEvent> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }
        return events.stream().map(this::toKnowledgeSummary).toList();
    }

    default RecentKnowledgeSection toRecentKnowledgeSection(List<KnowledgeEvent> events) {
        return new RecentKnowledgeSection(toKnowledgeSummaries(events));
    }

    EvolutionSummary toEvolutionSummary(EngineeringEvent event);

    default List<EvolutionSummary> toEvolutionSummaries(List<EngineeringEvent> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }
        return events.stream().map(this::toEvolutionSummary).toList();
    }

    default RecentEvolutionSection toRecentEvolutionSection(List<EngineeringEvent> events) {
        return new RecentEvolutionSection(toEvolutionSummaries(events));
    }

    private String text(ValidatableProposal proposal, String key) {
        if (proposal.getPayload() == null) {
            return null;
        }
        Object value = proposal.getPayload().get(key);
        return value instanceof String text ? text : null;
    }
}
