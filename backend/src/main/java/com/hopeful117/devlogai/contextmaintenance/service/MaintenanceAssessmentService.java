package com.hopeful117.devlogai.contextmaintenance.service;

import com.hopeful117.devlogai.contextmaintenance.dto.request.CreateMaintenanceAssessmentRequest;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceAssessmentResponse;

import java.util.List;
import java.util.UUID;

public interface MaintenanceAssessmentService {

    MaintenanceAssessmentResponse create(
            UUID projectId,
            CreateMaintenanceAssessmentRequest request
    );

    List<MaintenanceAssessmentResponse> getByProject(UUID projectId);

    List<MaintenanceAssessmentResponse> getByFinding(
            UUID projectId,
            UUID findingId
    );
}
