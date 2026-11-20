# Story 0213 - Implementation Report

## Repository State

- Branch: `main`.
- Worktree: modified files listed below; no commit, push or merge performed.

## Governance

- Governing ADRs: ADR-077, ADR-078 and ADR-081.
- Governing Stories: 0172, 0211, 0212 and 0213.
- Human implementation authorization: confirmed in the session after the
  Story 0213 plan was approved.
- DevLog context: unavailable in this environment; repository artifacts were
  used as authoritative context.

## Files Changed

- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/service/CoreCallPolicy.java`:
  shared non-retrying mapping for Core HTTP and transport failures.
- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/resource/ResourceSupport.java`:
  uses the shared policy while preserving non-disclosing resource outcomes.
- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/tool/SyncProjectHistoryTool.java`:
  uses the shared policy for mutation failures.
- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/tool/RegisterProjectTool.java`:
  maps authenticated registration failures without backend details.
- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/config/BearerTokenAuthInterceptor.java`:
  avoids emitting blank or null authentication headers and preserves delegation
  propagation when Authorization is explicitly supplied.
- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/config/DevlogBackendClientConfiguration.java`:
  removes exception messages and stack traces from backend request logs.
- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/service/McpToolObservabilityService.java`:
  keeps best-effort ingestion diagnostics non-sensitive.
- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/companion/CompanionAppTool.java`:
  returns bounded Companion errors without backend exception messages.
- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/companion/GetContextTraceTool.java`:
  removes backend exception details from trace errors.
- `mcp-server/src/test/java/hopefull117/devlogai_mcp/mcp_server/service/CoreCallPolicyTest.java`:
  covers authorization, non-disclosure, rate limiting, transient failures and
  functional conflicts.
- `mcp-server/src/test/java/hopefull117/devlogai_mcp/mcp_server/config/BearerTokenAuthInterceptorTest.java`:
  covers explicit Authorization with delegation and blank identity headers.
- `mcp-server/src/test/java/hopefull117/devlogai_mcp/mcp_server/companion/CompanionErrorSanitizationTest.java`:
  covers bounded Companion and Context Trace errors.
- `docs/stories/0213-mcp-core-call-policy/`: Story, analysis, plan and report.

## Implementation Summary

Core failures now use one bounded policy across the shared resource support and
the affected mutation tools. `401`, `403`, `429`, conflicts, server failures
and transport failures receive stable, non-sensitive classifications; protected
resource reads retain their existing non-disclosing `404` behavior.

No Java retry was introduced. Bearer and delegation propagation remains
principal-driven, while backend and observability logs no longer include
exception messages, response bodies or stack traces.

## Confirmed Decisions Applied

- Core `401` remains distinct from transport bearer renewal and preserves the
  ADR-081 data category `CORE_AUTHENTICATION`.
- Core `403` is classified as `CORE_AUTHORIZATION` unless a protected resource
  must remain non-disclosing.
- `429` maps to `RATE_LIMITED` without retry metadata or retry behavior.
- Mutations fail closed and are never implicitly replayed.
- Observability remains best-effort and does not replace the primary tool result.
- No runtime Python, provider credential or idempotency behavior was changed.

## Commands Executed

```bash
./mcp-server/mvnw test -Dtest=ResourceSupportTest,CoreCallPolicyTest,SyncProjectHistoryToolTest,RegisterProjectToolTest,McpToolObservabilityServiceTest -B
./mcp-server/mvnw test -B
git diff --check
```

## Test Results

- Focused MCP tests: 40 passed, 0 failures, 0 errors.
- Full MCP module tests: 114 passed, 0 failures, 0 errors.
- `git diff --check`: passed.
- Live Core, OAuth and provider integration validation: not run.

## Known Limitations

- The complete repository test suite was not run; validation covered the MCP
  module affected by this Story.
- DevLog context retrieval was unavailable in this environment.
- Story acceptance remains a human decision.

## Commit/Push

- Commit created: NO.
- Push performed: NO.

## Readiness

READY_FOR_CODE_REVIEW
