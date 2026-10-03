# Story 0159 - Implementation Report

## Repository State

- Branch: `main`
- Worktree: modified before implementation; only the Story 0159 files and the
  MCP contract test were changed by this implementation.
- Human implementation authorization: received in the current session.

## Governance

- Governing ADRs: ADR-063, ADR-067, ADR-069.
- Related Story: Story 0158.
- Story acceptance: not claimed; human validation remains pending.

## Investigation Result

The suspected loss of repository-engine information was not reproduced in the
inspected path.

The evidence shows:

1. `EngineeringContextContractMapper` maps repository evidence content,
   symbols, occurrence timestamps and metadata warnings into the shared
   `EngineeringContext` contract.
2. `DevlogProjectContextClient` receives the shared `EngineeringContext` type
   without a second projection or retrieval step.
3. `EngineeringContextTool` serializes that same contract directly through the
   MCP response boundary.
4. The existing Core mapper tests covered the Core mapping, while the MCP test
   did not cover enriched content and symbols. The missing proof was therefore
   at the final serialization boundary, not a demonstrated production loss.

## Files Changed

- `mcp-server/src/test/java/hopefull117/devlogai_mcp/mcp_server/tool/EngineeringContextToolUnitTest.java`:
  added a contract-level MCP serialization test for content, symbols,
  timestamps and warnings.
- `docs/stories/0159-mcp-engineering-context-fidelity/story.md`:
  recorded the canonical implementation report artifact.
- `docs/stories/0159-mcp-engineering-context-fidelity/implementation-report.md`:
  documented the investigation result, protocol and validation evidence.

## Protocol Validated

The test builds one authorized `EngineeringContext` containing enriched
evidence, passes it through the mocked Core client boundary, serializes it with
the MCP tool, and asserts the following fields in the resulting JSON:

- `evidence[].content.text` and `evidence[].content.revision`;
- `evidence[].symbols.extractorId`;
- `evidence[].symbols.declarations[].name` and source location;
- `evidence[].occurredAt`;
- `metadata.warnings`.

This is a deterministic transport and serialization proof. It does not claim a
live end-to-end comparison against a running database-backed Core instance.

## Architectural Decisions Preserved

- Java/Core remains the sole authority for EngineeringContext construction,
  scope and grounding.
- MCP remains a thin adapter over the shared contract.
- No retrieval, projection expansion, trust-boundary change or new production
  context model was introduced.
- No Story 0158 scores were modified.

## Tests Executed

```bash
./mcp-server/mvnw -pl mcp-server -am test \
  -Dtest=EngineeringContextToolUnitTest,EngineeringContextToolTest,StdioProtocolHygieneTest \
  -Dsurefire.failIfNoSpecifiedTests=false -B

./backend/mvnw -pl backend -am test \
  -Dtest=EngineeringContextContractMapperTest,EngineeringContextControllerWebMvcTest,EngineeringContextFacadeImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false -B
```

Results:

- MCP: 3 tests, 0 failures, 0 errors.
- Backend: 20 tests, 0 failures, 0 errors.

## Known Limitations

- A live Core-versus-MCP payload capture was not required after the deterministic
  boundary test established that the typed adapter preserves the targeted
  fields; it remains a useful follow-up if runtime evidence of loss reappears.
- The original DevLog analysis was context-truncated and is treated as a lead,
  not as proof of a production defect.

## Readiness

READY_FOR_HUMAN_REVIEW
