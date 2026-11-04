# Repository Analysis - Story 0179

## Scope Reviewed

The current repository after the merges of Stories 0177 and 0178, the existing
Story Context runtime runner, the live MCP launcher, and the running Docker
services.

## Current Evidence

- `main` is synchronized with `origin/main` at the merged Story 0177 revision.
- Backend, AI Engine, PostgreSQL, Keycloak, and frontend containers were running
  before the evaluation.
- The backend image initially predated the Story 0178 merge. Rebuilding only the
  backend image made the merged ledger implementation observable at runtime.
- A live broad scenario after rebuild completed successfully.
- The capture recorded 605 candidates, 605 unique candidates, 605 ranked
  candidates, 60 selected, 60 projected, 1 grounded, and 545 discarded.
- Normalized loss reasons were:
  - `SELECTION_CATEGORY_CONCENTRATION_LIMIT`: 470;
  - `SELECTION_ITEM_BUDGET_EXCEEDED`: 45;
  - `SELECTION_INSUFFICIENT_RELEVANCE`: 30;
  - `ENRICHMENT_TRUNCATED`: 1;
  - `CORE_SELECTION_REDUCED`: 1.
- The provider completed successfully with one interaction trace. Provider
  visibility remained `UNKNOWN` and `providerVisibleCount` remained `0`, as
  required by the callback contract limitation.
- The preflight outcome was `PROJECTION_REDUCED`; repository-context warnings
  included content enrichment truncation, evidence summary truncation, and
  repository-context budget application.
- An earlier four-scenario run before rebuilding the backend included two
  completed captures, one provider timeout, and one capture without a task ID.
  Those results are useful as operational evidence but cannot be treated as a
  controlled comparison because the runtime image did not include Story 0178.

## Relevant Runtime Surfaces

- `ai-engine/evaluations/story_context_budget_live.py`: existing bounded
  four-scenario live runner.
- `ai-engine/evaluations/story_context_specificity_live.py`: authenticated MCP
  client and snapshot retrieval helper.
- `backend/.../StoryContextSubmissionService.java`: Core snapshot and ledger
  creation boundary.
- `backend/.../StoryContextAgentCallbackFacade.java`: callback-linked provider
  outcome update.
- `backend/.../ContextLossLedger.java`: bounded stage accounting and digest.
- `docs/stories/0178-story-context-loss-aware-composition/decision-report.md`:
  recommendation to validate runtime composition and provider outcomes before
  reconsidering retrieval architecture.

## Interpretation

The current evidence supports a runtime measurement Story, not an immediate
production change. The broad capture indicates that selection category
concentration is the largest measured loss stage, but the sample size is one and
the earlier run showed provider timing variance. A controlled multi-scenario,
multi-repetition capture is required before selecting a policy change.

## Risks and Unknowns

- Live provider latency and timeout behavior may vary independently of Core
  selection and projection.
- The current runner labels profiles but does not itself alter Core budgets; the
  report must not treat profile labels as causal budget variants.
- Provider-visible per-reference attribution is unavailable from the current
  callback contract.
- Python `pytest` is not installed in the host environment; deterministic Python
  validation must use the available standard-library commands unless the
  dependency is explicitly provisioned.
- Runtime captures may contain sensitive repository information and must remain
  outside committed artifacts unless redacted and explicitly required.

## Recommendation

Approve a bounded runtime evaluation only. Defer changes to selection,
enrichment, projection, budgets, and retrieval until the controlled capture
produces stable evidence across all four scenarios.

## Vault Outcome

Vault was not consulted. Current repository stories, runtime artifacts, merged
implementation, and direct runtime evidence were sufficient for this analysis.
