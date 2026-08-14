package com.hopeful117.devlogai.contextmaintenance.service;

import com.hopeful117.devlogai.contextmaintenance.dto.request.CreateMaintenanceFindingRequest;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingResponse;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingStatus;

import java.util.List;
import java.util.UUID;

public interface MaintenanceFindingService {

    MaintenanceFindingResponse create(UUID projectId, CreateMaintenanceFindingRequest request);

    List<MaintenanceFindingResponse> getByProject(UUID projectId);

    MaintenanceFindingResponse updateStatus(UUID projectId, UUID findingId, MaintenanceFindingStatus status);
}
