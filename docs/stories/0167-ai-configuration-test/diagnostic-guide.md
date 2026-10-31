# Story 0167 Diagnostic Guide

- `CREDENTIAL_INVALID`: the credential is absent, revoked, rejected by the
  provider, or does not match the configured authentication method.
- `MODEL_UNAVAILABLE`: the selected model is not exposed by the provider
  metadata endpoint. No fallback model is selected.
- `PROVIDER_UNAVAILABLE`: the provider endpoint is inaccessible, returns a
  server error, or returns an incomplete/oversized model listing.
- `TIMEOUT`: the bounded provider probe exceeded its timeout budget.
- `CONFIGURATION_INVALID`: the provider/authentication/model combination is
  invalid before provider access.

Diagnostics never include provider response bodies, prompts, repository
context, credential handles in user-facing results, or secret material.
