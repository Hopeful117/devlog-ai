package com.hopeful117.devlogai.analysis.workflow;

import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.workflow.exception.UnsupportedAnalysisTypeException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalysisAiTaskTypeResolverTest {

    private final AnalysisAiTaskTypeResolver resolver =
            new AnalysisAiTaskTypeResolver();

    @Test
    void shouldMapSupportedAnalysisTypesToInsightGeneration() {
        assertEquals(AiTaskType.INSIGHT_GENERATION,
                resolver.resolve(AnalysisType.ARCHITECTURE_REVIEW));
        assertEquals(AiTaskType.INSIGHT_GENERATION,
                resolver.resolve(AnalysisType.PROJECT_EVOLUTION));
    }

    @Test
    void shouldRejectUnsupportedAnalysisTypeWithoutFallback() {
        assertThrows(UnsupportedAnalysisTypeException.class,
                () -> resolver.resolve(AnalysisType.TECHNICAL_DEBT));
    }
}
