# Code Review — Story 0095

## Scope

Three production files (IntentTerms NEW, RepositoryContextAdapter,
BudgetedDiverseEvidenceSelector), two new test files, story artifacts.

## Review checklist (mission §43)

- **findAll / unbounded loading**: none — paged overloads (existing) with
  PageRequest(0,200); verified by dedicated test asserting the exact
  PageRequest.
- **N+1**: none — two single queries per request; collector iterates
  in-memory lists.
- **Arbitrary quota numbers**: floor derives from budget (`clamp(b/10,2,8)`),
  rationale documented in selector javadoc + plan §6; not investigation counts.
- **Irrelevant diversity filler**: floor pass enforces minRelevance; test
  proves weak INSIGHT (score 20) is rejected with reason preserved.
- **Duplicated retrieval logic**: IntentTerms extracts the previously
  triplicated split rule; other call sites left unchanged deliberately
  (no churn); history search untouched.
- **God-service drift**: engine gained no new responsibility — it consumes a
  seam inside its own adapter and its own selector.
- **Speculative abstractions / KnowledgeReference DTO / ContextPack rename**:
  none; reference semantics carried by existing `fact:{id}`-style references.
- **Trust metadata loss**: none — snapshots carry id/content/source/
  evidenceReferences/detectedAt + observation supporting-fact ids.
- **Visible/citable ambiguity**: fact references use the established
  `fact:{id}` syntax consistent with System B's allow-list prefix.
- **Engineering Event / documentation / freshness scope creep**: zero diffs
  outside the three files (+tests).
- **RAG/vector**: none.
- **Tests coupled to implementation details**: selector tests assert semantic
  outcomes (counts by kind, reasons, budget bounds), not internal call order;
  adapter tests verify the documented window contract via the public paged
  overload signature.

## Observations (non-blocking)

1. Floor pass iterates all candidates once per request — O(n) with n≈250;
   negligible.
2. `MAXIMUM_FACT/OBSERVATION_CANDIDATES` constants are Story-level policy;
   documented as such, not ADR numbers.
3. One pre-existing quirk: `snapshot.latestProjectProfile()` is a DTO whose
   `analysisId` may point at an archived analysis — acceptable: facts remain
   valid persisted knowledge of that baseline.

## Verdict

**APPROVED_FOR_COMMIT_APPROVAL.**
