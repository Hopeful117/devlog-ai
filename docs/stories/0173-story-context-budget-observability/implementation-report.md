# Implementation Report - Story 0173

## Result

Implemented in the working tree and ready for human review. No commit or push
has been performed.

## Production Changes

- Added documented model context capacities to the Core provider catalog.
- Added Core fail-closed budget preflight before authenticated provider
  submission, including input estimate, output allowance, safety margin and
  bounded failure codes.
- Made the preflight outcome explicit as `AUTHORIZED`, `PROJECTION_REDUCED` or
  `REJECTED`, including rejection before provider invocation when the configured
  output allowance cannot fit the model capacity.
- Persisted budget accounting in the immutable task snapshot.
- Added normalized execution phase, provider status, incomplete reason and
  measurement certainty to interaction traces.
- Added Flyway migration `V72` and JPA persistence fields for the new trace data.
- Normalized ChatGPT incomplete-response diagnostics without persisting raw
  provider output.
- Added bounded Story Context metrics for preflight, provider-incomplete,
  parsing and validation failures.
- Updated `TOOLS.md` with runtime diagnostic and retention semantics.

## Tests Added/Modified

- Added `StoryContextBudgetPreflightTest` for known and unknown model capacity.
- Added deterministic tests for projection-reduced and insufficient-output
  preflight outcomes.
- Updated the migration-version expectation to include V72.
- Preserved existing constructor compatibility for existing unit fixtures.

## Commands Executed

```bash
./backend/mvnw -pl backend test -Dtest=StoryContextBudgetPreflightTest,StoryContextSubmissionServiceTest,AiProviderCatalogTest,AiInteractionTracePersistenceServiceTest,AiTaskResultServiceTest -B
./backend/mvnw -pl backend test -Dtest=AnalyzeStoryContextUseCaseTest,AiUserConfigurationPostgresIntegrationTest,StoryContextBudgetPreflightTest -B
./backend/mvnw -pl backend test -Dtest=StoryContextBudgetPreflightTest,StoryContextSubmissionServiceTest,AnalyzeStoryContextUseCaseTest -B
./backend/mvnw -pl backend -am clean verify -B
python3 -m py_compile app/providers/base.py app/providers/chatgpt.py app/schemas/interaction_trace.py app/services/interaction_trace.py app/providers/mock.py app/providers/openai.py app/providers/anthropic.py app/providers/gemini.py app/providers/openrouter.py
git diff --check
```

## Test Results

- Targeted Java tests: PASS, including the four budget-preflight tests and the
  submission/orchestration regression set of 28 tests.
- Regression/integration tests: PASS, 27 tests.
- Full backend verification: PASS, 1602 tests, JaCoCo checks passed.
- Python syntax compilation: PASS.
- Python pytest suite: NOT RUN; `pytest` is not installed in the environment.

## Documentation

Documentation update: `TOOLS.md` updated with budget diagnostic fields,
fail-closed behavior and retention boundaries.

## Vault Outcome

- Vault consulted: NO; no relevant vault context was required.
- Vault action: no vault action.
- Rationale: repository ADRs, Story artifacts and current source were sufficient.

## Known Limitations

- Gemini and OpenRouter models without a Core catalog entry remain fail-closed
  until their model-specific context capacity is supplied.
- Live provider reproduction was not run and is not required for CI validation.
- Python behavioral tests remain unavailable until the repository test dependency
  is installed.

## Commit/Push

- Commit created: NO
- Push performed: NO

## Readiness

READY_FOR_CODE_REVIEW
