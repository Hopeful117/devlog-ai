# Story 0161 - Repository Analysis

## Repository State

- Branch: `main`
- HEAD: `659ec50`
- Worktree: modified before this Story with unrelated AI provider and
  evaluation changes; those changes must remain outside this Story.
- DevLog context check: the previous Story Agent request was rate-limited by
  the configured upstream provider. This analysis uses current repository
  evidence.

## Existing Authorization Boundary

1. `AuthenticatedPrincipal` already provides the provider-neutral Core identity
   required by ADR-070: `principalId`, `PrincipalKind` and
   `authenticationSource`.
2. `RequestAuthenticatedPrincipalResolver` deliberately consumes an already
   authenticated `HttpServletRequest` principal. It does not interpret headers,
   project slugs or caller metadata as identity.
3. `ProjectMembershipRepository` and migration `V54` provide the existing
   project membership relation for `PROJECT_READER` and `PROJECT_OWNER`.
4. There is no active Spring Security filter or concrete identity-provider
   integration in the inspected backend. The principal resolver is an
   integration boundary, not an authentication implementation.
5. Existing authorized Story Context operations already fail closed when a
   principal is absent and authorize membership before detailed snapshot
   materialization.

## Existing Core to AI Boundary

1. `AIEngineClient` exposes `submit(PromptRequest)` and deliverable generation.
2. `RestAIEngineClient` uses a normal `RestClient` with configured base URL,
   connect timeout and read timeout. No service authentication header is
   currently configured.
3. `PromptRequest` carries `metadata`, but that map is request transport data;
   it is not an authenticated principal or an authorization decision.
4. `AnalysisWorkflowServiceImpl` creates AI tasks internally from an analysis
   and currently has no authenticated principal parameter. It cannot safely
   issue a user credential binding in its current use case boundary.
5. `AiTask` persists context, selection, provider and execution metadata, but
   has no credential-binding field. A token or credential secret must not be
   added to any existing snapshot JSON field.
6. AI Engine task submission schemas mirror the Core contract with strict
   Pydantic validation. A new opaque binding field requires coordinated Java
   and Python contract changes and explicit snapshot exclusion rules.

## Existing Service Authentication

`StoryContextAgentCallbackAuthenticator` implements a dedicated HMAC boundary
for the Core callback. It signs HTTP method, path and raw body, validates a
hexadecimal SHA-256 signature with constant-time comparison and fails closed
when the secret is blank. This is useful precedent, but its secret and semantic
purpose are dedicated to the inbound Story Context callback and must not be
reused for Core-to-AI binding operations.

No mTLS configuration, service-token rotation mechanism or shared service
identity module was found. Introducing mTLS would require deployment and local
certificate lifecycle changes that are not currently present.

## Recommended Repository-Consistent Direction

For the first bounded implementation, use a **dedicated HMAC service boundary**
for binding exchange, separate from `SCA_CALLBACK_SECRET`, with the following
constraints:

- secret is supplied through deployment secret management and blank means
  fail-closed;
- signature covers method, path, timestamp/expiry, nonce and raw body;
- signatures use constant-time comparison and a bounded clock-skew window;
- each request carries a request ID and nonce; replay protection is required;
- the secret has an explicit rotation strategy and must not be logged;
- HTTPS/mTLS remains a production hardening option and must not be weakened by
  the HMAC layer.

This reuses a proven local cryptographic pattern while keeping the new secret,
route and capability distinct. It does not authorize a caller to invent a
principal or project.

## Required Core Changes

- Add a non-secret binding representation and repository/table with principal,
  task, provider, status, issued/expiry timestamps and a safe audit identity.
- Add an application service that receives the existing authenticated
  `AuthenticatedPrincipal`, resolves the task project, checks project
  membership, and issues/revokes/validates bindings.
- Add explicit task association rather than accepting an arbitrary metadata
  value. The task association must be immutable after submission.
- Add a dedicated signed Core-to-AI endpoint/client operation for registration
  or validation, without sending OpenAI tokens to Core.
- Add migration and lifecycle tests for unknown, foreign, expired, revoked and
  task-mismatched bindings.

## Required AI Engine Changes

- Add a binding resolver/store keyed by opaque binding ID and local credential
  owner; it must not accept a caller-supplied token.
- Validate the Core-issued binding before provider resolution.
- Keep the binding ID out of prompts, model output, callbacks and interaction
  traces unless a separate non-sensitive audit field is explicitly approved.
- Reuse the existing redaction path and add tests covering binding IDs,
  signatures, tokens and error bodies.

## Security Risks

| Risk | Repository evidence | Required control |
|---|---|---|
| Caller injects credential reference | `PromptRequest.metadata` is generic input | Core-issued task association and binding validation |
| Cross-project use | Membership data exists but task workflow lacks principal | Resolve task project in Core before issuing binding |
| Replay of binding exchange | No current outbound auth | Timestamp, nonce, request ID and replay store |
| Secret confusion | Existing SCA HMAC secret | Dedicated binding secret and configuration key |
| Snapshot leakage | `AiTask` stores JSON snapshots | Explicit exclusion tests and DTO review |
| Service impersonation | No AI Engine auth currently | Dedicated authenticated Core-AI boundary, fail closed |
| Log leakage | Backend logs exceptions and task metadata in places | Safe categorized diagnostics and redaction tests |

## Analysis Result

The repository has enough authorization primitives to add the Core-owned binding,
but not enough authenticated task context to implement it as a Python-only
change. Story 0161 must therefore be implemented across Core and AI Engine.
The first implementation decision should be a dedicated HMAC binding boundary
with replay protection, unless the human explicitly selects mTLS and accepts
the additional deployment lifecycle work.

Implementation must stop before enabling Story 0160 live usage if the task
creation path still lacks an authenticated principal. Existing mock/API-key
paths must remain unaffected.
