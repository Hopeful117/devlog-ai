package com.hopeful117.devlogai.ai.engine.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AnalysisSynthesisResult(
        @NotBlank String title,
        @NotNull List<@Valid SynthesisSection> sections,
        @NotNull ArchitectureDeltaConclusion deltaConclusion,
        @NotNull List<@NotBlank String> groundingReferences
) {
    public enum ArchitectureDeltaConclusion {
        NO_MATERIAL_DELTA,
        DELTAS_PROPOSED,
        INSUFFICIENT_EVIDENCE
    }

    public record SynthesisSection(
            @NotBlank String name,
            @NotBlank String content
    ) {}
}
