package com.hopeful117.devlogai.contextmaintenance.dto.request;

import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceContextSurface;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingIssueType;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingSeverity;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceSuggestedActionCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMaintenanceFindingRequest(
        @NotNull MaintenanceContextSurface contextSurface,
        @NotNull MaintenanceFindingIssueType issueType,
        @NotNull MaintenanceFindingSeverity severity,
        @NotNull MaintenanceSuggestedActionCategory suggestedAction,
        boolean humanReviewRequired,
        @NotBlank @Size(max = 255) String summary,
        @Size(max = 20000) String details
) {
}
