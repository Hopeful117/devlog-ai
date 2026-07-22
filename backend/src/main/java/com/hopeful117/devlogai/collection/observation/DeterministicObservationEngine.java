package com.hopeful117.devlogai.collection.observation;

import com.hopeful117.devlogai.fact.entity.Fact;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeterministicObservationEngine implements ObservationEngine {

    @Override
    public List<DerivedObservation> derive(List<Fact> facts) {
        // ADR-023 defines the boundary but no deterministic observation rule yet.
        return List.of();
    }
}
