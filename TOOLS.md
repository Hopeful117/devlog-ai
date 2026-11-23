# DevLog MCP Tooling

## Purpose

This document describes the local MCP actor and delegation configuration. It
contains no bearer, delegation token, OAuth token or other secret value.

## MCP Tools Catalog

### Context and investigation

| Tool | Description |
|------|-------------|
| `devlog_story_agent` | Submits a Story Context Agent task. Returns structured, trust-tiered EngineeringContext for a project and intent. Requires `projectSlug`, `intent`, `idempotencyKey`. |
| `get_engineering_context` | Returns raw EngineeringContext for a project and intent. Requires `projectSlug`, `intent`, `files`. |
| `analyze_story_context` | Submits a Story-scoped context analysis (deprecated flow — prefer `devlog_story_agent`). |
| `search_project_history` | Keyword search over imported git commits (messages + file paths). Requires `projectSlug`, `query`. |
| `resolve_engineering_story` | Resolves an Engineering Story by number within a project. |
| `get_devlog_context_trace` | Returns diagnostics for a previous Story Context execution. |

### Project administration

| Tool | Description |
|------|-------------|
| `register_project` | Registers a new DevLog project. |
| `sync_project_history` | Triggers a git history import for a project's active source. |
| `sync_project_stories` | Synchronises Engineering Stories from the repository into DevLog. |

### Proposal review and acceptance

Typical sequence: `list_pending_proposals` → `get_proposal_review` → `validate_proposal`.

| Tool | Description |
|------|-------------|
| `list_pending_proposals` | Lists all proposals with status `PROPOSED` (pending human review) for a project. Returns `id`, `type`, `analysisId`, `sourceIndex`, `confidence`, `createdAt`, and a payload summary. |
| `get_proposal_review` | Returns a paginated review page for an analysis (PROPOSED items first). Includes full payload, evidence references, supporting facts, and existing validation decision. Use `analysisId` from `list_pending_proposals`. |
| `validate_proposal` | Accepts or rejects a proposal. `ACCEPTED` promotes it to trusted knowledge (Insight, EngineeringEvent, etc.). Requires authenticated MCP principal. Optional `insightSeverity` (LOW/MEDIUM/HIGH/CRITICAL) for INSIGHT proposals. |

### Companion and diagnostics

| Tool | Description |
|------|-------------|
| `open_devlog_companion` | Opens the DevLog Companion showing recent MCP tool activity for a project. |
| `story_context_agent_get_snapshot_authenticated` | Returns the snapshot from a previous Story Context Agent execution. |
| `story_context_agent_get_projection` | Returns the projection from a previous Story Context Agent execution. |

### Live evaluation

| Tool | Description |
|------|-------------|
| `prepare_bounded_live_evaluation` | Prepares a bounded live evaluation run. |
| `get_bounded_live_evaluation_preparation` | Returns the preparation status for a live evaluation. |
| `request_bounded_live_evaluation_authorization` | Requests human authorization for a live evaluation. |
| `get_bounded_live_evaluation_authorization` | Returns the current authorization status. |
| `approve_bounded_live_evaluation_authorization` | Approves a live evaluation authorization (human gate). |
| `consume_bounded_live_evaluation_authorization` | Consumes an approved authorization to start the live run. |

## Repository mapping

The canonical DevLog project mapping for this repository is:

```text
repository: devlog-ai
projectSlug: devlog-ai
```

Agents must use this mapping for DevLog preflight requests. A repository Story number is not a
DevLog Story UUID and must not be sent as `storyId` without a verified mapping.

## MCP actor

The MCP runtime authenticates as a technical Keycloak client. Core identifies
that client from the configured `azp` or `client_id` claim and represents it as
`AI_AGENT`.

Required configuration:

```text
KEYCLOAK_MCP_ACTOR_CLIENT_ID=<technical-client-id>
KEYCLOAK_ISSUER_URI=<issuer-uri>
KEYCLOAK_AUDIENCE=<configured-audience>
KEYCLOAK_MCP_AGENT_PRINCIPAL_ID=<agent-account-keycloak-subject>
KEYCLOAK_MCP_AGENT_PROJECTS=<comma-separated-authorized-project-slugs>
```

## Agent DevLog identity

Agent-owned DevLog accounts use the convention:

```text
<agent-id>-agent
```

Examples:

```text
opencode-agent
claude-agent
```

The account identifies the agent-owned DevLog principal that owns its provider
configuration and ChatGPT OAuth association. It is distinct from the technical
Keycloak MCP actor and from any human user account.

Core maps the authenticated MCP actor to this principal for autonomous agent
operations. The mapping is explicit and deployment-scoped; it is not inferred
from a project slug, prompt, or caller-supplied identity.

The client-credentials bearer is transport authentication only. It is not a
human identity and it does not authorize a project or an AI credential.

## Human delegation

A human project owner creates a bounded delegation through Core:

```text
POST /api/v1/mcp/delegations
{
  "projectSlug": "<project-slug>",
  "scopes": ["MCP_ENGINEERING_CONTEXT_READ"],
  "ttlSeconds": 3600
}
```

The response contains an opaque delegation token. Store it only in a protected
local runtime secret store or environment supplied to the MCP process.

```text
DEVLOG_MCP_DELEGATION_TOKEN=<opaque-token>
DEVLOG_MCP_DELEGATION_AUDIENCE=devlog-mcp
```

Do not put the delegation token in prompts, tool arguments, repository files,
logs, traces or committed configuration.

## Revocation

The human owner can revoke a delegation with:

```text
DELETE /api/v1/mcp/delegations/<grant-id>
```

Revocation blocks future delegated Core operations. It does not revoke a
provider credential or rewrite historical task records.

## Boundaries

- Core owns actor classification, delegation validation, project authorization
  and `AiCredentialBinding` issuance.
- MCP transports the technical bearer and opaque delegation proof only.
- The AI Engine owns ChatGPT provider tokens.
- Delegation renewal and transport bearer renewal are separate lifecycles.
- Missing, expired or revoked delegation fails closed.

## Local verification

Use the backend and MCP module tests before the full backend verification:

```bash
./backend/mvnw -pl backend test
./backend/mvnw -pl mcp-server test
./backend/mvnw -pl backend -am clean verify -B
```

## Story Context budget diagnostics

Story Context executions record bounded budget and provider diagnostics. Known
catalog models use their configured context capacity; executions with unknown
capacity fail closed before provider invocation. `maxOutputTokens` remains an
independent output allowance and is not treated as measured provider usage.

Normal traces contain metadata only. Diagnostic traces follow the configured
short retention policy, while the immutable Core execution snapshot remains a
separate audit/grounding artifact. Diagnostics must not contain credentials,
authorization headers, full prompts, repository payloads or raw provider output.

Provider-specific incomplete responses are terminal failures. The normalized
fields include execution phase, provider status, bounded incomplete reason and
measurement certainty; unavailable provider values are represented as `UNKNOWN`.

Story Context response completion policy uses the Core-owned
`story-context-completion-v1` policy. The current complex Story Context output
contract requires an explicitly authorized allowance of at least 4096 tokens.
An allowance below that threshold is rejected before provider invocation with
`BUDGET_COMPLETION_ALLOWANCE_INSUFFICIENT`; Core does not silently raise the
configured value. Provider adapters must not fall back to their local default
when a Story Context request lacks Core-authorized `maxOutputTokens`.

## MCP runtime diagnostics

The OpenCode MCP launcher delegates to the repository-owned supervisor at
`tools/devlog_mcp/runtime.py`. It keeps STDOUT exclusively for MCP protocol
traffic and writes redacted lifecycle events under:

```text
${XDG_STATE_HOME:-~/.local/state}/devlog/mcp/<instance-id>/
```

Each instance records a non-secret manifest and `events.jsonl` with bootstrap,
ownership, process exit and parent-session diagnostics. Bearers, client
secrets, delegation proofs, OAuth values, prompts and provider output must not
appear in these files.

Run the read-only diagnostic command with:

```bash
python3 tools/devlog_mcp/status.py status --json
python3 tools/devlog_mcp/status.py doctor --json
```

The supervisor does not use a global MCP lock. A fresh OpenCode session starts
an independent STDIO instance. Cleanup is limited to an instance whose parent
and child process identities are proven; ambiguous PID reuse is reported and
not force-killed.

The supervisor keeps the technical bearer as a private, bounded lease. Before
forwarding a new JSON-RPC request whose lease is expired or near expiry, it
reacquires the bearer and replaces only the owned Java child. The replacement
replays only the MCP `initialize` handshake. Already-forwarded requests are not
replayed unless the operation is explicitly classified as idempotent; the Story
Agent requires a caller-provided `idempotencyKey` before it can enter that
bounded replay path. If a request is still in flight or renewal cannot
complete, the runtime fails closed with `MCP_REAUTH_REQUIRED`.

When the MCP child returns the explicit bounded authentication error
`Authentication is required` with JSON-RPC code `-32600`, the supervisor may
reacquire the bearer and retry that same request once. A second authentication
failure is returned unchanged and is not retried. Authorization, delegation and
provider errors do not enter this retry path.

If the token endpoint times out or returns `502`, `503` or `504` during a
renewal, the supervisor performs one additional token acquisition after the
configured bounded backoff (`DEVLOG_MCP_TOKEN_RETRY_BACKOFF_SECONDS`, default
one second). This token acquisition retry does not replay the MCP operation.
Client errors, `500`, `401`, `403` and `404` remain terminal for token
acquisition. A Core `429` is handled separately from bearer renewal: the
runtime may perform one bounded retry for an explicitly replay-safe operation.
The Core adapter does not retry. A valid `Retry-After` value is propagated as
bounded `retryAfterSeconds`, capped at 60 seconds; an absent, invalid, expired
or excessive value uses the bounded one-second fallback. The operation is not
retried when it is a mutation without an authoritative idempotency guarantee,
or when its result is ambiguous. The retry is reported with bounded metadata
and is visible through the read-only status command.

Renewal events include `TOKEN_EXPIRED`, `TOKEN_RENEWAL_STARTED`,
`TOKEN_RENEWAL_RETRY`, `TOKEN_RENEWAL_SUCCEEDED` and `TOKEN_RENEWAL_FAILED`. The read-only status
command exposes the last renewal event without exposing expiry claims, bearer
values, client secrets or response bodies. Renewal is independent from Core
authorization, human delegation and AI Engine provider OAuth.

Runtime and Core failures expose only bounded diagnostic metadata: `source`,
`category`, `code`, `layer`, `retryable`, `action`, and when applicable
`attempt`/`maxAttempts`. A Core `429` response may additionally carry the
bounded `retryAfterSeconds` metadata; the status projections expose only their
allowlisted diagnostic fields. Recommended actions are allowlisted and do not
authorize an automatic retry. `retryable` means that
an automatic retry is permitted from the current boundary; it does not mean
that every caller may retry without applying the operation-specific policy.
`status --json` projects the last failure and `doctor --json` reports
configuration, backend and token-endpoint checks.
Neither command exposes bearer values, delegation proofs, payloads, prompts,
provider responses or backend error bodies.

## MCP operator runbook

### Initial read-only diagnosis

Run both commands from the repository root:

```bash
python3 tools/devlog_mcp/status.py status --json
python3 tools/devlog_mcp/status.py doctor --json
```

Record only the available `instanceId`, `operationId`, `generation`, `status`,
`reasonCode`, `source`, `category`, `code`, `layer`, `retryable`, `action`,
`attempt`, `maxAttempts`, timestamp and the result of the checks. Do not copy
bearers, client secrets, delegation tokens, prompts, JSON-RPC payloads,
repository content, provider responses or backend response bodies into an
issue, prompt, log or escalation.

### Recovery matrix

| Signal or code | Interpretation | Safe operator action | Escalation condition | Do not do |
|---|---|---|---|---|
| `MCP_REAUTH_REQUIRED` | Transport renewal or authentication failed | Run `doctor`, verify local non-secret configuration, then start a fresh MCP session | Escalate with bounded transport diagnostics if a fresh session fails | Replay an ambiguous mutation |
| `CORE_AUTHENTICATION_FAILED` | Core rejected authentication | Verify the configured Core access path | Escalate if Core continues rejecting valid configured access | Treat it as bearer expiry or repeatedly renew the bearer |
| `CORE_AUTHORIZATION_FAILED` | Core rejected project or scope authorization | Verify project access and the human delegation scope | Escalate to the project/delegation owner when scope is correct but access remains denied | Bypass or broaden the delegation |
| `CORE_CONFLICT` | Core reports a conflicting or already-changing operation | Reconcile the operation status using correlation metadata | Escalate when the authoritative operation status cannot be determined | Replay the operation blindly |
| `CORE_RESOURCE_NOT_FOUND` | The referenced Core resource is absent or the identifier is wrong | Check the resource identifier and project scope | Escalate when the identifier is verified but the resource remains absent | Retry the same invalid identifier |
| `FUNCTIONAL_ERROR` | Core rejected the operation parameters | Review the documented operation contract and correct the request | Escalate when valid parameters are rejected unexpectedly | Retry unchanged parameters |
| `DELEGATION_FAILED` | Delegation is absent, expired or revoked | Ask the human owner for a new bounded delegation | Escalate to the human owner if renewal or revocation state is unclear | Reuse an expired or revoked token |
| `PROVIDER_FAILED` | Provider authentication or configuration failed | Check provider configuration without exposing credentials | Escalate to the provider or project owner after non-secret checks | Copy provider credentials or responses into diagnostics |
| `RATE_LIMITED` | Core rate limit was reached | Allow the one bounded runtime retry, then wait before a new read-only attempt | Escalate when rate limiting persists after the bounded policy and safe wait | Launch concurrent retries or replay a mutation |
| `TRANSIENT_INFRASTRUCTURE` | Timeout or transient backend/transport failure | Run `doctor`, check service availability and retry only an explicitly safe operation | Escalate when bounded recovery fails or operation status is ambiguous | Replay an ambiguous operation |
| `MCP_RETRY_NOT_IDEMPOTENT` | The runtime refused a retry without an idempotency guarantee | Reconcile operation status and create a new reviewed operation only if safe | Escalate when the business effect cannot be determined | Resend the original operation |
| `MCP_PROCESS_FAILED` | The owned MCP child exited or became inaccessible | Collect `status` and `doctor`, then start a fresh session | Escalate with instance and generation metadata if restart fails | Kill a process whose ownership is not proven |
| `MCP_INSTANCE_STOPPING` | The runtime is stopping intentionally | Wait for shutdown or start a new session if needed | Escalate only if the instance remains stopping unexpectedly | Treat the expected interruption alone as an incident |
| `MCP_HANDSHAKE_FAILED` or `PROTOCOL_INVALID` | MCP session negotiation or protocol validation failed | Check the MCP client and preserve bounded correlation metadata | Escalate when a fresh session cannot establish a valid protocol connection | Copy raw payloads or backend messages into the report |
| `KEYCLOAK_UNREACHABLE` | The token endpoint is unreachable | Check the token endpoint with `doctor` and retry token acquisition only under its bounded policy | Escalate when the endpoint remains unavailable | Replay an MCP operation |
| `PARENT_PROCESS_GONE` | The owned parent process is no longer available | Collect `status` and `doctor`, then restart the owned runtime | Escalate when the new owned runtime cannot start | Retry an ambiguous operation |
| `MCP_JAR_MISSING` or `CONFIGURATION_INVALID` | Required local runtime configuration is invalid or missing | Correct non-secret local configuration and rerun `doctor` | Escalate when configuration is correct but startup still fails | Add credentials to logs or repository files |
| `TOKEN_ENDPOINT_TIMEOUT` or `TOKEN_ENDPOINT_5XX` | Token acquisition failed transiently | Allow the one bounded token-acquisition retry, then check `doctor` | Escalate when token acquisition still fails | Replay the MCP operation as part of token recovery |
| `TOKEN_ENDPOINT_REJECTED` | Token endpoint rejected authentication | Start a fresh authenticated runtime using verified non-secret configuration | Escalate when authentication remains rejected | Reuse stale credentials or replay an ambiguous operation |
| `TOKEN_RESPONSE_INVALID` | Token endpoint returned an invalid response shape | Check the token endpoint and non-secret runtime configuration | Escalate when the endpoint remains invalid after configuration checks | Copy token responses or retry an MCP mutation |
| `CORE_INTERNAL_ERROR` | Core returned an internal failure | Preserve bounded correlation metadata and wait for service recovery before a new safe attempt | Escalate with correlation metadata when the failure persists | Replay an ambiguous mutation |
| `MCP_UNKNOWN_DIAGNOSTIC` | Runtime received an unclassified diagnostic | Preserve bounded correlation metadata and escalate | Escalate immediately because no safe recovery is classified | Infer a retry policy from the unknown code |
| `MCP_JSONRPC_ERROR` | MCP protocol or unclassified response failure | Preserve bounded correlation metadata | Escalate when the client or runtime cannot establish a valid protocol session | Copy raw payloads or backend messages into the report |

### Rate limiting and `Retry-After`

The Python runtime is the only retry authority. For a Core `429`, it retries at
most once and only when the operation is explicitly replay-safe or carries the
required authoritative idempotency guarantee. The Java MCP adapter never
performs the retry itself.

`Retry-After` is handled as follows:

- a valid delay or HTTP date is converted to bounded seconds;
- values above 60 seconds are capped at 60 seconds;
- an absent, invalid or expired value uses the bounded one-second fallback;
- the wait is interruptible by runtime shutdown;
- a second `429` after the replay is terminal for that operation.

Do not manually replay a mutation, a request without an idempotency guarantee,
or any operation whose previous result is ambiguous. A manual retry, when
appropriate, must be a new operation reviewed against its business effect, not
an automatic resend of the previous payload.

### Bearer, Core and delegation recovery

Bearer transport renewal and Core authorization are separate lifecycles. A
Core `401` or `403` does not prove that the transport bearer expired. Check the
diagnostic `source` and `category` before taking action. A missing, expired or
revoked human delegation must be renewed by the human owner; the MCP runtime
must not renew or expand it automatically.

If `doctor --json` reports a configuration, token endpoint or ownership
failure, correct only the non-secret local configuration or restart the owned
runtime. Never place the bearer, client secret or delegation token in a prompt,
command output, issue or repository file.

### Incident checklist

Collect the following non-sensitive evidence:

1. UTC timestamp and environment.
2. `instanceId`, `operationId` and `generation`, when present.
3. Runtime status, `reasonCode`, `source`, `category`, `code`, `layer` and
   recommended `action`.
4. Attempt and maximum attempt values, and whether a bounded `429` retry was
   observed.
5. The redacted output of `status --json` and `doctor --json`.
6. The operator action already attempted and the next escalation requested.

Never collect or attach bearer values, client secrets, delegation tokens,
JSON-RPC payloads, prompts, repository contents, provider responses or raw
backend bodies. If any such value appears in a local diagnostic, redact it
before escalation and report only the bounded diagnostic code and correlation.

Runtime tests use only the Python standard library:

```bash
python3 -m unittest discover -s tools/devlog_mcp/tests -v
```
