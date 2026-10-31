# Story 0172 - MCP Runtime Observability and Instance Lifecycle

## Statut

IMPLEMENTED - AWAITING HUMAN REVIEW.

The Story and Implementation Plan were explicitly approved by the human project
owner. Human acceptance of the implementation remains pending.

## Contexte

The local DevLog MCP server is started by OpenCode as a Java STDIO process
through a machine-local launcher. The current launcher obtains one Keycloak
`client_credentials` bearer, exports it to the MCP process and starts the
prebuilt JAR.

The observed failure was:

```text
MCP error -32000: Connection closed
```

The backend, AI Engine and Keycloak were healthy. Two OpenCode instances were
running, but the MCP process was attached to the older instance while the
fresh instance had no DevLog MCP child process. There was no durable, safe
runtime record explaining the bootstrap, process ownership or exit reason.

The system therefore needs an operational observability and lifecycle boundary
for the local MCP runtime. This Story is about diagnosing and isolating that
boundary; it is not a replacement for Core authorization or provider
credential management.

## Objectif produit

Ensure that a fresh OpenCode session automatically receives a valid, observable
DevLog MCP STDIO instance, while old or orphaned instances cannot block the
fresh session or remain indefinitely without a deterministic cleanup path.

An operator must be able to answer, without reading secrets:

- which OpenCode session owns an MCP instance;
- whether bootstrap reached Keycloak, MCP startup and protocol readiness;
- why an instance stopped or became unavailable;
- whether an old process is still owned by a live session;
- which corrective action is safe.

## Autorite et dependances

- [ADR-067](../../decisions/ADR-067.md) - Java/Core deterministic authority.
- [ADR-077](../../decisions/ADR-077.md) - technical MCP actor and delegation boundary.
- [ADR-078](../../decisions/ADR-078.md) - OpenCode Agent and transport lifecycle.
- [Story 0171](../0171-mcp-actor-human-delegation/story.md) - actor and human delegation contract.
- [Investigation MCP/OpenCode/ChatGPT](../../investigations/devlog-mcp-opencode-chatgpt-oauth-stability.md).
- The local OpenCode launcher and MCP STDIO configuration.

If the implementation requires a new identity, delegation, OAuth or provider
credential boundary, it is outside this Story and requires an explicit
architectural decision.

## Perimetre IN

- Define a structured, non-sensitive event policy for the local MCP launcher.
- Give every MCP runtime instance a unique identifier and ownership metadata.
- Record launcher PID, MCP PID, parent OpenCode PID and parent process start
  time, without recording secrets.
- Record bootstrap phases, bounded failure categories, exit reason, exit code
  and runtime duration.
- Preserve MCP server diagnostics on `stderr` and persist a bounded local log
  for post-mortem investigation.
- Validate required local dependencies and endpoint reachability before MCP
  startup, with actionable failure categories.
- Detect parent-session disappearance and close the owned MCP process safely.
- Detect stale manifests and orphaned instances without killing processes owned
  by a live, different session.
- Make every new OpenCode session start its own valid STDIO instance without a
  global stale process or lock blocking startup.
- Provide a read-only status/doctor operation for runtime inspection.
- Add automated tests for bootstrap failures, ownership, cleanup and redacted
  diagnostics.
- Document the runtime directories, event categories, retention and safe
  troubleshooting procedure.

## Perimetre OUT

- Human OAuth login or browser automation.
- ChatGPT credential acquisition, storage, refresh or revocation.
- Changes to Core project authorization, actor classification or delegation
  semantics.
- Changes to `AiCredentialBinding` or AI Engine provider ownership.
- A generic process supervisor, service manager or multi-tenant runtime.
- Sharing one STDIO MCP process between independent OpenCode sessions.
- A durable task queue, broker, scheduler or automatic task retry system.
- Automatic deletion of processes whose ownership cannot be proven.
- Logging bearer tokens, client secrets, delegation tokens, OAuth codes,
  provider tokens, prompts, model output or repository-sensitive payloads.

## Invariants

1. A runtime instance is owned by exactly one OpenCode parent session.
2. A new session does not depend on an old session's MCP process, token or
   conversation state.
3. Cleanup may terminate only a process whose ownership is proven and whose
   parent is absent or whose recorded process start identity no longer matches.
4. A live unrelated session is never terminated by stale-instance cleanup.
5. STDOUT remains reserved for MCP protocol traffic; diagnostics use STDERR and
   the bounded runtime log only.
6. Logs contain only redacted metadata and bounded error categories.
7. Transport bearer acquisition remains separate from human delegation and
   provider OAuth.
8. An expired or unavailable bearer fails closed and produces a distinct
   transport diagnostic.
9. Runtime readiness is not inferred from process existence alone.
10. Cleanup is idempotent and safe to run repeatedly.

## Runtime event policy

Each event must include a timestamp, `instanceId`, `event`, `severity` and a
bounded diagnostic code. Where known, it may include the launcher PID, MCP PID,
parent PID, parent start time, duration and HTTP status. It must not include
secret values or request bodies.

Minimum event categories:

```text
LAUNCHER_STARTED
CONFIGURATION_VALIDATED
DEPENDENCY_CHECK_FAILED
TOKEN_ACQUISITION_STARTED
TOKEN_ACQUISITION_SUCCEEDED
TOKEN_ACQUISITION_FAILED
MCP_PROCESS_STARTED
MCP_PROCESS_EXITED
MCP_PARENT_DISAPPEARED
MCP_HANDSHAKE_FAILED
MCP_INSTANCE_READY
MCP_INSTANCE_STOPPED
MCP_INSTANCE_ORPHANED
MCP_INSTANCE_CLEANED
```

Minimum bounded failure categories:

```text
CONFIGURATION_INVALID
DEPENDENCY_MISSING
KEYCLOAK_UNREACHABLE
TOKEN_ENDPOINT_REJECTED
MCP_JAR_MISSING
MCP_PROCESS_FAILED
MCP_HANDSHAKE_FAILED
PARENT_PROCESS_GONE
STALE_OWNERSHIP_RECORD
CORE_UNREACHABLE
CORE_AUTHENTICATION_FAILED
CORE_AUTHORIZATION_FAILED
```

The event policy must define which HTTP statuses are retained and which error
messages are normalized. Access tokens, refresh tokens, client secrets and
delegation proofs are always excluded.

## Instance lifecycle design

The launcher creates an instance directory under a user-local runtime state
directory, for example:

```text
${XDG_STATE_HOME:-~/.local/state}/devlog/mcp/<instanceId>/
```

The directory contains only bounded operational metadata, such as:

```text
manifest.json
events.jsonl
```

The manifest records the process identity needed to prove ownership, including
the parent PID and parent process start time. The launcher must not use a
global singleton lock that makes a valid fresh OpenCode session depend on an
older session.

The launcher supervises the Java child while preserving STDIO protocol
semantics. Closing STDIN, receiving a termination signal or observing a dead
parent must produce a bounded shutdown event and terminate only the owned child
process.

Cleanup must first validate the manifest, PID and process start time. An
ambiguous record is reported for manual inspection and is not force-killed.

## Criteres d'acceptation

### AC-1 - Structured bootstrap diagnostics

Every launcher attempt emits a redacted, structured lifecycle sequence with a
stable `instanceId`, bootstrap phase, terminal status and bounded diagnostic
code. The sequence remains available after the MCP process exits.

### AC-2 - No secret leakage

Automated tests and a redaction scan demonstrate that logs and diagnostics do
not contain client secrets, bearer tokens, delegation tokens, OAuth codes,
provider credentials, prompts or model output.

### AC-3 - STDIO protocol integrity

MCP protocol traffic remains exclusively on STDOUT. Launcher diagnostics and
Java logs use STDERR or the runtime log and cannot corrupt MCP messages.

### AC-4 - Bootstrap failure visibility

Missing dependencies, invalid configuration, Keycloak failure, token rejection,
missing JAR and MCP process failure each produce a distinct bounded diagnostic
and non-zero launcher result where appropriate.

### AC-5 - Instance ownership

The runtime manifest records enough non-secret process identity to distinguish a
live instance from an orphaned instance and to prevent PID reuse from being
mistaken for ownership.

### AC-6 - Fresh-session independence

A fresh OpenCode session starts a valid independent MCP instance even when an
older instance exists. No stale global lock, fixed port or old process is a
precondition for startup.

### AC-7 - Safe orphan cleanup

When the parent OpenCode process disappears, the owned MCP child is terminated
and its runtime record is finalized. Cleanup never terminates a live unrelated
session and leaves ambiguous ownership records untouched.

### AC-8 - Idempotent cleanup

Running cleanup repeatedly produces no additional destructive action after the
first successful cleanup and reports already-clean state deterministically.

### AC-9 - Read-only diagnostics tool

The status/doctor operation reports active instances, ownership, readiness,
last event and recommended action without changing processes, credentials or
repository state.

### AC-10 - Runtime integration

An integration scenario proves: fresh launcher, successful MCP initialization,
tool invocation, parent shutdown, bounded child cleanup and a second fresh
launcher. The scenario captures only non-sensitive status, error codes and
correlation identifiers.

### AC-11 - Existing architecture preserved

The implementation does not change Core authorization, `AI_AGENT` identity,
human delegation, provider credential ownership or task-scoped binding
semantics defined by ADR-077, ADR-078 and Story 0171.

### AC-12 - Quality validation

Launcher tests, MCP module tests, applicable backend integration tests,
redaction tests and `git diff --check` pass. The implementation documents any
environment-dependent checks that cannot run locally.

## Tests attendus

- launcher unit tests for event creation and redaction;
- bootstrap tests for each dependency and token failure category;
- process ownership tests including PID reuse/start-time mismatch;
- parent disappearance and STDIN-close cleanup tests;
- repeated cleanup and ambiguous-record safety tests;
- MCP STDIO protocol integrity test;
- fresh-session-after-old-instance integration test;
- status/doctor read-only contract tests;
- existing MCP and Story Agent regression suites.

## Documentation concernee

- `TOOLS.md` runtime and troubleshooting guidance;
- MCP operator documentation;
- local OpenCode launcher configuration documentation;
- Story 0171 documentation only if lifecycle behavior changes its stated
  boundaries.

## Gouvernance

This Story is derived from the runtime investigation and is not yet approved
for implementation. Human review must confirm the event vocabulary, retention
policy, cleanup safety rules and whether the launcher implementation belongs in
the repository or remains machine-local.

No implementation authorization, Story acceptance, commit, push or deployment
authorization is implied by this artifact.
