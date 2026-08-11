# Implementation Plan — Story 31: Frontend Test Coverage & Bug Fixes

## Goal

Improve frontend test coverage and fix known bugs by:
1. Implementing tests for critical components and services
2. Fixing the "refresh understanding" bug in `project-understanding-section`
3. Setting up Playwright for end-to-end testing

## Scope

### In Scope
- Add unit tests for critical services and components
- Fix the "refresh understanding" bug
- Create Playwright configuration and initial tests
- Document the testing strategy

### Out of Scope
- Complete all missing tests (will be future stories)
- Major refactoring of frontend code
- Backend changes

## Approach

### Phase 1: Critical Tests (Weeks 1-2)
1. Add tests for `request-error.ts` and `toRequestError` function
2. Add unit tests for `ProjectUnderstandingService` methods
3. Add tests for `project-state.service` methods
4. Add tests for `insight.service` and `insight-proposal.service`

### Phase 2: Bug Fix (Week 2)
1. Investigate `project-understanding-section.component.html` and `.ts`
2. Identify why "refresh understanding" button doesn't work
3. Implement fix and add unit tests
4. Verify fix works in manual testing

### Phase 3: e2e Setup (Week 3)
1. Install Playwright
2. Configure Playwright for Angular
3. Create initial e2e tests for critical user flows:
   - Project understanding workflow
   - Dashboard navigation
   - Project-state overview

## Tasks

### Tasks
- [x] Add tests for `request-error.ts` (1 day)
- [x] Add unit tests for `ProjectUnderstandingService` (2 days)
- [x] Add tests for `project-state.service` (1 day)
- [x] Implement fix for "refresh understanding" bug (3 days) — *root cause backend*: `LazyInitializationException` on `ProjectCommit.changedFiles`; fixed via `@EntityGraph` on the collector query
- [x] Add unit tests for bug fix (1 day) — integration test `CommitChangedFilesEagerFetchIntegrationTest`
- [x] Install and configure Playwright (2 days)
- [x] Create initial e2e tests (2 days)
- [x] Update documentation (1 day)

## Success Metrics

- [x] All critical services have ≥ 2 unit tests
- [x] "Refresh understanding" bug is fixed and tested
- [x] Playwright is configured and initial tests pass
- [x] All new tests pass with 0 failures
- [x] Documentation updated with testing strategy

## Testing Strategy (frontend)

- **Unit** : Vitest via `@angular/build:unit-test` — `npm test` (`ng test`)
- **E2E** : Playwright (Chromium) — `npm run e2e` (`npx playwright test`)
- **E2E target** : le stack Docker tourne sur `http://localhost:18083` (nginx sert la SPA et proxy `/api` vers le backend `:18080`)
- **Config** : `frontend/playwright.config.ts` (baseURL `:18083`, testDir `./tests`, reporter HTML)
- **Setup** : `npm i -D @playwright/test` puis `npx playwright install chromium`
- **Rapport** : `npm run e2e:report` (ouvre `playwright-report/`)
- **Browsers** : binaire Chromium sous `~/.cache/ms-playwright` ; si libs système manquantes au premier run, exécuter `sudo npx playwright install-deps chromium`

## Dependencies

- DevLog backend running (already verified)
- Git access to frontend repository
- Node.js 18+ and npm installed

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Bug "refresh understanding" is complex | Start with detailed debugging before implementing |
| e2e setup complexity | Start with simple Playwright config, add tests incrementally |
| Test flakiness | Use Playwright's auto-wait features and proper test isolation |

## Deliverables

- `docs/stories/0031-frontend-test-coverage/implementation-plan.md`
- Updated `package.json` with Playwright dependencies
- New test files in `src/app/**/*.spec.ts`
- Updated documentation in `README.md` or `docs/`

## Timeline

- **Week 1**: Critical tests (60%)
- **Week 2**: Bug fix + e2e setup (40%)
- **Week 3**: Final testing and documentation

## Review Criteria

- Tests must be deterministic and reliable
- Bug fix must have unit tests covering the fix
- Playwright setup must be reproducible
- Documentation must be clear and actionable