package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.artifact.entity.Artifact;
import com.hopeful117.devlogai.artifact.entity.ArtifactType;
import com.hopeful117.devlogai.artifact.repository.ArtifactRepository;
import com.hopeful117.devlogai.challenge.entity.Challenge;
import com.hopeful117.devlogai.challenge.repository.ChallengeRepository;
import com.hopeful117.devlogai.decision.entity.Decision;
import com.hopeful117.devlogai.decision.repository.DecisionRepository;
import com.hopeful117.devlogai.knowledge.entity.KnowledgeEvent;
import com.hopeful117.devlogai.knowledge.relation.entity.KnowledgeRelation;
import com.hopeful117.devlogai.knowledge.relation.repository.KnowledgeRelationRepository;
import com.hopeful117.devlogai.knowledge.repository.KnowledgeEventRepository;
import com.hopeful117.devlogai.milestone.entity.Milestone;
import com.hopeful117.devlogai.milestone.repository.MilestoneRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.profile.service.ProjectProfileService;
import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.engineeringevent.EngineeringEvent;
import com.hopeful117.devlogai.engineeringevent.EngineeringEventRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInput;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputStatus;
import com.hopeful117.devlogai.projectcontextinput.repository.ProjectHumanContextInputRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectContextProviderImpl implements ProjectContextProvider {

    static final int MAX_RECENT_EVENTS = 20;
    static final int MAX_VALIDATED_PROPOSALS = 20;
    static final int MAX_ARCHITECTURE_ARTIFACTS = 20;
    static final int MAX_ARCHITECTURE_DECISIONS = 20;
    static final int MAX_RECENT_MILESTONES = 10;
    static final int MAX_RELATED_ANALYSES = 10;
    static final int MAX_VALIDATED_ENGINEERING_EVENTS = 10;
    static final int MAX_OPEN_CHALLENGES = 20;
    static final int MAX_KNOWLEDGE_RELATIONS = 50;
    static final int MAX_ENGINEERING_STORIES = 20;
    static final int MAX_HUMAN_CONTEXT_INPUTS = 10;

    private static final List<ArtifactType> ARCHITECTURE_ARTIFACT_TYPES = List.of(
            ArtifactType.API,
            ArtifactType.CONFIGURATION,
            ArtifactType.DATABASE,
            ArtifactType.INFRASTRUCTURE
    );

    private final ProjectRepository projectRepository;
    private final ProjectProfileService projectProfileService;
    private final KnowledgeEventRepository knowledgeEventRepository;
    private final ValidatableProposalRepository proposalRepository;
    private final ArtifactRepository artifactRepository;
    private final DecisionRepository decisionRepository;
    private final MilestoneRepository milestoneRepository;
    private final AnalysisRepository analysisRepository;
    private final EngineeringEventRepository engineeringEventRepository;
    private final ChallengeRepository challengeRepository;
    private final KnowledgeRelationRepository knowledgeRelationRepository;
    private final EngineeringStoryRepository engineeringStoryRepository;
    private final ProjectHumanContextInputRepository humanContextInputRepository;

    @Override
    public ProjectContextSnapshot build(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new com.hopeful117.devlogai.shared.exception.EntityNotFoundException("Project", projectId));

        ProjectProfileResponse latestProfile = latestProfileOrNull(projectId);

        List<AnalysisContext.KnowledgeEventSnapshot> recentKnowledgeEvents =
                knowledgeEventRepository.findByProjectIdOrderByCreatedAtDescIdDesc(
                                projectId, PageRequest.of(0, MAX_RECENT_EVENTS))
                        .stream()
                        .map(this::toKnowledgeEventSnapshot)
                        .toList();

        List<AnalysisContext.ValidatedProposalSnapshot> validatedProposals =
                proposalRepository.findByProjectIdAndStatusOrderByCreatedAtDescIdDesc(
                                projectId, ProposalStatus.ACCEPTED,
                                PageRequest.of(0, MAX_VALIDATED_PROPOSALS))
                        .stream()
                        .map(this::toValidatedProposalSnapshot)
                        .toList();

        List<AnalysisContext.ArtifactSnapshot> architectureArtifacts =
                artifactRepository.findByProjectIdAndTypeInOrderByCreatedAtDescIdDesc(
                                projectId, ARCHITECTURE_ARTIFACT_TYPES,
                                PageRequest.of(0, MAX_ARCHITECTURE_ARTIFACTS))
                        .stream()
                        .map(this::toArtifactSnapshot)
                        .toList();

        List<AnalysisContext.DecisionSnapshot> relatedDecisions =
                decisionRepository.findByProjectIdOrderByCreatedAtDescIdDesc(
                                projectId, PageRequest.of(0, MAX_ARCHITECTURE_DECISIONS))
                        .stream()
                        .map(this::toDecisionSnapshot)
                        .toList();

        List<AnalysisContext.MilestoneSnapshot> recentMilestones =
                milestoneRepository.findByProjectIdOrderByStartedAtDescIdDesc(
                                projectId, PageRequest.of(0, MAX_RECENT_MILESTONES))
                        .stream()
                        .map(this::toMilestoneSnapshot)
                        .toList();

        List<AnalysisContext.AnalysisSnapshot> recentAnalyses =
                analysisRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                        .stream()
                        .map(this::toAnalysisSnapshot)
                        .toList();

        List<ProjectContextSnapshot.EngineeringEventSnapshot> engineeringEvents =
                engineeringEventRepository.findRecentByProjectIdOrderByOccurredAtDescTargetCommitDescIdAsc(
                                projectId, PageRequest.of(0, MAX_VALIDATED_ENGINEERING_EVENTS))
                        .stream().map(this::toEngineeringEvent).toList();

        List<ProjectContextSnapshot.ChallengeSnapshot> openChallenges =
                challengeRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                        .stream()
                        .limit(MAX_OPEN_CHALLENGES)
                        .map(this::toChallengeSnapshot)
                        .toList();

        List<ProjectContextSnapshot.KnowledgeRelationSnapshot> knowledgeRelations =
                knowledgeRelationRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                        .stream()
                        .limit(MAX_KNOWLEDGE_RELATIONS)
                        .map(this::toKnowledgeRelationSnapshot)
                        .toList();

        List<ProjectContextSnapshot.EngineeringStorySnapshot> engineeringStories =
                engineeringStoryRepository.findByProject_IdOrderByCreatedAtDesc(projectId)
                        .stream()
                        .limit(MAX_ENGINEERING_STORIES)
                        .map(this::toEngineeringStorySnapshot)
                        .toList();

        List<ProjectContextSnapshot.HumanContextInputSnapshot> humanContextInputs =
                Objects.requireNonNullElse(
                                humanContextInputRepository
                        .findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc(
                                projectId, ProjectHumanContextInputStatus.ACTIVE),
                                List.<ProjectHumanContextInput>of())
                        .stream()
                        .limit(MAX_HUMAN_CONTEXT_INPUTS)
                        .map(this::toHumanContextInputSnapshot)
                        .toList();

        return new ProjectContextSnapshot(
                toProjectSnapshot(project),
                latestProfile,
                recentKnowledgeEvents,
                validatedProposals,
                architectureArtifacts,
                relatedDecisions,
                recentMilestones,
                recentAnalyses,
                engineeringEvents,
                openChallenges,
                knowledgeRelations,
                engineeringStories,
                humanContextInputs
        );
    }

    private ProjectProfileResponse latestProfileOrNull(UUID projectId) {
        try {
            return projectProfileService.getLatestByProject(projectId);
        } catch (EntityNotFoundException error) {
            String expected = "Project profile not found with identifier: %s".formatted(projectId);
            if (expected.equals(error.getMessage())) {
                return null;
            }
            throw error;
        }
    }

    private ProjectContextSnapshot.EngineeringEventSnapshot toEngineeringEvent(EngineeringEvent event) {
        return new ProjectContextSnapshot.EngineeringEventSnapshot(event.getId(),
                event.getCategory().name(), event.getTitle(), event.getSummary(),
                event.getSource().getId(), event.getBaseCommit(), event.getTargetCommit(),
                event.getOccurredAt(), event.getProposal().getId());
    }

    private ProjectContextSnapshot.ChallengeSnapshot toChallengeSnapshot(Challenge challenge) {
        return new ProjectContextSnapshot.ChallengeSnapshot(
                challenge.getId(), challenge.getTitle(), challenge.getDescription(),
                challenge.getImpact(), challenge.getStatus().name(),
                challenge.getResolution(), challenge.getCreatedAt()
        );
    }

    private ProjectContextSnapshot.KnowledgeRelationSnapshot toKnowledgeRelationSnapshot(
            KnowledgeRelation relation) {
        return new ProjectContextSnapshot.KnowledgeRelationSnapshot(
                relation.getId(), relation.getSourceEntityType(),
                relation.getSourceEntityId(), relation.getTargetEntityType(),
                relation.getTargetEntityId(), relation.getRelationType(),
                relation.getDescription(), relation.getCreatedAt()
        );
    }

    private ProjectContextSnapshot.EngineeringStorySnapshot toEngineeringStorySnapshot(
            EngineeringStory story) {
        return new ProjectContextSnapshot.EngineeringStorySnapshot(
                story.getId(), story.getProject().getId(), story.getStoryNumber(),
                story.getTitle(), story.getStatus().name(), story.getStoryPath(),
                story.getBaseCommit(), story.getTargetCommit(),
                story.getCreatedAt(), story.getCompletedAt()
        );
    }

    private ProjectContextSnapshot.HumanContextInputSnapshot toHumanContextInputSnapshot(
            ProjectHumanContextInput input
    ) {
        return new ProjectContextSnapshot.HumanContextInputSnapshot(
                input.getId(),
                input.getType(),
                input.getTitle(),
                input.getContentMarkdown(),
                input.getStatus().name(),
                input.getUpdatedAt()
        );
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

    private AnalysisContext.KnowledgeEventSnapshot toKnowledgeEventSnapshot(KnowledgeEvent event) {
        return new AnalysisContext.KnowledgeEventSnapshot(
                event.getId(), event.getType(), event.getTitle(), event.getDescription(),
                event.getCreatedAt()
        );
    }

    private AnalysisContext.ArtifactSnapshot toArtifactSnapshot(com.hopeful117.devlogai.artifact.entity.Artifact artifact) {
        return new AnalysisContext.ArtifactSnapshot(
                artifact.getId(), artifact.getType(), artifact.getName(), artifact.getPath(),
                artifact.getDescription(), artifact.getCreatedAt()
        );
    }

    private AnalysisContext.DecisionSnapshot toDecisionSnapshot(Decision decision) {
        return new AnalysisContext.DecisionSnapshot(
                decision.getId(), decision.getTitle(), decision.getContext(),
                decision.getChoice(), decision.getRationale(), decision.getConsequences(),
                decision.getCreatedAt()
        );
    }

    private AnalysisContext.MilestoneSnapshot toMilestoneSnapshot(Milestone milestone) {
        return new AnalysisContext.MilestoneSnapshot(
                milestone.getId(), milestone.getName(), milestone.getDescription(),
                milestone.getStatus(), milestone.getStartedAt(), milestone.getCompletedAt()
        );
    }

    private AnalysisContext.ValidatedProposalSnapshot toValidatedProposalSnapshot(
            ValidatableProposal proposal
    ) {
        return new AnalysisContext.ValidatedProposalSnapshot(
                proposal.getId(), proposal.getType(), proposal.getPayload(),
                proposal.getCreatedAt(), proposal.getDecidedAt()
        );
    }
}
