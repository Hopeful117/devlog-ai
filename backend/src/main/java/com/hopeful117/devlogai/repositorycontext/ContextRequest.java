package com.hopeful117.devlogai.repositorycontext;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextPlan;

import java.util.List;
import java.util.Objects;

public record ContextRequest(
        AnalysisContext analysisContext,
        IntentDefinition intent,
        UserGuidance guidance,
        List<Insight> validatedInsights,
        ContextPlan contextPlan,
        RepositoryContext.ContextBudget budget,
        RepositoryRevisionScope revisionScope
) {
    public ContextRequest {
        validatedInsights = List.copyOf(validatedInsights);
    }

    public ContextRequest(
            AnalysisContext analysisContext,
            IntentDefinition intent,
            UserGuidance guidance,
            List<Insight> validatedInsights,
            ContextPlan contextPlan,
            RepositoryContext.ContextBudget budget
    ) {
        this(analysisContext, intent, guidance, validatedInsights, contextPlan, budget, null);
    }

    public ContextProfile profile() {
        return contextPlan.primaryProfile();
    }

    public RepositoryRevisionScope requireRevisionScope() {
        Objects.requireNonNull(revisionScope, "RepositoryRevisionScope is required but was not provided");
        return revisionScope;
    }
}
