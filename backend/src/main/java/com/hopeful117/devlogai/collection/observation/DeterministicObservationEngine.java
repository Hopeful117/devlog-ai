package com.hopeful117.devlogai.collection.observation;

import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.observation.entity.ObservationType;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
                    FactType.BUILD_MODULE_DECLARED),
            new DockerServiceWiringObservationRule("DOCKER_SERVICE_WIRING", VERSION,
                    "The project defines multiple Docker Compose services with an explicit service dependency or runtime reference.",
                    ObservationType.ARCHITECTURE_MODULARIZATION)
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

    private record DockerServiceWiringObservationRule(
            String id, String version, String content, ObservationType observationType)
            implements ObservationRule {
        private static final Set<FactType> RELATIONSHIP_TYPES = Set.of(
                FactType.DOCKER_SERVICE_DEPENDS_ON, FactType.DOCKER_SERVICE_ENV_REFERENCE);

        @Override
        public Optional<DerivedObservation> evaluate(List<Fact> facts) {
            Map<String, Fact> declarations = facts.stream()
                    .filter(fact -> fact.getType() == FactType.DOCKER_SERVICE_DECLARED)
                    .filter(fact -> fact.getContent() != null
                            && fact.getContent().startsWith("service="))
                    .collect(java.util.stream.Collectors.toMap(
                            fact -> fact.getContent().substring("service=".length()),
                            fact -> fact,
                            (first, ignored) -> first,
                            java.util.LinkedHashMap::new));
            Optional<Fact> relationship = facts.stream()
                    .filter(fact -> RELATIONSHIP_TYPES.contains(fact.getType()))
                    .filter(fact -> endpoints(fact.getContent()).map(pair ->
                            !pair.getFirst().equals(pair.getLast())
                                    && declarations.containsKey(pair.getFirst())
                                    && declarations.containsKey(pair.getLast())).orElse(false))
                    .sorted(Comparator.comparing((Fact fact) -> fact.getType().name())
                            .thenComparing(Fact::getContent)
                            .thenComparing(Fact::getId, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .findFirst();
            if (relationship.isEmpty()) return Optional.empty();
            List<String> endpoints = endpoints(relationship.get().getContent()).orElseThrow();
            List<Fact> support = List.of(
                    declarations.get(endpoints.getFirst()),
                    declarations.get(endpoints.getLast()),
                    relationship.get());
            if (support.stream().anyMatch(fact -> fact.getId() == null)) {
                throw new IllegalArgumentException("Observation rules require persisted Facts");
            }

            Set<UUID> supportingIds = support.stream()
                    .map(Fact::getId)
                    .sorted()
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            return Optional.of(new DerivedObservation(
                    id, version, observationType, content, supportingIds));
        }

        private static Optional<List<String>> endpoints(String content) {
            if (content == null) return Optional.empty();
            String from = null;
            String to = null;
            for (String token : content.split(",")) {
                if (token.startsWith("from=")) from = token.substring("from=".length());
                if (token.startsWith("to=")) to = token.substring("to=".length());
            }
            return from == null || to == null ? Optional.empty() : Optional.of(List.of(from, to));
        }
    }
}
