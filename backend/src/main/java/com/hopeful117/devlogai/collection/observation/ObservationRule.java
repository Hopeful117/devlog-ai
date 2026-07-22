package com.hopeful117.devlogai.collection.observation;

import com.hopeful117.devlogai.fact.entity.Fact;

import java.util.List;
import java.util.Optional;

/** A versioned, deterministic rule deriving at most one observation from Facts. */
public interface ObservationRule {
    String id();
    String version();
    Optional<DerivedObservation> evaluate(List<Fact> facts);
}
