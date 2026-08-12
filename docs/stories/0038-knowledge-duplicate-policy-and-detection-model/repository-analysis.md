# Story 0038 — Knowledge Duplicate Policy And Detection Model — Repository Analysis

## Purpose

Read-only analysis of how DevLog currently handles duplicate-like behavior
across evidence selection, AI proposal generation, validation, and trusted
knowledge persistence in order to define a coherent duplicate policy.

## Relevant Components

### `ADR-006`
- Proposal history is immutable once accepted or rejected.
- A new analysis with a new interpretation creates a new proposal.
- **Implication**: duplication in `ValidatableProposal` history is not
  automatically a bug. Some repetition is structurally acceptable there.

### `ADR-049`
- Promotion into trusted knowledge should preserve semantic richness.
- **Implication**: richer trusted knowledge increases the system’s ability to
  compare semantic equivalence later, but does not itself define duplicate
  policy.

### `ADR-050`
- Existing trusted knowledge may become bounded input to new analyses.
- Repeated analysis should maximize marginal information value.
- **Implication**: duplicate prevention should primarily reduce redundant
  trusted knowledge, not erase proposal history.

### `KnowledgeSelectionServiceImpl`
- deterministic ranking and bounded selection
- fact-content deduplication
- bounded existing trusted architecture knowledge selection in Story 0037
- **Implication**: DevLog already has prevention-like mechanics upstream, but
  they are partial and intent-specific.

### `BudgetedDiverseEvidenceSelector`
- deduplicates repository evidence candidates
- **Implication**: evidence deduplication already exists, but only at repository
  context level.

### `AiTaskResultServiceImpl`
- duplicate terminal callback acknowledgement
- persists every valid proposal as a new `ValidatableProposal`
- **Implication**: callback idempotence exists, semantic duplicate suppression
  does not.

### `AiProposalContractValidator`
- rejects duplicate engineering-event proposals within one result set
- validates architecture enrichment targets in Story 0037
- **Implication**: DevLog already accepts Core-side duplicate prevention for at
  least one proposal family. The idea is architecturally compatible.

### `ValidationServiceImpl` + `ProposalPromotionService` + `InsightPromotionService`
- proposal acceptance and promotion are atomic
- accepted insight proposals create trusted `Insight` records
- Story 0037 accepted enrichments also create a relation trace
- **Implication**: trusted-knowledge duplication is currently easiest to stop
  before or at promotion, not after.

### Trusted `Insight` persistence
- no uniqueness constraint currently prevents semantically equivalent trusted
  `Insight` rows
- older trusted insights may lack `sourceType`
- **Implication**: exact DB constraints are insufficient for the real problem,
  which is mostly semantic rather than purely syntactic.

## Current Duplicate Landscape

DevLog currently has at least four distinct duplicate notions:

### 1. Technical duplicate execution
- repeated callback
- repeated external submission acknowledgement
- duplicate request keys / digests in some subsystems
- **Status**: already handled in several places

### 2. Evidence duplicate
- same repository evidence candidate
- same fact content
- same commit-diff evidence path
- **Status**: already partially handled upstream

### 3. Proposal duplicate
- two proposals with equivalent semantic content
- same result set duplicate
- same analysis repeated later with similar output
- **Status**:
  - partially handled for engineering events within a single result set
  - otherwise mostly allowed

### 4. Trusted knowledge duplicate
- two accepted `Insight` records with effectively the same meaning
- **Status**: no explicit global policy yet

## Policy Question

The central policy question is not:

> Can DevLog ever contain repeated semantic content?

It is:

> Where is repeated semantic content acceptable, and where does it become a
> defect?

## Findings

### 1. Proposal history should tolerate more duplication than trusted knowledge

Because ADR-006 preserves immutable proposal history, repeated or competing
proposals can be legitimate records of analysis history.

Therefore:

* duplicate-like `ValidatableProposal` history is not automatically wrong
* duplicate-like trusted `Insight` persistence is much more problematic

### 2. Human-only duplicate control is a weak boundary

Relying primarily on the validator to “notice duplicates” would:

* make quality depend on vigilance rather than system behavior;
* produce inconsistent outcomes across users and sessions;
* undermine the value of incremental knowledge evolution.

Human review should remain a safeguard and arbiter for ambiguous cases, not the
primary duplicate-control mechanism.

### 3. Upstream prevention and downstream safeguards solve different problems

#### Upstream
- prevents the AI from reproposing redundant knowledge in the first place
- reduces noise and human review load

#### Downstream
- catches obvious misses before trusted persistence
- protects the DB from continued degradation

These are complementary, not interchangeable.

### 4. Exact duplicates and semantic near-duplicates should not be treated identically

Exact duplicates are easier to enforce against.

Near-duplicates require heuristics and carry a higher false-positive risk.

This suggests a graduated policy:

* exact duplicate => stricter control
* strong near-duplicate => warning or soft enforcement
* ambiguous semantic overlap => human review

### 5. Existing DB duplicates should be treated as migration debt, not target behavior

The presence of duplicates in the current DB can be understandable in dev.

But they should be considered:

* transitional debt
* not an acceptable steady-state target

## Recommended Policy Direction

### Acceptable

1. repeated proposal history when produced by distinct analyses or lifecycle
   steps
2. rejected and accepted proposals that express similar ideas but preserve
   history
3. trusted knowledge that is intentionally distinct because it:
   * enriches prior knowledge
   * replaces prior knowledge through a future lifecycle
   * represents historical truth distinct from current truth

### Not acceptable as steady state

1. exact trusted `Insight` duplicates in the same project
2. obvious semantic restatements accepted as separate trusted knowledge when no
   historical or lifecycle distinction exists
3. repeated “new” knowledge that should have been:
   * `NO_SIGNIFICANT_DELTA`
   * `ENRICHES`
   * or blocked before promotion

## Recommended Enforcement Balance

### Upstream

Should be the primary line of defense.

Use:

* trusted knowledge context
* incremental-delta semantics
* no-proposal outcomes

to minimize redundant proposals.

### Downstream

Should be a protective safety net.

Initial recommended policy:

* hard-block obvious exact duplicate trusted knowledge
* warn or soft-block likely semantic near-duplicates depending on confidence
* preserve human override path only where ambiguity remains

### Human reviewer

Should:

* resolve ambiguous near-duplicate cases;
* not serve as the main duplicate detection mechanism.

## Minimum Comparison Signals For Follow-Up Stories

Later implementation should at least consider:

* project ID
* proposal / knowledge family
* normalized trusted knowledge type
* `sourceType`
* enrichment target identity
* title similarity
* summary / content similarity
* rationale overlap
* evidence overlap
* accepted relation context where relevant

## Risks

1. **Over-enforcement**
   - could block legitimate enrichments or historically distinct knowledge

2. **Under-enforcement**
   - leaves trusted knowledge degradation mostly unchanged

3. **Human-only policy**
   - cheap now, but poor long-term architecture

4. **DB-only uniqueness strategy**
   - too weak for semantic duplication

## Recommendation

Proceed with:

1. Story 0038 — define the policy and comparison model
2. Story 0039 — implement prevention and safeguards
3. Story 0040 — audit and remediate existing duplicate stock

The recommended answer to the original policy question is:

* duplicate trusted knowledge is **not** normal target behavior;
* existing duplicates in dev can be tolerated temporarily as debt;
* duplicate control should **not** rely primarily on the user;
* DevLog should use both:
  * upstream prevention
  * downstream safeguards
  with different responsibilities.
