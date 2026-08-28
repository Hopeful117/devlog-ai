# Repository Analysis - Story 0096

## Story

**0096 - Expose Selected Engineering Evidence to Human Analysis**

Status entering this mission: `READY_FOR_REPOSITORY_ANALYSIS`.

This analysis is repository-analysis only. It introduces no production code, test, endpoint,
Angular change, migration, implementation plan, commit, or remote mutation.

## Human Repository Analysis Review

**HUMAN_REPOSITORY_ANALYSIS_REVIEW = APPROVED_WITH_DECISION**

The repository findings are accepted. The previous `STORY_AMBIGUITY` is resolved as follows:

* selected evidence belongs to the specific `AiTask` execution whose persisted snapshot is shown;
* Analysis Detail defaults to the newest task by existing `createdAt DESC, id DESC` ordering;
* this ordering is presentation/default selection only, not a canonical/primary task or domain
  invariant;
* the response identifies the selected task and remains compatible with future task-specific
  navigation, which is outside Story 0096;
* a missing historical category key is `NOT_RECORDED`, while a present empty array is recorded empty;
* a nonterminal task with a null snapshot is `SNAPSHOT_PENDING`;
* a terminal task with a null snapshot is `SNAPSHOT_UNAVAILABLE`;
* malformed/contradictory snapshots fail closed as `READ_FAILURE` through the sanitized API error
  path.

No additional repository investigation is required before planning.

## Repository State

| Item | Observed value |
|---|---|
| Branch | `main` |
| HEAD | `10e0e457abd7eeb28fbdd4ec5f01963b25ca1752` |
| Worktrees | One: `/home/ludo/Bureau/workspace/devlog-ai` |
| Remote state | `main...origin/main` before the fetch attempt |
| Fetch | Attempted; failed because the SSH security-key agent refused signing |
| Worktree | Dirty before this mission |

Pre-existing changes include generated/build output under `ai-engine/app/api/__pycache__` and
`devlog-contracts/target`, the amended `docs/decisions/ADR-063.md`, and untracked investigation and
Story artifacts. They were preserved. The only file created by this mission is this artifact.

Recent local history starts with:

```text
10e0e45 Merge pull request #78 from Hopeful117/feature/story-0095-trusted-knowledge-category-composition
a1d3e41 docs(story): complete engineering reports for story 0095
0b62fd1 test(context): prove starvation fix and bounded knowledge retrieval
9b0e760 feat(context): availability-aware knowledge floors in evidence selection
31345ed feat(context): bounded intent-relevant Facts/Observations retrieval
```

## Governing Architecture

Story 0096 is a presentation/read projection over a persisted consumer-owned composition. The
authoritative source is `AiTask.selectedKnowledgeSnapshot`; it is not current `AnalysisContext`,
current project knowledge, current `EngineeringContext`, or a rebuilt `RepositoryContext`.

ADR-063 keeps retrieval, composition, projection, grounding, and expansion separate and makes the
human workflow a backend-direct context consumer (`docs/decisions/ADR-063.md:503-533,664-709`). For
this first slice:

| Invariant | Repository-analysis result |
|---|---|
| Retrieval changes | None required |
| `RepositoryContextEngine` responsibility | Unchanged |
| Angular to MCP | Prohibited and unnecessary |
| Context recomputation | Prohibited and unnecessary |
| New persistence/migration | Not required |
| Universal Evidence DTO / `KnowledgeReference` / `ContextPack` | Not required |
| RAG/vector infrastructure | Not required |
| Authorization redesign | Not required; existing isolation limits remain |
| ADR-006 proposal governance | Unchanged |

No `ARCHITECTURAL_CONFLICT` or `SCOPE_CONFLICT` was found.

## Current Analysis Workflow

The actual write path is:

```text
AnalysisWorkflowServiceImpl.start(analysisId)
  -> AnalysisService.start
  -> KnowledgeCollectionService.collect
  -> DeterministicAnalysisService.analyze
  -> ProjectProfileService.build
  -> AnalysisContextService.build
  -> AiTaskService.create(context)                 // task initially has no selected snapshot
  -> KnowledgeSelectionService.select
       -> SelectedKnowledge
       -> RepositoryContextService.build           // existing bounded composition
  -> AiTaskService.attachSelectedKnowledge
       -> SelectedKnowledgePromptProjectionService.toMap
       -> ai_tasks.selected_knowledge_snapshot
  -> SelectedKnowledgePromptProjectionService.toMap
       -> PromptRequest selected context
  -> AI Engine submission
```

The sequence is explicit in
`backend/src/main/java/com/hopeful117/devlogai/analysis/workflow/AnalysisWorkflowServiceImpl.java:51-91`.
The stored map and outbound map are separately produced from the same in-memory `SelectedKnowledge`
by the same projector. Selection attachment commits through the transactional AI Task service before
submission.

Failure timing matters:

| Failure point | Persisted condition |
|---|---|
| Before task creation | No AI Task |
| After creation, before attachment | `FAILED` task, null selected snapshot |
| After attachment, during submission | `FAILED` task with selected snapshot |
| During provider processing/callback | Terminal task normally retains its selected snapshot |

Snapshot availability therefore cannot be inferred from `AiTask.status` alone.

## Analysis / AiTask Relationship

### Physical relationship

`AiTask.analysis` is mandatory `@ManyToOne` and immutable as a foreign-key field
(`AiTask.java:34-36`). Migration V13 creates a non-null `analysis_id` foreign key and a normal index,
not a uniqueness constraint (`V13__create_ai_tasks_table.sql:1-22`). `Analysis` has no reverse task
field.

The implemented cardinality is:

```text
Analysis 1 <- 0..N AiTask
AiTask     -> exactly one Analysis
```

ADR-017 explicitly requires future retries and multiple AI tasks for one Analysis and documents
`Analysis -> 1..N AiTask` (`docs/decisions/ADR-017.md:20-47,108-130`). Zero tasks are possible before
or without workflow execution.

`AiTask` has no `project_id`. Its project is reached through
`AiTask.analysis_id -> Analysis.project_id`. The Analysis-to-project relation is mandatory.

### Current task resolution

The repository provides both a complete newest-first list and a single newest task:

```text
findByAnalysisIdOrderByCreatedAtDescIdDesc
findFirstByAnalysisIdOrderByCreatedAtDescIdDesc
```

See `AiTaskRepository.java:25-27`. Diagnostics and current context choose the first task with this
ordering (`AnalysisDiagnosticsServiceImpl.java:38-48,82-89`). Angular also displays only
`tasks.data[0]` (`analysis-detail-page.ts:95-116`; `analysis-detail-page.html:139-215`).

Local data currently contains 86 Analyses with tasks, zero with multiple tasks, and a maximum of one
task per Analysis. That describes current data, not the domain/schema invariant.

### Retries and attempts

There is no automatic task retry and no transition out of `FAILED`. `attemptCount` increments on
successful submission and on a submission/preparation failure while `CREATED`; it does not identify
a retry generation (`AiTaskServiceImpl.java:156-190`). A future retry or specialized operation is a
new `AiTask`, consistent with ADR-017.

The generic public `POST /api/v1/ai-tasks` can also create another task for an existing Analysis and
does not enforce Analysis state or one-task cardinality (`AiTaskController.java:24-31`;
`AiTaskServiceImpl.java:46-50`). Such a task is created without selected knowledge.

### Authoritative-task ambiguity and resolution

No field or constraint marks a task as primary, current, successful, output-producing, or
authoritative for the Analysis detail page. "Newest" is an existing read convention, not a domain
rule.

Proposal rows do retain an optional immutable `ai_task_id` relation
(`ValidatableProposal.java:41-46`; `V14__add_ai_task_proposal_traceability.sql:1-18`), but the current
proposal response omits that task ID (`ValidatableProposalResponse.java:11-37`) and the Analysis page
aggregates proposals by Analysis. If multiple specialized tasks exist, there may be multiple valid
input snapshots corresponding to different generated outputs.

Selecting "newest snapshot-bearing task," "latest successful task," or "task with proposals" would
invent behavior and could hide a newer failed/current execution. Selecting newest regardless of
status matches current diagnostics/UI, but the Story does not approve that rule.

The Repository Analysis originally concluded `STORY_AMBIGUITY`. Human review resolved it by adopting
newest `(createdAt DESC, id DESC)` as the default Analysis-detail presentation rule. Evidence remains
owned by and identified with that selected task. The rule creates no canonical/primary task, does not
change cardinality, and does not prevent future task-specific navigation.

## AiTask Persistence

Relevant entity fields are defined in `AiTask.java:30-117`:

| Field | Java / database semantics | Relevance |
|---|---|---|
| `id` | UUID primary key | Audit identity |
| `analysis` | non-null many-to-one, `analysis_id` not updateable | Analysis association |
| `correlationId` | non-null unique UUID, not updateable | Core/AI callback correlation |
| `taskType` | enum, not updateable | Specialized operation identity |
| `intentId`, `intentVersion`, `intentSnapshot` | strings plus JSONB | Intent traceability; raw snapshot is not evidence output |
| `userGuidanceSnapshot` | JSONB, not updateable | Separate guidance trace; not the selected-evidence source |
| `promptRequestId`, `promptVersion` | UUID/string | Prompt execution traceability |
| `provider`, `modelIdentifier` | strings | Execution metadata |
| `promptContentDigest`, `contextDigest` | 64-character strings | Prompt/provider context traceability |
| `selectedKnowledgeSnapshot` | `Map<String,Object>` mapped to JSONB | Story 0096 authority |
| `selectionVersion`, `selectionDigest` | strings | Selection identity |
| `status` | `CREATED/SUBMITTED/PROCESSING/COMPLETED/FAILED` | Lifecycle, not snapshot availability |
| `contextSnapshot` | non-null `Map<String,Object>` JSONB, not updateable | Pre-selection Analysis context; prohibited fallback |
| `externalJobId` | nullable string | Provider job metadata, not evidence |
| `attemptCount` | integer | Submission-attempt counter, not task generation |
| failure/timestamps | strings/timestamps | State and audit metadata |

There is no direct project ID on the task.

### V27 nullability

`V27__add_selected_knowledge_snapshot.sql` adds:

```text
selected_knowledge_snapshot JSONB NULL
selection_version           VARCHAR(100) NULL
selection_digest            VARCHAR(64) NULL
```

Its check constraint allows only:

```text
all three null
or
snapshot and version non-null, digest matching [0-9a-f]{64}
```

It performs no backfill and does not validate the JSON root/schema, known version, nonblank version,
or equality between columns and embedded snapshot metadata (`V27...sql:1-18`). Story 0096's legacy
null assumption is accurate. Local data has 80 non-null snapshots and 6 all-null tasks.

### Type-safety loss

```text
SelectedKnowledge typed records
  -> PromptProjection private typed records
  -> ObjectMapper.convertValue(..., Map.class)
  -> mutable Map<String,Object>
  -> Hibernate JSONB
  -> Map<String,Object> entity
  -> AiTaskMapper
  -> AiTaskResponse Map<String,Object>
  -> Angular Readonly<Record<string, JsonValue>>
```

Type safety is lost at `SelectedKnowledgePromptProjectionService.toMap`
(`SelectedKnowledgePromptProjectionService.java:26-30`) and remains lost through persistence and the
generic API (`AiTaskResponse.java:9-36`; `analysis.models.ts:84-112`). Java must regain type safety in
a focused read projector; Angular must never interpret the raw map.

### Immutability precision

The normal attachment operation only accepts `CREATED` and rejects a second attachment
(`AiTaskServiceImpl.java:68-78`). However, selected snapshot/version/digest columns are not marked
`updatable=false`, Lombok exposes setters, the map is mutable, and no database trigger prevents an
update. Story 0096 should describe this as the **persisted execution-time snapshot**, not claim
database-enforced immutability. Hardening persistence is outside scope.

## Snapshot Schema and Versions

### Top-level schema by current projection

The current `PromptProjection` record defines these keys in this order
(`SelectedKnowledgePromptProjectionService.java:120-137`):

```text
project
analysis
projectProfile
selectedFacts
selectedObservations
diagnostics
selectedInsights
existingArchitectureKnowledge
selectedEngineeringEvents
selectedHumanContextInputs
repositoryContext
evolutionContext
selectionMetadata
selectionDigest
```

Snapshot envelope fields are:

| Object | Exact persisted fields |
|---|---|
| `project` | `id`, `name`, `slug`, `description`, `status` |
| `analysis` | `id`, `type`, `intentId`, `intentVersion`, `status`, `startedAt`, `completedAt`, `createdAt` |
| `projectProfile` | `id`, `projectId`, `analysisId`, `profileVersion`, `rendererVersion`, `generatedAt`, `requestedRevision`, `resolvedRevisions`, `completeness`, `sections`, `deterministicSummary`, `sourceObservations`, `characteristicCount` |
| `projectProfile.completeness` | `status`, `collectionComplete`, `truncated`, `warningCount`, `errorCount`, `successfulCollectorCount`, `collectorsWithWarningsCount`, `failedCollectorCount` |
| `diagnostics` | `collectionComplete`, `truncated`, `warningCount`, `errorCount` |

`projectProfile.resolvedRevisions` is an arbitrary map, while `sections` and `sourceObservations` are
lists of arbitrary maps (`ProjectProfileResponse.java:9-25`). They are persisted but are not a sound
typed public contract without field-level selection.

### Historical versions observed locally

Current code only declares `knowledge-selection-v4`
(`KnowledgeSelectionServiceImpl.java:25`). Persisted data proves four versions:

| Version | Rows | Top-level additions present |
|---|---:|---|
| `knowledge-selection-v1` | 4 | Base project/analysis/profile, Facts, Observations, Insights, diagnostics, selection identity |
| `knowledge-selection-v2` | 1 | V1 plus `repositoryContext` |
| `knowledge-selection-v3` | 19 | V2 plus architecture knowledge, Engineering Events, evolution context |
| `knowledge-selection-v4` | 56 | V3 plus human context inputs on snapshots created after that projection change |
| null | 6 | No snapshot |

The local V4 population contains 37 snapshots with the human-context key and 19 without it. Version
V4 therefore does not uniquely determine exact top-level shape; projection capabilities evolved
without every shape change receiving a new selection version. Missing-key handling must be
presence-based as well as version-aware.

No local snapshot has an empty-object or non-object root. No local Facts, Observations, Insights, or
RepositoryContext key has the wrong container type. JSONB and the V27 constraint nevertheless permit
all of those malformed states.

### Version interpretation

Safe read behavior must follow these rules:

* Null root snapshot means `SNAPSHOT_UNAVAILABLE` for a terminal task, not an empty selection.
* A category key present as an empty array means available and none selected.
* A missing category key means not recorded by that historical projection, not none selected.
* Unknown extra keys can be ignored by the bounded human projection.
* Unknown selection versions with recognizable fields must not be treated as current-schema proof.
* Wrong root/category/value types, contradictory Analysis/project identity, or invalid known fields
  must become a read failure with sanitized logging, not silently empty categories.

Direct deserialization into current `SelectedKnowledge` is invalid: selected Insight IDs are
intentionally absent from the persisted prompt shape, and persisted RepositoryContext omits fields
required by its current full model.

## Exact Category Mapping

| Category | Persisted fields | Human meaning / limitation |
|---|---|---|
| Project | `id`, `name`, `slug`, `description`, `status` | Snapshot scope; compare ID with owning Analysis project |
| Analysis | `id`, `type`, intent identity, status, lifecycle times | Snapshot scope; compare ID with route Analysis |
| Facts | `id`, `type`, `content`, `source`, `evidenceReferences[]`, `detectedAt` | Deterministic evidence; no Analysis ID, confidence, or severity field |
| Observations | `id`, `type`, `content`, `ruleId`, `ruleVersion`, `supportingFactIds[]`, `createdAt` | Rule-derived evidence; Fact relation is IDs only |
| Prior Insights | `type`, `severity`, `title`, `content` | ACTIVE Insights selected as input; persisted prompt projection deliberately omits Insight and source-Analysis IDs |
| Architecture knowledge | `insightId`, `proposalId`, `normalizedType`, `severity`, `sourceType`, `title`, `content`, `rationale`, `evidenceReferences[]`, `createdAt` | A second, architecture-intent-only projection over ACTIVE architecture-relevant Insights, not Decisions or arbitrary documents |
| Engineering Events | `id`, `category`, `title`, `summary`, `sourceId`, `baseCommit`, `targetCommit`, `occurredAt`, `proposalId` | Selected from `validatedEngineeringEvents`; no separate trust/status field survives because validation is implied by source collection |
| Human context | `id`, `type`, `title`, `contentMarkdown`, `status`, `updatedAt` | ACTIVE project inputs are loaded, up to five selected; raw user-authored Markdown must be treated as untrusted text |
| Diagnostics | `collectionComplete`, `truncated`, `warningCount`, `errorCount` | Safe aggregate collection state |
| Selection metadata | version, rules, counts, budget, completeness | Audit/technical disclosure; rules are implementation names, not human evidence |
| Evolution context | revision comparison and bounded commit diff | Technical evidence/metadata for evolution analyses |
| RepositoryContext | compact selected evidence projection | Bounded evidence actually supplied, not the candidate universe |

### Architecture knowledge semantics

`selectExistingArchitectureKnowledge` runs only for intent `architecture-overview`, filters the same
ACTIVE Insight candidate set by architecture source types or fallback architectural/technology
Insight types, and limits to five (`KnowledgeSelectionServiceImpl.java:267-304`). It must be labelled
"existing architecture knowledge supplied to this Analysis," not Decisions, ADR documents, or
current architecture truth.

### Human context and Engineering Event trust

Project context loads only ACTIVE human inputs (`ProjectContextProviderImpl.java:155-159`), and the
snapshot retains status. Engineering Events come through a collection named
`validatedEngineeringEvents` and retain proposal/source IDs, commits, and occurrence time
(`ProjectContextSnapshot.java:75-97`). The human DTO should report these observed semantics without
inventing a universal trust field.

## Evolution Context

`evolutionContext` contains:

```text
contextVersion, projectId, sourceId, baseCommit, targetCommit,
comparisonPolicy, mergeCommit, targetCommittedAt, commitDiff
```

`commitDiff` contains project/repository IDs, commit and parent hashes, root/merge flags, commit
message/time, changed files, aggregate statistics, candidate ADR/roadmap references, evidence
references, truncation, and warnings. Each changed file includes change type, old/new path, binary
flag, insertions/deletions, language/category, exclusion state/reason, and evidence reference
(`CommitDiffAnalysisContext.java:14-45`). This is best represented as a separate structured category
or metadata section; it must not be collapsed into current repository state.

## RepositoryContext Snapshot

Only these fields survive the prompt projection:

```text
contextVersion
profile
evidence[]
warnings[]
contextDigest
```

The full internal `RepositoryContext` also has active profile keys, plan/intelligence explanations,
selected-by-layer counts, diagnostics, budget, used tokens, candidate/discarded counts, global
truncation, and selection decisions (`RepositoryContext.java:6-23`). Those fields do **not** survive
and must not be reconstructed.

Each persisted repository evidence item is exactly:

```text
layer
kind
reference
summary
occurredAt
relatedReferences[]
content | null
symbols | null
```

The current prompt projection omits internal score, provenance (`sourceType`, repository location,
originating file, identifier), extraction metadata, estimated tokens, ranking reasons, relevance,
and selection reason (`RepositoryEvidence.java:8-21,125-130`;
`SelectedKnowledgePromptProjectionServiceTest.java:132-155`). Path/file/source identity may exist
inside `reference` or related references for a given kind, but there is no independent persisted
path or provenance contract.

### Content

Persisted content fields are:

```text
status, text, reason, policyId, policyVersion, revision,
allocationPolicyId, allocationPolicyVersion, allocationRank
```

Statuses are `COMPLETE`, `TRUNCATED`, `SKIPPED`, and `UNAVAILABLE`
(`RepositoryEvidenceContent.java:3-37`). This is sufficient to avoid implying omitted/truncated text
was supplied. Internal `allocationReasons` is not persisted. There is no separate boolean
`truncated`; `status` carries that distinction.

### Symbols

Persisted symbol metadata includes status/reason, policy and extractor identities/versions, revision,
allocation rank, `truncated`, returned/available counts, and declarations. A declaration contains
kind, name, owning type, modifiers, return type, parameters (`type`, `name`), annotations, and source
location (`beginLine`, `beginColumn`, `endLine`, `endColumn`)
(`RepositoryEvidenceSymbols.java:5-70`). These values are useful for human inspection and safe as
plain text. Internal symbol allocation reasons are omitted.

## Selection Metadata and Digest

Current selection metadata is:

```text
selectionVersion
appliedRules[]
selectedKnowledgeCount
discardedKnowledgeCount
knowledgeBudget:
  maximumFacts
  maximumObservations
  maximumInsights
  maximumArchitectureKnowledge
  maximumRepositoryEvidence
completeness
```

Current V4 budgets are 40 Facts, 25 Observations, 10 Insights, 5 architecture items, and 60
repository evidence items. Engineering Events and human inputs are additionally limited in service
code to 10 and 5 (`KnowledgeSelectionServiceImpl.java:25-33,75-82`).

Human display classification:

| Data | Classification |
|---|---|
| Version, selected/discarded counts, completeness, budget | `SAFE_HUMAN_DISPLAY` |
| Applied rule identifiers | `TECHNICAL_DISCLOSURE_ONLY` |
| Collection diagnostics aggregate booleans/counts | `SAFE_HUMAN_DISPLAY` |
| Internal selection decisions/scores/ranking reasons | Not persisted; `DO_NOT_INVENT` |

`selectionDigest` is SHA-256 over a `DigestInput` built from current domain objects
(`KnowledgeSelectionServiceImpl.java:307-335`). It is deterministic for that serialization input,
but it is **not a hash of the persisted prompt projection**:

* selected human context inputs are omitted from the digest input;
* full RepositoryContext internals are included even though the prompt snapshot strips them;
* the database does not verify equality between embedded and column digest/version values.

The UI may show it as the persisted **selection digest/identifier**. It must not call it proof that
the displayed snapshot bytes are intact or attempt to recompute it from the human DTO.

## Snapshot Security Review

### Content present

The snapshot can contain repository-relative references/paths, bounded source text, Java symbols,
commit messages and changed paths, Fact/Observation content, architecture rationale, project profile
data, and raw human-authored Markdown. These are legitimate project engineering evidence, but may be
sensitive and all strings are untrusted.

### Content absent

The selected snapshot does not contain provider tokens, credentials by design, hidden system
instructions, rendered prompt templates, provider requests, `externalJobId`, or prompt execution
failure detail. Those live in other AI Task fields or outside this projection. A dedicated response
must not copy `intentSnapshot`, `contextSnapshot`, correlation/provider internals, or the full generic
`AiTaskResponse` merely for convenience.

The snapshot cannot guarantee that selected repository source itself contains no hard-coded secret.
No secret-redaction contract was found. That is a general source-evidence risk, not evidence that the
snapshot stores provider credentials.

### Projection whitelist

The category fields documented above are suitable for same-project human inspection. The human
projection should narrow project-profile exposure to typed identity, revision, completeness, and
summary fields; `sections`, `sourceObservations`, and `resolvedRevisions` contain nested arbitrary
maps and should not be blindly passed through unless each selected subfield is justified.

**Security classification: HIGH systemic, not a Story-specific blocker.** There is no application
authentication/authorization boundary, while current task APIs already expose the raw snapshot by
task, correlation ID, and Analysis. Story 0096 can enforce Analysis/task/project association but
cannot prove caller authorization. The dedicated endpoint must not worsen this by accepting a task
ID or exposing fields outside the persisted selected-evidence whitelist. ADR-063 already records the
authorization limitation and Story 0096 explicitly excludes redesign.

## Current Backend Contracts

Current Analysis subresources are hosted by `AnalysisController`:

```text
GET  /api/v1/analyses/{id}
GET  /api/v1/analyses/{id}/diagnostics
GET  /api/v1/analyses/{id}/warnings
GET  /api/v1/analyses/{id}/context
POST /api/v1/analyses/{id}/workflow
```

See `AnalysisController.java:23-78`. ADR-025 endorses lightweight diagnostics plus dedicated
Analysis-scoped resources and says diagnostics must not embed complete collections
(`docs/decisions/ADR-025.md:67-92`).

Current AI Task exposure is:

```text
GET /api/v1/ai-tasks/{id}
GET /api/v1/ai-tasks/correlation/{correlationId}
GET /api/v1/ai-tasks/analysis/{analysisId}
```

All return broad `AiTaskResponse`, which includes both raw `selectedKnowledgeSnapshot` and raw
`contextSnapshot` (`AiTaskController.java:34-50`; `AiTaskResponse.java:9-36`). The Analysis list route
does not first verify Analysis existence; an unknown Analysis and a real Analysis with no task both
produce an empty list (`AiTaskServiceImpl.java:147-153`).

The Angular Analysis page already receives `selectedKnowledgeSnapshot`; its model types it as
`Readonly<Record<string, JsonValue>> | null`, but the template does not use it
(`analysis.models.ts:84-112`; `analysis-detail-page.html:125-215`).

## Recommended Backend Read Boundary

With the human decision applied, the smallest consistent API is:

```http
GET /api/v1/analyses/{analysisId}/selected-evidence
```

This follows existing Analysis subresource naming, accepts no task ID, and communicates that the
result is the selected historical input rather than all current evidence.

Recommended ownership:

| Component | Responsibility |
|---|---|
| `AnalysisController` | Add the Analysis-scoped GET only; delegate all behavior |
| Focused Analysis selected-evidence service | Resolve Analysis, resolve the approved task, classify state, invoke projector |
| Focused snapshot projector/mapper | Validate raw map according to persisted shape and produce typed immutable DTOs |
| Analysis evidence DTO package | Story-specific response and category records |
| `AnalysisRepository.findWithProjectById` | Existing Analysis/project scope lookup |
| `AiTaskRepository.findFirstByAnalysisIdOrderByCreatedAtDescIdDesc` | Approved default Analysis-detail task selection |

The read service should have no dependency on `KnowledgeSelectionService`,
`KnowledgeCollectionService`, `AnalysisContextService`, `RepositoryContextService`,
`RepositoryContextEngine`, workspace/Git adapters, MCP, prompt services, or AI providers. Structural
absence of these dependencies is the strongest no-recompute guarantee.

The service should compare snapshot `analysis.id` and `project.id` with the route Analysis and owning
project when those fields are present. A mismatch is corrupted data/read failure, not evidence to
return. This is association isolation, not application-user authorization.

## API and State Semantics

Approved HTTP/state mapping:

| Repository condition | Detection | API/UI semantic |
|---|---|---|
| Analysis absent | Analysis repository miss | Existing `404 ENTITY_NOT_FOUND` |
| No associated task | Approved task query empty | `200 NO_AI_TASK` |
| Task exists, nonterminal, snapshot null | `CREATED/SUBMITTED/PROCESSING` plus null snapshot | `200 SNAPSHOT_PENDING`; do not call legacy unavailable |
| Terminal task, snapshot null | `COMPLETED/FAILED` plus null snapshot | `200 SNAPSHOT_UNAVAILABLE` |
| Non-null valid snapshot with any displayable item | Valid recorded category entries | `200 AVAILABLE_WITH_ENTRIES` |
| Non-null valid snapshot, category key present as `[]` | Key/type validation | `AVAILABLE_CATEGORY_EMPTY`: none selected |
| Non-null valid snapshot, no displayable category entries | All recorded category arrays empty; repository/evolution absent or empty | `200 AVAILABLE_GLOBALLY_EMPTY` with metadata |
| Historical category key absent | Missing key on an otherwise valid snapshot | Category `NOT_RECORDED`, not empty |
| Unknown extra key | Valid recognized fields plus extra | Ignore extra field |
| Unknown version | Non-null version not in supported interpretation set | `READ_FAILURE`; never assume current schema silently |
| Malformed/contradictory snapshot | Wrong root/type/value or scope mismatch | `READ_FAILURE`: sanitized 500; do not partially hide corruption |
| Infrastructure/serialization error | Exception | `READ_FAILURE`: existing sanitized `500 INTERNAL_ERROR` and Angular retry |

The workflow creates and attaches the task in separate service transactions. Human review therefore
adopts explicit `SNAPSHOT_PENDING` semantics so a transient nonterminal null is never classified as
legacy, unavailable, empty, or failed.

Malformed data is realistic at the schema level because JSONB has no shape constraint, although no
malformed local row was found. Existing `READ_FAILURE` behavior can cover it; no broad partial-
degradation framework is justified. Log identifiers/version and the validation issue, never evidence
bodies. No additional acceptance criterion is required if AC8's failed-read semantics are understood
to include malformed persisted data, but the future plan must include a focused malformed-snapshot
test.

`NO_AI_TASK`, snapshot pending, snapshot unavailable, and category availability are valid states of
an existing Analysis and should not be overloaded onto 404. Only missing Analysis is 404.

HTTP caching is not necessary for this Story. A one-shot Angular Observable is sufficient; adding
ETag/cache infrastructure would be scope without an existing convention.

## Payload and Polling

Read-only local PostgreSQL measurements:

| Metric | Value |
|---|---:|
| AI Tasks | 86 |
| Persisted snapshots | 80 |
| Null snapshots | 6 |
| Minimum snapshot size | 7,459 bytes |
| Average snapshot size | 127,706 bytes |
| Maximum snapshot size | 242,271 bytes |

The current Angular page polls `/api/v1/ai-tasks/analysis/{analysisId}` every 2 seconds in development
and 5 seconds in production until the latest task is terminal
(`environment.development.ts:5`; `environment.ts:5`; `analysis-detail-page.ts:95-123`). Because that
route returns full `AiTaskResponse`, it already retransmits the raw snapshot and pre-selection context
on every poll, even though neither is used in the execution panel.

Story 0096 must not add the typed human projection to diagnostics or another polling payload. The
dedicated evidence endpoint should be loaded once per approved Analysis/task identity, optionally
when its panel is opened, and cached/shared in the component. The existing broad task polling is a
pre-existing payload debt. Removing fields from its public response would break compatibility; a
future plan must decide whether a lightweight latest-task summary can be adopted within Story scope
or record the repeated raw payload as residual debt. The selected-evidence endpoint itself must not
be polled indefinitely.

## Current Angular Architecture

### Component map

| File/component | Current responsibility | Story 0096 effect |
|---|---|---|
| `app.routes.ts:96-100` | Lazy route `/analyses/:id` | Route unchanged |
| `analysis-detail-page.ts` | Route identity, Analysis resources, diagnostics/task polling | Gate/pass Analysis/task identity to evidence read surface; do not parse snapshot |
| `analysis-detail-page.html` | Analysis, diagnostics, execution, profile, warnings, raw current context | Place historical evidence between execution metadata and generated output |
| `analysis-detail-page.scss` | Local card, metadata, details, preformatted styles | Ensure evidence host/mobile layout remains usable |
| `analysis.service.ts` | Direct Core HTTP client | Add one Analysis-scoped selected-evidence GET |
| `analysis.models.ts` | Analysis, task, diagnostics, profile types | Add typed Story-specific read models or import them from a focused model file |
| `analysis-insights-section.*` | Loads current Analysis proposals and validated Insights | Keep as generated/current output; clarify input/output labels only as required |
| New focused standalone evidence component | None exists | Own one-shot evidence state and category presentation |

A dedicated evidence child component is preferable to making the already large page template parse
and render every category. It has one Analysis-scoped input (and, if needed after ambiguity
resolution, approved task identity), emits no domain mutation, loads a typed response, and presents
input evidence only.

### Existing reactive flow

```text
ActivatedRoute.paramMap
  -> routeId$ (map + shareReplay)
  -> switchMap to AnalysisService
  -> HTTP Observable
  -> LoadState<T>
  -> AsyncPipe
  -> template/subcomponent
```

Diagnostics and AI Tasks add `timer`, `exhaustMap`, terminal `takeWhile`, and `shareReplay`.
`exhaustMap` prevents overlapping requests; the outer `switchMap` cancels stale route/refresh streams
(`analysis-detail-page.ts:42-123`). Existing tests assert non-overlap, terminal stop, and no manual
subscription (`analysis-detail-page.spec.ts:154-185,208-209`).

For immutable evidence, the natural flow is:

```text
analysis/task readiness
  -> switchMap to getSelectedEvidence(analysisId)
  -> loading/loaded/error discriminated state
  -> shareReplay if multiple template consumers need it
  -> AsyncPipe
```

No timer is appropriate after an available snapshot is returned.

### No-task to task transition

Current task polling continues while the task list is empty, so a page opened before task creation
can observe `[] -> CREATED/SUBMITTED -> terminal` (`analysis-detail-page.ts:112-116`). The template
currently shows "No AI Task" during the empty phase (`analysis-detail-page.html:213-215`).

Because snapshot attachment follows task creation, loading as soon as any task appears can race with
attachment. The UI should key/reset evidence state by the newest task ID observed by existing task
polling, request again while that task remains pending as part of the existing task-readiness
transition, stop once evidence becomes available or terminally unavailable, and preserve explicit
retry after a read failure. Future manual task navigation remains outside scope.

## Safe Rendering and UX Patterns

Angular interpolation escapes strings. Existing Analysis tests prove script-like guidance and
context render as text without executable DOM nodes (`analysis-detail-page.spec.ts:192-206`). Story
0096 should use interpolation, `<code>`, and `<pre><code>` for repository/user content and avoid
`[innerHTML]` and sanitizer bypasses.

`ngx-markdown` exists for project human-context pages, but its current component test explicitly does
not validate rendered Markdown sanitization (`project-context-inputs-section.spec.ts:60-69`). Plain
text is therefore the safest first-slice presentation for `contentMarkdown`. Rich Markdown rendering
is unnecessary.

Existing patterns to reuse:

* Native `<details>/<summary>` for keyboard-operable progressive disclosure
  (`analysis-detail-page.html:173-206`).
* Global focus-visible styling includes `summary` (`styles.scss:109-112`).
* `role="status"` for loading and `role="alert"` plus retry for failures.
* Semantic sections with `aria-labelledby`, headings, lists, and description lists.
* Global `.metadata-list` collapses to one column below 42rem and wraps values
  (`styles.scss:196-229`).
* Existing preformatted blocks use wrapping and bounded scrolling
  (`analysis-detail-page.scss:30-38`).

Category headings and counts should remain visible while long bodies, symbols, revision details, and
technical metadata use disclosure. Long paths, digests, hashes, signatures, annotations, and code
require `overflow-wrap:anywhere` or scrollable preformatted containers. The current local Analysis
`dl` does not collapse on mobile; reuse the global responsive metadata class or add a focused evidence
layout rather than extending the two-column assumption.

Generated proposal/Insight output begins in `AnalysisInsightsSection` and is labelled "Insight
Proposals" and "Validated Insights" (`analysis-insights-section.html:1-84`). The selected-evidence
section should appear immediately before it and state that prior selected Insights/architecture
knowledge are inputs from before this execution.

## Angular / TypeScript Learning Notes

Story 0096 naturally exercises:

* readonly interfaces and category-specific nested response types;
* discriminated unions for API availability and component loading states;
* the difference between absent, null, empty, pending, unavailable, and failed values;
* `Observable`, `switchMap`, `shareReplay`, and `AsyncPipe` for one-shot reactive loading;
* parent/child component composition and required inputs;
* Angular `@if`, `@switch`, `@for`, and stable tracking keys;
* escaped interpolation versus unsafe HTML/Markdown rendering;
* semantic sections, native disclosure controls, status/alert roles, and responsive CSS.

`switchMap` is appropriate when route/task identity changes; `exhaustMap` remains appropriate for the
existing polling loop. A manual `.subscribe()` is unnecessary.

## DTO Options

### Option A - Flat typed response with typed arrays

One response record has fixed category arrays plus selection/snapshot metadata.

| Criterion | Assessment |
|---|---|
| Type safety | Strong |
| Angular ergonomics | Simple |
| Category fidelity | Strong |
| Historical missing category | Weak unless every array has separate availability |
| Over-generalization | Low |

This option alone would tempt the mapper to turn an absent historical key into `[]`, which is
semantically false.

### Option B - Category-specific typed sections with availability

One outer response has named category sections. Each section carries availability/count and its own
typed item array; there is no generic item body.

| Criterion | Assessment |
|---|---|
| Type safety | Strong |
| Angular ergonomics | Strong with discriminated unions |
| Category fidelity | Strongest |
| Historical missing category | Explicit `NOT_RECORDED` versus recorded empty |
| Schema evolution | Additive per named category |
| Over-generalization | Low if no universal payload/interface is introduced |

**Recommended option:** B. The wrapper may share only availability/count semantics; Fact,
Observation, prior Insight, architecture knowledge, Event, human input, evolution, and repository
items remain distinct records. This is a historical selected-snapshot projection, not
`UniversalEngineeringEvidence`.

### Option C - Return normalized generic evidence or the raw map

Rejected. A generic `kind + payload` shape moves JSON interpretation into Angular, loses
category-specific semantics, and approaches the universal Evidence DTO prohibited by the Story. Raw
map rendering repeats the current auditability problem.

## Existing Test Support

### Backend

| Existing test | Reusable support |
|---|---|
| `SelectedKnowledgePromptProjectionServiceTest` | Best representative projection fixture; repository content/symbols and explicit omissions |
| `AiTaskServiceTest` | Task creation, lifecycle, deterministic repository ordering |
| `AnalysisWorkflowServiceTest` | Create -> attach -> submit ordering and same projected prompt input |
| `AnalysisDiagnosticsServiceTest` | Analysis-first context lookup, latest task, no task, null-containing maps |
| `AnalysisControllerWebMvcTest` | Analysis subresource MockMvc convention |
| `AiTaskControllerWebMvcTest` | Current broad task API contract |
| `ApiErrorHandlingWebMvcTest` | 400/404/500 error envelope and correlation behavior |

There is no shared snapshot fixture builder. The prompt projection test's inline data is the best
source for a focused future fixture. The Python `intent_fixtures.py` shape is stale in at least one
detail (`repositoryContext.usedTokens`, now omitted) and must not define the Java read contract.

PostgreSQL integration tests use Spring Boot, Testcontainers PostgreSQL 17, `@ServiceConnection`, and
often `JdbcTemplate`. A focused future integration test can insert an Analysis/task JSONB snapshot,
mutate underlying Fact/Insight/project knowledge, and prove the response still comes only from stored
JSONB. The same infrastructure can persist all-null selection columns for a legacy test and create
two projects/Analyses/tasks for association isolation.

No-recompute is best proven structurally: the read service constructor has only Analysis/task
repositories and a pure projector. Tests should not need to mock forbidden context/retrieval services
because those dependencies must not exist.

### Frontend

Angular 22 and TypeScript 6 use Vitest through Angular's unit-test builder
(`frontend/package.json:19-49`; `frontend/angular.json:77-79`). Service tests use
`HttpTestingController`; page tests use `TestBed`, mocked services, fake timers, route
`BehaviorSubject`, and direct DOM assertions (`analysis.service.spec.ts:8-49`;
`analysis-detail-page.spec.ts:77-210`).

Playwright is available but currently configures only Desktop Chrome and has no axe integration
(`playwright.config.ts:25-30`). Responsive coverage therefore needs explicit narrow viewport testing;
accessibility coverage can use semantic DOM assertions plus focused keyboard behavior, with
Playwright where useful.

## AC Traceability

| AC | Current support | Required change area | Main risk | Test location |
|---|---|---|---|---|
| AC1 | Snapshot persisted and prompt-projected | Analysis evidence service/projector | Wrong task or accidental recomputation | New service + PostgreSQL integration test |
| AC2 | Raw map currently reaches client | Typed Java DTO and typed TS model | Leaking raw map/generic DTO | New projector/controller + service model tests |
| AC3 | FK and approved Analysis-scoped latest query exist | Analysis-first lookup and scope validation | No user auth | New service/controller isolation tests |
| AC4 | Distinct persisted top-level categories | Category-specific DTO sections/component | Missing historical key treated as empty; fabricated fields | Projector + component category tests |
| AC5 | Compact repository evidence/content/symbol fields persist | Repository evidence DTO and disclosure UI | Implying omitted internals/content existed | Projector fixture + DOM tests |
| AC6 | Version/digest/metadata persisted | Selection metadata projection and labels | Digest misrepresented as snapshot checksum | Projector + UI wording tests |
| AC7 | Generated output already separate component | Place and label evidence input before output | Prior Insights mistaken for current output | Evidence/page component tests |
| AC8 | Null and empty values physically distinguishable | Explicit outer/category states | Transient null, unknown version, malformed JSON | Service state matrix + UI state tests |
| AC9 | Escaped interpolation/details/status patterns exist | Focused component/style/a11y behavior | Markdown/XSS and mobile overflow | Component security + Playwright narrow viewport |
| AC10 | Existing polling/output routes exist | Additive endpoint and one-shot load | Existing broad task polling payload | Regression suites and request-count tests |

**AC mapping: 10/10 mapped.** Human review finalized AC3's newest-task default and AC8's
`SNAPSHOT_PENDING`, `SNAPSHOT_UNAVAILABLE`, `NOT_RECORDED`, recorded-empty, and `READ_FAILURE`
semantics.

## Definition of Done Mapping

| DoD area | Concrete repository area / verification |
|---|---|
| Typed persisted-only backend projection | Analysis controller, focused evidence service/projector/DTOs |
| Category-complete Angular presentation | New evidence component plus Analysis page host/service/models/styles |
| Historical/current and input/output distinction | Evidence copy/labels and placement before generated output |
| Legacy/empty/missing/loading/error states | Backend state matrix and frontend discriminated states |
| Association isolation | Analysis-first lookup, task query by Analysis, snapshot identity checks |
| Safe rendering | Plain interpolation/pre/code and hostile-string DOM tests |
| Focused backend tests | Projector, service, controller, PostgreSQL integration |
| Focused frontend tests | Service, evidence component, Analysis host/polling regression |
| Complete quality gates | Maven verify; frontend lint/format/unit/build; applicable Playwright |
| Documentation | Story artifacts plus minimal API/UI docs if existing inventory is updated |
| Scope guard | Dependency/import review proves no retrieval/MCP/persistence changes |

**DoD mapping: COMPLETE.**

## Quality Gates for a Future Plan

Focused backend regression:

```bash
cd backend
./mvnw -Dtest=SelectedKnowledgePromptProjectionServiceTest,AiTaskServiceTest,AnalysisDiagnosticsServiceTest,AnalysisControllerWebMvcTest,AiTaskControllerWebMvcTest,AnalysisWorkflowServiceTest test
```

Full backend gate:

```bash
cd backend
./mvnw clean verify
```

The Maven lifecycle runs tests, JaCoCo XML, and an 80% bundle line-coverage check. Optional local
SonarQube is documented as:

```bash
cd backend
./mvnw clean verify sonar:sonar -Dsonar.qualitygate.wait=true
```

Frontend gates:

```bash
cd frontend
npm run lint
npm run format:check
npm test -- --watch=false
npm run build
npm run e2e
```

The exact new focused class/spec names belong in the Implementation Plan, not this artifact.

## Documentation Updates

Future implementation should update only:

* Story 0096 lifecycle artifacts;
* the API inventory/documentation if the new Analysis subresource is documented there;
* concise Analysis UI documentation if the selected-evidence distinction is described in
  `docs/ui-ux.md`.

No new ADR or broad retrieval/MCP architecture documentation is required. ADR-025 contains stale
wording that calls the current `/context` payload the exact AI-transmitted context even though current
workflow sends selected knowledge; documentation reconciliation may correct that claim without
changing architecture.

## Risks and Blockers

| Severity | Finding | Handling |
|---|---|---|
| High | No application authorization; snapshot may include source and human-authored content | Preserve existing scope, Analysis association, strict field whitelist; do not redesign auth |
| High | Current task polling already retransmits snapshots averaging 127KB plus context every 2/5 seconds | Dedicated one-shot evidence endpoint; evaluate lightweight task summary without breaking API |
| Medium | V1-V4 and same-version shape drift; missing key is not empty | Per-category availability and presence-aware parser |
| Medium | Nonterminal null snapshot can be transient | Add pending semantics or gate load on selection identity/terminal task |
| Medium | JSONB shape is unconstrained | Fail closed as read failure; do not silently degrade to empty |
| Medium | Selection digest is not a snapshot checksum | Label as opaque persisted selection digest |
| Medium | Snapshot "immutability" is application-level, not database-enforced | Use precise historical/persisted wording; no persistence scope expansion |
| Low | Long code, symbols, references, Markdown, and hashes can overwhelm mobile UI | Focused component, progressive disclosure, wrapping/scrolling |

There is no `SECURITY_BLOCKER`, `ARCHITECTURAL_CONFLICT`, or `SCOPE_CONFLICT`. Security and payload
risks are material implementation constraints but do not require violating Story boundaries.

## Out-of-Scope Confirmation

Repository analysis found no need to:

* modify `RepositoryContextEngine` or any collector/ranker/selector;
* add search, history UI, current `EngineeringContext`, or current-context retrieval;
* implement `KnowledgeReference`, `ContextPack`, RAG/vector, or a universal evidence model;
* add or alter persistence, migrate/backfill snapshots, or change snapshot creation;
* modify MCP tools/resources or make Angular call MCP;
* change prompt templates, AI provider requests, proposal validation, or ADR-006 governance;
* introduce Spring Security or a new authorization architecture.

Scope remains unchanged.

## Recommendation

The repository has a clean read seam: resolve the Analysis and project, resolve the newest associated
task without accepting a caller task ID, parse only its persisted prompt-snapshot map into
category-specific typed sections, and expose it through a dedicated Analysis subresource. Angular can
load that resource once in a focused evidence component and keep generated proposals/Insights
separate. No retrieval or context engine is involved.

Human review approved newest-task ordering only as the default Analysis-detail presentation. The
response remains task-specific and identifies that task, preserving future task-specific navigation.
Presence-aware category parsing, pending/unavailable distinctions, and fail-closed corruption
handling are approved. The Story can proceed to implementation planning without reopening
architecture.

## Readiness

**READY_FOR_IMPLEMENTATION_PLAN**

Next step: **IMPLEMENTATION_PLAN**.
