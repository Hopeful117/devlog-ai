package com.hopeful117.devlogai.retrieval.event;

import java.util.UUID;

public record InsightAcceptedEvent(UUID projectId, UUID insightId) {}
