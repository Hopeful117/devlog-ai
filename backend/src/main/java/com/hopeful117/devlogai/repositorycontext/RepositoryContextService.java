package com.hopeful117.devlogai.repositorycontext;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;

import java.util.List;

public interface RepositoryContextService {
    /**
     * Shared retrieval primitive (ADR-063): returns the complete unfiltered
     * pre-composition candidate set from all registered collectors, before
     * ranking and before the evidence budget is applied.
     */
    List<RepositoryEvidence> retrieveCandidates(
            AnalysisContext context,
            IntentDefinition intent,
            UserGuidance guidance,
            List<Insight> validatedInsights
    );

    RepositoryContext build(
            AnalysisContext context,
            IntentDefinition intent,
            UserGuidance guidance,
            List<Insight> validatedInsights
    );

    /**
     * Builds a RepositoryContext, merging additional promoted candidates into
     * the collector output before ranking and selection.  Promoted candidates
     * are subject to the same budget, ranking, and diversity rules as
     * collector output.
     */
    RepositoryContext build(
            AnalysisContext context,
            IntentDefinition intent,
            UserGuidance guidance,
            List<Insight> validatedInsights,
            List<RepositoryEvidence> additionalCandidates
    );
}
