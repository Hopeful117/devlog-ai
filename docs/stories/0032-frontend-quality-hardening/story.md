# Story 0032 — Frontend Quality Hardening

## Status

Approved

## Priority

High

## Objective

Finalize the frontend quality baseline so the growing Angular application is checked automatically and consistently, before adding further features. Enable full TypeScript strict mode, add ESLint, wire the already-installed Prettier into scripts and CI, and make the coverage metric a **blocking** gate instead of a report.

## Motivation

The frontend is large and growing (37 `.spec.ts` / 159 unit tests). The recent quality work (story 0031) added unit coverage, Playwright e2e and CI jobs, but the following gaps remain and will slow down future features and let regressions slip through:

- **No full TypeScript strict mode**: `tsconfig.json` does not set `strict: true`; only some Angular compiler options are enabled (`strictInjectionParameters`, `strictInputAccessModifiers`). `strictNullChecks`, `noImplicitAny` and `strictFunctionTypes` are off.
- **No ESLint**: no `eslint` config, no `lint` script, so unused imports, dead branches and style/dogma violations are undetected.
- **Prettier installed but unused**: `prettier@^3.8.1` and `.prettierrc` exist, but there is no `format`/`format:check` npm script and no enforcement anywhere.
- **Coverage is reported, not gated**: the CI `frontend` job prints a coverage summary but never fails on a low value, so coverage can regress silently.
- **e2e in CI is a smoke test only** (runs against the static build, backend-dependent flows excluded). This is documented as remaining work from 0031.

This story brings the frontend to a reasonable, maintainable baseline — **not** an over-engineered setup.

## Scope

### In Scope

1. **TypeScript strict mode**
   - Enable `strict: true` (and keep existing flags) in `frontend/tsconfig.json`, propagated to `tsconfig.app.json` / `tsconfig.spec.json`.
   - Resolve all strict-mode compile errors in `src/` (type-safe null handling, no implicit any).
   - The production build (`ng build`) and unit test compilation must pass with strict enabled.

2. **ESLint**
   - Add `eslint`, `@typescript-eslint/*` and `@angular-eslint/*` as dev dependencies.
   - Add a flat config `eslint.config.js` with sensible recommended rules for TypeScript + Angular (templates included).
   - Add `lint` / `lint:fix` npm scripts.

3. **Prettier**
   - Keep the existing `.prettierrc` (printWidth 100, single quotes, angular HTML parser).
   - Add `format` / `format:check` npm scripts.

4. **CI integration** (`quality.yml`)
   - `frontend` job: run `npm run lint`, `npm run format:check`, and the strict build.
   - **Blocking coverage gate**: after unit tests, fail the job if `Lines` coverage is below a chosen floor.

5. **Unit / e2e**
   - All 37 spec files / 159 tests still pass after the strict/ESLint migration.
   - e2e stays as-is (2 tests) — extending the CI e2e to the full workflow is explicitly out of scope here (see Out of Scope).

### Out of Scope

- Full workflow e2e in CI (postgres + backend + ai-engine on the runner) — future story.
- Restyling or refactoring of components beyond what strict mode / lint errors require.
- Setting up a pre-commit hook / husky.
- Frontend coverage of templates to arbitrary high values.
- Any backend change.

## Constraints

- Baseline quality that is **reasonable and maintainable** — no dependency on heavyweight configs (e.g., no full `ts-essentials`, no custom ESLint plugins beyond Angular/TypeScript recommended).
- No behavior change: strict/lint/format are checks only; they must not alter runtime behavior.
- Keep the existing `.prettierrc` and `.editorconfig` (already coherent: 2-space, single quotes, final newline).
- The coverage floor must be set **below the current measured value** (Lines ≈ 81%, Statements ≈ 78%, Branches ≈ 75%, Functions ≈ 76%) so the gate is not flaky but still guards against regressions.

## Impact

- **Backend**: none.
- **Frontend**:
  - `frontend/tsconfig.json` (+ app/spec) — `strict: true`.
  - New `frontend/eslint.config.js`.
  - `frontend/package.json` — new devDeps + `lint`/`lint:fix`/`format`/`format:check` scripts.
  - Several `src/**/*.ts` files fixed to satisfy strict / lint (type-safe nulls, unused imports).
  - Some files reformatted by Prettier (one-time diff).
- **CI**: `quality.yml` frontend job gains `lint`, `format:check` steps and a failing coverage gate.
- **Tests**: unit + e2e unchanged in count (still green).

## Acceptance Criteria

### TypeScript strict
- AC-1: `frontend/tsconfig.json` sets `strict: true`.
- AC-2: `ng build` (production) compiles with strict mode, 0 errors.
- AC-3: `ng test` compiles the specs with strict mode, 0 errors.

### ESLint
- AC-4: `eslint.config.js` exists and lints `.ts` + Angular templates.
- AC-5: `npm run lint` passes with 0 errors on `src/`.

### Prettier
- AC-6: `npm run format:check` passes on `src/` (and other frontend files).
- AC-7: `npm run format` reformats without errors.

### CI
- AC-8: `quality.yml` `frontend` job runs `npm run lint` and `npm run format:check`.
- AC-9: `quality.yml` `frontend` job fails if unit coverage `Lines` is below the configured floor (gate, not just report).
- AC-10: All 3 CI jobs (`quality`, `frontend`, `frontend-e2e`) still pass on the PR.

### Regression safety
- AC-11: All existing frontend unit tests pass (37 files / 159 tests).
- AC-12: Existing e2e tests still pass (2 tests, local and CI smoke).
- AC-13: No runtime behavior change (checks only).

## Technical Context

Current `tsconfig.json` (verified):
```json
"compilerOptions": {
  "noImplicitOverride": true,
  "noPropertyAccessFromIndexSignature": true,
  "noImplicitReturns": true,
  "noFallthroughCasesInSwitch": true,
  ...
},
"angularCompilerOptions": {
  "enableI18nLegacyMessageIdFormat": false,
  "strictInjectionParameters": true,
  "strictInputAccessModifiers": true
}
```
No `strict: true`. `tsconfig.app.json` extends it.

Current frontend devDependencies (verified):
```json
"@angular/build": "^22.0.7", "@angular/cli": "^22.0.7", "@angular/compiler-cli": "^22.0.0",
"@playwright/test": "^1.62.1", "@vitest/coverage-v8": "^4.1.10", "jsdom": "^28.0.0",
"prettier": "^3.8.1", "typescript": "~6.0.2", "vitest": "^4.0.8"
```

Current scripts: `ng`, `start`, `build`, `watch`, `test`, `e2e`, `e2e:report` (no `lint`/`format`).

Current CI (`quality.yml`) `frontend` job: `npm ci` → `ng test --coverage --watch=false` → `ng build` → coverage summary (non-blocking) → upload.

## Dependencies

- Story 0031 (already introduced Vitest coverage, Playwright, CI jobs). This story builds on that baseline.
- No backend dependency.

## Risks

1. **Strict-mode migration churn** — many small type fixes across `src/`. Mitigation: split the change; keep runtime behavior identical; rely on the existing 159 unit tests to catch behavioral drift.
2. **Coverage gate flakiness** — if the floor is too close to the current value. Mitigation: set the floor with headroom (e.g., Lines ≥ 75%) and document the value.
3. **ESLint template rules** — Angular template linting may flag existing markup. Mitigation: use recommended rules, allow targeted disable comments only where genuinely needed.

## Artifacts (after approval)

- `repository-analysis.md`
- `implementation-plan.md`
- `implementation-report.md`
- `code-review.md`
- `engineering-report.md`
