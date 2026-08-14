package com.hopeful117.devlogai.contextmaintenance.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MaintenanceFindingActionRequest(
        @NotNull
        UUID actedBy,

        @Size(max = 2000)
        String comment
) {
}
