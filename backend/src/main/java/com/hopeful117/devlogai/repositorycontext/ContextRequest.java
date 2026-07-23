package com.hopeful117.devlogai.repositorycontext;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextPlan;

import java.util.List;

public record ContextRequest(
        AnalysisContext analysisContext,
        IntentDefinition intent,
        UserGuidance guidance,
        List<Insight> validatedInsights,
        ContextPlan contextPlan,
        RepositoryContext.ContextBudget budget
) {
    public ContextRequest {
        validatedInsights = List.copyOf(validatedInsights);
    }

    public ContextProfile profile() {
        return contextPlan.primaryProfile();
    }
}
