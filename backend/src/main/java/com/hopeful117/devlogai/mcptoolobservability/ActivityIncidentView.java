package com.hopeful117.devlogai.mcptoolobservability;

import java.time.Instant;
import java.util.List;

public record ActivityIncidentView(
        String       incidentId,
        String       severity,
        String       toolName,
        String       reasonCode,
        long         occurrenceCount,
        Instant      firstOccurredAt,
        Instant      lastOccurredAt,
        List<String> recentAlertIds
) {}
