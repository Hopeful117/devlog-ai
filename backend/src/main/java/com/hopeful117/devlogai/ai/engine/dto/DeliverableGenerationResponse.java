package com.hopeful117.devlogai.ai.engine.dto;

public record DeliverableGenerationResponse(
        String title, String content, String promptVersion, String promptDigest,
        String provider, String modelIdentifier
) { }
