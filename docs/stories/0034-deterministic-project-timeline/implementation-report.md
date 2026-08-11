# Story 0034: Deterministic Project Timeline — Implementation Report

## Overview

Ajout d'une projection read-model **déterministe et typée** `GET /api/v1/projects/{id}/timeline`
fusionnant 5 sources de confiance (stories complétées, Engineering Events, Knowledge Events,
décisions, milestones complétés) en une seule chronologie triée et bornée — **sans LLM**, sans
nouvelle persistance, sans N+1, avec un tri stable `(timestamp DESC, type.name ASC, id ASC)`
indépendant de l'ordre de déclaration de l'enum.

## Modified Files

### Backend
- `story/repository/EngineeringStoryRepository.java` — ajout de
  `findByProject_IdAndStatusOrderByCompletedAtDescIdDesc(UUID, StoryStatus, Pageable)` (+ import `StoryStatus`).
- `milestone/repository/MilestoneRepository.java` — ajout de
  `findByProjectIdAndStatusOrderByCompletedAtDescIdDesc(UUID, MilestoneStatus, Pageable)`.
- `projectstate/dto/ProjectStateSections.java` — **nouveau** param-object (record 7 sections).
- `projectstate/mapper/ProjectStateMapper.java` — `toResponse` passe de 8 params à
  `(Project, ProjectStateSections)` → résout `java:S107` (code smell résiduel Story 0033).
- `projectstate/service/ProjectStateProjectionServiceImpl.java` — construit et passe `ProjectStateSections`.
- `projectstate/service/ProjectStateProjectionServiceTest.java` — stubs `toResponse(any(), any())`.

### Frontend
- `app.routes.ts` — route enfant `projects/:id/timeline` (lazy `TimelinePage`).
- `features/workspace/project-workspace-layout.html` — entrée sidebar « Timeline » (`routerLink="timeline"`).

## New Files

### Backend
- `timeline/dto/TimelineEntryType.java` — enum `STORY_COMPLETED | ENGINEERING_EVENT | KNOWLEDGE_EVENT | DECISION | MILESTONE_COMPLETED`.
- `timeline/dto/TimelineEntry.java` — record `(UUID id, TimelineEntryType type, Instant timestamp, String title, String detail)`.
- `timeline/dto/TimelineResponse.java` — record `(UUID projectId, String projectName, List<TimelineEntry> entries)`.
- `timeline/mapper/TimelineMapper.java` — MapStruct `@Mapper(componentModel="spring")` ; 5 méthodes
  `toXEntry` + fabrique `toEntry(id, type, timestamp, title, detail)`.
- `timeline/service/TimelineProjectionService.java` — interface.
- `timeline/service/TimelineProjectionServiceImpl.java` — 1 requête bornée par source
  (`PageRequest(0,20)`), filtres `completedAt != null` (story/milestone), merge, tri
  `Comparator.comparing(timestamp, nullsLast).reversed().thenComparing(type.name()).thenComparing(id)`,
  limite globale 20, 404 via `EntityNotFoundException`.
- `timeline/controller/TimelineController.java` — `@GetMapping("/{projectId}/timeline")`.

### Backend (tests)
- `timeline/mapper/TimelineMapperTest.java` — mapping champ-à-champ des 5 sources + `detail`.
- `timeline/service/TimelineProjectionServiceTest.java` — cas vide, tri/tie-break (type.name puis id),
  borne 20, milestone+event, filtrage milestone sans `completedAt`, 404.
- `timeline/controller/TimelineControllerWebMvcTest.java` — 200 + assertions JSON, 404.

### Frontend
- `features/timeline/timeline.models.ts` — `TimelineEntryType`, `TimelineEntry`, `TimelineResponse`.
- `features/timeline/timeline.service.ts` — `getTimeline(projectId)` → `GET …/{id}/timeline`.
- `features/timeline/timeline-page.ts` — vue réactive (route → project → timeline), états
  loading/loaded/not-found/error, `imports: [AsyncPipe]`.
- `features/timeline/timeline-page.html` — liste chronologique (badge type + titre + détail +
  `<time>`), empty-state, états erreur.
- `features/timeline/timeline-page.scss` — tokens réutilisés (panel, badge, empty-state, état erreur).
- `features/timeline/timeline.service.spec.ts` — requête GET + encodage URL.
- `features/timeline/timeline-page.spec.ts` — rendu entrée, empty-state, 404, erreurs, pas de `.subscribe(`.

## Tests

- **Backend** : `./mvnw verify` → **554 tests, 0 échec**, **BUILD SUCCESS**, **jacoco:check
  « All coverage checks have been met »** (ligne ≥ 0.80, `**/*MapperImpl.class` exclu).
- **Frontend unit** : **39 fichiers / 171 tests** verts (162 → 171, +9 cas timeline).
- **Lint/format/build** : `npm run lint` 0 problème ; `format:check` propre ; `ng build` strict OK
  (chunk lazy `timeline-page` présent).

## Validation

```
cd backend && ./mvnw verify                 → BUILD SUCCESS (554 tests) + jacoco check OK
cd frontend && npm run lint                 → 0 problems
cd frontend && npm run format:check         → All matched files use Prettier code style
cd frontend && npm test -- --watch=false    → 39 files / 171 tests passed
cd frontend && npm run build                → OK (strict)
cd backend && ./mvnw sonar:sonar -Dsonar.token=$SONAR_TOKEN  → ANALYSIS SUCCESSFUL
  → Quality gate OK (new_violations=0, new_coverage=80.4 ≥ 80), coverage 84.3 %
```

## Deviations

- **Mapper en méthodes `default`** (au lieu de mapping `@Mapping` piloté par annotation) : les
  entrées timeline combinent type/timestamp/détail de sources hétérogènes ; des `default` buildant
  directement le record via la fabrique `toEntry` sont plus lisibles et évitent des `@Mapping`/
  `@Named` lourds. MapStruct génère toujours `TimelineMapperImpl` (utilisé par les tests).
- **`id` peuplé depuis l'entité source** (pas `null`), pour répondre à AC-2 et au `track entry.id`
  Angular.
- **Bound/borne** : per-source `PageRequest(0,20)` puis limite globale 20, comme validé.

## SonarQube Gate (verdir)

L'analyse Sonar a révélé 1 code smell **résiduel de Story 0033** non lié au code timeline :
`java:S107` sur `ProjectStateMapper.toResponse` (8 paramètres). Pour verdir le quality gate sans
masquer le smell, les 7 sections ont été groupées dans un record `ProjectStateSections`
(param-object) : `toResponse` passe à 2 paramètres. Aucun impact sur le contrat JSON ni sur le
frontend (rétro-compatible). Après refactor : **quality gate OK** (`new_violations=0`,
`new_coverage=80.4`, `new_duplicated_lines_density=0`), 0 issue ouverte.

## Remaining Work

- Commits / proposals explicitement exclus de V1 (documenté dans `story.md` / `repository-analysis.md`).
- Pagination, navigation d'entités liées, regroupement par date et résumé AI — hors scope.
- Aucun changement CI requis ; aucun ADR créé.

## Recommendation

Ready for Review. Endpoint 0034 implémenté : contrat typé, tri déterministe (tie-break explicite),
bornes appliquées, 404 géré, tests backend (unit+webmvc+mapper) et frontend (service+page) verts,
gates qualité (JaCoCo ≥ 0.80, lint, format, build, 171 tests) tous satisfaits.
