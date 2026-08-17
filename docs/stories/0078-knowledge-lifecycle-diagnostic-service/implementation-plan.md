# Story 0078 — Knowledge Lifecycle Diagnostic Service — Implementation Plan

> **NOT APPROVED — DO NOT IMPLEMENT**
>
> The Repository Analysis is approved. This plan is submitted to the
> Implementation Plan approval gate. No production code or test is written until
> that approval (then the Phase 6 implementation + verification gate).

## Objective

Deliver a **proposal-centric, deterministic, read-only** application diagnostic
that answers, given a `ValidatableProposal` id, whether its governed knowledge
lifecycle is complete, plus deterministic findings describing exactly where it is
broken. Consumes the lineage made exact by Story 0077. No migration, no generic
lineage storage, no REST/MCP/UI, no repair.

## Phase 1 — DTOs (`backend/.../lineage/dto/`)

New records in a new top-level package `com.hopeful117.devlogai.lineage.dto`:

1. `LineageStageStatus` (enum): `PRESENT`, `PENDING`, `NOT_APPLICABLE`, `MISSING`, `INCONSISTENT` (ADR-058 §14).
2. `KnowledgeLifecycleStatus` (enum): `COMPLETE`, `BROKEN`, `NOT_APPLICABLE`.
3. `KnowledgeLifecycleStageResponse` (record): stage-name, `LineageStageStatus`,
   artifact id (`UUID`, nullable), optional detail string.
4. `KnowledgeLifecycleDiagnosticResponse` (record): `proposalId`, `LifecycleType`
   (`ProposalType`), `proposalStatus` (`ProposalStatus`), overall
   `KnowledgeLifecycleStatus`, `List<ValidationStage | promoted-knowledge stage>`
   (as a simple list of stage responses), and `List<String> findings`.

Follow existing record style (e.g. `CreateValidationRequest`, SparkResponse) —
immutable, no builders unless the codebase requires them.

## Phase 2 — Service (`backend/.../lineage/service/`)

New `KnowledgeLifecycleDiagnosticService` (interface) + `KnowledgeLifecycleDiagnosticServiceImpl`:

- `@Service @RequiredArgsConstructor @Transactional(readOnly = true)`
- Inject: `ValidatableProposalRepository`, `ValidationRepository`,
  `DecisionRepository`, `InsightRepository`, `EngineeringEventRepository`.
- Entry: `KnowledgeLifecycleDiagnosticResponse diagnose(UUID proposalId)`.
- Resolution:
  1. Load proposal (`proposalRepository.findById`); if absent throw
     `EntityNotFoundException("Proposal", proposalId)` (existing shared exception).
  2. Resolve Validation: `validationRepository.findByProposalId(proposalId)`.
  3. Resolve promoted knowledge count by `ProposalType`:
     - `ENGINEERING_DECISION` → `decisionRepository.findByProposalId` (0/1)
     - `INSIGHT` → `insightRepository.findByProposalIdIn(List.of(id))` (0/1)
     - `ENGINEERING_EVENT` → `engineeringEventRepository.findByProposalIdIn(List.of(id))` (0/1)
  4. Evaluate against the truth table (Phase 3) → stage statuses + findings + overall status.
- Guards: supported-type check first; `CHALLENGE`/`DOCUMENTATION` → overall
  `NOT_APPLICABLE` with a single stage reported NOT_APPLICABLE, no findings, never BROKEN.
- Deterministic: pure reads, no LLM/ranking/selection.

## Phase 3 — Invariant / truth-table evaluation

Per-type per-stage classification (from repository-analysis Question F):

| ProposalStatus | Validation present | Count N | Validation stage | Promoted stage | Overall |
|---|---|---|---|---|---|
| PROPOSED | no | 0 | PENDING | PENDING | COMPLETE |
| PROPOSED | yes | 0 | PRESENT (+finding) | PENDING | BROKEN |
| REJECTED | no | 0 | MISSING (+finding) | NOT_APPLICABLE | BROKEN |
| REJECTED | yes, REJECTED | 0 | PRESENT | NOT_APPLICABLE | COMPLETE |
| REJECTED | yes | ≥1 | PRESENT (+finding) | PROMOTED (INCONSISTENT) | BROKEN |
| ACCEPTED | no | any | MISSING (INCONSISTENT) | PENDING | BROKEN |
| ACCEPTED | yes, ACCEPTED | 0 | PRESENT | MISSING — **invariant violation** | BROKEN |
| ACCEPTED | yes, ACCEPTED | 1 | PRESENT | PRESENT | COMPLETE |
| ACCEPTED | yes, ACCEPTED | >1 | PRESENT | INCONSISTENT (+finding) | BROKEN |
| ACCEPTED | yes, REJECTED | any | decision REJECTED vs status ACCEPTED (INCONSISTENT) | — | BROKEN |

Invariant text (finding) exactly:

> An ACCEPTED {PROPOSAL_TYPE} proposal MUST produce exactly one trusted {ARTIFACT}.

Findings are produced only for BROKEN cases (and the PROPOSED+Validation /
REJECTED+knowledge inconsistencies). PROPOSED/REJECTED-with-correct-validation
produce no findings.

## Phase 4 — Tests

### 4.1 Service unit tests (Mockito `@ExtendWith(MockitoExtension.class)`, mirrors repo pattern)
- Full truth table rows above (each as focused test).
- Per-type dispatch: each supported type used to select the correct repository call;
  unsupported types (`CHALLENGE`, `DOCUMENTATION`) → NOT_APPLICABLE / COMPLETE, never
  BROKEN, zero findings.
- Missing proposal id → `EntityNotFoundException`.
- Determinism/read-only: asserts only reads are invoked (no `save`).

### 4.2 Repository lookups
- `DecisionRepository.findByProposalId` (present from Story 0077);
  `InsightRepository.findByProposalIdIn`, `EngineeringEventRepository.findByProposalIdIn`
  already exist — verify semantics, no new repository methods needed.

### 4.3 Postgres integration test (Testcontainers, mirrors
`DecisionPromotionProvenancePostgresIntegrationTest`)
- Seed project/analysis/proposal via the same JDBC helpers pattern.
- Promote an ACCEPTED `ENGINEERING_DECISION` through `ValidationService`
  (`request.decision()=ACCEPTED`) in one transaction; assert
  `diagnose(proposalId).overall() == COMPLETE`, both stages PRESENT, findings empty.
- Insert an ACCEPTED proposal with **no** promoted Decision (JDBC), assert
  `overall() == BROKEN` with the invariant-violation finding.
- A `PROPOSED` proposal (no validation) → COMPLETE, validation PENDING, no findings.
- A `REJECTED` + REJECTED Validation → COMPLETE, no findings; a REJECTED proposal
  with a promoted artifact → BROKEN (INCONSISTENT).
- Verify read-only (no row count changes before/after).

### 4.4 Regression
- No existing test modified (no migration, no entity change).
- Full backend suite gate: **770+ passed / 0 failed**.

## Validation

- `./mvnw test` in `backend/` — full green (770 baseline + new lineage tests).
- Integration evidence: diagnostic reports COMPLETE for a genuinely promoted
  ENGINEERING_DECISION; BROKEN + invariant finding for an ACCEPTED proposal with no
  Decision; correct for PROPOSED / REJECTED / unsupported.
- Confirm no migration file added (Flyway stays at V43) and no new REST/MCP/UI.

## Explicitly out of scope (this plan)

- Any migration (V44) or entity change.
- Generic `LineageNode`/`LineageEdge` storage.
- REST controller, MCP tool, frontend.
- Phase 2 context-projection lineage (ProjectContext / RepositoryEvidence /
  EngineeringContext / MCP) and "produced-vs-selected" distinction.
- Automatic repair / artifact creation.
- LLM / AI diagnosis.
- Cross-project lineage.

## Deliverables

1. `lineage/dto/LineageStageStatus.java`
2. `lineage/dto/KnowledgeLifecycleStatus.java`
3. `lineage/dto/KnowledgeLifecycleStageResponse.java`
4. `lineage/dto/KnowledgeLifecycleDiagnosticResponse.java`
5. `lineage/service/KnowledgeLifecycleDiagnosticService.java` (interface)
6. `lineage/service/KnowledgeLifecycleDiagnosticServiceImpl.java`
7. Unit + repository + integration tests (Phase 4)
8. Post-approval: implementation report + engineering report

## Acceptance Criteria Recap

All 10 story criteria map to the truth table tests and the Postgres integration
verification (COMPLETE on successful promotion; BROKEN + invariant finding on
missing artifact; PROPOSED → PENDING/COMPLETE; REJECTED handling; per-type
dispatch; unsupported NOT_APPLICABLE; determinism/read-only; 770+ suite green; no
migration; no MCP/REST/UI/repair).