package com.hopeful117.devlogai.ai.engine.dto;

import com.hopeful117.devlogai.ai.reference.AiReferenceScope;
import com.hopeful117.devlogai.ai.reference.AiReferenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Provider transport value; Core-only binding metadata never crosses this boundary. */
public record ProviderAiReference(
        @NotNull AiReferenceType type,
        @NotBlank String ref,
        @NotNull AiReferenceScope scope
) { }
