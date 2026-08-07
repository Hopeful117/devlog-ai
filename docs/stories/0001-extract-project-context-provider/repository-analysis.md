# Repository Analysis

## Story Understanding

La Story 0001 demande d'extraire la logique de construction du contexte projet (les données project-scoped) actuellement contenue dans `AnalysisContextServiceImpl` vers une nouvelle abstraction `ProjectContextProvider` + `ProjectContextSnapshot`. L'objectif est de rendre le contexte projet accessible indépendamment d'une `Analysis` persistée, tout en préservant strictement le comportement actuel du workflow d'analyse.

C'est un refactoring architectural, pas une nouvelle fonctionnalité utilisateur. L'invariant principal est l'équivalence fonctionnelle du `AnalysisContext` avant et après le refactoring.

## Repository Summary

DevLog AI est un projet Java/Spring Boot avec :
- **Backend** : Spring Boot 3.5.5, Java 21, PostgreSQL 16, Flyway
- **AI Engine** : Python/FastAPI (non impacté par cette story)
- **Frontend** : Angular (non impacté par cette story)
- **Architecture** : Microservice monorepo avec collection de connaissances déterministe, moteur de contexte, et pipeline IA

Le backend suit une architecture en couches : entities → repositories → services → controllers. Les records Java sont utilisés comme DTOs et snapshots. Le code est propre, bien structuré, avec une couverture de tests significative (55 fichiers de tests).

## Affected Modules

| Module | Package | Pourquoi |
|---|---|---|
| `analysis.context` | `com.hopeful117.devlogai.analysis.context` | Contient `AnalysisContextServiceImpl` qui sera modifié pour déléguer au provider |
| `projectcontext` (nouveau) | `com.hopeful117.devlogai.projectcontext` | Nouveau module pour `ProjectContextProvider`, `ProjectContextProviderImpl`, `ProjectContextSnapshot` |
| `profile.service` | `com.hopeful117.devlogai.profile.service` | `ProjectProfileService.getLatestByProject()` sera utilisé par le provider |
| `knowledge` | `com.hopeful117.devlogai.knowledge` | `KnowledgeEventRepository` sera utilisé par le provider |
| `proposal` | `com.hopeful117.devlogai.proposal` | `ValidatableProposalRepository` sera utilisé par le provider |
| `artifact` | `com.hopeful117.devlogai.artifact` | `ArtifactRepository` sera utilisé par le provider |
| `decision` | `com.hopeful117.devlogai.decision` | `DecisionRepository` sera utilisé par le provider |
| `milestone` | `com.hopeful117.devlogai.milestone` | `MilestoneRepository` sera utilisé par le provider |
| `analysis.repository` | `com.hopeful117.devlogai.analysis.repository` | `AnalysisRepository` sera utilisé par le provider pour les analyses récentes |

## Existing Implementation

### AnalysisContextServiceImpl (fichier principal à modifier)

**Chemin** : `backend/src/main/java/com/hopeful117/devlogai/analysis/context/AnalysisContextServiceImpl.java`

**Interface** : `AnalysisContextService` — signature `build(UUID analysisId)`

**Dépendances actuelles** (9 repositories + 1 service) :
- `AnalysisRepository` — charge l'Analysis et le Project
- `FactRepository` — facts de l'analyse courante (analysis-scoped)
- `ObservationRepository` — observations de l'analyse courante (analysis-scoped)
- `ProjectProfileService` — profil de l'analyse courante via `getByAnalysis(analysisId)` (analysis-scoped)
- `KnowledgeEventRepository` — événements du projet (project-scoped)
- `ValidatableProposalRepository` — propositions acceptées du projet (project-scoped)
- `ArtifactRepository` — artefacts archi du projet (project-scoped)
- `DecisionRepository` — décisions du projet (project-scoped)
- `MilestoneRepository` — jalons du projet (project-scoped)
- `AnalysisRepository` (2ème usage) — analyses liées du projet (project-scoped)

**Constantes de pagination** :
- `MAX_FACTS = 100`
- `MAX_OBSERVATIONS = 50`
- `MAX_RECENT_EVENTS = 20`
- `MAX_RELATED_ANALYSES = 10`
- `MAX_ARCHITECTURE_ARTIFACTS = 20`
- `MAX_ARCHITECTURE_DECISIONS = 20`
- `MAX_RECENT_MILESTONES = 10`
- `MAX_VALIDATED_PROPOSALS = 20`

**Comportement conditionnel** :
- `ARCHITECTURE_REVIEW` → charge `relatedAnalyses`, `architectureArtifacts`, `relatedDecisions`
- `PROJECT_EVOLUTION` → charge `relatedAnalyses`, `recentMilestones`
- Autres types → aucune de ces données

**Appelants** :
- `AnalysisWorkflowServiceImpl.start(analysisId)` — workflow principal
- `AiTaskServiceImpl.create(request)` — crée une tâche IA

### AnalysisContext (record)

**Chemin** : `backend/src/main/java/com/hopeful117/devlogai/analysis/context/AnalysisContext.java`

12 champs, 8 records internes. Le record est immutable et public.

### ProjectProfileService

**Chemin** : `backend/src/main/java/com/hopeful117/devlogai/profile/service/ProjectProfileServiceImpl.java`

Deux méthodes de lecture :
- `getByAnalysis(analysisId)` — retourne le profile lié à une analyse spécifique
- `getLatestByProject(projectId)` — retourne le dernier profile du projet

### Repositories project-scoped

Tous supportent déjà la requête par `projectId` :
- `KnowledgeEventRepository.findByProjectIdOrderByCreatedAtDescIdDesc(projectId, pageable)`
- `ValidatableProposalRepository.findByProjectIdAndStatusOrderByCreatedAtDescIdDesc(projectId, ACCEPTED, pageable)`
- `ArtifactRepository.findByProjectIdAndTypeInOrderByCreatedAtDescIdDesc(projectId, types, pageable)`
- `DecisionRepository.findByProjectIdOrderByCreatedAtDescIdDesc(projectId, pageable)`
- `MilestoneRepository.findByProjectIdOrderByStartedAtDescIdDesc(projectId, pageable)`
- `AnalysisRepository.findByProjectIdOrderByCreatedAtDesc(projectId)` (pour les analyses récentes)
- `AnalysisRepository.findByProjectIdAndIdNotOrderByCreatedAtDescIdDesc(projectId, analysisId, pageable)` (pour les analyses liées avec exclusion)

### Tests existants

**`AnalysisContextServiceTest`** — 5 tests :
- `shouldBuildBoundedArchitectureContextForOneProject` — vérifie ARCHITECTURE_REVIEW
- `shouldBuildProjectEvolutionContextWithoutArchitectureKnowledge` — vérifie PROJECT_EVOLUTION
- `shouldKeepUnsupportedPoliciesOnTheCommonContextOnly` — vérifie TECHNICAL_DEBT
- `shouldExposeAcceptedProposalsAsImmutableSnapshotsOnly` — vérifie les propositions
- `shouldFailWhenAnalysisDoesNotExist` — vérifie l'erreur

Le test mocke directement les 9 repositories + `ProjectProfileService`.

## Relevant Documentation

- `docs/decisions/ADR-038.md` — Repository Context Engine
- `docs/decisions/ADR-039.md` — Context Intelligence
- `docs/decisions/ADR-040.md` — Knowledge/Evidence Separation
- `docs/decisions/ADR-006.md` — ValidatableProposal lifecycle
- `docs/architecture.md` — architecture globale
- `docs/knowledge-model.md` — modèle de connaissance
- `docs/pipeline.md` — pipeline d'analyse

## Constraints

1. **Invariant de non-régression** : Le `AnalysisContext` doit être fonctionnellement équivalent avant et après le refactoring.
2. **Profile analysis-scoped préservé** : `AnalysisContext.projectProfile` continue d'être le profil de l'analyse courante, pas le plus récent du projet.
3. **Provider agnostic du type** : `ProjectContextProvider.build(projectId)` ne prend pas de paramètre de type d'analyse.
4. **Aucun breaking change** : L'interface `AnalysisContextService` et le record `AnalysisContext` ne changent pas.
5. **Pas de nouvelle entité JPA** : `ProjectContextSnapshot` est un record Java non persisté.
6. **Pas de modification de schéma** : Aucune migration Flyway.
7. **Pas de nouvelle requête findByProjectId** dans `FactRepository` ou `ObservationRepository`.

## Risks

1. **Régression silencieuse** : Le refactoring pourrait modifier subtilement les données retournées. Mitigation : tests existants + adaptation des tests.
2. **Double appel aux repositories** : Si le provider et le service appellent les mêmes repositories. Mitigation : le provider est le seul à appeler les 6 repositories project-scoped.
3. **Changement involontaire du profile** : Si `getLatestByProject` remplace `getByAnalysis`. Mitigation : AC-7 vérifie ce point.
4. **Conditionnel oublié** : Si le filtrage par `AnalysisType` n'est pas préservé. Mitigation : tests existants couvrent les 3 cas.

## Open Questions

1. **Package du provider** : `com.hopeful117.devlogai.projectcontext` ou dans `analysis.context` ? Recommandation : package séparé.
2. **Constantes de pagination** : Où les placer ? Recommandation : dans le provider pour ses données, dans le service pour facts/observations.
3. **Gestion du latestProjectProfile nullable** : Le provider doit-il retourner null ou un Optional ? Recommandation : nullable (cohérent avec le pattern du codebase).

## Recommendation

**Ready for planning.**

L'analyse du code montre que le refactoring est réalisable en une seule story. Les 6 repositories project-scoped sont déjà dé-couplés de l'analysisId. L'abstraction `ProjectContextProvider` formalise une séparation qui existe déjà dans le code mais n'est pas nommée. Les risques sont maîtrisables grâce aux tests existants.

## Implementation Readiness

- ✅ Tous les repositories nécessaires existent
- ✅ Les requêtes project-scoped existent déjà
- ✅ `ProjectProfileService.getLatestByProject()` existe déjà
- ✅ Les tests existants couvrent le comportement actuel
- ✅ Aucune dépendance externe bloquante
- ✅ Aucun ADR conflictuel
- ✅ Le schema de base de données n'est pas impacté

---

Repository Analysis completed.

Awaiting human approval before Implementation Planning.
