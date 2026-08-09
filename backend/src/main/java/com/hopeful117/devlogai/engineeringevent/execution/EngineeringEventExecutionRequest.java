package com.hopeful117.devlogai.engineeringevent.execution;

import com.hopeful117.devlogai.intent.model.UserGuidance;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record EngineeringEventExecutionRequest(
        @NotNull UUID sourceId,
        @NotBlank @Size(max = 64) String targetCommit,
        @Valid UserGuidance userGuidance) { }
