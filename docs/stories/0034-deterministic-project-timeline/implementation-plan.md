# Story 0034 — Implementation Plan

## 1. Backend — repositories (additive)

- `EngineeringStoryRepository`: add `List<EngineeringStory> findByProject_IdAndStatusOrderByCompletedAtDescIdDesc(UUID projectId, StoryStatus status, Pageable pageable)`.
- `MilestoneRepository`: add `List<Milestone> findByProjectIdAndStatusOrderByCompletedAtDescIdDesc(UUID projectId, MilestoneStatus status, Pageable pageable)`.

## 2. Backend — DTOs (`timeline/dto`)

- `enum TimelineEntryType { STORY_COMPLETED, ENGINEERING_EVENT, KNOWLEDGE_EVENT, DECISION, MILESTONE_COMPLETED }`
- `TimelineEntry(UUID id, TimelineEntryType type, Instant timestamp, String title, String detail)`
- `TimelineResponse(UUID projectId, String projectName, List<TimelineEntry> entries)`

## 3. Backend — mapper (`TimelineMapper`, MapStruct)

- `TimelineEntry toStoryEntry(EngineeringStory story)` -> `type = STORY_COMPLETED`, `timestamp = completedAt`, `title = title`, `detail = "#" + storyNumber` (`@Mapping` + `@Named`/`after` or string concat in default helper).
- `TimelineEntry toEngineeringEventEntry(EngineeringEvent e)` -> `type = ENGINEERING_EVENT`, `timestamp = occurredAt`, `title = title`, `detail = category.name()`.
- `TimelineEntry toKnowledgeEventEntry(KnowledgeEvent k)` -> `type = KNOWLEDGE_EVENT`, `timestamp = createdAt`, `title = title`, `detail = type.name()`.
- `TimelineEntry toDecisionEntry(Decision d)` -> `type = DECISION`, `timestamp = createdAt`, `title = title`, `detail = null`.
- `TimelineEntry toMilestoneEntry(Milestone m)` -> `type = MILESTONE_COMPLETED`, `timestamp = completedAt`, `title = name`, `detail = null`.
- `TimelineEntry toEntry(TimelineEntryType type, Instant timestamp, String title, String detail)` default factory.

## 4. Backend — service

- `TimelineProjectionService.getTimeline(UUID projectId) -> TimelineResponse`.
- `TimelineProjectionServiceImpl`:
  1. `projectRepository.findById(projectId)` else `EntityNotFoundException("Project", projectId)`.
  2. One bounded query per source (`PageRequest.of(0, 20)`):
     - `storyRepository.findByProject_IdAndStatusOrderByCompletedAtDescIdDesc(id, COMPLETED, page)` (filter `completedAt != null`)
     - `engineeringEventRepository.findRecentByProjectIdOrderByOccurredAtDescTargetCommitDescIdAsc(id, page)`
     - `knowledgeEventRepository.findByProjectIdOrderByCreatedAtDescIdDesc(id, page)`
     - `decisionRepository.findByProjectIdOrderByCreatedAtDescIdDesc(id, page)`
     - `milestoneRepository.findByProjectIdAndStatusOrderByCompletedAtDescIdDesc(id, COMPLETED, page)` (filter `completedAt != null`)
  3. Map each raw list to `TimelineEntry` via mapper.
  4. Merge, sort comparator:
     ```
     Comparator.comparing(TimelineEntry::timestamp, Comparator.nullsLast(Comparator.naturalOrder()))
       .reversed()
       .thenComparing(entry -> entry.type().name())
       .thenComparing(TimelineEntry::id)
     ```
  5. `.limit(20)`.
  6. Build `TimelineResponse(projectId, project.name, entries)`.

## 5. Backend — controller

```java
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class TimelineController {
    private final TimelineProjectionService timelineService;
    @GetMapping("/{projectId}/timeline")
    public TimelineResponse getTimeline(@PathVariable UUID projectId) { return timelineService.getTimeline(projectId); }
}
```

## 6. Backend — tests

- `TimelineProjectionServiceTest` (Mockito): populated (multi-source merge + order), empty project, tie-break (same timestamp → type.name ASC then id ASC), global bounding (many items → 20), not-found (404/exception).
- `TimelineControllerWebMvcTest` (extends `ControllerWebMvcTestSupport`): 200 + field assertions, 404 when service throws.
- `TimelineMapperTest` (`TimelineMapperImpl`): each source mapping + factory.

## 7. Frontend — feature `features/timeline/`

- `timeline.models.ts`: `TimelineEntryType` union, `TimelineEntry`, `TimelineResponse`.
- `timeline.service.ts`: `getTimeline(projectId)` -> `GET {backend}/api/v1/projects/{id}/timeline`.
- `timeline-page.ts` (reuses Overview `viewModel$` reactive pattern: route param → service → loaded/error/loading).
- `timeline-page.html`: entries rendered grouped chronologically, type badge + title + detail + ISO timestamp; empty state; loading/error/not-found states.
- `timeline-page.scss`: reuse Overview panel/badge/empty-state tokens.
- `timeline-page.spec.ts`: rendering + empty state.

## 8. Frontend — routing + nav

- `app.routes.ts`: add children route `{ path: 'timeline', loadComponent: () => import('./features/timeline/timeline-page')... }` under `projects/:id`.
- `project-workspace-layout.html`: add sidebar link `routerLink="timeline"` label "Timeline".

## 9. Quality gates

- Backend: `./mvnw test` (JaCoCo line ≥ 0.80 enforced by `quality` job).
- Frontend: `npm run lint`, `npm run format:check`, `npm test` (vitest), `npm run build`.
- Update docs if any drift.

## Order of execution

1. Repos → 2. DTOs/mapper → 3. service/controller → 4. backend tests → 5. backend gates → 6. frontend feature → 7. route/nav → 8. frontend tests → 9. frontend gates → 10. reports.