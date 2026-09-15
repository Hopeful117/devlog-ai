package com.hopeful117.devlogai.ai.engine.dto;

import com.hopeful117.devlogai.proposal.entity.ProposalType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AiProposalResult(
        @NotNull ProposalType type,
        @NotNull Map<String, Object> payload,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
        List<UUID> supportingFactIds,
        List<UUID> supportingObservationIds,
        List<@NotBlank String> evidenceReferences,
        List<@Valid ProviderAiReference> supportingFactRefs,
        List<@Valid ProviderAiReference> supportingObservationRefs,
        List<@Valid ProviderAiReference> evidenceRefs
) {
    public AiProposalResult {
        supportingFactIds = supportingFactIds == null ? List.of() : List.copyOf(supportingFactIds);
        supportingObservationIds = supportingObservationIds == null ? List.of() : List.copyOf(supportingObservationIds);
        evidenceReferences = evidenceReferences == null ? List.of() : List.copyOf(evidenceReferences);
        supportingFactRefs = supportingFactRefs == null ? null : List.copyOf(supportingFactRefs);
        supportingObservationRefs = supportingObservationRefs == null ? null : List.copyOf(supportingObservationRefs);
        evidenceRefs = evidenceRefs == null ? null : List.copyOf(evidenceRefs);
    }

    public AiProposalResult(ProposalType type, Map<String, Object> payload,
            BigDecimal confidence, List<UUID> supportingFactIds,
            List<UUID> supportingObservationIds, List<String> evidenceReferences) {
        this(type, payload, confidence, supportingFactIds, supportingObservationIds,
                evidenceReferences, null, null, null);
    }
}
