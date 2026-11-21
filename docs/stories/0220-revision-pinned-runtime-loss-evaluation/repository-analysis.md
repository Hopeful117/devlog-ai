# Repository Analysis - Story 0220

## Status

READY FOR HUMAN REVIEW - IMPLEMENTATION NOT AUTHORIZED.

This analysis is based on the approved Story 0220 definition and current
repository evidence. It does not approve the Implementation Plan or authorize
production changes.

## Repository State

- Repository: `devlog-ai`
- Current branch: `story/0210-agent-response-acceptability-contract`
- Current local HEAD: `525c9701`
- Story 0219 was merged through PR #223; the local branch has not been switched
  to local `main` after that merge.
- Working tree was clean before materializing Story 0220.
- Story 0220 currently adds only its Story and Repository Analysis artifacts.

## DevLog Context

- DevLog applicability preflight was attempted through
  `devlog_devlog_story_agent` for the epic and recent Story artifacts.
- The request returned a bounded, truncated context with freshness revision
  `d1276891ed534a9960870e0ed29248e2ce6add6f`, but failed structured-output
  validation with `STRUCTURAL_CONTRACT_ERROR`.
- Repository evidence is therefore authoritative for this analysis.
- No Obsidian vault context was consulted; the decision is fully covered by
  repository Stories, ADRs, implementation reports, and current code.

## Governing Context

### Story 0178 - Existing Loss Ledger

Story 0178 already delivered a bounded Core-side `ContextLossLedger` and
attached it to the immutable Story Context execution snapshot. Its decision
report identifies composition, projection, preflight, and provider completion
as the current measurable bottlenecks and explicitly recommends revision-pinned
runtime captures before reconsidering retrieval architecture.

### Story 0219 - Explicit Identity

Story 0219 preserves project/source/revision scope, freshness, provenance, and
revision consistency through the canonical context, execution snapshot,
callback validation, and query projections. Story 0220 can use these identities
as capture keys and must not reconstruct them.

### Story 0209 - Response Quality

Story 0209 provides deterministic grounding and response-quality evaluation
fixtures. Its provider-free results remain distinct from live provider quality
measurements, which supports the `NOT_EVALUATED` rule in Story 0220.

### ADR-066 and ADR-079

ADR-066 requires replay-first evaluation infrastructure outside production
behavior. ADR-079 requires distinct selection, projection, provider-output, and
terminal failure semantics, with incomplete provider output treated as terminal.

## Current Implementation Findings

### Existing reusable surfaces

- `ContextLossLedger` already records bounded entries, aggregate counts, loss
  reasons, budgets, projection accounting, provider outcome metadata, evaluation
  telemetry, and a digest.
- `StoryContextSubmissionService` stores `lossAccounting` in the immutable task
  snapshot.
- `StoryContextAgentCallbackFacade` updates provider outcome status from bounded
  interaction traces on callback.
- `AiInteractionTraceRequest` and `AiInteractionTrace` already carry bounded
  incomplete reason and provider status fields.
- `ai-engine/evaluations` already contains Story Context runtime and ABC
  evaluation utilities that can host provider-free capture normalization and
  reports.

### Confirmed gap

`ContextLossLedger.capture` initializes provider-visible evidence count to zero
and marks visibility as unknown. The callback facade intentionally preserves
that state because the current callback contract does not expose per-reference
provider visibility. This is correct fail-closed behavior, not a defect to fix
by inference.

The Story must therefore distinguish:

- provider execution was authorized or attempted;
- provider returned a terminal status;
- provider-visible evidence attribution was measured;
- semantic quality was eligible for evaluation.

It must never convert the first two facts into the third.

### Existing evaluation limitations

- Provider-free replay can validate capture schema, identity, terminal-state
  normalization, denominator rules, and digest stability.
- It cannot establish live model faithfulness, latency, provider token usage, or
  per-reference provider visibility without a valid bounded capture.
- Live captures may be unavailable or stale and must become `NOT_EVALUATED`, not
  synthetic baseline data.

## Proposed Technical Boundary

The preferred implementation boundary is the evaluation layer plus bounded
capture adapters over existing Core snapshots and interaction traces.

Expected flow:

```text
immutable Core snapshot + interaction trace metadata
    -> revision/identity validation
    -> terminal outcome normalization
    -> loss-ledger correlation
    -> semantic eligibility gate
    -> deterministic report
```

No provider-facing request change is currently required. No new database entity
is justified by the current evidence. If implementation proves that a required
identity cannot be recovered from existing bounded surfaces, the Implementation
Plan must stop and request a contract decision rather than broadening storage.

## Scenario Matrix

The Repository Analysis recommends the following deterministic scenarios:

| Scenario | Technical outcome | Semantic outcome |
|---|---|---|
| targeted, complete | `COMPLETED` | eligible if capture and oracle are valid |
| broad, Core-reduced | `PROJECTION_REDUCED` or `COMPLETED` | eligible with bounded limitation |
| multi-source, revision-pinned | `COMPLETED` | eligible only when identities agree |
| insufficient evidence | `COMPLETED` | abstention/insufficient outcome |
| deliberately truncated | `PROJECTION_REDUCED` | bounded or `NOT_EVALUATED` per oracle |
| provider incomplete | `PROVIDER_INCOMPLETE` | `NOT_EVALUATED` |
| provider timeout | `PROVIDER_TIMEOUT` | `NOT_EVALUATED` |
| invalid or mixed-revision capture | `NOT_EVALUATED` | `NOT_EVALUATED` |

The exact scenario fixtures and oracles belong in the Implementation Plan and
must reuse existing Story 0177/0209 fixtures where their identities remain
valid.

## Risks and Controls

| Risk | Control |
|---|---|
| Inferring provider-visible evidence from projected evidence | Keep visibility `UNKNOWN` unless an explicit capture proves it |
| Mixing current and historical revisions | Validate project/source/revision identity before scoring |
| Counting provider failure as semantic failure | Use `NOT_EVALUATED` and exclude from quality denominators |
| Duplicating Story 0178 ledger logic | Reuse `ContextLossLedger` and add only normalization/correlation |
| Expanding production contracts prematurely | Keep detailed captures in evaluation artifacts and use existing bounded traces |
| Treating live results as baseline | Require frozen configuration, revision, oracle, and capture identity |
| Leaking prompts or repository payloads | Persist only bounded metadata, digests, counts, reasons, and statuses |

## Scope Recommendation

Story 0220 should remain an evaluation/observability Story. The Repository
Analysis finds no evidence that a new retrieval path, budget increase, provider
retry policy, public contract field, or domain persistence entity is required.

The Implementation Plan should decide the exact artifact format and adapters,
but should preserve this boundary:

- existing Core metadata remains authoritative;
- evaluation consumes immutable snapshots and bounded traces;
- provider visibility is unknown unless explicitly captured;
- production behavior is unchanged;
- a later corrective Story is required for any measured production change.

## Required Implementation-Plan Decisions

1. Select the canonical capture format under `ai-engine/evaluations`.
2. Define the minimum identity envelope and digest inputs.
3. Define terminal-state normalization and `NOT_EVALUATED` eligibility rules.
4. Define whether optional live capture support is required or deferred.
5. Define report schema and denominator handling.
6. Define focused backend, AI Engine, and replay validation commands.

## Readiness

`READY_FOR_HUMAN_REVIEW`.

Human approval of this Repository Analysis is required before an Implementation
Plan may be created.
