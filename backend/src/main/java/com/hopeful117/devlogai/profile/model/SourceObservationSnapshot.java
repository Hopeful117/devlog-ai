package com.hopeful117.devlogai.profile.model;

import com.hopeful117.devlogai.observation.entity.ObservationType;
import java.util.UUID;

public record SourceObservationSnapshot(UUID id, ObservationType type, String ruleId, String ruleVersion) { }
