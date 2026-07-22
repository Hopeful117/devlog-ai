package com.hopeful117.devlogai.ai.engine.dto;

import com.hopeful117.devlogai.proposal.entity.ProposalType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AiProposalResult(
        @NotNull ProposalType type,
        @NotNull Map<String, Object> payload,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
        @NotNull List<UUID> supportingFactIds,
        @NotNull List<UUID> supportingObservationIds,
        @NotNull List<@NotBlank String> evidenceReferences
) {
}
