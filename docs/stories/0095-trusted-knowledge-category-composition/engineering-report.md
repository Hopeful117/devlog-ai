# Engineering Report — Story 0095

## Branch

`feature/story-0095-trusted-knowledge-category-composition` — based on
ADR-063 acceptance (`a4d35fb`, on top of `main @ 04d0887`). Unmerged; PR after
the ADR branch.

# Story Summary

First ADR-063 increment: RepositoryContextEngine now composes bounded,
intent-relevant trusted/project knowledge (ACTIVE Insights, Engineering
Stories, Decisions, bounded Facts/Observations) alongside Git evidence, using
availability-aware category floors — without contract changes, without
unbounded loading, and while remaining a consumer-specific composer.

# ADR-063 Compliance

Capability parity ✓ · retrieval/composition/projection/grounding/expansion
kept distinct ✓ · shared primitive extracted only where duplication existed
(IntentTerms) ✓ · consumer-owned composition preserved (floors live in the
consumer's selector) ✓ · bounded pre-pool Facts/Observations per the accepted
clarification (mechanism undecided, deterministic chosen) ✓ · trust tiers and
provenance survive ✓ · visible≠citable untouched ✓ · no ContextPack, no RAG,
no big-bang migration ✓.

# DevLog Effectiveness Before

Self-use intent naming Insights/Stories/Facts/Observations explicitly returned
only Git evidence + ≤1 knowledge item. History search helped for mechanism
history; prompt-level internals required source inspection (recorded in
repository-analysis §1).

# Baseline Measurements

Five intents × live stack: pool constant 238; selected Git 59–60/60; INSIGHT
0–1; STORY 0; DECISION 0–1; FACT/OBS 0 (structurally absent). Full table in
repository-analysis §2.

# Retrieval Strategy

Bounded recent window (200) over the baseline Analysis' Facts/Observations →
deterministic intent-term scoring (shared `IntentTerms`) → top 8 / top 6.
Deterministic-first per ADR-063; LLM summarization not used.

# Bounded Facts/Observations Strategy

Hard cap BEFORE candidate pool; verified at exactly PageRequest(0,200);
scoping by baseline analysis id; empty-baseline degradation to today's
behavior.

# Trust / Reference Semantics

Insights remain ACTIVE-only trusted set; facts keep `fact:{id}` identity +
evidenceReferences + detectedAt; observations keep supportingFactIds +
createdAt — consistent with System B's grounding syntax (Story-0094 class
ambiguity avoided).

# Category-Aware Composition

Floor pass in BudgetedDiverseEvidenceSelector: clamp(budget/10,2..8)=6 @60;
all existing gates enforced; unused capacity returns to rank order; reason
`SELECTED_BY_CATEGORY_FLOOR` makes reservations measurable.

# Intent Sensitivity

Knowledge composition now differs per intent (different insights/stories/facts
selected across the five benchmarks) because floor survivors are chosen by
intent-driven relevance instead of being budget-evicted. Deterministic term
matching only.

# Implementation

31345ed adapter seam · 9b0e760 selector floors · 0b62fd1 tests (+ story docs).

# Tests

New 11; targeted suites 15/15; full backend **931/931**, JaCoCo gates met,
BUILD SUCCESS. Pre-existing selector suite unchanged-green.

# Performance

+2 paged queries/request (≤200 rows each, FK-indexed); candidate pool grew
238→245–250; response still bounded at 60 items / token cap.

# Before / After Benchmark

| Intent | Git B→A | INSIGHT B→A | STORY B→A | FACT/OBS B→A |
|---|---|---|---|---|
| history | 59→53 | 0→5 | 0→1 | 0→0 |
| architecture | 59→53 | 0→3 | 0→3 | 0→1 |
| recent-sync | 59→53 | 0→4 | 0→1 | 0→1 |
| persistence | 59→53 | 0→3 | 0→2 | 0→1 |
| decision-gov | 58→53 | 1→3 | 0→2 | 0→1 |

Manual relevance inspection (§38): decision-gov returns the ADR Decision +
"Use of ADR" insight + proposal-review stories — genuinely on-topic.
architecture/persistence return documentation/architecture insights plus
related stories — relevant. recent-sync knowledge is the weakest fit
(generic documentation insights) — reported honestly; its primary evidence
remains Git recency.

# Live MCP Validation

Executed against the redeployed stack through the real
`get_engineering_context` endpoint (not unit fixtures); responses bounded,
additive, freshness block unchanged (known separate debt).

# DevLog Effectiveness After

The same self-use intent that previously returned zero knowledge now returns
5–7 relevant trusted/project items naming the exact categories requested.

# Self-Use Result

Question about this Story itself: composition surfaces trusted-knowledge
stories (duplicate-debt, maintenance workflows) as relevant neighborhood
evidence; Story-0095/ADR-063 artifacts themselves are NOT yet retrievable
because origin/main has not ingested this branch — requires merge + 0093 sync.
Honest limitation, expected by design.

# Remaining ADR-063 Migration Work

Freshness projection alignment · progressive-expansion links · Engineering
Event canonical-reference convergence · documentation adoption · hybrid
retrieval channels (per ADR order).

# Known Limitations

Observation candidates rarely win seats yet (low lexical overlap with current
intents); recent-sync knowledge relevance is mediocre; floors reserve slots
even when Git is genuinely the best evidence for an intent (mitigated by
relevance gate, still costs ~6 slots ceiling).

# Suggested Next Story

**"Align engineering-context freshness projection with canonical freshness
checkpoints"** — smallest high-value repair surfaced again during this Story's
live validation (STALE override + missing ingestedRevision misleads every MCP
consumer), independent of remaining ADR sequencing.
