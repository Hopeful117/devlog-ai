package com.hopeful117.devlogai.collection.observation;

import com.hopeful117.devlogai.observation.entity.ObservationType;

import java.util.Set;
import java.util.UUID;

public record DerivedObservation(
        ObservationType type,
        String content,
        Set<UUID> supportingFactIds
) {
    public DerivedObservation {
        supportingFactIds = Set.copyOf(supportingFactIds);
    }
}
