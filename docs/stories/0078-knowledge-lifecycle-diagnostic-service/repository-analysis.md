# Story 0078 — Repository Analysis: Knowledge Lifecycle Diagnostic Service

## Status

Prepared (awaiting human approval)

## Scope of this artifact

Targeted verification that Story 0078 can be implemented as a **proposal-centric,
deterministic, read-only, application-level diagnostic** that answers, given a
`ValidatableProposal`, whether its governed knowledge lifecycle is complete —
with **no generic lineage storage, no migration, no REST, no MCP and no UI** —
by reconstructing every edge from already-persisted domain relationships.

ADR-058 remains **Proposed** and is intentionally **not modified** by this Story.
Story 0077 closed the only NOT_RECONSTRUCTIBLE V1 edge (`ValidatableProposal →
Decision`); Story 0078 consumes that now-exact lineage.

Reference flow (per Story type):

```text
Proposal → Validation → Promoted Knowledge (Decision | Insight | EngineeringEvent)
```

---

## 1. MCP context assessment

Calls to `get_engineering_context(projectSlug="devlog-ai", intent=...)` returned:

| Class | Items |
|---|---|
| DIRECTLY_RELEVANT | ADR-006 governance implicit in project notes; proposal/promotion validated insights; Engineering Story conventions |
| USEFUL_BACKGROUND | Recipe for the Story doc set (0072–0076); project overview / ADR-practices validated insights |
| STALE_OR_CONFLICTING | none |
| NOISE | Maintenance / deduplication / projection-refresh commits and stories 0068–0076, unrelated to lineage diagnostics |
| MISSING | ADR-058/ADR-006 content, entities (`ValidatableProposal`, `Validation`, `Decision`, `Insight`, `EngineeringEvent`), enum values, repositories, Story 0077 implementation. The repository is authoritative and was used for every claim below. |

---

## 2. Lifecycle edge matrix (authoritative, from code)

| Edge | Persisted reference | Classification | Evidence |
|---|---|---|---|
| Proposal → Validation | `validations.proposal_id` UNIQUE (`uk_validation_proposal_id`) | DIRECTLY_STORED | `Validation.java` `@OneToOne unique`; `ValidationRepository.findByProposalId` |
| Proposal → Decision | `decisions.proposal_id` UNIQUE NULLABLE (`uk_decision_proposal_id`) | DIRECTLY_STORED (Story 0077, V43) | `Decision.java:36` `@OneToOne`; `DecisionRepository.findByProposalId` |
| Proposal → Insight | `insights.proposal_id` UNIQUE | DIRECTLY_STORED | `Insight.java:42` `@OneToOne`; `InsightRepository.findByProposalIdIn` |
| Proposal → EngineeringEvent | `engineering_events.proposal_id` UNIQUE | DIRECTLY_STORED | `EngineeringEvent.java:22` `@OneToOne`; `EngineeringEventRepository.findByProposalIdIn` |

**Every edge required by the ADR-058 V1 governed-lifecycle diagnostic is already
persisted.** No new column, table or migration is required.

Note the asymmetry introduced by Story 0077: `Insight` and `EngineeringEvent`
persist both `proposal_id` and `validation_id`; `Decision` persists only
`proposal_id` (validation is DERIVED via `proposal → Validation`). The diagnostic
must therefore resolve the Validation stage uniformly through
`ValidationRepository.findByProposalId(proposalId)` for all three types, rather
than via each promoted artifact's own `validation_id`.

---

## 3. Proposal-driven dispatch (promotion contract)

`ProposalPromotionService.promote(...)` (`validation/service/ProposalPromotionService.java:24`)
dispatches on `proposal.getType()`:

```text
INSIGHT             → insights.promote(...)
ENGINEERING_EVENT   → promoteEvent(...)      (ENGINEERING_DECISION, ENGINEERING_EVENT only? no)
ENGINEERING_DECISION→ promoteDecision(...)
CHALLENGE / DOCUMENTATION / other → default → IllegalArgumentException
```

Only `INSIGHT`, `ENGINEERING_EVENT` and `ENGINEERING_DECISION` have promotion
handlers. This is the exact set of **supported** lifecycle types for the
diagnostic. `CHALLENGE` and `DOCUMENTATION` are recognized enum members but have
no governed promotion path — they map to `NOT_APPLICABLE` (unsupported), never
an invariant violation.

---

## 4. Per-stage reconstruction

### 4.1 Validation stage

- Lookup: `ValidationRepository.findByProposalId(proposalId)` → `Optional<Validation>`.
- Gives `decision` (`ValidationDecision`: ACCEPTED / REJECTED) and `validatedAt`.

### 4.2 Promoted-knowledge stage

| ProposalType | Repository lookup | Cardinality |
|---|---|---|
| ENGINEERING_DECISION | `DecisionRepository.findByProposalId(proposalId)` | exactly 0 or 1 (`@OneToOne` UNIQUE) |
| INSIGHT | `InsightRepository.findByProposalIdIn(List.of(proposalId))` | exactly 0 or 1 (`@OneToOne` UNIQUE) |
| ENGINEERING_EVENT | `EngineeringEventRepository.findByProposalIdIn(List.of(proposalId))` | exactly 0 or 1 (`@OneToOne` UNIQUE) |

All three are still the enforcement of "exactly one", so the diagnostic counts
must be 0 or 1; a result > 1 cannot occur at the DB level (unique indexes) but the
service conservatively reports `INCONSISTENT` if the repository ever returns more
than one.

---

## Analysis Question A — Is a diagnostic read-only and fully reconstructible?

**Yes.** Every stage is resolved from persisted, non-nullable or unique references
already present after Story 0077 (matrix §2). Each lookup is a direct repository
read; there is **no** write, no promotion, no creation, no state transition. The
service is `@Transactional(readOnly = true)` exactly like the existing
`ProposalReviewService`. ADR-058 §21 requires detection without repair — this
service only reports.

## Analysis Question B — Is any new lineage storage or migration justified?

**No.** All three `proposal → promoted-knowledge` edges are DIRECTLY_STORED, and
`proposal → validation` is DIRECTLY_STORED. ADR-058 §15 mandates persisting only
relationships that would otherwise be lost; none are lost now. Highest Flyway =
**V43**; Story 0078 introduces **no V44** and no entity.

## Analysis Question C — Which package owns the service?

Story 0077 chose domain-internal placement (`decision/entity`, `validation/service`).
The diagnostic is **cross-cutting** (it reads proposal, validation, decision,
insight and event repositories together), so no single domain package cleanly
owns it. The existing `analysis/diagnostics` package is analysis-execution
diagnostics (warnings, execution snapshots) — a different concern.

**Recommendation:** a new top-level package
`com.hopeful117.devlogai.lineage` (`service` + `dto`), mirroring ADR-058's
"Engineering Knowledge Data Lineage" vocabulary and independent of any single
domain. This leaves `analysis/diagnostics` untouched and avoids overloading
`validation` or `proposal`.

## Analysis Question D — What is the correct proposal-centric contract?

Entry: the diagnostic is keyed by `UUID proposalId` (ADR-058 §13
`diagnose_knowledge_lifecycle(lifecycleType, reference)`). Given a proposal id
the service loads the `ValidatableProposal`, reads its `type` and `status`, then
resolves Validation + promoted knowledge for that proposal only. It is strictly
project-scoped by construction (the proposal itself carries `project_id`; ADR-058
§17), and it never federates unrelated artifacts.

## Analysis Question E — How are missing vs invalid distinguished (ADR-058 §14)?

A per-stage status enum is required to distinguish expected states:

```text
PRESENT        stage occurred and its artifact exists
PENDING        stage is expected to occur later (not decided yet)
NOT_APPLICABLE stage is not expected for this proposal (rejected / unsupported type)
MISSING        expected artifact/edge is absent (break)
INCONSISTENT   state contradicts the type/status contract (multiple artifacts,
               decided-but-no-validation, rejected-but-promoted)
```

These map directly onto the stage semantics below (Question F).

## Analysis Question F — Lifecycle truth table per supported type

Given proposal `P` of a supported type with `status` and a resolved `Validation`
`V` and promoted knowledge count `N`:

| P.status | V present | N | Stage outcomes | Lifecycle |
|---|---|---|---|---|
| PROPOSED | no | 0 | validation PENDING, knowledge PENDING | COMPLETE (pending decision) |
| PROPOSED | yes | 0 | validation PRESENT, knowledge PENDING → INCONSISTENT | BROKEN |
| PROPOSED | yes | ≥1 | validation PRESENT, knowledge INCONSISTENT (promoted before acceptance) | BROKEN |
| PROPOSED | no | ≥1 | validation PENDING, knowledge INCONSISTENT (promoted before acceptance) | BROKEN |
| REJECTED | no | 0 | validation MISSING (should exist), knowledge NOT_APPLICABLE | BROKEN |
| REJECTED | yes (REJECTED) | 0 | validation PRESENT, knowledge NOT_APPLICABLE | COMPLETE |
| REJECTED | yes | ≥1 | validation PRESENT, knowledge PROMOTED → INCONSISTENT | BROKEN |
| ACCEPTED | no | anything | validation MISSING → INCONSISTENT | BROKEN |
| ACCEPTED | yes (ACCEPTED) | 0 | validation PRESENT, knowledge MISSING → **invariant violation** | BROKEN |
| ACCEPTED | yes (ACCEPTED) | 1 | validation PRESENT, knowledge PRESENT | COMPLETE |
| ACCEPTED | yes | >1 | knowledge INCONSISTENT (multiple) | BROKEN |
| ACCEPTED | yes (REJECTED) | anything | validation decision REJECTED vs status ACCEPTED → INCONSISTENT | BROKEN |

The reference invariant (ADR-058 §4) is:

> An ACCEPTED ENGINEERING_DECISION proposal MUST produce exactly one trusted Decision.

Extended identically to INSIGHT and ENGINEERING_EVENT per ADR-058's candidate
invariants. Since trusted-knowledge promotion only occurs after an ACCEPTED
validation, a `PROPOSED` proposal that already has promoted knowledge is
inconsistent and reports BROKEN with the promoted stage INCONSISTENT (mirroring
the REJECTED+knowledge row). `CHALLENGE` / `DOCUMENTATION` → whole-lifecycle
NOT_APPLICABLE (unsupported): COMPLETE (nothing expected), never BROKEN.

## Analysis Question G — How is lifecycle "completeness" exposed?

A single response per proposal describing each stage (validation, promoted
knowledge) with its status + optional artifact id, a derived overall
`LifecycleStatus` (e.g. `COMPLETE` / `BROKEN` / `NOT_APPLICABLE`), and a list of
deterministic findings for any BROKEN case (including the invariant-violation
text). No AI, no LLM; ADR-058 §12.

## Analysis Question H — Are there pre-existing services or duplication risks?

- `ProposalReviewService` already bundles validation+knowledge counts, but returns
  a review-page aggregation, not a per-proposal lifecycle diagnosis; it is a
  consumer UI adapter, not lineage. The new diagnostic is a distinct,
  proposal-keyed observable.
- `AnalysisDiagnosticsService` is analysis-execution diagnostics; unrelated.
- No existing `KnowledgeLifecycleDiagnostic` / `LifecycleDiagnostic` symbol exists
  (confirmed by search) — the Story is net-new and non-duplicative.

## Analysis Question I — Transactional semantics

Read-only single transaction; no shared-commit concerns (ADR-058 §16 applies only
to authoritative *promotion* writes, which this Story never performs). ADR-006
governance is preserved: the diagnostic only *observes*; it never bypasses or
mutates validation/promotion.

## Analysis Question J — Legacy Decisions (Story 0077 NULL provenance)

A `Decision` with `proposal_id = NULL` (legacy/manual) is never returned by
`DecisionRepository.findByProposalId(proposalId)` and therefore **cannot affect**
any proposal-keyed diagnostic result. It is not reachable from a proposal and is
not flagged. The invariant applies to ACCEPTED supported proposals; a legacy
Decision with NULL provenance simply corresponds to no proposal and is invisible
here (consistent with Story 0077's NOT_APPLICABLE handling at the `Decision` only
if ever queried by decision id — out of scope).

## Analysis Question K — What is the exact API / non-functional surface?

- **Input:** `UUID proposalId`.
- **Output:** deterministic DTO (stages + overall status + findings).
- **No REST controller, no MCP tool, no frontend.** MCP is ADR-058 Phase 2; Story
  0078 exposes the application capability only (so a future MCP adapter can wrap it).
- **Deterministic:** pure repository reads; no LLM, no ranking, no selection.

## Analysis Question L — Test strategy

- **Unit (service, mock repos):** truth table §4.2 / Question F — PROPOSED no-V
  (PENDING, COMPLETE), REJECTED no-V (BROKEN), REJECTED+V (COMPLETE), REJECTED+knowledge
  (INCONSISTENT), ACCEPTED no-V (BROKEN), ACCEPTED no-knowledge (invariant violation,
  BROKEN), ACCEPTED one-knowledge (COMPLETE), ACCEPTED multiple-knowledge
  (INCONSISTENT), unsupported type (NOT_APPLICABLE), per-type dispatch correctness.
- **Repository:** each `findByProposalId`/`findByProposalIdIn` return semantics.
- **Integration (Postgres):** promote an ACCEPTED ENGINEERING_DECISION via
  `ValidationService`, then assert the diagnostic reports COMPLETE with the
  persisted Knowledge; assert a deliberately missing Decision reports BROKEN +
  invariant finding. Fidelity mirrors Story 0077's
  `DecisionPromotionProvenancePostgresIntegrationTest`.
- **Baseline:** run full backend suite (currently **770 passed / 0 failed**) at
  phase 6 gates; the phase-targeted gate is the new lineage suite.

## Analysis Question M — Risks

- Package-choice risk (Question C) resolved by a new isolated `lineage` package.
- Over-reporting valid intermediate states: mitigated by the truth table
  (PROPOSED → PENDING, not MISSING).
- Repository multiplicity: unique indexes make >1 impossible; service guards
  conservatively anyway.
- Scope creep toward MCP/REST/repair: explicitly out of scope (Question K).

## Analysis Question N — Dependencies

- `ValidatableProposalRepository` (load proposal), `ValidationRepository`,
  `DecisionRepository`, `InsightRepository`, `EngineeringEventRepository`.
- Story 0077's `Decision.proposal` + `findByProposalId` (already on this branch).
- `ProposalStatus`, `ProposalType`, `ValidationDecision`, `InsightStatus` enums.

## Analysis Question O — Explicitly out of scope

- Generic `LineageNode`/`LineageEdge` table and any migration (V44).
- Modification of ADR-058 (remains Proposed) or ADR-006.
- REST controller, MCP tool, frontend/UI.
- Phase 2 context-projection lineage (ProjectContext/RepositoryEvidence/EngineeringContext/MCP).
- Ranking / selection lineage (`decision exists but not selected` distinction).
- Automatic repair / auto-creation of missing Decision/Insight/Event.
- LLM / AI-based diagnosis.
- Cross-project lineage.
- `CHALLENGE`/`DOCUMENTATION` diagnostic fidelity beyond NOT_APPLICABLE.

---

## Conclusion

Story 0078 is implementable as a lean, read-only, proposal-keyed application
service over already-persisted lineage. Every stage is DIRECTLY_STORED after
Story 0077; no storage, migration, entity or adapter is required. The meaningful
design surface is the deterministic status/invariant model (Questions E–G), the
package placement (Question C) and focused tests (Question L). ADR-058 and
ADR-006 are untouched and governance is observed, never mutated.

**REPOSITORY_ANALYSIS_APPROVAL_REQUIRED**

No implementation, migration, entity, service, controller, or production test
changes were made. This is documentation only.