package com.hopeful117.devlogai.evaluation.livepreparation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record LiveEvaluationPreparationRequest(
        @NotBlank String benchmarkId,
        @NotBlank String benchmarkVersion,
        @NotBlank String caseId,
        @NotBlank String manifestId,
        @NotBlank String manifestVersion,
        @NotBlank String manifestDigest,
        @NotBlank String oracleStatus,
        @NotBlank String oracleDigest,
        @NotBlank String sourceRevision,
        @NotBlank String runtimeRevision,
        @NotNull Map<String, Object> runtimeAttestation,
        @NotNull Map<String, Object> runtimeIdentities,
        @NotNull Map<String, Object> operationalLimits,
        @NotBlank String provider,
        @NotBlank String model,
        @NotNull Map<String, Object> pricingContract,
        @NotBlank String artifactNamespace,
        @NotNull List<String> observationIds
) {}
