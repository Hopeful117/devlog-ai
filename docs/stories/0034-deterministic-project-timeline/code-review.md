# Code Review Report

## Review Summary

L'implémentation ajoute une projection read-model déterministe et typée du projet
(`GET /api/v1/projects/{id}/timeline`) fusionnant 5 sources persistées, sans LLM, sans nouvelle
persistance, avec tri stable et bornes. Backend, mapper, frontend et tests évoluent en lockstep.
Les suites (backend full `verify` + jacoco, frontend 171 tests, lint/format/build) restent vertes.

**Recommandation : Approved**

## Inputs Reviewed

- Story 0034 (approuvée)
- Repository Analysis (approuvée)
- Implementation Plan (approuvé)
- Implementation Report (complète)
- `timeline/dto/*`, `timeline/mapper/TimelineMapper`, `timeline/service/TimelineProjectionServiceImpl`,
  `timeline/controller/TimelineController`
- Repos étendus : `EngineeringStoryRepository`, `MilestoneRepository`
- Tests backend (service, controller WebMvc, mapper)
- Frontend : `features/timeline/*`, `app.routes.ts`, `project-workspace-layout.html`

## Acceptance Criteria Verification

### Backend
- **AC-1 — endpoint renvoie projectId/projectName/entries** : Pass. `TimelineResponse` + controller
  WebMvc test (200 + assertions JSON).
- **AC-2 — entries exposent id/type/timestamp/title/detail** : Pass. `TimelineEntry` ; `id` peuplé
  depuis l'entité source.
- **AC-3 — fusion 5 sources, tri (timestamp DESC → type.name ASC → id ASC), bornée à 20** : Pass.
  Comparator explicite (indépendant de l'ordre d'énum), `.limit(20)` ; testé (tri/tie-break/bornage).
- **AC-4 — projet vide → entries vides non null** : Pass. `List.copyOf` d'une liste vide ; testé.
- **AC-5 — requêtes bornées une par source, pas de N+1** : Pass. `PageRequest(0,20)`/source, 5 requêtes
  au plus, agrégation mémoire.
- **AC-6 — aucun appel LLM** : Pass. Aucune source d'IA dans la chaîne.
- **AC-7 — 404 projet inexistant** : Pass. `EntityNotFoundException` conservée ; test rejoué.
- **AC-8 — tests unitaires populated/empty/tie-break/bornage + intégration 200/404** : Pass.
  `TimelineProjectionServiceTest` (6 cas) + `TimelineControllerWebMvcTest` (2 cas) + `TimelineMapperTest`.

### Frontend
- **AC-9 — feature timeline rend type/titre/détail/date** : Pass. `timeline-page.html` (badge + titre +
  détail + `<time datetime>`).
- **AC-10 — empty-state** : Pass. « No timeline entries yet. ».
- **AC-11 — route + sidebar** : Pass. Route lazy `projects/:id/timeline` + lien « Timeline ».
- **AC-12 — aucun appel LLM** : Pass.
- **AC-13 — spec couvre rendu + empty state** : Pass. `timeline-page.spec.ts` (7 cas).
- **AC-14 — tests existants verts** : Pass. 39 fichiers / 171 tests.

## Implementation Plan Compliance

- 1 Repos étendus (story/milestone, bornés `OrderByCompletedAtDescIdDesc`) ✔
- 2–3 DTOs (`TimelineEntryType`, `TimelineEntry`, `TimelineResponse`) ✔
- 3 Mapper MapStruct (5 sources + fabrique) ✔
- 4 Service (404, 5 requêtes bornées, merge, tri, limite 20) ✔
- 5 Controller ✔
- 6 Tests backend ✔
- 7–8 Frontend feature + route + nav ✔
- 9 Quality gates ✔

**Déviations documentées (assumées)** :
1. Mapper implémenté en méthodes `default` (fabrique `toEntry`) plutôt qu'en mapping `@Mapping`
   piloté par annotation : sources hétérogènes (type/timestamp/détail), plus lisible. `TimelineMapperImpl`
   toujours généré et utilisé par les tests.
2. `id` de l'entité source propagé dans `TimelineEntry` (requis AC-2 / `track` Angular).

## Findings

1. **Tie-break indépendant de l'ordre d'énum (Fonctionnalité : conforme).** Tri explicite par
   `type.name()` puis `id` ; aucun recours à `ordinal()`. ✔
2. **Story↔Commit non inventé (Fonctionnalité : conforme).** `detail` des stories porte seulement le
   numéro ; `baseCommit`/`targetCommit` restent sur l'entité story — aucun join fabriqué avec
   `ProjectCommit` (relation non persistée dans `KnowledgeRelation.EntityType`). ✔
3. **Commits/proposals exclus (Fonctionnalité : conforme décision).** Documenté ; commits trop bruités,
   proposals dupliqueraient les Engineering Events. ✔
4. **Robustesse filtering** : stories/milestones COMPLETED sans `completedAt` filtrées avant mapping
   (évite un `timestamp` null parasite). Testé. ✔
5. **Filtre `nullsLast` sur le compare** : défensif sur un éventuel timestamp null résiduel, sans
   changer l'ordre nominal. ✔
6. **`java:S107` résidu Story 0033 résolu (Fonctionnalité : gate Sonar verdi).** `toResponse` à 8
   paramètres → 2 via le param-object `ProjectStateSections`. Sans impact contrat/frontend. ✔

## Architecture Compliance

- ✅ Read model déterministe des domaines existants ; aucune source de vérité nouvelle.
- ✅ Aucune persistance/migration ; aucune route backend supplémentaire hors `timeline`.
- ✅ Une requête bornée par source, pas de N+1.
- ✅ Contrat fortement typé (record + enum), pas de `Map<String,Object>`.
- ✅ Direction de dépendances inchangée ; mapping centralisé dans le mapper MapStruct.

## Test Assessment

- **Backend** : `./mvnw verify` → **554 tests, 0 échec, BUILD SUCCESS** ; **jacoco:check OK**
  (ligne ≥ 0.80, `**/*MapperImpl.class` exclu).
- **Frontend unit** : **39 fichiers / 171 tests**, 0 échec.
- **Lint/format/build** : 0 problème, Prettier propre, `ng build` strict OK.
- Aucune régression identifiée.

## Validation Performed

```
cd backend  && ./mvnw verify             → BUILD SUCCESS (554 tests) + jacoco check OK
cd frontend && npm run lint              → 0 problems
cd frontend && npm run format:check      → All matched files use Prettier code style
cd frontend && ng test --watch=false     → 39 files / 171 tests passed
cd frontend && npm run build             → OK (strict)
cd backend  && ./mvnw sonar:sonar -Dsonar.token=$SONAR_TOKEN → ANALYSIS SUCCESSFUL
            → Quality gate OK (new_violations=0, new_coverage=80.4), coverage 84.3 %
```

## Residual Risks

- **Heuristique de bornage/merge** : un item ancien peut être éclipsé si >20 items plus récents
  existent dans une autre source. Accepté et documenté (V1 bornée, prévisible).
- **Doublons sémantiques** KNOWLEDGE_EVENT / DECISION / ENGINEERING_EVENT : acceptés (signaux
  distincts), tri déterministe.
- Le runtime réel de la route `timeline` n'est pas exercé par Playwright e2e (couvert en unitaire) ;
  nettable au prochain build de la stack Docker.

## Recommendation

**Approved**

Implémentation correcte et complète : 14 AC vérifiées, tri déterministe (tie-break explicite),
bornes appliquées, contrat typé, 404 géré, aucun LLM ni N+1. Backend et frontend verts (171 tests,
jacoco ≥ 0.80), conformes aux décisions produit approuvées.

---

Code Review completed.

Awaiting human approval before finalization or merge.