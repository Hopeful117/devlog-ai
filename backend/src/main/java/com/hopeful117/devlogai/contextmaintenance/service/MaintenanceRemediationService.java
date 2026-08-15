package com.hopeful117.devlogai.contextmaintenance.service;

import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingResponse;

import java.util.UUID;

public interface MaintenanceRemediationService {

    MaintenanceFindingResponse refreshProjection(
            UUID projectId,
            UUID findingId,
            UUID actedBy,
            String comment
    );

    MaintenanceFindingResponse archiveStaleHumanContext(
            UUID projectId,
            UUID findingId,
            UUID actedBy,
            String comment
    );

    MaintenanceFindingResponse refreshMissingProjection(
            UUID projectId,
            UUID findingId,
            UUID actedBy,
            String comment
    );

    MaintenanceFindingResponse refreshProjectUnderstanding(
            UUID projectId,
            UUID findingId,
            UUID actedBy,
            String comment
    );
}
