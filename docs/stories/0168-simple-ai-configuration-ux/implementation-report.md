# Implementation Report - Story 0168

## Repository State

- Branch: `feat/story-0168-simple-ai-configuration-ux`.
- Base branch: `main` at the Story 0167 merge.
- Worktree: modified; no commit or push performed.
- DevLog Story Agent context was unavailable in the current tool set.

## Governance

- Epic: Simplifier la configuration des providers et modeles IA de DevLog AI.
- Authorized Story: Story 0168 - Simple AI Configuration UX.
- Governing ADRs: ADR-074, ADR-075, ADR-076.
- The Repository Analysis and updated Implementation Plan were explicitly
  approved by the human project owner.

## Files Changed

- `backend/.../OAuthConnectionIntentCallbackController.java`: HMAC-authenticated
  AI Engine callback for server-to-server OAuth completion.
- `backend/.../OAuthConnectionIntentService.java`: consumes the Core-owned
  intent for the authenticated AI Engine callback.
- `backend/.../ApiKeyConnectionIntentService.java`: returns the configured
  browser-reachable AI Engine intake base URL without changing private service
  calls.
- `backend/.../OidcSecurityConfiguration.java`: permits only the HMAC-checked
  OAuth callback route through the JWT filter boundary.
- `ai-engine/app/services/chatgpt_oauth.py`: finalizes OAuth through Core and
  redirects the browser with a non-sensitive status, with bounded retry.
- `ai-engine/app/api/chatgpt_auth.py`: handles success/failure redirects.
- `ai-engine/app/core/config.py`, `ai-engine/app/main.py`: public redirect and
  strict CORS origin configuration, including fail-closed URL/origin validation.
- `ai-engine/app/services/api_key_enrollment.py`: forwards the configured
  Anthropic workspace header during provider validation.
- `frontend/src/app/features/ai-configuration/*`: Simple configuration page,
  typed API service, models, responsive styles, and tests.
- `frontend/tests/playwright-test.spec.ts`, `frontend/playwright.config.ts`:
  mobile API-key enrollment E2E coverage and configurable SPA test server.
- `frontend/src/app/app.routes.ts`, `frontend/src/app/core/layout/app-layout.html`:
  settings route and navigation.
- `.env.example`, `docker-compose.yml`, `frontend/README.md`: public intake,
  CORS, and user-facing configuration documentation.
- `docs/stories/0168-simple-ai-configuration-ux/`: repository analysis,
  implementation plan, and this report.

## Implementation Summary

The frontend now exposes a Simple AI configuration route for ChatGPT OAuth and
Anthropic API-key setup, catalog-driven model selection, explicit model choice,
state rendering, bounded testing, reconnect actions, and secret-free errors.
OAuth completion stays inside the AI Engine/Core service boundary. API-key
intake uses a short-lived signed Core intent and a separately configured public
AI Engine endpoint protected by an explicit CORS allowlist; Core never receives
the API key.

## Architectural Decisions Preserved

- Core remains the authority for configuration, intents, associations, and
  runtime state.
- AI Engine remains the owner of provider secrets.
- OAuth completion codes and credential handles do not enter frontend state or
  browser URLs.
- API-key fallback, credential selection, model fallback, and Advanced
  parameters remain outside the Simple scope.
- Explicit model selection is preserved and recommendations are advisory only.

## Tests Added or Modified

- Frontend service contract tests for Core operations and signed API-key intake.
- Frontend component tests for provider labels, initial state, and API-key
  clearing before submission.
- AI Engine CORS allowlist test.
- AI Engine public URL/CORS validation, OAuth cookie security, replay protection,
  OAuth scope provenance, and API-key recovery tests.
- Core OAuth callback service test covering intent-principal resolution.
- Core public/internal URL separation and HTTPS enforcement tests.

## Commands Executed

```bash
cd ai-engine && uv run pytest -q
cd frontend && npm run lint
cd frontend && npm run format:check
cd frontend && npx ng test --watch=false
cd frontend && npm run build
cd frontend && npm run e2e
./backend/mvnw -pl backend -am test -B
git diff --check
```

## Test Results

- AI Engine: all tests passed.
- Frontend unit tests: 56 test files, 281 tests passed.
- Frontend lint: passed.
- Frontend format check: passed.
- Frontend production build: passed with the existing unrelated
  `project-maintenance-section.scss` budget warning.
- Anthropic live enrollment validation: passed against `https://api.anthropic.com`
  using the local `.env` key and workspace configuration; no secret was printed
  or persisted.
- ChatGPT OAuth configuration validation: passed; live start endpoint returned
  the expected provider redirect and session cookie. Completing OAuth still
  requires an interactive provider authorization.
- Backend Maven suite: 1570 tests passed, 0 failures, 0 errors. The run still
  emitted Testcontainers/Hikari shutdown connection warnings.
- Frontend E2E: 1 SPA smoke test passed; 1 existing project-flow test was
- blocked because the backend returned an unavailable-project request, so the
  expected `devlog-ai` link was absent; the new AI settings E2E passed.
- `git diff --check`: passed.

The independent review findings were addressed before this report was
revalidated: callback retries now sign fresh replay identities, OAuth
transactions can be released after failed exchange while preventing concurrent
reuse, Core distinguishes invalid/conflict/service callback responses, the
public API-key URL is required rather than falling back to the private URL, and
the UI persists the selected model before testing it.

## Documentation Outcome

Documentation update: required and completed in `frontend/README.md`. It now
documents the `/settings/ai` route, server-to-server OAuth completion, the
public API-key intake boundary, and the explicit CORS configuration.

## Vault Outcome

- Vault consulted during Repository Analysis: yes, targeted search only.
- Relevant curated note found: no.
- Vault action: no vault action.
- Rationale: no relevant transverse note was found, and the repository Story,
  ADRs, and current implementation were sufficient. No vault write was made.

## Known Limitations

- Full ChatGPT OAuth completion was not executed because it requires an
  interactive authorization session; the configured start flow was validated.
- The public AI Engine URL and CORS origins are now validated as absolute
  HTTP(S) configuration at AI Engine startup; operators must still align them
  with the deployed frontend and redirect URI.
- Frontend E2E now covers the settings route with mocked Core/provider
  boundaries at mobile width, but it is not a live-provider test.

## Commit / Push

- Commit created: NO
- Push performed: NO

## Readiness

READY_FOR_CODE_REVIEW
