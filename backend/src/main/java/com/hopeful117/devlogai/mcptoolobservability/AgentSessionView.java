package com.hopeful117.devlogai.mcptoolobservability;

import java.time.Instant;

public record AgentSessionView(
        String agentSessionId,
        String agentProvider,
        Instant firstObservedAt,
        Instant lastObservedAt,
        long operationCount,
        long respondedCount,
        long failedCount,
        long interruptedCount,
        long activeOperationCount
) {}
