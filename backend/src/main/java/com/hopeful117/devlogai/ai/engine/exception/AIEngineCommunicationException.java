package com.hopeful117.devlogai.ai.engine.exception;

public class AIEngineCommunicationException extends RuntimeException {

    public AIEngineCommunicationException(String message) {
        super(message);
    }

    public AIEngineCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
