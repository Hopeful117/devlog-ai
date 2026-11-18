package com.hopeful117.devlogai.retrieval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "devlog.retrieval.embedding")
public record EmbeddingClientProperties(
        Duration timeout,
        String defaultModel
) {
}
