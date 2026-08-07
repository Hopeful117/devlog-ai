## Overview

Extracted project-scoped context construction logic from `AnalysisContextServiceImpl` into a new `ProjectContextProvider` abstraction. The `AnalysisContext` returned by `build(analysisId)` is functionally identical before and after the refactoring.

## Modified Files

| File | Change |
|------|--------|
| `backend/src/main/java/com/hopeful117/devlogai/analysis/context/AnalysisContextServiceImpl.java` | Injected `ProjectContextProvider`, removed 6 repository dependencies, removed 4 private helper methods, removed 7 constants, kept analysis-scoped queries and type-conditional filtering |

## New Files

| File | Description |
|------|-------------|
| `backend/src/main/java/com/hopeful117/devlogai/projectcontext/ProjectContextSnapshot.java` | Record holding all project-scoped context data (immutable via `List.copyOf`) |
| `backend/src/main/java/com/hopeful117/devlogai/projectcontext/ProjectContextProvider.java` | Interface with single method `build(UUID projectId)` |
| `backend/src/main/java/com/hopeful117/devlogai/projectcontext/ProjectContextProviderImpl.java` | Implementation injecting all 8 project-scoped repositories, returns all project data without analysis-type filtering |
| `backend/src/test/java/com/hopeful117/devlogai/projectcontext/ProjectContextProviderTest.java` | 7 tests covering all data, empty data, missing profile, pagination limits, immutability, and analysis exclusion |

## Tests

- **AnalysisContextServiceTest**: 5 tests (all pass)
  - `shouldBuildBoundedArchitectureContextForOneProject` — verifies ARCHITECTURE_REVIEW with facts, observations, knowledge events, artifacts, decisions
  - `shouldBuildProjectEvolutionContextWithoutArchitectureKnowledge` — verifies PROJECT_EVOLUTION with milestones, related analyses
  - `shouldKeepUnsupportedPoliciesOnTheCommonContextOnly` — verifies TECHNICAL_DEBT returns empty extra lists
  - `shouldExposeAcceptedProposalsAsImmutableSnapshotsOnly` — verifies proposal immutability
  - `shouldFailWhenAnalysisDoesNotExist` — verifies EntityNotFoundException

- **ProjectContextProviderTest**: 7 tests (all pass)
  - `shouldBuildProjectContextWithAllData` — all repository mocks populated
  - `shouldReturnEmptyListsWhenNoData` — empty repository results
  - `shouldHandleMissingProfileGracefully` — null profile from getLatestByProject
  - `shouldApplyPaginationLimits` — verifies correct PageRequest sizes for all 5 paginated queries
  - `shouldReturnImmutableLists` — verifies UnsupportedOperationException on all 6 lists
  - `shouldReturnAllRecentAnalyses` — no exclusion logic in provider

## Validation

```
./mvnw compile -q  → BUILD SUCCESS (no errors)
./mvnw test -Dtest="AnalysisContextServiceTest,ProjectContextProviderTest" → Tests run: 12, Failures: 0
```

Pre-existing failures (unrelated):
- `AnalysisWorkflowServiceTest.shouldFailTaskAndAnalysisWhenSubmissionFails` — NullPointerException on IntentDefinition
- `InitialCollectorsTest` — assertion failures
- `ValidationControllerWebMvcTest` — HTTP status mismatch
- `DevlogAiBackendApplicationTests.contextLoads` — requires database connection

## Deviations

None. Implementation follows the plan exactly.

## Remaining Work

None for this story. The refactoring is complete and all related tests pass.

## Recommendation

**Ready for Review**
