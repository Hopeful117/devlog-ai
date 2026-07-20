package com.hopeful117.devlogai.knowledge.dto.response;

import com.hopeful117.devlogai.knowledge.entity.KnowledgeEventType;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeEventResponse(

        UUID id,

        UUID projectId,

        KnowledgeEventType type,

        String title,

        String description,

        Instant createdAt,

        Instant updatedAt


) {
}
