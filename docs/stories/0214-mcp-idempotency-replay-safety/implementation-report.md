## Implementation Report

### Repository State

- Branch: reported from the current worktree; no branch integration performed.
- HEAD: inspected before implementation.
- Worktree: modified files listed below, including pre-existing Story planning artifacts.

### Governance

- Governing ADR(s): ADR-077, ADR-078, ADR-081.
- Authorized Story: Story 0214 - Idempotence et securite du rejeu MCP.
- DevLog context: unavailable for this implementation pass; repository artifacts
  and current source were used as authority.

### Files Changed

- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/tool/DevLogStoryAgentTool.java`:
  requires a non-blank caller-provided idempotency key for Story Agent calls.
- `tools/devlog_mcp/runtime.py`: allows the bounded transport-auth replay for
  `devlog_story_agent` only when its MCP arguments contain a non-blank
  `idempotencyKey`.
- `mcp-server/src/test/java/hopefull117/devlogai_mcp/mcp_server/tool/DevLogStoryAgentToolTest.java`:
  covers missing-key rejection and updates request-scoped key propagation.
- `backend/src/main/java/com/hopeful117/devlogai/ai/task/entity/AiTask.java`,
  the repository/use-case files, and migration
  `V88__scope_story_agent_idempotency.sql`: scope idempotency records by
  operation, project, principal, and delegation context with a composite unique
  index.
- `mcp-server/src/test/java/hopefull117/devlogai_mcp/mcp_server/StdioProtocolHygieneTest.java`:
  verifies the generated MCP schema requires `idempotencyKey`.
- `tools/devlog_mcp/tests/test_runtime.py`: covers keyed and unkeyed Story Agent
  replay behavior.
- `TOOLS.md`: documents the explicit idempotency-key requirement and bounded
  replay exception.
- `docs/epics/mcp-error-resilience-policy/epic.md`: links materialized Stories
  0212 through 0214.
- `docs/stories/0214-mcp-idempotency-replay-safety/`: Story, repository
  analysis, implementation plan, and this report.

### Implementation Summary

The MCP Story Agent boundary now rejects absent or blank idempotency keys before
authentication/backend submission. The runtime keeps all existing read-only
retry behavior and extends the single transport-auth retry to Story Agent only
when the caller supplied that key. Core now scopes the key lookup by operation,
project, principal, and delegation context. Mutations outside the approved
class remain fail-closed and are not replayed.

### Architectural Decisions Preserved

- Java Core remains authoritative for idempotency-key hashing, request digest
  comparison, scoped conflict detection, and result reuse.
- Runtime `operationId` remains a lifecycle/observability identifier and is not
  treated as a durable business idempotency key.
- `register_project`, `sync_project_history`, follow-ups, and `429` handling
  were not expanded in this Story.
- Bearer renewal, delegation, and normalized Core error ownership remain in
  their existing Stories.

### Tests Added/Modified

- `DevLogStoryAgentToolTest`: 11 tests pass, including missing-key rejection.
- `AnalyzeStoryContextUseCaseTest`: 24 tests pass, including scoped key
  lookup/conflict behavior and legacy unscoped-key rejection.
- `StdioProtocolHygieneTest`: 2 tests pass, including the required schema field.
- `test_runtime.py`: keyed/unkeyed Story Agent replay coverage added.
- Full MCP module and Python runtime suites executed successfully.

### Commands Executed

```bash
python3 -m unittest tools/devlog_mcp/tests/test_runtime.py -v
python3 -m unittest discover -s tools/devlog_mcp/tests -v
./backend/mvnw -pl mcp-server test -Dtest=DevLogStoryAgentToolTest -B
./backend/mvnw -pl mcp-server test -Dtest='DevLogStoryAgentToolTest,StdioProtocolHygieneTest' -B
./backend/mvnw -pl backend -am test -Dtest=AnalyzeStoryContextUseCaseTest -Dsurefire.failIfNoSpecifiedTests=false -B
./backend/mvnw -pl mcp-server test -B
git diff --check
```

### Test Results

- Python targeted runtime suite: 48 tests passed.
- Python complete runtime test discovery: 50 tests passed.
- Java targeted Story Agent suite: 11 tests passed.
- Java MCP schema and Story Agent suites: 13 tests passed.
- Java backend `AnalyzeStoryContextUseCaseTest`: 23 tests passed.
- Java complete MCP module suite: 115 tests passed.
- `git diff --check`: passed.
- Maven emitted existing Mockito dynamic-agent warnings; no test failures.

### Known Limitations

- No idempotency contract was added to `register_project`, `sync_project_history`,
  or Story Agent follow-ups.
- No `429` policy was implemented.
- The full backend suite was not run; the focused backend reactor test passed.
- Legacy keyed rows without an idempotency scope are intentionally non-replayable.
  They fail closed and require status reconciliation followed by a new scoped
  key; automatic migration was not attempted because historical authorization
  context is unavailable.

### Implementation-Level Decisions Made

- A Story Agent call without a non-blank caller-provided key is invalid and does
  not reach Core.
- The runtime determines keyed replay safety from the MCP tool arguments and
  never from the local `operationId`.

### Blockers / Repository Inconsistencies

- No implementation blocker remains.
- Human acceptance is still required; the agent does not declare Story
  acceptance.

### Git Diff Summary

The worktree contains the Story 0214 documentation artifacts plus the MCP Java,
Python runtime, tests, and documentation changes listed above.

### Commit/Push

- Commit created: NO.
- Push performed: NO.

### Readiness

READY_FOR_HUMAN_REVIEW
