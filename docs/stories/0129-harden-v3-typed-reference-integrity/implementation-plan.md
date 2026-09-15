# Story 0129 - Implementation Plan

## Status

**PLAN EXECUTED - IMPLEMENTATION COMPLETED - HUMAN REVIEW ACCEPTED**

This document remains the historical pre-implementation plan. The planned
corrective slice was executed and human review accepted it. It does not imply
that a commit, push or merge has occurred.

## Planned Corrective Slice

1. Reproduce the v3 `ENRICHES` target authorization defect with a selected
   non-architecture Insight.
2. Specify the Core distinction between `INSIGHT/PROJECT` namespace validity and
   membership in `existingArchitectureKnowledge`.
3. Add a fail-closed projection contract for unresolved citable identities.
4. Implement authoritative Java checks against the originating task snapshot.
5. Align Python validation defensively without moving authority to Python.
6. Add focused positive, negative, isolation and deleted-entity tests.
7. Run relevant backend and AI Engine suites and verify legacy compatibility.
8. Review the diff to ensure Story Context, Event, Decision and retrieval paths
   remain unchanged.

## Expected Seams

- `backend/.../ai/engine/service/AiTaskResultServiceImpl.java`
- `backend/.../ai/engine/service/AiProposalContractValidator.java`
- `backend/.../knowledge/selection/SelectedKnowledgePromptProjectionService.java`
- `backend/.../ai/reference/AiReferenceResolver.java`
- `ai-engine/app/services/insight_generation_service.py`
- v3-focused Java and Python test seams

These were the investigation-derived seams used during the authorized
implementation.

## Explicit Non-Goals

- No new AI reference type or scope.
- No mapping schema or versioning mechanism.
- No migration of Story Context Analysis.
- No global raw-ID removal.
- No Retrieval/RAG/Agent infrastructure.
- No changes to v1/v2 behavior or trusted knowledge promotion.

## Verification Plan

```bash
./backend/mvnw -pl backend -am test -Dtest=<focused-v3-tests> -B
./backend/mvnw -pl backend -am test -B
cd ai-engine && python -m pytest -q
cd frontend && npx ng test --coverage --watch=false
git diff --check
```

The commands above describe the planned verification surface. Actual reported
evidence is recorded in `implementation-report.md`; no suites are rerun by
this documentation synchronization pass.

## Execution Result

- Planned corrective slice: executed.
- Human review: accepted with no blocking findings.
- Story state: implemented, reviewed, accepted, ready to commit.
- Commit, push and merge: not performed.

## Learning Boundary

- Ludovic should drive the target-membership semantics and failure taxonomy.
- Pair on transaction ordering and snapshot isolation.
- Delegate repetitive fixtures and serialization assertions only after the
  authority rules are agreed.
