# Implementation Report - Story 0175

## Result

Implemented the first bounded completion-policy slice in the working tree. No
commit or push has been performed. Human code review and Story acceptance remain
pending.

## Production Changes

- Added `story-context-completion-v1` accounting to Core Story Context budget
  decisions.
- Classified the current Story Context analysis contract as complex when it has
  at least ten required analysis fields.
- Rejected complex executions with an authorized output allowance below 4096
  tokens before provider invocation using
  `BUDGET_COMPLETION_ALLOWANCE_INSUFFICIENT`.
- Preserved Core model-capacity and serialized-prompt preflight checks.
- Added catalog resolution for the AI Engine's `chatgpt` provider identity so
  Core can apply the documented ChatGPT model-capacity policy.
- Added `requiredOutputLimit` to bounded task snapshot budget accounting.
- Applied the accounting fields to initial and follow-up Story Context
  submissions.
- Added a shared AI Engine output-allowance helper. Story Context providers now
  require Core-authorized `maxOutputTokens` instead of silently falling back to
  their local default.
- Updated supported provider adapters: ChatGPT, OpenAI, Anthropic, Gemini and
  OpenRouter.
- Updated `TOOLS.md` with the completion-policy and operator diagnostic
  behavior.

## Tests Added/Modified

- `StoryContextBudgetPreflightTest`: complex output allowance rejection and
  required allowance accounting, including the `chatgpt` catalog identity.
- `test_chatgpt_provider.py`: missing Core-authorized Story Context allowance
  is rejected before provider invocation.

## Commands Executed

```bash
./backend/mvnw -pl backend test -Dtest=StoryContextBudgetPreflightTest,StoryContextSubmissionServiceTest,AnalyzeStoryContextUseCaseTest -B
python3 -m py_compile app/providers/base.py app/providers/chatgpt.py app/providers/openai.py app/providers/anthropic.py app/providers/gemini.py app/providers/openrouter.py tests/test_chatgpt_provider.py
./backend/mvnw -pl backend -am clean verify -B
./backend/mvnw -pl mcp-server test -B
git diff --check
```

## Test Results

- Focused backend tests: PASS, 31 tests.
- Full backend verification: PASS, 1605 tests; JaCoCo checks passed.
- Full backend verification after the catalog alias fix: PASS, 1608 tests;
  JaCoCo checks passed.
- MCP module tests: PASS, 70 tests.
- Python syntax compilation: PASS.
- Python pytest tests: BLOCKED; `python3 -m pytest` reports that pytest is not
  installed in the environment.
- Runtime services were rebuilt successfully. The active AI Engine fallback is
  still `2500`, but Story Context now rejects that fallback unless Core sends an
  explicit allowance. The active MCP principal currently has empty Core
  `allowedParameters`, so the live request is correctly blocked before provider
  invocation until `maxOutputTokens=4096` is saved through the Core AI
  configuration API/UI.
- DevLog MCP post-rebuild validation was unavailable because the MCP execution
  returned transport `EOF`/HTTP 500 diagnostics during the service restart
  window. During the first rebuilt-runtime attempt, backend logs confirmed the
  request reached Core and was rejected with `BUDGET_CAPACITY_UNKNOWN` before
  the provider because of the `chatgpt` catalog-identity mismatch. That
  mismatch is now covered by a regression test and fixed; a subsequent live
  request was not completed because the MCP transport returned `EOF` during
  restart.

## Documentation

Documentation update: `TOOLS.md` documents the completion policy, rejection
code, threshold and provider fallback rule.

## Vault Outcome

- Vault consulted: NO; repository Story, ADRs, implementation and tests were
  sufficient.
- Vault action: no vault action.

## Known Limitations

- The 4096-token minimum is a bounded policy for the current complex output
  contract, not evidence that every provider/model will complete successfully.
- The default 2000-token allowance now fails closed for complex Story Context
  requests until an explicitly authorized higher allowance is configured.
- The local `.env` currently configures the AI Engine fallback to `2500`; this is
  intentionally not treated as Core authorization and was not silently raised.
- A live complete complex response was not validated in this environment.
- Python behavioral coverage remains unavailable until pytest is installed.

## Commit/Push

- Commit created: NO
- Push performed: NO

## Readiness

READY_FOR_CODE_REVIEW
