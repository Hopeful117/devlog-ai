package com.hopeful117.devlogai.projectunderstanding.dto;

import com.hopeful117.devlogai.intent.model.UserGuidance;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProjectUnderstandingRequest(
        @NotNull UUID sourceId,
        @Size(max = 255) String targetRevision,
        @Valid UserGuidance userGuidance
) { }
