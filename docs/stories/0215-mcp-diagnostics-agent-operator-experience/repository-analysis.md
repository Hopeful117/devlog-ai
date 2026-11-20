# Repository Analysis - Story 0215

## Findings

- `tools/devlog_mcp/runtime.py` emits bounded lifecycle and tool events, but its
  JSON-RPC reason mapping covers only part of the ADR-081 taxonomy.
- `tools/devlog_mcp/status.py` projects runtime state and last failures, while
  `doctor` currently performs only endpoint reachability checks.
- `CoreCallPolicy` already owns Core error provenance and maps HTTP failures to
  structural categories without Java retries.
- Backend MCP operation and incident projections already expose reason codes and
  correlation identifiers; no new persistence is required.
- Existing tests cover transport/Core separation and redaction, providing a
  focused regression surface.

## Constraints

- Java Core remains authoritative for Core error provenance.
- The Python runtime remains authoritative for transport renewal diagnostics.
- No new retry, queue, schema, identity or delegation behavior is authorized.
- `TOOLS.md` is the canonical operator-facing documentation.

## DevLog Context

DevLog context was unavailable in this session. Current repository state, the
epic and accepted ADR-081 were used as authority.

## Recommendation

Implement the smallest additive diagnostic contract across `CoreCallPolicy`,
runtime reason projection, `status/doctor`, tests and `TOOLS.md`. Reuse existing
MCP event and Activity projections instead of introducing a second telemetry
model.
