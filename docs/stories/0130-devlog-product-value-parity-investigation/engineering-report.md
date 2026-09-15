# Story 0130 - Engineering Report

## Repository State

| Field | Value |
|---|---|
| Branch | `main` |
| HEAD | `9cc3f9488a13db1002cdda7044292cc93d6cbc46` |
| Worktree | Modified only by this investigation's new documentation artifacts |
| Origin main | `9cc3f9488a13db1002cdda7044292cc93d6cbc46` |
| Production code changed | `NO` |
| Commit/push | `NO` |

## Required Final Report

```text
BASELINE_HEAD = 9cc3f9488a13db1002cdda7044292cc93d6cbc46
BRANCH = main
ORIGIN_MAIN = 9cc3f9488a13db1002cdda7044292cc93d6cbc46
WORKTREE_STATUS = Clean at start; four new investigation documents now untracked
LATEST_STORY = Story 0129 - Harden v3 Typed-Reference Integrity
LATEST_ACCEPTED_ADR = ADR-068
DEVLOG_ANALYSIS_ENTRY_POINTS = Angular Analysis/Project Understanding, POST /api/v1/analyses, POST /api/v1/analyses/{id}/workflow
HUMAN_ANALYSIS_ENTRY_POINTS = Angular analysis/result/proposal review pages and the Analysis REST endpoints
AI_MCP_ANALYSIS_ENTRY_POINTS = get_engineering_context, search_project_history, analyze_story_context, MCP resources; backend engineering-context and Story Context REST endpoints
DEVICE_INTEGRATION_ENTRY_POINTS = Analysis completion -> CommunicationDecisionService -> AgentCommunicationPort -> AgentPresence HTTP adapter

CURRENT_HUMAN_UTILITY = LOW
CURRENT_AI_UTILITY = LOW
HUMAN_AI_PARITY = LOW / FAILING

HUMAN_ANALYSES_INSPECTED = 3 live completed results (2 devlog-ai describe-project, 1 trading-os analyze-engineering-decision), plus human UI/source path
AI_INTERFACE_INSPECTED = engineering-context REST endpoint, MCP source tools/resources, Story Context REST/MCP entry point
AI_INTERFACE_EXERCISED = YES, live Core REST equivalent of MCP get_engineering_context; live MCP JSON-RPC call NO because mcp-server is not a Compose service/running process

DIRECT_REPOSITORY_BASELINE_COMPARED = YES, against git log/path search, ADR/Story/file/test reading; no timed controlled benchmark yet

HISTORY_DATA_AVAILABLE = YES, imported Trading OS commits and changed files
HISTORY_DATA_ACTUALLY_USED = PARTIAL, bounded commit/change evidence and path search; no causal chronology in result
ADR_CONTEXT_ACTUALLY_USED = WEAK, generic ADR-presence summaries; governing ADR bodies not shown in inspected result
STORY_CONTEXT_ACTUALLY_USED = WEAK/PARTIAL, story summaries and paths can be selected; Story bodies/relationships not demonstrated in result
DIFF_CONTEXT_ACTUALLY_USED = PARTIAL, changed-file summaries and counts; not a coherent before/after explanation

PRIMARY_HUMAN_VALUE_GAP = Human output is generic proposal/insight prose and the UI does not expose an explorable historical evidence workspace
PRIMARY_AI_VALUE_GAP = Bounded context is discovery-oriented and does not reliably synthesize causal ADR/Story/commit/source constraints better than direct repository reading
PRIMARY_PARITY_GAP = Agent receives richer ranked discovery and history access while human receives generic results and limited drill-down; neither gets a proven causal synthesis

DEVICE_PATH_EXISTS = YES, Java port/adapter and completion trigger exist
DEVICE_PATH_END_TO_END_PROVEN = NO, default endpoint is unused and no device service/e2e proof exists
DEVICE_CURRENTLY_HAS_USEFUL_EVENTS_TO_SURFACE = NO EVIDENCE; current generic analyses are not interruption-worthy

STORY_0130_PHASE_1_STATUS = HUMAN_REVIEWED; LOW human/AI utility and LOW/FAILING parity preserved
STORY_0130_PHASE_2_STATUS = REFINED; candidate oracle requires human validation
BENCHMARK_SUITE_DEFINED = YES
BENCHMARK_VERSION = benchmark-suite-v1 / 1.0.0
CASE_01_FROZEN = YES
CASE_02_FROZEN = YES
CASE_03_FROZEN = YES
CASE_04_NEGATIVE_CONTROL_FROZEN = YES
GROUND_TRUTH_DEFINED = CANDIDATE_DEFINED; artifact resolution and causal labels require human approval
GROUND_TRUTH_HUMAN_VALIDATION_REQUIRED = YES
DIRECT_REPOSITORY_BASELINE_PROTOCOL_DEFINED = YES; timings pending execution
HUMAN_BASELINE_PROTOCOL_DEFINED = YES; timings pending execution
AI_BASELINE_PROTOCOL_DEFINED = YES; formal executable harness not implemented

EVIDENCE_RECALL_DEFINED = YES
EVIDENCE_PRECISION_DEFINED = YES
CONSTRAINT_RECALL_DEFINED = YES
CAUSAL_LINK_RECALL_DEFINED = YES
CHANGE_IMPACT_RECALL_DEFINED = YES
UNSUPPORTED_CLAIM_RATE_DEFINED = YES
NEGATIVE_CONTROL_ACCURACY_DEFINED = YES
CONTEXT_EFFICIENCY_DEFINED = YES
EVIDENCE_STABILITY_DEFINED = YES
METRIC_THRESHOLDS_FROZEN = YES; any future change requires explicit reason, human approval and suite version increment

REAL_PIPELINE_EVALUATION_DESIGNED = YES
DETERMINISTIC_SUITE_DESIGNED = YES
LIVE_PRODUCT_SUITE_DESIGNED = YES
HUMAN_ACCEPTANCE_PROTOCOL_DEFINED = YES
AI_ACCEPTANCE_PROTOCOL_DEFINED = YES
PARITY_GATE_DEFINED = YES

BENCHMARK_LEAKAGE_GUARDS_DEFINED = YES
BENCHMARK_VERSIONING_DEFINED = YES

CURRENT_RED_BASELINE_EXECUTED = PARTIAL; exploratory live baseline from Phase 1, not formal harness execution
CURRENT_RED_BASELINE_RESULT = FAIL; generic restatement, weak causality/constraints/impact, direct repository often superior

ENGINEERING_CORRECTNESS_GATE_DEFINED = YES
AI_UTILITY_GATE_DEFINED = YES
HUMAN_UTILITY_GATE_DEFINED = YES
FINAL_ACCEPTANCE_RULE = ENGINEERING_PASS AND AI_UTILITY_PASS AND HUMAN_UTILITY_PASS
DIRECTION_RETHINK_TRIGGER_DEFINED = YES; after two bounded iterations with either utility gate failing or direct repo equal/better
EVALUATION_HARNESS_IMPLEMENTATION_AUTHORIZED = NO
PRODUCTION_ANALYSIS_IMPROVEMENT_AUTHORIZED = NO

ROOT_CAUSE_1 = Generic intents and output contracts optimize repository description/proposal emission rather than a concrete historical/change-impact question
ROOT_CAUSE_2 = Chronology, ADR/Story bodies and cross-artifact causal relationships are collected or discoverable only partially and are not assembled into the result
ROOT_CAUSE_3 = Consumer projections diverge: human UI hides selected evidence while MCP exposes bounded discovery; prompt/result observability is incomplete

DEVLOG_CURRENT_UNIQUE_VALUE = Core-owned provenance, typed/grounded references, freshness and bounded cross-source evidence; currently a useful discovery/audit foundation, not a demonstrated superior engineering answer

RECOMMENDED_NEXT_VERTICAL_SLICE = Bounded historical engineering synthesis for one concrete change-impact question, exposed to both human and AI consumers

HUMAN_SUCCESS_CRITERIA = For three fixed Trading OS questions, median answer time is at least 30% lower than direct reconstruction, with inspectable linked chronology and no unsupported factual claims
AI_SUCCESS_CRITERIA = One bounded response contains relevant ADR/Story/commit/diff/source/test references and typed freshness metadata; at least one causal question needs fewer than three follow-up searches, with stable evidence across three replays

NEW_ADR_REQUIRED = NO for the slice; ADR-006/063/067/068 are sufficient, subject to architecture review if ADR-063 parity is formally strengthened

IMPLEMENTATION_AUTHORIZED = NO
PRODUCTION_CODE_CHANGED = NO
COMMIT = NO
PUSH = NO
MERGE = NO
```

## Plain Answers

1. **Is DevLog useful to the human today?** Low usefulness. It provides valid
   status, provenance and generic summaries, but the inspected outputs do not
   materially reduce the effort of understanding a complex evolution.
2. **Is DevLog useful to an AI/coding agent today?** Low, with a narrow
   discovery benefit. It can return bounded evidence and lexical history, but a
   coding agent can reconstruct the same or better answer from the repository
   with comparable or lower effort.
3. **Where does parity fail?** MCP/agent access has richer ranked evidence,
   history search and resources; human UI has analysis/proposal review but not
   equivalent exploration. More fundamentally, neither path has demonstrated
   causal historical synthesis.
4. **What does DevLog know that direct inspection does not exploit efficiently?**
   Imported normalized evidence, selection reasons, freshness, trust tiers,
   execution snapshots, cross-analysis records and Core-owned reference
   validation. These are not yet composed into a consistently useful answer.
5. **Why are current analyses useful or useless?** They are technically
   grounded descriptions, but mostly restate technology/file presence. The
   Trading OS decision output proposed generic testing, ADR and microservice
   practices instead of explaining the PAPER execution evolution.
6. **Is device communication end to end?** No. The Java HTTP path exists, but
   there is no running device service or delivery proof, and failures are
   deliberately dropped.
7. **What single next Story has the strongest chance of proving value?** The
   bounded historical engineering synthesis slice described in the plan, using
   the Trading OS question and requiring both projections to pass.
8. **What would cause abandonment or substantial rethink?** After two bounded
   iterations with fixed questions, if DevLog cannot beat direct reconstruction
   on human time or agent search count, or if users still classify results as
   restatement despite complete evidence links, the generic AI-analysis
   direction should be abandoned or reduced to provenance/discovery tooling.

## Governance Preserved

This investigation found no justification for weakening correctness. ADR-006
remains intact: AI output is non-trusted until human validation. ADR-068 typed
references and fail-closed grounding remain intact. Usefulness must be improved
through better question scoping, historical evidence assembly and consumer
projection, not by accepting unsupported output or treating confidence as
evidence.

## Readiness

`READY_FOR_HUMAN_REVIEW`

This report does not declare Story acceptance and does not authorize the
recommended implementation.
