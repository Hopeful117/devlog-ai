package com.hopeful117.devlogai.source.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateSourceActivationRequest(@NotNull Boolean active) {
}
