# Implementation Plan - Story 0178

## Status

**PLAN APPROVED - IMPLEMENTATION IN PROGRESS**

This plan is based on the human-approved Repository Analysis for Story 0178 and
has been explicitly approved. It authorizes only the bounded implementation
described below.

## Objective

Add bounded, deterministic loss accounting to the existing Java/Core Story
Context pipeline so that an execution can explain how evidence moves from
candidate collection to provider-visible projection. Preserve all existing
authority, grounding, digest, trust, revision, and fail-closed semantics.

The first implementation will remain Core/evaluation-side:

- immutable execution snapshots receive a bounded `lossAccounting` section;
- normal interaction traces receive only bounded aggregate metadata;
- the provider request contract is unchanged;
- Python does not calculate, authorize, or persist the ledger;
- no retrieval, embedding, vector, or production budget change is introduced.

## Governing Constraints

- Java/Core remains authoritative for candidate identity, scope, revision,
  trust, selection, enrichment authorization, projection, grounding, budget
  preflight, and provider-call authorization.
- The ledger is diagnostic metadata, not trusted project knowledge and not AI
  output.
- Existing `contextDigest`, `projectionDigest`, grounding allow-lists, and
  callback identity semantics remain authoritative.
- Full prompts, repository payloads, credentials, authorization headers, and
  raw provider output are excluded from normal diagnostics.
- `response.incomplete` remains terminal and is never represented as successful
  provider-visible evidence.
- Existing uncommitted backend, Docker, benchmark, Playwright, OAuth, and Story
  0177 changes are outside this Story and must not be reverted or included.

## Design Decisions

### 1. Ledger ownership

Introduce an immutable Core-side `ContextLossLedger` value under the existing
project/repository-context diagnostic boundary. It is a diagnostic projection
over existing evidence and outcomes, not a new retrieval model or persistence
entity.

The ledger contains:

- schema/policy version;
- execution/context/projection identity references;
- aggregate counts for candidates, unique candidates, ranked candidates,
  selected evidence, enriched evidence, projected evidence, grounded evidence,
  provider-visible evidence, and overflow entries;
- aggregate budget measurements with unit and certainty;
- bounded reason counts;
- bounded per-reference entries joined only by canonical evidence reference.

### 2. Per-reference entry bound

Use a configurable bounded maximum of 256 entries for diagnostic detail. All
stage counts and reason counts remain aggregated when the bound is exceeded.
Entries beyond the bound are represented by `detailOverflowCount`; the ledger
must never grow with repository size without limit.

Each entry contains only:

- canonical reference;
- evidence family/kind and originating file when already available;
- project/source/revision identity or a bounded revision marker;
- trust/citability state when already available;
- stage status for selected, enriched, projected, grounded, and provider-visible;
- one normalized loss reason per stage where applicable;
- before/after estimated token values where available.

It does not contain evidence text, full summaries, prompts, provider output, or
secrets.

### 3. Stage joining

The canonical evidence reference is the only join key. The implementation must
not join by array position, display path alone, relevance score, or provider
output text.

Stage semantics:

- candidates/ranking/selection derive from `RepositoryContext` and selection
  decisions;
- enrichment derives from the before/after `RepositoryEvidence` content and
  existing enrichment status/reason metadata;
- projection derives from input evidence, projected evidence, accounting, and
  projection warnings;
- grounding derives from the existing authorized grounding reference set;
- provider-visible evidence derives from the exact Core projection boundary,
  not from AI output. If exact prompt-level reference visibility cannot be
  measured without serializing/storing prompt content, report it as
  `UNKNOWN` rather than infer it.

### 4. Persistence and transport

Add the ledger to the existing immutable Story Context execution snapshot under
`lossAccounting`. Add only aggregate fields to normal interaction trace
metadata. Do not add the ledger to the provider-facing `PromptRequest` unless a
later compatibility decision proves it necessary.

No database migration is planned. Existing JSON snapshot/metadata persistence
is the compatibility surface. If repository inspection during implementation
proves that the snapshot cannot safely carry the bounded field, stop and report
the required contract decision rather than introducing an unplanned entity.

### 5. Failure semantics

The ledger distinguishes:

- `CORE_SELECTION_REDUCED`;
- `SELECTION_ITEM_BUDGET_EXCEEDED`;
- `SELECTION_TOKEN_BUDGET_EXCEEDED`;
- `ENRICHMENT_SKIPPED`;
- `ENRICHMENT_TRUNCATED`;
- `ENRICHMENT_UNAVAILABLE`;
- `PROJECTION_COMPACTED`;
- `PROJECTION_EVIDENCE_REMOVED`;
- `GROUNDING_NOT_AUTHORIZED`;
- `PROMPT_PREFLIGHT_REJECTED`;
- `PROVIDER_INCOMPLETE`;
- `PROVIDER_VISIBILITY_UNKNOWN`.

Existing domain warning strings remain unchanged. The ledger maps them to
versioned normalized categories rather than replacing them.

## Implementation Sequence

### Phase 1 - Define bounded diagnostic contracts

Add immutable records/enums for ledger summary, entry, stage status, budget
measurement, and normalized loss reason. Add validation for canonical
references, bounded strings, valid stage transitions, and the 256-entry detail
limit.

Likely surfaces:

- `backend/.../repositorycontext/` or the existing project-context diagnostic
  package for `ContextLossLedger`;
- `RepositoryContext`/diagnostics integration only if needed to retain the
  pre-projection evidence identity set;
- no public REST/MCP contract change in this phase.

### Phase 2 - Capture candidate, ranking, and selection outcomes

Build ledger input from existing `RepositoryContext` fields and
`SelectionDecision` values. Preserve current decisions and digest inputs.
Record aggregate and bounded per-reference selection reasons, duplicate counts,
candidate/unique/selected/discarded counts, and selection token usage.

Do not change selection ordering, requested-file priority, category policy,
budget limits, or ranking behavior.

### Phase 3 - Capture enrichment outcomes

Capture before/after evidence token estimates and existing content status/reason
values around symbol/file enrichment. Normalize current warning constants into
ledger reasons while preserving the existing warning list and final evidence
objects.

Verify that configuration-file exclusion and requested-file behavior remain
unchanged.

### Phase 4 - Capture projection outcomes

Extend the projection result/accounting path to derive bounded per-reference
projection outcomes and compaction/removal reason counts. Preserve the current
mechanical degradation order, projection digest calculation, byte/token limits,
and fail-closed impossible-projection behavior.

Record projected evidence count and removed evidence count without adding loss
details to the provider payload.

### Phase 5 - Attach snapshot and trace metadata

Attach `lossAccounting` to the existing Core-authorized execution snapshot after
projection and preflight decisions are known. Add only compact aggregate fields
to normal interaction-trace metadata:

- ledger version;
- selected/projected/provider-visible counts;
- dominant loss reason;
- preflight outcome;
- provider terminal category when available;
- ledger digest.

Keep provider request and callback identity contracts unchanged. Preserve
fail-closed behavior if ledger construction itself cannot validate a required
canonical identity; represent optional measurements as `UNKNOWN` only where the
existing contract permits it.

### Phase 6 - Extend evaluation and report generation

Extend Story 0177 replay captures with stage outcomes and add deterministic
fixtures for:

- exact targeted evidence surviving all stages;
- broad evidence reduced by selection;
- selected file skipped or truncated during enrichment;
- projection content removal;
- tail evidence removal;
- grounding exclusion;
- preflight rejection;
- provider-incomplete terminal state;
- unknown provider visibility.

Keep the evaluator model-free and compatible with the existing Story 0177
standalone replay. Do not force the unrelated ADR-066 reviewed-intent runner to
consume a different capture contract.

### Phase 7 - Documentation reconciliation

Update only Story 0178 documentation and any canonical operational diagnostic
documentation directly affected by the final implementation. Do not modify the
generic evaluation README or unrelated historical investigations.

## Planned Files and Surfaces

Expected implementation surfaces, subject to verification during execution:

- `backend/src/main/java/.../repositorycontext/` or project-context diagnostics:
  new bounded ledger records/enums;
- `RepositoryContext` and/or `RepositoryContextDiagnostics`: ledger inputs and
  stage accounting integration;
- `RepositoryContextEngine`: stage boundary capture orchestration;
- `SelectedFileContentEnricher` and `SelectedJavaSymbolEnricher`: bounded
  enrichment outcome capture if existing evidence metadata is insufficient;
- `AgentContextProjectionService` and `AgentRepositoryContext`: projection
  accounting integration without provider-payload expansion;
- `StoryContextSubmissionService`: snapshot and aggregate trace attachment;
- existing Story Context diagnostic/trace DTOs only if additive fields are
  required;
- backend unit/contract tests for each affected surface;
- `ai-engine/evaluations/` Story 0177 capture/report extensions only if the
  current offline evaluator cannot consume the new bounded fields;
- `docs/stories/0178-story-context-loss-aware-composition/`: implementation
  report and final decision report.

No frontend, MCP server, database migration, Docker, provider adapter, or
Python runtime source change is planned.

## Test Strategy

### Contract and unit tests

- ledger rejects missing/invalid canonical references;
- ledger detail bound and overflow accounting are deterministic;
- stage joins use canonical references and remain stable under ordering changes;
- selection reasons and aggregate counts match existing decisions;
- enrichment before/after tokens and statuses are captured correctly;
- projection compaction/removal maps to normalized reasons;
- grounding and revision restrictions remain fail-closed;
- snapshot/trace fields are bounded and redact sensitive content.

### Integration tests

- repository context through enrichment produces a complete ledger;
- Story Context submission persists `lossAccounting` without changing prompt
  request or callback identity;
- preflight rejection produces a ledger terminal category without provider
  invocation;
- provider incomplete fixture produces terminal `PROVIDER_INCOMPLETE` and no
  successful analysis result;
- old provider contract remains valid because no request fields are added.

### Evaluation tests

- replay Story 0177 corpus with stage-level metrics;
- compare targeted, broad, insufficient, multi-source, and truncated cases;
- verify deterministic digests and stable reason aggregation;
- verify no secrets, prompts, raw repository content, or raw provider output are
  emitted.

## Validation Commands

Focused:

```bash
./backend/mvnw -pl backend test \
  -Dtest=RepositoryContextServiceTest,BudgetedDiverseEvidenceSelectorTest,SelectedFileContentEnricherTest,AgentContextProjectionServiceTest,StoryContextBudgetPreflightTest -B
```

Relevant Story Context and grounding tests should then be run using their exact
repository class names. The existing Story 0177 replay remains:

```bash
cd ai-engine && python3 -m py_compile \
  evaluations/hybrid_retrieval.py \
  tests/test_hybrid_retrieval_evaluation.py
cd ai-engine && python3 -m evaluations.hybrid_retrieval --json
```

Broader validation:

```bash
./backend/mvnw -pl backend -am clean verify -B
cd ai-engine && python3 -m pytest -q
git diff --check
```

If `pytest` remains unavailable, report it as blocked rather than claiming the
Python suite passed.

## Risks and Mitigations

- **Ledger changes canonical digests:** keep diagnostic accounting outside
  existing digest inputs unless the digest contract explicitly requires it;
  add digest regression tests.
- **Ledger increases snapshot size:** bound entries, aggregate overflow, and do
  not include content or prompts.
- **Stage joins misattribute evidence:** use canonical references only and test
  reordered inputs and duplicate candidates.
- **Diagnostics leak sensitive data:** use allow-listed scalar metadata and
  redaction tests.
- **Existing worktree changes conflict:** inspect and preserve pre-existing
  modifications; do not revert or silently include them.
- **Provider-visible status is inferred:** report `UNKNOWN` unless the exact
  provider boundary can be measured without storing prompts.
- **Scope expands into retrieval redesign:** stop and request a new Story if a
  requirement needs a new retrieval universe or budget policy.

## Implementation-Level Decisions

This plan makes the following bounded decisions:

1. Initial ledger storage uses existing execution snapshot/trace mechanisms; no
   new database entity is introduced.
2. Per-reference diagnostic detail is capped at 256 entries with aggregate
   overflow counts.
3. The provider request contract remains unchanged.
4. Unknown provider visibility is reported explicitly rather than inferred.
5. The existing Story 0177 standalone evaluator remains the evaluation entry
   point; the unrelated reviewed-AI-intent harness contract is not changed.

## Blockers / Repository Inconsistencies

- DevLog project mapping and Story lifecycle registration were not available
  from the repository tooling configuration in this session; this does not
  block repository analysis or planning.
- `pytest` was unavailable during prior Story 0177 validation; it remains a
  validation environment issue, not an implementation design blocker.
- The worktree contains unrelated uncommitted changes that must remain outside
  the Story 0178 diff.

## Readiness

`IMPLEMENTATION_IN_PROGRESS`
