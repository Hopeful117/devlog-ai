package com.hopeful117.devlogai.engineeringevent.execution;

import com.hopeful117.devlogai.history.context.CommitDiffAnalysisContext;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;
import java.util.Map;
import java.util.UUID;

record PreparedEngineeringEventExecution(
        UUID projectId, UUID sourceId, String baseCommit, String targetCommit,
        UserGuidance guidance, IntentDefinition intent, Map<String, Object> sourceSnapshot,
        CommitDiffAnalysisContext commitContext) { }
