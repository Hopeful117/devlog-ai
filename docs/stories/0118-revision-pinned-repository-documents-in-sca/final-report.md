# Story 0118 — Final Report

## Commit
- **SHA**: `db50dfb`
- **Branch**: `story/0118-revision-pinned-repository-documents-in-sca`
- **Message**: `feat(0118): revision-pinned repository documents in SCA`

## Governance
- **Authorizing Story**: Story 0118 (DESIGN CONSOLIDATED)
- **Governing ADRs**: ADR-063 (§28.1, §41, §42)
- **Commit created**: YES
- **Push performed**: NO (awaiting human authorization)

## Implementation Summary
Implemented revision-pinned, bounded repository document collection for Story Context Analysis. The system deterministically reads ADR and story document bodies at a resolved revision, applies configurable budget limits, parses ADR status, extracts one-hop references, and produces `HUMAN_AUTHORED` trust-tier evidence with canonical `document:{sourceId}:{path}@{revision}` references.

## Files Changed (31 files, +2461/-34)

### Core Types
| File | Description |
|------|-------------|
| `RepositoryRevisionScope.java` | Immutable record for revision scope (sourceId, revision, workspacePath, strategy) |
| `DocumentReference.java` | Canonical document identity with `toEvidenceReference()` |
| `DocumentStatus.java` | Enum: ACCEPTED, PROPOSED, SUPERSEDED, REJECTED, UNKNOWN |
| `AdrStatusParser.java` | Deterministic parser for ADR `## Status` section |
| `DocumentReferenceExtractor.java` | Markdown reference extractor (ADR numbers, story numbers, roadmap) |
| `DocumentBudgetPolicy.java` | Spring `@Component` with configurable limits |
| `DocumentBodyCollector.java` | Main collector: workspace resolution, content reading, ADR parsing, one-hop traversal |

### Infrastructure Wiring
| File | Change |
|------|--------|
| `ContextRequest.java` | Added `revisionScope` field |
| `RepositoryContextService.java` | New `build(...)` overload with scope |
| `RepositoryContextEngine.java` | Implements scope-aware build |
| `KnowledgeSelectionService.java` | New `select(...)` overload with scope |
| `KnowledgeSelectionServiceImpl.java` | Forwards scope to build |
| `AnalyzeStoryContextUseCase.java` | Resolves scope, passes to selection |
| `application.properties` | Document budget properties |

### Tests (6 new files, 6 modified)
| File | Tests |
|------|-------|
| `AdrStatusParserTest.java` | 9 tests |
| `DocumentReferenceExtractorTest.java` | 8 tests |
| `DocumentBudgetPolicyTest.java` | 5 tests |
| `DocumentReferenceTest.java` | 9 tests |
| `RepositoryRevisionScopeTest.java` | 9 tests |
| `DocumentBodyCollectorTest.java` | 3 tests |
| `DocumentBodyCollectorIntegrationTest.java` | 6 tests |

### Evaluation
| File | Change |
|------|--------|
| `scenario.json` | Added document evidence item with body text |
| `replay.json` | Updated promptContentDigest |

## Verification
- **Backend tests**: 1176/1176 PASS (BUILD SUCCESS)
- **AI Engine tests**: All PASS
- **ADR-066 evaluation**: Gate PASSED (STRONG)
- **JaCoCo coverage**: All checks met
- **git diff --check**: Clean

## Key Design Decisions
1. **Java deterministic authority**: ADR status parsing, revision resolution, budget enforcement all stay Java-side
2. **Python probabilistic boundary**: Document body text reaches Python via `content.text`; ADR status stays Java-side
3. **One-hop traversal**: Collector reads current story, extracts references, then collects referenced ADRs/stories
4. **Canonical identity**: `document:{sourceId}:{path}@{revision}` ensures revision-pinned grounding
5. **Trust tier**: Document evidence classified as `HUMAN_AUTHORED` (not `TECHNICAL_EVIDENCE`)

## Known Limitations
- No automatic roadmap inclusion (requires explicit reference in story content)
- ADR status parsing limited to `## Status` heading (no full Markdown AST)
- Budget enforcement is per-document, not cross-document

## Readiness
**READY_FOR_HUMAN_REVIEW**

## Governance Checklist
- [x] Java/Core deterministic authority preserved
- [x] Python probabilistic boundary respected
- [x] EngineeringContext sole authority
- [x] Grounding mandatory (evidence references required)
- [x] Trust boundaries (persisted != trusted, confidence != evidence)
- [x] Human gates respected (no self-authorized merge/push)
- [x] Scope discipline (changes limited to Story 0118)
