package com.hopeful117.devlogai.contextmaintenance.dto.response;

import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceContextSurface;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingIssueType;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingSeverity;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingStatus;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceSuggestedActionCategory;

import java.time.Instant;
import java.util.UUID;

public record MaintenanceFindingResponse(
        UUID id,
        UUID projectId,
        MaintenanceContextSurface contextSurface,
        MaintenanceFindingIssueType issueType,
        MaintenanceFindingSeverity severity,
        MaintenanceFindingStatus status,
        MaintenanceSuggestedActionCategory suggestedAction,
        boolean humanReviewRequired,
        String summary,
        String details,
        Instant createdAt,
        Instant updatedAt
) {
}
