package com.hopeful117.devlogai.fact.service;

import com.hopeful117.devlogai.fact.dto.request.CreateFactRequest;
import com.hopeful117.devlogai.fact.dto.response.FactResponse;

import java.util.List;
import java.util.UUID;

public interface FactService {

    FactResponse create(CreateFactRequest request);

    FactResponse getById(UUID id);

    List<FactResponse> getByAnalysis(UUID analysisId);
}
