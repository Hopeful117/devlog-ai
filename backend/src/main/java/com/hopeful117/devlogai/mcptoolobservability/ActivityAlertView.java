package com.hopeful117.devlogai.mcptoolobservability;

import java.time.Instant;

public record ActivityAlertView(
        String  alertId,
        String  severity,
        String  eventType,
        String  toolName,
        String  reasonCode,
        Integer durationMs,
        Instant occurredAt,
        String  instanceId,
        String  operationId,
        String  agentSessionId,
        String  agentProvider
) {}
