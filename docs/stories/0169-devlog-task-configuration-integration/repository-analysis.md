# Repository Analysis - Story 0169

## Repository State

- Branch: `main`.
- HEAD: `bd51f0e` (merged Story 0168).
- Worktree: clean before this analysis artifact.
- DevLog Story Agent context was unavailable in the current tool set.

## Story and Governance

- Story: Story 0169 - DevLog Task Configuration Integration.
- Story status: `PROPOSED - design materialized; implementation not authorized`.
- Epic: Simplifier la configuration des providers et modeles IA de DevLog AI.
- Governing ADR: ADR-073, accepted by the human project owner on 2026-10-30.
- This analysis does not authorize implementation or create an implementation plan.

## Objective

Demonstrate that a user configuration in `READY` state can execute a real DevLog
task through a Core-owned, task-scoped credential binding for both ChatGPT OAuth
and Anthropic API key authentication.

## Current Implementation

### Core binding capability

`AiCredentialBinding` and its service/repository already provide a task-scoped
binding model. Current validation covers the principal, project membership,
task, provider, credential association, expiry, and revocation boundaries.

Relevant area:

- `backend/src/main/java/com/hopeful117/devlogai/aicredentialbinding/`

### AI Engine credential resolution

The AI Engine has a binding registry, credential resolver, authenticated binding
endpoints, and ChatGPT/Anthropic provider adapters. Unknown, expired, revoked,
foreign, and task-mismatched bindings are intended to fail closed before a
provider call.

Relevant areas:

- `ai-engine/app/security/credential_resolver.py`
- `ai-engine/app/services/binding_registry.py`
- `ai-engine/app/api/bindings.py`
- `ai-engine/app/providers/chatgpt.py`
- `ai-engine/app/providers/anthropic.py`

### REST Story Agent path

The principal-aware Story Agent path is implemented through:

- `DevLogStoryAgentController`
- `DevLogStoryAgentService`
- `StoryContextSubmissionService`
- `AnalyzeStoryContextUseCase`

The current submission service issues a binding only for the ChatGPT provider
and passes the authenticated principal. Anthropic user configuration is not yet
connected to this task submission path.

### MCP path

The MCP Story Agent tool delegates to the Core capability and propagates a
principal. It therefore reaches the same current ChatGPT-only behavior, but it
does not yet prove parity for Anthropic or for all configuration/runtime checks.

Relevant area:

- `mcp-server/src/main/java/.../DevLogStoryAgentTool.java`

### Background/general analysis path

`AnalysisWorkflowServiceImpl` creates and submits tasks directly through the AI
Engine client without resolving the authenticated user's `AiUserConfiguration`
or issuing a task-scoped binding. The path has no explicit principal context
for the Story 0169 authorization decision.

Relevant area:

- `backend/src/main/java/com/hopeful117/devlogai/analysis/workflow/AnalysisWorkflowServiceImpl.java`

## Gap Matrix

| Story requirement | Current evidence | Gap |
| --- | --- | --- |
| Resolve active `READY` configuration | User configuration and runtime state exist | Task submission does not consistently consume or enforce them |
| Block unavailable model | Catalog/state logic detects availability | Submission paths do not uniformly block unavailable models |
| Emit task-scoped Core binding | ChatGPT Story Agent path partially does this | Anthropic and background paths are not integrated |
| OAuth ChatGPT vertical slice | Existing Story Agent path is partially wired | End-to-end task execution and parity tests are incomplete |
| Anthropic API-key vertical slice | Provider and binding support exist | No complete task submission path issues and consumes the active Anthropic configuration |
| Principal/project/task/provider validation | Binding service validates these | Only binding-aware paths receive these guarantees |
| Expiry/revocation enforcement | Binding registry/provider resolution support it | Complete task-level coverage is missing |
| REST/MCP/background parity | REST/MCP share the current capability | Background bypasses the binding/configuration decision |
| Metadata cannot override Core | Binding registry rejects arbitrary scopes | Provider/model/configuration propagation is not uniform |
| Existing trust/validation cycle | Existing task and proposal validation remain present | New execution paths must preserve them explicitly |

## Architectural Constraints

- Core remains the sole authority for principal, project, task, provider,
  configuration readiness, and credential binding authorization.
- The AI Engine owns provider secrets and must not infer authorization from
  arbitrary task metadata, project slugs, provider claims, or OAuth tokens.
- `AiCredentialBinding` is task-scoped and must not become a project-global
  credential.
- No provider, credential, or model fallback may be introduced.
- REST, MCP, and background paths must converge on the same Core authorization
  decision.
- Binding identifiers and secrets must not enter prompts, model output,
  callbacks, interaction traces, or user-visible MCP results.
- Grounding and the existing human validation/trust-promotion lifecycle remain
  unchanged.

## Required Decisions or Clarifications

1. Define the principal/identity model for background tasks before implementation.
   A background task cannot safely issue a user-bound binding without an
   explicit principal context or an explicitly approved non-user execution mode.
2. Define the authoritative source and propagation path for the selected model
   and `READY` runtime state at task submission.
3. Confirm whether Story 0169 covers only Story Agent tasks or also the general
   analysis workflow. The Story explicitly requires REST, MCP, and background
   parity, so the background scope must be resolved before planning.
4. Confirm the task/provider contract for Anthropic alongside the existing
   ChatGPT path.

## Test Gaps

The current tests cover individual binding, resolver, association, and Story
Agent behaviors, but do not establish the complete Story 0169 matrix:

- OAuth task execution from a `READY` configuration;
- Anthropic API-key task execution from a `READY` configuration;
- wrong principal, project, task, and provider;
- expired and revoked bindings before provider invocation;
- model unavailable blocking submission;
- metadata tampering not changing Core's decision;
- REST/MCP/background parity;
- non-regression for Stories 0160 and 0161.

## Documentation Outcome

Repository analysis artifact created. No implementation documentation or runtime
configuration was changed. No vault action is proposed from this analysis.

## Workflow Gate

Story 0169 is ready for human review of this Repository Analysis. Implementation
planning must not begin until the human explicitly approves this analysis and
the unresolved background-principal and task-scope decisions are accepted.
