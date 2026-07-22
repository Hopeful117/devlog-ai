package com.hopeful117.devlogai.intent.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public record UserGuidance(
        @Size(min = 1, max = 500) String focus,
        @Size(min = 1, max = 200) String audience,
        @Size(min = 1, max = 100) String levelOfDetail,
        @Size(min = 1, max = 100) String writingStyle,
        @Size(min = 1, max = 500) String outputContext,
        @Size(max = 10) List<@NotBlank @Size(max = 300) String> priorities
) {
    public UserGuidance {
        focus = normalize(focus);
        audience = normalize(audience);
        levelOfDetail = normalize(levelOfDetail);
        writingStyle = normalize(writingStyle);
        outputContext = normalize(outputContext);
        priorities = priorities == null ? List.of()
                : priorities.stream().map(UserGuidance::normalize).toList();
    }

    @JsonIgnore
    public boolean isEmpty() {
        return blank(focus) && blank(audience) && blank(levelOfDetail)
                && blank(writingStyle) && blank(outputContext) && priorities.isEmpty();
    }

    public static UserGuidance from(Map<String, Object> values) {
        if (values == null || values.isEmpty()) return null;
        Object rawPriorities = values.get("priorities");
        List<String> priorities = rawPriorities instanceof List<?> list
                ? list.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                : List.of();
        return new UserGuidance(text(values, "focus"), text(values, "audience"),
                text(values, "levelOfDetail"), text(values, "writingStyle"),
                text(values, "outputContext"), priorities);
    }

    private static String text(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof String text ? text : null;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
