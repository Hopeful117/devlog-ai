# Implementation Plan — Story 0032 (Frontend Quality Hardening)

## Goal

Finaliser le socle qualité frontend avant d'ajouter des fonctionnalités : `strict` TypeScript,
ESLint, Prettier câblé, gate de couverture bloquant en CI. Socle raisonnable et maintenable.

## Approach

### Step 1 — TypeScript strict mode
1. Ajouter `"strict": true` dans `frontend/tsconfig.json`.
2. Corriger les erreurs de compilation strict (type-safe null, no implicit any) dans `src/**/*.ts`.
3. Vérifier que `ng build` et `ng test` compilent en strict.

### Step 2 — ESLint
4. Ajouter devDeps : `eslint`, `@typescript-eslint/parser`, `@typescript-eslint/eslint-plugin`,
   `@angular-eslint/*`, `eslint-config-prettier` (pour ne pas entrer en conflit avec Prettier).
5. Créer `frontend/eslint.config.js` (flat config) : TS recommended + Angular recommended
   (templates inclus) + désactivation des règles de formatage (délégation à Prettier).
6. Ajouter scripts `lint` (`eslint .`) et `lint:fix`.

### Step 3 — Prettier
7. Conserver `.prettierrc` existant.
8. Ajouter scripts `format` (`prettier --write .`) et `format:check` (`prettier --check .`).
9. Appliquer `npm run format` une fois (diff one-off).

### Step 4 — CI (`quality.yml`, job `frontend`)
10. Ajouter `npm run lint` et `npm run format:check`.
11. Transformer le résumé de couverture en **gate bloquant** : échec si `Lines < 75%`.

### Step 5 — Vérification finale
12. `ng test --coverage --watch=false` : 37 specs / 159 tests verts, Lines ≥ 75 %.
13. `ng build` strict : 0 erreur.
14. `npm run lint` / `npm run format:check` : 0 erreur.
15. e2e (local + CI smoke) : verts.

## Gate de couverture (proposition)

- `Lines ≥ 75 %` (valeur actuelle ≈ 81 %) — marge contre la flakiness, blocage des régressions nettes.
- Implémentation du gate : parse `coverage/**/coverage-final.json` (déjà scripté) ; `exit 1` si la
  valeur est sous le seuil ; géré en step séparé après les tests unitaires.

## Files Affected

- `frontend/tsconfig.json`
- `frontend/package.json`, `frontend/package-lock.json`
- `frontend/eslint.config.js` (nouveau)
- Plusieurs `frontend/src/**/*.ts` (correctifs strict/lint)
- Fichiers reformattés par Prettier (one-off)
- `.github/workflows/quality.yml` (job `frontend`)

## Testing Strategy

- Unit : Vitest (inchangé, doit rester vert).
- Build : `ng build` strict.
- Lint/format : scripts npm.
- E2E : inchangé (2 tests).

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Migration strict disruptive | Corriger par fichiers, 159 tests unitaires pour détecter un drift |
| Gate coverage flaky | Seuil 75 % avec marge sous le 81 % actuel |
| Diff Prettier large | Appliquer en one-off dans le même commit, documenté |

## Success Criteria

- [ ] `strict: true` actif, build + tests compilent en strict (0 erreur).
- [ ] `eslint .` passe (0 erreur) ; config flat TS+Angular.
- [ ] `prettier --check .` passe ; scripts `format`/`format:check`.
- [ ] CI job `frontend` : lint + format:check + gate coverage bloquant.
- [ ] 37 specs / 159 tests verts ; Lines ≥ 75 %.
- [ ] e2e verts. Aucun changement de comportement runtime.