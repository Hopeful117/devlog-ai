package com.hopeful117.devlogai.retrieval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "devlog.retrieval.backfill")
public record BackfillProperties(
        boolean enabled,
        int batchSize
) {
}
