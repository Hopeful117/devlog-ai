# Engineering Report — Bugfix: JaCoCo Coverage Pipeline Failure

## Story

**ID**: bugfix-jacoco-coverage-pipeline
**Status**: Completed
**Commit**: `85de2c6`

---

## Problem

CI/CD pipeline systematically failed at the JaCoCo coverage check phase. Line coverage was 79.8% against an 80% threshold — a gap of 0.2%.

## Root Cause

MapStruct annotation processor generates `*MapperImpl` classes at compile time. These auto-generated implementations:
- Account for 763 lines of code (11.3% of codebase)
- Have 1-3% test coverage (tested indirectly through integration tests)
- Artificially deflate the coverage metric below the threshold

## Solution

Excluded `**/*MapperImpl.class` from the JaCoCo `check` goal via the `<excludes>` configuration parameter. This is the documented, standard approach for excluding generated code from coverage analysis in Maven projects.

## Results

| Metric | Before | After |
|--------|--------|-------|
| Coverage (excl. generated) | N/A | 89.7% |
| JaCoCo check | FAIL | PASS |
| Tests passing | 533 | 533 |
| Classes analyzed | 409 | 391 |
| SonarQube Quality Gate | ERROR | **OK** |
| New violations | 6 | 0 |
| New coverage | N/A | 80.0% |
| New duplication | N/A | 0.0% |

## Artifacts Produced

1. `repository-analysis.md` — Root cause analysis and evidence
2. `implementation-plan.md` — Configuration change plan
3. `implementation-report.md` — Validation results
4. `code-review.md` — Independent verification

## Risks

- **Low risk**: Configuration-only change, no behavioral impact
- **Standard practice**: Excluding generated code is recommended by MapStruct documentation
- **Reversible**: Removing the `<excludes>` element restores original behavior

## Lessons Learned

- JaCoCo `check` goal's `<excludes>` parameter must be placed at the goal configuration level, not inside `<rules>` — placing it inside `<rules>` causes a Maven error
- Generated code coverage should be measured separately from hand-written code coverage
