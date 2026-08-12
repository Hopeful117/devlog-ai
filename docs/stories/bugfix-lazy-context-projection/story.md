# Bugfix: LazyInitializationException During Architecture Review Context

**Status**: Completed
**Type**: Bugfix
**Date**: 2026-08-12

## Problem

During an architecture review, the analysis context systematically fails. The `GET /api/v1/analyses/{id}/context` endpoint returns HTTP 404 with `"Analysis context snapshot not found with identifier: {id}"` because the workflow (`AnalysisWorkflowServiceImpl.start`) aborts with an exception **before** it reaches `aiTaskService.create(...)`, so no `AiTask`/`contextSnapshot` is ever persisted.

## Root Cause

`AnalysisContextServiceImpl.build()` is non-transactional (and the workflow method is non-transactional too; in production `spring.jpa.open-in-view=false`). While building the context it dereferences **lazy collection associations** after the loading transaction has closed:

- `toObservationSnapshot()` iterates `observation.getSupportingFacts()` — a `@ManyToMany(fetch = FetchType.LAZY)` collection (`Observation.java`). The source query `ObservationRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc` had **no** `@EntityGraph`, unlike its sibling `findByAnalysisIdOrderByTypeAscIdAsc` which has one.
- `toFactSnapshot()` iterates `fact.getEvidenceReferences()` — a lazy `@ElementCollection`. `FactRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc` also had **no** `@EntityGraph`.

Both throws `org.hibernate.LazyInitializationException` (`Cannot lazily initialize collection ... no session`), surfacing through the workflow. This path runs for **every** analysis (regardless of type) and is masked in dev/tests where OSIV stays enabled.

Two additional repository read methods used by `ProjectContextProviderImpl.build` were hardened to the same standard (engineering events / stories), matching their already-annotated siblings, as defensive consistency for the same class of bug.

## Solution

- `ObservationRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc` → `@EntityGraph(attributePaths = "supportingFacts")` (mirrors `findByAnalysisIdOrderByTypeAscIdAsc`).
- `FactRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc` → `@EntityGraph(attributePaths = "evidenceReferences")`.
- `EngineeringEventRepository.findRecentByProjectIdOrderByOccurredAtDescTargetCommitDescIdAsc` → `@EntityGraph({project, analysis, proposal, validation, source})` (mirrors `findByProjectIdOrderByOccurredAtDescTargetCommitDescIdAsc`).
- `EngineeringStoryRepository.findByProject_IdOrderByCreatedAtDesc` → `@EntityGraph(attributePaths = "project")`.

Each fix is proven by an OSIV-disabled integration test that reproduces the exact `LazyInitializationException` without the fix and passes with it.

## Acceptance Criteria

1. ✅ `Observation.supportingFacts` is eagerly fetched by `findByAnalysisIdOrderByCreatedAtDescIdDesc` (integration test fails with `LazyInitializationException` without it)
2. ✅ `Fact.evidenceReferences` is eagerly fetched by `findByAnalysisIdOrderByDetectedAtDescIdDesc` (regression proved)
3. ✅ `EngineeringEvent.findRecentByProjectId...` carries the same `@EntityGraph` as its sibling
4. ✅ `EngineeringStory.findByProject_IdOrderByCreatedAtDesc` fetches `project`
5. ✅ No new persistence or migration
6. ✅ No behavior/ranking change, deterministic fix
7. ✅ Full backend suite passes (559+) and JaCoCo ≥ 0.80
8. ✅ Frontend unaffected
