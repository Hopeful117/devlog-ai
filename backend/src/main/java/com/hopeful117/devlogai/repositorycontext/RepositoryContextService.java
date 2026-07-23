package com.hopeful117.devlogai.repositorycontext;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;

import java.util.List;

public interface RepositoryContextService {
    RepositoryContext build(
            AnalysisContext context,
            IntentDefinition intent,
            UserGuidance guidance,
            List<Insight> validatedInsights
    );
}
