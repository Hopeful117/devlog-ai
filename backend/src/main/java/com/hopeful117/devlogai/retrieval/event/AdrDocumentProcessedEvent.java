package com.hopeful117.devlogai.retrieval.event;

import java.util.UUID;

public record AdrDocumentProcessedEvent(
        UUID projectId,
        UUID sourceId,
        String path,
        String title,
        String content
) {}
