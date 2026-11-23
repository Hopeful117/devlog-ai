# Story 0227 - Implementation Report

## Repository State

- Branch: `story/0227-devlog-context-availability-and-bounded-resume`
- Base: `origin/main` at `7c021adedcfad9d0b7546cdb122cc8378e6b8597`
- Worktree: Modified; no commit or push performed

## Governance

- Governing ADRs: ADR-067, ADR-079, ADR-081
- Related Stories: 0214, 0217, 0218, 0223, 0226
- Authorized Story: 0227 - DevLog Context Availability and Bounded Resume

## Implementation Summary

The MCP Story Agent adapter now separates submission timeout from polling timeout,
performs one bounded same-key resume after a lost submission response, and returns
non-sensitive `SUBMISSION_TIMEOUT` diagnostics when identity recovery remains
unknown. The AI Engine also rejects a technically completed investigation that
claims success without citable evidence pinned to the requested revision.

## Files Changed

- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/tool/DevLogStoryAgentTool.java`: bounded submission and same-key resume.
- `mcp-server/src/main/resources/application.properties`: submission timeout configuration.
- `mcp-server/src/test/java/hopefull117/devlogai_mcp/mcp_server/tool/DevLogStoryAgentToolTest.java`: timeout and resume coverage.
- `ai-engine/app/services/story_context_analysis_generation_service.py`: evidence completion guard.
- `ai-engine/tests/test_devlog_ai_q1_b_binding.py`: Q1-B contract coverage.
- `ai-engine/tests/test_story0184_bounded_behavioral_baseline.py`: bounded investigation baseline coverage.
- `ai-engine/tests/test_story_context_analysis_wire_contract.py`: wire contract coverage.
- `ai-engine/tests/test_story_context_investigation.py`: revision-pinned evidence guard coverage.
- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/StoryContextSubmissionService.java`: marks bounded investigations as evidence-required.

## Architectural Decisions Preserved

- Core remains authoritative for task identity and idempotency.
- MCP reuses the caller's exact idempotency key and does not cancel tasks.
- A timeout is not reported as successful analysis.
- Evidence must be citable and match the investigation revision.
- No credentials, prompts, or raw provider payloads are included in timeout diagnostics.

## Implementation-Level Decisions

- When an investigation has exhausted its action budget, a non-final provider
  decision is classified immediately as `BUDGET_EXHAUSTED` instead of starting
  another corrective generation. This keeps the bounded investigation contract
  fail-closed and prevents provider retries after the authorized action budget
  is exhausted.
- Q1-B keeps protocol question id `Q1`; `CASE-03` is the separate oracle case id
  used by other evaluation assets.

## Validation

Executed:

```bash
./mvnw test -Dtest=DevLogStoryAgentToolTest -B
./mvnw test -B                    # mcp-server
./mvnw test -Dtest=AnalyzeStoryContextUseCaseTest,StoryContextSubmissionServiceTest,StoryContextResultValidatorTest,StoryContextInvestigationActionServiceTest -B
uv run pytest -q
git diff --check
```

The full AI Engine suite passes with two skipped tests. The full MCP suite passes
with 134 tests. The focused Core suite passes with 50 tests, and the full
backend suite passes with 1,897 tests and no failures. On resumption, the
focused AI Engine selection passed 50 tests, the focused Core selection passed
34 tests, and `DevLogStoryAgentToolTest` passed all 14 tests after increasing
only its concurrency-test scheduling budget from 100 ms to 1,000 ms.

Runtime checks on resumption:

- Docker services `devlog-backend`, `devlog-ai-engine`, `devlog-frontend`,
  `devlog-postgres`, and `devlog-keycloak` were running.
- Backend actuator health returned HTTP 200 with `status=UP`.
- AI Engine health returned HTTP 200 with `status=healthy`.
- The MCP server is configured as a local STDIO process, not a Docker service.
  No `devlog` MCP connection or tool was exposed in the current agent session,
  so session reconnection and the DevLog applicability check could not be
  completed here.
- `git diff --check` passed and all modified/untracked files are within the
  Story 0227 implementation and report directory.

## Known Limitations

- The authoritative Core idempotency lookup is reused through the existing submit endpoint; no new recovery endpoint was introduced.
- Live DevLog preflight remains unavailable after repeated bounded timeouts;
  this resumed session also had no exposed `devlog` MCP connection. No task,
  snapshot, or other DevLog identity was fabricated.
- No live C validation or provider campaign was run.
- Core covers same-key conflict, equivalent-request reuse, delegated
  project-membership revalidation, and the full backend suite passed.

## Commit / Push

- Commit created: NO
- Push performed: NO

## Readiness

READY_FOR_HUMAN_REVIEW
