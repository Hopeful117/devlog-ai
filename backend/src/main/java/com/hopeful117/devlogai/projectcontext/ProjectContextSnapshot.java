package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.knowledge.relation.entity.EntityType;
import com.hopeful117.devlogai.knowledge.relation.entity.KnowledgeRelationType;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProjectContextSnapshot(
        AnalysisContext.ProjectSnapshot project,
        ProjectProfileResponse latestProjectProfile,
        List<AnalysisContext.KnowledgeEventSnapshot> recentKnowledgeEvents,
        List<AnalysisContext.ValidatedProposalSnapshot> validatedProposals,
        List<AnalysisContext.ArtifactSnapshot> architectureArtifacts,
        List<AnalysisContext.DecisionSnapshot> relatedDecisions,
        List<AnalysisContext.MilestoneSnapshot> recentMilestones,
        List<AnalysisContext.AnalysisSnapshot> recentAnalyses,
        List<EngineeringEventSnapshot> validatedEngineeringEvents,
        List<ChallengeSnapshot> openChallenges,
        List<KnowledgeRelationSnapshot> knowledgeRelations,
        List<EngineeringStorySnapshot> engineeringStories,
        List<HumanContextInputSnapshot> humanContextInputs
) {
    public ProjectContextSnapshot {
        recentKnowledgeEvents = List.copyOf(recentKnowledgeEvents);
        validatedProposals = List.copyOf(validatedProposals);
        architectureArtifacts = List.copyOf(architectureArtifacts);
        relatedDecisions = List.copyOf(relatedDecisions);
        recentMilestones = List.copyOf(recentMilestones);
        recentAnalyses = List.copyOf(recentAnalyses);
        validatedEngineeringEvents = List.copyOf(validatedEngineeringEvents);
        openChallenges = List.copyOf(openChallenges);
        knowledgeRelations = List.copyOf(knowledgeRelations);
        engineeringStories = List.copyOf(engineeringStories);
        humanContextInputs = List.copyOf(humanContextInputs);
    }

    public ProjectContextSnapshot(AnalysisContext.ProjectSnapshot project,
            ProjectProfileResponse latestProjectProfile,
            List<AnalysisContext.KnowledgeEventSnapshot> recentKnowledgeEvents,
            List<AnalysisContext.ValidatedProposalSnapshot> validatedProposals,
            List<AnalysisContext.ArtifactSnapshot> architectureArtifacts,
            List<AnalysisContext.DecisionSnapshot> relatedDecisions,
            List<AnalysisContext.MilestoneSnapshot> recentMilestones,
            List<AnalysisContext.AnalysisSnapshot> recentAnalyses) {
        this(project, latestProjectProfile, recentKnowledgeEvents, validatedProposals,
                architectureArtifacts, relatedDecisions, recentMilestones, recentAnalyses,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public ProjectContextSnapshot(
            AnalysisContext.ProjectSnapshot project,
            ProjectProfileResponse latestProjectProfile,
            List<AnalysisContext.KnowledgeEventSnapshot> recentKnowledgeEvents,
            List<AnalysisContext.ValidatedProposalSnapshot> validatedProposals,
            List<AnalysisContext.ArtifactSnapshot> architectureArtifacts,
            List<AnalysisContext.DecisionSnapshot> relatedDecisions,
            List<AnalysisContext.MilestoneSnapshot> recentMilestones,
            List<AnalysisContext.AnalysisSnapshot> recentAnalyses,
            List<EngineeringEventSnapshot> validatedEngineeringEvents,
            List<ChallengeSnapshot> openChallenges,
            List<KnowledgeRelationSnapshot> knowledgeRelations,
            List<EngineeringStorySnapshot> engineeringStories
    ) {
        this(project, latestProjectProfile, recentKnowledgeEvents, validatedProposals,
                architectureArtifacts, relatedDecisions, recentMilestones, recentAnalyses,
                validatedEngineeringEvents, openChallenges, knowledgeRelations,
                engineeringStories, List.of());
    }

    public record EngineeringEventSnapshot(UUID id, String category, String title,
            String summary, UUID sourceId, String baseCommit, String targetCommit,
            Instant occurredAt, UUID proposalId) { }

    public record ChallengeSnapshot(UUID id, String title, String description,
            String impact, String status, String resolution, Instant createdAt) { }

    public record KnowledgeRelationSnapshot(UUID id, EntityType sourceEntityType,
            UUID sourceEntityId, EntityType targetEntityType, UUID targetEntityId,
            KnowledgeRelationType relationType, String description, Instant createdAt) { }

    public record EngineeringStorySnapshot(UUID id, UUID projectId, Integer storyNumber,
            String title, String status, String storyPath, String baseCommit, String targetCommit,
            Instant createdAt, Instant completedAt) { }

    public record HumanContextInputSnapshot(
            UUID id,
            ProjectHumanContextInputType type,
            String title,
            String contentMarkdown,
            String status,
            Instant updatedAt
    ) { }
}
