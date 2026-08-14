package com.hopeful117.devlogai.contextmaintenance.dto.response;

import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingActionType;

import java.time.Instant;
import java.util.UUID;

public record MaintenanceFindingActionResponse(
        UUID id,
        MaintenanceFindingActionType actionType,
        UUID actedBy,
        Instant actedAt,
        String comment
) {
}
