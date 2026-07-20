package com.hopeful117.devlogai.shared.exception;

public class EntityNotFoundException extends RuntimeException {
    public EntityNotFoundException(String entity, Object identifier) {
        super("%s not found with identifier: %s"
                .formatted(entity, identifier));
    }
}
