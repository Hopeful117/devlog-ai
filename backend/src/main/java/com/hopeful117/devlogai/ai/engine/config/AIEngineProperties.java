package com.hopeful117.devlogai.ai.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai-engine")
public record AIEngineProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
}
