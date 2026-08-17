# Story 0077 — Preserve Decision Promotion Provenance — Implementation Report

## Summary

* Closed the only NOT_RECONSTRUCTIBLE V1 lineage edge — `ValidatableProposal → Decision` — by persisting `Decision.proposal_id`.
* Minimal production surface: `Decision` entity (+1 nullable `@OneToOne` field), one additive Flyway migration (`V43`), one line in `promoteDecision(...)`, one repository method.
* **No generic lineage table, no graph infrastructure, no duplicated provenance** (`validation_id` / `analysis_id` / `ai_task_id` remain derived from `proposal`).
* No backfill: legacy Decisions keep `proposal_id = NULL`.
* Full backend suite green: **770 tests, 0 failures** (baseline 768 + 2 new integration tests).

## Delivered Artifacts

* `Decision.java` — `@OneToOne` `ValidatableProposal proposal` (`proposal_id`, `updatable=false`, `unique=true`, nullable)
* `V43__add_decision_proposal_provenance.sql` — nullable `proposal_id` + `uk_decision_proposal_id` UNIQUE + `fk_decision_proposal` FK; no backfill
* `ProposalPromotionService.java` — `promoteDecision(...)` sets `.proposal(proposal)`
* `DecisionRepository.java` — `Optional<Decision> findByProposalId(UUID)`
* `ProposalPromotionServiceTest.java` — both ENGINEERING_DECISION promotion tests now assert `decision.getProposal() == proposal`
* `DecisionPromotionProvenancePostgresIntegrationTest.java` — 2 Testcontainers tests (migration + uniqueness + legacy NULL; `findByProposalId`)
* Docs: `story.md`, `repository-analysis.md`, `implementation-plan.md`, `implementation-report.md`, `engineering-report.md`

## Validation

### Backend

```
Tests run: 770, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

* `ProposalPromotionServiceTest`: ENGINEERING_DECISION promotions now verified to carry the source proposal reference.
* `DecisionPromotionProvenancePostgresIntegrationTest` (new, Testcontainers):
  * migration applied (latest = VM-version file); `proposal_id` column present; `uk_decision_proposal_id` UNIQUE present; duplicate `proposal_id` rejected (`DataIntegrityViolationException`); legacy Decision with `NULL proposal_id` persists.
  * `findByProposalId` returns exactly one promoted Decision; empty for an unlinked proposal.
* `ValidationServiceTest` — unchanged (already asserts `promotionService.promote(proposal, savedValidation, ...)` is invoked inside the `@Transactional` validation, guaranteeing the Decision is created with the proposal in the same transaction).
* `DecisionServiceTest` / `DecisionControllerWebMvcTest` — unchanged, still green (manual CRUD without a proposal still allowed).

## Acceptance Criteria Verification

| # | Criterion | Status |
|----|---|---|
| 1 | Promoted Decision references source proposal | ✅ integration + unit tested |
| 2 | `Decision.proposal_id` unique | ✅ `uk_decision_proposal_id` + tests |
| 3 | Promotion atomic with Validation acceptance | ✅ `@Transactional` `ValidationServiceImpl`; Decision created in same call |
| 4 | Legacy Decisions readable with `NULL`; no backfill | ✅ migration + legacy test |
| 5 | No generic lineage table | ✅ verified (no new table) |
| 6 | No duplicated analysis/validation/task provenance | ✅ only `proposal_id` added |
| 7 | Existing Decision CRUD compatible | ✅ `DecisionServiceTest`/MVC green |
| 8 | Existing ENGINEERING_DECISION E2E functional | ✅ full suite green |

## Final Assessment

All 8 acceptance criteria satisfied. The implementation is minimal and precisely scoped: it persists the single missing provenance edge, derives the rest through `Decision.proposal`, and introduces no lineage infrastructure. It is the required enabler for the Story 0078 Knowledge Lifecycle Diagnostic Service.