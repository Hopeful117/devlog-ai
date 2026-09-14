package com.hopeful117.devlogai.knowledge.selection;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.projectcontext.EngineeringRelationship;
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
        List<ProjectContextSnapshot.KnowledgeRelationSnapshot> knowledgeRelations,
        List<AdmissionDiagnostic> admissionDiagnostics,
        RepositoryContext repositoryContext,
        AnalysisContext.EvolutionContext evolutionContext,
        SelectionMetadata selectionMetadata,
        String selectionDigest,
        List<EngineeringRelationship> engineeringRelationships
) {
    public SelectedKnowledge {
        selectedObservations = List.copyOf(selectedObservations);
        selectedFacts = List.copyOf(selectedFacts);
        selectedInsights = List.copyOf(selectedInsights);
        existingArchitectureKnowledge = List.copyOf(existingArchitectureKnowledge);
        selectedEngineeringEvents = List.copyOf(selectedEngineeringEvents);
        selectedHumanContextInputs = List.copyOf(selectedHumanContextInputs);
        knowledgeRelations = List.copyOf(knowledgeRelations);
        admissionDiagnostics = List.copyOf(admissionDiagnostics);
        engineeringRelationships = List.copyOf(engineeringRelationships);
    }

    public SelectedKnowledge(AnalysisContext.ProjectSnapshot project,
            AnalysisContext.AnalysisSnapshot analysis, ProjectProfileResponse projectProfile,
            List<AnalysisContext.ObservationSnapshot> selectedObservations,
            List<AnalysisContext.FactSnapshot> selectedFacts, DiagnosticSnapshot diagnostics,
            List<InsightSnapshot> selectedInsights, RepositoryContext repositoryContext,
            SelectionMetadata selectionMetadata, String selectionDigest) {
        this(project, analysis, projectProfile, selectedObservations, selectedFacts, diagnostics,
                selectedInsights, List.of(), List.of(), List.of(), List.of(), List.of(), repositoryContext, null,
                selectionMetadata, selectionDigest, List.of());
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
            List<com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot.HumanContextInputSnapshot>
                    selectedHumanContextInputs,
            RepositoryContext repositoryContext,
            AnalysisContext.EvolutionContext evolutionContext,
            SelectionMetadata selectionMetadata,
            String selectionDigest
    ) {
        this(project, analysis, projectProfile, selectedObservations, selectedFacts, diagnostics,
                selectedInsights, existingArchitectureKnowledge, selectedEngineeringEvents,
                 selectedHumanContextInputs, List.of(), List.of(), repositoryContext, evolutionContext, selectionMetadata, selectionDigest,
                 List.of());
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
                 List.of(), List.of(), List.of(), repositoryContext, evolutionContext, selectionMetadata, selectionDigest,
                 List.of());
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
            List<ProjectContextSnapshot.EngineeringEventSnapshot> selectedEngineeringEvents,
            List<ProjectContextSnapshot.HumanContextInputSnapshot> selectedHumanContextInputs,
            List<ProjectContextSnapshot.KnowledgeRelationSnapshot> knowledgeRelations,
            RepositoryContext repositoryContext,
            AnalysisContext.EvolutionContext evolutionContext,
            SelectionMetadata selectionMetadata,
            String selectionDigest
    ) {
        this(project, analysis, projectProfile, selectedObservations, selectedFacts, diagnostics,
                selectedInsights, existingArchitectureKnowledge, selectedEngineeringEvents,
                selectedHumanContextInputs, knowledgeRelations, List.of(), repositoryContext,
                evolutionContext, selectionMetadata, selectionDigest, List.of());
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
            List<ProjectContextSnapshot.EngineeringEventSnapshot> selectedEngineeringEvents,
            List<ProjectContextSnapshot.HumanContextInputSnapshot> selectedHumanContextInputs,
            List<ProjectContextSnapshot.KnowledgeRelationSnapshot> knowledgeRelations,
            List<AdmissionDiagnostic> admissionDiagnostics,
            RepositoryContext repositoryContext,
            AnalysisContext.EvolutionContext evolutionContext,
            SelectionMetadata selectionMetadata,
            String selectionDigest
    ) {
        this(project, analysis, projectProfile, selectedObservations, selectedFacts, diagnostics,
                selectedInsights, existingArchitectureKnowledge, selectedEngineeringEvents,
                selectedHumanContextInputs, knowledgeRelations, admissionDiagnostics, repositoryContext,
                evolutionContext, selectionMetadata, selectionDigest, List.of());
    }

    public record DiagnosticSnapshot(boolean collectionComplete, boolean truncated,
                                     int warningCount, int errorCount) { }

    public record InsightSnapshot(UUID id, UUID analysisId, InsightType type,
                                  InsightSeverity severity, String title, String content) { }

    public record AdmissionDiagnostic(
            UUID relationId,
            String reason,
            String relationType,
            String sourceEntityType,
            UUID sourceEntityId,
            String targetEntityType,
            UUID targetEntityId
    ) { }

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
            evidenceReferences = evidenceReferences == null ? List.of() : List.copyOf(evidenceReferences);
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
