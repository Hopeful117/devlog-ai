# Story 0229 - MCP Live Evaluation Authorization

## Status

ACCEPTED — 2026-11-24.

## Objective

Allow an authenticated human to approve one exact Core-owned bounded live
evaluation plan and obtain the redacted authorization artifact required by the
existing LIVE runner.

## Scope

- Persist pending and approved authorization requests.
- Bind each request to `planId`, `planDigest`, principal and idempotency key.
- Restrict approval to human principals authorized for the project.
- Expose request, approval and read operations through Core and thin MCP tools.
- Validate the artifact binding in the Python runner.
- Preserve zero provider calls during request and approval.

## Non-Goals

- Autonomous approval, OAuth consent or credential handling.
- Provider execution, budget changes or plan mutation.
- Provider fallback or retry-policy changes.

## Acceptance Criteria

- A non-`READY` or expired plan cannot produce an authorization request.
- A changed plan digest returns a conflict.
- Equivalent idempotent requests return one pending request.
- An AI agent cannot approve a request.
- Human approval produces a redacted `CORE` artifact with bounded expiry.
- The artifact is bound to the exact plan identity and digest.
- The runner rejects missing, expired or mismatched Core artifact fields before
  any provider call.
- Focused Core, MCP and Python tests pass.
