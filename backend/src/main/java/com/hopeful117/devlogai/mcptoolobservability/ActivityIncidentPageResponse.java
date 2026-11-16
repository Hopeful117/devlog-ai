package com.hopeful117.devlogai.mcptoolobservability;

import java.time.Instant;
import java.util.List;

public record ActivityIncidentPageResponse(
        List<ActivityIncidentView> content,
        long    totalIncidents,
        long    errorCount,
        long    warnCount,
        int     windowMinutes,
        Instant windowStart,
        int     page,
        int     size
) {}
