package com.hopeful117.devlogai.contextmaintenance.service;

import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceEvaluationResponse;

import java.util.UUID;

public interface MaintenanceEvaluationService {

    MaintenanceEvaluationResponse evaluate(UUID projectId);
}
