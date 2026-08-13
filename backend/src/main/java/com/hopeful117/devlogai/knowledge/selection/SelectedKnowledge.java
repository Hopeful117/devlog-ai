package com.hopeful117.devlogai.knowledge.selection;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SelectedKnowledge(
        AnalysisContext.ProjectSnapshot project,
        AnalysisContext.AnalysisSnapshot analysis,
        ProjectProfileResponse projectProfile,
        List<AnalysisContext.ObservationSnapshot> selectedObservations,
        List<AnalysisContext.FactSnapshot> selectedFacts,
        DiagnosticSnapshot diagnostics,
        List<InsightSnapshot> selectedInsights,
        List<ExistingArchitectureKnowledgeSnapshot> existingArchitectureKnowledge,
        List<com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot.EngineeringEventSnapshot>
                selectedEngineeringEvents,
        List<com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot.HumanContextInputSnapshot>
                selectedHumanContextInputs,
        RepositoryContext repositoryContext,
        AnalysisContext.EvolutionContext evolutionContext,
        SelectionMetadata selectionMetadata,
        String selectionDigest
) {
    public SelectedKnowledge {
        selectedObservations = List.copyOf(selectedObservations);
        selectedFacts = List.copyOf(selectedFacts);
        selectedInsights = List.copyOf(selectedInsights);
        existingArchitectureKnowledge = List.copyOf(existingArchitectureKnowledge);
        selectedEngineeringEvents = List.copyOf(selectedEngineeringEvents);
        selectedHumanContextInputs = List.copyOf(selectedHumanContextInputs);
    }

    public SelectedKnowledge(AnalysisContext.ProjectSnapshot project,
            AnalysisContext.AnalysisSnapshot analysis, ProjectProfileResponse projectProfile,
            List<AnalysisContext.ObservationSnapshot> selectedObservations,
            List<AnalysisContext.FactSnapshot> selectedFacts, DiagnosticSnapshot diagnostics,
            List<InsightSnapshot> selectedInsights, RepositoryContext repositoryContext,
            SelectionMetadata selectionMetadata, String selectionDigest) {
        this(project, analysis, projectProfile, selectedObservations, selectedFacts, diagnostics,
                selectedInsights, List.of(), List.of(), List.of(), repositoryContext, null,
                selectionMetadata, selectionDigest);
    }

    public SelectedKnowledge(
            AnalysisContext.ProjectSnapshot project,
            AnalysisContext.AnalysisSnapshot analysis,
            ProjectProfileResponse projectProfile,
            List<AnalysisContext.ObservationSnapshot> selectedObservations,
            List<AnalysisContext.FactSnapshot> selectedFacts,
            DiagnosticSnapshot diagnostics,
            List<InsightSnapshot> selectedInsights,
            List<ExistingArchitectureKnowledgeSnapshot> existingArchitectureKnowledge,
            List<com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot.EngineeringEventSnapshot>
                    selectedEngineeringEvents,
            RepositoryContext repositoryContext,
            AnalysisContext.EvolutionContext evolutionContext,
            SelectionMetadata selectionMetadata,
            String selectionDigest
    ) {
        this(project, analysis, projectProfile, selectedObservations, selectedFacts, diagnostics,
                selectedInsights, existingArchitectureKnowledge, selectedEngineeringEvents,
                List.of(), repositoryContext, evolutionContext, selectionMetadata, selectionDigest);
    }

    public record DiagnosticSnapshot(boolean collectionComplete, boolean truncated,
                                     int warningCount, int errorCount) { }

    public record InsightSnapshot(UUID id, UUID analysisId, InsightType type,
                                  InsightSeverity severity, String title, String content) { }

    public record ExistingArchitectureKnowledgeSnapshot(
            UUID insightId,
            UUID proposalId,
            InsightType normalizedType,
            InsightSeverity severity,
            String sourceType,
            String title,
            String content,
            String rationale,
            List<String> evidenceReferences,
            Instant createdAt
    ) {
        public ExistingArchitectureKnowledgeSnapshot {
            evidenceReferences = List.copyOf(evidenceReferences);
        }
    }

    public record SelectionMetadata(String selectionVersion, List<String> appliedRules,
                                    int selectedKnowledgeCount, int discardedKnowledgeCount,
                                    KnowledgeBudget knowledgeBudget, String completeness) {
        public SelectionMetadata { appliedRules = List.copyOf(appliedRules); }
    }

    public record KnowledgeBudget(int maximumFacts, int maximumObservations,
                                  int maximumInsights, int maximumArchitectureKnowledge,
                                  int maximumRepositoryEvidence) { }
}
