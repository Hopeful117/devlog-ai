# High-Value Deterministic Engineering Relationships - Architecture Study

## Executive Summary

Stories 0123 and 0124 established two important boundaries:

- an explicit relationship is directional, typed, and projected only when both
  endpoints are available;
- relationship-aware admission is bounded, deterministic, one-hop, and shared
  by human and AI consumers.

The current runtime is semantically sparse because the observed
`INSIGHT --RESOLVES--> INSIGHT` edges are lifecycle-oriented rather than
architecture-explanatory. The repository already contains stronger signals,
but they do not all have the same trust or temporal semantics.

The smallest useful initial set is four categories, implemented as two
different kinds of association:

1. **Change association**: revision-scoped links from commits and Stories to
   changed files or bounded change windows.
2. **Support/evidence association**: grounded links from Facts, Observations,
   and accepted Insights to their canonical evidence.
3. **Governance/constraint association**: explicit links from ADRs or accepted
   Decisions to the artifact or Story they explicitly govern.
4. **Module dependency association**: revision-scoped links derived from
   sufficiently strong import/build/module evidence.

The recommendation is **OPTION_C_HYBRID**:

- retain `KnowledgeRelation` for durable, project-owned, human-validated
  semantic relationships;
- add a reconstructible repository-derived relationship projection for
  revision-scoped code, change, and evidence associations;
- do not create authoritative relations from semantic similarity, co-occurrence,
  confidence, or unvalidated AI output.

The next implementation Story should implement only **change association** as a
repository-derived projection. It gives immediate architecture-overview value,
is deterministic from existing Git data, and avoids prematurely introducing a
general code graph or new durable ontology.

## Existing Deterministic Signals

| Signal | Stable identity | Project ownership | Revision / temporal scope | Provenance | Deterministic relation potential | Current reach |
|---|---|---|---|---|---|---|
| Fact | `fact:{uuid}` | Through its Analysis and project | Analysis execution; `detectedAt`; evidence references may identify a revision | `source`, fingerprint, evidence references | Yes, for explicit evidence references; no for semantic meaning | `AnalysisContext`, `SelectedKnowledge`, grounding |
| Observation | `observation:{uuid}` | Through its Analysis and project | Analysis execution; rule execution time | `ruleId`, rule version, supporting Fact UUIDs | Yes, to supporting Facts; no causal relation beyond declared support | `AnalysisContext`, `SelectedKnowledge` |
| Insight | `insight:{uuid}` | Direct project and source Analysis | Created/validated time; source Analysis scope | Proposal/validation IDs, rationale, confidence, evidence references, source type | Yes, to explicit evidence and lifecycle provenance; interpretation remains trusted only after promotion | Active Insight retrieval, `SelectedKnowledge` |
| Decision | `decision:{uuid}` | Direct project | Created time; status/current applicability is separate | Proposal provenance, context, choice, rationale, consequences | Yes, to explicitly referenced artifacts; no automatic component governance | Project context and repository evidence |
| Challenge | `challenge:{uuid}` | Direct project | Created/status/resolution timestamps | Status, impact, resolution | Yes, to explicitly linked events/decisions; no automatic root-cause assertion | Project context and repository evidence |
| Engineering Story | `story:{uuid}` plus `(project, story number)` | Direct project | `(baseCommit, targetCommit]`, status and lifecycle timestamps | Story path, title, commits | Yes, to commits/files only when the window proves it | Project context, Story Context Analysis |
| Engineering Event | `event:{uuid}` | Direct project, Analysis, Source | Occurrence time, base/target commits | Proposal/validation, source, commit bounds | Yes, to its explicitly represented commits or change window | Project context and selected events |
| Commit | `git:{sourceId}:{sha}` | Through Source and Project | Commit timestamp, immutable SHA, parent SHAs | Author, message, parents, stats | Yes, to changed files and parents | Repository history and repository evidence |
| Parent commit | Commit SHA in parent list | Through owning Source/Project | Immutable graph ancestry | Parent SHA from Git | Yes, chronology and ancestry; not causal intent | History persistence and history search |
| ChangedFile / diff | `diff:{sha}:{path}` | Through Commit, Source, Project | Commit revision and path; change type | Old/new path, insertions/deletions, change type | Yes, commit-to-file and bounded Story-to-file association | Commit-diff repository evidence |
| Repository evidence | Canonical `git:`, `diff:`, `fact:`, `observation:`, etc. reference | Scope depends on evidence owner | Usually revision-scoped; exact metadata varies by kind | Layer, kind, reference, summary, provenance, scores | Yes when association is explicit and identity is canonical | `EngineeringContext`, repository context |
| Source file path | Normalized path within `(source, revision)` | Through repository Source | Resolved `RepositoryRevisionScope` | Path, source, revision | Yes, containment and change association | Repository structure and diff evidence |
| Symbol | Owning type/name plus source location; canonical evidence reference remains required | Through source file and repository scope | Resolved revision | File, line, declaration metadata, signature | Yes for containment; dependency/call only with strong extractor evidence | Repository enrichment and `EngineeringEvidence` |
| Module/package | Deterministic path/package identity where known | Through repository source | Revision-scoped for structure | Directory/package/build metadata | Yes for containment and dependency if import/build evidence exists | Repository structure; not currently a relation graph |
| ADR / Story document | Canonical revision-aware document identity, e.g. `document:{sourceId}:{path}@{revision}` | Through repository Source | Document revision | Path, document type, status, explicit references | Yes for explicit references; no truth assertion about prose | Repository document evidence |
| Existing KnowledgeRelation | UUID; typed `(sourceType, sourceId) -> (targetType, targetId)` | Direct project | `createdAt`; no revision field | Description and creation time, but no independent description provenance | Yes as explicit durable relation; no automatic endpoint integrity | ProjectContext, AnalysisContext, SelectedKnowledge, prompt highlights |
| Enrichment metadata | Evidence layer, score, ranking reasons, extraction metadata | Through repository evidence | Repository revision | Collector and extractor metadata | Metadata only; must not become semantic relation truth | Repository context and EngineeringContext |

Important current boundaries:

- Java Core constructs and authorizes the context. Python cannot reconstruct
  relations or access the repository independently.
- Facts and Observations already have explicit support structure. Adding a
  second relation representation must not create contradictory grounding
  semantics.
- `KnowledgeRelation` currently stores a project, typed UUID endpoints,
  relation type, description, and `createdAt`; it has no revision, source,
  validity interval, or endpoint foreign keys.
- Project context loads at most 50 persisted relations. Story 0124 admits only
  bounded Insight and Engineering Event endpoints and retains strict projection
  closure.
- The prompt projection exposes relation type and endpoint identity only;
  descriptions and diagnostics are not AI-facing knowledge.

## Current Relationship Model

The current path is:

```text
KnowledgeRelation persistence
  -> ProjectContextSnapshot.KnowledgeRelationSnapshot
  -> AnalysisContext
  -> KnowledgeSelectionService
  -> SelectedKnowledge
  -> relationshipHighlights projection
  -> PromptRequest
```

`KnowledgeRelation` is a polymorphic project-owned edge. Its endpoint is a
type plus UUID, with supported entity types `CHALLENGE`, `DECISION`,
`ENGINEERING_EVENT`, and `INSIGHT`. Current relation types include `RESOLVES`,
`CAUSED_BY`, `RELATES_TO`, `DERIVED_FROM`, `ADDRESSES`, and `INFORMED_BY`.

The model fits explicit durable domain relationships, but has material limits:

- endpoint UUIDs have no database foreign keys;
- creation does not prove endpoint existence or same-project ownership;
- relations have no revision identity or validity interval;
- descriptions have no separately represented provenance or trust;
- the prompt projection is intentionally thin and closure-based;
- the human UI has no first-class relation navigation;
- project context and some selected-evidence surfaces drop relations;
- repository-derived structure is not a durable relation source.

Story 0123 correctly rejected inference from text, co-occurrence, packages,
commits, or Story identity. Story 0124 correctly made endpoint admission
bounded and consumer-neutral. Those constraints should remain unchanged.

## Fact vs Interpretation Boundary

| Candidate assertion | Core may assert as fact? | Boundary |
|---|---|---|
| Commit `C` changes file `F` | **Yes** | Directly established by persisted `ChangedFile` / Git diff data at `C`. |
| Commit `C` has parent `P` | **Yes** | Directly established by the immutable commit parent list. |
| Story `S` spans commits in `(base, target]` | **Yes, when bounds and parent traversal are valid** | The commit window is deterministic; incomplete bounds must fail closed. |
| Story `S` changes file `F` | **Yes, conditionally** | Only when `F` is present in a commit diff inside the proven Story window. This is an association, not proof of intent. |
| Engineering Event `E` spans a change window | **Yes, when its commit bounds are explicit and valid** | Preserve event and revision provenance. |
| Fact `F` is supported by evidence `R` | **Yes** | Only from the Fact's explicit evidence references and canonical allow-list. |
| Observation `O` is supported by Fact `F` | **Yes** | Only from `supportingFactIds`; do not infer causality. |
| Insight `I` is grounded in evidence `R` | **Yes, after accepted trusted provenance is checked** | Evidence reference is deterministic; the Insight's conclusion remains its authored interpretation. |
| ADR `A` governs component `B` | **Only with explicit mapping/reference** | An exact ADR reference proves reference, not that the ADR applies to every nearby component. |
| Decision `D` constrains subsystem `B` | **Only with explicit mapping/reference** | Choice/rationale prose alone does not establish a machine relation. |
| Component `A` depends on component `B` | **Conditionally** | Safe only for a defined import/build/module dependency extractor with a precise rule. Package proximity is insufficient. |
| Controller `A` calls service `B` | **Conditionally, high bar** | Requires strong static wiring/call evidence; do not claim from naming conventions or type co-occurrence. |
| Component `A` owns responsibility `R` | **No, absent explicit metadata** | Directory/package containment does not prove responsibility. |
| Change `X` violates ADR `A` | **No** | This is an interpretation requiring policy and human/AI reasoning. |
| Component `A` is architecturally important | **No** | Importance is an interpretation, not a deterministic edge. |
| Two objects are related because they occur in the same Story/context | **No** | Co-occurrence is at most `POSSIBLE_RELEVANCE`. |

The boundary is consistent with ADR-067: factual extraction, AI
interpretation, and recommendation must remain separate. A deterministic
association may be supplied as evidence for interpretation, but must not be
worded or typed as a stronger semantic claim than its extractor proves.

## Candidate Relation Categories

Classification uses the required labels. `EXISTING_EXPLICIT_RELATION` means
the category is already represented as a persisted or declared association,
not that it is complete or sufficient for architecture-overview.

| Category | Classification | Safe deterministic subset | Unsafe subset |
|---|---|---|---|
| Governance / constraint | `EXISTING_EXPLICIT_RELATION` for Decision/Challenge/Insight edges; `DETERMINISTICALLY_DERIVABLE_WITH_SMALL_EXTENSION` for explicit ADR/document references | ADR -> explicitly referenced Story/document/component identifier; Decision -> explicitly mapped artifact | ADR -> every component mentioned nearby; Decision -> inferred subsystem constraint; change violates ADR |
| Change association | `DETERMINISTICALLY_DERIVABLE_NOW` for Commit -> ChangedFile and parent; `DETERMINISTICALLY_DERIVABLE_WITH_SMALL_EXTENSION` for Story/Event -> bounded change window | Commit -> changed file; Story -> files changed inside proven commit interval; Event -> explicit commit/change window | Story -> files inferred from title; Event -> causal impact; commit message -> architecture intent |
| Support / evidence | `EXISTING_EXPLICIT_RELATION` | Fact -> evidence reference; Observation -> supporting Fact; accepted Insight -> explicit evidence references | Evidence similarity -> support; a high confidence Insight -> proof |
| Dependency | `DETERMINISTICALLY_DERIVABLE_WITH_SMALL_EXTENSION` for module/import/build dependency | Module/package -> module/package when import/build edges satisfy a defined rule | Component responsibility or architecture dependency from directory/name co-occurrence |
| Interaction | `DETERMINISTICALLY_DERIVABLE_WITH_SMALL_EXTENSION` only for strong static wiring; otherwise `REQUIRES_SEMANTIC_INFERENCE` | Explicit Spring bean wiring, declared interface/adapter mapping, or bounded call evidence if extractor is authoritative | Controller -> service because names look conventional; runtime behavior without tracing |
| Responsibility / ownership | `DETERMINISTICALLY_DERIVABLE_WITH_SMALL_EXTENSION` only for explicit metadata; otherwise `REQUIRES_SEMANTIC_INFERENCE` | Module contains file/symbol; explicit ownership metadata | Module owns capability; class is responsible for business concern based on prose/names |
| Chronology / evolution | `EXISTING_EXPLICIT_RELATION` in commit parents and Story bounds; otherwise `DETERMINISTICALLY_DERIVABLE_NOW` | Commit -> parent; Story -> bounded revision interval; Decision creation -> later change only as chronology | Decision caused change; temporal proximity as causality |
| Provenance / derivation | `EXISTING_EXPLICIT_RELATION` in IDs, evidence refs, proposal/validation links, and Observation support | Insight -> source Analysis/proposal/validation; Observation -> rule and Facts; Fact -> evidence | AI interpretation -> trusted relation before validation; confidence -> provenance |

The first four recommended categories are the smallest set that covers what,
why, where, and with what evidence without pretending to own a universal
ontology. Interaction and responsibility remain valuable, but their semantic
risk is higher than their initial deterministic coverage.

## Architecture-Overview Value

| Category | Main components | Responsibilities | Interactions | Recent change | Why / governing decision | Supporting evidence | Human attention | Overall |
|---|---|---|---|---|---|---|---|---|
| Governance / constraint | Medium | Medium | Low | Medium | **High** | High | High | **HIGH** when explicit |
| Change association | High | Medium | Medium | **High** | Medium | High | **High** | **HIGH** |
| Support / evidence | Low | Medium | Low | Medium | Medium | **High** | High | **HIGH** |
| Dependency | **High** | Medium | **High** | Medium | Low | High | High | **HIGH**, with strict extractor |
| Interaction | High | High | **High** | Medium | Low | Medium | High | MEDIUM initially |
| Responsibility / ownership | Medium | **High** | Low | Low | Medium | Medium | High | MEDIUM only with explicit metadata |
| Chronology / evolution | Medium | Low | Medium | **High** | Medium | High | Medium | MEDIUM/HIGH as context |
| Provenance / derivation | Low | Medium | Low | Medium | Medium | **High** | **High** | HIGH as a grounding cross-cut |

Ease of extraction is not the ranking criterion. For example, package
containment is easy to calculate but is not enough to assert responsibility.
Conversely, a Story-to-diff association is highly useful because its meaning
is narrow and its evidence is reconstructible.

## Source Reliability

| Proposed association | Authoritative source | Deterministic | Revision-scoped | Project-scoped | Reconstructible | Trust / confidence concerns |
|---|---|---:|---:|---:|---:|---|
| Commit -> ChangedFile | Persisted Git commit and `ChangedFile` data | YES | YES | YES through Source | YES | Proves changed path, not intent or impact. |
| Commit -> parent | Persisted parent SHA / imported Git history | YES | YES | YES | YES | Parent graph must be complete; no causal interpretation. |
| Story -> changed file | Story base/target commits plus parent traversal and diff data | YES, when complete | YES | YES | YES | Must fail closed for missing bounds or incomplete history. |
| Event -> change window | Validated Engineering Event's explicit source/commit bounds | YES, when explicit | YES | YES | YES | Validation makes the event trusted; the diff association remains technical evidence. |
| Fact -> evidence | Fact `evidenceReferences` and grounding allow-list | YES | Usually YES | YES | YES | Exact canonical identity is required; path-only aliases are unsafe. |
| Observation -> Fact | `supportingFactIds` and Fact identity | YES | Analysis-scoped | YES | YES | Support is not causality or correctness of the rule. |
| Accepted Insight -> evidence | Accepted Insight provenance and evidence references | YES | Source Analysis/revision metadata | YES | YES | Insight prose remains a human-validated interpretation, not a code fact. |
| ADR -> explicit reference | Repository document parser plus exact ADR identifier/path/reference | YES for the reference | YES | YES through source | YES | Document authorship is not truth; status and supersession must remain visible. |
| Decision -> explicit mapping | Persisted Decision relation or explicit reference | YES | Usually project/time scoped | YES | YES | Choice/rationale alone must not become component governance. |
| Module -> module dependency | Import graph or build dependency metadata | YES if extractor rule is explicit | YES | YES | YES | Dependency direction can be language/build-specific; avoid calling it runtime interaction. |

The existing repository context includes source paths, Java declarations,
symbols, modules, diff data, and canonical evidence metadata. It does not yet
constitute a complete import/call graph. A small module-dependency extractor is
therefore possible, but it should be a later bounded extension rather than a
reason to introduce a static-analysis platform.

## Temporal / Revision Semantics

Relations have different temporal classes and should not be flattened:

| Temporal class | Examples | Identity requirements |
|---|---|---|
| Timeless or project-lifecycle knowledge | Explicit Decision -> explicitly mapped artifact; accepted Insight -> source Analysis | Stable project/entity IDs, creation/validation metadata, current status where applicable |
| Revision-scoped technical fact | Module -> module dependency; Commit -> ChangedFile; Story -> changed file | `(project, source, revision, endpoint identity)`; canonical reference must include revision provenance |
| Event/history relation | Commit -> parent; Story -> commit window; Event -> change window | Immutable commit IDs, interval/bounds, event time, and source identity |
| Grounding/provenance association | Fact -> evidence; Observation -> Fact; Insight -> evidence | Exact canonical reference plus source/revision and trust tier |

`Component A -> depends on Component B` is not timeless merely because both
components have stable names. It may be true at R1 and false at R2. A relation
created at R1 must not silently answer a current-revision question at R2.

`KnowledgeRelation` should not be reused for every class:

- durable, project-owned, human-validated semantic edges can use it;
- commit/file, Story-window, module-dependency, and symbol-containment edges
  need revision-aware reconstruction metadata;
- support/evidence links are already part of grounding and provenance contracts
  and should not be duplicated as untyped domain edges;
- chronology is better represented by commit parent and Story bounds than by
  a generic semantic edge.

## KnowledgeRelation Suitability

| Use case | Assessment | Reason |
|---|---|---|
| Durable validated domain relationships | **GOOD_FIT** | Project ownership, typed UUID endpoints, direction, and durable identity fit explicit human-validated knowledge. Validation and lifecycle rules remain necessary. |
| Revision-scoped repository relationships | **POOR_FIT** | No source/revision/validity interval; a UUID edge would risk stale current-state claims. |
| Code-derived transient relationships | **POOR_FIT** | Persistence would incorrectly imply durable semantic authority and would make reconstruction difficult. |
| Provenance-heavy evidence relationships | **PARTIAL_FIT** | Typed endpoints fit navigation, but current fields do not carry canonical evidence references, revision provenance, trust tier, or extractor metadata. Existing evidence contracts are the better source of truth. |

Option C avoids forcing unrelated semantics into one table. It also avoids a
new relation type for every extractor output. A repository-derived projection
can use a stable relation kind and canonical references at the projection
boundary, while preserving the underlying evidence and extractor metadata.

## Human / AI Consumer Equity

Every recommended association passes the equity test only if it is exposed as
the same Core-owned capability, not manufactured in the Python prompt path.

| Category | Human inspection | Provenance display | Future navigation | Same AI input | AI-only advantage? |
|---|---|---|---|---|---|
| Change association | Yes, through Story, history, diff, and source views | Commit SHA, path, Story bounds, source, revision | Yes, commit/file/Story expansion | Yes, via bounded Core projection | No |
| Support / evidence | Yes, through Fact/Observation/Insight evidence references | Exact canonical evidence references and grounding metadata | Yes, evidence expansion | Yes, via the same selected references | No |
| Governance / constraint | Yes, through ADR/Decision documents and explicit mappings | Document revision, status, identifier, Decision provenance | Yes, document/Decision expansion | Yes, if in authorized context | No |
| Dependency | Yes, through source/build/import evidence | Source, revision, file/symbol/import evidence | Yes, module/file neighborhood | Yes, if same projection is authorized | No |

The existing gap is not an argument for AI-only relation enrichment. Current
human surfaces are incomplete: the relation REST API exists, but the frontend
has no first-class navigation, and some context contracts omit relationships.
That is a product/retrieval capability gap to close through shared Core
capabilities, not through hidden AI semantics.

## Options

### Option A - KnowledgeRelation

Put all proposed edges directly into the current `KnowledgeRelation` table.

**Advantages**

- Reuses existing persistence, project lookup, relation projection, and
  bounded admission.
- Human navigation through the existing relation API is conceptually simple.
- Durable semantic edges remain in one place.

**Costs and risks**

- Revision-scoped code relations become stale or misleading without adding
  revision/source/validity fields.
- Persisting every commit/file and dependency edge creates volume and lifecycle
  churn that the current model was not designed for.
- `description` still lacks independent provenance and trust.
- Polymorphic UUID endpoints do not enforce endpoint existence or ownership.
- Migration would blur validated domain knowledge and technical evidence.

**Assessment**: suitable only for explicit durable relations; unsafe as a
universal home.

### Option B - Repository-Derived Projection

Keep repository relationships reconstructible from source, revision, Git data,
and repository evidence. Project them into the shared Core context when
authorized and bounded, without persisting them as durable `KnowledgeRelation`
rows.

**Advantages**

- Correctly models revision changes and reconstruction.
- Preserves technical-evidence trust rather than implying human validation.
- Avoids graph-database and universal-ontology scope.
- Naturally supports source paths, symbols, diffs, and module identities.

**Costs and risks**

- Human navigation must use shared detail/expansion capabilities, not only the
  current relation API.
- More projection and canonical-reference plumbing is required.
- A future implementation must avoid duplicating retrieval logic between REST,
  MCP, and AI context construction.

**Assessment**: best fit for repository-derived edges, but insufficient alone
for durable Decision/Insight/Challenge semantics.

### Option C - Hybrid

Use `KnowledgeRelation` for durable, explicitly authored and human-validated
domain relationships. Use a repository-derived projection for revision-scoped
technical, change, dependency, and evidence associations. Both flow through
the same Core retrieval/composition/projection boundary and canonical identity
rules.

**Advantages**

- Preserves trust and lifecycle distinctions.
- Preserves temporal correctness for code-derived facts.
- Keeps human and AI consumers on the same capability and evidence universe.
- Minimizes migration by extending existing repository-context seams.
- Is compatible with ADR-063's future Retrieval Layer: shared retrieval returns
  annotated candidates; each consumer still owns composition and budget.

**Costs and risks**

- Two association representations require clear documentation and adapter
  rules.
- Human navigation must eventually converge on common reference/expansion
  capabilities.
- The boundary must be tested to prevent duplicate or contradictory edges.

**Assessment**: recommended.

## Minimum Recommended Relation Set

The first set contains four categories, but only the first category should be
implemented in the next Story. The remaining three define the target boundary
and can be staged after the first projection proves the seam.

### 1. Change association: `CHANGES`

- Source endpoints: Commit, Engineering Story, Engineering Event.
- Target endpoints: ChangedFile, canonical source-file reference, or bounded
  repository evidence item.
- Direction: change owner/window -> changed artifact.
- Source of truth: persisted commit parents, `ChangedFile` rows, Story
  `baseCommit`/`targetCommit`, and explicit Event commit bounds.
- Extraction: direct diff mapping for Commit; parent-traversed bounded window
  for Story/Event.
- Temporal scope: always revision/event scoped.
- Trust: `TECHNICAL_EVIDENCE`; Story/Event ownership remains its own
  human-authored or trusted provenance.
- Example: `story:0124 -> diff:{sha}:backend/.../KnowledgeSelectionServiceImpl.java`.
- Architecture value: **HIGH**. It answers what changed, where it changed,
  and which component deserves attention without inferring why.

### 2. Support association: `SUPPORTED_BY`

- Source endpoints: Fact, Observation, accepted Insight.
- Target endpoints: canonical repository evidence, Fact, or Observation as
  already permitted by the grounding contract.
- Direction: knowledge claim -> supporting evidence.
- Source of truth: `evidenceReferences`, `supportingFactIds`, proposal/
  validation provenance, and grounding allow-list.
- Extraction: existing explicit references; no similarity expansion.
- Temporal scope: source Analysis/revision and evidence revision.
- Trust: preserve source trust tier; support does not promote trust.
- Example: `observation:{uuid} -> fact:{uuid}` and
  `fact:{uuid} -> git:{sourceId}:{sha}`.
- Architecture value: **HIGH** for inspectability and human attention; it
  makes every architecture statement auditable.

### 3. Governance association: `GOVERNS`

- Source endpoints: ADR or accepted Decision.
- Target endpoints: explicitly referenced Story, document, component, or
  behavior identifier.
- Direction: governing artifact -> governed scope.
- Source of truth: exact ADR/Story/document reference or an explicit persisted
  mapping; document status and revision must be retained.
- Extraction: deterministic identifier/reference parser plus validation of the
  referenced target.
- Temporal scope: document revision and governance status; not timeless by
  default.
- Trust: ADR/Decision origin and current status remain separate from technical
  evidence; no AI promotion.
- Example: `document:source:ADR-063.md@R -> story:0124` only when the reference
  is explicit and the document is in the authorized scope.
- Architecture value: **HIGH** for why a boundary exists and which decision
  should govern review.

### 4. Module dependency association: `DEPENDS_ON`

- Source endpoints: module/package or explicitly identified source component.
- Target endpoints: module/package or explicitly identified source component.
- Direction: importer/build consumer -> imported/build dependency.
- Source of truth: import declarations, build dependency declarations, and
  revision-scoped source structure.
- Extraction: bounded import/build extractor with language/build-specific rules;
  not a full call graph.
- Temporal scope: repository revision.
- Trust: `TECHNICAL_EVIDENCE`; dependency does not imply runtime call,
  responsibility, or architectural desirability.
- Example: `backend.knowledge.selection -> backend.insight.repository` from a
  resolved Java import at revision R.
- Architecture value: **HIGH** for component boundaries and coupling, if the
  extractor states exactly what dependency means.

No recommended category asserts `VIOLATES`, `OWNS_RESPONSIBILITY`, or
`CALLS` in the first step. Those require policy or stronger semantic/static
analysis and would increase ontology risk.

## Test Strategy

Tests should target the extractor/projection boundary, not merely serialized
prompt shape. Every association must be tested as shared Core data before any
consumer-specific projection.

### Change association

- Positive fixture: a commit with a persisted `ChangedFile` yields a directed
  Commit -> ChangedFile association with the canonical SHA/path identity.
- Direction: reverse direction is absent unless separately established.
- Endpoint identity: renamed files preserve old/new path semantics explicitly;
  no path-only alias is substituted for the canonical reference.
- Project ownership: a commit from another Source/Project cannot enter the
  current project projection.
- Revision correctness: a Story includes only files in `(baseCommit,
  targetCommit]`; a file changed after `targetCommit` is absent.
- No co-occurrence inference: a file mentioned in a Story title but absent from
  the diff produces no edge.
- Evidence disappearance: removing the diff mapping removes the edge.
- Bounded admission: candidate and relation counts remain within Story 0124
  limits; related admission cannot expand the final category budget.
- Equity: the same canonical edge is available through human expansion and the
  AI projection when authorized.
- Prompt survival: both endpoints and the edge survive only when closure and
  budget conditions are satisfied.
- Negative fixture: incomplete Story bounds or incomplete parent traversal
  fails closed and emits no authoritative Story -> file edge.

### Support association

- Positive fixture: a Fact with `evidenceReferences` produces exactly those
  canonical support associations.
- Direction: Fact/Observation/Insight -> evidence, never evidence -> claim by
  default.
- Endpoint identity: canonical `fact:`, `observation:`, `git:`, and `diff:`
  references are preserved exactly.
- Project ownership: a reference outside the authorized project/source is
  rejected from the grounding allow-list.
- Revision correctness: evidence revision metadata is retained and mismatched
  revision evidence is not silently treated as current.
- No co-occurrence inference: two claims citing the same file do not support
  each other.
- Evidence disappearance: removing an evidence reference removes the edge.
- Bounded admission: support edges do not cause unbounded claim/evidence
  selection or bypass existing evidence budgets.
- Equity: a human can inspect the same evidence reference and AI receives no
  hidden support edge.
- Prompt survival: the edge is represented only when the claim and citable
  evidence are both projected.
- Negative fixture: high Insight confidence without an evidence reference
  produces no support edge.

### Governance association

- Positive fixture: an ADR containing an exact authorized Story/component
  reference yields ADR/document -> target with document revision identity.
- Direction: governing artifact -> governed target.
- Endpoint identity: preserve document canonical reference and target identity.
- Project ownership: a document from another Source/Project is excluded.
- Revision correctness: a superseded or older document remains historical and
  is not silently presented as the current governing decision.
- No co-occurrence inference: an ADR and component in the same repository
  folder produce no edge without an explicit reference/mapping.
- Evidence disappearance: removing the explicit reference removes the edge.
- Bounded admission: governance edges cannot pull arbitrary documents or
  components beyond configured budgets.
- Equity: the exact ADR/Decision and mapping are human-navigable and AI-visible
  through the same authorized capability.
- Prompt survival: a governance edge is omitted if either endpoint is absent.
- Negative fixture: prose saying “this is important for architecture” without
  an exact target reference produces no `GOVERNS` edge.

### Module dependency association

- Positive fixture: a Java import or build declaration resolves to a target
  module and yields importer -> dependency.
- Direction: importer/consumer -> imported dependency.
- Endpoint identity: use revision-scoped module/package/source references, not
  display names alone.
- Project ownership: unresolved or external dependencies are classified
  separately and cannot become internal project edges.
- Revision correctness: an import removed at R2 disappears from the R2
  projection, even if it existed at R1.
- No co-occurrence inference: same package prefix, same Story, or same changed
  commit does not create dependency.
- Evidence disappearance: deleting the import/build declaration removes the
  edge.
- Bounded admission: dependency projection has bounded neighborhood/edge
  counts and does not recursively traverse the graph.
- Equity: the human can inspect the import/build evidence and AI receives the
  same edge/reference semantics.
- Prompt survival: only edges whose endpoints are within the authorized
  projected repository evidence survive.
- Negative fixture: a controller and service with conventional names but no
  import/wiring evidence produce no dependency or interaction edge.

Cross-cutting tests should also prove deterministic ordering, stable digest
behavior, strict two-endpoint closure, no recursive traversal, trust-tier
preservation, and no accidental inclusion of diagnostics in the prompt.

## Recommended Architecture

**OPTION_C_HYBRID** is recommended.

The boundary should be:

```text
Durable validated domain knowledge
  -> KnowledgeRelation

Revision-scoped repository facts
  -> reconstructible relationship projection

Both sources
  -> Core authorization and shared retrieval
  -> consumer-owned bounded composition
  -> human and AI projections
```

This preserves ADR-063's five responsibilities:

- Retrieval finds annotated candidates and their canonical references.
- Composition applies the consumer's budget and intent policy.
- Projection represents the selected closure for human or AI consumers.
- Grounding validates exact evidence identity.
- Expansion lets both consumers inspect deeper detail.

It also preserves ADR-067's boundary: Java Core owns scope, relation
eligibility, trust, and context construction; the Python engine receives only
the authorized projection and may interpret it, but cannot create authoritative
relations.

The first implementation should create a shared change-association projection,
not a new general relation table and not a new prompt ontology. The projected
association should carry, at minimum:

- relation semantic name;
- source and target canonical references;
- source/target category;
- project and source identity;
- revision or revision interval;
- trust tier;
- provenance/evidence references;
- deterministic expansion target.

Whether this is eventually represented by a dedicated contract or an existing
repository-evidence extension is an implementation detail for the next Story.
It must not be encoded as a durable `KnowledgeRelation` without explicit
revision semantics.

## Smallest Next Story

### Goal

Expose a bounded, revision-correct, reconstructible **change association** in
the shared Core context so architecture-overview can explain which files and
repository components were changed by a selected Story or Engineering Event.

### Scope

- Define the deterministic meaning of Commit -> ChangedFile and Story/Event ->
  bounded changed-file association.
- Reuse persisted commit parent and `ChangedFile` data.
- Resolve Story windows only from explicit valid base/target commits.
- Preserve canonical commit/diff references, source identity, revision scope,
  and technical-evidence trust.
- Expose the association through the existing Core retrieval/composition seam
  used by human and AI consumers.
- Apply bounded candidate, edge, and endpoint closure rules compatible with
  Story 0124.
- Add deterministic diagnostics for incomplete or unavailable change evidence.
- Add focused extractor, projection, boundedness, grounding, and consumer
  equity tests.

### Non-goals

- No new durable `KnowledgeRelation` relation type.
- No universal ontology or graph database.
- No recursive graph traversal.
- No dependency, call graph, ownership, or responsibility inference.
- No LLM-created or similarity-derived relationships.
- No prompt redesign.
- No RAG, vector search, embeddings, runtime tracing, or full static analysis.
- No frontend UI implementation; only preserve the shared capability seam needed
  for future human navigation.

### Acceptance Criteria

1. A persisted commit and changed-file record produce one deterministic directed
   change association with canonical endpoint identities.
2. A valid Story change window includes only diffs from `(baseCommit,
   targetCommit]` and excludes later or unrelated changes.
3. Missing Story bounds, missing parent traversal, or missing diff evidence fail
   closed without an authoritative Story-to-file association.
4. Associations are project/source/revision scoped and retain technical-
   evidence trust and provenance.
5. The final association set is bounded and does not expand existing knowledge
   budgets or bypass Story 0124 endpoint closure.
6. No association is created from title text, shared Story identity, path
   co-occurrence, or confidence alone.
7. Human and AI consumers receive the same authorized association semantics and
   canonical references; no AI-only edge exists.
8. Eligible associations survive the final prompt projection only when both
   endpoints are projected.
9. The existing durable `KnowledgeRelation` lifecycle remains unchanged.
10. Focused and full relevant backend verification passes.

### Risks

- Story commit windows may be incomplete or ambiguous in imported history.
- Renames and deleted files need explicit old/new path semantics.
- Existing repository evidence references may have multiple representations;
  canonical identity must prevent visible-vs-citable mismatches.
- Adding change edges may compete with existing repository evidence budgets if
  composition is not kept separate from retrieval.
- Human navigation remains incomplete until shared expansion surfaces consume the
  same references.

### Dependencies

- `RepositoryRevisionScope` and the existing commit-parent traversal.
- `ProjectCommit` / `ChangedFile` persistence and commit-diff collector.
- Canonical `git:` and `diff:` evidence identity.
- Story base/target commit metadata.
- ADR-063 retrieval/composition/grounding boundaries.
- Stories 0123 and 0124 projection closure and bounded admission behavior.

## LEARN / PAIR / DELEGATE

### LEARN

- Semantic relation boundaries: association versus causality, interaction,
  responsibility, and governance.
- Fact versus interpretation and the effect of trust tiers.
- Revision identity, historical validity, and reconstruction.
- Human/AI consumer equity and capability parity.
- Why confidence cannot promote `POSSIBLE_RELEVANCE` to `EXPLICIT`.

### PAIR

- The minimum relation set and exact semantic names.
- Placement between `KnowledgeRelation`, repository evidence, and a future
  Retrieval Layer.
- Revision and validity representation for repository-derived associations.
- Canonical identity and endpoint closure at projection boundaries.
- How much module dependency evidence is strong enough for authoritative
  technical association.

### DELEGATE

- Mechanical commit/diff extractors.
- Revision-window adapters.
- Canonical reference adapters.
- Positive and negative fixtures.
- Deterministic ordering, boundedness, closure, and projection tests.
- Repetitive REST/MCP/shared-capability adapters once their contract is fixed.

## Open Questions

1. Should the next change projection target canonical `diff:{sha}:{path}`
   references directly, or introduce a first-class revision-scoped source-file
   reference while preserving diff evidence as provenance?
2. Are Engineering Events always guaranteed to carry valid commit bounds, or
   should the first Story support only Story and Commit sources?
3. What is the smallest human-facing expansion capability needed to satisfy
   ADR-063 capability parity before adding a frontend relation view?
4. Should explicit ADR -> component mappings be persisted as durable mappings,
   or remain revision-scoped document references until a concrete use case
   requires lifecycle semantics?
5. Which import/build ecosystems are sufficiently represented in the current
   repository to justify a later module-dependency extractor?
6. Should relation diagnostics remain observability-only, or become a human
   retrieval result without entering AI prompt knowledge?
