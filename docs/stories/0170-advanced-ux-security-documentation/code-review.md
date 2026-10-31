# Code Review - Story 0170

## Scope

Reviewed the complete uncommitted Story 0170 diff after implementation and
validation, with focus on Core/AI Engine authority, parameter propagation,
credential boundaries, frontend state transitions, and test coverage.

## Findings

### Fixed

- `ai-engine/app/providers/chatgpt.py`: the provider read
  `self._max_output_tokens` when no task-level override was present, but the
  constructor did not initialize that field. This would fail at runtime for
  ChatGPT tasks using the configured default. The constructor now receives and
  stores `settings.llm_max_output_tokens`, and the affected constructor test
  was updated.

- `backend/.../StoryAgentFollowUpService.java`: follow-up requests omitted the
  parent task's Core-issued binding, provider, model, and authorized parameters.
  The initial execution snapshot now retains the non-secret authorized
  parameter map, and follow-ups copy the complete execution contract.

- `ai-engine/app/providers/chatgpt.py` and `anthropic.py`: providers used only
  their process-level model configuration. The prompt contract now carries the
  Core-authorized model identity for Story Context tasks, and both providers
  use it at the provider boundary while retaining the configured model for
  non-user-bound tasks.

- `ai-engine/tests/test_chatgpt_provider.py`: added direct payload coverage for
  the authorized model and `max_output_tokens` value.

## Review Results

- No remaining blocking or high-severity findings identified.
- Final independent review found no confirmed correctness defects. The three
  remaining coverage gaps identified in the second review are now covered by
  explicit regression tests.
- Core remains authoritative for the allowlist, bounds, readiness, and task
  authorization.
- The AI Engine only consumes authorized task parameters and does not acquire
  credentials from frontend state or user-visible payloads.
- Frontend behavior preserves explicit user model selection and explicit
  recommendation reset; no automatic provider/model fallback was introduced.
- Diagnostics and error messages remain bounded and do not include secrets or
  raw provider responses.

## Validation

- AI Engine focused regression suite: 29 tests passed.
- Backend follow-up and authenticated submission regression suite: 6 tests
  passed.
- Full AI Engine suite: passed.
- Backend full suite: 1,578 tests passed.
- Frontend unit suite: 286 tests passed.
- Frontend lint, format check, and production build: passed.
- AI Settings SPA E2E with mocked API boundaries: 2 tests passed.
- `git diff --check`: passed.

## Remaining Risk

- Docker-backed E2E passed all 3 tests after adding the dedicated OIDC Compose
  override, idempotent Keycloak test-user bootstrap, bearer injection in the
  Playwright-only fixture, and project seeding. Live ChatGPT/Anthropic calls
  remain intentionally unexecuted because no real credentials were authorized;
  local provider contract tests and the mock provider validate the safe path.

## Readiness

READY_FOR_HUMAN_REVIEW
