package com.hopeful117.devlogai.projectfreshness;

import java.util.UUID;

public class SourceRevisionUnavailableException extends RuntimeException {
    public SourceRevisionUnavailableException(UUID sourceId, Throwable cause) {
        super("The current revision could not be resolved for Source " + sourceId + ".", cause);
    }
}
