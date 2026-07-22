package com.hopeful117.devlogai.analysis.workflow;

import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.workflow.exception.UnsupportedAnalysisTypeException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class AnalysisAiTaskTypeResolver {

    private static final Map<AnalysisType, AiTaskType> SUPPORTED_TYPES = supportedTypes();

    public AiTaskType resolve(AnalysisType analysisType) {
        AiTaskType taskType = SUPPORTED_TYPES.get(analysisType);
        if (taskType == null) {
            throw new UnsupportedAnalysisTypeException(analysisType);
        }
        return taskType;
    }

    private static Map<AnalysisType, AiTaskType> supportedTypes() {
        EnumMap<AnalysisType, AiTaskType> types = new EnumMap<>(AnalysisType.class);
        types.put(AnalysisType.ARCHITECTURE_REVIEW, AiTaskType.INSIGHT_GENERATION);
        types.put(AnalysisType.PROJECT_EVOLUTION, AiTaskType.INSIGHT_GENERATION);
        return Map.copyOf(types);
    }
}
