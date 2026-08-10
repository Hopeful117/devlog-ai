package com.hopeful117.devlogai.knowledge.relation.service;

import com.hopeful117.devlogai.knowledge.relation.dto.request.CreateKnowledgeRelationRequest;
import com.hopeful117.devlogai.knowledge.relation.dto.response.KnowledgeRelationResponse;
import com.hopeful117.devlogai.knowledge.relation.entity.EntityType;

import java.util.List;
import java.util.UUID;

public interface KnowledgeRelationService {

    KnowledgeRelationResponse create(CreateKnowledgeRelationRequest request);

    KnowledgeRelationResponse getById(UUID id);

    List<KnowledgeRelationResponse> getByProject(UUID projectId);

    List<KnowledgeRelationResponse> getBySource(EntityType sourceEntityType, UUID sourceEntityId);

    List<KnowledgeRelationResponse> getByTarget(EntityType targetEntityType, UUID targetEntityId);

    void delete(UUID id);
}
