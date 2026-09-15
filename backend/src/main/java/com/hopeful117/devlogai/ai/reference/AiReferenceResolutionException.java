package com.hopeful117.devlogai.ai.reference;

/** Hard failure raised when an execution-scoped AI reference cannot be authorized. */
public class AiReferenceResolutionException extends RuntimeException {
    private final String code;

    public AiReferenceResolutionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
