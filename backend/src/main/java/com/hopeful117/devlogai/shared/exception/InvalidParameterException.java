package com.hopeful117.devlogai.shared.exception;

public class InvalidParameterException extends RuntimeException {
    public InvalidParameterException(String parameter, Object value) {
        super("Invalid value for parameter '%s': %s".formatted(parameter, value));
    }
}
