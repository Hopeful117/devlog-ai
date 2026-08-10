# Engineering Story 0002

## Status

Completed

## Metadata

- **Story ID:** 0002
- **Title:** Expose deterministic EngineeringStoryContext from ProjectContextProvider
- **Status:** Completed
- **Created:** 2026-08-07
- **Author:** Kiko (OpenClaw)
- **Depends on:** Story 0001 (ProjectContextProvider)

---

## Objective

Expose un premier `EngineeringStoryContext` déterministe via une API REST, consommable par Kiko pour préparer et analyser des Engineering Stories sans cycle `Analysis` persisté.

---

## Motivation

L'objectif produit prioritaire est de faire de DevLog un agent spécialisé assistant Kiko pendant la préparation et l'analyse des Engineering Stories. Le flux cible est :

```text
Kiko
  → décrit une Engineering Story
  → DevLog sélectionne le contexte projet pertinent
  → EngineeringStoryContext
  → Kiko
```

Story 0001 a introduit `ProjectContextProvider` et `ProjectContextSnapshot`, rendant le contexte project-scoped accessible sans `Analysis`. Cette capacité constitue la fondation de `EngineeringStoryContext`.

Story 0002 construit le premier incrément fonctionnel : un type dédié et un endpoint permettant à Kiko de consommer ce contexte.

---

## Scope

### Inclus

- Enregistrement `EngineeringStoryContext` structurant la réponse pour Kiko
- Service `EngineeringStoryContextService` assemblant le contexte à partir de `ProjectContextProvider`
- Endpoint REST `GET /api/projects/{projectId}/engineering-story-context`

### Exclu

- Filtrage par mot-clé ou pertinence story (V1 retourne tout le contexte project-scoped)
- Contexte repository (fichiers modifiés, stack technique) — nécessite un mécanisme hors `Analysis` à définir
- Interprétation IA — les faits restent déterministes
- Persistance de `EngineeringStoryContext`
- Frontend
- Orchestration agent ou monitoring passif
- Nouveau Context Profile dans `ContextIntelligence` — prématuré sans consommateur IA

---

## Faits / Evidence

### Établis (code existant)

- `ProjectContextProvider.build(projectId)` retourne un `ProjectContextSnapshot` contenant : project, latestProjectProfile, recentKnowledgeEvents, validatedProposals, architectureArtifacts, relatedDecisions, recentMilestones, recentAnalyses
- `ProjectContextSnapshot` est un record Java immutable (List.copyOf)
- Les 6 repositories project-scoped sont déjà injectés dans `ProjectContextProviderImpl`
- Le service existant `AnalysisContextServiceImpl` consomme déjà `ProjectContextProvider` — le pattern est validé
- Les constants de pagination sont définies dans `ProjectContextProviderImpl`

### Données sélectionnées comme pertinentes

- Le `ProjectContextSnapshot` est la source unique pour le contexte project-scoped
- Le `EngineeringStoryContext` enveloppe le snapshot avec des métadonnées (horodatage)

### Interprétations hors scope

- Quels éléments du contexte sont pertinents pour une story spécifique — nécessite un mécanisme de sélection que nous n'avons pas encore
- Comment scoring ou filtrer le contexte par rapport à la description de la story — nécessite IA ou heuristique dédiée
- Quels fichiers du code source sont impactés par la story — nécessite un mécanisme de mapping story→fichiers

---

## Acceptance Criteria

### AC-1 : `EngineeringStoryContext` existe et contient le `ProjectContextSnapshot`

**Given** un `projectId` valide
**When** le contexte est construit
**Then** le `EngineeringStoryContext` contient le `ProjectContextSnapshot` complet (tous les champs)

### AC-2 : `EngineeringStoryContext` est un record Java immutable

**Evidence** Le type est un `record` avec un compact constructor appliquant `List.copyOf` sur les listes du snapshot.

### AC-3 : `EngineeringStoryContextService` existe et est injectable

**Evidence** L'interface et l'implémentation sont annotées `@Service` avec `@RequiredArgsConstructor`. L'implémentation dépend de `ProjectContextProvider`.

### AC-4 : L'assemblage délégué à `ProjectContextProvider`

**Evidence** `EngineeringStoryContextServiceImpl.build(projectId)` appelle `projectContextProvider.build(projectId)` et enveloppe le résultat dans un `EngineeringStoryContext`.

### AC-5 : L'endpoint REST expose le contexte

**Evidence** `GET /api/projects/{projectId}/engineering-story-context` retourne un `ResponseEntity<EngineeringStoryContext>` avec status 200. Le paramètre `projectId` est un `@PathVariable UUID`.

### AC-6 : L'endpoint retourne 404 si le projet n'existe pas

**Evidence** Si `ProjectContextProvider.build(projectId)` lève une exception (projet inexistant), l'endpoint retourne 404.

### AC-7 : Le contexte est déterministe

**Evidence** Pour un même `projectId`, le `EngineeringStoryContext` retourné est identique (même données, même structure). Aucune interprétation IA n'est produite.

### AC-8 : Les tests unitaires couvrent le service

**Evidence** `EngineeringStoryContextServiceTest` contient au minimum 2 tests : un cas nominal et un cas d'erreur (projet inexistant).

### AC-9 : Les tests existants passent

**Evidence** `mvn test -Dtest="EngineeringContextServiceTest,AnalysisContextServiceTest,ProjectContextProviderTest"` — tous les tests passent.

---

## Impacted Components

| Composant | Type | Impact |
|---|---|---|
| `projectcontext` | Package existant | Ajout de 2 fichiers : record + service |
| `controller` | Package existant | Ajout de 1 controller |
| `AnalysisContextService` | Service existant | Aucun changement |
| `ProjectContextProvider` | Service existant (Story 0001) | Aucun changement — consommé tel quel |
| `RepositoryContextEngine` | Service existant | Aucun changement — pas de coupling |
| `ContextIntelligence` | Service existant | Aucun changement — pas de nouveau profile |

---

## Risks

### Risk-1 : Couplage entre `EngineeringStoryContext` et `ProjectContextSnapshot`

**Impact** Faible. `EngineeringStoryContext` est une enveloppe de `ProjectContextSnapshot`. Si le snapshot évolut, l'enveloppe s'adapte. Le coupling est intentionnel et documenté.

### Risk-2 : Le endpoint retourne trop de données pour une story spécifique

**Impact** Faible pour V1. Le filtrage par pertinence est explicitement hors scope. Le consommateur (Kiko) peut filtrer côté client. Un endpoint avec filtering sera ajouté dans une story future.

### Risk-3 : Absence de contexte repository (fichiers, stack technique)

**Impact** Moyen. Kiko n'aura pas encore accès aux fichiers impactés par la story. Ce gap est documenté et sera addressé dans une story future nécessitant un mécanisme de mapping story→fichiers.

---

## Architecture Notes

### Pourquoi un nouveau service plutôt que réutiliser `RepositoryContextEngine`

Le `RepositoryContextEngine` requiert un `AnalysisContext` et un `IntentDefinition` en entrée. Ces types sont liés au pipeline d'analyse existant. Les utiliser pour `EngineeringStoryContext` violerait la contrainte « ne pas créer de fausse Analysis ». Le nouveau service est plus simple, plus direct, et respecte la séparation des responsabilités.

### Pourquoi pas de nouveau Context Profile

Les Context Profiles (ADR-039) sont conçus pour le pipeline d'analyse avec `IntentDefinition`. `EngineeringStoryContext` n'utilise pas ce pipeline. L'ajout d'un profile prématuré créerait une fausse couche d'abstraction. Le profile sera ajouté quand un consommateur IA utilisera le contexte.

### Pourquoi pas de filtering

Le filtering par pertinence nécessite de comprendre la story (NLP ou heuristique). Pour V1, le contexte complet est retourné. Kiko, en tant que LLM, peut sélectionner les éléments pertinents lui-même. Le filtering sera ajouté quand le besoin sera validé par l'usage.

---

## Definition of Done

- [ ] `EngineeringStoryContext` record créé
- [ ] `EngineeringStoryContextService` interface et implémentation créées
- [ ] `EngineeringStoryContextController` créé
- [ ] Tests unitaires du service créés
- [ ] `mvn compile`成功
- [ ] `mvn test` — tous les tests passent
- [ ] Documentation de la story complétée
- [ ] Aucun changement dans les services existants (AnalysisContextService, ProjectContextProvider, etc.)

---

## Dependencies

- Story 0001 (ProjectContextProvider) — complétée
- Aucune nouvelle dépendance externe
- Aucun changement de schéma BDD
- Aucune nouvelle entité JPA
