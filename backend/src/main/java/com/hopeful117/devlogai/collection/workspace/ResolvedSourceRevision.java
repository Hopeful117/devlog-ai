package com.hopeful117.devlogai.collection.workspace;

import java.util.UUID;

public record ResolvedSourceRevision(
        UUID sourceId,
        String requestedRevision,
        String resolvedRevision
) { }
