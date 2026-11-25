package com.hopeful117.devlogai.evaluation.livepreparation;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Code-defined registry of approved live evaluation contracts (Story 0239, decision D1).
 *
 * <p>Entries change only through a reviewed code change; nothing at runtime can
 * add or alter a contract. The Q1 contract keeps its historical behaviour
 * (repeatable runs, no campaign ceiling). The Story 0223 8 x 3 x 3 campaign
 * ({@link #ABC_CAMPAIGN}) allows one consumption per case batch within
 * {@link #CAMPAIGN_MAX_OBSERVATIONS} and {@link #CAMPAIGN_MAX_WALL_CLOCK}.
 */
@Component
public class LiveEvaluationContractRegistry {

    /** Story 0239 D3: at most 72 consumed observations per campaign. */
    public static final int CAMPAIGN_MAX_OBSERVATIONS = 72;
    /** Story 0239 D3: at most 3 hours of planned wall clock per campaign. */
    public static final Duration CAMPAIGN_MAX_WALL_CLOCK = Duration.ofHours(3);

    static final Map<String, Object> ABC_OPERATIONAL_LIMITS = Map.of(
            "maxModelTurns", 8,
            "maxProviderCalls", 8,
            "maxToolOrActionOperations", 8,
            "maxReadBytesPerOperation", 64000,
            "maxReadTokensPerOperation", 8000,
            "maxWallClockSeconds", 120,
            "maxOutputTokensPerResponse", 32768);
    static final Map<String, Object> ABC_RUNTIME_IDENTITIES = Map.of(
            "A", Map.of("runtime", "java-core-story-context-one-shot", "entrypoint",
                    "backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java",
                    "promptVersion", "story-context-agent-protocol/v1", "answerContract", "story-context-analysis-result-v1"),
            "B", Map.of("runtime", "comparative-v4-direct-investigator", "entrypoint",
                    "ai-engine/evaluations/comparative_baseline_v4/runtime.py", "configuration",
                    "docs/evaluation/devlog-ai-q1-b-direct-config-v1.json", "promptVersion",
                    "comparative-v4-common-answer-1.0.0", "answerContract", "comparative-v4-common-answer-1.0.0"),
            "C", Map.of("runtime", "python-core-bounded-investigation", "entrypoint",
                    "ai-engine/app/services/story_context_analysis_generation_service.py", "promptVersion",
                    "story-context-agent-protocol/v2", "answerContract", "story-context-analysis-result-v1"));
    static final List<String> ABC_CONDITIONS = List.of(
            "A_DEVLOG_CURRENT", "B_DIRECT_INVESTIGATOR", "C_DEVLOG_ENGINEERING_ANALYST");

    static final LiveEvaluationContract Q1_FIRST_TEST = new LiveEvaluationContract(
            "devlog-ai-abc-first-test",
            "devlog-ai-abc", "1.0.0",
            "devlog-ai-abc-first-test", "1.1.0",
            "eaca1774647e4d9e15f0f6b77a98aa733a787895f0cde1f282933de3405dc598",
            "0673d1d32433cdaff054d5f1defd1bedd01c0039",
            "openai_chatgpt", "gpt-5.6-luna",
            "q1-chatgpt-oauth-pricing-v1",
            "0b48079816061d0660476bf2bb5d08572ff5f3378be7d5f3ba77d4013c229fbd",
            ABC_OPERATIONAL_LIMITS, ABC_RUNTIME_IDENTITIES, ABC_CONDITIONS,
            orderedCases("Q1", "2127d4ce224b1f39f5d8f113bb0b24b3dab7e9443fcb101600a774341c8d6d4e"),
            1, false, null, null);

    /**
     * Story 0223 campaign, 8 cases x 3 conditions x 3 repetitions, executed as
     * eight case batches (Story 0237 AC-6). Manifest
     * {@code docs/evaluation/devlog-ai-abc-campaign-manifest-v1.json}; oracle
     * digests are the frozen canonical digests of
     * {@code docs/evaluation/devlog-ai-abc-oracle-proposals-v1.json}.
     */
    static final LiveEvaluationContract ABC_CAMPAIGN = new LiveEvaluationContract(
            "devlog-ai-abc-campaign",
            "devlog-ai-abc", "1.0.0",
            "devlog-ai-abc-campaign", "1.0.0",
            "8bff8a655ad3daa907e67b4dcc44e999eaece40f9edef0729b9db607f7cc6dfe",
            "0673d1d32433cdaff054d5f1defd1bedd01c0039",
            "openai_chatgpt", "gpt-5.6-luna",
            "q1-chatgpt-oauth-pricing-v1",
            "0b48079816061d0660476bf2bb5d08572ff5f3378be7d5f3ba77d4013c229fbd",
            ABC_OPERATIONAL_LIMITS, ABC_RUNTIME_IDENTITIES, ABC_CONDITIONS,
            orderedCases(
                    "Q1", "2127d4ce224b1f39f5d8f113bb0b24b3dab7e9443fcb101600a774341c8d6d4e",
                    "Q2", "90180f17318ac4c8bf3c735de1f408597ea1a81e1b254913d8f100e68591577d",
                    "Q3", "474f5ba5d787b5dd48cc9b881e9ef1ea7d170e82342f41184b1a1af8cce7d1b0",
                    "Q4", "67493086a247364f641dda2b85fce825b12e3510a7922416b5852cb9a416f769",
                    "Q5", "e4c8cb4e0f7cc24c4adba9d1f38cccd902a7d6dcde6505f471e52a0caffa3813",
                    "Q6", "6a99440c6eb5385ff29d95b3dd2d951c30424001e1885ce03a019d0bb5b65327",
                    "Q7", "41b0acbf7293eab4aa983e7260a601d07222211fb449aeb15024a8c22a7a02cd",
                    "Q8", "3e54bba8b7ddfc14f25aa06a0f6fa400c4055f457539815de4ca3e569c89d3ad"),
            3, true, CAMPAIGN_MAX_OBSERVATIONS, CAMPAIGN_MAX_WALL_CLOCK);

    private final List<LiveEvaluationContract> contracts;

    public LiveEvaluationContractRegistry() {
        this(List.of(Q1_FIRST_TEST, ABC_CAMPAIGN));
    }

    public LiveEvaluationContractRegistry(List<LiveEvaluationContract> contracts) {
        this.contracts = List.copyOf(contracts);
        long distinct = this.contracts.stream().map(LiveEvaluationContract::manifestDigest).distinct().count();
        if (distinct != this.contracts.size()) {
            throw new IllegalArgumentException("live evaluation contracts must have distinct manifest digests");
        }
    }

    public static LiveEvaluationContractRegistry approved() {
        return new LiveEvaluationContractRegistry();
    }

    public Optional<LiveEvaluationContract> find(String manifestId, String manifestVersion, String manifestDigest) {
        return contracts.stream()
                .filter(contract -> contract.manifestId().equals(manifestId)
                        && contract.manifestVersion().equals(manifestVersion)
                        && contract.manifestDigest().equals(manifestDigest))
                .findFirst();
    }

    public Optional<LiveEvaluationContract> findByManifestDigest(String manifestDigest) {
        return contracts.stream().filter(contract -> contract.manifestDigest().equals(manifestDigest)).findFirst();
    }

    /** Contract used to render a blocked plan when the request matches no contract. */
    public LiveEvaluationContract fallback() {
        return contracts.getFirst();
    }

    static Map<String, String> orderedCases(String... caseAndDigest) {
        Map<String, String> cases = new LinkedHashMap<>();
        for (int index = 0; index + 1 < caseAndDigest.length; index += 2) {
            cases.put(caseAndDigest[index], caseAndDigest[index + 1]);
        }
        return cases;
    }
}
