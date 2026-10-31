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
