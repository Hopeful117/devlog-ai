package com.hopeful117.devlogai.deliverable.service;

import com.hopeful117.devlogai.deliverable.dto.CreateDeliverableRequest;
import com.hopeful117.devlogai.deliverable.dto.DeliverableResponse;

import java.util.List;
import java.util.UUID;

public interface DeliverableService {
    DeliverableResponse generate(CreateDeliverableRequest request);
    DeliverableResponse getById(UUID id);
    List<DeliverableResponse> getByProject(UUID projectId);
    List<DeliverableResponse> getByAnalysis(UUID analysisId);
}
