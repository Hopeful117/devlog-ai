package com.hopeful117.devlogai.evaluation.livepreparation;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveEvaluationContractTest {

    static LiveEvaluationContract campaign() {
        return new LiveEvaluationContract(
                "devlog-ai-abc-campaign", "devlog-ai-abc", "1.0.0",
                "devlog-ai-abc-campaign", "1.0.0", "c".repeat(64),
                LiveEvaluationContractRegistry.Q1_FIRST_TEST.sourceRevision(),
                "openai_chatgpt", "gpt-5.6-luna",
                LiveEvaluationContractRegistry.Q1_FIRST_TEST.pricingVersion(),
                LiveEvaluationContractRegistry.Q1_FIRST_TEST.pricingDigest(),
                LiveEvaluationContractRegistry.ABC_OPERATIONAL_LIMITS,
                LiveEvaluationContractRegistry.ABC_RUNTIME_IDENTITIES,
                LiveEvaluationContractRegistry.ABC_CONDITIONS,
                LiveEvaluationContractRegistry.orderedCases(
                        "Q1", "1".repeat(64), "Q2", "2".repeat(64), "Q3", "3".repeat(64), "Q4", "4".repeat(64),
                        "Q5", "5".repeat(64), "Q6", "6".repeat(64), "Q7", "7".repeat(64), "Q8", "8".repeat(64)),
                3, true,
                LiveEvaluationContractRegistry.CAMPAIGN_MAX_OBSERVATIONS,
                LiveEvaluationContractRegistry.CAMPAIGN_MAX_WALL_CLOCK);
    }

    @Test
    void q1ContractKeepsTheHistoricalObservationSet() {
        LiveEvaluationContract q1 = LiveEvaluationContractRegistry.Q1_FIRST_TEST;

        assertEquals(List.of("Q1-A-DEVLOG-CURRENT-r1", "Q1-B-DIRECT-INVESTIGATOR-r1",
                "Q1-C-DEVLOG-ENGINEERING-ANALYST-r1"), q1.observationIds("Q1"));
        assertFalse(q1.hasCampaignCeiling());
        assertEquals("Q1_ORACLE_ACCEPTED", LiveEvaluationContract.oracleAcceptedLabel("Q1"));
    }

    @Test
    void campaignBatchIsAllConditionsTimesRepetitionsOfOneCase() {
        LiveEvaluationContract contract = campaign();

        List<String> ids = contract.observationIds("Q2");
        assertEquals(9, ids.size());
        assertEquals("Q2-A-DEVLOG-CURRENT-r1", ids.getFirst());
        assertEquals("Q2-C-DEVLOG-ENGINEERING-ANALYST-r3", ids.getLast());
        assertEquals(Duration.ofMinutes(18), contract.batchWallClock());
        assertEquals(8 * contract.batchObservationCount(), contract.maxCampaignObservations());
        assertTrue(contract.batchWallClock().multipliedBy(8).compareTo(contract.maxCampaignWallClock()) <= 0);
    }

    @Test
    void registryMatchesOnlyTheExactManifestIdentity() {
        LiveEvaluationContractRegistry registry = new LiveEvaluationContractRegistry(
                List.of(LiveEvaluationContractRegistry.Q1_FIRST_TEST, campaign()));

        assertTrue(registry.find("devlog-ai-abc-campaign", "1.0.0", "c".repeat(64)).isPresent());
        assertTrue(registry.find("devlog-ai-abc-campaign", "1.0.1", "c".repeat(64)).isEmpty());
        assertTrue(registry.find("devlog-ai-abc-campaign", "1.0.0", "d".repeat(64)).isEmpty());
        assertEquals("devlog-ai-abc-first-test", LiveEvaluationContractRegistry.approved().fallback().contractId());
    }

    @Test
    void registryRejectsDuplicateManifestDigests() {
        assertThrows(IllegalArgumentException.class, () -> new LiveEvaluationContractRegistry(
                List.of(LiveEvaluationContractRegistry.Q1_FIRST_TEST, LiveEvaluationContractRegistry.Q1_FIRST_TEST)));
    }
}
