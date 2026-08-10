# Code Review Report — Story 0029

## Review Summary

Story 0029 introduit l'entité `EngineeringStory` et son infrastructure pour établir la traçabilité déterministe entre Engineering Stories et leur évolution Git.

**Recommandation : Approved** — avec 3 finding de type Minor à traiter.

## Inputs Reviewed

- Story 0029 story.md
- Repository Analysis approuvée
- Implementation Plan approuvé
- Implementation Report
- Code source : `EngineeringStory.java`, `StoryStatus.java`, `EngineeringStoryRepository.java`, `EngineeringStoryService.java`, `EngineeringStoryServiceImpl.java`, `EngineeringStoryController.java`, `EngineeringStoryMapper.java`, DTOs, `ProjectContextSnapshot.java`, `ProjectContextProviderImpl.java`, `AnalysisContext.java`, `AnalysisContextServiceImpl.java`, `RepositoryContextAdapter.java`
- Migration : `V36__create_engineering_stories_table.sql`
- Tests : `EngineeringStoryServiceTest.java`, `EngineeringStoryControllerWebMvcTest.java`, `ProjectContextProviderTest.java`, `RepositoryContextAdapterTest.java`

## Acceptance Criteria Verification

| AC | Description | Status |
|----|-------------|--------|
| AC-1 | `EngineeringStory` JPA entity with the 10 planned fields | ✅ `EngineeringStory.java` |
| AC-2 | `StoryStatus` enum with `REGISTERED/IN_PROGRESS/COMPLETED` | ✅ `StoryStatus.java` |
| AC-3 | Migration V36 creating the table (FK CASCADE, UNIQUE, CHECK) | ✅ `V36__create_engineering_stories_table.sql` |
| AC-4 | Repository with project queries | ✅ `EngineeringStoryRepository.java` |
| AC-5 | Service `register/startImplementation/complete/getById/getByProject` | ✅ `EngineeringStoryServiceImpl.java` |
| AC-6 | Controller REST at `/api/v1/projects/{projectId}/stories` | ✅ `EngineeringStoryController.java` |
| AC-7 | Request/Response DTOs with Jakarta validation | ✅ |
| AC-8 | MapStruct mapper | ✅ `EngineeringStoryMapper.java` |
| AC-9 | Unit tests service + controller | ✅ 10 tests (`EngineeringStoryServiceTest`, `EngineeringStoryControllerWebMvcTest`) |
| AC-10 | `ProjectContextSnapshot` enriched with recent stories | ✅ `engineeringStories` field + wiring |
| AC-11 | All existing tests continue to pass | ✅ 529 tests, 0 failures |

Transitions d'état testées : démarrer une story déjà démarrée et compléter une story non démarrée sont couverts par `shouldThrowWhenStartingAnAlreadyStartedStory` / `shouldThrowWhenCompletingWithoutStarting`.

## Code Quality

- **Pattern consistency**: Suit le pattern CRUD Challenge/Decision (Story 0024/0027) — structure de package, mapper MapStruct, DTO, contrôleur.
- **Immutabilité**: Les listes du snapshot sont copiées via `List.copyOf()` dans le constructeur compact.
- **Limites**: `MAX_ENGINEERING_STORIES = 20`, cohérent avec les limites existantes du provider.
- **Transitions d'état**: Validation linéaire `REGISTERED → IN_PROGRESS → COMPLETED` appliquée.
- **Migration**: Contrainte UNIQUE `(project_id, story_number)` + CHECK status, cohérente avec V34 (challenges).

## Findings

### Minor 1 — Dead code dans le repository (nettoyage) — ✅ Corrigé
`EngineeringStoryRepository` déclarait 3 méthodes non utilisées par le code de production :
- `existsByProject_Id`
- `findByProject_IdAndStoryNumber`
- `findByProject_IdAndStatusOrderByStoryNumber`

**Résolution :** méthodes supprimées (ainsi que les imports `StoryStatus`/`Optional` devenus inutiles).

### Minor 2 — Gestion HTTP des transitions invalides (500 au lieu d'un statut client) — ✅ Corrigé
`EngineeringStoryServiceImpl.requireStatus` levait une `IllegalStateException` (aucun handler → HTTP 500).

**Résolution :** remplacée par `ConflictException` (409), déjà mappée par le `GlobalExceptionHandler`. Tests mis à jour (`assertThrows(ConflictException.class)`).

### Minor 3 — Absence de scoping par projet sur `getById` / `start` / `complete` — ✅ Corrigé
Ces opérations ignoraient le `projectId` du path et pouvaient agir sur une story d'un autre projet.

**Résolution :**
- Signatures service : `getById(storyId, projectId)`, `startImplementation(storyId, projectId, ...)`, `complete(storyId, projectId, ...)`
- `requireStoryInProject(...)` vérifie que `story.project.id == projectId`, sinon lève `EntityNotFoundException`
- Contrôleur propage `projectId`
- Nouveau test `shouldThrowWhenStoryBelongsToAnotherProject`

## Recommendation

**Approved.** Les 3 findings Minor ont été traités et validés. Suite complète : **529 tests, 0 échec** (528 existants + 1 nouveau test de scoping). Aucun Blocker ni Major restant.

## SonarQube Quality Gate (AC-11)

Analyse exécutée via `mvnw sonar:sonar` (localhost:9000) :

| Measure | Value | Threshold | Status |
|---------|-------|-----------|--------|
| new_coverage | 81.2% | ≥ 80% | ✅ OK |
| new_duplicated_lines_density | 0.0% | ≤ 3% | ✅ OK |
| new_violations | 0 | 0 | ✅ OK |
| caycStatus | compliant | — | ✅ OK |

**Verdict : PASSED** — Quality Gate global `OK`.

Corrections ajoutées pour passer le gate :
- `DecisionServiceImpl` : littéral `"Decision"` dupliqué extrait en constante `DECISION` (CRITICAL in leak).
- `EngineeringStoryServiceTest` : lambda simplifiée (une seule invocation potentiellement levable) (MAJOR).
- Note : la première analyse utilisait un `jacoco.xml` périmé (goal `report` non lié au phase `test`) → couverture faussée à 79.8%. Après régénération via `jacoco:report`, la couverture réelle du code nouveau est **81.2%**.