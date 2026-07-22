package com.hopeful117.devlogai.collection.observation;

import com.hopeful117.devlogai.observation.entity.ObservationType;

import java.util.Set;
import java.util.UUID;

public record DerivedObservation(
        String ruleId,
        String ruleVersion,
        ObservationType type,
        String content,
        Set<UUID> supportingFactIds
) {
    public DerivedObservation {
        if (ruleId == null || ruleId.isBlank()) throw new IllegalArgumentException("ruleId is required");
        if (ruleVersion == null || ruleVersion.isBlank()) throw new IllegalArgumentException("ruleVersion is required");
        supportingFactIds = Set.copyOf(supportingFactIds);
    }
}
