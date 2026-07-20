package com.hopeful117.devlogai.documentation.dto.response;

import com.hopeful117.devlogai.documentation.entity.DocumentationType;

import java.time.Instant;
import java.util.UUID;

public record DocumentationResponse(

        UUID id,

        UUID projectId,

        String title,

        DocumentationType type,

        String content,

        Integer version,

        Instant createdAt,

        Instant updatedAt



) {
}
