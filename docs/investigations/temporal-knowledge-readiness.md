# Temporal Knowledge Readiness — Investigation Report

## 1. Purpose

Determine how DevLog today represents, detects, evaluates, and expresses the
**temporal validity** of engineering knowledge (current, stale, superseded,
archived, refreshed, invalidated), and decide whether/when a Temporal Knowledge
initial capability is justified. This is an **investigation only**: no code,
migration, entity, or ADR changes were made.

## 2. Scope

- Map knowledge-bearing domain objects (Insight, Decision, EngineeringEvent,
  ValidatableProposal, Validation, Human Context Input, Challenge, Knowledge
  Event/Relation, Project Understanding, Project Freshness, RepositoryEvidence,
  AnalysisContext, MCP context assembly).
- Inventory temporal fields and their time semantics.
- Trace stale detection, refresh, supersession, and retrieval/deterministic
  exclusion paths end-to-end.
- Compare Temporal Knowledge against Lineage (ADR-058), Event Sourcing, and
  Retrieval/RAG so the boundaries are explicit.
- Classify readiness and recommend one first slice.

## 3. Method

Evidence was gathered from the MCP engineering context (project/engineering
notes) first, then **verified against the repository code and migrations**
(repository evidence takes precedence). Investigation phases 0–15 were driven
to completion; the report is the phase-14 deliverable.

## 4. Temporal Fields Inventory

No dedicated "validity window" columns exist anywhere
(effectiveFrom/validUntil/supersededAt/lastConfirmedAt/lastSeenAt/expiry are all
absent from migrations and entities).

| Concept | Time fields present | Time semantics |
|---|---|---|
| `Insight` | `createdAt`, `updatedAt`; `status` enum only | status-based validity; **no superseded/archived-at timestamp** |
| `Decision` | `createdAt`, `updatedAt` (`decidedAt` proxied via proposal) | creation/processing only |
| `EngineeringEvent` | `occurredAt` (NOT NULL), `createdAt` | **occurrence/effective time** = real "when it happened" |
| `ValidatableProposal` | `createdAt`; `decidedAt` (nullable) | creation + decision/processing time |
| `Validation` | `validatedAt` (NOT NULL), `createdAt`/`updatedAt` | validation/authorization time |
| `ProjectHumanContextInput` | `createdAt`, `updatedAt`; `status` (ACTIVE/ARCHIVED) | processing + status; **no archived-at timestamp** |
| `Challenge` | `createdAt`; status (OPEN/RESOLVED/ACCEPTED/MITIGATED) | creation + status |
| `KnowledgeEvent` | `createdAt` | event log |
| `KnowledgeRelation` | `createdAt` | relation creation |
| `ProjectCommit` (history) | commit-author/time, committed date | git history; **not versioned knowledge snapshots** |
| `Source` | polling times | persistence |
| `ProjectFreshness` | baseline/current revision + revision times | external-source freshness |
| `Milestone` | `startedAt`, `completedAt` | project timeline |
| Project Understanding claims | `createdAt`/`updatedAt` (via claims) | processing |

**Classification of time semantics:** EVENT time (`occurredAt`),
PROCESSING time (`createdAt`/`updatedAt` as created/modified),
VALIDATION time (`validatedAt`, `decidedAt`), EFFECTIVE time (absent — the gap),
PERSISTENCE time (`updatedAt` as last-modified). The dominant, always-present
dimension is **processing/creation time**; true **effective validity time**
does not exist.

## 5. Status / Lifecycle Model (existing temporal primitives)

- `InsightStatus`: ACTIVE, ARCHIVED, SUPERSEDED (column added in migration V42).
- `ProposalStatus`: PROPOSED, ACCEPTED, REJECTED.
- `ValidationDecision`: ACCEPTED, REJECTED.
- `ProjectHumanContextInputStatus`: ACTIVE, ARCHIVED.
- `ChallengeStatus`: OPEN, RESOLVED, ACCEPTED, MITIGATED.
- `ProjectFreshnessStatus`: NO_BASELINE, CURRENT, STALE, UNKNOWN.
- `MaintenanceFindingStatus`: OPEN, ACKNOWLEDGED, RESOLVED, DISMISSED.
- Lineage enums: `LineageStageStatus` (PRESENT/PENDING/NOT_APPLICABLE/MISSING/INCONSISTENT),
  `KnowledgeLifecycleStatus` (COMPLETE/BROKEN/NOT_APPLICABLE).

Supersession is expressed **only via the enum** — there is no timestamp of when
supersession/archival occurred and no causal pointer persisted beyond Knowledge
Relations.

## 6. Stale Detection (current behaviour)

Present and partially deterministic, but heterogeneous by surface:

- **Human Context Inputs** (`MaintenanceEvaluationServiceImpl#staleHumanContextCandidates`):
  heuristic rule — an ACTIVE input is stale if `ageDays >= 30 && lagDays >= 14`
  relative to a newer ACTIVE input of the same type. Emits
  `STALE_HUMAN_CONTEXT_INPUT` finding. **Heuristic thresholds, not a model.**
- **Project Understanding / Projection refresh** (`STALE_PROJECT_UNDERSTANDING`,
  `MISSING_PROJECTION_REFRESH`, `PROJECTION_REFRESH_GAP` findings): refresh-gap detection.
- **Project Freshness** (`ProjectFreshnessClassifier`): deterministic git-revision
  comparison (baseline vs current) → CURRENT/STALE/NO_BASELINE/UNKNOWN with
  refresh guidance. Deterministic but models the **source**, not the knowledge.
- **Duplicate/overlap** (`TRUSTED_KNOWLEDGE_EXACT_DUPLICATE`,
  `TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE`, `TRUSTED_KNOWLEDGE_OVERLAP_REVIEW`;
  dedup agent + overlap agent).

Findings live in `maintenance_findings`/`maintenance_assessments`, are
triaged OPEN→ACKNOWLEDGED→RESOLVED/DISMISSED, with sealed remediation for
STALE_HUMAN_CONTEXT_INPUT (`AUTO_RESOLVE` = `HumanContextInputStatus.ARCHIVED`)
and auto-resolve of deterministic findings. **Evaluation is manually triggered
via the Maintenance Assessment/Evaluation endpoint — there is no scheduler.**

## 7. Refresh / Supersession Capabilities

- **Human Context:** `ARCHIVED` transition exists; archived inputs are correctly
  **excluded** from context assembly (`ProjectContextProviderImpl` queries
  `Status = ACTIVE` only).
- **Insight:** `archiveInsight(id)` and `supersedeInsight(id, canonicalId)`
  exist (state transitions), the latter also creating an `INSIGHT→INSIGHT`
  supersession Knowledge Relation.
- **No claim of automatic refresh; no effective-time validity; no audit trail
  of when a status transition occurred** (only `updatedAt` on the row).

## 8. Deterministic Context Path & Temporal Exclusion Gaps

The full path: Source → Facts/Observations → AnalysisContext →
KnowledgeSelection (incl. RepositoryContextAdapter for Story preparation) →
SelectedKnowledge → projection → MCP.

**Confirmed gap:** The trusted-knowledge retrieval layer does **not** filter by
validity status:

- `KnowledgeSelectionServiceImpl#select` loads insights via
  `insightRepository.findByProjectIdOrderByCreatedAtDesc(...)`
  (lines 67–72) with **no `status` filter**, so ARCHIVED/SUPERSEDED insights are
  still eligible for promotion into prompts and architecture-knowledge slices.
- `RepositoryContextAdapter#buildRepositoryContext` feeds
  `insightRepository.findByProjectIdOrderByCreatedAtDesc(projectId)` (line 60)
  with the same absence — deterministic **Story preparation context** can include
  superseded/archived trusted knowledge.

By contrast, `InsightServiceImpl.list`, `InsightPromotionService`,
`TrustedKnowledgeDuplicateAuditService` do use
`findByProjectIdAndStatusIn(...)`, but promotion/audit status sets are (at least
`ARCHIVED`/`ACTIVE`) and differ per caller.

**Earliest deterministic exclusion layer:** the JPA query in the repository. A
remediation can close the gap at that layer (status-filtered retrieval), which
is minimal and near zero-risk.

## 9. Freshness vs Authority

- **Authority** is governed by ADR-006 validation + ADR-058 lineage: only accepted
  proposals become trusted knowledge (validated Insights, Decisions,
  EngineeringEvents).
- **Freshness** today is largely authoritative-flavored **age/lag heuristics** +
  source-revision classification; it is not authority. A Temporal Knowledge
  model must keep authority (lineage) and freshness (temporal) **orthogonal**
  — a fresh-but-unauthorized artifact is still not trusted; an authorized-but-
  stale artifact is still valid until superseded.

## 10. Temporal vs Lineage

ADR-058 (Proposed) covers provenance: WHICH source/validation/promotion produced
a trusted artifact, and diagnostics distinguish MISSING vs INVALID. It
explicitly defers a Phase 2:
`Trusted Knowledge → ProjectContext → RepositoryEvidence → EngineeringContext → MCP`
and mentions temporal reasoning as outside scope of phase 1.

- **Lineage answers:** where did this come from? Is it authorized? Traceable?
- **Temporal answers:** is this still current? Since when? What supersedes it?
- These are orthogonal and complementary. Temporal Knowledge is **not a subset**
  of ADR-058; but the Decision/EngineeringEvent/Insight provenance model built
  for lineage (Story 0077/0078) is exactly the join key temporal metadata needs.

## 11. Temporal vs Event Sourcing

DevLog stores snapshots/state (current status) plus a `KnowledgeEvent` log, but
there is **no append-only event-sourced aggregate** and no temporal-query
(bitemporality, state-at-time) capability. Reintroducing state change history for
status transitions is an option, but marginal: the primary near-term value is
**current-validity filtering**, not full state-at-time reconstruction. Event
Sourcing is a larger architectural commitment with no demonstrated need here.

## 12. Temporal vs Retrieval / RAG

Retrieval (RAG) integration is explicitly deferred post-Phase-2-lineage in
ADR-058. No vector/RAG infra exists yet. Retrieval introduces its own
query-time filtering/ranking concerns. Temporal metadata should be attached
**at storage time** (as part of the trusted-knowledge rows/relations) so that
future optional retrieval can consume it **without** depending on vector infra.

## 13. Desired Temporal Knowledge V1 (classification)

| Capability | Class | Rationale |
|---|---|---|
| Status-aware deterministic retrieval (exclude ARCHIVED/SUPERSEDED from prompt context) | **REQUIRED_V1** | Closes a live correctness gap; minimal, at the repository/selection layer |
| Supersession relation modelling (Knowledge Relation types incl. supersedes/refreshes) | REQUIRED_V1 | Existing supersede path already creates relations; needs a first-class `SUPERSEDES`/`REFRESHES` type + filter |
| Record status-transition timestamp (`supersededAt`/`archivedAt`) | USEFUL_LATER | No effective-time queries needed yet; heuristic detection is agnostic |
| Effective-time validity window (validFrom/validUntil, bitemporal) | NOT_NEEDED now | No consumer; decisions/insights are monotonic promotions, not dated contracts |
| Full Event Sourcing state-at-time | NOT_NEEDED now | No demonstrated need; larger commitment |
| Project Freshness/understanding as a Temporal-Knowledge sub-model | ALREADY_EXISTS / integrate | Present as source-revision + finding heuristics; align, don't rebuild |

## 14. Candidate First Slices Compared

| Slice | Boundary | Risk | Adventure |
|---|---|---|---|
| **A. Status-aware trusted-knowledge retrieval** (filter ACTIVE in `KnowledgeSelectionServiceImpl` + `RepositoryContextAdapter`, add repo query by ACTIVE status) | Deterministic exclusion at selection | Very low | Small |
| B. First-class `SUPERSEDES`/`REFRESHES` KnowledgeRelationType + relation-aware filtering | Relation layer | Low | Medium |
| C. Status-transition timestamps (`supersededAt`/`archivedAt`) on Insight/HumanInput | Entity/migration | Low | Medium |
| D. Unify stale-detection heuristics into one temporal validity evaluation | Services | Medium | Medium |
| E. Effective-time validity windows / bitemporal | Model | High | High |
| F. Event-sourced state-at-time | Architecture | High | High |

## 15. Recommended First Slice

**Slice A.** Closed-loop dogfooding of the existing lifecycle: add an
`InsightRepository.findByProjectIdAndStatusOrderByCreatedAtDescIdDesc(projectId, ACTIVE)`
query and use it in `KnowledgeSelectionServiceImpl` and `RepositoryContextAdapter`
so prompts and Story-preparation context only ever expose ACTIVE trusted
knowledge. This is the smallest coherent Temporal-Knowledge-V1 step, is fully
deterministic, removes a real defect (superseded/archived knowledge surfacing
to the agent), and lays the storage join key for slices B/C to follow.

## 16. Dogfooding Assessment

DevLog's own approach (status-filtered trusted knowledge feeding deterministic
context) is directly exercised by Slice A. The GitHub-backend/Yocto-style git
knowledge model, proposal/validation promotion, and lineage diagnostics already
form the boundary Slice A patrols. The MCP engineering context corroborates that
stale/superseded handling is an active, agent-visible concern (stories 0069/0070/
0071/0074).

## 17. Deliverables & Exit Criteria

- Remove non-ACTIVE trusted knowledge from deterministic context (Slice A).
- (Later) first-class supersession relation type; (later) transition timestamps.
- No RAG, no Event Sourcing, no effective-time windows in V1.
- Verification: existing backend suite (789 green) + new status-filtered
  selection tests.

## 18. Unresolved Questions & Risks

- Risk: silent removal of ARCHIVED/SUPERSEDED from context changes prompt
  content — must be covered by tests and ideally a transient diagnostic.
- Question: should SUPERSEDED insights be excluded uniformly, or conditionally
  (e.g. still surfaced for CE/ASR when no successor exists)?
- Question: single source of truth for the "current" status set per consumer —
  recommend a shared constant/helper used by both selection paths.
- Open: whether to persist a `supersededAt`/`replacementOf` on Insight now vs
  when a consumer needs it.

## 19. Conclusion & Recommendation

- **Classification: C — LINEAGE_FOUNDATION_FIRST.**

The system already has strong lifecycle primitives (status enums, supersede
relations, provenance, lineage diagnostics, stale/duplicate findings) and a
clear, cheap first temporal slice (status-aware deterministic retrieval) that
adopts the just-merged lineage/provenance foundation as its join key. However,
a dedicated Temporal Knowledge ADR is premature until the lineage Phase 2 boundary
(Trusted Knowledge → … → MCP, explicitly deferred in ADR-058) is designed, because
temporal exposure overrides and retrieval are Phase-2 concerns. Proceed in order:
(1) close the deterministic status-filter gap now as a small lineage-adjacent fix
**within** the existing lifecycle (not a new Temporal Knowledge subsystem);
(2) after ADR-058 Phase 2 validates against real projects, design Temporal
Knowledge as an explicit, orthogonal extension to lineage; (3) only then write a
Temporal Knowledge ADR. Do not create a Temporal Knowledge ADR before the lineage
Phase 2 contract is stable.