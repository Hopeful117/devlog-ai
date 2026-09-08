# Story 0116 - Implementation Plan

## Status

**IMPLEMENTED - COMMITTED ON MAIN BRANCH**

This plan records the implementation sequence from the feature branch and main branch commits. It is not a new implementation authorization.

## Planned Vertical Slice

Per governing design `docs/investigations/validated-knowledge-sca-integration-design.md`:

1. **AnalysisContext construction for SCA**: Build AnalysisContext from latest ProjectProfile Analysis baseline, adapt for SCA intent with current Story only
2. **KnowledgeSelectionService integration**: Call `select()` with SCA AnalysisContext to get Story-aware SelectedKnowledge (v5)
3. **Grounding contract construction**: Build `allowedEvidenceReferences` from `EngineeringEvidence.reference` and `relatedReferences`
4. **Task creation with selected knowledge**: `AiTaskService.createForStoryContextAnalysisEntity()` stores grounding contract in contextSnapshot
5. **Task submission**: Call `aiTaskService.submit()` to transition CREATED → SUBMITTED before Python call
6. **PromptRequest with grounding contract**: Send selected knowledge + groundingContract to Python
7. **Python defensive validation**: Consume Java contract, validate against it
8. **Authoritative callback validation**: Java revalidates grounding, classification, relationType, digest consistency
9. **EngineeringEvidence reference field**: Add `reference` field preserving canonical `RepositoryEvidence.reference`
10. **Test updates**: Fix constructor signatures for PromptRequest, EngineeringEvidence

## Actual Commit Sequence

| Commit | Outcome |
|---|---|
| `b753e8d` | `feat: wire validated knowledge into Story Context Analysis (Story 0116)` — single commit containing all production changes, test fixes, and Story documentation |

Note: Implementation was done directly on `main` branch after Story 0115 merge (no separate feature branch was used per the user's workflow).

## Verification Planned

- Focused knowledge-selection and repository-context tests (Story 0115 regression)
- Full backend Maven verification with coverage (`./backend/mvnw -pl backend test`)
- Full AI Engine regression (`cd ai-engine && python3 -m pytest -q`)
- `git diff --check`
- No generated artifacts committed (devlog-contracts/target/ excluded by .gitignore)

## Explicitly Unchanged

- `KnowledgeSelectionService` public interface and final budget (40/25/10/5/60)
- `StoryContextAnalysisResult` output schema (no new fields)
- `EvidenceRef` shape (`{reference, resource?}`; resource remains navigation-only)
- Insight trust semantics (context only, no trust inheritance)
- Story 0114 freshness capture/persistence unchanged
- Historical analyses unchanged (no backfill)
- Java/Core authority over grounding (no Python-side authority reconstruction)
- No RAG, vectors, new agent, microservice, or event-driven redesign
- No Decisions/EngineeringEvents as selected knowledge (deferred per design)
- No frontend changes