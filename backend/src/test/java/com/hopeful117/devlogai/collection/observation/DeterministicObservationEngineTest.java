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
        assertThrows(IllegalArgumentException.class,
                () -> engine.derive(List.of(transientFact, fact(FactType.DOCKER_COMPOSE_PRESENT))));
    }

    private Fact fact(FactType type) {
        return Fact.builder().id(UUID.randomUUID()).type(type).build();
    }
}
