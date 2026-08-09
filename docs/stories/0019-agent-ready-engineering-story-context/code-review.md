# Code Review Report — Story 0019

## Review Status

Ready for human approval.

## Scope Reviewed

The review inspected the approved Story, Repository Analysis, Implementation Plan, complete diff,
projection contract and policy, degradation/accounting/digest logic, controller modes, snapshot
reuse, failure mapping, tests, canonical documentation, Docker/API benchmark, adapter behavior,
JaCoCo, and authenticated SonarQube results.

## Findings

No Blocker, Major, Minor, or Observation finding remains.

The first authenticated analysis exposed three inherited issues outside the original Story 0019
diff. After explicit human authorization, the history-import transactional proxy boundary and the
Project Understanding assertion lambda were corrected. The final Quality Gate passes with no
unresolved issue.

## Corrected During Review

The first live projection exposed inconsistent aggregate counts after deterministic tail removal:
34 evidence items were transported while layer/kind maps still described the original 60 selected
items. The projector now derives both maps from the final transported list. A regression test and
the rebuilt live API prove `evidence.size == selectedCount == layerTotal == kindTotal == 34`.

SonarQube also identified several maintainability issues in the new code. Response typing,
projection decomposition, assertion setup, nested conditional logic, and unused code were cleaned
up without changing the contract.

## Acceptance-Criteria Assessment

AC-1 through AC-13 pass through focused tests, complete regression coverage, explicit full mode,
adapter compatibility, bounded failure, deterministic projection, stable dual digests, and
unchanged internal Repository Context semantics.

AC-14 passes for the representative `devlog-ai` measurement: wire payload fell from 164,445 to
33,064 bytes (79.9%), the real adapter succeeded within its three-second bound, and retained
Project Context plus selected repository navigation evidence remains directly usable. The result
is repository-specific and makes no general productivity claim.

AC-15 passes: documentation, complete tests, JaCoCo, Docker/API, benchmark, and authenticated
SonarQube all pass. The Quality Gate reports 84.2% new-code coverage, 0.0% new duplication, zero new
violations, and zero unresolved issues.

## Architecture and Safety Review

Projection remains an integration-boundary concern owned by Java Core. The authoritative rich
model, ranking, selection, enrichment, AI-task snapshots, revision pinning, filesystem security,
and persistence are unchanged. Deterministic degradation preserves order, never promotes rejected
evidence, retains at least one usable item, and fails explicitly when even that cannot fit.

The separate projection digest covers canonical semantic payload while the Repository Context
digest retains Core authority. Generated time and self-referential accounting fields are excluded
as documented. Full diagnostics remain opt-in and lossless.

## Validation Assessment

* Focused Story tests: pass.
* Complete backend: 445 tests, 0 failures/errors/skips; pass.
* JaCoCo: 83.15% line coverage; pass.
* Engineering Story adapter: 9 tests; pass.
* Docker rebuild and exact live request: pass.
* Default/full benchmark and accounting consistency: pass.
* `git diff --check`: pass.
* Authenticated SonarQube Quality Gate: `OK`; zero unresolved issues.

## Residual Risks

* Tight projection limits can remove final-tail evidence; explicit counters/warnings and full mode
  bound this risk.
* The byte/4 token count is provider-neutral and intentionally approximate.
* Synchronized evidence may lag an uncommitted working tree; direct current-repository inspection
  remains mandatory.
* Cold local infrastructure startup can exceed ordinary timings even though the measured endpoint
  and adapter request stayed within three seconds.

## Technical Recommendation

Technical recommendation: Approve.

Human Code Review approval is still required before the Engineering Report, finalization, commit,
push, or merge.
