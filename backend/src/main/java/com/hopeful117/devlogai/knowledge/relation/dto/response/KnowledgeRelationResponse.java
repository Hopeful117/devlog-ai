package com.hopeful117.devlogai.knowledge.relation.dto.response;

import com.hopeful117.devlogai.knowledge.relation.entity.EntityType;
import com.hopeful117.devlogai.knowledge.relation.entity.KnowledgeRelationType;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeRelationResponse(
        UUID id,
        UUID projectId,
        EntityType sourceEntityType,
        UUID sourceEntityId,
        EntityType targetEntityType,
        UUID targetEntityId,
        KnowledgeRelationType relationType,
        String description,
        Instant createdAt
) {
}
