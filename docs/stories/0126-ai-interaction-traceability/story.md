# Story 0126: AI Interaction Traceability for DevLog Analyses

## Status

`IMPLEMENTED — READY FOR HUMAN REVIEW`

## Goal

Persist a durable, attempt-level technical trace for AI interactions so a
failed or retried DevLog analysis can be reconstructed without treating AI
execution data as project knowledge.

## Scope

- Add a reusable `AiInteractionTrace` aggregate associated with `AiTask` and
  `Analysis`.
- Capture one trace for every concrete LLM attempt, including corrective
  retries and failed attempts.
- Extend the existing Python-to-Java result callback with structured traces.
- Persist normal operational metadata by default and optional diagnostic
  payloads under explicit configuration.
- Expose internal trace queries by AI task and analysis.
- Preserve optional `traceId` and `spanId` fields for future distributed
  tracing without adding OpenTelemetry infrastructure.

## Non-Goals

- No OpenTelemetry, Jaeger, Tempo, Grafana, or distributed tracing platform.
- No frontend dashboard.
- No prompt redesign or grounding-ID redesign.
- No change to validator authority or trusted-knowledge promotion.
- No automatic cleanup job; diagnostic expiry is persisted for later cleanup.
- No automatic injection of traces into future prompts.

## Design

`AiTask` remains the functional work aggregate. `AiInteractionTrace` records
one concrete provider interaction and is never trusted knowledge, a proposal,
or grounding authority.

Python captures the rendered prompt, provider response, parsing/validation
outcome, retry relationship, timing, and usage available at the provider
boundary. Java Core receives the structured callback extension and owns
durable persistence, diagnostic-level filtering, expiry metadata, and query
access.

`NORMAL` is the default. It stores fingerprints, counts, timing, provider
identity, validation status, and failure/retry metadata. `DIAGNOSTIC` additionally
stores redacted prompt, response, parsed output, and validation diagnostics.
Core controls what is persisted with `DEVLOG_AI_TRACE_LEVEL`; diagnostic data
expires after `DEVLOG_AI_TRACE_DIAGNOSTIC_RETENTION` (default `7d`). No cleanup
automation is included in this Story.

## Retry Lifecycle

```text
attempt 1 -> provider output -> parse/validation result
                         |
                         +-> retry reason
attempt 2 -> corrective prompt -> parse/validation result
```

Attempts are ordered by `attempt`, have distinct trace IDs, and retain the
retry reason on the corrective attempt. A failed task callback persists traces
before task failure handling. Trace persistence errors are isolated and logged
without changing the AI task result.

## Acceptance

1. Successful and failed AI callbacks can persist interaction traces.
2. Corrective retries produce a second trace rather than overwriting attempt 1.
3. Attempt ordering and retry reason are queryable.
4. Normal mode excludes raw prompt and response payloads.
5. Diagnostic mode persists redacted rich payloads with an expiry timestamp.
6. API keys, authorization headers, and provider credentials are redacted.
7. Selected-knowledge and grounding fingerprints/counts are persisted without
   duplicating full context in normal mode.
8. Optional `traceId` and `spanId` remain nullable.
9. Traces cannot authorize grounding or promote trusted knowledge.
10. Traces are queryable by AI task ID and analysis ID.
11. Existing callback compatibility and task behavior remain intact.
12. Focused Java/Python and broader relevant suites pass.

## Known Limitations

- Diagnostic payload cleanup is not automated; `expiresAt` is persisted for a
  later retention story.
- The current callback is the durable handoff, so a process failure before the
  callback cannot persist a trace in Core.
- Provider-specific usage is best-effort and may be null.
- OpenTelemetry correlation fields are accepted but are not populated until a
  distributed tracing layer exists.
