package com.hopeful117.devlogai.repositoryobservation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kill-switch contract (mission §36): when automatic observation is disabled,
 * no scheduling infrastructure may exist in the context at all.
 */
class RepositoryObservationConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RepositoryObservationConfiguration.class);

    @Test
    void enablesSchedulingOnlyWhenObservationIsEnabled() {
        runner.withPropertyValues("devlog.repository-observation.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    void introducesNoSchedulingWhenDisabled() {
        runner.withPropertyValues("devlog.repository-observation.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    void introducesNoSchedulingWhenUnconfigured() {
        runner.run(context -> assertThat(context)
                .doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }
}
