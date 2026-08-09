package com.hopeful117.devlogai.projectunderstanding;

import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;

import java.util.Map;
import java.util.UUID;

record PreparedProjectUnderstanding(
        UUID projectId,
        UUID sourceId,
        String targetRevision,
        String resolvedRevision,
        UserGuidance guidance,
        IntentDefinition intent,
        Map<String, Object> sourceSnapshot
) { }
