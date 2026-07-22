package com.hopeful117.devlogai.collection.observation;

import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.observation.entity.ObservationType;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class RequiredFactTypesObservationRule implements ObservationRule {
    private final String id;
    private final String version;
    private final ObservationType observationType;
    private final String content;
    private final Set<FactType> requiredTypes;

    RequiredFactTypesObservationRule(String id, String version, ObservationType observationType,
                                     String content, FactType first, FactType... remaining) {
        this.id = id;
        this.version = version;
        this.observationType = observationType;
        this.content = content;
        this.requiredTypes = EnumSet.of(first, remaining);
    }

    @Override public String id() { return id; }
    @Override public String version() { return version; }

    @Override
    public Optional<DerivedObservation> evaluate(List<Fact> facts) {
        Set<FactType> available = EnumSet.noneOf(FactType.class);
        facts.forEach(fact -> available.add(fact.getType()));
        if (!available.containsAll(requiredTypes)) return Optional.empty();
        if (facts.stream().filter(fact -> requiredTypes.contains(fact.getType()))
                .anyMatch(fact -> fact.getId() == null)) {
            throw new IllegalArgumentException("Observation rules require persisted Facts");
        }

        Set<UUID> supportingIds = facts.stream()
                .filter(fact -> requiredTypes.contains(fact.getType()))
                .map(Fact::getId)
                .sorted()
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return Optional.of(new DerivedObservation(
                id, version, observationType, content, supportingIds
        ));
    }
}
