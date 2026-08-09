# Code Review — Story 0022

## Verdict

The implementation is architecturally coherent and the code/quality checks are ready for human
Gate 3 review. No blocking code defect was found. Completion cannot be recommended without an
explicit Gate 3 decision, because AC-14's required live proposal-to-event promotion remains
unproven after two valid provider runs returned zero proposals.

## Review findings

### Execution and evidence boundary

The public execution accepts only a complete immutable target identity and derives the base from
the imported first parent. Synchronization and history import occur before the short claim
transaction; the claim rechecks Project/Source ownership and a partial unique key resolves active
execution races. The persisted scope, selection digest, and downstream event provenance all retain
the same boundary. `AnalysisEvolutionScope.isNew()` intentionally forces `persist` for its shared
primary key; the entity is created once with its Analysis and has no update path, and the behavior
was proven against PostgreSQL and the live Docker service.

### AI and trust boundary

The event Intent, task type, prompt, and output model are separate from generic Insight generation.
Python performs defense-in-depth validation, while Core remains authoritative for schema, category,
limits, duplicates, and grounding subsets reconstructed from the immutable task snapshot. Zero
proposals remains valid and preferable to unsupported interpretation.

### Validation and persistence

Promotion is dispatched by proposal type inside the existing pessimistically locked Validation
transaction. Event payload/scope checks happen before persistent decision state; database uniqueness
on proposal and Validation supplies the final exactly-once boundary. Rejection creates no event,
unsupported accepted types fail explicitly, and the Insight path retains its severity and
Deliverable behavior. Legacy `KnowledgeEvent` storage and APIs remain readable and semantically raw;
the new validated domain therefore does not ambiguously replace historical records.

### Queries, context, and UI

Project list/detail ordering is deterministic and bounded. Review hydration remains batched, the
Angular UI sends severity only for Insights, and event execution is an explicit action. Only
validated compact events enter Project/agent context; the mandatory evolution scope has priority
under budget reduction.

### Quality

All complete suites and builds pass. Sonar findings introduced during implementation were removed
and focused boundary/concurrency tests raised new-code coverage to exactly 80.0%. Final authenticated
metrics are 0.0% duplication and zero new bugs, vulnerabilities, security hotspots, code smells, or
unresolved issues.

## Residual risks

* **Blocking acceptance evidence — AC-14:** two valid OpenAI executions returned zero grounded
  proposals, so live individual acceptance, atomic promotion, exactly-once retry, and read/context
  projection could not be exercised end to end. These paths are covered automatically but the Story
  explicitly requires a representative live demonstration. Gate 3 must request another authorized
  validation strategy or explicitly accept this exception.
* **Low — shared-primary-key creation marker:** `AnalysisEvolutionScope.isNew()` always returns true
  to select JPA `persist` for `@MapsId`. This is safe under the current create-only lifecycle but
  should be revisited if a future Story introduces scope mutation.
* **Low — provider conservatism:** the prompt correctly permits zero output. A future evaluation set
  may be useful to calibrate recall without weakening grounding or introducing fabricated defaults.

## Repository hygiene

`git diff --check` passes. Generated IDE, Python bytecode, and editable-package metadata are absent
from the intended diff. The disposable live Project was removed, the real six pending proposals are
unchanged, and no credentials were added to source or artifacts. Changes remain uncommitted pending
Gate 3 approval; `engineering-report.md` has not been created.
