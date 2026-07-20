package com.hopeful117.devlogai.knowledge.service;

import com.hopeful117.devlogai.knowledge.dto.request.CreateKnowledgeEventRequest;
import com.hopeful117.devlogai.knowledge.dto.response.KnowledgeEventResponse;

import java.util.List;
import java.util.UUID;

public interface KnowledgeEventService {
    KnowledgeEventResponse create(CreateKnowledgeEventRequest request);

    List<KnowledgeEventResponse> getByProject(UUID projectId);

    KnowledgeEventResponse getById(UUID id);
}
