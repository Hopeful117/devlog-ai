package com.hopeful117.devlogai.retrieval.event;

import java.util.UUID;

public record DecisionChangedEvent(UUID projectId, UUID decisionId) {}
