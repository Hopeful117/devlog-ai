package com.hopeful117.devlogai.repositoryobservation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables automatic repository HEAD change detection (ADR-062). Kill-switch:
 * {@code devlog.repository-observation.enabled=false} removes the scheduled
 * detector entirely. Single-instance deployment is assumed; fixedDelay
 * prevents overlapping cycles within the process.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "devlog.repository-observation",
        name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(RepositoryObservationProperties.class)
public class RepositoryObservationConfiguration {
}
