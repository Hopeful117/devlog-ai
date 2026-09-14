# Story 0127 - Implementation Plan

## Status

**IMPLEMENTED - UNCOMMITTED, READY FOR HUMAN REVIEW**

This document records the implementation sequence. It is not a new
implementation authorization.

## Planned Vertical Slice

1. Define immutable Core reference value semantics, namespaces and scopes.
2. Implement deterministic bindings and registry creation from authorized
   `SelectedKnowledge`.
3. Reuse bindings for semantic sections, relationship endpoints and architecture
   knowledge aliases.
4. Add grounding capability metadata without changing validator behavior.
5. Add the execution-scoped mapping snapshot, contract version and digest.
6. Persist the snapshot on `AiTask` using nullable JSONB and add V49.
7. Wire standard Analysis and Story Context Analysis preparation paths.
8. Add persistence/reload, legacy-null and provider-projection regression tests.
9. Run focused and full backend verification and review scope integrity.

## Actual Implementation Sequence

| Area | Result |
|---|---|
| Reference model | Implemented with immutable `AiReference`, type and scope values |
| Registry | Implemented with deterministic factory, binding reuse and lookup |
| Grounding metadata | Implemented for facts, observations and repository evidence |
| Snapshot | Implemented with contract version, bindings and SHA-256 digest |
| Persistence | Added nullable JSONB mapping field to `AiTask` |
| Database | Added `V49__add_ai_reference_mapping_snapshot_to_ai_tasks.sql` |
| Preparation paths | Wired standard Analysis and Story Context Analysis |
| Verification | Full backend suite: 1,316 passed |

## Explicitly Unchanged

- No Python or Pydantic changes.
- No provider prompt or `PromptRequest` changes.
- No callback typed-reference resolution.
- No `AiProposalContractValidator` behavior changes.
- No REST or MCP contract changes.
- No proposal persistence changes.
- No `contextDigest` changes.
- No selection, ranking, retrieval, budget or semantic-classification changes.
- No Story 0128 implementation.
- No Story 0126 trace schema or persistence changes.

## Verification Commands

```bash
./backend/mvnw -pl backend -am test -B
git diff --check
```
