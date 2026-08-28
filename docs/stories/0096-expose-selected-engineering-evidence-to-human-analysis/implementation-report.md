# Story 0096 - Implementation Report

## Status

**IMPLEMENTATION_COMPLETE**

**HUMAN_BACKEND_REVIEW_APPROVED**

**ANGULAR_CHECKPOINTS_A_TO_J_COMPLETE**

**FINAL_HUMAN_REVIEW_APPROVED**

**STORY_COMPLETE**

Backend Slices 1-4 passed the required human backend review without contract changes. The human then
explicitly delegated Angular checkpoints A-J to OpenCode as an exception to the learning-oriented
ownership recorded in the approved plan. Backend and frontend implementation are complete, and the
final human review accepted the implementation and concluded the Story.

## Git Baseline

| Item | Value |
|---|---|
| Branch | `main` |
| HEAD | `10e0e457abd7eeb28fbdd4ec5f01963b25ca1752` |
| Worktrees | One: `/home/ludo/Bureau/workspace/devlog-ai` |
| Initial worktree | Dirty with previously recorded generated/build and documentation changes |
| Remote fetch | Not retried; the prior SSH security-key signing-agent failure was already documented and local HEAD was unchanged |
| Commit/push/merge | None |

Pre-existing unrelated changes were preserved.

## Implemented Endpoint

```http
GET /api/v1/analyses/{analysisId}/selected-evidence
```

The caller supplies only the Analysis ID. The service resolves the newest associated `AiTask` using
the existing repository ordering:

```text
createdAt DESC
id DESC
```

This remains a default Analysis-detail presentation convention only. Evidence is owned by and
identified with the returned `AiTask`; no canonical/primary task or uniqueness invariant was added.

## Response Contract

The response is `AiTaskSelectedEvidenceResponse`, with:

- `state`;
- owning `analysisId` and `projectId`;
- task audit identity: `id`, `taskType`, `status`, and `createdAt`;
- task-column `selectionVersion` and opaque `selectionDigest`;
- typed `snapshotMetadata`;
- category-specific typed `categories`.

Successful outer states are:

| Condition | State |
|---|---|
| Existing Analysis, no task | `NO_AI_TASK` |
| Nonterminal newest task, null snapshot | `SNAPSHOT_PENDING` |
| Terminal newest task, null snapshot | `SNAPSHOT_UNAVAILABLE` |
| Newest task with valid non-null snapshot | `AVAILABLE` |

`READ_FAILURE` is not a success state. Malformed, contradictory, or unsupported snapshots throw a
sanitized internal exception and use the existing `500 INTERNAL_ERROR` response path.

### Synthetic Shape

The following uses synthetic identifiers and no persisted local evidence:

```json
{
  "state": "AVAILABLE",
  "analysisId": "20000000-0000-0000-0000-000000000001",
  "projectId": "30000000-0000-0000-0000-000000000001",
  "task": {
    "id": "10000000-0000-0000-0000-000000000001",
    "taskType": "INSIGHT_GENERATION",
    "status": "COMPLETED",
    "createdAt": "2026-08-27T10:00:00Z"
  },
  "selectionVersion": "knowledge-selection-v4",
  "selectionDigest": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "snapshotMetadata": {
    "project": { "id": "30000000-0000-0000-0000-000000000001", "name": "Example" },
    "analysis": { "id": "20000000-0000-0000-0000-000000000001" },
    "projectProfile": { "profileVersion": "v1", "deterministicSummary": "Synthetic summary" },
    "diagnostics": { "collectionComplete": true, "truncated": false },
    "selection": { "selectionVersion": "knowledge-selection-v4", "selectedKnowledgeCount": 1 },
    "repositoryContext": { "contextVersion": "repository-context-engine-v1" }
  },
  "categories": {
    "facts": {
      "availability": "RECORDED",
      "count": 1,
      "items": [{
        "id": "40000000-0000-0000-0000-000000000001",
        "type": "SOURCE_DIRECTORY_PRESENT",
        "content": "Synthetic evidence text",
        "source": "src/example",
        "evidenceReferences": ["fact:synthetic"],
        "detectedAt": "2026-08-27T09:59:00Z"
      }]
    },
    "observations": { "availability": "RECORDED", "count": 0, "items": [] },
    "priorInsights": { "availability": "NOT_RECORDED", "count": 0, "items": [] },
    "architectureKnowledge": { "availability": "NOT_RECORDED", "count": 0, "items": [] },
    "engineeringEvents": { "availability": "NOT_RECORDED", "count": 0, "items": [] },
    "humanContext": { "availability": "NOT_RECORDED", "count": 0, "items": [] },
    "evolutionContext": { "availability": "RECORDED", "count": 0, "items": [] },
    "repositoryEvidence": { "availability": "RECORDED", "count": 0, "items": [] }
  }
}
```

Every category section enforces `count == items.size()`.

## Exact DTO Categories

- Facts: identity, type, content, source, evidence references, detection time.
- Observations: identity, type, content, rule identity/version, supporting Fact IDs, creation time.
- Prior Insights: type, severity, title, content; persisted prompt projection has no Insight ID.
- Existing architecture knowledge: Insight/proposal IDs, normalized/source types, severity, title,
  content, rationale, evidence references, creation time.
- Engineering Events: identity, category, title, summary, source/commit scope, occurrence time,
  proposal ID.
- Human context: identity, type, title, raw `contentMarkdown` string, status, update time.
- Evolution context: scope/revision/comparison fields and typed commit-diff, changed-file, statistics,
  reference, truncation, and warning structures.
- Repository evidence: layer, kind, reference, summary, occurrence time, related references, typed
  content and typed symbol/declaration/parameter/location structures.

Repository content statuses remain distinct: `COMPLETE`, `TRUNCATED`, `SKIPPED`, and `UNAVAILABLE`.

## Historical Projection

`HistoricalSelectedEvidenceSnapshotProjector` supports:

- `knowledge-selection-v1`;
- `knowledge-selection-v2`;
- `knowledge-selection-v3`;
- `knowledge-selection-v4`.

Category presence is determined with `containsKey`, including same-version V4 shape drift:

- missing category key -> `NOT_RECORDED`, count zero;
- present category array `[]` -> `RECORDED`, count zero;
- missing nullable object key -> `NOT_RECORDED`;
- present nullable object key with null -> `RECORDED`, count zero;
- unknown extra key -> ignored;
- malformed known field, unknown version, or contradictory identity -> fail closed.

Embedded selection version/digest values are compared with task columns when present. The digest is
not recomputed and is not represented as a snapshot checksum.

## Strict Whitelist and Error Safety

The projector returns only approved typed fields. It does not return raw maps or JSON nodes and does
not pass through project-profile `sections`, `sourceObservations`, or `resolvedRevisions`.

It does not expose AI Task context/intent/user-guidance snapshots, provider requests or internals,
prompt bodies/templates, correlation internals, `externalJobId`, or failure details.

Malformed read exceptions contain only task ID, selection version, and failing field path. They do
not retain a value-bearing cause, so the existing global exception logger cannot emit evidence bodies
through the exception chain. Existing API handling returns the sanitized message:

```text
An unexpected error occurred.
```

## No-Recompute Proof

The new read service constructor has exactly:

- `AnalysisRepository`;
- `AiTaskRepository`;
- `HistoricalSelectedEvidenceSnapshotProjector`.

The projector has only `ObjectMapper`.

The new read path contains none of:

- `KnowledgeSelectionService`;
- `KnowledgeCollectionService`;
- `AnalysisContextService`;
- `RepositoryContextService`;
- `RepositoryContextEngine`;
- Git/workspace adapters;
- MCP;
- AI provider clients;
- prompt composition services.

## PostgreSQL Fidelity Proof

The PostgreSQL 17/Testcontainers integration suite proves:

- a persisted JSONB snapshot remains the response source after current Fact, Insight, and project
  profile rows are mutated;
- newest task selection uses `createdAt DESC`, then `id DESC` for equal timestamps;
- the response identifies the selected task;
- a snapshot project identity contradicting the owning Analysis project fails closed;
- all-null legacy snapshot/version/digest triads map to pending or unavailable by task lifecycle;
- malformed persisted known shapes fail without evidence-body leakage.

## Files Added

### Production

- `backend/src/main/java/com/hopeful117/devlogai/analysis/evidence/dto/AiTaskSelectedEvidenceResponse.java`
- `backend/src/main/java/com/hopeful117/devlogai/analysis/evidence/projection/HistoricalSelectedEvidenceSnapshotProjector.java`
- `backend/src/main/java/com/hopeful117/devlogai/analysis/evidence/service/AiTaskSelectedEvidenceService.java`
- `backend/src/main/java/com/hopeful117/devlogai/analysis/evidence/service/AiTaskSelectedEvidenceServiceImpl.java`

### Tests

- `backend/src/test/java/com/hopeful117/devlogai/analysis/evidence/projection/HistoricalSelectedEvidenceSnapshotProjectorTest.java`
- `backend/src/test/java/com/hopeful117/devlogai/analysis/evidence/service/AiTaskSelectedEvidenceServiceTest.java`
- `backend/src/test/java/com/hopeful117/devlogai/analysis/evidence/service/AiTaskSelectedEvidencePersistenceIntegrationTest.java`

### Frontend Production

- `frontend/src/app/features/analyses/ai-task-selected-evidence.models.ts`
- `frontend/src/app/features/analyses/ai-task-selected-evidence-section.ts`
- `frontend/src/app/features/analyses/ai-task-selected-evidence-section.html`
- `frontend/src/app/features/analyses/ai-task-selected-evidence-section.scss`

### Frontend Tests

- `frontend/src/app/features/analyses/ai-task-selected-evidence-section.spec.ts`

## Files Modified

### Production

- `backend/src/main/java/com/hopeful117/devlogai/analysis/controller/AnalysisController.java`

### Tests

- `backend/src/test/java/com/hopeful117/devlogai/analysis/controller/AnalysisControllerWebMvcTest.java`

### Frontend Production

- `frontend/src/app/features/analyses/analysis.service.ts`
- `frontend/src/app/features/analyses/analysis-detail-page.ts`
- `frontend/src/app/features/analyses/analysis-detail-page.html`

### Frontend Tests

- `frontend/src/app/features/analyses/analysis.service.spec.ts`
- `frontend/src/app/features/analyses/analysis-detail-page.spec.ts`

### Workflow Documentation

- `docs/stories/0096-expose-selected-engineering-evidence-to-human-analysis/story.md`
- `docs/stories/0096-expose-selected-engineering-evidence-to-human-analysis/implementation-plan.md`
- `docs/stories/0096-expose-selected-engineering-evidence-to-human-analysis/implementation-report.md`
- `docs/ui-ux.md`

`ApiErrorHandlingWebMvcTest` was not modified because its existing test already locks the sanitized
500 response and absence of sensitive exception detail.

## Verification

Focused backend gate:

```bash
./mvnw -Dtest=HistoricalSelectedEvidenceSnapshotProjectorTest,AiTaskSelectedEvidenceServiceTest,AiTaskSelectedEvidencePersistenceIntegrationTest,AnalysisControllerWebMvcTest,ApiErrorHandlingWebMvcTest test
```

Result: **PASS** - 48 tests, 0 failures, 0 errors, 0 skipped.

Full backend gate:

```bash
./mvnw clean verify
```

Result: **PASS** - 971 tests, 0 failures, 0 errors, 0 skipped. JaCoCo's 80% bundle line-coverage gate
passed.

Focused frontend gate:

```bash
npm test -- --watch=false \
  --include=src/app/features/analyses/ai-task-selected-evidence-section.spec.ts \
  --include=src/app/features/analyses/analysis.service.spec.ts \
  --include=src/app/features/analyses/analysis-detail-page.spec.ts
```

Result: **PASS** - 3 files, 23 tests.

Full frontend gates:

```bash
npm run lint
npm run format:check
npm test -- --watch=false
npm run build
```

Results:

- lint: **PASS**;
- Prettier formatting check: **PASS**;
- unit tests: **PASS** - 45 files, 219 tests;
- production build: **PASS**;
- build retains one unrelated pre-existing warning: `project-maintenance-section.scss` exceeds its
  component-style budget by 135 bytes.

Playwright was not run for Story 0096 because `frontend/tests/projects-flow.spec.ts` has no API
interception or deterministic selected-evidence fixture and depends on mutable local project data.
The approved plan explicitly permits focused component coverage in this condition. Deterministic
Angular component tests cover semantic structure, native disclosure behavior, hostile text, and all
state behavior. Responsive behavior is implemented through the component's narrow-layout styles and
verified by lint, formatting, and the production build, but no browser-level narrow-viewport assertion
was run.

Repository checks:

- `git diff --check`: pass;
- frontend Story files: focused additions and Analysis integration only;
- no migration, MCP, retrieval, prompt, provider, or `AiTaskResponse` changes.

## Residual Technical Debt

- No application authentication/authorization boundary exists.
- The broad AI Task polling response still retransmits raw snapshots and current context.
- Historical JSONB has V1-V4 and same-version schema drift with no JSONB shape constraint.
- `selectionDigest` is not a persisted snapshot checksum.
- Snapshot immutability is application-level rather than database-enforced.
- Persisted source/human evidence may itself contain sensitive project content; no new secret-redaction
  capability was introduced.

## Frontend Implementation

**ANGULAR_CHECKPOINTS_A_TO_J_COMPLETE**

The Angular implementation:

- mirrors the approved backend DTO as a readonly discriminated union with category-specific types;
- performs one direct Core GET to `/api/v1/analyses/{analysisId}/selected-evidence`;
- keys reads by Analysis, newest task identity, and snapshot readiness without parsing the raw task
  snapshot;
- does not add a timer or manual subscription and does not refetch for equivalent polling emissions;
- distinguishes loading, sanitized read failure, `NO_AI_TASK`, `SNAPSHOT_PENDING`,
  `SNAPSHOT_UNAVAILABLE`, and `AVAILABLE`;
- distinguishes `RECORDED` empty categories from `NOT_RECORDED` historical categories;
- presents all eight typed evidence categories, task/selection identity, approved snapshot metadata,
  repository content status, and symbol detail;
- uses interpolation and preformatted text only, with no `innerHTML`, Markdown renderer, or sanitizer
  bypass;
- uses semantic sections/headings/lists, native details/summary disclosure, status/alert roles, and a
  single-column narrow-layout breakpoint;
- appears after AI execution metadata and before generated Insight Proposals and Validated Insights.

## Readiness

**FINAL_HUMAN_REVIEW_APPROVED**

**STORY_COMPLETE**

The final human review accepted the persisted selected-evidence read model, no-recompute
architecture, strict typed projection, state and historical category semantics, Angular integration,
security boundaries, verification evidence, and the documented decision not to add Playwright
coverage for this Story.
