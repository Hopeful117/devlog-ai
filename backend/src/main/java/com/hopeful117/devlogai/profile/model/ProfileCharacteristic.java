package com.hopeful117.devlogai.profile.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProfileCharacteristic(
        String code, String label, String description, CharacteristicStatus status,
        List<UUID> sourceObservationIds, int evidenceCount, Map<String, Object> metadata
) {
    public ProfileCharacteristic {
        sourceObservationIds = List.copyOf(sourceObservationIds);
        metadata = Map.copyOf(metadata);
        if (sourceObservationIds.isEmpty()) throw new IllegalArgumentException("A profile characteristic requires an Observation");
    }
}
