package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;

import java.util.List;

public record ProjectContextSnapshot(
        AnalysisContext.ProjectSnapshot project,
        ProjectProfileResponse latestProjectProfile,
        List<AnalysisContext.KnowledgeEventSnapshot> recentKnowledgeEvents,
        List<AnalysisContext.ValidatedProposalSnapshot> validatedProposals,
        List<AnalysisContext.ArtifactSnapshot> architectureArtifacts,
        List<AnalysisContext.DecisionSnapshot> relatedDecisions,
        List<AnalysisContext.MilestoneSnapshot> recentMilestones,
        List<AnalysisContext.AnalysisSnapshot> recentAnalyses,
        List<EngineeringEventSnapshot> validatedEngineeringEvents
) {
    public ProjectContextSnapshot {
        recentKnowledgeEvents = List.copyOf(recentKnowledgeEvents);
        validatedProposals = List.copyOf(validatedProposals);
        architectureArtifacts = List.copyOf(architectureArtifacts);
        relatedDecisions = List.copyOf(relatedDecisions);
        recentMilestones = List.copyOf(recentMilestones);
        recentAnalyses = List.copyOf(recentAnalyses);
        validatedEngineeringEvents = List.copyOf(validatedEngineeringEvents);
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
                architectureArtifacts, relatedDecisions, recentMilestones, recentAnalyses, List.of());
    }

    public record EngineeringEventSnapshot(java.util.UUID id, String category, String title,
            String summary, java.util.UUID sourceId, String baseCommit, String targetCommit,
            java.time.Instant occurredAt, java.util.UUID proposalId) { }
}
