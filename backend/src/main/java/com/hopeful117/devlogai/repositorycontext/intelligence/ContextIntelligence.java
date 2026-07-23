package com.hopeful117.devlogai.repositorycontext.intelligence;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.intent.model.IntentDefinition;

public interface ContextIntelligence {
    ContextPlan plan(AnalysisContext context, IntentDefinition intent);
}
