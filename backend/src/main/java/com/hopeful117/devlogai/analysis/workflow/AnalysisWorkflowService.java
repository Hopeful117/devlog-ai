package com.hopeful117.devlogai.analysis.workflow;

import com.hopeful117.devlogai.analysis.workflow.dto.AnalysisWorkflowResult;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;

import java.util.UUID;

public interface AnalysisWorkflowService {

    AnalysisWorkflowResult start(UUID analysisId);

    /**
     * Starts the workflow on behalf of an authenticated principal. When the
     * principal is present, Core issues and registers the user-bound provider
     * credential binding for the AI task before submission (Story 0241).
     */
    default AnalysisWorkflowResult start(UUID analysisId, AuthenticatedPrincipal principal) {
        if (principal != null) {
            throw new IllegalStateException("user-bound analysis workflow is not supported");
        }
        return start(analysisId);
    }
}
