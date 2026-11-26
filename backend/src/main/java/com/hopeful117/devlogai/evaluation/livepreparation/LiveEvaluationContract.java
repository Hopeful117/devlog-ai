package com.hopeful117.devlogai.evaluation.livepreparation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One Core-approved bounded live evaluation contract (Story 0239).
 *
 * <p>A contract fixes every identity a live evaluation may use. Callers cannot
 * submit a free-form manifest: a preparation request is accepted only when it
 * matches a registered contract exactly. A preparation covers one case batch
 * (all conditions x all repetitions of one case).
 *
 * @param oneConsumptionPerCase when true, each case batch of the contract can be consumed at most once
 * @param maxCampaignObservations campaign-wide ceiling on consumed observations, or null for none
 * @param maxCampaignWallClock campaign-wide ceiling on planned wall clock, or null for none
 */
public record LiveEvaluationContract(
        String contractId,
        String benchmarkId,
        String benchmarkVersion,
        String manifestId,
        String manifestVersion,
        String manifestDigest,
        String sourceRevision,
        String provider,
        String model,
        String pricingVersion,
        String pricingDigest,
        Map<String, Object> operationalLimits,
        Map<String, Object> runtimeIdentities,
        List<String> conditions,
        Map<String, String> caseOracleDigests,
        int repetitions,
        boolean oneConsumptionPerCase,
        Integer maxCampaignObservations,
        Duration maxCampaignWallClock
) {
    public LiveEvaluationContract {
        Objects.requireNonNull(contractId, "contractId");
        Objects.requireNonNull(manifestDigest, "manifestDigest");
        operationalLimits = Map.copyOf(operationalLimits);
        runtimeIdentities = Map.copyOf(runtimeIdentities);
        conditions = List.copyOf(conditions);
        caseOracleDigests = Collections.unmodifiableMap(new LinkedHashMap<>(caseOracleDigests));
        if (conditions.isEmpty() || caseOracleDigests.isEmpty() || repetitions < 1) {
            throw new IllegalArgumentException("live evaluation contract must define conditions, cases and repetitions");
        }
        if (!(operationalLimits.get("maxWallClockSeconds") instanceof Integer seconds) || seconds < 1) {
            throw new IllegalArgumentException("live evaluation contract requires a positive maxWallClockSeconds");
        }
    }

    public boolean hasCase(String caseId) {
        return caseOracleDigests.containsKey(caseId);
    }

    /** Deterministic observation ids of one case batch: {@code {caseId}-{condition}-r{n}}, repetition-major. */
    public List<String> observationIds(String caseId) {
        List<String> ids = new ArrayList<>();
        for (int repetition = 1; repetition <= repetitions; repetition++) {
            for (String condition : conditions) {
                ids.add(caseId + "-" + condition.replace('_', '-') + "-r" + repetition);
            }
        }
        return List.copyOf(ids);
    }

    public int batchObservationCount() {
        return conditions.size() * repetitions;
    }

    /** Planned wall clock of one case batch: observations x the per-observation limit. */
    public Duration batchWallClock() {
        return Duration.ofSeconds((long) batchObservationCount() * (Integer) operationalLimits.get("maxWallClockSeconds"));
    }

    public boolean hasCampaignCeiling() {
        return oneConsumptionPerCase || maxCampaignObservations != null || maxCampaignWallClock != null;
    }

    /** Authorization label of the accepted oracle for one case, e.g. {@code Q1_ORACLE_ACCEPTED}. */
    public static String oracleAcceptedLabel(String caseId) {
        return caseId + "_ORACLE_ACCEPTED";
    }
}
