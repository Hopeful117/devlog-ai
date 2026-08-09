package com.hopeful117.devlogai.projectunderstanding;

import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class ProjectUnderstandingExecutionKey {
    private static final String VERSION = "project-understanding-execution-v1";
    private static final String DEFAULT_REVISION = "<default>";
    private final ObjectMapper objectMapper;

    public ProjectUnderstandingExecutionKey(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String compute(UUID projectId, UUID sourceId, String targetRevision,
                          IntentDefinition intent, UserGuidance guidance) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("version", VERSION);
        canonical.put("projectId", projectId.toString());
        canonical.put("sourceId", sourceId.toString());
        canonical.put("targetRevision", normalizeRevision(targetRevision));
        canonical.put("intentId", intent.id());
        canonical.put("intentVersion", intent.version());
        canonical.put("guidance", guidance == null || guidance.isEmpty() ? null : guidance);
        try {
            byte[] json = objectMapper.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public String normalizeRevision(String revision) {
        return revision == null || revision.isBlank() ? DEFAULT_REVISION : revision.trim();
    }
}
