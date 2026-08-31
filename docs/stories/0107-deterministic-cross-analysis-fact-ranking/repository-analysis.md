# Story 0107 — Repository Analysis

## Status

**IMPLEMENTATION_READY_FOR_HUMAN_REVIEW**

## Baseline

- Verified baseline SHA: `70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a`
- Implementation branch: `story/0107-deterministic-cross-analysis-fact-ranking`
- Governing investigation: `docs/investigations/story-0106-knowledge-collection-determinism-investigation.md`

## Current Ranking Boundary

`KnowledgeSelectionServiceImpl.select()` defines Fact ordering as:

```text
factScore + guidanceScore DESC
→ FactType name ASC
→ FactSnapshot.id UUID ASC
```

`FactSnapshot.id` is the UUID of a persisted `Fact` row. `Fact` belongs to one Analysis and receives its UUID from `@GeneratedValue(strategy = GenerationType.UUID)`. Equivalent collected Facts recreated in another Analysis therefore have different IDs.

When candidate count exceeds the `maximumFacts = 40` budget, same-score and same-type candidates can be selected differently solely because of their persistence UUID assignment.

## Available Stable Fact Identity

### Persisted fingerprint

`CollectedFact.create()` computes SHA-256 over:

```text
collector version
FactType
normalized content
sorted evidence references
resolved repository revision
```

The fingerprint is persisted in `Fact.fingerprint` and is deterministic and Analysis-independent for equivalent collection inputs.

### Availability at selection boundary

`AnalysisContext.FactSnapshot` currently contains:

```text
id
type
content
source
evidenceReferences
detectedAt
```

It does not contain the persisted fingerprint. Extending the snapshot would affect every constructor and would serialize the new field through `PromptProjection`, changing the model-facing payload unless additional transport exclusions were introduced. The fingerprint is also nullable for manually created Facts because `FactMapper` ignores it.

### Selected canonical ordering

The smallest safe ordering uses existing stable semantic fields already present at the selection boundary:

```text
source
→ content
→ lexicographically compared sorted evidence references
```

Fact type remains the preceding comparator dimension. These fields are Analysis-independent and contain no UUID or operational timestamp. If all comparator dimensions are equal, the Fact snapshots are semantically equivalent under the current selection model; `factContentKey(type + content)` already deduplicates them before discretionary selection can distinguish them.

This ordering is a deterministic tie-breaker, not a new relevance policy. Existing score and FactType ordering remain authoritative.

## Fingerprint Verdicts

```text
FACT_FINGERPRINT_EXISTS = YES
FACT_FINGERPRINT_PERSISTED = YES
FACT_FINGERPRINT_AVAILABLE_IN_FACT_SNAPSHOT = NO
FACT_FINGERPRINT_ANALYSIS_INDEPENDENT = YES
FACT_FINGERPRINT_SUITABLE_AS_TOTAL_TIEBREAKER = PARTIAL
```

The fingerprint is suitable for collector-created Facts but is nullable and unavailable at the selection boundary. Propagating it would broaden the change and model-facing projection. It is therefore not selected for this focused correction.

## Observation Comparator Assessment

Observation ordering also ends with `ObservationSnapshot.id`. Current deterministic observation production creates at most one Observation per rule, rule types distinguish current candidates before UUID ordering, and the deterministic engine produces six observations while the budget is 25. No current bounded same-score/same-type selection defect was demonstrated.

```text
OBSERVATION_EQUIVALENT_DEFECT = NO (for the current reachable lifecycle)
OBSERVATION_ACTION = NONE
```

The UUID usage is recorded but production scope is not expanded without a demonstrated selection-affecting defect.

## Closure And Projection Assessment

The Fact comparator is reused for required and discretionary Facts. Replacing only its final tie-breaker preserves:

- observation-to-Fact grounding closure by UUID membership
- Fact budget accounting
- existing scoring
- content deduplication
- repository context construction
- Semantic Sections
- PromptProjection fields
- grounding IDs and persistence traceability

## Alternatives Rejected

### Persisted fingerprint propagation

Rejected for this Story because it requires snapshot/projection changes, affects multiple constructor sites, risks changing prompt payloads, and needs null handling for manual Facts.

### `type + content` only

Rejected as incomplete because distinct Facts can share type/content while differing in source or evidence; comparator equality could then expose encounter order.

### UUID fallback

Rejected because it preserves the confirmed defect.

### Timestamp fallback

Rejected because timestamps are operational and Analysis-local.

## Test Strategy

Add a focused Knowledge Selection regression that:

1. creates more than 40 same-score/same-type Facts;
2. builds five candidate universes with identical semantic Facts and permuted UUID assignments;
3. executes real bounded selection;
4. compares selected Fact semantics, not UUIDs;
5. verifies all five semantic selections and orders are identical;
6. demonstrates a chosen permutation would differ under the prior UUID ordering.

## Scope Boundary

Expected implementation remains limited to:

- `KnowledgeSelectionServiceImpl.java`
- `KnowledgeSelectionServiceTest.java`
- Story 0107 lifecycle artifacts

No architectural decision is required.
