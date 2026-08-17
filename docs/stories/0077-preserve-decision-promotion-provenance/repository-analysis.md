# Story 0077 — Repository Analysis: Preserve Decision Promotion Provenance

## Status

Prepared (awaiting human approval)

## Scope of this artifact

Targeted verification of the hypothesis that persisting **only**
`Decision.proposal_id` closes the ADR-058 V1 provenance gap for the
ENGINEERING_DECISION lifecycle, without generic lineage infrastructure and
without duplicating other provenance.

Reference flow:

```text
Analysis → AI Task → ENGINEERING_DECISION proposal → Validation → Decision
   → RepositoryEvidence → EngineeringContext → MCP
```

---

## 1. MCP context assessment

Calls to `get_engineering_context(projectSlug="devlog-ai", intent=...)` returned:

| Class | Items |
|---|---|
| DIRECTLY_RELEVANT | ADR-006 governance (implicit in MCP/RAG/Context-Engine project notes); reference Decision `ae47a47d-65fa-4a30-810c-f114b37755bd` |
| USEFUL_BACKGROUND | ADR documentation pattern insights; Engineering Story conventions |
| STALE_OR_CONFLICTING | none |
| NOISE | Majority of evidence: maintenance / deduplication / projection-refresh commits and stories 0068–0076, unrelated to this slice |
| MISSING | ADR-058 / ADR-006 content, Decision model, `ProposalPromotionService`, `ValidatableProposal`, `Validation`, `KnowledgeRelation` |

Repository is the authoritative implementation truth; all claims below are
sourced from the code, not from MCP.

---

## 2. Current lifecycle relationship matrix

| Edge | Persisted reference | Classification | Evidence |
|---|---|---|---|
| Analysis → AI Task | `ai_tasks.analysis_id` NOT NULL FK | RECONSTRUCTIBLE_EXACTLY | `AiTask.java`; `AiTaskRepository.findByAnalysisId...` |
| AI Task → ValidatableProposal | `validatable_proposals.ai_task_id` (nullable, `updatable=false`) | RECONSTRUCTIBLE_EXACTLY (generated) | set in `AiTaskResultServiceImpl.toProposals(...)` `.aiTask(task)` |
| ValidatableProposal → Validation | `validations.proposal_id` UNIQUE (`uk_validation_proposal_id`) | DIRECTLY_STORED | `Validation.java` `@OneToOne unique` |
| ValidatableProposal → Insight | `insights.proposal_id` UNIQUE | DIRECTLY_STORED | `Insight.java:42` `@OneToOne` |
| ValidatableProposal → EngineeringEvent | `engineering_events.proposal_id` UNIQUE | DIRECTLY_STORED | `EngineeringEvent.java` `@OneToOne` |
| ValidatableProposal → Decision | **none** | **NOT_RECONSTRUCTIBLE** | `Decision.java`; `V3__create_decisions_table.sql` |

The only missing edge is **Proposal → Decision**.

---

## 3. Decision model and promotion behavior

- `Decision` entity (`decision/entity/Decision.java`): `id`, `project_id` (only
  FK), `title/context/choice/rationale/consequences`, `created_at`, `updated_at`.
  **No** lifecycle/status enum, **no** proposal/validation/analysis/task ref.
- `decisions` table (`db/migration/V3__create_decisions_table.sql`): sole FK
  `project_id`.
- `DecisionRepository`: `findByProjectIdOrderByCreatedAtDesc[IdDesc]`; **no**
  proposal-based lookup.
- `ProposalPromotionService.promoteDecision(...)` (`validation/service/ProposalPromotionService.java:52`):
  builds `Decision` from `project` + payload; the `validation` argument is
  **unused**; the source proposal is never referenced.
- `DecisionServiceImpl` / `DecisionMapper`: pure manual CRUD (create/update/
  delete); `DecisionResponse` = `id, projectId` + content only.
- `ValidationServiceImpl.validate(...)` is `@Transactional` (class + method) and
  calls `promotionService.promote(...)` synchronously after persisting the
  Validation and flipping proposal status → **Decision persistence inherits the
  same transaction** as validation acceptance (ADR-006 + ADR-058 §16). No
  separate commit boundary.

---

## 4. Promotion provenance comparison

| Promoted type | proposal ref | validation ref | analysis ref | aiTask ref (via proposal) |
|---|---|---|---|---|
| Decision | ❌ none | ❌ none | ❌ none | ❌ n/a |
| Insight | ✅ `proposal_id` (unique) | ✅ `validation_id` (unique) | ✅ `analysis_id` | ✅ `proposal.ai_task_id` |
| EngineeringEvent | ✅ `proposal_id` (unique) | ✅ `validation_id` (unique) | ✅ `analysis_id` | ✅ `proposal.ai_task_id` |

Decision is the **only** promoted knowledge type whose promotion provenance is
dropped. The gap is specific to the Decision promotion path, not systemic.

---

## Analysis Question A — Minimal provenance: is `Decision.proposal` alone sufficient?

**Yes.** Persisting only `Decision.proposal_id` closes the chain:

```text
Decision ──proposal_id──▶ ValidatableProposal
                             ├── analysis_id   (NOT NULL)          → Analysis
                             ├── ai_task_id    (nullable)          → AiTask
                             └── Validation     (unique proposal_id) → Validation
```

- Decision → Analysis: via `proposal.analysis_id` (NOT NULL). Exact.
- Decision → AiTask: via `proposal.ai_task_id` (set for generated proposals;
  null for manual). Exact when present.
- Decision → Validation: via `Validation.proposal_id` unique lookup. Exact.
- Decision → Proposal: the persisted unique FK itself. Exact.

Single authoritative path; no ambiguity; no additional provenance storage.

## Analysis Question B — Duplication: should Decision also persist validation/analysis/aiTask?

| Candidate ref | Classification | Rationale |
|---|---|---|
| `proposal_id` | **REQUIRED** | the only lost edge |
| `validation_id` | DERIVABLE / REDUNDANT | `Validation` is uniquely keyed by `proposal_id`; storing both creates contradictory-state risk (`Decision.validation ≠ Decision.proposal.validation`) |
| `analysis_id` | DERIVABLE / REDUNDANT | `proposal.analysis_id` is stable; storing both risks `Decision.analysis ≠ Decision.proposal.analysis` |
| `ai_task_id` | DERIVABLE / REDUNDANT | nullable on proposal; duplicative |

Do **not** copy Insight/EngineeringEvent symmetry: those entities persist
analysis/validation because they are constructed from them independently; a
Decision's content comes entirely from the proposal payload. A single
authoritative `Decision.proposal` reference is preferred. No duplicate
provenance.

## Analysis Question C — Cardinality

Requirement: one accepted ENGINEERING_DECISION proposal → exactly one Decision.

**Decision:** `@OneToOne(optional=true)` on `proposal` with a **UNIQUE**
`proposal_id` FK (`updatable=false`), mirroring `Insight.java:42` and
`EngineeringEvent.java`. The unique index enforces the exactly-one contract at
the database, and it automatically rejects double-promotion of the same
proposal.

Selection: Proposal→Decision = **@OneToOne + UNIQUE** (not many-to-one).

## Analysis Question D — Legacy data

- The project already contains Decisions created before promotion provenance
  existed, including the reference Decision `ae47a47d` produced by the validated
  E2E, plus any Decisions created through `DecisionServiceImpl.create` (manual
  CRUD, project only).
- **Migration strategy:** `proposal_id` must be **nullable**; **no heuristic
  backfill** (no matching by content/timestamps/title — forbidden by ADR-058).
- Legacy / manual Decisions keep `proposal_id = NULL` and remain valid, readable
  records.
- Diagnostics (Story 0078): a NULL proposal reference is a valid
  legacy/manual Decision whose promotion provenance is **NOT_APPLICABLE** — never
  a fabricated association and never a standalone invariant violation. The
  accepted-proposal → exactly-one-Decision invariant applies only to ACCEPTED
  ENGINEERING_DECISION proposals and to Decisions that carry a proposal
  reference.

## Analysis Question E — Domain / API exposure

| Surface | Classification |
|---|---|
| `Decision` entity | **REQUIRED_NOW** (the persistence point) |
| `DecisionResponse` / REST `proposalId` | USEFUL_LATER (deferred to Story 0078 diagnostic) |
| ProjectContext projection | OUT_OF_SCOPE (Phase 2) |
| EngineeringContext | OUT_OF_SCOPE (Phase 2) |
| MCP | OUT_OF_SCOPE (Phase 2) |

This Story does not expand into Phase 2 context lineage.

## Analysis Question F — Invariant enablement

After adding `Decision.proposal_id` + `DecisionRepository.findByProposalId`:

| Invariant | Status | Why |
|---|---|---|
| PROPOSED ENGINEERING_DECISION → no promoted Decision | **ENABLED** | `findByProposalId` empty; no Decision linked |
| REJECTED ENGINEERING_DECISION → no promoted Decision | **ENABLED** | `findByProposalId` empty |
| ACCEPTED ENGINEERING_DECISION → exactly one Decision | **ENABLED** | `findByProposalId` returns exactly one; unique index guards DB |

The deterministic evaluation service itself is **Story 0078**; Story 0077 only
makes the data capable of supporting it. This split is coherent: provenance
persistence is independently testable and required regardless.

---

## 5. Migration plan (draft — not implemented)

- Next Flyway version: **V43** (highest existing is V42).
- `V43__add_decision_proposal_provenance.sql`:
  - `ALTER TABLE decisions ADD COLUMN proposal_id UUID NULL;`
  - unique index/constraint on `proposal_id`;
  - `FOREIGN KEY (proposal_id) REFERENCES validatable_proposals(id);`
  - **no backfill**.
- ENGINEERING_DECISION lifecycle work added no `decisions` migration, so V43 is
  the clean next slot.

## 6. Minimal production change surface (planned)

1. `Decision.java`: add `@OneToOne` `ValidatableProposal proposal` (`joinColumn
   proposal_id`, `unique=true`, `nullable`), `updatable=false`.
2. `V43__add_decision_proposal_provenance.sql` (above).
3. `ProposalPromotionService.promoteDecision(...)`: add `.proposal(proposal)`.
   (Passing `validation` remains an option; not persisted per Question B.)
4. `DecisionRepository`: add `Optional<Decision> findByProposalId(UUID)`.

No new generic lineage service, no lineage table, no graph infra.

## 7. Test strategy (planned)

- `ProposalPromotionServiceTest`: ENGINEERING_DECISION promotion sets `proposal`;
  INSIGHT / EVENT promotion unaffected; non-promotable types unchanged.
- `ValidationServiceTest`: accepted ENGINEERING_DECISION → Decision linked within
  the validation transaction; rollback removes the Decision (atomicity).
- Decision repository: `findByProposalId` returns exactly one (linked) / empty
  (unlinked proposal).
- Migration: `V43` applies over a DB containing legacy Decisions; legacy rows
  keep NULL; unique index enforced.
- Uniqueness: second promotion of the same accepted ENGINEERING_DECISION proposal
  fails.
- Legacy null provenance: legacy Decision (NULL proposal) readable, not flagged.
- Existing `DecisionServiceTest` / `DecisionControllerWebMvcTest` keep passing
  (CRUD compatibility: manual Decisions without a proposal still allowed).

## 8. Risks

- Migration is additive and reversible in risk terms (nullable column + unique
  index); no data rewrite.
- Accidental duplicate provenance avoided by persisting only `proposal_id`.
- JPA `@OneToOne` cardinality must match the schema unique index — covered by
  repository/uniqueness tests.
- API compatibility: `DecisionResponse` unchanged.
- Transaction: Decision creation shares the validation transaction, so a failure
  rolls back Decision + Validation + proposal status together.

## 9. Dependencies

- `Decision` entity (backend).
- `V43` Flyway migration.
- `ProposalPromotionService`.
- Existing `ValidationServiceImpl` transaction boundary (already `@Transactional`).

## 10. Explicitly out of scope for this Story

- `KnowledgeLifecycleDiagnosticService` (Story 0078).
- Generic `LineageNode` / `LineageEdge`.
- `KnowledgeRelation` redesign.
- Graph database.
- Context projection lineage.
- `RepositoryEvidence` lineage.
- Ranking / selection lineage.
- MCP lineage.
- Retrieval Layer / RAG.
- Automatic lifecycle repair.
- Heuristic legacy backfill.
- Exposure of `proposalId` in `DecisionResponse` / ProjectContext / EngineeringContext.

---

## Conclusion

`Decision.proposal_id` (UNIQUE, nullable) is sufficient and minimal to close the
only NOT_RECONSTRUCTIBLE V1 edge. All downstream provenance (validation,
analysis, AI task) is derived from the single authoritative `Decision.proposal`
reference, satisfying ADR-058's derived-vs-persisted principle. No generic
lineage or graph infrastructure is justified.

**REPOSITORY_ANALYSIS_APPROVAL_REQUIRED**

No implementation, migration, entity, promotion-service, or production test
changes were made. This is documentation only.