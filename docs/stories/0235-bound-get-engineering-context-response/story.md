# Story 0235 — Borner la taille de la réponse `get_engineering_context`

## Status

IMPLEMENTATION_DONE

## Context

During the qualitative evaluation of the epic "Fiabiliser les réponses DevLog pour les agents"
(2026-11-24), `get_engineering_context` returned a 130,142-character JSON response (Config D).
This saturates MCP context windows and degrades the signal-to-noise ratio for consumers.

Root cause: `EngineeringContextTool` serializes the full `EngineeringContext` object with no
size limit. `EngineeringContextContractMapper` places evidence in both `evidence[]`
(backwards-compatibility) and `sections[]`, effectively doubling the payload.

**Finding reference**: Epic evaluation finding F4 — "`get_engineering_context` : réponse non bornée"

**Implementation**: Delivered in PR #241, merged 2026-11-24.

## Governing ADRs

- No dedicated ADR — operational transport improvement.

## Implementation (delivered)

In `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/tool/EngineeringContextTool.java`:

- Added `DEFAULT_MAX_CHARS = 25_000` and `TRUNCATION_WARNING = "RESPONSE_SIZE_TRUNCATED"`.
- Both `getEngineeringContext` overloads call `boundedJson(objectMapper.writeValueAsString(engineeringContext))`.
- `boundedJson()` iteratively trims `evidence[]` then `sections[]` until `≤ DEFAULT_MAX_CHARS`,
  then appends `RESPONSE_SIZE_TRUNCATED` to `metadata.warnings`.
- Fallback: raw `substring(0, DEFAULT_MAX_CHARS)` on JSON parse error.

## Acceptance Criteria (verified)

1. `get_engineering_context` default response is ≤ 25,000 characters. ✅
2. When truncation occurs, `metadata.warnings` contains `"RESPONSE_SIZE_TRUNCATED"`. ✅
3. The truncated response is valid JSON. ✅
4. `EngineeringContextToolUnitTest` covers: below threshold (unchanged), above threshold
   (truncated + warning), valid JSON after truncation. ✅
