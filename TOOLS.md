# DevLog MCP Tooling

## Purpose

This document describes the local MCP actor and delegation configuration. It
contains no bearer, delegation token, OAuth token or other secret value.

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

Runtime tests use only the Python standard library:

```bash
python3 -m unittest discover -s tools/devlog_mcp/tests -v
```
