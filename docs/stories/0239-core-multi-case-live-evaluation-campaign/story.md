# Story 0239 - Core Multi-Case Live Evaluation Campaign Contract

## Status

**IMPLEMENTED - HUMAN ACCEPTANCE PENDING.**

Decisions D1-D4 recorded and implementation requested by the human on
2026-11-25. The campaign contract entry was registered with Story 0237 AC-6. No provider call, live evaluation, commit, push or
merge was performed.

## Epic

[Fiabiliser les reponses DevLog pour les agents](../../epics/agent-response-reliability/epic.md)

## Governing Artifacts

- ADR-006 - AI Proposal and Knowledge Promotion Workflow
- ADR-066 - Replayable AI Intent Evaluation
- ADR-067 - DevLog Engineering Story Context Agent
- Story 0223 - Agentic Quality Evaluation and Real Engineering Story Pilot (8 x 3 x 3 matrix)
- Story 0228 - MCP Live Evaluation Preparation
- Story 0229 - MCP Live Evaluation Authorization
- Story 0236 - MCP Agent Approval for Live Evaluation
- Story 0237 - ABC Harness Validity (AC-6 blocked by this Story)

## Problem

The human-selected closure evidence level for the epic is the full Story 0223
campaign: 8 questions x 3 conditions x 3 repetitions = 72 observations. All
eight oracles are `ACCEPTED`.

Core live preparation and authorization only accept the Q1 contract:

- `LiveEvaluationPreparationService` hard-codes the manifest id, version and
  digest, the source revision, the Q1 oracle digest, the three Q1 observation
  ids and `caseId=Q1`, and rejects anything else with
  `... is not the approved Q1 contract`;
- `LiveEvaluationAuthorizationService.approve` hard-codes
  `oracleStatus=Q1_ORACLE_ACCEPTED`;
- the authorization is consumed once for the whole observation set.

The evaluation harness must not bypass this Core authority (ADR-067), so the
campaign cannot run until Core can prepare and authorize a multi-case
contract.

## Goals

- Core prepares, authorizes and consumes a bounded multi-case campaign under
  the same fail-closed rules as Q1.
- The approved contract stays explicit and reviewable: no free-form manifest
  accepted from callers.
- The existing Q1 contract keeps working unchanged.

## Proposed Design

1. **Approved contract registry (decision D1).** Replace the Q1 constants
   with a Core-owned registry of approved live evaluation contracts. Each
   entry fixes:
   - manifest id, version and digest;
   - benchmark id and version;
   - source revision;
   - provider and model;
   - pricing digest;
   - operational limits;
   - runtime identities;
   - the ordered case list, with one accepted oracle digest per case;
   - repetitions.

   The Q1 contract becomes the first entry. The campaign contract is added
   when its manifest is frozen. Recommended form: code-defined entries
   reviewed through ADR/PR, not a database table writable at runtime.
2. **Deterministic observation set.** Core derives the expected observation
   ids from the contract as `{caseId}-{condition}-r{n}`. The request must match
   them exactly.
3. **Per-case oracle status.** Authorization records `oracleStatus` per case
   (`ACCEPTED` for each) instead of the literal `Q1_ORACLE_ACCEPTED`.
4. **Consumption granularity (decision D2).** Either one authorization per
   campaign, consumed once, with missing cells recorded as `MISSING` and never
   retried; or one authorization per case batch (3 conditions x 3
   repetitions). The recommended option is per-case batches, because a
   72-cell run under a single short-lived authorization is fragile.
5. **Limits.** Operational limits stay per observation. A campaign-level
   ceiling (maximum observations, maximum total wall clock) is added and
   checked by Core.

## In Scope

- `backend/.../evaluation/livepreparation/*` and
  `backend/.../evaluation/liveauthorization/*`, plus their tests.
- MCP tool parameter passthrough in `PrepareLiveEvaluationTool` if new fields
  are required.
- Documentation of the approved campaign contract.

## Out of Scope

- The evaluation harness matrix runner and campaign manifest. They remain
  Story 0237 AC-6, unblocked by this Story.
- Oracle changes, semantic scoring, live execution.
- Weakening any existing check: revision, runtime attestation, pricing,
  provider/model, one-shot consumption, approver identity.

## Acceptance Criteria

### AC-1 - Registry
Preparation validates requests against an approved contract registry. Q1
requests produce the same plan content as today, and Q1 tests pass unchanged.

### AC-2 - Campaign contract
A request for the registered campaign contract with exactly the derived
observation set and the accepted per-case oracle digests reaches `READY`.
Any unknown manifest, case, oracle digest, observation id, repetition count or
limit is blocked with an explicit blocker.

### AC-3 - Per-case oracle status
The authorization artifact lists the accepted oracle status per case, and no
Q1 literal remains in multi-case artifacts.

### AC-4 - Consumption
The chosen D2 granularity is enforced. A consumed authorization cannot be
replayed, and a partial batch never re-executes completed cells.

### AC-5 - Campaign ceiling
Core rejects a plan whose observation count or total wall clock exceeds the
contract ceiling.

### AC-6 - Validation
- `LiveEvaluationPreparationServiceTest` and
  `LiveEvaluationAuthorizationServiceTest` are extended;
- `./backend/mvnw -pl backend -am clean verify -B` passes;
- MCP server tests pass if the tool signature changes.

## Decisions Required

- D1: registry form (code-defined, recommended, or persisted).
- D2: consumption granularity (per campaign or per case batch, recommended).
- D3: campaign-level ceilings (maximum observations, maximum total duration).
- D4: whether the epic closure level stays 72 cells once the cost of the
  ChatGPT OAuth subscription runs is known.

## Dependencies

- Story 0238 should land first. Otherwise the Story 0237 freshness gate blocks
  every live run regardless of this Story.
- Story 0237 AC-6 (harness matrix) is implemented after this Story is
  accepted.

## Workflow and Gates

1. Human design approval (D1-D4).
2. Explicit implementation authorization.
3. Implement, validate, review diff.
4. Human acceptance, then Story 0237 AC-6, then a fresh live authorization.

## Decisions (human, 2026-11-25)

- D1: code-defined registry (`LiveEvaluationContractRegistry`), changed only through reviewed code.
- D2: one authorization per case batch (3 conditions x 3 repetitions = 9 observations; 8 batches).
- D3: campaign ceilings of 72 observations and 3 hours of planned wall clock.
- D4: epic closure keeps the full 72-cell campaign.

## Implementation Report

### Delivered
- `LiveEvaluationContract` (new): one approved contract, with:
  - the derived case-batch observation ids `{caseId}-{CONDITION}-r{n}` (repetition-major);
  - the planned batch wall clock;
  - a per-case accepted-oracle label (`Q1_ORACLE_ACCEPTED` for Q1, so the Python harness stays compatible).
- `LiveEvaluationContractRegistry` (new, Spring `@Component`): code-defined
  registry whose only entry today is the Q1 contract. Duplicate manifest
  digests are rejected.
- `LiveEvaluationPreparationService`: the Q1 constants are replaced by registry lookups.
  - Blockers are now contract-relative: unknown manifest, case outside the
    contract, case oracle digest, derived observation set, limits, runtime
    identities, pricing, provider/model, batch above the campaign ceilings.
  - The plan adds `contractId` and, for contracts with campaign rules, `campaignCeiling`.
- `LiveEvaluationAuthorizationService`:
  - approval records `oracleStatus={caseId}_ORACLE_ACCEPTED` and `caseOracleStatus`. A plan without a case identity is refused.
  - consumption enforces the campaign rules under an advisory campaign lock:
    one consumption per case batch, at most 72 observations, at most 3 h of planned wall clock.
- `LiveEvaluationAuthorizationRepository`: `findByStatusAndPreparation_Project_Id`.

### Compatibility
The Q1 contract keeps its historical behaviour: same observation set, same
blockers semantics (messages reworded), repeatable runs, no campaign ceiling,
same `Q1_ORACLE_ACCEPTED` label. The plan digest changes because `contractId`
is added; plans are always prepared fresh, so no stored plan is reused.

### Not delivered - campaign registry entry
The campaign contract cannot be registered yet:
- its manifest does not exist (Story 0237 AC-6), so its digest is unknown;
- only the Q1 oracle has a `canonicalDigest` in
  `docs/evaluation/devlog-ai-abc-oracle-proposals-v1.json`. Q2-Q8 have none.

Registering the entry is a one-entry code change once both are frozen, with
`repetitions=3`, `oneConsumptionPerCase=true`, `CAMPAIGN_MAX_OBSERVATIONS`
and `CAMPAIGN_MAX_WALL_CLOCK`.

### Tests
- `LiveEvaluationContractTest` (new, 4)
- `LiveEvaluationCampaignPreparationTest` (new, 6)
- `LiveEvaluationCampaignAuthorizationTest` (new, 7): first consumption,
  double consumption refused, observation ceiling, wall-clock ceiling, full
  8-batch campaign fits, Q1 rerun unaffected, per-case oracle label.
- Existing `LiveEvaluationPreparationServiceTest` and
  `LiveEvaluationAuthorizationServiceTest` adapted (registry argument;
  approval fixtures now carry `caseId=Q1`).
- Focused `LiveEvaluation*Test`: 26 tests, 0 failures.
- Full `./backend/mvnw -pl backend -am clean verify -B`: BUILD SUCCESS, 1948 tests, 0 failures, 0 errors.

### Campaign entry registered (2026-11-25, with Story 0237 AC-6)
`LiveEvaluationContractRegistry.ABC_CAMPAIGN` now binds:
- manifest `devlog-ai-abc-campaign` 1.0.0, digest `8bff8a65...6dfe`;
- the frozen canonical digests of the eight oracles;
- `repetitions=3`, one consumption per case batch, 72 observations, 3 h.

`LiveEvaluationCampaignPreparationTest.approvedRegistryAcceptsTheFrozenCampaignBatch`
shows a Q7 batch reaching `READY` with the default registry.
