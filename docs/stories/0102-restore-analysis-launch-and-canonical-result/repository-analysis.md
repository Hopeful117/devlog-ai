# Repository Analysis — Story 0102

## Story

**0102 — Restore Analysis Launch and Canonical Result Consultation**

Status entering this mission: `READY_FOR_REPOSITORY_ANALYSIS`.

This analysis is repository-analysis only. It introduces no production code, test, endpoint,
Angular change, migration, implementation plan, commit, or remote mutation.

## Human Repository Analysis Review

**HUMAN_REPOSITORY_ANALYSIS_REVIEW = PENDING**

The repository findings require human review before implementation planning proceeds.

## Repository State

| Item | Observed value |
|---|---|
| Branch | `p0-restore-analysis-launch-and-result` |
| HEAD | `885c446a3ecbaa566e49ea1d42b5c18e185672d3` |
| Worktrees | One: `/home/ludo/Bureau/workspace/devlog-ai` |
| Remote state | `main...origin/main` before the fetch attempt |
| Fetch | Attempted; failed because the SSH security-key agent refused signing |
| Worktree | Dirty before this mission |

Pre-existing changes include `frontend/src/app/features/analyses/project-analyses-section.html`
which was already modified before this mission began. The only files created by this mission are
this artifact and the other Story 0102 lifecycle documents.

## Governing Architecture

Analysis launch follows a generic V1 intent-driven contract:

```text
Human selects Intent
  → Angular emits CreateAnalysisRequest
  → POST /api/v1/analyses
  → Backend resolves IntentDefinition from catalog
  → Backend derives AnalysisType
  → Backend validates and persists Analysis
  → POST /api/v1/analyses/{id}/workflow
  → Workflow orchestrates execution pipeline
  → Analysis Result composed at query time
  → GET /api/v1/analyses/{id}/result
  → Angular renders canonical result
```

ADR-006 governs proposal lifecycle semantics. `ADR_REQUIRED = NO` for this Story.

## Regression A — Project Evolution Cannot Be Launched

### Observation

When `Project Evolution` is selected in the Analysis UI and the user clicks "Create and launch",
the request fails. The UI reports an error and no usable Analysis is produced.

### Story 0099 Authoritative Contract

Story 0099 (`docs/stories/0099-align-generic-analysis-launch-with-executable-intent-contracts/`)
established the authoritative generic-launch contract. Key decisions:

**1. AnalysisType is NOT human-selectable in generic launch:**

From `story.md` line 183:
> "Does AnalysisType belong in the human generic launch experience? NO."

**2. Generic launch is objective/Intent-driven:**

From `story.md` line 18-24:
> "Make generic Analysis launch coherent for a human engineer by presenting executable engineering
> objectives with objective-derived Project or repository scope instead of independent internal
> AnalysisType, Intent, and scope mechanics."

**3. All four generic V1 objectives derive ARCHITECTURE_REVIEW:**

From `story.md` lines 188-196:
> | Product objective | Intent | Derived AnalysisType | Fixed scope |
> |---|---|---|---|
> | Understand this project | `describe-project-v1` | `ARCHITECTURE_REVIEW` | `PROJECT_SCOPE` |
> | Prepare README information | `generate-readme-v1` | `ARCHITECTURE_REVIEW` | `REPOSITORY_SCOPE` |
> | Review the architecture | `architecture-overview-v1` | `ARCHITECTURE_REVIEW` | `PROJECT_SCOPE` |
> | Analyze engineering decisions | `analyze-engineering-decision-v1` | `ARCHITECTURE_REVIEW` | `PROJECT_SCOPE` |

**4. This derivation is documented as a V1 compatibility policy:**

From `story.md` lines 208-209:
> "This mapping is a V1 launch policy, not a claim that all future generic objectives are architecture
> reviews. Any future mapping change requires a separately reviewed product contract change."

**5. CreateAnalysisRequest.type is retained for compatibility/internal reasons:**

From `implementation-plan.md` line 38:
> "Existing `type` input becomes optional legacy metadata and must match the derived type when sent."

From `engineering-report.md` line 45:
> "`type` | required (ARCHITECTURE_REVIEW | PROJECT_EVOLUTION) | optional legacy; must match derived if present"

**6. PROJECT_EVOLUTION in AnalysisAiTaskTypeResolver represents the dedicated Engineering Event workflow:**

From `story.md` lines 204-205:
> "`PROJECT_EVOLUTION` remains the internal classification of the dedicated, commit-bounded
> Engineering Event workflow;"

From `story.md` lines 213-218:
> | Intent | Generic executable | Generic launcher V1 | Dedicated workflow |
> |---|---|---|---|
> | `analyze-engineering-event-v1` | **No** | **Exclude** | Engineering Event source+commit execution only |

**7. Story 0099 removed the AnalysisType selector from the frontend:**

From `implementation-plan.md` lines 61-62:
> "no editable AnalysisType"

From `engineering-report.md` line 17:
> "The legacy editable AnalysisType is removed from the Angular launch UI but preserved in persistence, diagnostics, and internal context behavior."

**8. Story 0100 reverted the Story 0099 frontend changes:**

Git history shows:
- Commit `39dab5b` (Story 0099): Replaced `type` form control with `objective` form control, removed AnalysisType selector
- Commit `a2f29c4` (Story 0100): Reverted frontend back to old form with `type` and `intentKey` controls

The diff between Story 0099 and Story 0100 shows:
```diff
-  @Input({ required: true }) objectives: readonly Objective[] = [];
-  @Input({ required: true }) sources: readonly Source[] = [];
+  @Input({ required: true }) intents: readonly IntentDefinition[] = [];
+  readonly types: readonly LaunchableAnalysisType[] = ['ARCHITECTURE_REVIEW', 'PROJECT_EVOLUTION'];
+  type: new FormControl<LaunchableAnalysisType>('ARCHITECTURE_REVIEW', {...}),
+  intentKey: new FormControl('', {...}),
```

### Root Cause

The root cause is NOT that the backend should accept `PROJECT_EVOLUTION`. The root cause is that
Story 0100 reverted the Story 0099 frontend changes, reintroducing the AnalysisType selector
that Story 0099 intentionally removed.

The backend correctly rejects `PROJECT_EVOLUTION` because:
1. Story 0099 established that all generic V1 objectives derive `ARCHITECTURE_REVIEW`
2. The backend's type consistency validation is correct per Story 0099 contract
3. The frontend violates the Story 0099 contract by exposing `PROJECT_EVOLUTION` as a choice

### Contract Mismatch (Corrected)

| Layer | Story 0099 Contract | Current State | Violation? |
|---|---|---|---|
| Frontend UI selector | Objective-based (no type selector) | Type selector with PROJECT_EVOLUTION | YES — Story 0100 regression |
| Frontend model | Objective/Intent-based | Type/Intent-based | YES — Story 0100 regression |
| Backend CreateAnalysisRequest.type | Optional legacy, must match derived | Optional legacy, must match derived | CORRECT |
| Backend AnalysisServiceImpl.create() | Hardcoded ARCHITECTURE_REVIEW derivation | Hardcoded ARCHITECTURE_REVIEW derivation | CORRECT |
| Backend AnalysisAiTaskTypeResolver | Allows PROJECT_EVOLUTION for dedicated workflow | Allows PROJECT_EVOLUTION for dedicated workflow | CORRECT |

### Test Gap

- `AnalysisControllerWebMvcTest`: tests launch with `ARCHITECTURE_REVIEW` only; no test for
  `PROJECT_EVOLUTION` rejection or acceptance
- `AnalysisServiceTest`: tests create with `ARCHITECTURE_REVIEW` only; no test for type derivation
  logic
- No test verifies that the frontend cannot produce an unsupported human-selected AnalysisType
- The frontend test (`analysis-form.spec.ts`) emits `type: 'ARCHITECTURE_REVIEW'` and never
  tests `PROJECT_EVOLUTION`

### Why Existing Tests Passed

The tests only exercised the `ARCHITECTURE_REVIEW` path. The `PROJECT_EVOLUTION` path was never
tested at the creation boundary. The form component test verifies form validation and emission
but does not test backend contract compatibility.

Story 0099's verification contract (from `implementation-plan.md` lines 112-113) stated:
> "the generic UI lists only the four approved objectives"
> "no incompatible user-selected type/Intent pair can be created"

Story 0100 did not re-verify these Story 0099 acceptance criteria.

### Affected Intents

All GENERIC intents are affected when combined with `PROJECT_EVOLUTION`:
- `describe-project-v1`
- `generate-readme-v1`
- `architecture-overview-v1`

The rejection occurs before intent-specific logic, so the failure is type-based, not intent-based.

## Regression B — Architecture Review Result Becomes Inaccessible

### Observation

Some Architecture Review + Intent combinations appear to start successfully. Upstream processing
may execute and the maintenance agent may discover and persist new Facts. However, the UI
eventually displays an error and the user cannot inspect the Analysis result.

Backend logs include:

```text
Caused by: tools.jackson.databind.DatabindException:
Cannot cast
com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse$FactItem
to
com.hopeful117.devlogai.analysis.result.dto.AnalysisResultResponse$EvidenceItem
```

### Execution Contract Trace

**Analysis launch → execution → result composition:**

1. `AnalysisWorkflowServiceImpl.start()` orchestrates the full pipeline
2. AI Task is created, knowledge selected, submitted to AI engine
3. AI engine returns proposals (INSIGHT, ENGINEERING_DECISION, ENGINEERING_EVENT)
4. Proposals are validated and persisted
5. Analysis status transitions to COMPLETED

**Result composition:**

6. `GET /api/v1/analyses/{id}/result` triggers `AnalysisResultQueryServiceImpl.getResult()`
7. `buildEvidence()` (line 344-361) calls `selectedEvidenceService.getSelectedEvidence()`
8. `selectedEvidenceService` returns `AiTaskSelectedEvidenceResponse` with typed category sections
9. Each category section contains domain-specific items (e.g., `FactItem`, `ObservationItem`)
10. `curateCategory()` (line 412-428) performs unchecked cast:

```java
@SuppressWarnings("unchecked")
List<AnalysisResultResponse.EvidenceItem> evidenceItems = (List<AnalysisResultResponse.EvidenceItem>) items;
```

11. Jackson serializes the response to JSON

### Root Cause

The `curateCategory` method performs an unchecked Java cast from `List<FactItem>` to
`List<EvidenceItem>`. At the Java level, this cast succeeds due to type erasure — the JVM
cannot verify generic type parameters at runtime.

However, when Jackson serializes the response to JSON, it encounters actual `FactItem` objects
in the list that are not `EvidenceItem` instances. Jackson's type information handling (or the
lack thereof) triggers a `DatabindException` because it cannot cast `FactItem` to `EvidenceItem`.

### Type Mismatch Analysis

**`AiTaskSelectedEvidenceResponse.FactItem`:**
```java
public record FactItem(
    UUID id,
    String type,
    String content,
    String source,
    List<String> evidenceReferences,
    Instant detectedAt
) {}
```

**`AnalysisResultResponse.EvidenceItem`:**
```java
public record EvidenceItem(
    String layer,
    String kind,
    String reference,
    String summary,
    Instant occurredAt,
    List<String> relatedReferences
) {}
```

These are structurally different types with different field names and types. The cast is
fundamentally incorrect — it relies on Java type erasure to hide the mismatch at compile time,
but the mismatch is exposed at serialization time.

### Affected Evidence Categories

The same unsafe cast pattern is applied to all 8 evidence categories:

| Category | Source Item Type | Cast Safe? |
|---|---|---|
| Facts | `FactItem` | NO — different fields |
| Observations | `ObservationItem` | NO — different fields |
| Prior Insights | `PriorInsightItem` | NO — different fields |
| Architecture Knowledge | `ArchitectureKnowledgeItem` | NO — different fields |
| Engineering Events | `EngineeringEventItem` | NO — different fields |
| Human Context | `HumanContextItem` | NO — different fields |
| Evolution Context | `EvolutionContextItem` | NO — different fields |
| Repository Evidence | `RepositoryEvidenceItem` | MAYBE — similar fields but still different type |

The failure manifests when FACT evidence is present because Facts are the most commonly
populated category during Analysis execution. Other categories would fail similarly if populated.

### Why Analysis Execution Succeeds

The Analysis execution pipeline completes successfully:
- Knowledge collection ✓
- Deterministic analysis ✓
- Project profile ✓
- Analysis context ✓
- AI task creation ✓
- Knowledge selection ✓
- AI engine submission ✓
- Proposal persistence ✓
- Analysis status → COMPLETED ✓

The failure occurs only during **result composition** — the query-time read model that projects
the persisted data into the API response. The Analysis itself has succeeded; only the
human-facing result surface is broken.

### Test Gap

- `AnalysisResultQueryServiceImplTest`: mocks `selectedEvidenceService.getSelectedEvidence()` to
  return `AiTaskSelectedEvidenceResponse.noAiTask()` or `AiTaskSelectedEvidenceResponse.available()`
  with empty categories; never tests with actual `FactItem` instances
- The test verifies the `AVAILABLE` path with trusted artifacts but does not exercise the
  evidence composition path with real typed items
- No test serializes the `AnalysisResultResponse` to JSON, which is where the failure manifests

### Why Existing Tests Passed

The tests mock the selected evidence service and verify the service method is called. They do
not exercise the actual type casting because:
1. The mock returns a pre-built response with empty or null categories
2. The `curateCategory` method is never called with actual `FactItem` instances
3. The test verifies Java-level behavior (method calls, return values) but not JSON serialization
4. The `@SuppressWarnings("unchecked")` annotation hides the compiler warning

## Architecture Invariants Preserved

- ADR-006 proposal lifecycle semantics unchanged
- AnalysisResult as query-time read model unchanged
- Trusted-artifact provenance model unchanged
- IntentDefinition ownership of execution semantics unchanged
- Backend ownership of business validation unchanged
- Angular ownership of SPA navigation unchanged

## No Architectural Conflict

No `ARCHITECTURAL_CONFLICT` or `SCOPE_CONFLICT` was found. Both regressions are implementation
defects in existing code paths, not architectural disagreements. The corrections are minimal
and localized.

## Proposed Minimal Regression Tests

### Regression A

- Backend WebMvc test: `POST /api/v1/analyses` with `type: "PROJECT_EVOLUTION"` returns 400
  (proves backend correctly rejects unsupported type)
- Backend unit test: `AnalysisServiceImpl.create()` rejects `PROJECT_EVOLUTION` when type is
  explicitly provided (proves type consistency validation)
- Backend unit test: `AnalysisServiceImpl.create()` accepts null type (proves optional legacy)
- Frontend test: form does not expose AnalysisType selector (proves Story 0099 contract)
- Frontend test: form emits only objective/Intent-based payload (proves correct contract)

### Regression B

- Backend unit test: `curateCategory()` correctly projects `FactItem` → `EvidenceItem`
- Backend unit test: `AnalysisResultResponse` serializes to JSON without `DatabindException`
- Backend parameterized test: all 8 evidence categories with realistic item instances

## Proposed Minimal Implementation Scope

### Regression A Fix

Restore the Story 0099 frontend contract:
- Revert the AnalysisType selector reintroduced by Story 0100
- Restore the objective-based form from Story 0099
- Restore the `Objective` interface and scope-derived controls
- Restore the `sources` input and auto-selection logic
- Verify all Story 0099 acceptance criteria remain satisfied

This is NOT about making the backend accept `PROJECT_EVOLUTION`. This is about restoring the
frontend contract that Story 0099 established and Story 0100 accidentally reverted.

### Regression B Fix

- Replace the unchecked cast in `curateCategory()` with explicit projection/mapping
- Map each domain-specific item type to `EvidenceItem` by semantic correspondence
- Preserve existing behavior for curated evidence limits, category semantics, and counts

## `git status --short`

```text
M frontend/src/app/features/analyses/project-analyses-section.html
?? docs/stories/0102-restore-analysis-launch-and-canonical-result/
```
