package com.hopeful117.devlogai.knowledge.selection;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;

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
        RepositoryContext repositoryContext,
        SelectionMetadata selectionMetadata,
        String selectionDigest
) {
    public SelectedKnowledge {
        selectedObservations = List.copyOf(selectedObservations);
        selectedFacts = List.copyOf(selectedFacts);
        selectedInsights = List.copyOf(selectedInsights);
    }

    public record DiagnosticSnapshot(boolean collectionComplete, boolean truncated,
                                     int warningCount, int errorCount) { }

    public record InsightSnapshot(UUID id, UUID analysisId, InsightType type,
                                  InsightSeverity severity, String title, String content) { }

    public record SelectionMetadata(String selectionVersion, List<String> appliedRules,
                                    int selectedKnowledgeCount, int discardedKnowledgeCount,
                                    KnowledgeBudget knowledgeBudget, String completeness) {
        public SelectionMetadata { appliedRules = List.copyOf(appliedRules); }
    }

    public record KnowledgeBudget(int maximumFacts, int maximumObservations,
                                  int maximumInsights, int maximumRepositoryEvidence) { }
}
