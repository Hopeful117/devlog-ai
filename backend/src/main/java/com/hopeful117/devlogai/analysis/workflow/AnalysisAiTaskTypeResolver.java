package com.hopeful117.devlogai.analysis.workflow;

import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.workflow.exception.UnsupportedAnalysisTypeException;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import org.springframework.stereotype.Component;

@Component
public class AnalysisAiTaskTypeResolver {

    public AiTaskType resolve(IntentDefinition intent) {
        return switch (intent.outputProposalType()) {
            case INSIGHT -> AiTaskType.INSIGHT_GENERATION;
            case ENGINEERING_EVENT -> AiTaskType.EVENT_PROPOSAL_GENERATION;
            case ENGINEERING_DECISION -> AiTaskType.DECISION_PROPOSAL_GENERATION;
            default -> throw new IllegalArgumentException(
                    "Unsupported proposal output type: " + intent.outputProposalType());
        };
    }

    public AiTaskType resolve(AnalysisType analysisType, IntentDefinition intent) {
        if (analysisType != AnalysisType.ARCHITECTURE_REVIEW
                && analysisType != AnalysisType.PROJECT_EVOLUTION) {
            throw new UnsupportedAnalysisTypeException(analysisType);
        }
        return resolve(intent);
    }
}
