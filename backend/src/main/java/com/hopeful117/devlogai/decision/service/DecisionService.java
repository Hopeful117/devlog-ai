package com.hopeful117.devlogai.decision.service;


import com.hopeful117.devlogai.decision.dto.request.CreateDecisionRequest;
import com.hopeful117.devlogai.decision.dto.request.UpdateDecisionRequest;
import com.hopeful117.devlogai.decision.dto.response.DecisionResponse;

import java.util.List;
import java.util.UUID;

public interface DecisionService {
    DecisionResponse create(CreateDecisionRequest request);

    DecisionResponse update(UUID id, UpdateDecisionRequest request);

    void delete(UUID id);

    List<DecisionResponse> getByProject(UUID projectId);

    DecisionResponse getById(UUID id);
}
