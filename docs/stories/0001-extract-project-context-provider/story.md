# Story 0001 — Extraire `ProjectContextProvider` du contexte d'analyse

## Status

Completed

## 1. Title

Extraire `ProjectContextProvider` et `ProjectContextSnapshot` pour découpler le contexte projet du cycle `Analysis`

## 2. Context

DevLog AI construit actuellement tout contexte — projet inclus — à travers `AnalysisContextServiceImpl`, qui prend un `analysisId` comme unique point d'entrée. Ce couplage est justifié pour le workflow d'analyse existant, où chaque donnée projet est récupérée dans le cadre d'une exécution d'analyse spécifique.

Cependant, le projet se dirige vers un usage où DevLog doit pouvoir fournir du contexte projet à des consommateurs externes (comme un agent Kiko préparant des Engineering Stories) sans qu'une `Analysis` persistée soit nécessaire. Le contexte projet — décisions, jalons, artefacts, événements de connaissance, propositions validées, profil le plus récent — existe indépendamment de toute analyse et devrait pouvoir être construit indépendamment.

L'analyse architecturale du code a montré que 6 des 9 repositories utilisés par `AnalysisContextServiceImpl` sont déjà project-scoped. Le couplage à `analysisId` est artifactuel pour ces 6 sources, pas essentiel au domaine.

## 3. Problem

`AnalysisContextServiceImpl.build(UUID analysisId)` est le seul moyen de construire un contexte structuré du projet. Ce service :

- exige un `analysisId` valide comme unique point d'entrée ;
- obtient le `projectId` uniquement via `analysis.getProject()` ;
- utilise `analysis.getType()` pour conditionner le chargement de certaines données (artefacts, décisions, jalons) ;
- charge le profil via `projectProfileService.getByAnalysis(analysisId)` au lieu de `getLatestByProject(projectId)`.

Les 6 requêtes suivantes sont **déjà project-scoped** dans le code mais sont invoquées à travers le filtre de l'analysisId :

1. `knowledgeEventRepository.findByProjectIdOrderByCreatedAtDescIdDesc(projectId)`
2. `proposalRepository.findByProjectIdAndStatusOrderByCreatedAtDescIdDesc(projectId, ACCEPTED)`
3. `artifactRepository.findByProjectIdAndTypeInOrderByCreatedAtDescIdDesc(projectId, types)`
4. `decisionRepository.findByProjectIdOrderByCreatedAtDescIdDesc(projectId)`
5. `milestoneRepository.findByProjectIdOrderByStartedAtDescIdDesc(projectId)`
6. `analysisRepository.findByProjectIdAndIdNotOrderByCreatedAtDescIdDesc(projectId, analysisId)` (les analyses liées, hors courante)

Aucune abstraction ne permet de construire ce sous-ensemble project-scoped sans analysisId.

## 4. Objective

Introduire une abstraction `ProjectContextProvider` qui encapsule la construction du contexte projet pur (les données project-scoped), et un record `ProjectContextSnapshot` qui représente ce contexte. Adapter `AnalysisContextServiceImpl` pour déléguer au provider lors de la construction du `AnalysisContext`, sans modifier la sémantique, le comportement observable, ni le contrat des consommateurs existants.

## 5. Architectural Intent

Séparer deux responsabilités actuellement fusionnées dans `AnalysisContextServiceImpl` :

- **Responsabilité A** : Construire le contexte projet pur (décisions, jalons, artefacts, événements, propositions, analyses récentes, profil le plus récent) à partir d'un `projectId`.
- **Responsabilité B** : Composer le contexte d'analyse complet en combinant le contexte projet avec les données analysis-scoped (facts, observations, snapshot de l'analyse courante, profil de l'analyse courante, exclusion de l'analyse courante dans les analyses liées).

La responsabilité A est extraite dans `ProjectContextProvider`. La responsabilité B reste dans `AnalysisContextServiceImpl`.

Le flux cible est :

```
repositories (project-scoped)
    → ProjectContextProvider
        → ProjectContextSnapshot

ProjectContextSnapshot
    → AnalysisContextServiceImpl (compose avec analysis-scoped data)
        → AnalysisContext
```

Plus tard, un second consommateur pourra utiliser directement `ProjectContextSnapshot` :

```
ProjectContextSnapshot
    → EngineeringStoryContextService (hors scope de cette story)
```

## 6. Scope

### Inclus

- Création de `ProjectContextProvider` (interface)
- Création de `ProjectContextProviderImpl` (implémentation)
- Création de `ProjectContextSnapshot` (record)
- Récupération des 6 sources de données project-scoped dans le provider
- Récupération du profil le plus récent via `ProjectProfileService.getLatestByProject(projectId)`
- Adaptation de `AnalysisContextServiceImpl` pour déléguer au provider
- Conservation des données analysis-scoped dans `AnalysisContextServiceImpl`
- Conservation du comportement conditionnel lié à `AnalysisType`
- Préservation de la sémantique du `projectProfile` de l'analyse courante
- Préservation de l'exclusion de l'analyse courante dans `relatedAnalyses`
- Tests unitaires du nouveau provider
- Adaptation des tests existants d'`AnalysisContextService`

### Exclus

Voir section 7.

## 7. Out of Scope

- Endpoint Engineering Story
- `EngineeringStoryContext` (DTO)
- `StoryFocus`
- Kiko / OpenClaw
- Agent Job Orchestrator (ADR-042)
- Monitoring passif (ADR-041)
- AI Engine / soumission de tâches IA
- Nouvelle interprétation IA
- Modification de `ValidatableProposal` (lecture existante uniquement)
- Analyse avancée de Git / Commit Diff (ADR-035/036)
- Modification du schéma de `facts` ou `observations`
- Ajout de requêtes `findByProjectId` dans `FactRepository` ou `ObservationRepository`
- Frontend
- Authentification
- Refactoring du Repository Context Engine
- Refactoring de Knowledge Selection Service
- Refactoring du IntentCatalog

## 8. Current Behavior

`AnalysisContextServiceImpl.build(UUID analysisId)` fonctionne comme suit :

1. Charge l'`Analysis` via `analysisRepository.findById(analysisId)`
2. Obtient le `Project` via `analysis.getProject()`
3. **Conditionnellement** selon `analysis.getType()` :
   - `ARCHITECTURE_REVIEW` → charge `relatedAnalyses`, `architectureArtifacts`, `relatedDecisions`
   - `PROJECT_EVOLUTION` → charge `relatedAnalyses`, `recentMilestones`
   - Autres types → aucune de ces données
4. Charge le profile via `projectProfileService.getByAnalysis(analysisId)`
5. Charge les facts via `factRepository.findByAnalysisId(analysisId)`
6. Charge les observations via `observationRepository.findByAnalysisId(analysisId)`
7. Charge les événements de connaissance via `knowledgeEventRepository.findByProjectId(projectId)`
8. Charge les propositions validées via `proposalRepository.findByProjectIdAndStatus(projectId, ACCEPTED)`
9. Assemble le record `AnalysisContext`

**Conséquences de ce comportement :**

- Le profile retourné est toujours celui de l'analyse courante, pas le plus récent du projet.
- Les analyses liées excluent toujours l'analyse courante.
- Les artefacts et décisions ne sont chargés que pour `ARCHITECTURE_REVIEW`.
- Les jalons ne sont chargés que pour `PROJECT_EVOLUTION`.
- Aucune donnée projet n'est accessible sans un `analysisId` valide.

## 9. Expected Behavior

Après le refactoring, le comportement observable de `AnalysisContextServiceImpl.build(UUID analysisId)` est **fonctionnellement identique** :

- Le `projectProfile` dans `AnalysisContext` continue d'être le profil de l'analyse courante (via `projectProfileService.getByAnalysis(analysisId)`).
- Les analyses liées continuent d'exclure l'analyse courante.
- Le conditionnel par `AnalysisType` est préservé.
- Les mêmes données sont retournées pour les mêmes inputs.

Le changement interne est :

- Les 6 sources project-scoped sont construites par `ProjectContextProvider.build(projectId)`.
- `AnalysisContextServiceImpl` compose le `AnalysisContext` en combinant le `ProjectContextSnapshot` avec les données analysis-scoped.
- Le `ProjectContextSnapshot.latestProjectProfile` est obtenu via `projectProfileService.getLatestByProject(projectId)` (différent du `projectProfile` de l'`AnalysisContext`).

Un nouveau point d'entrée existe : `ProjectContextProvider.build(projectId)` retourne un `ProjectContextSnapshot` contenant le contexte projet pur, accessible sans `analysisId`.

## 10. Detailed Requirements

### 10.1 `ProjectContextSnapshot` (record)

Le record doit contenir :

| Champ | Type | Source | Condition de chargement |
|---|---|---|---|
| `project` | `ProjectSnapshot` | `AnalysisContext.ProjectSnapshot` | Toujours |
| `latestProjectProfile` | `ProjectProfileResponse` | `ProjectProfileService.getLatestByProject(projectId)` | Si un profile existe pour le projet |
| `recentKnowledgeEvents` | `List<KnowledgeEventSnapshot>` | `KnowledgeEventRepository.findByProjectId(projectId)` | Toujours |
| `validatedProposals` | `List<ValidatedProposalSnapshot>` | `ValidatableProposalRepository.findByProjectIdAndStatus(projectId, ACCEPTED)` | Toujours |
| `architectureArtifacts` | `List<ArtifactSnapshot>` | `ArtifactRepository.findByProjectIdAndTypeIn(projectId, types)` | Toujours (pas conditionnel) |
| `relatedDecisions` | `List<DecisionSnapshot>` | `DecisionRepository.findByProjectId(projectId)` | Toujours (pas conditionnel) |
| `recentMilestones` | `List<MilestoneSnapshot>` | `MilestoneRepository.findByProjectId(projectId)` | Toujours (pas conditionnel) |
| `recentAnalyses` | `List<AnalysisSnapshot>` | `AnalysisRepository.findByProjectIdOrderByCreatedAtDesc(projectId)` | Toujours (sans exclusion) |

**Points importants :**

- `latestProjectProfile` est optionnel (`Optional<ProjectProfileResponse>` ou nullable) car un projet peut ne pas encore avoir de profile.
- `architectureArtifacts`, `relatedDecisions`, `recentMilestones` sont **toujours** chargés par le provider (pas de condition par `AnalysisType`). Le conditionnel par type reste dans `AnalysisContextServiceImpl` qui choisit quels champs du snapshot utiliser.
- `recentAnalyses` contient les analyses récentes du projet **sans exclusion** de l'analyse courante. L'exclusion est la responsabilité de `AnalysisContextServiceImpl`.

### 10.2 `ProjectContextProvider` (interface)

```java
public interface ProjectContextProvider {
    ProjectContextSnapshot build(UUID projectId);
}
```

Signature minimale. Pas de paramètre de type d'analyse — le provider est agnostic du type.

### 10.3 `ProjectContextProviderImpl` (implémentation)

Le provider injecte :

- `ProjectRepository` (ou récupère le project via une autre source — à évaluer)
- `ProjectProfileService` (méthode `getLatestByProject`)
- `KnowledgeEventRepository`
- `ValidatableProposalRepository`
- `ArtifactRepository`
- `DecisionRepository`
- `MilestoneRepository`
- `AnalysisRepository`

Le provider applique les mêmes limites que `AnalysisContextServiceImpl` pour la pagination :

| Limite | Constante | Valeur actuelle |
|---|---|---|
| Knowledge events | `MAX_RECENT_EVENTS` | 20 |
| Validated proposals | `MAX_VALIDATED_PROPOSALS` | 20 |
| Architecture artifacts | `MAX_ARCHITECTURE_ARTIFACTS` | 20 |
| Decisions | `MAX_ARCHITECTURE_DECISIONS` | 20 |
| Milestones | `MAX_RECENT_MILESTONES` | 10 |
| Related analyses | `MAX_RELATED_ANALYSES` | 10 |

Ces constantes peuvent être déplacées dans le provider ou dans une classe partagée.

### 10.4 Adaptation de `AnalysisContextServiceImpl`

Le service continue de :

1. Charger l'`Analysis` via `analysisRepository.findById(analysisId)`
2. Obtenir le `Project` via `analysis.getProject()`
3. Appeler `projectContextProvider.build(projectId)` pour les données project-scoped
4. Charger les facts via `factRepository.findByAnalysisId(analysisId)` (analysis-scoped)
5. Charger les observations via `observationRepository.findByAnalysisId(analysisId)` (analysis-scoped)
6. Charger le profile via `projectProfileService.getByAnalysis(analysisId)` (analysis-scoped, **pas** le latestProjectProfile)
7. **Composer** le `AnalysisContext` en combinant :
   - Le `ProjectContextSnapshot` pour les données project-scoped
   - Les données analysis-scoped (facts, observations, profile, analysis snapshot)
   - Le filtrage conditionnel par `AnalysisType` (artefacts, décisions, jalons, analyses liées)
   - L'exclusion de l'analyse courante dans les analyses liées

**Le profile de l'analyse courante** (`projectProfileService.getByAnalysis(analysisId)`) n'est **pas** remplacé par `latestProjectProfile`. Les deux existent dans des contextes différents.

**Le conditionnel par `AnalysisType`** reste dans `AnalysisContextServiceImpl`. Le provider retourne toutes les données, et le service filtre selon le type d'analyse. Cela signifie que le provider peut retourner des artefacts, décisions et jalons que le service ne utilisera pas pour certains types d'analyse — c'est acceptable car le coût de ces requêtes est faible (20 items max chacune) et cela simplifie le provider.

**L'exclusion de l'analyse courante** dans `relatedAnalyses` reste dans `AnalysisContextServiceImpl`. Le provider retourne toutes les analyses récentes, et le service filtre.

### 10.5 Constantes et limites

Les constantes de pagination doivent être définies une seule fois. Options :

- **Option A** : Les constantes restent dans `AnalysisContextServiceImpl` et sont partagées avec le provider via une classe dédiée (ex: `ContextLimits`).
- **Option B** : Les constantes sont dans le provider et `AnalysisContextServiceImpl` les importe.
- **Option C** : Les constantes sont dans une classe de configuration partagée.

L'option B est la plus simple : le provider est la source des limites pour les données qu'il gère.

## 11. Domain / Architecture Constraints

### Contrainte 1 : Invariant de non-régression

> Pour une même `Analysis` et les mêmes données persistées, le `AnalysisContext` observé après le refactoring doit être fonctionnellement équivalent à celui produit avant.

Cet invariant est le critère ultime. Toute déviation dans les données retournées est un bug de régression.

### Contrainte 2 : Le profile analysis-scoped est préservé

`AnalysisContext.projectProfile` continue d'être le profil de l'analyse courante, pas le plus récent du projet. Ces deux concepts sont distincts et ne doivent pas être confondus.

### Contrainte 3 : Le provider est agnostic du type d'analyse

`ProjectContextProvider.build(projectId)` ne prend pas de paramètre de type d'analyse. Il retourne toutes les données project-scoped. Le filtrage par type reste dans `AnalysisContextServiceImpl`.

### Contrainte 4 : Aucun breaking change pour les consommateurs

L'interface `AnalysisContextService` et le record `AnalysisContext` ne changent pas. Les consommateurs (`AnalysisWorkflowServiceImpl`, `AiTaskServiceImpl`, `KnowledgeSelectionService`, `RepositoryContextEngine`) ne sont pas modifiés.

### Contrainte 5 : Les données analysis-scoped restent dans leur repository

Pas de requête `findByProjectId` ajoutée dans `FactRepository` ou `ObservationRepository`. Ces données restent liées à une analyse par conception de domaine.

### Contrainte 6 : Pas de nouvelle entité JPA

`ProjectContextSnapshot` est un record Java, pas une entité JPA. Il n'est pas persisté — il est construit à la volée et consommé immédiatement.

## 12. Acceptance Criteria

### AC-1 : `ProjectContextProvider` existe et est injectable

Un bean `ProjectContextProvider` est disponible dans le contexte Spring. `ProjectContextProviderImpl` est annotated `@Service` et injecte les repositories nécessaires.

### AC-2 : `ProjectContextSnapshot` contient les 8 champs

`ProjectContextSnapshot` est un record Java public avec les champs : `project`, `latestProjectProfile` (nullable), `recentKnowledgeEvents`, `validatedProposals`, `architectureArtifacts`, `relatedDecisions`, `recentMilestones`, `recentAnalyses`. Toutes les listes sont immutables (`List.copyOf`).

### AC-3 : Le provider retourne des données project-scoped

`ProjectContextProvider.build(projectId)` retourne un `ProjectContextSnapshot` contenant les données du projet identifiées par `projectId`. Le provider n'accepte pas de `analysisId`.

### AC-4 : Le profil le plus récent est récupéré via `getLatestByProject`

`ProjectContextSnapshot.latestProjectProfile` est obtenu via `ProjectProfileService.getLatestByProject(projectId)`. Si aucun profile n'existe, le champ est null (ou absent selon le design du record).

### AC-5 : Les données sont paginées avec les limites existantes

Le provider applique les mêmes limites de pagination que celles actuellement définies dans `AnalysisContextServiceImpl` (MAX_FACTS=100 pour facts, MAX_OBSERVATIONS=50 pour observations — mais ces-là restent dans le service ; MAX_RECENT_EVENTS=20, MAX_VALIDATED_PROPOSALS=20, MAX_ARCHITECTURE_ARTIFACTS=20, MAX_ARCHITECTURE_DECISIONS=20, MAX_RECENT_MILESTONES=10, MAX_RELATED_ANALYSES=10 pour les données du provider).

### AC-6 : `AnalysisContextServiceImpl` délègue au provider

`AnalysisContextServiceImpl.build(analysisId)` appelle `ProjectContextProvider.build(projectId)` pour obtenir les données project-scoped. Les 6 repositories project-scoped ne sont plus appelés directement par `AnalysisContextServiceImpl`.

### AC-7 : Le profile analysis-scoped est préservé

`AnalysisContext.projectProfile` est toujours obtenu via `projectProfileService.getByAnalysis(analysisId)`, pas via `latestProjectProfile`.

### AC-8 : Le conditionnel par `AnalysisType` est préservé

Le comportement actuel est préservé :
- `ARCHITECTURE_REVIEW` → `relatedAnalyses`, `architectureArtifacts`, `relatedDecisions`
- `PROJECT_EVOLUTION` → `relatedAnalyses`, `recentMilestones`
- Autres types → aucune de ces données dans `AnalysisContext`

Le provider retourne toutes les données ; `AnalysisContextServiceImpl` filtre.

### AC-9 : L'exclusion de l'analyse courante est préservée

`AnalysisContext.relatedAnalyses` n'inclut pas l'analyse courante. Cette exclusion est appliquée par `AnalysisContextServiceImpl` après avoir reçu les données du provider.

### AC-10 : Le `AnalysisContext` est fonctionnellement équivalent

Pour une `Analysis` donnée et les mêmes données persistées, le `AnalysisContext` retourné après le refactoring contient exactement les mêmes données que celui retourné avant. Vérifiable via les tests existants.

### AC-11 : Tous les tests existants passent

`AnalysisContextServiceTest` passe sans modification du comportement observé. Les tests de `RepositoryContextServiceTest`, `KnowledgeSelectionServiceTest` et tout autre test consommant `AnalysisContext` ne sont pas affectés.

### AC-12 : Le provider est testé unitairement

Un nouveau test `ProjectContextProviderTest` vérifie que le provider :
- retourne les données de tous les repositories
- applique les bonnes limites de pagination
- gère l'absence de profile (latestProjectProfile = null)
- retourne des listes vides quand les repositories sont vides

## 13. Impacted Components

### Nouveaux fichiers

| Fichier | Type | Package |
|---|---|---|
| `ProjectContextProvider.java` | Interface | `com.hopeful117.devlogai.projectcontext` |
| `ProjectContextProviderImpl.java` | Service | `com.hopeful117.devlogai.projectcontext` |
| `ProjectContextSnapshot.java` | Record | `com.hopeful117.devlogai.projectcontext` |
| `ProjectContextProviderTest.java` | Test unitaire | `com.hopeful117.devlogai.projectcontext` |

### Fichiers modifiés

| Fichier | Nature de la modification |
|---|---|
| `AnalysisContextServiceImpl.java` | Injection de `ProjectContextProvider`, délégation pour les 6 sources project-scoped, conservation des sources analysis-scoped, conservation du conditionnel par type |
| `AnalysisContextServiceTest.java` | Adaptation : mock du `ProjectContextProvider` au lieu des 6 repositories project-scoped, vérification que le provider est appelé avec le bon `projectId` |

### Fichiers inchangés

| Fichier | Raison |
|---|---|
| `AnalysisContextService.java` | L'interface `build(UUID analysisId)` ne change pas |
| `AnalysisContext.java` | Le record ne change pas |
| `AnalysisWorkflowServiceImpl.java` | Appelle `analysisContextService.build(analysisId)` — pas de changement |
| `AiTaskServiceImpl.java` | Idem |
| `KnowledgeSelectionServiceImpl.java` | Prend `AnalysisContext` en entrée — pas de changement |
| `RepositoryContextEngine.java` | Idem |
| `ProjectProfileServiceImpl.java` | Le provider appelle `getLatestByProject` — pas de modification de ce service |
| Tous les repositories | Aucune requête ajoutée |

## 14. Test Strategy

### Tests existants à adapter

**`AnalysisContextServiceTest`**

Le test actuel mocke directement les 6 repositories project-scoped. Après le refactoring :

- Mock `ProjectContextProvider` au lieu des 6 repositories project-scoped
- Vérifier que `ProjectContextProvider.build(projectId)` est appelé avec le bon `projectId`
- Conserver les mocks pour `factRepository`, `observationRepository`, `projectProfileService` (getByAnalysis), `analysisRepository`
- Vérifier que les données project-scoped dans `AnalysisContext` correspondent à celles retournées par le provider mocké
- Les tests de conditionnel par type (ARCHITECTURE_REVIEW, PROJECT_EVOLUTION, TECHNICAL_DEBT) sont adaptés pour vérifier que le service filtre correctement les données du provider

### Tests nouveaux

**`ProjectContextProviderTest`**

Tests unitaires avec mocks des 7-8 repositories :

- `shouldBuildProjectContextWithAllData` — projet avec données dans tous les repositories
- `shouldReturnEmptyListsWhenNoData` — projet sans données
- `shouldHandleMissingProfileGracefully` — `getLatestByProject` retourne empty
- `shouldApplyPaginationLimits` — vérifier que les limites (20, 20, 20, 20, 10, 10) sont appliquées
- `shouldReturnImmutableLists` — les listes dans le snapshot sont immutables
- `shouldReturnAllRecentAnalyses` — le provider retourne toutes les analyses sans exclusion

### Tests non affectés

- `RepositoryContextServiceTest` — mock `AnalysisContext` directement
- `KnowledgeSelectionServiceTest` — idem
- Tout test ne dépendant pas de `AnalysisContextServiceImpl`

## 15. Migration / Compatibility

### Base de données

Aucune migration Flyway. Aucune modification de schéma. `ProjectContextSnapshot` est un record Java non persisté.

### API REST

Aucun endpoint ajouté ou modifié. Aucun breaking change pour les consommateurs HTTP.

### Interne

`AnalysisContextServiceImpl` change son implémentation interne mais pas son contrat public (`build(UUID analysisId)` retourne le même `AnalysisContext`). Les consommateurs internes (`AnalysisWorkflowServiceImpl`, `AiTaskServiceImpl`) ne sont pas modifiés.

### Rollback

Si le refactoring introduce une régression, supprimer `ProjectContextProvider*` et restaurer l'implémentation directe dans `AnalysisContextServiceImpl`. Le coût de rollback est faible car les fichiers sont peu nombreux et isolés.

## 16. Risques

### Risque 1 : Régression silencieuse dans les données

**Probabilité** : Moyenne
**Impact** : Élevé
**Mitigation** : L'invariant de non-régression est vérifié par les tests existants. L'adaptation des tests doit couvrir explicitement l'équivalence fonctionnelle. Un test de comparaison directe (construire l'ancien et le nouveau contexte, vérifier l'égalité) pourrait être ajouté.

### Risque 2 : Double appel aux repositories

**Probabilité** : Faible
**Impact** : Faible
**Mitigation** : Le provider est le seul à appeler les 6 repositories project-scoped. `AnalysisContextServiceImpl` ne les appelle plus directement. Vérifier par inspection du code qu'il n'y a pas de double appel.

### Risque 3 : Changement involontaire du profile analysis-scoped

**Probabilité** : Faible
**Impact** : Élevé
**Mitigation** : Le test doit vérifier explicitement que `AnalysisContext.projectProfile` provient de `getByAnalysis(analysisId)` et non de `getLatestByProject(projectId)`. AC-7 couvre ce point.

### Risque 4 : Le provider retourne trop de données

**Probabilité** : Faible
**Impact** : Faible
**Mitigation** : Le provider applique les mêmes limites de pagination. Le coût de 6 requêtes simples avec LIMIT 10-20 est négligeable.

### Risque 5 : Le conditionnel par type est oublié

**Probabilité** : Faible
**Impact** : Moyen
**Mitigation** : AC-8 vérifie que le conditionnel est préservé. Les tests existants (3 cas de type) couvrent ce comportement.

## 17. Definition of Done

- [ ] `ProjectContextProvider` interface créée dans `com.hopeful117.devlogai.projectcontext`
- [ ] `ProjectContextSnapshot` record créé dans `com.hopeful117.devlogai.projectcontext`
- [ ] `ProjectContextProviderImpl` créé et annoté `@Service`
- [ ] Le provider injecte les 7-8 repositories et `ProjectProfileService`
- [ ] `AnalysisContextServiceImpl` injecte `ProjectContextProvider`
- [ ] `AnalysisContextServiceImpl` délègue les 6 sources project-scoped au provider
- [ ] `AnalysisContextServiceImpl` conserve : facts, observations, profile analysis-scoped, analysis snapshot
- [ ] `AnalysisContextServiceImpl` conserve le conditionnel par `AnalysisType`
- [ ] `AnalysisContextServiceImpl` conserve l'exclusion de l'analyse courante dans `relatedAnalyses`
- [ ] `ProjectContextProviderTest` créé et passe
- [ ] `AnalysisContextServiceTest` adapté et passe
- [ ] Tous les tests existants passent (`mvn test`)
- [ ] Aucune migration Flyway ajoutée
- [ ] Aucun endpoint ajouté ou modifié
- [ ] Aucun fichier hors scope modifié
- [ ] Le `AnalysisContext` est fonctionnellement équivalent avant/après le refactoring

## 18. Open Questions

### Q1 : Faut-il une classe `ContextLimits` partagée ?

Les constantes de pagination (MAX_RECENT_EVENTS, etc.) sont actuellement dans `AnalysisContextServiceImpl`. Après le refactoring, elles sont utilisées par le provider. Deux options :

- **Option A** : Les constantes restent dans `AnalysisContextServiceImpl` et le provider les reçoit via injection ou constantes statiques.
- **Option B** : Les constantes sont dans le provider, et `AnalysisContextServiceImpl` les importe si nécessaire (pour facts/observations qui restent dans le service).

**Recommandation** : Option B — le provider est la source des limites pour les données qu'il gère. `AnalysisContextServiceImpl` garde ses propres limites pour facts et observations.

### Q2 : Le package `projectcontext` est-il le bon ?

Le package `com.hopeful117.devlogai.projectcontext` est une suggestion. Il pourrait aussi aller dans `com.hopeful117.devlogai.analysis.context` (puisque c'est utilisé par `AnalysisContextServiceImpl`) ou rester séparé. La séparation est préférable car le provider a une responsabilité distincte de l'analyse.

**Recommandation** : Package séparé `com.hopeful117.devlogai.projectcontext` pour refléter la séparation de responsabilité.

### Q3 : Faut-il un test d'intégration ?

Un test d'intégration avec la base de données pourrait vérifier que le provider retourne des données réelles. Cependant, les tests unitaires avec mocks sont suffisants pour valider le refactoring.

**Recommandation** : Non pour cette story. Les tests unitaires avec mocks couvrent le refactoring. Un test d'intégration pourrait être ajouté plus tard si le provider est utilisé par de nouveaux consommateurs.
