# Investigation: Context Composition & Trusted Knowledge Projection

Investigation-only artifact — zero production changes.
Classification tags: **OBSERVED** / **INFERRED** / **PROPOSED** / **NOT VERIFIED**.

---

# Executive Summary

DevLog owns a rich knowledge estate (44,235 facts · 345 observations · 18
active insights · 21 engineering stories · 44 knowledge relations · full
commit/changed-path history · freshness checkpoints) but exposes it through
**five partially independent context mechanisms** with divergent retrieval,
selection, budgeting, projection and grounding semantics:

1. **RepositoryContextEngine** (get_engineering_context) — recency/git-
   dominated, intent-insensitive; starves trusted knowledge (18 insight + 21
   story candidates vs ≤3 selected); drops facts/observations by construction;
   projects freshness without ingestedRevision.
2. **KnowledgeSelectionService / SelectedKnowledge** (Understanding +
   Engineering Event) — grounding-closure selection over real Facts/
   Observations; strict allow-list citation; but visible-context syntax
   (bare changed-file paths) diverges from citable-reference syntax
   (`git:{sourceId}:{sha}:{path}`) — the exact Story-0094 failure.
3. **Documentation generation** — Insight-only input; cannot see any
   repository-derived knowledge that was not first promoted into prose.
4. **search_project_history** — deterministic lexical retrieval over commits +
   paths; *better historical recall than the context engine* for mechanism
   questions (17 vs 0 matches).
5. **MCP Resources** — detail-by-UUID navigation without discovery.

Central finding: these systems share almost no retrieval or composition
primitives. What should be shared — canonical knowledge references, trust
semantics, temporal signals, scoring/recall primitives, grounding identity —
is re-implemented (or omitted) per consumer. What should stay consumer-specific
— budgets, ranking weights, prompt projection — is conflated with retrieval.

**Recommended direction (PROPOSED)**: converge incrementally on **shared
retrieval + composition primitives with consumer-specific projections**
(Option C/D hybrid): canonical `KnowledgeReference` identity + trust/temporal
metadata; category-aware composition with floors; progressive expansion via
existing Resources/history search; ContextPack as a thin consumer-facing
contract later. **Not** a god-service: budgets, weights and prompt projection
remain consumer-owned.

Next Story (ONE, derived): *"Project trusted knowledge into engineering
context with category-aware composition"* — wire ACTIVE insights + stories +
facts/observation summaries as first-class candidates with per-category
floors in RepositoryContextEngine. Smallest step that fixes the worst
starvation while establishing the reference/trust primitive the future layer
needs.

Candidate ADR: **REQUIRED before implementation of the shared primitives**
("Engineering Context Retrieval & Composition Architecture").

---

# Investigation Question

(as above; capability parity assumed from prior investigations — payload parity
explicitly rejected)

# Prior Evidence

- Story 0093 — observed/ingested/baseline checkpoints; sync without AI.
- Human-vs-Agent investigation — empty-facts projection; git domination;
  workflow divergence; New Analysis chronic failures.
- Story 0094 — event-flow grounding hardening; post-fix live run cited bare
  changed-file paths rejected by validator.

# Current Context Consumers

| # | Consumer | Entry | Purpose | AI-facing | Human-facing |
|---|---|---|---|---|---|
| 1 | RepositoryContextEngine | EngineeringContextFacade | repository evidence for story prep | via MCP | via MCP |
| 2 | get_engineering_context contract | MCP tool | agent briefing | yes | indirectly |
| 3 | Engineering Event | understanding/event executions | commit-pair interpretation | yes (SelectedKnowledge) | cockpit |
| 4 | describe-project / Understanding | understanding-executions | project knowledge build | yes | proposals→review |
| 5 | Documentation generation | POST /deliverables | narrative deliverables | yes (insights only) | UI |
| 6 | Insight generation | analysis workflow | propose knowledge | yes | validation |
| 7 | MCP Resources (9) | resources/read | detail navigation | yes | no |
| 8 | search_project_history | MCP tool | lexical commit/path recall | yes | REST |

Full per-consumer mechanics were traced in the Human-vs-Agent investigation
and re-verified at `04d0887`; deltas recorded below.

# Current Context Architecture (ACTUAL)

```text
                    persisted knowledge (Postgres)
   commits │ changed_files │ facts(44k) │ observations │ insights(18)
   stories(21) │ decisions(1) │ relations(44) │ human inputs │ freshness
     │            │                │              │             │
     ▼            ▼                ▼              ▼             ▼
┌───────────────────────────────┐   ┌──────────────────────────────────┐
│ System A RepositoryContext-   │   │ System B KnowledgeSelection-     │
│ Engine                        │   │ Service (AnalysisContext)        │
│ 6 collectors · rank · budget  │   │ facts+obs closure · digest       │
│ =60 items/6k tokens           │   │ =SelectedKnowledge               │
└──────────┬────────────────────┘   └───────┬──────────────┬───────────┘
           ▼                                ▼              ▼
   get_engineering_context         Engineering Event    Understanding/
   (MCP; freshness re-projected,   (allow-list refs     proposal pipeline
    ingestedRevision dropped)      git:{src}:{sha}:path) (describe-project)
                                    ▲ Story0094 mismatch
┌──────────────────────────┐   ┌───────────────────────────────┐
│ System C Deliverables    │   │ System D search_project_      │
│ insights-only input      │   │ history (lexical commits+paths)│
└──────────────────────────┘   └───────────────────────────────┘
                     System E MCP Resources (by UUID)
```

Shared core across A–E: **none** beyond the underlying repositories. Pairwise:
A/B share AnalysisContext type only (A passes it EMPTY); B/C share insight
rows; D/E share nothing with others.

# Knowledge Inventory

| Category | State | Trust class | Notes |
|---|---|---|---|
| commits / changed files | PERSISTED | TECHNICAL_EVIDENCE | source-scoped, SHA-provenance |
| diff content | TRANSIENT (reconstructed on demand) | TECHNICAL_EVIDENCE | never persisted |
| Facts | PERSISTED (44,235; devlog-ai 39,317) | TECHNICAL_EVIDENCE | per-analysis, fingerprinted |
| Observations | PERSISTED (401; 345) | TECHNICAL_EVIDENCE | |
| Insights | PERSISTED (ACTIVE 18) | TRUSTED (human-promoted) | created only on acceptance |
| ValidatableProposals | PERSISTED | UNVALIDATED until accepted | pending=reviewable |
| accepted proposals | PERSISTED | TRUSTED | promoted into domain rows |
| knowledge relations | PERSISTED (44) | MIXED (entity-typed edges) | unconsumed by any context system |
| human context inputs | PERSISTED | HUMAN_AUTHORED | GOAL/CONSTRAINT/… notes |
| project profile | DERIVED snapshot | TECHNICAL_EVIDENCE | resolvedRevisions baseline |
| ADRs | REPOSITORY DOCUMENTS ONLY (+candidateAdrReferences metadata) | HUMAN_AUTHORED | no persistence/projection |
| Engineering Stories | PERSISTED (21) + repo docs | MIXED | registry ≠ markdown |
| roadmap | repo docs + ROADMAP evidence heuristic | HUMAN_AUTHORED | fact-path based |
| challenges | PERSISTED (open 2) | HUMAN_AUTHORED | unused in any context system |
| repository structure | TRANSIENT live scan @HEAD | TECHNICAL_EVIDENCE | hidden read-sync debt |
| analyses / understanding | PERSISTED | TECHNICAL_EVIDENCE | targetRevision provenance |
| freshness checkpoints | PERSISTED | SYSTEM metadata | 3-checkpoint model (0093) |

# Trust Model

Current implicit tiers: **TRUSTED** (insights, accepted proposals, human
inputs) > **TECHNICAL_EVIDENCE** (facts, observations, commits, structure) >
**UNVALIDATED** (pending proposals — excluded everywhere) > **HUMAN_AUTHORED
documents** (ADRs/stories/roadmap — outside persistence). Citation semantics
are consumer-specific: Engineering Event may cite facts/observations/evidence-
references only; Documentation cites nothing (prose-only insights);
get_engineering_context exposes evidence without citation semantics at all.
Answer: **no**, categories are neither presented nor citable equally today,
and they should not be — but trust must be an explicit, transportable property
(currently it is implied by which table a row lives in).

---

# RepositoryContextAdapter Trace (re-verified @04d0887)

Loads ProjectContextSnapshot via ProjectContextProviderImpl (project, notes,
validatedProposals L93, knowledgeRelations L141, humanContextInputs L155,
profile, stories, milestones…). Then `synthesizeAnalysisContext` builds
AnalysisContext with **facts = List.of(), observations = List.of()** (verified
again in source). Field disposition:

| Snapshot field | Disposition |
|---|---|
| project/analysis snapshots | TRANSFORMED into AnalysisContext |
| latestProjectProfile | LOADED_AND_USED (passed through) |
| facts / observations | **LOADED_BUT_NEVER_QUERIED — passed as empty** |
| validatedProposals | LOADED_BUT_UNUSED by engine collectors |
| knowledgeRelations | LOADED_BUT_UNUSED (no collector reads them) |
| humanContextInputs | TRANSFORMED_ELSEWHERE (ProjectContext notes; KnowledgeSelection uses them on System B path) |
| ACTIVE insights | TRANSFORMED into snapshot.validatedInsights → collector candidates (budget-starved) |

# RepositoryContextEngine Trace

Pipeline: ContextIntelligence.plan(engineering-story-v1) → 6 collectors →
DeterministicEvidenceRanker → BudgetedDiverseEvidenceSelector → enrichers.
Collector inputs: 4× persisted rows (commits≤20, changed-paths≤50,
knowledge lists, synthetic analysis), 1× live git scan, 1× empty. All operate
on **snapshot projections**, not raw persistence — duplication: path→layer
heuristics exist in BOTH DeterministicKnowledgeContextCollector and
CommitDiffEvidenceCollector; recency windows implemented independently.

# SelectedKnowledge / Engineering Event Trace

Snapshot sections (OBSERVED from task 94022367): selectedFacts,
selectedObservations, selectedInsights, selectedEngineeringEvents,
selectedHumanContextInputs, existingArchitectureKnowledge, repositoryContext,
evolutionContext{commitDiff{changedFiles(20), evidenceReferences(20 prefixed
git:{sourceId}:{sha}:{path})}}, diagnostics, digests.
Allow-lists (0094) derive ONLY from fact.evidenceReferences +
repositoryContext.evidence + commitDiff.evidenceReferences.
**Visibility/citability divergence**: `commitDiff.changedFiles[].newPath`
exposes bare paths (`backend/.../RepositorySyncJobExecutor.java`) while the
citable form of the same object is the prefixed reference. Nothing in the
prompt marks bare paths as non-citable ⇒ post-0094 rejection
(`evidenceReferences contains references absent from the allowed list:
['backend/src/main/java/...']`).
Classification: **CONTEXT_GROUNDING_MISMATCH + CONTRACT_AMBIGUITY**
(not MODEL_MISUSE; allow-list itself was semantically correct).

# Documentation Context Trace

Input = Insight rows only ({id,analysisId,type,severity,title,content}).
Repository-derived knowledge survives only if human-promoted into insight
prose. No freshness/revision metadata attached. Main limitation: **projection
boundary erases everything except promoted prose** (class PROJECTION_LOSS at
consumer contract).

# MCP Context Trace

get_engineering_context → contract drops ranking reasons/provenance/
diagnostics/ingestedRevision; freshness recomputed to STALE overriding
canonical PARTIALLY_FRESH when baseline≠HEAD. Resources: detail-by-UUID;
freshness resource alone exposes all three checkpoints correctly.

# search_project_history Trace

Lexical AND-token search over subject/body/changed paths; deterministic;
revision-current after 0093 sync. Recall superiority demonstrated above
(mechanism concepts 9–17 matches vs 0 in context). It is a genuine retrieval
primitive — currently orphaned from composition (results are not references
into any context system).

# Current Context Systems (hypothesis test)

| Pair | Relationship |
|---|---|
| A ↔ B | PARTIALLY_SHARED (AnalysisContext type; A feeds it empty) |
| A ↔ C | INDEPENDENT |
| A ↔ D | INDEPENDENT |
| B ↔ C | PARTIALLY_SHARED (insight rows as output→input) |
| B ↔ D | INDEPENDENT |
| D ↔ E | PARTIALLY_SHARED (resource URIs appear in D results) |
| A ↔ E | PARTIALLY_SHARED (evidence.resource fields) |

Five systems confirmed; no shared retrieval/scoring/reference primitives.

# Shared vs Independent Components

Duplicated semantically-equivalent logic: recency scoring, lexical intent-term
matching (engine ranker ≈ structure collector ≈ history search), path→category
mapping, budget/truncation, provenance fields. NOT duplicated: grounding
closure (B unique), diversity selection (A unique).

# Retrieval · Composition · Projection · Grounding · Budgeting (definitions found)

- RETRIEVAL: only D does real recall; A polls fixed windows; B selects from a
  pre-built closure; C retrieves nothing.
- COMPOSITION: A (rank+diversity+budget), B (closure+limits), C (none),
  D (rank by token overlap), E (n/a).
- PROJECTION: five different contracts (EngineeringEvidence,
  SelectedKnowledge JSON, DeliverableRequest insights, search result,
  Resource payloads).
- GROUNDING: explicit only in B (0094); A/E expose evidence without citation
  semantics; C has none.
- BUDGETING: A global 60 items/6k tokens; B per-section limits; others none.

# Freshness

Canonical 3-checkpoint model exists (0093) but is projected inconsistently:
context block recompute→STALE + drops ingestedRevision; deliverables attach
none; event context pins target/base commits (good); history search is
implicitly current (post-sync). Temporal signals available (committedAt,
detectedAt, createdAt, validation time, checkedAt) but unused as ranking
inputs beyond crude recency weights.

# Temporal Semantics

Event-time vs knowledge-time vs validation-time are distinguishable in the
schema but not modeled as first-class retrieval dimensions anywhere.
PROPOSED: temporal relevance as a pluggable signal in future composition.

# Knowledge Relations

44 persisted relations (typed edges among Challenge/Decision/Event/Insight).
No context mechanism traverses them. Commit↔Story linkage exists implicitly
(story base/target commits); Fact↔Observation↔proposal support edges exist as
foreign keys. Sufficient for reference-expansion later; graph traversal not
required now.

# Facts / Observations Gap (quantified §19)

Volume: devlog-ai owns 39,317 facts / 345 observations; latest analysis alone
=763 facts. Naïvely enabling them as engine candidates would grow the pool
from 238 to ~40k under a 60-item global budget ⇒ total starvation (every other
category → 0). Duplication risk with COMMIT_DIFF evidence is high (facts often
derive from same commits). Consumer value: highest for B-style consumers that
need grounded citations; for A-style briefing, aggregated/summarized forms or
top-K-by-relevance-with-floors are required — not raw inclusion.

# Loaded-but-Unused Knowledge

| Category | Why loaded | Planned? | Verdict |
|---|---|---|---|
| validatedProposals | snapshot completeness | unclear | accepted ones already promoted (duplication risk); pending must stay UNVALIDATED-excluded |
| knowledgeRelations | snapshot completeness | likely future | keep loaded; add reference-expansion before inclusion as evidence |
| humanContextInputs | used elsewhere | yes (System B + notes) | correct to exclude from A's git-heavy budget today |
| latestProjectProfile | used (baseline resolution) | yes | fine |

"Available" ≠ "must include" — each needs consumer-specific composition rules.

# ADR / Story Discoverability

ADRs: repository documents + candidateAdrReferences metadata only; no
persistence, no projection, no resource; discoverable solely via history
search paths. Stories: 21 persisted registry rows (base/target commits) +
markdown docs; collector candidates exist but were starved to 0 in all runs;
story resources are detail-by-UUID. Gap = DISCOVERY + PROJECTION, not storage.

# Budget Starvation Reproduction (measured)

Candidate pool constant at 238 across intents. Derived composition:

| Category | Candidates | Selected (5 runs) | Discarded |
|---|---:|---:|---:|
| COMMIT_DIFF | ~50 (cap) | 41–50 | 0–9 |
| GIT_HISTORY | 20 (cap) | 9–15 | 5–11 |
| Structure files+aggregates | ~40+~10 | ≤1 | ≥49 |
| ACTIVE insights | **18** | **0–1** | **17–18** |
| Engineering stories | **21** | **0** | **21** |
| Decisions | 1 | 0 | 1 |
| CURRENT_ANALYSIS | 1 | 0 | 1 |
| Facts/Observations | 0 (not loaded) | — | — |

Git candidates (~70) consume the 60-item budget before knowledge categories
place even one item in 3 of 5 runs. **BUDGET_STARVATION: PROVEN** with numbers.

# Intent Sensitivity Experiments

Five intents (history-intro / rationale / decisions / recent-sync /
persistence) produced near-identical outputs: same candidate count (238),
same dominant layers, top-relevance items identical across intents in 3/5
cases; only ±2 item shuffles differ. Selection is effectively
recency/category-driven; lexical intent influence is marginal.
INTENT_SENSITIVITY: EFFECTIVELY ABSENT in System A.

# Human vs MCP vs AI Comparison (two questions)

Q1 "How does DevLog handle Git repositories?" · Q2 "Why separate detection
from synchronization?"

| Knowledge need | Human docs(A′) | MCP ctx | Eng Event | History search |
|---|---|---|---|---|
| commits/paths | ✗ | ✓✓ | scoped ✓ | ✓✓ best recall |
| facts/obs | ✗ | ✗ | ✓ (scoped) | ✗ |
| trusted insights | ✓ only input | 0–1 | selectedInsights | ✗ |
| ADR content | ✗ | ✗ | candidate refs only | path-level |
| story registry | ✗ | ✗ | ✗ | ✓ (paths) |
| freshness | ✗ | partial(block wrong/resource right) | pinned revisions | implicit current |

Capability verdicts per cell backed by runtime traces from this + prior
investigations.

# Story 0094 Evidence-Reference Failure Analysis

See SelectedKnowledge trace. Classification: CONTEXT_GROUNDING_MISMATCH +
CONTRACT_AMBIGUITY. Fix direction (PROPOSED, not implemented): single canonical
reference syntax surfaced everywhere visible, or explicit NON_CITABLE markers
in prompt contract — decided at Story level, not here.

# Visible vs Citable Context

Current semantics: VISIBLE = entire prompt payload; CITABLE = only explicit
allow-lists (event flow) or nothing (docs/MCP-context flows). The distinction
is preserved *by validator* but communicated *only partially* by prompt
(0094 improved it for IDs; path-vs-reference syntax remains ambiguous).
Everything-visible-citable is undesirable (untrusted blobs); reliable
discrimination requires canonical reference syntax + explicit non-citable
marking.

# Capability Parity

Concrete definition (PROPOSED): every consumer can (a) obtain the knowledge
categories its task requires at needed granularity, (b) receive trust +
temporal + revision metadata with each item, (c) expand from any item to its
source (reference → detail/search), (d) know exactly what it may cite.
Payloads may differ arbitrarily. Progressive expansion via existing Resources +
history search satisfies (c) without payload inflation — superior to growing
one monolithic context.

# Progressive Context Expansion

Already half-present: evidence.resource URIs (A→E), commit-context resources,
history search. Missing: discovery of story/decision/event IDs from context,
relation traversal hints, ADR references as navigable links. With those,
initial pack + expansion becomes coherent without new infrastructure.

# ContextPack Assessment

**USEFUL_LATER** (not NEEDED today): current consumers' contracts already
carry most ContextPack ingredients separately (intent, digest, warnings,
evidence, grounding sets). A first-class ContextPack becomes valuable when ≥2
consumers share composition — i.e., AFTER primitive convergence begins.
Premature now; would ossify the wrong boundaries.

# Shared Context Layer Assessment

**YES, PARTIAL**: share retrieval primitives (lexical recall like D, windowed
polling, reference identity, trust/temporal metadata, scoring helpers) and
grounding identity; do NOT centralize budgets, ranking weights, prompt
projection, narrative formatting, or category requirements. Guardrail against
god-service: the shared layer returns *candidates with metadata*; consumers
own composition policy.

# Future RAG Compatibility

The proposed primitives define the exact seams hybrid retrieval plugs into:
canonical references → index keys; trust metadata → filter/rerank features;
temporal signals → recency features; relations → expansion edges. Vector/
lexical indexes remain REBUILDABLE PROJECTIONS over domain truth (ADR-aligned);
retrieval layer never becomes authoritative.

# Failure Mode Taxonomy

| Historical problem | Class |
|---|---|
| Mechanism classes invisible in context (self-use test) | MISSING_RETRIEVAL |
| Facts/Observations absent from MCP | PROJECTION_LOSS (+SELECTION gap) |
| Insights/stories starved (18/21→≤1/0) | BUDGET_STARVATION |
| Intent insensitivity across 5 questions | BAD_SELECTION |
| 0094 changed-file citation rejection | GROUNDING_MISMATCH/CONTRACT_AMBIGUITY |
| Generic documentation answers | CONSUMER_CONTRACT_LIMITATION |
| STALE override vs PARTIALLY_FRESH | FRESHNESS projection loss |
| No path from context item to ADR/story content | INSUFFICIENT_NAVIGATION |
| Quality vs availability distinction | failures above are AVAILABILITY failures; no observed case of good-context-poor-generation except generic docs (which was unavailability) |


# Architecture Options

| Criterion | A: patch consumers | B: enrich engine, reuse everywhere | C: shared primitives + consumer composition | D: full Context Retrieval+Composition layer |
|---|---|---|---|---|
| correctness of grounding | per-fix risk | medium | high | high |
| coupling | low now, drift later | high (one engine for all) | **medium-low** | high central |
| migration complexity | trivial each | large | incremental | largest |
| testability | scattered | centralized | primitives unit-testable | centralized |
| consumer flexibility | high | low | **high** | medium |
| future RAG fit | poor | partial | **excellent** | excellent |
| performance | n/a | single choke point | fine | fine |
| starvation fix speed | slow (N fixes) | fast but risky | **targeted** | delayed |

Option E discovered: none beyond these; "keep five systems" is Option A
at steady state.

# Recommended Target Architecture (PROPOSED)

```text
Consumer intent
   ▼
[Shared] KnowledgeReference registry: canonical id per item
         (commit/path/fact/observation/insight/story/adr-ref/relation)
         + trust tier + temporal stamps + revision provenance
   ▼
[Shared] Retrieval primitives: lexical recall (history-search class),
   windowed recency, relation expansion — return candidates+metadata
   ▼
[Consumer] Composition policy: required categories, floors/ceilings,
   weights, budget  →  selected set
   ▼
[Consumer] Projection + Grounding contract: consumer contract shape,
   citable vs visible marking via canonical references
   ▼
Expansion: references → Resources / history search / relations
```

# What Should Be Shared

canonical reference identity · trust tiers · temporal metadata · revision
provenance · lexical/recency retrieval primitives · grounding identity syntax.

# What Must Remain Consumer-Specific

budgets · ranking weights · category requirements/floors · prompt projection ·
citation allow-list construction · narrative formatting.

# Incremental Migration Strategy (derived)

1. Canonical KnowledgeReference + trust/temporal metadata primitive
   (foundation; no behavior change).
2. Fix trusted-knowledge projection in System A with category floors
   (next Story below).
3. Align freshness projection (stop STALE override; include ingestedRevision).
4. Introduce progressive-expansion links (story/decision/event discovery;
   ADR candidate refs navigable).
5. Migrate Engineering Event visibility/citability to canonical references.
6. Documentation composition upgrade (insights + referenced evidence).
7. Only then: hybrid retrieval indexes as rebuildable projections.

# Immediate Next Step

See Recommended Next Story.

# Candidate ADR

**REQUIRED** before steps 1–2 implementation:
"Engineering Context Retrieval & Composition Architecture" — durable decision:
shared primitives boundary, canonical reference identity, capability-parity
principle, what stays consumer-owned. (Not created here.)

# Recommended Next Engineering Story (ONE)

**"Project trusted knowledge into engineering context with category-aware
composition"**
Scope: RepositoryContextEngine only — register ACTIVE insights, engineering
stories and fact/observation *summaries* as first-class candidates carrying
trust/temporal/reference metadata; add per-category floors (e.g., min 3
knowledge-layer items when available) alongside the existing global budget;
map canonical reference fields through the contract. Improves the worst-proven
failure (starvation) while creating the first consumer of the shared reference
primitive — the seed of the target architecture.

# Risks

- Naïve facts inclusion ⇒ 40k-candidate explosion (measured) — must use
  summaries/top-K/floors.
- Category floors may evict useful git items for narrow questions — floors
  must be small and availability-aware.
- Canonical-reference churn across contracts — version the primitive.
- Shared-layer scope creep — enforced by the share/consumer-specific split.

# Open Questions

- Right summary granularity for fact/observation projection (per-analysis?
  per-theme?).
- Should pending proposals ever appear as UNVALIDATED-marked context? (ADR
  question.)
- ADR ingestion: persistence vs on-demand repository read at expansion time.
