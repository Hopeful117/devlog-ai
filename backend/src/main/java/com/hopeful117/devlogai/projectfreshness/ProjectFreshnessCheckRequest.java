package com.hopeful117.devlogai.projectfreshness;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ProjectFreshnessCheckRequest(@NotNull UUID sourceId) { }
