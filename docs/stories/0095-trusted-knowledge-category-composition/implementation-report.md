# Implementation Report — Story 0095

## Branch

`feature/story-0095-trusted-knowledge-category-composition`
(base: ADR-063 acceptance commit `a4d35fb` on top of `main @ 04d0887`;
**note**: ADR-063 docs branch was not yet merged to origin/main when this
Story branched — the Story branch therefore contains the accepted ADR text
and should be PR-ordered after it.)

## Story

0095 — Project trusted knowledge into engineering context with category-aware
composition (first ADR-063 increment)

## Status

READY_FOR_COMMIT_APPROVAL

## Production Changes

1. **NEW** `repositorycontext/intelligence/IntentTerms.java` — shared
   deterministic term-extraction primitive (ranker-family split rule).
2. `projectcontext/RepositoryContextAdapter.java` — bounded Fact/Observation
   retrieval seam: latest comparable baseline Analysis → 200-row paged windows
   → intent-term scoring → top 8 facts / 6 observations into AnalysisContext;
   safe empty degradation without a baseline profile; two helpers made
   package-private for focused tests.
3. `repositorycontext/selection/BudgetedDiverseEvidenceSelector.java` —
   knowledge floor pass between diversity and ordinary selection:
   `floor = clamp(maxItems/10, 2, 8)` (=6 @60), knowledge kinds {INSIGHT,
   ENGINEERING_STORY, DECISION, ARTIFACT, MILESTONE, CHALLENGE,
   ENGINEERING_EVENT, FACT, OBSERVATION}, all existing gates respected,
   reason `SELECTED_BY_CATEGORY_FLOOR`.

No repository overloads were needed (paged variants already existed).
No MCP contract change; no other consumer touched.

## Test Results

| Suite | Result |
|---|---|
| KnowledgeFloorSelectionTest (new) | 5/5 PASS |
| RepositoryContextAdapterBoundedKnowledgeTest (new) | 6/6 PASS |
| BudgetedDiverseEvidenceSelectorTest (existing) | 4/4 PASS |
| Full backend `clean verify` | **931/931 PASS**, coverage gates met, BUILD SUCCESS |

Intermediate failures during development were test-design issues (kind-share
interplay, an unused stub), corrected before final green; production logic
unchanged after first compile.

## Invariant Verification

- [x] No unbounded loading — window verified at exactly PageRequest(0,200)
- [x] Floors never select below-minRelevance items (dedicated test)
- [x] Unused floor capacity returns to rank order (60/60 selected test)
- [x] Item+token budgets enforced with floors active
- [x] Git retains majority share (≥25 asserts; live 53/60)
- [x] Trust/provenance/time preserved on new candidates (fact:{id}, detectedAt,
      evidenceReferences, supportingFactIds)
- [x] Zero MCP schema change; zero Engineering Event/documentation changes
- [x] RepositoryContextEngine stays a composition consumer

## Runtime Validation

Redeploy: `docker compose up -d --build backend`, healthy, endpoint 200.
Same five benchmark intents executed pre- and post-deploy (see
engineering-report Before/After table). Knowledge items selected rose from
0–2 to 5–7 per intent at relevance 64–77 while Git held 53/60.
