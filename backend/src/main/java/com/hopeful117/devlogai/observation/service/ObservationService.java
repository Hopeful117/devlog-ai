package com.hopeful117.devlogai.observation.service;

import com.hopeful117.devlogai.observation.dto.request.CreateObservationRequest;
import com.hopeful117.devlogai.observation.dto.response.ObservationResponse;

import java.util.List;
import java.util.UUID;

public interface ObservationService {

    ObservationResponse create(CreateObservationRequest request);

    ObservationResponse getById(UUID id);

    List<ObservationResponse> getByAnalysis(UUID analysisId);
}
