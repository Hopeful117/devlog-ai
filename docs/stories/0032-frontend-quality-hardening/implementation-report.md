# Story 0032: Frontend Quality Hardening — Implementation Report

## Overview

Finalisé le socle qualité du frontend : TypeScript strict complet activé, ESLint installé et câblé,
Prettier (déjà présent) intégré aux scripts et à la CI, et la couverture devient un **gate bloquant**
traduit par un échec du job. Aucun changement de comportement runtime : toutes les étapes (strict,
lint, format, gate) sont des vérifications seules.

## Modified Files

- `frontend/tsconfig.json` — activation de `"strict": true` (hérité par `tsconfig.app.json` /
  `tsconfig.spec.json`). Aucun correctif de type nécessaire : le code compilait déjà en strict.
- `frontend/package.json` — ajout des devDeps ESLint/Angular-ESLint et des scripts `lint` /
  `lint:fix` / `format` / `format:check`.
- `frontend/package-lock.json` — conséquence de l'installation des nouvelles devDeps.
- `frontend/.gitignore` — (inchangé) ; un nouveau `.prettierignore` exclut les artefacts.
- `frontend/src/app/features/analyses/analysis-detail-page.ts` — suppression de 3 imports inutilisés
  (`CollectionWarning`, `JsonValue`, `ProjectProfile`) levés par `@typescript-eslint/no-unused-vars`.
- ~26 fichiers `src/**` et `tests/**` reformatés en one-shot par Prettier (diff unique, aucun
  changement sémantique).
- `.github/workflows/quality.yml` — job `frontend` : ajout des étapes `npm run lint` et
  `npm run format:check`, et gate de couverture bloquant (`Lines < 75%` → échec du job).

## New Files

- `frontend/eslint.config.js` — config ESLint **flat** (ESLint 10) : règles recommandées
  TypeScript + Angular (templates inclus via `angular.configs.templateRecommended` /
  `templateAccessibility`), `eslint-config-prettier` pour neutraliser les conflits de format, et
  un bloc dédié CommonJS pour l'outillage Playwright (`tests/**`, `playwright.config.ts`).
- `frontend/.prettierignore` — exclut `node_modules`, `dist`, `coverage`, `playwright-report`,
  `test-results`, `.angular`, `out-tsc`, `package-lock.json`.

## Tests

Étapes de validation exécutées localement :

- **Strict TS** : `tsc -p tsconfig.app.json --noEmit` et `tsc -p tsconfig.spec.json --noEmit` → **0 erreur** ; `ng build` (strict) → **0 erreur** (bundle généré).
- **ESLint** : `npm run lint` → **0 problème** sur `src/` + templates + outillage.
- **Prettier** : `npm run format:check` → **tout conforme** ; `npm run format` → reformate sans erreur.
- **Unit** : `ng test --watch=false` → **37 fichiers / 159 tests verts**.
- **Couverture** : `ng test --coverage --watch=false` → **Lines ≈ 78.5 %** (80.6 % via JSON brut) — au-dessus du plancher de 75 %.
- **E2E** : `npm run e2e` (stack Docker `:18083`) → **2 tests verts**.
- **Backend** : `./mvnw test` → **exit 0** (aucune régression, aucune modification backend).

## Validation

```
cd frontend && tsc -p tsconfig.app.json --noEmit    → 0 errors (strict)
cd frontend && tsc -p tsconfig.spec.json --noEmit   → 0 errors (strict)
cd frontend && npm run build                        → OK (strict)
cd frontend && npm run lint                         → 0 problems
cd frontend && npm run format:check                 → All files use Prettier code style
cd frontend && npm test -- --watch=false            → 37 files / 159 tests passed
cd frontend && npm run e2e                          → 2 passed
cd backend && ./mvnw test                           → exit 0
```

## Deviations

- **Plancher de couverture fixé à `Lines ≥ 75 %`** (choix documenté dans story.md). La valeur
  mesurée actuelle (≈ 78.5–81 %) laisse de la marge (24h non flaky) tout en gardant le gate bloquant.
- **`@typescript-eslint/no-empty-object-type` désactivé** : le modèle utilise des types d'enveloppe
  de payload vides (discriminated-union) que cette règle flaggue sans valeur réelle. Choix assumé,
  documenté dans `eslint.config.js`.
- **`@typescript-eslint/no-require-imports` désactivé** pour `tests/**` et `playwright.config.ts`
  (outillage CommonJS, hors application Angular).
- **DevDeps installées** : `eslint@10.8.1`, `@eslint/js@10.0.1`, `typescript-eslint@8.67.0`,
  `@angular-eslint/*@22.1.0`, `angular-eslint@22.1.0`, `eslint-config-prettier@10.1.8` — versions
  alignées avec Angular 22 / ESLint flat.

## Remaining Work

- (source) Couverture des templates `.html` volumineux encore à compléter (hors scope, réutilisé
  de 0031).
- e2e full-workflow en CI (dépendant de la stack Docker) — hors scope de cette story, documenté.

## Recommendation

Ready for Review. Socle qualité opérationnel et vérifié (strict + lint + format + gate de
couverture bloquant), aucun comportement runtime modifié, aucun test existant cassé.