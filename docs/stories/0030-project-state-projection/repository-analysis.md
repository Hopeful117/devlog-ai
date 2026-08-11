# Repository Analysis — Story 0030 (Project State Projection)

## Understanding

Story 0030 demande un endpoint déterministe `GET /api/v1/projects/{id}/state` qui projette les données existantes de DevLog en 5 sections répondant aux 5 questions d'un ingénieur, plus une page Angular qui affiche cette projection.

Le projet est fusionné (backend + frontend) car la projection sans UI n'a pas de valeur utilisateur.

## Repository Overview

**Backend (Spring Boot) :**
- Pattern : Controller → Service (interface + impl) → Repository → Mapper → DTO
- DTOs : records Java (pas de Lombok pour les DTOs)
- Mappers : MapStruct avec `@Mapper(componentModel = "spring")`
- Tests unitaires : Mockito (`@ExtendWith(MockitoExtension.class)`)
- Tests d'intégration : MockMvc via `ControllerWebMvcTestSupport`
- Exceptions : `EntityNotFoundException` pour les 404
- Audit : `@CreatedDate` / `@LastModifiedDate` avec `AuditingEntityListener`

**Frontend (Angular standalone) :**
- Composants standalone avec imports explicites
- Services injectés via `inject()`
- Routes lazy-loaded dans `app.routes.ts`
- Patterns RxJS : `switchMap`, `catchError`, `startWith`, `shareReplay`
- Layout : sidebar + content area via `ProjectWorkspaceLayout`
- Styles : SCSS avec conventions BEM

## Data Available for Projection

| Section | Repositories existants | Requêtes disponibles | Requête manquante |
|---|---|---|---|
| `objective` | `MilestoneRepository` | `findByProjectIdAndStatusOrderByStartedAtDesc` ✅ | — |
| | `EngineeringStoryRepository` | `findByProjectIdOrderByCreatedAtDesc` ✅ | `findByProjectIdAndStatus` ❌ |
| | `ChallengeRepository` | `findByProjectIdOrderByCreatedAtDesc` ✅ | `findByProjectIdAndStatus` ❌ |
| `activeWork` | `ChallengeRepository` | idem | `findByProjectIdAndStatus` ❌ |
| | `ValidatableProposalRepository` | `findByProjectIdAndStatus` ✅ | — |
| `recentChanges` | `EngineeringStoryRepository` | idem | Filtrer COMPLETED côté service ✅ |
| | `DecisionRepository` | `findByProjectIdOrderByCreatedAtDesc` ✅ | — |
| | `ProjectCommitRepository` | `findByProjectIdOrderByCommittedAtDescCommitHashDesc` ✅ | — |
| `roadmapProgress` | `MilestoneRepository` | `findByProjectIdAndStatusOrderByStartedAtDesc` ✅ | — |
| | `EngineeringStoryRepository` | idem | Filtrer REGISTERED côté service ✅ |
| `pendingActions` | `ValidatableProposalRepository` | `findByProjectIdAndStatus` ✅ | — |
| | `ChallengeRepository` | idem | `findByProjectIdAndStatus` ❌ |

**Requêtes manquantes à ajouter :**
- `ChallengeRepository.findByProjectIdAndStatusOrderByCreatedAtDesc(UUID projectId, ChallengeStatus status)`
- `EngineeringStoryRepository.findByProjectIdAndStatusOrderByCreatedAtDesc(UUID projectId, StoryStatus status)`

## Modules Affectés

| Composant | Impact |
|---|---|
| `projectstate/` (nouveau) | Controller, Service, DTOs, Mapper — backend uniquement |
| `challenge/repository/ChallengeRepository.java` | Ajout query `findByProjectIdAndStatus` |
| `story/repository/EngineeringStoryRepository.java` | Ajout query `findByProjectIdAndStatus` |
| `app.routes.ts` | Ajout route overview |
| `features/project-state/` (nouveau) | Composant Angular, service, modèles |
| `features/workspace/project-workspace-layout.html` | Ajout lien "Overview" dans la sidebar |
| Tests backend | Unit + intégration |
| Tests frontend | Composant spec |

## Behaviors That Must Not Change

- Les endpoints existants (aucun modifié)
- Les entités et migrations existantes
- Le layout sidebar existant (ajout d'un lien, pas de refonte)
- Les patterns de test existants
- Le routing existant (ajout, pas de remplacement)

## Documentation

- `docs/roadmap.md`
- `docs/stories/0029-engineering-story-identity/story.md`
- Conventions backend (Controller, Service, Repository patterns)
- Conventions frontend (standalone components, RxJS patterns)

## Constraints

- Aucune nouvelle table ni migration
- Réutilise les repositories et entités existantes
- < 100ms response time, pas de N+1
- Pas de LLM, pas de cache
- Le frontend ne doit pas être over-engineered (5 sections, pas de formulaire)
- Les patterns existants doivent être respectés (MapStruct, records, standalone components)

## Risks

1. **N+1 queries** : Si la projection lazy-load des entités liées, les performances seront mauvaises. **Mitigation** : chaque section utilise une seule requête, les données sont assemblées en mémoire.

2. **Repository manquant** : `ChallengeRepository` n'a pas de query par status. **Mitigation** : ajouter la query (1 ligne de code).

3. **Frontend scope creep** : La page Overview pourrait grossir si on ajoute trop de navigation. **Mitigation** : scope strict — 5 sections, données display-only, pas de formulaire.

4. **Route existante** : Le route par défaut `/projects/{id}` charge actuellement `ProjectDetailPage` (le cockpit). **Mitigation** : remplacer par la nouvelle vue Overview, ou ajouter un sous-route.

## Recommendation

**Prêt pour le planning.** Le repository est bien compris, les patterns sont clairs, les données existent. Les seules modifications backend sont : 1 nouveau package `projectstate` + 2 queries ajoutées aux repositories existants. Le frontend est un composant standalone avec un service.
