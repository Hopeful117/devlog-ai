# Repository Analysis — Story 0095

## 1. DevLog self-use BEFORE (§4)

`get_engineering_context(devlog-ai, intent="Implement ADR-063 first increment:
project trusted knowledge into RepositoryContextEngine with category-aware
composition, including relevant Insights, Engineering Stories, bounded Facts
and Observations.")` — freshness STALE (repo `04d0887` vs baseline `491d0cf`),
60/238 selected, layers COMMIT_DIFF/GIT_HISTORY(+1 diversity pick); **zero**
Insights/Stories/Facts/Observations despite the intent naming them — the exact
defect this Story fixes. History search surfaced ADR-062/0092-0094 commits
usefully; prompt-level internals still required source inspection.

## 2. Baseline measurements at HEAD (§5/§7)

Five benchmark intents against the live stack (files archived under
`/tmp` during run; numbers below are the recorded results):

| Intent | cand | Git sel | INSIGHT | STORY | DECISION | FACT/OBS |
|---|---|---|---|---|---|---|
| history | 238 | 59 | 0 | 0 | 1 | 0 |
| architecture | 238 | 59 | 0 | 0 | 0 | 0 |
| recent-sync | 238 | 59 | 0 | 0 | 0 | 0 |
| persistence | 238 | 59 | 0 | 0 | 0 | 0 |
| decision-governance | 238 | 58+1src | 1 | 0 | 0 | 0 |

Candidate pool constant 238; knowledge ≤1 via diversity pick only.

## 3. Pipeline trace (§9, actual classes)

```
RepositoryContextAdapter.buildRepositoryContext(projectId, storyDescription,
    ProjectContextSnapshot)
  ├─ synthesizeAnalysisContext → AnalysisContext(facts=List.of(),
  │                                             observations=List.of(), …)
  ├─ insightRepository.findByProjectIdAndStatusIn…(ACTIVE)   ← trusted set
  └─ repositoryContextService.build = RepositoryContextEngine.build
       ├─ DeterministicContextIntelligence.plan (engineering-story-v1;
       │    minRelevance=35, strong=75, kindShare≤25%, minDiverseLayers=3)
       ├─ collectors: CurrentAnalysis(10) · DeterministicKnowledge(20 ·
       │    consumes analysisContext().facts()/observations() ⇒ today 0) ·
       │    GitHistory(30 ≤20) · CommitDiff(35 ≤50 paths) ·
       │    ProjectKnowledge(40 insights/stories/decisions/…) ·
       │    Structure(40 live scan)
       ├─ DeterministicEvidenceRanker (semantic intent terms + weights)
       ├─ BudgetedDiverseEvidenceSelector (diversity → ordinary rank;
       │    NO category floors ⇒ starvation site)
       └─ enrichers → EngineeringContextContractMapper (additive fields only)
```

## 4. Why Facts/Observations are lost (§10)

The adapter deliberately bypasses `AnalysisContextServiceImpl` (which loads
fact/observation repositories per persisted Analysis) because that path needs
a real Analysis id; the synthetic context has none. The seam chosen: the
snapshot already carries `latestProjectProfile()` whose `analysis()` IS a real
persisted baseline Analysis — use it as the bounded retrieval scope. No
behavior change when no profile exists (empty lists, as today).

## 5. Bounded strategy choice (§11/§12)

Selected: **bounded recent window + deterministic lexical scoring**.
- Window: paged query `PageRequest.of(0, 200)` on new derived overloads
  (`findByAnalysisIdOrderByDetectedAtDesc`, `…CreatedAtDesc` with Pageable) —
  worst case 400 rows/request for devlog-ai's 763-fact latest analysis; no
  unbounded findAll possible.
- Scoring: shared `IntentTerms.extract(objective)` (same split rule as
  existing term models: lowercase, `[a-z0-9]+`, length ≥3) matched against
  Fact.`content` / Observation.`content`; top-8 facts / top-6 observations by
  matches then recency.
- Rejected: LLM summarization (ADR-063 forbids prescribing it; deterministic
  suffices); aggregation/grouped projection (larger surface than V1 needs);
  loading all then filtering in memory beyond window (violates §11 hard cap).
- Provenance preserved end-to-end: collector emits `fact:{id}` references and
  detectedAt timestamps unchanged (§13 satisfied).

## 6. Composition change site (§19/§20/§21)

`BudgetedDiverseEvidenceSelector`: insert `selectKnowledgeFloor()` between
diversity and ordinary passes. Floor formula `clamp(budget/10, 2, 8)` — at
budget 60 ⇒ 6 (~10%): smallest reservation that guarantees knowledge
categories a real opportunity while leaving ≥54 slots to ranked Git coverage;
relative to budget, not investigation counts. Knowledge kinds set: INSIGHT,
ENGINEERING_STORY, DECISION, ARTIFACT, MILESTONE, CHALLENGE,
ENGINEERING_EVENT, FACT, OBSERVATION. Floor candidates must clear
minimumRelevance and kind allowance and both budgets — irrelevant knowledge is
never forced (§34). Reason string distinguishes floor selections for
measurability.

## 7. Intent sensitivity (§22/§35)

No new mechanism needed: the ranker's semantic-term scoring already
differentiates knowledge candidates per intent; today their scores die at the
budget, so intents looked identical. Floors make the differentiation visible.
Deterministic only.

## 8. Contract compatibility (§26)

Zero schema changes: FACT/OBSERVATION kinds and PROJECT_DOCUMENTATION/ADR/
ROADMAP/RELATED_SOURCE_CODE layers already exist in the contract; new evidence
reuses them additively.

## 9. Performance (§32)

Bounded window queries ×2 per request (200 rows each), single-shot, indexed by
analysis_id FK; no N+1 (collector iterates the pre-fetched lists).

## 10. Trust semantics (§14/§15/§17/§18)

Insights: ACTIVE-only (unchanged adapter query). Stories/decisions flow from
snapshot as before; floors change *selection*, not *trust*. Facts carry
detectedAt + `fact:{id}` identity; observations carry supporting-fact links.
No SUPERSEDED-flattening risk introduced (no document lifecycle modeled).
