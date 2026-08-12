package com.hopeful117.devlogai.insight.service;

import com.hopeful117.devlogai.insight.entity.InsightType;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class InsightPayloadSupport {
    private InsightPayloadSupport() {
    }

    public static String requiredText(Map<String, Object> payload, String field) {
        Object value = payload == null ? null : payload.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Accepted insight proposal is missing payload field: " + field);
        }
        return text;
    }

    public static String optionalText(Map<String, Object> payload, String field) {
        Object value = payload == null ? null : payload.get(field);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    public static UUID requiredUuid(Map<String, Object> payload, String field) {
        Object value = payload == null ? null : payload.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Accepted insight proposal is missing payload field: " + field);
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Accepted insight proposal has invalid UUID field: " + field);
        }
    }

    public static InsightType toDomainType(String proposalType) {
        return switch (proposalType) {
            case "ARCHITECTURE_DESCRIPTION", "INFRASTRUCTURE_DESCRIPTION" -> InsightType.ARCHITECTURAL;
            case "TECHNOLOGY_DESCRIPTION" -> InsightType.TECHNOLOGY;
            case "PROJECT_PRESENTATION", "INSTALLATION", "USAGE", "REQUIREMENTS", "API_DESCRIPTION" ->
                    InsightType.DOCUMENTATION;
            default -> throw new IllegalArgumentException("Unsupported insight proposal type: " + proposalType);
        };
    }

    public static String normalize(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
