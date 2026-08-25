package com.hopeful117.devlogai.repositoryobservation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Automatic repository HEAD change detection (ADR-062, story 0092).
 * Detection only: observations feed the freshness checkpoints — never
 * synchronization, understanding or AI.
 */
@ConfigurationProperties(prefix = "devlog.repository-observation")
public record RepositoryObservationProperties(
        boolean enabled,
        Duration interval,
        Duration initialDelay
) {
}
