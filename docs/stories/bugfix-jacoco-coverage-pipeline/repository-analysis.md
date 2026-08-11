# Repository Analysis — Bugfix: JaCoCo Coverage Pipeline Failure

## Story

**ID**: bugfix-jacoco-coverage-pipeline
**Problem**: CI/CD pipeline systematically fails due to JaCoCo coverage threshold check (79.8% vs 80% threshold)
**Scope**: pom.xml configuration change + optional test coverage improvement

---

## Repository Structure

- **Build system**: Maven (pom.xml at `backend/pom.xml`)
- **Language**: Java 21, Spring Boot 4.1.0
- **Coverage tool**: JaCoCo 0.8.14
- **Code generation**: MapStruct 1.6.3 (generates `*MapperImpl` classes)
- **SonarQube integration**: Yes, configured via `sonar-maven-plugin`

---

## Root Cause Analysis

### The Problem

The JaCoCo `check` goal enforces a minimum line coverage of 80% (`COVEREDRATIO 0.80` on `BUNDLE` element). Current coverage is 79.8% (5391/6753 lines covered).

### Why Coverage Is Below Threshold

MapStruct generates `*MapperImpl` classes at compile time. These classes:
- Are auto-generated, not hand-written code
- Have very low test coverage (1-3%) because they're tested indirectly through integration tests
- Account for 763 lines of code (11.3% of total codebase)
- Artificially deflate the coverage metric

### Evidence

From `target/site/jacoco/jacoco.csv`:
- Total lines: 6753
- Covered: 5391 (79.8%)
- Missed: 1362

Excluding `*MapperImpl`:
- Adjusted total: 5990 lines
- Adjusted covered: 5373 (89.7%)
- Adjusted missed: 617

The gap between 79.8% and 89.7% is entirely attributable to generated code.

---

## Affected Files

| File | Impact |
|------|--------|
| `backend/pom.xml` | JaCoCo plugin configuration — needs `excludes` for `*MapperImpl.class` |

---

## Risks

1. **Low risk**: Excluding generated code from coverage is standard practice for MapStruct projects
2. **No behavioral change**: Only affects CI/CD coverage reporting, not runtime behavior
3. **No test changes needed**: The fix is configuration-only

---

## Constraints

- Must not change the 80% threshold — only the excluded classes
- Must not affect SonarQube analysis (separate tool)
- Must maintain all existing tests (533 passing)

---

## Recommendation

**Option A (Recommended)**: Exclude `**/*MapperImpl.class` from the JaCoCo `check` goal's scope via `<excludes>` configuration. This is the standard, documented approach for excluding generated code from coverage checks.

The `check` goal supports `<excludes>` at the goal level (not inside `<rules>`), which was the source of earlier configuration errors.
