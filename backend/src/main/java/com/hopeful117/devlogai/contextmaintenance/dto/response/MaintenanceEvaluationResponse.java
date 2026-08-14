package com.hopeful117.devlogai.contextmaintenance.dto.response;

import java.util.List;
import java.util.UUID;

public record MaintenanceEvaluationResponse(
        String version,
        UUID projectId,
        int createdCount,
        int skippedCount,
        List<MaintenanceFindingResponse> createdFindings
) {
    public static final String PROJECTION_VERSION = "maintenance-evaluation-v1";

    public MaintenanceEvaluationResponse {
        createdFindings = List.copyOf(createdFindings);
    }
}
