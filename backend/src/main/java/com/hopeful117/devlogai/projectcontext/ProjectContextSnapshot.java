package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;

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
        List<AnalysisContext.AnalysisSnapshot> recentAnalyses
) {
    public ProjectContextSnapshot {
        recentKnowledgeEvents = List.copyOf(recentKnowledgeEvents);
        validatedProposals = List.copyOf(validatedProposals);
        architectureArtifacts = List.copyOf(architectureArtifacts);
        relatedDecisions = List.copyOf(relatedDecisions);
        recentMilestones = List.copyOf(recentMilestones);
        recentAnalyses = List.copyOf(recentAnalyses);
    }
}
