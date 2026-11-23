# Story 0228 - Repository Analysis

## Status

HISTORICAL - superseded by the approved Story and implementation plan.

This analysis records the pre-authorization design review. It does not
authorize live provider execution, budget changes, commits, pushes or merges.

## Objective Reviewed

Story 0228 proposes a Core-owned capability that prepares a bounded ABC live
evaluation through a thin MCP adapter. The capability must produce an immutable,
redacted plan and must not execute a provider call or create/renew a campaign
authorization.

## Repository State

- Branch: `main`
- Current HEAD: `00615dd3fc3fdb07467aa6a990dffe22d1253988`
- Worktree contained the proposed `ADR-082` as an untracked file during this
  analysis.
- The Story and epic registration were part of the design review.
- Production implementation was authorized later; this document is not a
  current implementation status report.

## Governing Constraints

- ADR-082 was `PROPOSED` during this analysis and is now accepted; see ADR-082.
- ADR-066 separates deterministic replay from optional live execution and keeps
  live execution outside normal pull-request CI.
- ADR-067 requires Core ownership of capability semantics, authorization and
  deterministic validation, with transport adapters remaining thin.
- ADR-072 and ADR-073 keep OAuth credentials inside the AI Engine boundary and
  bind provider usage to Core-authorized task/principal identity.
- ADR-081 requires bounded retries, structural error provenance, durable
  idempotency and fail-closed handling of ambiguous results.
- The approved ChatGPT OAuth pricing artifact uses
  `CHATGPT_OAUTH_SUBSCRIPTION_NOT_METERED`, `NOT_APPLICABLE` currency and
  `OPERATIONAL_LIMITS_ONLY`. It does not declare zero cost.

## Existing Relevant Implementation

### Python evaluation runner

`ai-engine/evaluations/devlog_ai_abc_runner/live.py` already validates the
manifest, oracle, revisions, adapters, contracts, budgets, pricing and artifact
isolation. It reports bounded readiness diagnostics and performs no provider
call during preflight.

`ai-engine/evaluations/devlog_ai_abc_runner/live_execution.py` contains the
provider-facing execution boundary and reads credentials only from the process
environment. This boundary must remain separate from preparation.

The current Python preflight is useful as validation evidence and a compatibility
reference, but it must not become a second authority for Core authorization or
plan identity.

### Core idempotency and identity patterns

`AiTaskRepository` and Story Context submission services already support hashed
idempotency keys, scope hashes, canonical request digests and transactional
submission locking.

`StoryContextInvestigationActionService` provides an established pattern for
same-action replay, request-digest conflict detection and bounded action ledgers.

These patterns can guide the new preparation operation, but an `AiTask` itself
represents an AI execution. Reusing it for a provider-free preparation plan
would require proof that its lifecycle and persistence semantics remain correct.
The current evidence favors a dedicated preparation-plan representation, subject
to implementation planning confirmation.

### MCP adapter patterns

`mcp-server/.../StoryContextAgentProtocolTool.java` demonstrates the required
shape for a thin authenticated MCP adapter:

- resolve the MCP principal;
- require the authenticated bearer;
- forward the bearer and delegation to Core;
- serialize the Core response;
- avoid duplicating domain validation.

`DevlogProjectContextClient` provides the existing HTTP client boundary between
MCP Server and Core. Story 0228 should extend this boundary rather than call
Python or the evaluation filesystem directly from MCP.

### Existing live Stories

Stories 0220, 0221, 0223 and 0227 establish revision pinning, bounded captures,
technical-versus-semantic separation, redaction, live preflight behavior and
bounded resume diagnostics. Story 0228 should compose these contracts rather
than redefine them.

## Proposed Capability Boundary

```text
MCP prepare_live_evaluation
        |
        v
MCP Server thin adapter
        |
        v
Core preparation application capability
        |
        +--> deterministic manifest/revision/worktree validation
        +--> non-secret OAuth/MCP readiness projection
        +--> immutable idempotent preparation plan
        |
        v
Python live runner revalidation
        |
        v
Separate campaign-authorized live execution
```

Preparation must terminate before provider construction or provider network
access. The live runner must revalidate the plan, runtime identity, campaign
authorization and expiration immediately before any provider call.

## Required Contract Areas

The implementation plan must define exact versioned contracts for:

- benchmark, case, manifest and oracle identity;
- source and runtime revisions;
- treatment identities and answer contracts;
- operational budgets and stop conditions;
- provider/model and non-metered pricing identity;
- artifact destination reference;
- OAuth/MCP readiness state without credential values;
- plan identity, request digest and idempotency scope;
- bounded blocker codes and diagnostic metadata;
- plan expiration and revalidation state.

## Persistence Finding

The repository has durable Core entities and idempotency patterns, but no
existing evaluation-preparation aggregate was found. The Implementation Plan
must decide between:

1. a dedicated immutable preparation-plan entity/repository/migration; or
2. an existing operation/projection mechanism whose semantics can prove durable
   idempotency and immutable plan retrieval without coupling the plan to AI task
   execution.

The plan must not choose an existing mechanism solely to avoid a migration. The
chosen representation must support concurrent same-key requests, conflict
detection, authorized retrieval and redacted plan persistence.

## Security Findings

- OAuth tokens must not cross the AI Engine provider boundary.
- MCP responses must not include bearer tokens, delegation tokens, prompts,
  provider payloads or full repository content.
- Error messages must use bounded codes and safe metadata.
- A missing or expired delegation must remain `NOT_AUTHORIZED`; preparation must
  not renew or broaden it.
- `NOT_MEASURED` must remain distinct from monetary zero.

## Validation Findings

The provider-free ABC tests already cover manifest/oracle integrity, redaction,
idempotency boundaries and the non-metered pricing validator. Additional tests
are required for the new Core plan lifecycle, MCP adapter, concurrent replay and
runner-side plan revalidation.

The existing integration simulation can validate local boundaries, but it does
not prove live provider quality or authorize live execution. Story 0228 must not
change that interpretation.

## Risks

1. Duplicating Python preflight logic in Core could create divergent authorities.
2. Reusing `AiTask` could accidentally imply provider execution or create task
   lifecycle side effects.
3. Persisting arbitrary artifact filesystem paths could leak local topology or
   create unsafe write behavior.
4. A plan can become stale between preparation and execution; final
   revalidation is mandatory.
5. OAuth readiness metadata can prove configuration state but cannot prove
   provider semantic success.
6. The current ABC manifest revision differs from the current repository HEAD;
   the preparation capability must report this mismatch, not silently repoint
   the benchmark.

## Open Questions for the Implementation Plan

- What exact Core entity or operation store owns the immutable plan?
- Are blocked plans persisted, returned transiently, or both?
- What is the retention period and cleanup mechanism for plans?
- Which non-secret OAuth/MCP readiness states are exposed?
- Is a public REST adapter required, or is the Core HTTP boundary plus MCP
  adapter sufficient for this Story?
- Is the artifact destination a logical reference only, with the runner owning
  local directory creation?
- Which existing Core service owns worktree/revision validation, and which
  checks must remain in the Python runner?

## DevLog Context Limitation

The DevLog applicability request was attempted but the service connection closed.
No usable Story UUID, snapshot, freshness report or historical DevLog context was
available. This analysis therefore relies on the current repository, ADR-082,
the ABC runbook, and the existing Stories and implementations listed above.

## Recommendation

Approve this Repository Analysis with the persistence representation and REST
surface left as explicit Implementation Plan decisions. After approval, produce
an Implementation Plan that resolves those two choices, defines the exact DTOs
and migrations, and maps each acceptance criterion to focused tests before any
production code is changed.
