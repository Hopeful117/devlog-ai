package com.hopeful117.devlogai.repositorycontext.profile;

import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.intent.model.InsightType;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContextProfileResolverTest {
    private final ContextProfileResolver resolver = new ContextProfileResolver();

    @Test
    void intentTakesPriorityOverGenericAnalysisType() {
        assertEquals(ContextProfile.README_GENERATION,
                resolver.resolve(AnalysisType.ARCHITECTURE_REVIEW,
                        intent("generate-readme")));
        assertEquals(ContextProfile.ARCHITECTURE_REVIEW,
                resolver.resolve(AnalysisType.PROJECT_EVOLUTION,
                        intent("architecture-overview")));
        assertEquals(ContextProfile.PROJECT_STATE,
                resolver.resolve(AnalysisType.PROJECT_EVOLUTION,
                        intent("describe-project")));
    }

    @Test
    void unknownFutureIntentFallsBackToAnalysisProfile() {
        assertEquals(ContextProfile.HISTORY_ANALYSIS,
                resolver.resolve(AnalysisType.PROJECT_EVOLUTION, intent("future-intent")));
        assertEquals(ContextProfile.KNOWLEDGE_EXTRACTION,
                resolver.resolve(AnalysisType.TECHNICAL_DEBT, intent("future-intent")));
    }

    private IntentDefinition intent(String id) {
        return new IntentDefinition(id, "v1", "objective",
                List.of(InsightType.PROJECT_PRESENTATION), List.of("grounded"),
                Map.of("type", "object"), "template-v1");
    }
}
