# Story 31: Frontend Test Coverage & Bug Fixes

## Problem

Plusieurs fonctionnalités user-facing sont buggées en production (notamment "refresh understanding"). Le frontend n'a pas de tests e2e et le coverage de tests unitaires est incomplet.

## Scope

- Augmenter la couverture de tests unitaires côté frontend
- Investiguer et corriger les bugs connus
  - Refresh understanding (bouton ne fonctionne pas)
  - Overview page (number: null des stories)
- Identifier les besoins pour tests e2e (Playwright)

## Acceptance Criteria

- [x] Les composants critiques ont des tests unitaires couvrant les cas d'erreur
- [x] Les bugs identifiés sont corrigés
  - Refresh understanding → corrigé (cause racine backend : `LazyInitializationException`, fix `@EntityGraph`)
  - Overview page (number: null) → corrigé (fix MapStruct `storyNumber` → `number`)
  - Overview page illisible (CSS tokens legacy) → corrigé (`--color-*` → design system)
- [x] Tous les tests passent (backend 537, frontend unit 159, e2e 2)
- [x] Rapport d'analyse produit
- [x] Tests e2e Playwright configurés

## Repository Analysis

Voir `repository-analysis.md`

## Couverture finale (source frontend)

| Métrique | Avant | Après |
|----------|-------|-------|
| Statements | 74.67% | **78.41%** |
| Branches | 72.75% | **75.36%** |
| Functions | 70.0% | **75.77%** |
| Lines | 78.42% | **81.34%** |

- Fichiers de test : 37 `.spec.ts` / 159 tests.
- Composants critiques désormais couverts : `project-state-page` (`project-state.service`),
  `workspace-layout`, `insight.service` / `insight-proposal.service`, `dashboard-page`,
  `engineering-events*`, `engineering-event-detail-page`, `deliverable-detail-page`,
  `proposal-review-page`.
- `app.routes.ts` volontairement non instrumenté en unitaire : ses lambdas `loadComponent`
  feraient chuter artificiellement les métriques globales (cf. `repository-analysis.md`). Couvert
  par e2e.