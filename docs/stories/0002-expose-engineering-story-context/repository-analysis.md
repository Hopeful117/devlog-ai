# Repository Analysis

## Story Understanding

Story 0002 demande d'exposer un premier `EngineeringStoryContext` déterministe via une API REST, permettant à Kiko de consommer le contexte project-scoped sans cycle `Analysis` persisté.

La Story construit sur Story 0001 (`ProjectContextProvider`) et ajoute :
- Un type de réponse dédié (`EngineeringStoryContext`)
- Un service d'assemblage (`EngineeringStoryContextService`)
- Un endpoint REST (`GET /api/projects/{projectId}/engineering-story-context`)

La Story est explicitement bornée : pas de filtering par pertinence, pas de contexte repository, pas d'interprétation IA, pas de persistence.

---

## Repository Summary

Le repository DevLog AI est un microservice Java/Spring Boot avec une architecture en couches :
- **Controllers** : endpoints REST
- **Services** : logique métier
- **Repositories** : accès aux données (Spring Data JPA)
- **Entities** : modèle de domaine JPA

Le package `projectcontext` (introduit par Story 0001) contient `ProjectContextProvider`, `ProjectContextProviderImpl` et `ProjectContextSnapshot`. C'est la fondation de cette Story.

---

## Affected Modules

### `projectcontext` (nouveaux fichiers)

**Pourquoi impliqué** : C'est le package qui contient déjà `ProjectContextProvider`. Les nouveaux fichiers (`EngineeringStoryContext`, `EngineeringStoryContextService`, `EngineeringStoryContextServiceImpl`) y seront ajoutés.

**Responsabilité actuelle** : Fournir le contexte project-scoped via `ProjectContextProvider`.

**Fichiers ajoutés** :
- `EngineeringStoryContext.java` (record)
- `EngineeringStoryContextService.java` (interface)
- `EngineeringStoryContextServiceImpl.java` (implémentation)

### `controller` (nouveau fichier)

**Pourquoi impliqué** : L'endpoint REST doit être exposé via un controller.

**Responsabilité actuelle** : Les controllers existants exposent les API REST du projet.

**Fichier ajouté** :
- `EngineeringStoryContextController.java`

### `shared.exception.handler` (existant, sans modification)

**Pourquoi impliqué** : Le `GlobalExceptionHandler` gère déjà `EntityNotFoundException` et retourne 404. Le controller n'a pas besoin de catch explicite.

**Aucun changement requis.**

---

## Existing Implementation

### `ProjectContextProvider` (Story 0001)

**Location** : `backend/src/main/java/com/hopeful117/devlogai/projectcontext/ProjectContextProvider.java`

Interface avec une méthode :
```java
ProjectContextSnapshot build(UUID projectId);
```

### `ProjectContextProviderImpl` (Story 0001)

**Location** : `backend/src/main/java/com/hopeful117/devlogai/projectcontext/ProjectContextProviderImpl.java`

Injecte 8 repositories :
- `ProjectRepository`
- `ProjectProfileService`
- `KnowledgeEventRepository`
- `ValidatableProposalRepository`
- `ArtifactRepository`
- `DecisionRepository`
- `MilestoneRepository`
- `AnalysisRepository`

Retourne un `ProjectContextSnapshot` contenant toutes les données project-scoped.

### `ProjectContextSnapshot` (Story 0001)

**Location** : `backend/src/main/java/com/hopeful117/devlogai/projectcontext/ProjectContextSnapshot.java`

Record avec 8 champs :
- `project` (AnalysisContext.ProjectSnapshot)
- `latestProjectProfile` (ProjectProfileResponse, nullable)
- `recentKnowledgeEvents` (List)
- `validatedProposals` (List)
- `architectureArtifacts` (List)
- `relatedDecisions` (List)
- `recentMilestones` (List)
- `recentAnalyses` (List)

Toutes les listes sont immutables (List.copyOf).

### `GlobalExceptionHandler`

**Location** : `backend/src/main/java/com/hopeful117/devlogai/shared/exception/handler/GlobalExceptionHandler.java`

Gère `EntityNotFoundException` → 404 NOT_FOUND. Le controller peut laisser l'exception se propager.

### Pattern de controller existant

**Exemple** : `ProjectProfileController`

```java
@RestController
@RequiredArgsConstructor
public class ProjectProfileController {
    private final ProjectProfileService profileService;

    @GetMapping("/api/v1/projects/{projectId}/latest-profile")
    public ResponseEntity<ProjectProfileResponse> latestByProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(profileService.getLatestByProject(projectId));
    }
}
```

Le pattern est : `@RestController`, injection via `@RequiredArgsConstructor`, `@GetMapping` avec `@PathVariable`, retour `ResponseEntity`.

### Pattern de test controller

**Exemple** : `ProjectControllerWebMvcTest`

Utilise `ControllerWebMvcTestSupport` comme base, MockMvc, mocks Mockito, vérifications status et jsonPath.

---

## Relevant Documentation

- `docs/stories/0001-extract-project-context-provider/` — Story 0001 complétée
- ADR-037 — Repository-First Context Extraction
- ADR-038 — Repository Context Engine
- ADR-039 — Context Intelligence

---

## Constraints

1. **Pas de fausse Analysis** — EngineeringStoryContext ne doit pas créer d'entité Analysis
2. **Pas de détournement d'AnalysisContext** — Le type analysis-scoped n'est pas réutilisé pour un contexte project-scoped
3. **Déterminisme** — Aucune interprétation IA dans le contexte retourné
4. **Réutilisation de ProjectContextProvider** — La fondation Story 0001 est consommée telle quelle
5. **Pas de modification des services existants** — AnalysisContextService, ProjectContextProvider restent inchangés
6. **Pas de nouveau Context Profile** — Context Intelligence n'est pas modifié
7. **Pas de persistence** — EngineeringStoryContext n'est pas entité JPA
8. **Pas de frontend** — API REST uniquement
9. **Pas de filtering** — Le contexte complet est retourné (filtrage client-side par Kiko)

---

## Risks

### Risk-1 : Le contexte complet peut être volumineux

**Impact** Faible pour V1. Le `ProjectContextSnapshot` contient des listes paginées (max 20 éléments par liste). La taille est contrôlée par les constantes dans `ProjectContextProviderImpl`.

### Risk-2 : Absence de contexte repository

**Impact** Moyen. Kiko n'aura pas accès aux fichiers du code source impactés par la story. Ce gap est documenté et explicitement hors scope.

### Risk-3 : Le record EngineeringStoryContext enveloppe ProjectContextSnapshot

**Impact** Faible. Le coupling est intentionnel. Si le snapshot évolue, l'enveloppe s'adapte. La couche d'abstraction permet d'ajouter des champs metadata (horodatage, etc.) sans modifier le snapshot.

---

## Open Questions

None.

---

## Recommendation

**Ready for planning**

Le repository est suffisamment compris. La fondation (Story 0001) est en place. Les patterns de controller et de test existent. Aucunblocking prerequisite n'est identifié.

---

## Implementation Readiness

- `ProjectContextProvider` existe et fonctionne (Story 0001)
- Le pattern de controller est documenté et testé
- Le `GlobalExceptionHandler` gère `EntityNotFoundException` → 404
- Aucun changement de schéma BDD requis
- Aucune nouvelle entité JPA requise
- Aucune dépendance externe supplémentaire

Aucun blocking prerequisite identifié.

---

Repository Analysis completed.

Human approval required before Implementation Planning.

Awaiting explicit human approval.
