package com.hopeful117.devlogai.analysis.workflow;

import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalysisAiTaskTypeResolverTest {

    private final AnalysisAiTaskTypeResolver resolver =
            new AnalysisAiTaskTypeResolver();

    @Test
    void shouldMapIntentOutputTypes() {
        IntentCatalog intents = new IntentCatalog();
        assertEquals(AiTaskType.INSIGHT_GENERATION,
                resolver.resolve(intents.resolve("describe-project-v1")));
        assertEquals(AiTaskType.EVENT_PROPOSAL_GENERATION,
                resolver.resolve(intents.resolve("analyze-engineering-event-v1")));
        assertEquals(AiTaskType.DECISION_PROPOSAL_GENERATION,
                resolver.resolve(intents.resolve("analyze-engineering-decision-v1")));
    }
}
