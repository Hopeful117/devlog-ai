package com.hopeful117.devlogai.collection.observation;

import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.observation.entity.ObservationType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicObservationEngineTest {
    private final DeterministicObservationEngine engine = new DeterministicObservationEngine();

    @Test
    void shouldDeriveVersionedTraceableObservationsFromFactsOnly() {
        Fact dockerfile = fact(FactType.DOCKERFILE_PRESENT);
        Fact compose = fact(FactType.DOCKER_COMPOSE_PRESENT);
        Fact unrelated = fact(FactType.README_PRESENT);

        List<DerivedObservation> result = engine.derive(List.of(unrelated, compose, dockerfile));

        assertEquals(1, result.size());
        DerivedObservation observation = result.getFirst();
        assertEquals("CONTAINERIZED_PROJECT", observation.ruleId());
        assertEquals("1", observation.ruleVersion());
        assertEquals(ObservationType.CONTAINERIZED_PROJECT, observation.type());
        assertEquals(Set.of(dockerfile.getId(), compose.getId()), observation.supportingFactIds());
        assertFalse(observation.supportingFactIds().contains(unrelated.getId()));
    }

    @Test
    void shouldRequireEveryConditionAndNeverInferFromAnIsolatedFact() {
        assertTrue(engine.derive(List.of(fact(FactType.DOCKERFILE_PRESENT))).isEmpty());
        assertTrue(engine.derive(List.of(fact(FactType.REST_CONTROLLER_DECLARED))).isEmpty());
    }

    @Test
    void shouldBeReproducibleRegardlessOfFactOrder() {
        List<Fact> facts = new ArrayList<>(List.of(
                fact(FactType.TEST_SOURCE_DIRECTORY_PRESENT),
                fact(FactType.TEST_FILE_PRESENT),
                fact(FactType.TEST_FRAMEWORK_DECLARED),
                fact(FactType.INTEGRATION_TEST_FILE_PRESENT),
                fact(FactType.DOCKERFILE_PRESENT),
                fact(FactType.DOCKER_COMPOSE_PRESENT)
        ));
        List<DerivedObservation> first = engine.derive(facts);
        Collections.reverse(facts);

        assertEquals(first, engine.derive(facts));
        assertEquals(List.of("AUTOMATED_TEST_SUITE", "CONTAINERIZED_PROJECT", "INTEGRATION_TEST_SUITE"),
                first.stream().map(DerivedObservation::ruleId).toList());
    }

    @Test
    void shouldRejectTransientFactsToKeepTraceability() {
        Fact transientFact = Fact.builder().type(FactType.DOCKERFILE_PRESENT).build();
        Fact persistedFact = fact(FactType.DOCKER_COMPOSE_PRESENT);
        List<Fact> facts = List.of(transientFact, persistedFact);
        assertThrows(IllegalArgumentException.class,
                () -> engine.derive(facts));
    }

    @Test
    void shouldDeriveArchitectureModularizationFromDockerServiceWiring() {
        Fact serviceDeclared = service("backend");
        Fact targetServiceDeclared = service("database");
        Fact envReference = relationship(FactType.DOCKER_SERVICE_ENV_REFERENCE,
                "backend", "database");

        List<DerivedObservation> result = engine.derive(
                List.of(serviceDeclared, targetServiceDeclared, envReference));

        assertTrue(result.stream().anyMatch(o ->
                o.type() == ObservationType.ARCHITECTURE_MODULARIZATION
                        && o.ruleId().equals("DOCKER_SERVICE_WIRING")));
    }

    @Test
    void shouldNotDeriveArchitectureModularizationWithoutEnvReference() {
        Fact serviceDeclared = service("backend");
        Fact anotherService = service("database");

        List<DerivedObservation> result = engine.derive(List.of(serviceDeclared, anotherService));

        assertTrue(result.stream().noneMatch(o ->
                o.type() == ObservationType.ARCHITECTURE_MODULARIZATION),
                "Should not derive modularization without env reference");
    }

    @Test
    void shouldNotDeriveArchitectureModularizationWithSingleService() {
        Fact serviceDeclared = service("backend");
        Fact envReference = relationship(FactType.DOCKER_SERVICE_ENV_REFERENCE,
                "backend", "database");

        List<DerivedObservation> result = engine.derive(List.of(serviceDeclared, envReference));

        assertTrue(result.stream().noneMatch(o ->
                o.type() == ObservationType.ARCHITECTURE_MODULARIZATION),
                "Should not derive modularization without multiple service declarations");
    }

    @Test
    void shouldLinkModularizationObservationToSupportingFacts() {
        Fact serviceDeclared = service("backend");
        Fact targetServiceDeclared = service("database");
        Fact envReference = relationship(FactType.DOCKER_SERVICE_ENV_REFERENCE,
                "backend", "database");

        List<DerivedObservation> result = engine.derive(
                List.of(serviceDeclared, targetServiceDeclared, envReference));

        DerivedObservation modularization = result.stream()
                .filter(o -> o.type() == ObservationType.ARCHITECTURE_MODULARIZATION)
                .findFirst().orElseThrow();
        assertTrue(modularization.supportingFactIds().contains(serviceDeclared.getId()));
        assertTrue(modularization.supportingFactIds().contains(targetServiceDeclared.getId()));
        assertTrue(modularization.supportingFactIds().contains(envReference.getId()));
        assertEquals(3, modularization.supportingFactIds().size());
    }

    @Test
    void shouldDeriveModularizationFromDependsOnRelationship() {
        Fact serviceDeclared = service("backend");
        Fact targetServiceDeclared = service("database");
        Fact dependsOn = relationship(FactType.DOCKER_SERVICE_DEPENDS_ON,
                "backend", "database");

        List<DerivedObservation> result = engine.derive(
                List.of(serviceDeclared, targetServiceDeclared, dependsOn));

        DerivedObservation modularization = result.stream()
                .filter(o -> o.type() == ObservationType.ARCHITECTURE_MODULARIZATION)
                .findFirst().orElseThrow();
        assertTrue(modularization.supportingFactIds().contains(serviceDeclared.getId()));
        assertTrue(modularization.supportingFactIds().contains(targetServiceDeclared.getId()));
        assertTrue(modularization.supportingFactIds().contains(dependsOn.getId()));
    }

    @Test
    void shouldRequireTwoDistinctDeclaredServiceIdentities() {
        Fact firstDeclaration = service("backend");
        Fact duplicateDeclaration = service("backend");
        Fact relationship = relationship(FactType.DOCKER_SERVICE_DEPENDS_ON,
                "backend", "database");

        List<DerivedObservation> result = engine.derive(
                List.of(firstDeclaration, duplicateDeclaration, relationship));

        assertTrue(result.stream().noneMatch(observation ->
                observation.type() == ObservationType.ARCHITECTURE_MODULARIZATION));
    }

    @Test
    void shouldUseOnlyOneRelationshipAndItsDeclarationsAsBoundedSupport() {
        Fact backend = service("backend");
        Fact database = service("database");
        Fact cache = service("cache");
        Fact databaseDependency = relationship(FactType.DOCKER_SERVICE_DEPENDS_ON,
                "backend", "database");
        Fact cacheDependency = relationship(FactType.DOCKER_SERVICE_DEPENDS_ON,
                "backend", "cache");

        DerivedObservation observation = engine.derive(List.of(
                        cacheDependency, cache, databaseDependency, database, backend)).stream()
                .filter(value -> value.type() == ObservationType.ARCHITECTURE_MODULARIZATION)
                .findFirst().orElseThrow();

        assertEquals(3, observation.supportingFactIds().size());
        assertTrue(observation.supportingFactIds().contains(backend.getId()));
    }

    private Fact fact(FactType type) {
        return Fact.builder().id(UUID.randomUUID()).type(type).build();
    }

    private Fact service(String name) {
        return Fact.builder().id(UUID.randomUUID())
                .type(FactType.DOCKER_SERVICE_DECLARED)
                .content("service=" + name)
                .build();
    }

    private Fact relationship(FactType type, String from, String to) {
        return Fact.builder().id(UUID.randomUUID())
                .type(type)
                .content("from=" + from + ",to=" + to)
                .build();
    }
}
