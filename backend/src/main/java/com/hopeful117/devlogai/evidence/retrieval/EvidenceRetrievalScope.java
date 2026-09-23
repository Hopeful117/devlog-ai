package com.hopeful117.devlogai.evidence.retrieval;

import java.util.UUID;

public record EvidenceRetrievalScope(
        UUID projectId,
        UUID sourceId
) {
}
