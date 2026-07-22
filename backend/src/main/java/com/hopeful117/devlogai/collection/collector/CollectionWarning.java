package com.hopeful117.devlogai.collection.collector;

public record CollectionWarning(String code, String message) {
    public CollectionWarning {
        if (code == null || code.isBlank() || message == null || message.isBlank()) {
            throw new IllegalArgumentException("Warning code and message must not be blank");
        }
    }
}
