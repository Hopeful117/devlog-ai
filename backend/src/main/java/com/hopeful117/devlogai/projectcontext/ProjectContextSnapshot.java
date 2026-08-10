package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.knowledge.relation.entity.EntityType;
import com.hopeful117.devlogai.knowledge.relation.entity.KnowledgeRelationType;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;

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
        List<KnowledgeRelationSnapshot> knowledgeRelations
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
                List.of(), List.of(), List.of());
    }

    public record EngineeringEventSnapshot(UUID id, String category, String title,
            String summary, UUID sourceId, String baseCommit, String targetCommit,
            Instant occurredAt, UUID proposalId) { }

    public record ChallengeSnapshot(UUID id, String title, String description,
            String impact, String status, String resolution, Instant createdAt) { }

    public record KnowledgeRelationSnapshot(UUID id, EntityType sourceEntityType,
            UUID sourceEntityId, EntityType targetEntityType, UUID targetEntityId,
            KnowledgeRelationType relationType, String description, Instant createdAt) { }
}
