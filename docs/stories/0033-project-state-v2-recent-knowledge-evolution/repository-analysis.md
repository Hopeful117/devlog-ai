# Repository Analysis — Story 0033 (Project State v2: Recent Knowledge & Recent Evolution)

## Understanding

Story 0033 enrichit la projection déterministe `ProjectState` (`GET /api/v1/projects/{id}/state`) de
deux nouvelles sections top-level — `recentKnowledge` (KnowledgeEvents) et `recentEvolution`
(EngineeringEvents validés) — sans LLM, sans nouvelle persistance, en réutilisant les repos existants.
Les 4 décisions produit ont été validées (trust level des KnowledgeEvents, sections top-level,
limite 5, exposition des commits).

## Overview du module `projectstate` (vérifié)

**Backend :**
- `ProjectStateController` → `GET /api/v1/projects/{projectId}/state`.
- `ProjectStateProjectionServiceImpl` : injecte 8 repos (Project, EngineeringStory, Challenge,
  ValidatableProposal, Decision, Milestone, ProjectCommit) + `ProjectStateMapper` ; assemble 5 sections.
- `ProjectStateResponse` (record) : `projectId`, `projectName`, `objective`, `activeWork`,
  `recentChanges`, `roadmapProgress`, `pendingActions`.
- `ProjectStateMapper` (MapStruct, `componentModel = "spring"`) : `toResponse(...)` + méthodes
  `to<X>Section(...)`, et `to<X>Summary(...)` par entité.

**Frontend :**
- `features/project-state/` : `project-state-page.ts` (`viewModel$` via `route.paramMap` + service),
  `project-state.models.ts`, `project-state-page.html`, `.scss`, `.spec.ts`, `service.ts` + `service.spec.ts`.
- Route `/projects/:id/overview` (lazy) ; lien "Overview" dans la sidebar du workspace.

## Données disponibles (vérifié)

### KnowledgeEvent
- Entité `knowledge/entity/KnowledgeEvent.java` :
  `id (UUID)`, `project`, `title (String)`, `description (text 5000)`, `type (KnowledgeEventType)`,
  `createdAt (Instant, @CreatedDate)`, `updatedAt`.
- `KnowledgeEventType` : FEATURE, BUG, REFACTORING, ARCHITECTURE, DOCUMENTATION, DEPENDENCY,
  SECURITY, PERFORMANCE, TEST, DEPLOYMENT, OTHER.
- Repo `KnowledgeEventRepository` :
  - `List<KnowledgeEvent> findByProjectId(UUID)` ✅
  - `List<KnowledgeEvent> findByProjectIdOrderByCreatedAtDesc(UUID)` ✅ (utilisable)
  - `findByProjectIdOrderByCreatedAtDescIdDesc(UUID, Pageable)` ✅
- **Aucune requête nouvelle nécessaire.**

### EngineeringEvent
- Entité `engineeringevent/EngineeringEvent.java` :
  `id (UUID)`, `project`, `analysis`, `proposal`, `validation`, `source`, `category
  (EngineeringEventCategory)`, `title`, `summary`, `significance`, `baseCommit`, `targetCommit`,
  `occurredAt (Instant)`, `createdAt (Instant)`. (Abs : mergeCommit/comparisonPolicy — présents
  seulement dans le modèle TS frontend / DTO d'exécution, pas dans l'entité.)
- Repo `EngineeringEventRepository` :
  - `List<EngineeringEvent> findRecentByProjectIdOrderByOccurredAtDescTargetCommitDescIdAsc(UUID, Pageable)` ✅
  (tri déjà adapté : par `occurredAt` desc, puis `targetCommit` desc, `id` asc).

## Modifications prévues

### Backend
| Fichier | Changement |
|---|---|
| `projectstate/dto/inner/KnowledgeSummary.java` | Nouveau record : `id, type, title, createdAt` |
| `projectstate/dto/inner/EvolutionSummary.java` | Nouveau record : `id, category, title, baseCommit, targetCommit, occurredAt` |
| `projectstate/dto/response/RecentKnowledgeSection.java` | Nouveau : `List<KnowledgeSummary> recentKnowledge` |
| `projectstate/dto/response/RecentEvolutionSection.java` | Nouveau : `List<EvolutionSummary> recentEvolution` |
| `projectstate/dto/response/ProjectStateResponse.java` | Record : + `recentKnowledge`, `recentEvolution` |
| `projectstate/mapper/ProjectStateMapper.java` | + `toKnowledgeSummary`, `toEvolutionSummary`, `toRecentKnowledgeSection`, `toRecentEvolutionSection` |
| `projectstate/service/ProjectStateProjectionServiceImpl.java` | + injecter 2 repos, builder les 2 sections (limite 5), passer à `toResponse` |
| `ProjectStateProjectionService` (interface) | inchangé |

### Backend — mappers/assemblage (précision)
- `toResponse(...)` doit accepter 2 paramètres supplémentaires pour les sections.
- `recentKnowledge` : `knowledgeRepository.findByProjectIdOrderByCreatedAtDesc(projectId)` puis
  `.subList(0, min(5, size))` (ou `.limit(5)`), mappé en `List<KnowledgeSummary>`.
- `recentEvolution` : `eventRepository.findRecentByProjectIdOrderByOccurredAtDescTargetCommitDescIdAsc(
  projectId, PageRequest.of(0, 5))`.

### Frontend
| Fichier | Changement |
|---|---|
| `project-state.models.ts` | + `KnowledgeSummary`, `EvolutionSummary`, `RecentKnowledgeSection`, `RecentEvolutionSection` ; étendre `ProjectState` |
| `project-state-page.html` | + 2 panneaux (headings + empty-state), styles existants réutilisés |
| `project-state-page.spec.ts` | + cas : rendu + empty-state des 2 sections |
| `project-state.service.ts` | inchangé (même endpoint) |

## Tests attendus

### Backend
- `ProjectStateProjectionServiceTest` : + compilation des 2 sections, populated + empty.
- `ProjectStateControllerWebMvcTest` : endpoint renvoie `recentKnowledge` + `recentEvolution`.

### Frontend
- `project-state-page.spec.ts` : assertions sur le rendu des 2 nouvelles sections.

## Contraintes

- ProjectState = read model déterministe ; sources de vérité = domaines existants.
- Pas de LLM, pas de N+1 (1 requête/section), pas de migration/table.
- Performance < 100 ms (bornes fixes).

## Risques

1. **Interprétation KnowledgeEvent (sans status)** — levée : traité comme connaissance « récemment
   apprise », wording UI dédié.
2. **Changement de record public** — app/model frontend + tests mis à jour dans le même changement ;
   sections existantes inchangées (backward compatible).
3. **Perf** — 2 requêtes supplémentaires bornées ; vérifier < 100 ms in integration test.

## Recommandation

Prêt pour le plan d'implémentation. Périmètre faible et bien délimité, repos prêts, aucun changement
de contrat existant.