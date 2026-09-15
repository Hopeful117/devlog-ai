package com.hopeful117.devlogai.ai.engine.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AnalysisSynthesisResult(
        @NotBlank String title,
        @NotNull List<@Valid SynthesisSection> sections,
        @NotNull ArchitectureDeltaConclusion deltaConclusion,
        List<@NotBlank String> groundingReferences,
        List<@Valid ProviderAiReference> groundingRefs
) {
    public AnalysisSynthesisResult {
        groundingReferences = groundingReferences == null ? List.of() : List.copyOf(groundingReferences);
        groundingRefs = groundingRefs == null ? null : List.copyOf(groundingRefs);
    }

    public AnalysisSynthesisResult(String title, List<SynthesisSection> sections,
            ArchitectureDeltaConclusion deltaConclusion, List<String> groundingReferences) {
        this(title, sections, deltaConclusion, groundingReferences, null);
    }
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
