# Implementation Plan

## Overview

Extraire la logique de construction du contexte projet (6 sources project-scoped) de `AnalysisContextServiceImpl` vers une nouvelle abstraction `ProjectContextProvider` + `ProjectContextSnapshot`, puis adapter `AnalysisContextServiceImpl` pour déléguer au provider tout en préservant le comportement observable.

## Planned Changes

### Étape 1 — Créer le record `ProjectContextSnapshot`

Nouveau record Java dans un nouveau package `com.hopeful117.devlogai.projectcontext`.

Le record contient :
- `project` (`AnalysisContext.ProjectSnapshot`)
- `latestProjectProfile` (`ProjectProfileResponse`, nullable)
- `recentKnowledgeEvents` (`List<AnalysisContext.KnowledgeEventSnapshot>`)
- `validatedProposals` (`List<AnalysisContext.ValidatedProposalSnapshot>`)
- `architectureArtifacts` (`List<AnalysisContext.ArtifactSnapshot>`)
- `relatedDecisions` (`List<AnalysisContext.DecisionSnapshot>`)
- `recentMilestones` (`List<AnalysisContext.MilestoneSnapshot>`)
- `recentAnalyses` (`List<AnalysisContext.AnalysisSnapshot>`)

Toutes les listes sont immutables (`List.copyOf` dans le constructeur compact).

### Étape 2 — Créer l'interface `ProjectContextProvider`

```java
public interface ProjectContextProvider {
    ProjectContextSnapshot build(UUID projectId);
}
```

### Étape 3 — Créer `ProjectContextProviderImpl`

Le service injecte :
- `ProjectRepository`
- `ProjectProfileService`
- `KnowledgeEventRepository`
- `ValidatableProposalRepository`
- `ArtifactRepository`
- `DecisionRepository`
- `MilestoneRepository`
- `AnalysisRepository`

Constantes de pagination (déplacées depuis `AnalysisContextServiceImpl`) :
- `MAX_RECENT_EVENTS = 20`
- `MAX_VALIDATED_PROPOSALS = 20`
- `MAX_ARCHITECTURE_ARTIFACTS = 20`
- `MAX_ARCHITECTURE_DECISIONS = 20`
- `MAX_RECENT_MILESTONES = 10`
- `MAX_RELATED_ANALYSES = 10`

Le provider retourne toutes les données project-scoped sans conditionnel par type d'analyse.

### Étape 4 — Adapter `AnalysisContextServiceImpl`

Modifications :
1. Injecter `ProjectContextProvider`
2. Appeler `projectContextProvider.build(projectId)` pour obtenir le `ProjectContextSnapshot`
3. Extraire les données project-scoped du snapshot
4. **Conserver** le conditionnel par `AnalysisType` en filtrant les données du provider
5. **Conserver** l'exclusion de l'analyse courante dans `relatedAnalyses`
6. **Conserver** `projectProfileService.getByAnalysis(analysisId)` pour le profile analysis-scoped
7. **Conserver** les appels à `factRepository` et `observationRepository` (analysis-scoped)
8. Retirer les injections des 6 repositories project-scoped (ils sont maintenant dans le provider)

### Étape 5 — Créer `ProjectContextProviderTest`

Tests unitaires avec mocks des 7-8 repositories :
- `shouldBuildProjectContextWithAllData`
- `shouldReturnEmptyListsWhenNoData`
- `shouldHandleMissingProfileGracefully`
- `shouldApplyPaginationLimits`
- `shouldReturnImmutableLists`
- `shouldReturnAllRecentAnalyses`

### Étape 6 — Adapter `AnalysisContextServiceTest`

Modifications :
1. Mock `ProjectContextProvider` au lieu des 6 repositories project-scoped
2. Vérifier que `ProjectContextProvider.build(projectId)` est appelé avec le bon `projectId`
3. Conserver les mocks pour `factRepository`, `observationRepository`, `projectProfileService` (getByAnalysis), `analysisRepository` (findById)
4. Vérifier que les données project-scoped dans `AnalysisContext` correspondent à celles retournées par le provider mocké

## Files to Modify

| Fichier | Action |
|---|---|
| `backend/src/main/java/com/hopeful117/devlogai/analysis/context/AnalysisContextServiceImpl.java` | Injection du provider, délégation, retrait des 6 injections project-scoped |
| `backend/src/test/java/com/hopeful117/devlogai/analysis/context/AnalysisContextServiceTest.java` | Adaptation des mocks et vérifications |

## Files to Create

| Fichier | Type |
|---|---|
| `backend/src/main/java/com/hopeful117/devlogai/projectcontext/ProjectContextSnapshot.java` | Record |
| `backend/src/main/java/com/hopeful117/devlogai/projectcontext/ProjectContextProvider.java` | Interface |
| `backend/src/main/java/com/hopeful117/devlogai/projectcontext/ProjectContextProviderImpl.java` | Service |
| `backend/src/test/java/com/hopeful117/devlogai/projectcontext/ProjectContextProviderTest.java` | Test |

## Dependencies

Aucune nouvelle dépendance externe. Le provider utilise uniquement les repositories et services existants.

## Test Plan

1. `ProjectContextProviderTest` — 6 tests unitaires couvrant les cas normaux et edge cases
2. `AnalysisContextServiceTest` — 5 tests adaptés pour vérifier la délégation au provider
3. `mvn test` — tous les tests existants continuent de passer
4. `mvn compile` — le projet compile sans erreur

## Risks

1. **Régression dans les données** — Les tests existants vérifient l'équivalence fonctionnelle
2. **Profile analysis-scoped confondu avec latestProjectProfile** — Le test vérifie explicitement que `getByAnalysis` est appelé pour le profile de l'AnalysisContext
3. **Conditionnel par type oublié** — Les 3 cas de type sont testés

## Validation Checklist

- [ ] `ProjectContextProvider` interface créée
- [ ] `ProjectContextSnapshot` record créé avec 8 champs
- [ ] `ProjectContextProviderImpl` créé et annoté `@Service`
- [ ] Le provider injecte les 7-8 repositories
- [ ] `AnalysisContextServiceImpl` injecte `ProjectContextProvider`
- [ ] Les 6 injections project-scoped sont retirées de `AnalysisContextServiceImpl`
- [ ] Le conditionnel par `AnalysisType` est préservé
- [ ] L'exclusion de l'analyse courante est préservée
- [ ] Le profile analysis-scoped est préservé
- [ ] `ProjectContextProviderTest` créé et passe
- [ ] `AnalysisContextServiceTest` adapté et passe
- [ ] `mvn test` passe
- [ ] `mvn compile` passe
- [ ] Aucun fichier hors scope modifié

## Recommendation

Prêt pour implémentation.
