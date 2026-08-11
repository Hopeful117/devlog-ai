# Story 0033: Project State v2 — Recent Knowledge & Recent Evolution — Implementation Report

## Overview

Enrichi la projection déterministe `ProjectState` de deux nouvelles sections top-level —
`recentKnowledge` (KnowledgeEvents) et `recentEvolution` (EngineeringEvents validés) — exclusivement
à partir de données déjà persistées, sans LLM, sans nouvelle persistance, en réutilisant les
repos existants. Les 5 sections existantes restent inchangées (rétro-compatible).

## Modified Files

### Backend
- `projectstate/dto/response/ProjectStateResponse.java` — ajout des champs `recentKnowledge` et
  `recentEvolution` (record passe de 7 à 9 composants).
- `projectstate/mapper/ProjectStateMapper.java` — `toResponse` gagne 2 paramètres ; ajout de
  `toKnowledgeSummary`, `toKnowledgeSummaries`, `toRecentKnowledgeSection`, `toEvolutionSummary`,
  `toEvolutionSummaries`, `toRecentEvolutionSection`.
- `projectstate/service/ProjectStateProjectionServiceImpl.java` — injection des 2 repos
  (`KnowledgeEventRepository`, `EngineeringEventRepository`) ; nouvelles méthodes privées
  `buildRecentKnowledge` (borne à 5 via `subList`) et `buildRecentEvolution`
  (`PageRequest.of(0, 5)`) ; passage des 2 sections à `toResponse`.

### Backend (tests)
- `projectstate/service/ProjectStateProjectionServiceTest.java` — 2 repos mockés ; cas populated et
  empty étendus aux nouvelles sections + `verify(mapper).toRecentKnowledgeSection/toRecentEvolutionSection`.
- `projectstate/controller/ProjectStateControllerWebMvcTest.java` — réponse enrichie + assertions
  JSON `$.recentKnowledge.recentKnowledge` / `$.recentEvolution.recentEvolution` (isArray).
- `projectstate/mapper/ProjectStateMapperTest.java` — ajout de 4 tests de mapping champ-à-champ et
  vide-→-vide pour `KnowledgeSummary` et `EvolutionSummary`.

### Frontend
- `features/project-state/project-state.models.ts` — nouveaux types `KnowledgeEventType`,
  `EngineeringEventCategory`, `KnowledgeSummary`, `EvolutionSummary`, `RecentKnowledgeSection`,
  `RecentEvolutionSection` ; extension de `ProjectState`.
- `features/project-state/project-state-page.html` — 2 nouveaux panneaux (« What have we learned
  recently? » / « What recently changed? ») réutilisant `.panel`, `.status-badge`, `.empty-state`.
- `features/project-state/project-state-page.spec.ts` — fixtures enrichies + 3 nouveaux tests.
- `features/project-state/project-state.service.spec.ts` — fixture `ProjectState` enrichie
  (rétro-compat TypeScript).

## New Files

- `projectstate/dto/inner/KnowledgeSummary.java` — record `(UUID id, KnowledgeEventType type, String title, Instant createdAt)`.
- `projectstate/dto/inner/EvolutionSummary.java` — record `(UUID id, EngineeringEventCategory category, String title, String baseCommit, String targetCommit, Instant occurredAt)`.
- `projectstate/dto/response/RecentKnowledgeSection.java` — `(List<KnowledgeSummary> recentKnowledge)`.
- `projectstate/dto/response/RecentEvolutionSection.java` — `(List<EvolutionSummary> recentEvolution)`.

## Tests

- **Backend** : suite complète verte (exit 0) — service (populated/empty), controller (JSON des
  nouvelles sections), mapper (champ-à-champ de `KnowledgeSummary`/`EvolutionSummary`).
- **Frontend unit** : **37 fichiers / 162 tests** verts (159 → 162, +3 cas des nouvelles sections).
- **Frontend e2e** : **2 tests** Playwright verts (stack Docker, correction de régression).
- **Lint/format/build** : `npm run lint` 0 problème ; `format:check` propre ; `ng build` strict OK.

## Validation

```
cd backend && ./mvnw test                           → exit 0 (suite complète)
cd frontend && npm run lint                         → 0 problems
cd frontend && npm run format:check                 → All matched files use Prettier code style
cd frontend && npm run build                        → OK (strict)
cd frontend && npm test -- --watch=false            → 37 files / 162 tests passed
cd frontend && ng test --coverage --watch=false     → Lines ≈ 80.7 % (>= 75 %)
cd frontend && npm run e2e                          → 2 passed
```

## Deviations

- **Mapper MapStruct / collision de noms** : le record `RecentKnowledgeSection` possède un
  composant nommé exactement comme le paramètre/section. MapStruct tentait alors de mapper le
  composant `List<KnowledgeSummary> recentKnowledge` vers la section. Résolu par des noms de
  paramètres distincts (`recentKnowledgeSection`/`recentEvolutionSection`) + `@Mapping(target,
  source)` explicites. Déviation mineure par rapport au plan (noms de paramètres internes).
- **Tests mapper ajoutés** : au-delà du plan (service + controller), ajout de cas réels de mapping
  dans `ProjectStateMapperTest` pour verrouiller les champs exposés (AC-2/AC-3).

## Remaining Work

- Dashboard global (agrégation multi-projets) et heuristiques de blocage — hors scope, explicités.
- Aucun changement CI requis (couvert par les jobs existants).

## Recommendation

Ready for Review. Les deux sections sont exposées, bornées à 5, avec lists vides (non null) sur
projet vide, rétro-compatibles, sans LLM ni migration ni N+1. Tests backend et frontend verts.