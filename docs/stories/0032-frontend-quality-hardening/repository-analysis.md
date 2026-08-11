# Repository Analysis — Story 0032 (Frontend Quality Hardening)

## Understanding

Story 0032 durcit le socle qualité du frontend Angular avant d'ajouter de nouvelles fonctionnalités :
activer le strict mode TypeScript, ajouter ESLint, câbler le Prettier déjà installé (scripts + CI),
et rendre la couverture unitaire **bloquante** en CI. Le but est un socle raisonnable et
maintenable, pas une sur-configuration.

## Repository Overview (frontend)

- **Framework** : Angular 22 (standalone), TypeScript `~6.0.2`.
- **Tests unitaires** : Vitest via `@angular/build:unit-test` (37 `.spec.ts` / 159 tests).
- **Tests e2e** : Playwright (2 tests), CI = smoke statique (`PLAYWRIGHT_SERVE`).
- **Build** : `@angular/build:application`, sortie `dist/frontend`.
- **CI** : `.github/workflows/quality.yml` — 3 jobs (`quality` backend, `frontend`, `frontend-e2e`).

## État actuel des contrôles (vérifié dans le repo)

### TypeScript strict mode
`frontend/tsconfig.json` (racine) :
```json
"compilerOptions": {
  "noImplicitOverride": true,
  "noPropertyAccessFromIndexSignature": true,
  "noImplicitReturns": true,
  "noFallthroughCasesInSwitch": true,
  "skipLibCheck": true,
  "isolatedModules": true,
  ...
}
"angularCompilerOptions": {
  "enableI18nLegacyMessageIdFormat": false,
  "strictInjectionParameters": true,
  "strictInputAccessModifiers": true
}
```
- **`strict: true` ABSENT** (confirmé : `grep '"strict"' frontend/tsconfig*.json` → rien).
- Conséquences : `strictNullChecks`, `noImplicitAny`, `strictFunctionTypes` désactivés.
- `tsconfig.app.json` étend `tsconfig.json` (include `src/**/*.ts`, exclut `*.spec.ts`).
- `tsconfig.spec.json` (séparé) compile les specs.

### ESLint
- **Aucune config ESLint** (`eslint.config.*` / `.eslintrc*` absents) : `ls frontend | grep eslint` → rien.
- **Aucun script `lint`** dans `package.json`.

### Prettier
- `prettier@^3.8.1` **déjà en devDependencies** (installé mais non utilisé).
- `frontend/.prettierrc` **existe** : `{ printWidth: 100, singleQuote: true, overrides: [.html → parser angular] }`.
- `frontend/.editorconfig` **existe** : 2 espaces, quotes simples, newline finale.
- **Aucun script** `format` / `format:check` ; rien en CI.

### Coverage / CI
- Le job `frontend` de `quality.yml` exécute `ng test --coverage --watch=false`, `ng build`, affiche un
  résumé de couverture (Statements/Functions/Branches/Lines) — **non bloquant**.
- Coverage mesuré à ce jour : Lines ≈ 81 %, Statements ≈ 78 %, Branches ≈ 75 %, Functions ≈ 76 %.

## Changements nécessaires

| # | Changement | Fichiers |
|---|---|---|
| 1 | `strict: true` (racine) + résolution des erreurs strict | `tsconfig.json` + plusieurs `src/**/*.ts` |
| 2 | ESLint flat config + devDeps (`eslint`, `@typescript-eslint/*`, `@angular-eslint/*`) | `eslint.config.js`, `package.json` |
| 3 | Scripts `lint`, `lint:fix`, `format`, `format:check` | `package.json` |
| 4 | CI : `lint`, `format:check`, gate coverage bloquant | `quality.yml` |
| 5 | Reformattage Prettier (diff one-off) | fichiers frontend |

## Impact estimé

- **Erreurs strict mode** : à quantifier à l'implémentation (potentiellement plusieurs dizaines de
  corrections type `strictNullChecks` / `noImplicitAny`). Aucun changement de comportement.
- **Diff Prettier** : reformatage global une fois ; risque de "noise" dans le diff du commit.
- **Aucun impact backend** ; jobs `quality` et `frontend-e2e` inchangés.

## Contraintes

- Gate coverage avec marge sous les valeurs actuelles (proposition : `Lines ≥ 75 %`) pour éviter la
  flakiness tout en bloquant les régressions nettes.
- ESLint recommended (TypeScript + Angular), pas de plugins exotiques.
- Checks only : aucun changement de comportement runtime.

## Risques

1. **Migration strict disruptive** — Mitigation : corriger par fichiers, s'appuyer sur les 159 tests
   unitaires pour détecter toute dérive de comportement.
2. **Gate coverage flaky** — Mitigation : seuil avec marge (75 % lines).
3. **Diff de formatage large** — Mitigation : appliquer `prettier --write` dans le même commit que
   l'ajout du script, documenté.

## Recommandation

Prêt pour le plan d'implémentation. Les 4 axes (strict, ESLint, Prettier, gate coverage) sont isolés
et de risque faible à moyen, sans changement de contrat API ni de CI backend.