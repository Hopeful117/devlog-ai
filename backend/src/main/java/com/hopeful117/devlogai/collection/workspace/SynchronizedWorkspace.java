package com.hopeful117.devlogai.collection.workspace;

import java.nio.file.Path;
import java.util.UUID;

public record SynchronizedWorkspace(
        UUID sourceId,
        Path path,
        String resolvedRevision
) {
}
