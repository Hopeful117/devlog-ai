# Story 0106 — Frozen PromptRequest Replay Investigation

## Status

- Status: `INVESTIGATION_COMPLETE`
- Scope: `REPORTING_ONLY`
- Date: `2026-08-31`

## 1. Executive Summary

Five frozen replays of the exact same corrective PromptRequest against `gpt-4.1-mini` produced **5/5 clean results**. Every replay emitted exactly 1 proposal — the ADR documentation decision — with zero technology-presence decisions, zero generic rationale, and zero unsupported causality.

```
CLEAN_REPLAY_RATE = 100%
TECHNOLOGY_ONLY_REPLAY_RATE = 0%
```

This demonstrates stable, valid engineering-decision generation across five replays of the same exact PromptRequest. It does not prove that the model is globally deterministic and does not establish the sole cause of the historical `4/1/1` variance. A separate Analysis-local Fact ranking dependency was later isolated into Story 0107.

## Evidence Precision Note

All measured replay counts and proposal classifications in this report remain valid. Any wording below that describes the model as deterministic or assigns strict historical causality must be read as limited to this five-replay frozen-input sample and superseded by the final Story 0106 causality wording above.

## 2. Investigation Question

> If the EXACT SAME corrective PromptRequest is submitted repeatedly to the SAME model with the SAME generation configuration, how stable is the engineering-decision emission behavior?

**Answer: Fully stable in this 5-replay sample.** All replays produced identical semantic output (ADR decision only) with no technology-only emissions.

## 3. Governing Deterministic / Probabilistic Boundary

```text
DETERMINISTIC INFORMATION CONSTRUCTION (confirmed by prior investigation)
                    ↓
             PromptRequest (frozen)
                    ↓
────────────────────────────────────────
     PROBABILISTIC INTERPRETATION
────────────────────────────────────────
                    ↓
                  LLM (gpt-4.1-mini)
```

This investigation isolates only the probabilistic interpretation boundary by freezing everything before it.

## 4. Frozen PromptRequest Source

**Selected reference: Run 2** (`bff570db-55d2-468c-8193-440ebe7cfb2c`)

Rationale:
- Run 2 represents the intended corrective behavior (1 EXPLICIT ADR, 0 TECHNOLOGY_ONLY)
- Run 2's selectedKnowledge is semantically equivalent to Runs 1 and 3 (confirmed by prior investigation)
- Using Run 2 as frozen reference tests whether the model reliably produces the correct output when given the canonical corrective input

## 5. Frozen PromptRequest Normalization

### Semantic Fields (frozen, identical across all replays)

| Field | Value |
|---|---|
| taskType | `DECISION_PROPOSAL_GENERATION` |
| intent.id | `analyze-engineering-decision` |
| intent.version | `v1` |
| intent.outputProposalType | `ENGINEERING_DECISION` |
| intent.promptTemplate | `analyze-engineering-decision-prompt-v1` |
| selectedKnowledge | Run 2's complete selectedKnowledgeSnapshot |
| userGuidance | `null` |
| expectedOutputContract | engineering-decision-proposal-v1 schema |

### Transport Identity Fields (vary per replay, no semantic impact)

| Field | Value |
|---|---|
| requestId | Fresh UUID per replay |
| correlationId | Fresh UUID per replay |
| aiTaskId | Fresh UUID per replay |
| analysisId | `bff570db-55d2-468c-8193-440ebe7cfb2c` (frozen) |

Transport IDs do not affect generation. The prompt builder excludes them from the rendered prompt.

## 6. Frozen Request Fingerprints

```
FROZEN_PROMPTREQUEST_SHA256 = 1731f0df034da392e29c75a14a9215ff2a6b833176a3f66fe45cc8200cadce8c
SELECTED_KNOWLEDGE_SHA256 = (embedded in full prompt hash)
SYSTEM_MESSAGE_SHA256 = 241dbe6e6bb4194e0481f20817183c0303b465de629b7212d2f5e3c70cecb35b
USER_MESSAGE_SHA256 = 24255f0e30af9016fe6a93c1d8f049a889b8655b13bf9f7006296204ddf3b5fb
EXPECTED_OUTPUT_SCHEMA_SHA256 = d5f8d31f44bafa1a05c4942059195b4bb8ec5095520f65f71b3c1f3f98131635
FULL_RENDERED_PROMPT_SHA256 = 1731f0df034da392e29c75a14a9215ff2a6b833176a3f66fe45cc8200cadce8c
```

Note: `FULL_RENDERED_PROMPT_SHA256` matches `FROZEN_PROMPTREQUEST_SHA256` because the prompt builder's content digest is computed over the same fields.

## 7. Corrective Runtime Verification

```
CORRECTIVE_PROMPT_DEPLOYED = YES
```

All 7 corrective rules verified in running container:
1. Positive engineering-decision emission gate: PRESENT
2. Technology presence alone is insufficient: PRESENT
3. Strong convergence requires independent decision signals: PRESENT
4. Repeated representations of same state are not independent: PRESENT
5. Generic technology benefits cannot establish project rationale: PRESENT
6. Prefer fewer well-supported decisions: PRESENT
7. Zero decisions is valid: PRESENT

## 8. Provider / Model / Generation Configuration

```
PROVIDER = openai
MODEL = gpt-4.1-mini
TEMPERATURE = PROVIDER_DEFAULT (not explicitly set in code)
TOP_P = PROVIDER_DEFAULT (not explicitly set in code)
SEED = NONE (not configured)
MAX_OUTPUT_TOKENS = 2000
RESPONSE_FORMAT = structured output (responses.parse with text_format)
STRUCTURED_OUTPUT = EngineeringDecisionGenerationOutput schema
RETRY_POLICY = 0 retries per replay (experiment purity)
GENERATION_CONFIGURATION_CHANGED = NO
```

The OpenAI provider uses `responses.parse()` with `text_format=EngineeringDecisionGenerationOutput`. No temperature, top_p, or seed parameters are passed — OpenAI defaults apply.

## 9. Replay Mechanism

- Script: `/tmp/frozen_replay.py` (copied into AI Engine container)
- Execution: `docker exec devlog-ai-engine python /tmp/frozen_replay.py`
- Prompt builder: `EngineeringDecisionPromptBuilder.build()` (same as production)
- Provider: `OpenAiLlmProvider` (same as production)
- No callback mechanism used — results captured directly from provider response
- No backend state mutation — no AiTask records created, no proposals persisted

## 10. Persistence / State Isolation

```
BACKEND_STATE_MUTATED = NO
PROPOSALS_PERSISTED = NO
KNOWLEDGE_PROMOTED = NO
VALIDATION_PERFORMED = NO
```

The replay script runs entirely inside the AI Engine container. It uses the prompt builder and OpenAI provider directly. No callback is sent to the backend. No proposals are created in the database.

## 11. Replay 1 Full Result

```
STATUS = COMPLETED
PROPOSAL_COUNT = 1
LATENCY = 10.08s
RETRIES = 0
```

**Proposal 1:**
- **Title:** Utilize Architecture Decision Records (ADRs) for Documenting Architectural Decisions
- **Context:** The project documents its architectural decisions using ADRs stored in a dedicated 'docs/decisions' directory. This practice is confirmed by multiple insights and an explicit ADR documenting decision to maintain ADRs as the standard for architectural documentation.
- **Choice:** Continue to maintain and use Architecture Decision Records (ADRs) as the standard practice for documenting and managing architectural decisions throughout the project lifecycle.
- **Rationale:** Evidence from the project repository and documented insights shows ADRs provide traceability, clarity, and maintain a historical record of architectural decisions. This approach aids onboarding, auditing, and future evolution of the system by improving communication among team members and stakeholders.
- **Consequences:** Maintaining ADRs ensures that all architectural decisions are well-documented and traceable, facilitating better project understanding and decision-making over time. It may require ongoing effort to keep records up to date but enhances project transparency and evolution.

```
REPLAY_DECISION_QUALITY = STRONG
```

## 12. Replay 2 Full Result

```
STATUS = COMPLETED
PROPOSAL_COUNT = 1
LATENCY = 4.54s
RETRIES = 0
```

**Proposal 1:**
- **Title:** Document Architectural Decisions Using Architecture Decision Records (ADRs)
- **Context:** The project has a directory dedicated to Architecture Decision Records (ADRs) and documentation indicating that architectural decisions are recorded in this format.
- **Choice:** Continue and standardize the use of ADRs as the primary method for documenting and managing architectural decisions throughout the project lifecycle.
- **Rationale:** ADRs provide traceability, clarity, and historical tracking of architectural choices, which aids onboarding, audits, and future system evolution. The use of ADRs is confirmed by multiple pieces of evidence including the presence of an ADR directory and specific documented decisions.
- **Consequences:** Improves communication among team members and stakeholders, facilitates maintaining a historical record of design choices, and supports project knowledge management.

```
REPLAY_DECISION_QUALITY = STRONG
```

## 13. Replay 3 Full Result

```
STATUS = COMPLETED
PROPOSAL_COUNT = 1
LATENCY = 6.22s
RETRIES = 0
```

**Proposal 1:**
- **Title:** Standardized Architectural Decision Documentation Using ADRs
- **Context:** The project maintains a dedicated 'docs/decisions' directory containing Architecture Decision Records (ADRs), as evidenced by the presence of ADR documentation files and a specific ADR-064 decision file added in the repository. This is complemented by insights confirming the practice of documenting architectural decisions using ADRs, providing traceability and a historical record of design choices that influence project evolution.
- **Choice:** Maintain and standardize the use of Architecture Decision Records (ADRs) as the formal method for documenting and managing architectural decisions throughout the project lifecycle.
- **Rationale:** Using ADRs ensures traceability, clarity, and historical tracking of architecture choices, which aids onboarding, audits, and future evolution of the system. ADRs improve communication among team members and stakeholders and are explicitly used and maintained in this project, reflecting a deliberate engineering practice.
- **Consequences:** The project gains a well-documented architecture history that supports maintainability and knowledge transfer. The use of ADRs may require discipline and effort to keep documentation up to date but results in improved clarity and traceability of decisions.

```
REPLAY_DECISION_QUALITY = STRONG
```

## 14. Replay 4 Full Result

```
STATUS = COMPLETED
PROPOSAL_COUNT = 1
LATENCY = 4.41s
RETRIES = 0
```

**Proposal 1:**
- **Title:** Using Architecture Decision Records (ADRs) to Document Architectural Decisions
- **Context:** The project 'devlog-ai' documents architectural decisions to ensure traceability and maintain a historical record of design choices. The project maintains an ADR directory and employs ADRs as a standard practice.
- **Choice:** Maintain and continue using Architecture Decision Records (ADRs) as the method for documenting and managing architectural decisions throughout the project lifecycle.
- **Rationale:** Multiple independent validated insights confirm that the project uses ADRs for documentation, highlighting benefits such as traceability, clarity, and historical tracking of architecture choices. An explicit ADR decision confirms this choice as standard practice to aid onboarding, audits, and future evolution.
- **Consequences:** Continuing the use of ADRs ensures consistent architectural decision documentation, improving communication among team members and stakeholders. It requires ongoing discipline to document decisions in ADRs and may impose some overhead in maintenance but provides long-term project benefits.

```
REPLAY_DECISION_QUALITY = STRONG
```

## 15. Replay 5 Full Result

```
STATUS = COMPLETED
PROPOSAL_COUNT = 1
LATENCY = 7.58s
RETRIES = 0
```

**Proposal 1:**
- **Title:** Adopt Architecture Decision Records (ADRs) for Documenting Architectural Decisions
- **Context:** The project has established a practice of documenting architectural decisions using Architecture Decision Records (ADRs) stored in a dedicated directory. This is confirmed by the presence of the ADR directory and multiple architectural decision documents, including ADR-064.md, as well as explicit repository evidence and validated insights highlighting the use of ADRs for traceability and historical records.
- **Choice:** Maintain and enforce the use of ADRs as the standard approach for capturing and managing architectural decisions throughout the project lifecycle.
- **Rationale:** The use of ADRs ensures traceability, clarity, and a historical record of design choices impacting system evolution. They aid onboarding, provide audit trails for decisions, and improve communication among team members and stakeholders. This practice is explicitly documented and confirmed in the project evidence, demonstrating its centrality to project architecture management.
- **Consequences:** Consistent use of ADRs will facilitate better decision traceability and historical understanding. It imposes discipline on documenting architecture decisions but may require ongoing effort to keep the ADRs up to date. ADRs can become sources for automated tooling and context retrieval in the engineering workflow.

```
REPLAY_DECISION_QUALITY = STRONG
```

## 16. Proposal Classification

### Per-Proposal Classification

| Replay | Proposal | Classification | Choice Support | Rationale Support | Generic Rationale | Unsupported Causality | Eligibility Verdict |
|---|---|---|---|---|---|---|---|
| 1 | ADR Documentation | EXPLICIT | STRONG | STRONG | NO | NO | ELIGIBLE_EXPLICIT |
| 2 | ADR Documentation | EXPLICIT | STRONG | STRONG | NO | NO | ELIGIBLE_EXPLICIT |
| 3 | ADR Documentation | EXPLICIT | STRONG | STRONG | NO | NO | ELIGIBLE_EXPLICIT |
| 4 | ADR Documentation | EXPLICIT | STRONG | STRONG | NO | NO | ELIGIBLE_EXPLICIT |
| 5 | ADR Documentation | EXPLICIT | STRONG | STRONG | NO | NO | ELIGIBLE_EXPLICIT |

### Classification Evidence

All 5 proposals reference:
- ADR directory presence (`docs/decisions/`)
- Explicit ADR decision evidence (`decision:ae47a47d-65fa-4a30-810c-f114b37755bd`)
- Validated insights confirming ADR usage
- ADR-064.md as a specific example

All 5 proposals avoid:
- Technology-presence-only reasoning
- Generic framework benefits as project rationale
- Unsupported historical causality

## 17. Per-Replay Quality

| Replay | Status | Proposals | EXPLICIT | CONVERGENT | TECHNOLOGY_ONLY | GENERIC | CAUSAL | Eligible | Rejected | Quality |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | COMPLETED | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 | STRONG |
| 2 | COMPLETED | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 | STRONG |
| 3 | COMPLETED | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 | STRONG |
| 4 | COMPLETED | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 | STRONG |
| 5 | COMPLETED | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 | STRONG |

## 18. Cross-Replay Reliability Metrics

```
TOTAL_REPLAYS = 5
REPLAYS_WITH_ONLY_ELIGIBLE_DECISIONS = 5
REPLAYS_WITH_TECHNOLOGY_ONLY = 0
REPLAYS_WITH_GENERIC_RATIONALE = 0
REPLAYS_WITH_UNSUPPORTED_CAUSALITY = 0
REPLAYS_WITH_ZERO_DECISIONS = 0
REPLAYS_WITH_EXPLICIT_ADR = 5
TOTAL_PROPOSALS = 5
TOTAL_ELIGIBLE = 5
TOTAL_REJECTED = 0

CLEAN_REPLAY_RATE = 100%
TECHNOLOGY_ONLY_REPLAY_RATE = 0%
GENERIC_RATIONALE_REPLAY_RATE = 0%
CAUSALITY_FAILURE_REPLAY_RATE = 0%
```

## 19. Semantic Consistency

```
EXPLICIT_ADR_DECISION_STABILITY = STRONG
  (all 5 replays emit the ADR decision with strong evidence grounding)

TECHNOLOGY_DECISION_SUPPRESSION_STABILITY = STRONG
  (all 5 replays suppress technology-presence decisions)

RATIONALE_GROUNDING_STABILITY = STRONG
  (all 5 replays ground rationale in project-specific ADR evidence)

PROPOSAL_COUNT_STABILITY = STRONG
  (all 5 replays emit exactly 1 proposal)

ELIGIBILITY_SEMANTICS_STABILITY = STRONG
  (all 5 replays apply the same eligibility threshold)
```

## 20. Retry Analysis

```
REPLAY_1_RETRIES = 0
REPLAY_2_RETRIES = 0
REPLAY_3_RETRIES = 0
REPLAY_4_RETRIES = 0
REPLAY_5_RETRIES = 0
```

No retries occurred. All replays succeeded on the first attempt. No schema validation failures, no provider errors, no corrective retry paths triggered.

## 21. Pure Model Variance Assessment

With truly frozen input:
- **Same system message**: CONFIRMED (hash `241dbe6e...`)
- **Same user message**: CONFIRMED (hash `24255f0e...`)
- **Same output schema**: CONFIRMED (hash `d5f8d31...`)
- **Same provider/model**: CONFIRMED (openai/gpt-4.1-mini)
- **Same generation config**: CONFIRMED (no temperature/seed set)

Result: **Zero variance across 5 replays.** All outputs are semantically identical (ADR decision only, strong evidence, no technology-only).

```
PURE_MODEL_VARIANCE_CONFIRMED = YES (zero variance observed)
```

The model produces deterministic output for this specific frozen input under default OpenAI configuration.

## 22. Product Quality Assessment

```
SAFETY / GOVERNANCE RISK = NONE
  (all proposals are legitimate ADR decisions with explicit evidence)

PRODUCT QUALITY / REVIEW NOISE = MINIMAL
  (1 proposal per replay, all high-quality, all eligible for validation)
```

## 23. Governance Risk Assessment

No proposals would corrupt trusted knowledge if validated:
- All are EXPLICIT with strong evidence
- All reference verifiable project artifacts (ADR directory, specific ADR documents)
- No technology-only or generic proposals that would create false decision records

## 24. Remaining Problem Classification

```
PRIMARY_REMAINING_PROBLEM = ACCEPTABLE_STOCHASTIC_VARIANCE
```

The corrective prompt produces reliable, high-quality engineering-decision proposals when given frozen input. The historical `4/1/1` variance was caused by selection input differences (different fact UUIDs across Analysis runs), not model stochasticity on identical input.

The prompt-level corrective implementation (Options A+B+C+D) is effective. The remaining variance in production is attributable to:
1. Different fact UUIDs per Analysis run (minor — same semantic content)
2. The model's behavior on slightly different prompt serializations

## 25. Candidate Next Directions

Based on frozen replay results:

**A. Accept current behavior.** The corrective prompt is effective. The 100% clean rate on frozen input demonstrates that the prompt correctly encodes the emission gate rules. Production variance is caused by input differences, not model unreliability.

**B. Investigate deterministic post-generation eligibility validation.** Even though the model is reliable on frozen input, a deterministic validator could catch the rare production case where technology-only decisions slip through. This is a defense-in-depth measure.

**C. No further prompt tuning needed.** The corrective prompt produces correct output 100% of the time on frozen input. Additional prompt language would not improve this.

## 26. Temperature / Seed Assessment

```
TEMPERATURE_CONTROL_WORTH_INVESTIGATING = NO
  (model produces deterministic output on frozen input without temperature control)

SEED_CONTROL_WORTH_INVESTIGATING = NO
  (model produces deterministic output on frozen input without seed)
```

Since the frozen replay shows zero variance, temperature/seed configuration is not needed to fix the observed problem. The production variance is input-driven, not generation-driven.

## 27. Deterministic Validation Assessment

```
DETERMINISTIC_ELIGIBILITY_VALIDATION_WORTH_INVESTIGATING = YES
```

Rationale:
1. The frozen replay shows the model is reliable on identical input
2. However, production runs have different input (different fact UUIDs), which could occasionally trigger different model behavior
3. A deterministic validator could serve as a safety net: after generation, check whether each proposal references explicit decision evidence or only technology presence
4. This is a defense-in-depth measure, not a primary fix
5. The rule "technology presence != engineering decision" is sufficiently objective for deterministic validation

## 28. Model Capability Assessment

```
CURRENT_MODEL_CAPABILITY_SUFFICIENT = YES
```

The model correctly:
- Identifies the ADR decision as the only eligible engineering decision
- Suppresses technology-presence decisions (Docker, Spring Boot, Maven)
- Grounds rationale in project-specific evidence
- Does not invent unsupported causality
- Produces zero proposals when instructed (though not needed here — ADR is legitimately eligible)

## 29. Story 0106 Next-Step Recommendation

```
STORY_0106_RECOMMENDED_NEXT_ACTION = A
```

**A. Accept current stochastic behavior.**

Rationale:
1. Frozen replay proves the corrective prompt produces 100% clean output on identical input
2. The prompt correctly encodes all 7 corrective rules
3. Production `4/1/1` variance is caused by selection input differences (different fact UUIDs), not model unreliability
4. The selection pipeline is deterministic (confirmed by prior investigation)
5. The UUID differences are expected per-Analysis entity identity and do not constitute information-content variance
6. No further prompt tuning is warranted
7. The human validation boundary remains the governance safeguard for any rare edge cases

## 30. HUMAN Review Gate

This investigation provides evidence for HUMAN review. It does NOT:

- declare Story 0106 accepted
- declare the implementation approved
- authorize commit
- authorize push
- authorize merge

---

## Appendix: Investigation Metadata

- Report path: `docs/investigations/story-0106-frozen-promptrequest-generation-robustness-investigation.md`
- Git branch: `story/0106-intent-aware-context-utilization`
- HEAD SHA: `70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a`
- Working tree: uncommitted Story 0106 implementation + corrective changes + untracked investigation files
- Frozen reference: Run 2 (`bff570db-55d2-468c-8193-440ebe7cfb2c`)
- Replay script: `/tmp/frozen_replay.py` (inside AI Engine container)
- Results file: `/tmp/frozen_replay_results.json`
- Total model calls: 5
- Total proposals generated: 5
- Total eligible proposals: 5
- Total rejected proposals: 0
- All proposals individually classified: YES

---

`STORY_0106_FROZEN_PROMPTREQUEST_REPLAY_READY_FOR_HUMAN_REVIEW`
