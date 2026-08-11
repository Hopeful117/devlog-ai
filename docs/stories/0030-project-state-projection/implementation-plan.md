# Implementation Plan — Story 0030 (Project State Projection)

## Scope

Backend endpoint + Angular frontend displaying 5 sections answering 5 project questions. No new persistence, no LLM, no cache.

## Architecture

### Backend — New package: `projectstate`

```
projectstate/
├── controller/
│   └── ProjectStateController.java
├── dto/
│   ├── response/
│   │   ├── ProjectStateResponse.java
│   │   ├── ObjectiveSection.java
│   │   ├── ActiveWorkSection.java
│   │   ├── RecentChangesSection.java
│   │   ├── RoadmapProgressSection.java
│   │   └── PendingActionsSection.java
│   └── inner/
│       ├── StorySummary.java
│       ├── ChallengeSummary.java
│       ├── ProposalSummary.java
│       ├── MilestoneSummary.java
│       ├── DecisionSummary.java
│       └── CommitSummary.java
├── mapper/
│   └── ProjectStateMapper.java
└── service/
    ├── ProjectStateProjectionService.java
    └── ProjectStateProjectionServiceImpl.java
```

### Backend — Modifications to existing files

| File | Change |
|---|---|
| `challenge/repository/ChallengeRepository.java` | Add `findByProjectIdAndStatusOrderByCreatedAtDesc(UUID, ChallengeStatus)` |
| `story/repository/EngineeringStoryRepository.java` | Add `findByProjectIdAndStatusOrderByCreatedAtDesc(UUID, StoryStatus)` |

### Frontend — New files

```
features/project-state/
├── project-state-page.ts
├── project-state-page.html
├── project-state-page.scss
├── project-state.service.ts
├── project-state.service.spec.ts
├── project-state.models.ts
└── project-state.models.spec.ts
```

### Frontend — Modified files

| File | Change |
|---|---|
| `app.routes.ts` | Add `/projects/:id/overview` route |
| `workspace/project-workspace-layout.html` | Add "Overview" link in sidebar |

## Implementation Sequence

### Phase 1: Backend DTOs and Mapper (AC-3 to AC-8, AC-12)

1. Create inner DTOs: StorySummary, ChallengeSummary, ProposalSummary, MilestoneSummary, DecisionSummary, CommitSummary
2. Create section DTOs: ObjectiveSection, ActiveWorkSection, RecentChangesSection, RoadmapProgressSection, PendingActionsSection
3. Create ProjectStateResponse (root DTO)
4. Create ProjectStateMapper (MapStruct)
5. Write unit tests for mapper

### Phase 2: Backend Repository Queries (AC-10)

1. Add ChallengeRepository.findByProjectIdAndStatusOrderByCreatedAtDesc
2. Add EngineeringStoryRepository.findByProjectIdAndStatusOrderByCreatedAtDesc
3. Verify existing repository queries cover all section needs

### Phase 3: Backend Service (AC-1, AC-4 to AC-8, AC-10)

1. Create ProjectStateProjectionService interface
2. Create ProjectStateProjectionServiceImpl:
   - Inject all 8 repositories
   - Implement getProjectState(UUID projectId):
     - Fetch project (404 if not found)
     - Assemble each section from repository queries
     - Map to DTOs via mapper
   - No N+1: each section = 1 repository call
3. Write unit tests:
   - All sections populated
   - All sections empty
   - Project not found → 404

### Phase 4: Backend Controller (AC-2, AC-9, AC-11, AC-13, AC-14)

1. Create ProjectStateController:
   - GET /api/v1/projects/{projectId}/state
   - Path variable: UUID projectId (Jakarta validation)
   - Delegates to service
2. Write integration test (MockMvc):
   - Happy path: 200 with all sections
   - Project not found: 404
   - Invalid UUID: 400

### Phase 5: Frontend Service and Models (AC-15, AC-23)

1. Create project-state.models.ts with TypeScript interfaces matching backend DTOs
2. Create project-state.service.ts with getProjectState(projectId) method
3. Write unit test for service

### Phase 6: Frontend Component (AC-15 to AC-22)

1. Create ProjectStatePage component:
   - Route parameter: id (project UUID)
   - Fetch project + state in parallel
   - Display 5 sections with headings matching questions
   - Empty sections show placeholder
   - Loading state while fetching
   - Error state when API fails
   - Responsive layout (desktop + tablet)
2. Write component spec

### Phase 7: Routing Integration (AC-21)

1. Add route /projects/:id/overview in app.routes.ts
2. Add "Overview" link in sidebar navigation
3. Verify navigation works end-to-end

## DTO Design

### ProjectStateResponse (root)

```java
public record ProjectStateResponse(
    UUID projectId,
    String projectName,
    ObjectiveSection objective,
    ActiveWorkSection activeWork,
    RecentChangesSection recentChanges,
    RoadmapProgressSection roadmapProgress,
    PendingActionsSection pendingActions
) {}
```

### Inner DTOs (shared across sections)

```java
public record StorySummary(UUID id, Integer number, String title, StoryStatus status) {}
public record ChallengeSummary(UUID id, String title, ChallengeStatus status, String impact) {}
public record ProposalSummary(UUID id, String type, ProposalStatus status, Double confidence) {}
public record MilestoneSummary(UUID id, String name, MilestoneStatus status) {}
public record DecisionSummary(UUID id, String title, String choice, Instant createdAt) {}
public record CommitSummary(UUID id, String hash, String subject, Instant committedAt, int filesChanged) {}
```

### Section DTOs

```java
public record ObjectiveSection(
    String description,
    MilestoneSummary currentMilestone,
    StorySummary activeStory,
    List<ChallengeSummary> openChallenges
) {}

public record ActiveWorkSection(
    List<StorySummary> inProgressStories,
    List<ChallengeSummary> openChallenges,
    List<ProposalSummary> proposedProposals
) {}

public record RecentChangesSection(
    List<StorySummary> completedStories,
    List<DecisionSummary> recentDecisions,
    List<CommitSummary> recentCommits
) {}

public record RoadmapProgressSection(
    List<MilestoneSummary> plannedMilestones,
    List<StorySummary> registeredStories
) {}

public record PendingActionsSection(
    List<ProposalSummary> proposedProposals,
    List<ChallengeSummary> openChallenges,
    List<StorySummary> unstartedStories
) {}
```

## Performance Strategy

- Each section = 1 repository query (no N+1)
- Total: ~8 queries per request
- No lazy loading: all data fetched eagerly
- No caching: recalculated on demand
- Target: < 100ms (should be well under with simple queries)

## Test Strategy

### Backend Unit Tests (ProjectStateProjectionServiceTest)

- Test each section independently with populated data
- Test each section with empty data
- Test project not found → EntityNotFoundException
- Verify repository calls (Mockito verify)

### Backend Integration Tests (ProjectStateControllerWebMvcTest)

- GET /api/v1/projects/{id}/state → 200 with JSON
- GET /api/v1/projects/{nonexistent}/state → 404
- GET /api/v1/projects/{invalid-uuid}/state → 400

### Frontend Tests

- Service: HTTP call mocked, response mapped to models
- Component: loading/error/loaded states tested
