package com.hopeful117.devlogai.engineeringevent.execution;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@Component
class EngineeringEventExecutionKey {
    private final ObjectMapper mapper;
    EngineeringEventExecutionKey(ObjectMapper mapper) { this.mapper = mapper; }
    String compute(PreparedEngineeringEventExecution value) {
        byte[] input = mapper.writeValueAsBytes(Map.of(
                "projectId", value.projectId(), "sourceId", value.sourceId(),
                "targetCommit", value.targetCommit(), "intent", value.intent().key(),
                "guidance", value.guidance() == null ? Map.of() : value.guidance()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
