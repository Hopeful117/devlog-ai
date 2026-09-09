package com.hopeful117.devlogai.knowledge.selection;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;
import com.hopeful117.devlogai.repositorycontext.RepositoryRevisionScope;

public interface KnowledgeSelectionService {
    SelectedKnowledge select(AnalysisContext context, IntentDefinition intent, UserGuidance guidance);

    SelectedKnowledge select(AnalysisContext context, IntentDefinition intent, UserGuidance guidance,
                            RepositoryRevisionScope revisionScope);
}
