package com.hopeful117.devlogai.collection.collector;

public class NonFatalCollectionException extends RuntimeException {
    private final String code;

    public NonFatalCollectionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}
