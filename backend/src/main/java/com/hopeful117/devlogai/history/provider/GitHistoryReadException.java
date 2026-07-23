package com.hopeful117.devlogai.history.provider;

public class GitHistoryReadException extends RuntimeException {
    public GitHistoryReadException(String message, Throwable cause) {
        super(message, cause);
    }

    public GitHistoryReadException(String message) {
        super(message);
    }
}
