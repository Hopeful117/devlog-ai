# Repository Analysis — Story 0018

## Status

Ready for human approval.

## Executive Summary

Story 0018 is feasible by composing the existing Analysis pipeline, but the current generic launch
flow is not yet a safe product-level Project Understanding operation.

The repository already owns almost all of the required execution chain: Git workspace
synchronization, deterministic Facts and Observations, immutable Project Profiles, AnalysisContext,
ranked Repository Context, `describe-project-v1`, AI submission, proposals, diagnostics, and human
validation. The missing capability is a Core-owned application contract that validates and snapshots
one deliberate Source/revision, imports its history, creates the canonical Analysis, starts the
workflow, and applies a concurrency-safe equivalent-execution policy.

The recommended V1 is **explicitly single-source per Project Understanding execution**. This is a
product-specific scope, not a change to generic Analysis semantics. It removes the current ambiguity
where one `targetRevision` is applied to every active Source, provides durable source provenance, and
keeps later multi-source understanding as an intentional extension rather than an accidental policy.

The Angular Project Cockpit should expose one outcome-oriented action that is always available:
`Understand project` before the first successful execution and `Refresh understanding` afterward.
Both labels call the same typed backend contract. Advanced generic Analysis creation remains
available and unchanged.

## DevLog Context Outcome

The repository mapping was updated after the project was recreated:

* slug: `devlog-ai`;
* current UUID: `f3d56247-aada-4a76-982b-e6802c0b309c`.

The context adapter was then rerun with the complete Story and returned:

`DEVLOG_CONTEXT_ERROR: DevLog returned HTTP 404. Repository Analysis continues without DevLog.`

A direct request established the exact cause: `Project profile not found with identifier:
f3d56247-aada-4a76-982b-e6802c0b309c`. The project exists, but the fresh project has no Profile yet.
This is not a stale mapping. It is direct live evidence of the bootstrap gap Story 0018 addresses:
Engineering Story Context currently requires understanding that a newly created project cannot yet
produce by itself.

Per the adapter failure contract, analysis continued through targeted repository inspection. The
repository remains authoritative.

## Current Repository State

* Canonical repository: `/home/ludo/Bureau/workspace/devlog-ai`
* Branch: `main`
* The working tree contained only the new Story 0018 lifecycle directory at analysis time
* Story 0017 is implemented; project edit and permanent deletion are present in the Cockpit
* Story 0018 implementation files have not been modified

## Existing Execution Capabilities

### Generic Analysis lifecycle

`AnalysisController` exposes two separate mutations:

* `POST /api/v1/analyses` creates a `PENDING` Analysis;
* `POST /api/v1/analyses/{id}/workflow` starts it.

`AnalysisServiceImpl.create` resolves the project and Intent, snapshots Intent id/version and User
Guidance, and persists the Analysis. It does not validate Source availability, Source ownership, or
revision validity. It also has no transaction or equivalent in-flight lookup around creation.

`AnalysisWorkflowServiceImpl.start` then performs:

1. `PENDING` to `IN_PROGRESS` transition under a pessimistic Analysis-row lock;
2. deterministic knowledge collection;
3. deterministic analysis;
4. immutable Project Profile creation;
5. AnalysisContext creation;
6. AI Task creation and knowledge selection;
7. AI Engine submission and task transition.

Once start succeeds, runtime failures are translated to a traceable failed Analysis and, when an AI
Task exists, a failed task. This failure boundary should be reused rather than rebuilt.

The frontend currently composes create then start with `concatMap`. If the second HTTP request is
never made or fails before the workflow starts, a stray `PENDING` Analysis remains. `exhaustMap`
prevents repeat clicks only inside one mounted component; it does not protect multiple browser tabs,
clients, or concurrent requests.

### Canonical understanding semantics

`IntentCatalog` already registers `describe-project-v1`. Its output contract allows structured
project-presentation, architecture-description, and technology-description proposals, and it selects
`project-state-v1` plus `history-v1` context profiles. The product endpoint must resolve this Intent
through the catalog on every request; Angular must not send or recreate the Intent definition.

Only `ARCHITECTURE_REVIEW` and `PROJECT_EVOLUTION` are launchable through
`AnalysisAiTaskTypeResolver`, both as `INSIGHT_GENERATION`. No dedicated Project Understanding
Analysis type exists. Adding a new enum value would ripple through persistence, task resolution,
frontend models, filters, and compatibility without changing the underlying output. The smallest
coherent V1 should use `ARCHITECTURE_REVIEW` internally with `describe-project-v1`, while the public
contract and UI expose Project Understanding terminology. A new Analysis type is not justified by
current behavior.

### Source synchronization and revision handling

`GitWorkspaceManager` already provides the authoritative revision contract:

* clone or fetch the Source workspace;
* use an explicit branch, tag, or commit when supplied;
* otherwise resolve the Source default branch, then `origin/HEAD`;
* resolve to an exact commit;
* checkout/reset a clean detached workspace;
* serialize synchronization per Source in the current process;
* reject inactive, unpersisted, or unsupported Sources.

The manager safely validates a revision by actually resolving it. Its per-process Source lock avoids
workspace corruption but is not a database-level Project Understanding duplicate lock.

`KnowledgeCollectionServiceImpl` currently loads **all active Sources for the project** at workflow
execution time and applies the Analysis' single `targetRevision` string to every one. It stores a map
of Source UUID to resolved revision only later in execution diagnostics. `Analysis` itself contains
no `sourceId` or immutable Source set.

Consequences for Story 0018:

* a selected Source cannot currently be persisted on the Analysis;
* Sources activated or deactivated between creation and collection change execution scope;
* an explicit branch valid in one repository may fail in another;
* in-flight equivalence cannot include Source identity using current Analysis columns;
* the resulting Analysis is project-wide even if the user believed one Source was selected.

For the product-specific contract, silently inheriting this behavior would violate deliberate Source
selection and traceability.

### History import is separate

`ProjectHistoryService.importHistory(sourceId, revision)` synchronizes one Source, reads Git history,
and idempotently persists commits and changed files. It is currently reachable through a separate
HTTP endpoint and is not called by the generic Analysis workflow.

The `history-v1` context profile therefore sees only previously imported commits. A newly connected
Source may be synchronized and scanned successfully while its understanding analysis receives no Git
history. Project Understanding must compose history import inside the Core application path; Angular
must not coordinate a history-import request before creating an Analysis.

Repeated import is already idempotent by Source and commit hash. The composition still needs a clear
failure boundary and should avoid needless duplicate synchronization where a modest internal API
refactor can reuse the resolved workspace/revision.

### Profile bootstrap dependency

`ProjectProfileService.build(analysisId)` creates one immutable Profile per Analysis and
`getLatestByProject` requires at least one Profile. `AnalysisContextService` also requires the Profile
for the current Analysis.

The fresh live project reproduced the expected initial state: project lookup succeeds, while
Engineering Story Context fails because no Profile exists. A successful first Project Understanding
run naturally establishes the missing Profile. Later runs create new immutable snapshots and are
therefore suitable for refresh without a permanent initialized flag.

## Recommended Backend Boundary

Introduce a dedicated Project Understanding application service and one typed HTTP endpoint. Its
public request should contain:

* project UUID;
* selected Source UUID;
* optional target revision;
* optional User Guidance.

Intent id/version and internal Analysis type must be server-owned constants resolved through the
normal catalogs. The response should identify whether a new execution was created or an equivalent
in-flight execution was reused, and return enough Analysis identity/status information for immediate
navigation.

The service should own this sequence:

1. lock the project-level launch boundary transactionally;
2. resolve the project and the selected active Git Source by both Source and project UUID;
3. resolve `describe-project-v1` and the supported internal Analysis/task mapping;
4. normalize revision and guidance into a deterministic execution identity;
5. find and reuse an equivalent `PENDING` or `IN_PROGRESS` understanding execution;
6. validate/synchronize the requested revision and import history;
7. persist a new Analysis with immutable selected-Source provenance;
8. start the existing workflow through a product-specific source scope;
9. return the created or reused Analysis identity.

No Analysis should be created for a missing Source, ownership mismatch, unsupported Source, invalid
revision, or missing canonical Intent. Once an Analysis exists and work starts, existing failure
traceability should apply.

The exact transaction split belongs to planning because network/git and AI calls must not hold one
database transaction open. Project-level serialization is still required around equivalent lookup
and Analysis creation. A database-enforced execution key or lock strategy must close the race; a
plain `exists` query is insufficient.

## Durable Source Scope and Generic Compatibility

The recommended schema evolution is a nullable selected Source relationship or equivalent immutable
source-scope field on `Analysis`:

* Project Understanding Analyses always populate it;
* existing and advanced generic Analyses keep null, preserving their current all-active-Sources
  behavior;
* knowledge collection uses the selected Source when populated and the existing project-wide query
  otherwise;
* diagnostics continue storing resolved revisions and will contain exactly one entry for the product
  flow;
* Source deletion behavior must be reconciled with Analysis history—historical provenance should not
  disappear accidentally.

This is intentionally smaller and clearer than adding an Analysis-to-Source join model before a real
multi-source product requirement exists. It also makes the Story's in-flight identity concrete:
project, Source, normalized requested revision/default marker, canonical Intent version, and
normalized guidance.

Completed and failed executions do not block refresh. A later request may always create a new
Analysis. Only equivalent `PENDING` or `IN_PROGRESS` work is reused.

## Frontend Product Experience

`ProjectDetailPage` already loads Project, Sources, Analyses, Deliverables, and Trusted Insights into
the Cockpit. It knows whether an active Source exists and whether earlier Analyses are present. This
is the correct product surface.

The current `ProjectAnalysesSection` is an advanced generic interface. It exposes Analysis types,
Intent choices, and a two-request create/start sequence. Rebranding that form would remove advanced
functionality and still leave backend atomicity unresolved.

Add a distinct Project Understanding component or Cockpit action that:

* is visible before and after successful executions;
* says `Understand project` initially and `Refresh understanding` after a prior canonical execution;
* lists active compatible Sources, selecting the only one by default;
* requires a deliberate choice when several Sources are active;
* allows default revision or optional branch/tag/commit text;
* optionally captures existing User Guidance fields without exposing Intent/context internals;
* calls one typed Core endpoint;
* disables duplicate local submission while relying on Core for global deduplication;
* navigates to the created or reused Analysis detail;
* communicates unavailable, preparing, running, reused, and failed states through accessible status
  and alert regions.

The existing generic Analysis section remains available for expert use. Result review, diagnostics,
proposals, and human validation continue through the current Analysis detail page.

The Cockpit currently labels any prior Analysis as project understanding in some summary text. The
new component must identify prior canonical understanding executions by Intent id/version, not merely
by Analysis count or type.

## Failure and Concurrency Boundaries

The implementation plan must distinguish three phases:

1. **Precondition/preparation failure before Analysis persistence:** return a standard actionable
   error and create no Analysis.
2. **Failure after Analysis start:** retain the failed Analysis, diagnostics available so far, and AI
   Task failure when applicable; preserve all earlier Profiles, proposals, and Trusted Knowledge.
3. **Equivalent in-flight request:** return/reuse the existing Analysis deterministically rather than
   create another one or report a misleading generic error.

Source synchronization currently mutates a local cache and history import persists commits before
Analysis creation. These are safe, reusable preparation effects, but planning must make their
idempotency explicit. They must never promote proposals or alter Trusted Knowledge.

Cross-instance execution is a future operational concern, but database uniqueness/locking should be
correct even if the application later runs more than one Core instance. The in-memory workspace lock
alone cannot provide this guarantee.

## Human Validation and Trust Boundary

The existing flow produces structured proposals and does not automatically promote them. Proposal
validation remains a separate explicit user action. Project Understanding must not call validation
or Trusted Knowledge mutation services.

Earlier successful Analyses, Profiles, proposals, validations, and Trusted Insights are immutable or
independently persisted and must survive refresh failure. A refresh supplements history; it does not
replace or retract trusted understanding.

## Architectural Alignment

The recommendation preserves established boundaries:

* ADR-003 and ADR-005: Core owns deterministic analysis and AI orchestration boundaries;
* ADR-021: Sources are explicit project inputs and remain distinct from Project identity;
* ADR-025: Analysis diagnostics remain the operational trace;
* ADR-028 and ADR-029: AI consumes deterministic prepared context rather than repository access;
* ADR-040: human validation remains the Trusted Knowledge promotion boundary;
* ADR-041: passive monitoring remains a future capability, not part of this user-triggered Story;
* ADR-042: the future durable AgentJob orchestrator is not pulled into this synchronous V1.

No new ADR is required if planning implements a thin product application service over these existing
boundaries. An ADR would become necessary if planning introduces multi-source execution snapshots,
background jobs, autonomous scheduling, or changes the trust/promotion model.

## Affected Areas

### Backend

* a new Project Understanding controller/request/response/application service package;
* `Analysis` persistence and Flyway migration for durable selected-Source/execution identity;
* `AnalysisRepository` concurrency-safe in-flight lookup/locking support;
* `AnalysisService` creation seam or internal factory reuse;
* `KnowledgeCollectionServiceImpl` selected-source behavior while retaining generic fallback;
* `ProjectHistoryService` internal composition seam;
* standard conflict/validation error mapping where needed;
* focused application, repository, workflow, WebMvc, and PostgreSQL integration tests.

### Frontend

* typed Project Understanding models and service;
* a dedicated Cockpit component/action and styles;
* `ProjectDetailPage` integration and canonical-execution detection;
* focused service and component tests;
* no prompt, Intent, context-profile, or multi-request orchestration in Angular.

### Documentation

* `README.md` and API/product documentation;
* `docs/ui-ux.md` for the always-available outcome-oriented action;
* `docs/architecture.md` to distinguish explicit initialization/refresh from future passive
  monitoring where wording currently implies automatic first connection;
* `docs/roadmap.md` to mark repository bootstrap understanding implemented while retaining later
  passive evolution work;
* Story lifecycle artifacts.

## Required Testing Strategy

Planning must include:

* application-service tests for first execution, later refresh, canonical Intent ownership, Source
  ownership, inactive/unsupported Source, default and explicit revision, and failure boundaries;
* concurrency tests proving equivalent pending/running requests create at most one Analysis;
* tests proving completed/failed or materially different requests may create a later Analysis;
* collection tests proving selected-source execution scans only that Source and generic null-scope
  Analysis still scans all active Sources;
* history tests proving first execution imports history and refresh remains idempotent;
* workflow tests preserving failed Analysis/task traceability and proposal-validation separation;
* PostgreSQL-backed schema tests for uniqueness/locking and Source provenance deletion semantics;
* Angular HTTP tests for the single endpoint and typed response;
* Angular component tests for first/refresh labels, one/many/no active Sources, revision input,
  duplicate clicks, reused execution navigation, status/alert accessibility, and retry;
* regression tests for generic Analysis creation/launch and Project Cockpit CRUD;
* complete backend verification, frontend tests/build/lint, JaCoCo, authenticated SonarQube Quality
  Gate wait, and local Docker/API/UI validation.

Mock-only tests are insufficient for the concurrency guarantee.

## Risks and Mitigations

1. **A product request analyzes the wrong repository.** Persist and enforce one Source belonging to
   the Project; collection uses that snapshot.
2. **One revision is applied to unrelated repositories.** Keep Project Understanding single-source;
   leave generic multi-source behavior unchanged.
3. **History context is empty on first run.** Compose idempotent history import inside Core before
   knowledge selection.
4. **Create/start separation leaves orphaned PENDING Analyses.** Expose one product application
   contract and make Core own the sequence.
5. **Concurrent clients create equivalent work.** Use a database-backed lock/unique execution
   identity, not only frontend or in-memory guards.
6. **Long Git/AI work holds database locks.** Separate short transactional claim/create phases from
   external work while preserving the claimed execution identity.
7. **Source deletion erases historical provenance.** Define foreign-key behavior explicitly and test
   it against the Story 0017 project deletion cascade.
8. **The UI hides the generic expert workflow.** Add a dedicated product component and retain the
   current Analysis section.
9. **Refresh overwrites trusted state.** Preserve immutable historical records and keep validation
   explicit.
10. **Fresh projects cannot use context-dependent tools.** Make the first understanding run create
    the Profile required by Engineering Story Context; keep missing-Profile errors actionable until
    it completes.

## Open Questions Resolved for Planning

* **Automatic on first connection?** No. The capability is explicitly requested and always remains
  available.
* **First run versus refresh implementation?** One endpoint, service, validation path, and lifecycle;
  only presentation differs.
* **Single or multiple Sources in V1?** One explicitly selected active Git Source per execution.
* **Who chooses the Intent?** Core always resolves `describe-project-v1`; the client does not choose.
* **Which Analysis type?** Reuse internal `ARCHITECTURE_REVIEW` in V1; do not add an enum solely for
  presentation.
* **Who imports history?** The Core product workflow, idempotently; never Angular.
* **What happens to equivalent work?** Reuse and navigate to the existing pending/running Analysis.
* **Can refresh run after completion or failure?** Yes; terminal executions never block a new one.
* **Are proposals trusted automatically?** No; existing human validation remains mandatory.
* **Does this implement passive monitoring or AgentJob?** No.

## Recommendation

Approve this Repository Analysis and proceed to Implementation Planning.

Planning should introduce one Core-owned, single-source Project Understanding contract over the
existing workflow, persist its Source/execution identity, import history before context selection,
and enforce concurrency-safe in-flight reuse. The Cockpit should expose the same action before and
after first success, while generic Analysis controls remain intact.

No implementation file should be modified before explicit human approval of this analysis and the
subsequent Implementation Plan.
