# Story 0128 - Implementation Plan

## Status

**IMPLEMENTED - UNCOMMITTED, READY FOR HUMAN ACCEPTANCE**

This document records the implemented sequence. It does not declare Story
acceptance or authorize a commit or push.

## Vertical Slice

1. Preserve `architecture-overview-v1` and `v2` as legacy contracts.
2. Register `architecture-overview-v3` through the existing intent catalog.
3. Project Core-issued typed references and separate grounding candidates.
4. Add strict Python v3 schemas, prompt dispatch, validation and one retry.
5. Add Java callback DTO fields and resolve them from the originating task snapshot.
6. Convert resolved identities into the existing proposed Insight lifecycle.
7. Update the Angular human-facing architecture objective to select v3.
8. Verify focused and full Core, AI Engine and frontend suites.

## Implemented Areas

| Area | Result |
|---|---|
| Intent versioning | Added `architecture-overview-v3`; v1/v2 retained |
| Core projection | Added typed context and distinct grounding candidate sets |
| Snapshot resolution | Added immutable reverse resolver over persisted task mappings |
| Java callback | Resolves typed proposal and synthesis references before persistence |
| Python contract | Added strict typed references and v3 response models |
| Prompt execution | Added v3 prompt/template and typed response dispatch |
| Frontend | Architecture review selector now requests v3 |
| Verification | Backend 1,324; frontend 260; AI Engine suite passed |

## Explicitly Unchanged

- Story Context Analysis contract and execution path.
- `architecture-overview-v1` and `architecture-overview-v2` semantics.
- Trust promotion and `ValidatableProposal` lifecycle.
- Selection, ranking, budgets and repository retrieval.
- Public exposure of the internal mapping snapshot.
- Commit and push state.
