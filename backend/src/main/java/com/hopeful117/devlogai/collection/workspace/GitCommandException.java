package com.hopeful117.devlogai.collection.workspace;

public class GitCommandException extends RuntimeException {

    private final Integer exitCode;

    public GitCommandException(String message) {
        super(message);
        this.exitCode = null;
    }

    public GitCommandException(String message, Integer exitCode) {
        super(message);
        this.exitCode = exitCode;
    }

    public GitCommandException(String message, Throwable cause) {
        super(message, cause);
        this.exitCode = null;
    }

    public GitCommandException(String message, Integer exitCode, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public boolean isCommandNotFound() {
        return exitCode != null && exitCode == 1;
    }
}
