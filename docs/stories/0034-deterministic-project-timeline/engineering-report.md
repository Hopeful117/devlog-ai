# Engineering Report

## Story

Story 0034 : ajouter une projection read-model **déterministe et typée** de l'évolution du projet
(`GET /api/v1/projects/{id}/timeline`) fusionnant 5 sources de confiance en une chronologie unique,
triée et bornée — sans LLM, sans persistance nouvelle.

## Objective

Répondre à la question « comment ce projet a-t-il évolué récemment ? » via un contrat fortement typé
et un ordre déterministe, en cohérence avec la directive « Implementation Timing » d'ADR-048
(renforcer Project History / Timeline avant l'artifact generation). Ne pas dégrader l'Overview et
ne pas introduire de relation Story↔Commit « inventée ».

## Repository Analysis Summary

5 sources identifiées et vérifiées : `EngineeringStory` (COMPLETED, `completedAt`), `EngineeringEvent`
(`occurredAt`), `KnowledgeEvent` (`createdAt`), `Decision` (`createdAt`), `Milestone` (COMPLETED,
`completedAt`). 3 requêtes bornées préexistaient (engineering event, knowledge, decision) ; **2
requêtes ont été ajoutées** (story et milestone en `OrderByCompletedAtDescIdDesc`, additive).
Commits et proposals exclus (bruit / duplication des Engineering Events ; relation Story↔Commit non
persistée → aucun join fabriqué).

## Implementation Summary

1. **DTOs** : `TimelineEntryType` (enum des 5 types), `TimelineEntry` (record typé), `TimelineResponse`.
2. **Mapper** : `TimelineMapper` MapStruct — 5 méthodes `toXEntry` + fabrique `toEntry(id, type,
   timestamp, title, detail)` (méthodes `default`, éviter un mapping annotation-driven lourd pour des
   sources hétérogènes).
3. **Service** : `TimelineProjectionServiceImpl` — 404 via `EntityNotFoundException`, une requête
   bornée (`PageRequest(0,20)`) par source, filtres `completedAt != null` (story/milestone), merge,
   tri `(timestamp DESC, type.name ASC, id ASC)` (tie-break explicite, indépendant de l'ordre d'énum),
   limite globale 20.
4. **Controller** : `TimelineController` → `GET /api/v1/projects/{projectId}/timeline`.
5. **Frontend** : feature `features/timeline/` (models, service, page ts/html/scss + specs), route lazy
   `projects/:id/timeline`, entrée sidebar « Timeline ». Overview non modifié.

## Modified Files

| Fichier | Changement |
|---|---|
| `backend/.../story/repository/EngineeringStoryRepository.java` | + `findByProject_IdAndStatusOrderByCompletedAtDescIdDesc` (+ import `StoryStatus`) |
| `backend/.../milestone/repository/MilestoneRepository.java` | + `findByProjectIdAndStatusOrderByCompletedAtDescIdDesc` |
| `frontend/src/app/app.routes.ts` | + route enfan `projects/:id/timeline` |
| `frontend/.../workspace/project-workspace-layout.html` | + lien sidebar « Timeline » |

## Created Files

| Fichier | Type |
|---|---|
| `backend/.../timeline/dto/TimelineEntryType.java` | enum DTO |
| `backend/.../timeline/dto/TimelineEntry.java` | record DTO |
| `backend/.../timeline/dto/TimelineResponse.java` | record DTO |
| `backend/.../timeline/mapper/TimelineMapper.java` | mapper MapStruct |
| `backend/.../timeline/service/TimelineProjectionService.java` | interface service |
| `backend/.../timeline/service/TimelineProjectionServiceImpl.java` | implémentation service |
| `backend/.../timeline/controller/TimelineController.java` | controller |
| `backend/.../timeline/{mapper,service,controller}` tests | 3 fichiers de test |
| `frontend/.../features/timeline/*` | models, service, page ts/html/scss + 2 specs |

## Architecture Impact

- Backend : nouveau package `timeline`, 2 additions de requêtes repos. Aucune persistance, aucune
  migration, aucun changement de schéma/config, aucun ADR.
- Frontend : nouvelle feature + route + nav. Overview intact.
- CI : aucun changement nécessaire (couverts par les jobs backend `verify` + frontend existants).
- Déviations : mapper en méthodes `default` ; `id` propagé depuis l'entité source (AC-2 / `track`).

## Validation

```
cd backend  && ./mvnw verify             → BUILD SUCCESS (554 tests) + jacoco check OK (ligne ≥ 0.80)
cd frontend && npm run lint              → 0 problems
cd frontend && npm run format:check      → All matched files use Prettier code style
cd frontend && ng test --watch=false     → 39 files / 171 tests passed (162 → 171)
cd frontend && npm run build             → OK (strict, chunk lazy `timeline-page`)
cd backend  && ./mvnw sonar:sonar -Dsonar.token=$SONAR_TOKEN → ANALYSIS SUCCESSFUL
            → Quality gate OK (new_violations=0), coverage 84.3 %
```

## SonarQube Gate

1 code smell résiduel **Story 0033** (`java:S107`, `toResponse` à 8 params) a été corrigé en
regroupant les 7 sections dans `ProjectStateSections` (param-object) pour verdir le gate. Après
analyse : **0 issue ouverte**, quality gate **OK** (`new_violations=0`, `new_coverage=80.4`),
couverture projet **84.3 %**.

## Review Outcome

Code Review Approved : 14 AC vérifiées. Tri déterministe (tie-break explicite), bornes appliquées,
contrat typé (pas de `Map<String,Object>`), Story↔Commit non inventé, commits/proposals exclus,
aucun LLM ni N+1.

## Remaining Work

- Pagination / chargement infini, navigation vers entités liées, regroupement par date, résumé AI —
  hors scope V1.
- Exercer la route `timeline` au runtime réel (Playwright e2e) lors du prochain build de la stack
  Docker (couvert en unitaire pour l'instant).

## Lessons Learned

1. **Tie-break déterministe sans reliance à l'ordre d'énum.** Trier avec `type.name()`
   (lexicographique) puis `id`, jamais `ordinal()` — robuste si l'enum est réordonné.
2. **Contrat minimal typé plutôt qu'un bag générique.** Un `TimelineEntry` commun + enum de type
   suffit pour 5 sources ; évite le polymorphisme et le `Map<String,Object>`.
3. **Bornage par source puis agrégation.** Une requête `PageRequest` par source (pas de N+1, pas de
   chargement illimité), tri mémoire, limite globale — simple et prévisible, sans sur-ingénierie.

## Final Status

**Completed**

---

Engineering Story 0034 workflow complete.