package com.hopeful117.devlogai.repositorycontext.profile;

import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import org.springframework.stereotype.Component;

@Component
public class ContextProfileResolver {
    public ContextProfile resolve(AnalysisType analysisType, IntentDefinition intent) {
        return switch (intent.id()) {
            case "architecture-overview" -> ContextProfile.ARCHITECTURE_REVIEW;
            case "generate-readme" -> ContextProfile.README_GENERATION;
            case "describe-project" -> ContextProfile.PROJECT_STATE;
            default -> switch (analysisType) {
                case ARCHITECTURE_REVIEW -> ContextProfile.ARCHITECTURE_REVIEW;
                case PROJECT_EVOLUTION -> ContextProfile.HISTORY_ANALYSIS;
                default -> ContextProfile.KNOWLEDGE_EXTRACTION;
            };
        };
    }
}
