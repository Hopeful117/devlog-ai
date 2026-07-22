package com.hopeful117.devlogai.collection.observation;

import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.observation.entity.ObservationType;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class DeterministicObservationEngine implements ObservationEngine {

    private static final String VERSION = "1";
    private static final List<ObservationRule> RULES = List.of(
            rule("CONTAINERIZED_PROJECT", ObservationType.CONTAINERIZED_PROJECT,
                    "The project defines both a Docker image and a Docker Compose environment.",
                    FactType.DOCKERFILE_PRESENT, FactType.DOCKER_COMPOSE_PRESENT),
            rule("SPRING_BOOT_REST_APPLICATION", ObservationType.SPRING_BOOT_REST_APPLICATION,
                    "The project exposes REST controllers through a Spring Boot web application.",
                    FactType.SPRING_BOOT_DETECTED, FactType.REST_CONTROLLER_DECLARED),
            rule("ARCHITECTURE_DECISION_DOCUMENTATION", ObservationType.ARCHITECTURE_DOCUMENTATION_PRESENT,
                    "The project contains a dedicated set of architecture decision records.",
                    FactType.ADR_DIRECTORY_PRESENT, FactType.ADR_DOCUMENT_PRESENT),
            rule("AUTOMATED_TEST_SUITE", ObservationType.AUTOMATED_TEST_SUITE_PRESENT,
                    "The project contains an automated test source tree and test files.",
                    FactType.TEST_SOURCE_DIRECTORY_PRESENT, FactType.TEST_FILE_PRESENT),
            rule("INTEGRATION_TEST_SUITE", ObservationType.INTEGRATION_TEST_SUITE_PRESENT,
                    "The project contains both automated tests and explicitly identified integration tests.",
                    FactType.TEST_FILE_PRESENT, FactType.INTEGRATION_TEST_FILE_PRESENT),
            rule("MULTI_MODULE_BUILD", ObservationType.MULTI_MODULE_BUILD,
                    "The project uses a build system that declares multiple build modules.",
                    FactType.BUILD_SYSTEM_DETECTED, FactType.MULTI_MODULE_STRUCTURE_PRESENT,
                    FactType.BUILD_MODULE_DECLARED)
    );

    @Override
    public List<DerivedObservation> derive(List<Fact> facts) {
        List<Fact> snapshot = List.copyOf(facts);
        return RULES.stream()
                .map(rule -> rule.evaluate(snapshot))
                .flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparing(DerivedObservation::ruleId))
                .toList();
    }

    private static ObservationRule rule(String id, ObservationType type, String content,
                                        FactType first, FactType... remaining) {
        return new RequiredFactTypesObservationRule(id, VERSION, type, content, first, remaining);
    }
}
