# Engineering Report

## Story

Extraire `ProjectContextProvider` et `ProjectContextSnapshot` pour découpler le contexte projet du cycle `Analysis` dans DevLog AI.

## Objective

Rendre le contexte projet (décisions, jalons, artefacts, événements, propositions, analyses récentes, profil le plus récent) accessible indépendamment d'une `Analysis` persistée, tout en préservant le comportement du workflow d'analyse existant.

## Repository Analysis Summary

L'analyse a identifié que 6 des 9 repositories utilisés par `AnalysisContextServiceImpl` sont déjà project-scoped. Le couplage à `analysisId` est artifactuel, pas essentiel au domaine. L'abstraction `ProjectContextProvider` formalise une séparation qui existe déjà dans le code.

Modules affectés : `analysis.context` (modifié), `projectcontext` (nouveau), `profile.service`, `knowledge`, `proposal`, `artifact`, `decision`, `milestone`.

## Implementation Summary

Le refactoring a été réalisé en 6 étapes :
1. Création de `ProjectContextSnapshot` (record immutable)
2. Création de `ProjectContextProvider` (interface)
3. Création de `ProjectContextProviderImpl` (8 repositories injectés)
4. Adaptation de `AnalysisContextServiceImpl` (délégation au provider)
5. Création de `ProjectContextProviderTest` (7 tests)
6. Adaptation de `AnalysisContextServiceTest` (5 tests adaptés)

## Modified Files

| Fichier | Nature |
|---|---|
| `backend/src/main/java/com/hopeful117/devlogai/analysis/context/AnalysisContextServiceImpl.java` | Injection du provider, retrait de 6 repositories, conservation du conditionnel par type |
| `backend/src/test/java/com/hopeful117/devlogai/analysis/context/AnalysisContextServiceTest.java` | Mock du provider au lieu des 6 repositories |

## Created Files

| Fichier | Type |
|---|---|
| `backend/src/main/java/com/hopeful117/devlogai/projectcontext/ProjectContextSnapshot.java` | Record |
| `backend/src/main/java/com/hopeful117/devlogai/projectcontext/ProjectContextProvider.java` | Interface |
| `backend/src/main/java/com/hopeful117/devlogai/projectcontext/ProjectContextProviderImpl.java` | Service |
| `backend/src/test/java/com/hopeful117/devlogai/projectcontext/ProjectContextProviderTest.java` | Test |

## Architecture Impact

Le refactoring introduit une nouvelle couche d'abstraction (`ProjectContextProvider`) entre les repositories et `AnalysisContextServiceImpl`. Cette séparation est cohérente avec l'architecture existante et prépare le terrain pour de futurs consommateurs du contexte projet (comme un agent Kiko).

Aucun breaking change. Aucune modification de schéma. Aucun endpoint ajouté ou modifié.

## Validation

```
./mvnw compile -q → BUILD SUCCESS
./mvnw test -Dtest="AnalysisContextServiceTest,ProjectContextProviderTest" → 12/12 pass
```

Les échecs pré-existants (4 failures, 2 errors) sont non-liés au refactoring (database connection, legacy tests).

## Review Outcome

La Code Review a identifié une seule observation (duplication des constantes de pagination) classée comme non-bloquante. Aucun finding Major ou Blocker.

**Recommandation : Approved**

## Remaining Work

Aucun travail restant pour cette story. Le refactoring est complet et tous les critères d'acceptation sont satisfaits.

## Lessons Learned

1. **Séparation project-scoped vs analysis-scoped** : Le code réel montrait déjà cette séparation implicitement. L'abstraction `ProjectContextProvider` la rend explicite et réutilisable.

2. **Importance du profile analysis-scoped** : La distinction entre `getByAnalysis(analysisId)` et `getLatestByProject(projectId)` est un point de vigilance. Le refactoring a préservé cette distinction.

3. **Tests comme filet de sécurité** : Les 5 tests existants ont servi de filet de sécurité pour vérifier l'équivalence fonctionnelle. L'adaptation des tests (mock du provider) a été straightforward.

## Final Status

**Completed**

---

Engineering Story 0001 workflow complete.
