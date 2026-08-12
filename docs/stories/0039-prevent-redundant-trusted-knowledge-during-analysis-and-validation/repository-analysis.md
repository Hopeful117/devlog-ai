# Story 0039 — Prevent Redundant Trusted Knowledge During Analysis And Validation — Repository Analysis

## Purpose

Analyze the minimum repository changes required to prevent new redundant trusted
knowledge from being created, while preserving:

* ADR-006 proposal history;
* ADR-050 incremental knowledge evolution;
* ADR-051 duplicate policy;
* existing validation and promotion boundaries.

This Story is not about cleaning existing duplicate stock.

It is about preventing new duplicate trusted knowledge from being created in the
normal analysis → proposal → validation → promotion lifecycle.

## Relevant Components

### `ADR-050`

Story 0037 already introduced bounded trusted architecture knowledge context
for `architecture-overview`.

Current benefits:

* repeated architecture analyses can already emit:
  - `NEW`
  - `ENRICHES`
  - no proposal
* the AI now has an architecture-specific comparison target

Current limitation:

* this only reduces duplicate creation pressure upstream;
* it does not guarantee that a semantically equivalent proposal cannot still be
  accepted and promoted as a new trusted `Insight`.

### `ADR-051`

Now defines the policy boundary:

* repeated proposal history can remain acceptable;
* redundant trusted knowledge is not acceptable target-state behavior;
* upstream prevention is primary;
* downstream safeguards are still required.

**Implication**:

Story 0039 must improve behavior without collapsing ADR-006 history semantics.

### `KnowledgeSelectionServiceImpl`

Current role:

* deterministic selection;
* bounded insight selection;
* architecture-specific `existingArchitectureKnowledge` context;
* fact-content deduplication.

Strength:

* already provides the upstream context needed for architecture duplicate
  avoidance.

Limitation:

* duplicate-aware behavior is currently architecture-only;
* non-architecture intents still receive no explicit duplicate-control context;
* the service does not itself decide whether a future trusted write is allowed.

### `AiProposalContractValidator`

Current role:

* validates grounding;
* validates allowed payload shape;
* validates architecture `deltaType`;
* validates `ENRICHES` target membership in selected existing architecture
  knowledge;
* rejects duplicate engineering-event proposals within a single AI result set.

Strength:

* already proves the Core can reject structurally invalid or duplicate-like
  output before persistence.

Limitation:

* it only knows the selected context of the current AI task;
* it does not evaluate the broader trusted knowledge base for exact or semantic
  duplicate risk;
* using it as the primary duplicate-enforcement boundary for trusted knowledge
  would blur proposal-history vs trusted-persistence responsibilities.

### `AiTaskResultServiceImpl`

Current role:

* validates callback contract;
* validates references;
* validates proposal contract;
* persists every valid result as a new `ValidatableProposal`.

Important property:

* this is exactly consistent with ADR-006.

Implication:

* Story 0039 should not suppress proposal history here as its main strategy;
* otherwise repeated analysis history would be lost instead of merely prevented
  from becoming redundant trusted knowledge.

### `ValidationServiceImpl`

Current role:

* owns the acceptance / rejection decision transaction;
* marks proposal `ACCEPTED` or `REJECTED`;
* persists `Validation`;
* triggers promotion through `ProposalPromotionService`.

Strength:

* this is the first business boundary where the system knows:
  - a human has chosen `ACCEPTED`;
  - which proposal is about to become trusted knowledge.

Implication:

* this is the best place to introduce a duplicate-prevention business guard
  before trusted persistence occurs.

### `ProposalPromotionService`

Current role:

* dispatches accepted proposals to domain-specific promotion logic.

Strength:

* clean central handoff to trusted persistence.

Implication:

* a duplicate-prevention guard could be invoked either:
  - in `ValidationServiceImpl` before status transition / promotion;
  - or here just before dispatch.

Recommended boundary:

* prefer `ValidationServiceImpl` or a dedicated service called from it, so
  duplicate rejection remains a business conflict rather than a lower-level
  promotion exception.

### `InsightPromotionService`

Current role:

* creates the trusted `Insight`;
* creates `DERIVED_FROM` relation for accepted `ENRICHES`.

Strength:

* has direct access to the exact trusted write shape.

Limitation:

* this is too late for the preferred business behavior;
* a duplicate detected only here would likely appear as a technical promotion
  failure rather than a clean “acceptance conflicts with trusted duplicate
  policy” decision.

### `InsightRepository`

Current role:

* fetches trusted insights by project and coarse type/severity ordering.

Strength:

* enough repository surface already exists to perform a bounded project-scoped
  duplicate check.

Limitation:

* no dedicated duplicate-oriented query exists;
* no exact uniqueness constraint exists;
* no semantic lookup infrastructure exists.

Implication:

* Story 0039 should likely add a small duplicate-check query path or use
  project-scoped recent trusted knowledge with deterministic in-memory
  normalization, rather than introducing heavy search infrastructure.

## Current Duplicate-Control Gaps

### 1. Proposal history is preserved, but trusted writes are not screened

Today:

* valid AI output becomes `ValidatableProposal`;
* accepted proposal becomes trusted `Insight`;
* nothing checks whether that trusted `Insight` is semantically redundant with
  already accepted trusted knowledge.

### 2. Upstream duplicate reduction is intent-specific

Architecture analysis now has bounded trusted context and delta semantics.

But:

* it is not yet a repository-wide safeguard;
* it does not protect against missed model behavior;
* it does not protect validation of old or manually reviewed proposals.

### 3. Exact and semantic duplicate handling are not yet separated in code

ADR-051 requires:

* harder enforcement for obvious exact duplicates;
* more cautious handling for strong semantic near-duplicates.

Current code has no such distinction for trusted insights.

## Recommended Direction

### 1. Keep proposal persistence behavior intact

Do **not** make `AiTaskResultServiceImpl` the main suppression point for Story
0039.

Reason:

* ADR-006 treats proposal history as valuable lifecycle history;
* duplicate trusted knowledge is the target problem, not the existence of
  repeated proposals.

### 2. Add a dedicated trusted-duplicate guard before promotion

Preferred shape:

* introduce a dedicated service responsible for evaluating whether an accepted
  `INSIGHT` proposal would create a forbidden trusted duplicate;
* invoke it from `ValidationServiceImpl` before final acceptance / promotion is
  persisted.

Why here:

* this is the clearest business boundary;
* failure can surface as a deterministic conflict;
* transaction semantics remain clean;
* proposal history is preserved because the proposal is still present even if
  acceptance is refused.

### 3. Start with exact duplicate hard-blocks

First implementation slice should prioritize:

* same project;
* same trusted-knowledge family;
* same semantic payload after normalization;
* no meaningful enrichment or historical distinction.

Likely comparison inputs:

* `sourceType`
* normalized title
* normalized summary / content
* normalized rationale
* proposal `deltaType`
* enrichment target identity when present

This is the safest hard-block candidate set.

### 4. Treat strong near-duplicates conservatively in V1

Near-duplicate enforcement has higher false-positive risk.

For Story 0039, the safest first slice is likely:

* exact duplicates = hard block
* strong near-duplicates = either:
  - explicit conflict only for a narrow, deterministic heuristic;
  - or documented warning / deferred handling if false-positive risk is too
    high

Repository analysis suggests not overcommitting to aggressive semantic blocking
in the first implementation unless tests prove it is stable.

### 5. Preserve legitimate enrichments

`ENRICHES` must remain valid when:

* the target insight is explicit;
* new content adds meaningful information;
* the proposal is not merely restating the target insight.

This likely requires duplicate checks to distinguish:

* exact restatement of the target insight
* versus genuine extension of the target insight

## Recommended Implementation Boundary

The cleanest boundary is:

1. `AiTaskResultServiceImpl`
   - keep proposal persistence behavior unchanged
2. `ValidationServiceImpl`
   - invoke a new trusted duplicate guard before acceptance / promotion
3. `ProposalPromotionService` / `InsightPromotionService`
   - remain focused on successful promotion, not primary duplicate policy

This keeps:

* analysis history intact;
* business conflict explicit;
* trusted persistence protected.

## Test Impact

Expected new or updated tests:

### Backend

* `ValidationServiceTest`
  - accepting an exact duplicate insight should fail with conflict
  - rejecting a proposal should remain unaffected
* `InsightPromotionServiceTest`
  - enrichments still promote correctly
  - exact restatement enrichments can be blocked before promotion
* duplicate-guard service tests
  - exact duplicate detection
  - cross-project isolation
  - non-duplicate acceptance
  - legitimate enrichment acceptance
* integration tests
  - repeated architecture-equivalent analysis does not create redundant trusted
    knowledge after acceptance

### AI-engine / contract side

Possibly none for the first strict downstream slice if the contract remains
unchanged.

Only add AI-engine changes if Story 0039 also expands upstream duplicate bias
beyond Story 0037’s current architecture behavior.

## Risks

### 1. Over-blocking enrichments

If exact-match heuristics ignore target semantics, valid enrichments could be
rejected.

### 2. Under-blocking redundant `NEW`

If the duplicate guard only checks a too-small subset of trusted knowledge,
redundant accepted insights may still pass.

### 3. Wrong boundary

If duplicate prevention is implemented too late in promotion, failures may
appear as technical exceptions rather than clear validation conflicts.

### 4. Scope drift

If Story 0039 tries to solve full semantic deduplication, contradiction, and
legacy cleanup simultaneously, the Story will become too large and risky.

## Recommendation

Proceed with Story 0039 as a focused slice:

* preserve proposal persistence;
* add a business-level trusted duplicate guard before promotion;
* hard-block obvious exact duplicates;
* keep semantic near-duplicate handling narrow and test-driven;
* preserve legitimate `ENRICHES` behavior.
