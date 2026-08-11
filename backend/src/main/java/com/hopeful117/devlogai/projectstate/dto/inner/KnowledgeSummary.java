package com.hopeful117.devlogai.projectstate.dto.inner;

import com.hopeful117.devlogai.knowledge.entity.KnowledgeEventType;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeSummary(
        UUID id,
        KnowledgeEventType type,
        String title,
        Instant createdAt
) {
}