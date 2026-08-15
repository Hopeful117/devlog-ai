package com.hopeful117.devlogai.insight.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "devlog.insight.similarity")
public class InsightSimilarityProperties {
    private double exactThreshold = 1.0;
    private double duplicateThreshold = 0.85;
    private double overlapThreshold = 0.45;
}
