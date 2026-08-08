# Repository Analysis

## Story Understanding

Story 0012 requests a deterministic improvement to the precision and explainability of the
`engineering-story-v1` Repository Context policy.

The requested behavior has three connected parts:

* rank Story terms according to their actual discriminating value instead of allowing vocabulary
  shared by most candidates to saturate semantic and guidance scores;
* prevent one repeated evidence category from filling nearly the entire context while retaining its
  strongest relevant candidates;
* expose enough candidate, selection, and preferred-layer diagnostics to distinguish collection
  absence from ranking/selection exclusion.

The objective is not to make DevLog understand source behavior or to manufacture missing ADR/Git/
diff evidence. It is to make the current bounded deterministic evidence more useful and to expose
why expected evidence is absent.

The Story explicitly excludes new collectors, source/configuration content extraction, symbols,
dependencies, embeddings, ingestion/freshness redesign, automatic project resolution, agent
behavior, Kiko/Engineering-Skills changes, persistence, and workflow changes. The later real
benchmark is also not part of implementation validation; Story 0012 only prepares a policy suitable
for observation in a subsequent Engineering Story.

## Repository Summary

The relevant implementation is entirely in the Java backend's `repositorycontext` and
`projectcontext` packages.

Repository Context follows the ADR-038 pipeline:

```text
collectors → ranker → selector → RepositoryContextEngine → RepositoryContext
```

ADR-039 adds deterministic Context Intelligence before that pipeline. Versioned Context Profiles
own ranking weights, preferred layers, diversity requirements, and internal selection strategy.
ADR-040 distinguishes transient Repository Evidence from validated Trusted Knowledge; both may be
ranked and selected, but balancing must not erase that distinction or imply that absent evidence
exists.

The externally consumed Engineering Story response is assembled synchronously by
`EngineeringStoryContextServiceImpl` and exposed by GET and JSON-body POST operations through
`EngineeringStoryContextController`. The response embeds `RepositoryContext` directly, so additive
diagnostic fields become part of the API serialization contract even though no new endpoint is
needed.

The current repository is healthy after Story 0011: the backend Quality Gate is clean, 379 tests
pass, JaCoCo satisfies the 80% bundle rule, and there is no inherited Sonar issue baseline that must
be tolerated by Story 0012.

The DevLog-first request for this Repository Analysis succeeded only through the canonical
Engineering-Skills adapter path. It reproduced the benchmark shape with 58 candidates and 58
selections, 51 `RELATED_SOURCE_CODE` selections including 40 `TEST_FILE` items, 5 validated
Insights, one current Analysis, one previous Analysis, 2,800 estimated tokens, and no ADR,
Git-history, commit-diff, project-documentation, or roadmap candidate. The installed-skill symlink
invocation still returned no output; that consumer adapter defect is outside this DevLog Story.

## Affected Modules

### Backend — Context Intelligence

Package:

`com.hopeful117.devlogai.repositorycontext.intelligence`

Relevant components:

* `ContextProfileDefinition` — immutable versioned profile definition containing criterion weights,
  preferred layers, minimum diversity, and token priority;
* `ContextPlan` — composed runtime plan consumed by ranking and selection;
* `DeterministicContextIntelligence` — owns the predefined profiles and their explanations;
* `DeterministicContextIntelligenceTest` — protects profile composition and the
  `engineering-story-v1` definition.

This module is involved because ADR-039 assigns evidence priorities, diversity, and token allocation
strategy to Context Profiles. If concentration policy is configurable per profile, its immutable
definition and composed plan belong here. The current profile does not express a category policy.

### Backend — Evidence Ranking

Package:

`com.hopeful117.devlogai.repositorycontext.ranking`

Relevant component:

* `DeterministicEvidenceRanker` — calculates semantic, architectural, historical, recency,
  confidence, and guidance criteria and sorts candidates deterministically.

The ranker receives the complete candidate list in `rank(List<RepositoryEvidence>, ContextRequest)`
but currently builds semantic terms separately for every candidate. Terms are normalized to unique
lowercase alphanumeric strings of at least three characters. Each substring match contributes 25
points up to 100, regardless of how many repository candidates contain that term.

This ownership permits a deterministic corpus-aware query model: term frequency across the already
collected candidate set can identify non-discriminating vocabulary without project-specific or
Story-specific stopwords. The exact scoring formula is a planning concern, but it belongs in the
ranker and must produce a new explicit policy version if ranking semantics change.

There is currently no dedicated ranker test class. Ranking is exercised indirectly through
`RepositoryContextServiceTest` and Context Intelligence tests, which is insufficient for term-
frequency, tie-breaking, and explanation edge cases required by this Story.

### Backend — Evidence Selection

Package:

`com.hopeful117.devlogai.repositorycontext.selection`

Relevant components:

* `EvidenceSelector` and `SelectionResult` — selector contract;
* `BudgetedDiverseEvidenceSelector` — reference deduplication, diversity-first selection, global
  item/token enforcement, and per-candidate decisions.

The selector first walks preferred layers until the minimum number of represented layers is met,
then walks the complete ranked list and selects every candidate that fits the remaining global
budgets. It has no layer/kind/family concentration policy. With 58 candidates under a 60-item budget,
all Story 0009-shaped candidates are selected.

Selection decisions currently expose only reference, selected flag, reason, score, and token
estimate. Every selected item uses `SELECTED_BY_RANK_AND_DIVERSITY`, even when it was selected by the
ordinary rank-fill pass. Exclusions distinguish item budget, token budget, and duplicate reference,
but not relevance or category concentration. Deduplication is internal, so raw candidate counts and
decision counts can also diverge when duplicate references exist.

There is no dedicated selector test class. Existing service tests cover global item/token budgets
and multi-layer assembly but not equal scores, duplicate accounting, category concentration, sparse
profiles, or exact selection-phase reasons.

### Backend — Repository Context Assembly and Contract

Package:

`com.hopeful117.devlogai.repositorycontext`

Relevant components:

* `RepositoryContextEngine` — collects, ranks, selects, sorts selected evidence, calculates selected
  counts by layer, warnings, truncation, and digest;
* `RepositoryContext` — immutable serialized response containing evidence, selected layer counts,
  budgets, candidate/discarded totals, decisions, warnings, and digest;
* `RepositoryEvidence` — immutable evidence, score, provenance, extraction metadata, token estimate,
  and ranking explanations;
* `ContextRequest` — transports the plan and budgets through ranking/selection;
* `RepositoryContextServiceTest` — current engine-level deterministic/budget coverage.

`RepositoryContextEngine` has the raw candidates, ranked candidates, selected evidence, active plan,
and decisions required to calculate layer/kind distributions and preferred-layer availability. It
currently exposes only `selectedByLayer`. Warnings are unstructured strings and contain only global
budget application and summary truncation.

The digest includes selected evidence, selected layer counts, budgets, token use, decisions, and
warnings. New diagnostics and policy versions must participate consistently if they affect the
meaning of the returned context. The current digest is already sensitive to evidence timestamps;
the benchmark's request-time digest variability is real but outside this Story unless a narrow
contract dependency is discovered during planning.

### Backend — Engineering Story Context API

Package:

`com.hopeful117.devlogai.projectcontext`

Relevant components:

* `EngineeringStoryContext` — embeds `RepositoryContext`;
* `EngineeringStoryContextController` — exposes GET compatibility and body-based POST;
* `EngineeringStoryContextServiceImpl` and `RepositoryContextAdapter` — build the project snapshot,
  synthetic Analysis context, full Story Intent, Guidance, and Repository Context;
* `EngineeringStoryContextControllerWebMvcTest`, `EngineeringStoryContextServiceTest`, and
  `RepositoryContextAdapterTest` — protect transport and assembly.

No endpoint, request, or service-flow change is required. Additive Repository Context diagnostics
must remain serializable through both existing operations. The full Story is already transmitted in
the POST body and used as Intent objective and Guidance input.

### Collectors

All `RepositoryContextCollector` implementations are adjacent but not expected to change.

The benchmark absence of ADR/Git/diff candidates is a collector/data-availability observation, not
a selector defect. Story 0012 may count and diagnose those candidates but must not alter collectors,
repository ingestion, or project data to make a preferred layer appear.

## Existing Implementation

### Existing behavior

* `DeterministicContextIntelligence` defines seven predefined versioned profiles.
* `engineering-story-v1` uses semantic 15, architectural 15, historical 25, recency 20, confidence
  20, and guidance 5 weights.
* Its preferred layers are `RELATED_SOURCE_CODE`, `GIT_HISTORY`, `COMMIT_DIFF`, `ADR`,
  `PROJECT_DOCUMENTATION`, and `ROADMAP`, with minimum diversity 3.
* `DeterministicEvidenceRanker` scores each candidate across six criteria under policy
  `multi-criteria-v1`.
* Semantic relevance uses all normalized Intent ID/objective terms and substring matching against
  kind, summary, and originating file.
* Guidance relevance uses priorities, focus, and output context against the summary.
* Both semantic and guidance scores increase by 25 for every matching term and cap at 100.
* Ranking order is final score descending, layer ordinal, then reference.
* `BudgetedDiverseEvidenceSelector` deduplicates by reference, attempts preferred-layer diversity,
  then fills the global item/token budget by rank.
* `RepositoryContextEngine` sorts the selected output by layer, score, and reference, exposes
  selected layer counts, produces selection decisions, and hashes the explainable result.
* `RepositoryContext` is fully exposed through Engineering Story Context.

### Missing behavior

* No candidate-corpus frequency affects semantic or guidance scoring.
* No reusable generic-term/discrimination policy exists.
* No per-profile category concentration policy exists.
* No candidate or selected counts by evidence kind/family are exposed.
* No candidate counts by layer are exposed.
* Preferred layers with zero candidates are not diagnosed.
* Selection decisions cannot distinguish diversity selection from ordinary rank-fill selection.
* No exclusion reason exists for relevance or category concentration.
* Excluded candidates' layer/kind are not directly present in `SelectionDecision`, limiting consumer
  interpretation when the evidence itself is not selected.
* No dedicated ranker or selector unit tests exercise Story 0012 behavior.

### Behavior that must remain unchanged

* collectors and their candidate evidence/provenance;
* Repository Evidence versus Trusted Knowledge distinction;
* global maximum item/token/history/summary budgets;
* diversity-first selection where eligible candidates exist;
* immutable evidence, ranking criteria, provenance, collector metadata, and references;
* deterministic ordering and digest generation;
* GET and body-based POST Engineering Story Context operations;
* full Story transmission into Intent and Guidance;
* API error contract and optional Kiko fallback behavior;
* persistence, AI interpretation, validation, and Human Approval workflows.

No database entity or repository is directly affected. Candidate/selection diagnostics are computed
per request from the existing immutable pipeline state.

## Relevant Documentation

* `README.md` — current Repository Context capability, Engineering Story Context API, trust model,
  runtime, and validation contract.
* `docs/decisions/ADR-038.md` — deterministic Repository Context Engine, ranking, selection,
  diversity, explainability, traceability, and budget ownership.
* `docs/decisions/ADR-039.md` — deterministic Context Intelligence, versioned predefined profiles,
  multi-criteria scoring, diversity, and explainability ownership.
* `docs/decisions/ADR-040.md` — separation of Repository Evidence from validated Trusted Knowledge.
* `docs/stories/0012-engineering-story-evidence-precision/story.md` — approved investigation scope
  and benchmark-derived acceptance contract.
* `/tmp/devlog-story-0009-benchmark/benchmark-summary.md` — disposable observational benchmark used
  to verify the evidence distribution; it remains outside the repository and is not a Story
  artifact.
* Engineering-Skills `engineering-story/references/devlog-context.md` — consumer trust, transport,
  and fallback contract used during this Repository Analysis.

No repository-level `AGENTS.md` or DevLog-specific workflow prompt set exists in the current tree.

## Constraints

* ADR-038 requires collection, ranking, selection, budgeting, and context construction to remain
  deterministic, bounded, explainable, and traceable.
* ADR-039 assigns weighting, diversity, and evidence-selection strategy to predefined versioned
  profiles, not runtime user configuration.
* A change to ranking semantics must not continue to identify itself as
  `multi-criteria-v1`; policy/version explanations must remain truthful.
* Shared Context Profiles must retain compatible behavior unless explicitly versioned and tested.
* Category policy must be generic. A selector branch that special-cases only `TEST_FILE` or port
  vocabulary would violate the Story.
* Strongly relevant evidence must be eligible beyond ordinary concentration where the approved
  policy defines that behavior; relevance must be calculated before concentration selection.
* Candidate absence and selector exclusion are distinct states and must remain distinguishable.
* A missing preferred layer is non-fatal and must not create synthetic evidence.
* Selection decisions must remain one deterministic authoritative outcome per deduplicated
  candidate; raw-versus-deduplicated counts must be defined consistently.
* Additive Repository Context fields affect the public JSON response and therefore require WebMvc/
  serialization coverage even though endpoint compatibility is retained.
* Existing provenance, ranking criteria, explanations, token estimates, budgets, and digest
  traceability cannot be weakened.
* No persistence or migration is justified: all required diagnostics derive from current request
  state.
* No frontend, AI Engine, Docker, Kiko, Engineering-Skills, or workflow change belongs to this
  Story.
* The ignored `.env` token may be used for authenticated Sonar validation but must never be printed
  or tracked.
* Story 0011 established a zero-issue Sonar baseline; Story 0012 must leave zero new unresolved
  issue and pass the existing Quality Gate and JaCoCo threshold.

The proposed behavior is an implementation evolution explicitly anticipated by ADR-038 and ADR-039:
ranking strategies may evolve independently and predefined profiles own diversity/selection policy.
No new ADR is required if implementation versions the changed ranking/selection policy and preserves
those ownership boundaries. A new ADR would be required only if planning moves policy outside
Context Intelligence, introduces runtime configurability, or changes the deterministic selection
principle.

## Risks

### Corpus-aware ranking may over-penalize valid cross-cutting terms

A term that appears in many candidate paths can still be central to a broad Story. Frequency must
reduce non-discriminating contribution without making common but explicitly important concepts
impossible to rank. Dedicated ranker tests are required because current coverage is indirect.

### Category limits may discard legitimately broad evidence

An unconditional low per-kind cap can hide a large but valid source/test impact surface. Profile
policy needs a deterministic relationship between ordinary concentration, strong relevance, and
global budgets. This is more subtle than simply taking the first N tests.

### Profile composition can make concentration ambiguous

`ContextPlan` composes multiple profiles by averaging criterion weights and taking maximum diversity,
but no composition rule exists for category policy. If policy is added to shared profile records,
planning must define deterministic composition and compatible defaults rather than assume the
single-profile Engineering Story case.

### Public diagnostic additions can create compatibility or digest drift

`RepositoryContext` is serialized directly and constructed in tests. New fields are additive over
HTTP but require internal constructor migration and explicit digest decisions. Unordered map
serialization must not make otherwise identical contexts nondeterministic.

### Counts can be misleading around duplicate references

The engine currently counts raw collector candidates while the selector decides only over
deduplicated references. Candidate distributions, discarded totals, and decision counts require a
documented raw/deduplicated basis to avoid contradictory diagnostics.

### Exclusion-reason precedence can become inaccurate

A candidate may exceed category, item, and token constraints simultaneously. The selector needs a
stable precedence that reports the reason actually responsible at the point of decision rather than
recomputing an approximate reason from final totals.

### Missing-layer diagnostics could be mistaken for a collector regression

The benchmark contains no ADR/Git/diff candidate, but this may reflect project ingestion or stored
data rather than collector implementation. Diagnostics must state absence without assigning an
unsupported cause.

### The installed skill adapter defect is separate

The installed-symlink adapter again returned no output while the canonical Engineering-Skills path
succeeded. Expanding Story 0012 to fix the consumer would mix repository ownership and invalidate
the focused DevLog scope. It remains a separate Engineering-Skills follow-up.

## Open Questions

None.

The exact corpus-discrimination formula, category representation, strong-relevance overflow rule,
policy composition, diagnostic record shape, and reason precedence are implementation-design
decisions that can be made during planning within the Story's explicit deterministic and
compatibility constraints. No missing product requirement or external decision prevents that work.

## Recommendation

Ready for planning

This is a technical recommendation only. It does not approve this Repository Analysis or authorize
Implementation Planning.

## Implementation Readiness

The Story can be implemented using the current repository.

Ownership is clear across Context Intelligence, ranking, selection, engine assembly, and the
Engineering Story response. All required input data already exists in memory during a context
request. No database migration, external service, new collector, new dependency, or frontend change
is required.

The main missing test seams are explicit rather than blocking: dedicated ranker and selector tests
should be introduced because existing engine-level coverage cannot safely characterize corpus-aware
scoring, category concentration, equal-score decisions, and duplicate accounting in isolation.

No blocking contract, ownership, data, architecture, ADR conflict, or technical prerequisite is
missing.

## Approval Required

Repository Analysis completed.

Human approval required before Implementation Planning.

Awaiting explicit human approval.
