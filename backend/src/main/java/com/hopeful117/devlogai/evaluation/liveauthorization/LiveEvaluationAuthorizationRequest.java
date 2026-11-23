package com.hopeful117.devlogai.evaluation.liveauthorization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LiveEvaluationAuthorizationRequest(
        @NotNull UUID planId,
        @NotBlank String planDigest
) {}
