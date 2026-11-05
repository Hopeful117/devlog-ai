# Implementation Report - Story 0184

## Repository State

- Branch: `main`
- HEAD: `d83600a`
- Worktree: modified; Story 0184 implementation and documentation files are
  listed below.
- Commit created: NO
- Push performed: NO

## Governance

- Governing Story: Story 0184 - Bounded Story Context Investigation and Targeted
  Retrieval
- Governing ADRs: ADR-006, ADR-063, ADR-067, ADR-069, ADR-079
- Repository Analysis: approved by the human
- Implementation Plan: approved by the human
- Story acceptance: not granted; awaiting human review

## Implementation Summary

Story Context now has an explicit opt-in bounded investigation path. Java/Core
owns the typed retrieval capabilities, validates project/revision/path/budget
scope, executes deterministic search/read operations, persists bounded action
metadata, and extends the grounding contract only with canonical citable
evidence. The AI Engine retains the existing v1 one-shot path and, only when
Core initializes the investigation state, runs a strict v2 plan/action/final
loop through the authenticated Core action endpoint.

The capability is disabled by default. No RAG, embedding, vector search,
database migration, initial-context budget increase, or trusted-knowledge
promotion was introduced.

## Files Changed

- `backend/src/main/java/.../storycontextanalysis/investigation/`: typed action,
  scope, budget, usage, capability, result, and validation contracts.
- `backend/src/main/java/.../InvestigationCapabilityExecutionService.java`:
  Core-owned deterministic search/read execution and bounded evidence projection.
- `backend/src/main/java/.../StoryContextInvestigationActionService.java`:
  authenticated transactional action execution, idempotency, snapshot ledger,
  and grounding-contract extension.
- `backend/src/main/java/.../StoryContextAgentProtocolController.java`:
  strict authenticated investigation action endpoint.
- `backend/src/main/java/.../StoryContextSubmissionService.java`:
  explicit opt-in initialization and bounded investigation metadata propagation.
- `backend/src/main/java/.../AiTaskRepository.java`:
  pessimistic task lookup used to serialize action execution.
- `ai-engine/app/schemas/story_context_investigation.py`:
  strict versioned v2 plan/action/result contracts.
- `ai-engine/app/prompts/story_context_analysis.py`:
  bounded investigation prompt and decision schema.
- `ai-engine/app/services/story_context_analysis_generation_service.py`:
  bounded provider/Core action loop and terminal handling.
- `ai-engine/app/clients/core_callback_client.py`:
  HMAC-authenticated Core action client.
- `docs/logging-policy.md`:
  operator-visible investigation enablement and diagnostic boundaries.
- `docs/stories/0184-bounded-story-context-investigation/`:
  Story, Repository Analysis, Implementation Plan, and this report.

## Architectural Decisions Preserved

- Java/Core remains the authority for scope, revision, evidence identity, trust,
  retrieval authorization, grounding, and final callback validation.
- Python has no repository, database, filesystem, or retrieval-index access.
- Retrieved evidence is usable for grounding only after Core canonical identity,
  provenance, trust, citability, and revision checks.
- v1 one-shot Story Context behavior remains unchanged unless the request
  explicitly initializes `guidance.investigation.enabled=true`.
- Incomplete provider output remains terminal and is never parsed as success.
- Investigation state remains bounded non-trusted execution metadata.

## Tests Added or Modified

- Core investigation contract and validator tests.
- Core capability execution tests for valid search/read, empty results,
  duplicates, limits, and revision mismatch.
- Core action controller/service tests for authentication, initialization,
  idempotency, snapshot state, and grounding updates.
- Submission tests for absent, disabled, valid, and invalid investigation
  initialization.
- AI Engine investigation schema, callback-client, loop, grounding synchronization,
  and bounded behavioral baseline tests.
- The baseline covers broad/truncated context, targeted retrieval, empty result,
  action rejection, budget exhaustion, and v1 compatibility.

## Commands Executed

```bash
./backend/mvnw -pl backend test -Dtest='*Investigation*Test,StoryContextAgentProtocolControllerTest,StoryContextSubmissionServiceTest,StoryContextAgentCallbackFacadeTest,StoryContextCallbackIdentityValidatorTest,StoryContextResultValidatorTest,StoryAgentFollowUpServiceTest' -B
./backend/mvnw -pl backend test -Dtest=CommitChangedFilesEagerFetchIntegrationTest -B
./backend/mvnw -pl backend -am test -B
uv run --project ai-engine --extra dev pytest ai-engine/tests/test_story_context_investigation.py ai-engine/tests/test_core_callback_client.py -q
uv run --project ai-engine --extra dev pytest ai-engine/tests/test_story0184_bounded_behavioral_baseline.py -q
python3 -m compileall -q ai-engine/app ai-engine/tests/test_story_context_investigation.py
git diff --check
```

## Test Results

- Focused backend Story Context/investigation suite: **40 tests passed**.
- Spring application-context integration test: **1 test passed** after fixing
  constructor selection for the new service.
- Full backend reactor suite: **1650 tests passed**, build successful.
- Story 0184 focused Python tests: **19 tests passed**.
- Story 0184 bounded behavioral baseline: **5 tests passed**.
- Python bytecode compilation: **passed**.
- `git diff --check`: **passed**.

The full AI Engine suite was also executed. It contains seven existing failures
outside Story 0184, including retry fixtures that construct incomplete `Prompt`
mocks and an existing causal-grounding fixture expectation. Those failures were
not modified or hidden. The Story 0184-specific tests pass independently.

## Known Limitations

- The bounded analyst remains disabled by default and has not been enabled in
  the production Docker configuration.
- Live provider validation of the new v2 loop was not performed with credentials.
- The evaluation is credential-free behavioral coverage, not a quality benchmark
  over a live repository corpus. Default enablement requires a subsequent
  measured decision.
- The action endpoint currently exposes the minimal search/read capability set;
  additional evidence families require separate contracts and tests.
- DevLog MCP context was unavailable during this implementation; repository and
  canonical Story/ADR evidence were used instead.

## Documentation Reconciliation

Documentation update: **required and completed**.

`docs/logging-policy.md` now documents the opt-in investigation boundary,
supported capabilities, bounded diagnostics, stop reasons, and terminal provider
failure semantics. Story-local implementation artifacts were also added. No
generic architecture or RAG documentation was changed because this Story does
not introduce hybrid retrieval.

## Vault Outcome

The Obsidian vault was not consulted. No vault action is proposed because the
implemented behavior and operational guidance are repository-specific and are
documented in the canonical repository Story and logging policy.

## Readiness

**READY_FOR_HUMAN_REVIEW**

Human acceptance is still required. No commit, push, merge, or deployment has
been performed.
