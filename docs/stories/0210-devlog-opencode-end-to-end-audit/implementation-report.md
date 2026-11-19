# Story 0210 - Implementation Report

## Repository State

- Branch: `main`
- HEAD: `b97e88a0`
- Worktree: Modified before this Story; unrelated Story 0209 and workspace
  changes were preserved.

## Governance

- Governing ADRs: ADR-006, ADR-063, ADR-066, ADR-067, ADR-079
- Authorized Story: Story 0210 - DevLog to OpenCode End-to-End Audit and
  Comparative Evaluation
- Human approval: received before implementation

## Files Changed For Story 0210

- `ai-engine/evaluations/story_context_specificity_live.py`: deterministic
  Story-0210 idempotency keys, frozen manifest selection, terminal-status
  preservation, bounded failure captures and unique live campaign identities.
- `ai-engine/evaluations/story_context_e2e_v2.py`: protocol-v2 capture
  aggregation separating direct-context availability from OAuth answer quality.
- `ai-engine/evaluations/scenarios/story-0210-e2e-v2/manifest.json`: frozen
  five-scenario protocol-v2 evaluation manifest.
- `ai-engine/tests/test_story_context_specificity_live.py`: deterministic key,
  terminal capture and manifest contract tests.
- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/service/McpToolObservabilityService.java`:
  request-scoped principal support for event ingestion.
- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/tool/DevLogStoryAgentTool.java`:
  passes the authenticated MCP request principal to observability.
- `mcp-server/src/test/java/hopefull117/devlogai_mcp/mcp_server/tool/DevLogStoryAgentToolTest.java`:
  verifies the request-scoped principal is supplied on failure events.

## Implementation Summary

The live Story Context evaluator now produces reproducible idempotency keys,
uses the Story-0210 scenario corpus, preserves terminal execution statuses and
records bounded failed conditions instead of aborting the entire campaign. MCP
tool observability now uses the principal authenticated for the current request,
which preserves event ingestion in request-scoped/stdio execution.

## Architectural Decisions Preserved

- Java/Core remains authoritative for context, scope, grounding and callback
  acceptance.
- Python evaluation code does not gain repository, database or credential access.
- Failed, timed-out and incomplete executions are not represented as successful
  analyses.
- Provider-visible reference attribution remains explicitly unknown when the
  callback contract does not expose it.
- Existing unrelated worktree changes were not reverted or included.

## Tests Added/Modified

- `ai-engine/tests/test_story_context_specificity_live.py`: 4 tests covering
  deterministic identity, terminal status preservation and the five-scenario
  manifest.
- `mcp-server/src/test/java/hopefull117/devlogai_mcp/mcp_server/tool/DevLogStoryAgentToolTest.java`:
  updated observability invocation contract.

## Commands Executed

```bash
uv run pytest tests/test_story_context_specificity_live.py -q
PYTHONPATH=evaluations uv run pytest evaluations/test_story_context_runtime_readiness.py -q
uv run pytest tests/test_story_context_analysis_wire_contract.py tests/test_story_context_response_quality.py -q
uv run pytest tests/test_story_context_specificity_live.py tests/test_story_context_response_quality.py -q
uv run pytest tests/test_story_context_e2e_v2.py tests/test_config.py tests/test_llm_providers.py tests/test_chatgpt_oauth.py -q
uv run python -m py_compile evaluations/story_context_specificity_live.py tests/test_story_context_specificity_live.py
./backend/mvnw -pl mcp-server -am -Dtest=McpToolObservabilityServiceTest,DevLogStoryAgentToolTest -Dsurefire.failIfNoSpecifiedTests=false test -B
uv run python evaluations/story_context_specificity_live.py --target-revision f1729828e59b90f34a5f2c41730e22920417ddbf --output /tmp/opencode/story0210-live-v6-oauth-context
uv run python evaluations/story_context_e2e_v2.py /tmp/opencode/story0210-live-v6-oauth-context --manifest evaluations/scenarios/story-0210-e2e-v2/manifest.json --target-revision f1729828e59b90f34a5f2c41730e22920417ddbf
```

## Test Results

- Story-0210 Python tests: passed, 4 tests.
- Runtime readiness tests: passed, 11 tests.
- Existing response-quality/wire-contract tests: passed, 31 tests.
- Live evaluator/response-quality tests: passed, 12 tests.
- Protocol-v2 evaluator/configuration tests: passed, 59 tests.
- MCP focused tests: passed, 15 tests.
- Python compilation: passed.
- Diff whitespace validation: passed.
- Live external-provider evaluation: executed; campaign v4 was partial because
  provider capacity was unavailable for the baseline and later Story Agent
  cells. The active local configuration was subsequently restored to the
  `openai_chatgpt` OAuth provider and the AI Engine container was recreated.

## Documentation Reconciliation

Documentation update: the canonical Story and this implementation report were
updated. No broader architecture or user-facing documentation update is
required for these bounded evaluator and observability changes.

## Known Limitations

- A complete answer-quality comparison is not defined for the direct-context
  condition; it is intentionally a context-availability arm in protocol v2.
- The existing aggregate runtime report remains tied to its historical Story
  0158 manifest and was not generalized in this bounded implementation.
- Qualitative faithfulness, relevance, actionability and context efficiency
  still require human review.
- The worktree contains pre-existing unrelated modifications and untracked
  files.

## Independent Review

An independent review identified and the implementation corrected:

- incomplete Story Agent results being counted as successful;
- unsupported terminal-status handling;
- infrastructure failures being conflated with evaluation failures;
- unbounded raw context/provider capture;
- partial manifest enforcement;
- missing MCP read deadlines;
- non-JSONL capture output;
- insufficient request-scoped principal test coverage.

The corrected focused test suite passed after these changes.

## Live Evaluation

The live runner was executed with the protocol-v2 manifest and three
repetitions per scenario. The OAuth campaign produced 30 bounded JSONL captures
outside the repository under
`/tmp/opencode/story0210-live-v6-oauth-context`.

The repository revision was aligned to
`f1729828e59b90f34a5f2c41730e22920417ddbf` before the final run. Campaign
identity includes the revision and repetition so Core submission de-duplication
cannot silently reuse an earlier capture.

The direct-context arm produced 15 `SUCCESS` captures with 100% context
availability and revision consistency. The OAuth Story Agent arm produced 13
`SUCCESS`, 1 `NOT_EVALUATED` timeout and 1 `INVALID_CAPTURE`; its execution
completeness is 86.7%.

The protocol-v2 aggregate reports the direct-context arm as a retrieval and
budget diagnostic, not as a generated-answer quality baseline. OAuth answer
quality remains `HUMAN_REVIEW_REQUIRED`; no semantic delta is claimed from the
direct-context arm. The active runtime provider is `openai_chatgpt` with OAuth;
the local `LLM_API_KEY`, if present, is not used by that provider.

## Vault Outcome

- Vault consulted: no.
- Outcome: no vault action.
- Rationale: the Story concerns repository-local MCP, evaluator and observability
  contracts; no curated vault context was required to determine implementation.

## Git Diff Summary

The Story-specific changes are limited to the evaluator, its five-scenario
manifest, focused tests, and MCP observability principal propagation. The full
worktree diff also contains pre-existing unrelated changes and was not staged or
committed.

## Commit/Push

- Commit created: NO
- Push performed: NO

## Readiness

READY_FOR_HUMAN_REVIEW
