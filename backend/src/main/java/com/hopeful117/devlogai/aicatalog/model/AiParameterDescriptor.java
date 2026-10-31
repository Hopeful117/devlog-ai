package com.hopeful117.devlogai.aicatalog.model;

public record AiParameterDescriptor(
        String name,
        String displayName,
        String type,
        Integer minimum,
        Integer maximum,
        Integer defaultValue
) {
    public AiParameterDescriptor {
        if (name == null || name.isBlank() || displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("AI parameter metadata must be named");
        }
        if (!"integer".equals(type) || minimum == null || maximum == null || defaultValue == null
                || minimum > defaultValue || defaultValue > maximum) {
            throw new IllegalArgumentException("AI parameter metadata is invalid");
        }
    }
}
