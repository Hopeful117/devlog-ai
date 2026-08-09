# Repository Analysis — Story 0022

## Status

Ready for Human Approval Gate 1.

## Executive conclusion

Story 0022 is the correct first long-term memory increment, but its implementation must preserve a
domain distinction that the initial Story wording deliberately left for analysis:

* `KnowledgeEvent` is an imported or manually recorded **raw occurrence**;
* `EngineeringEvent` must be a **human-validated interpretation of a significant evolution**.

ADR-016 explicitly requires these layers to remain distinct. The existing `KnowledgeEvent` table
must therefore remain readable and must not be mutated into the new trusted object. A dedicated
immutable `EngineeringEvent` domain is not a competing duplicate: it is the higher validated layer
already anticipated by ADR-006, ADR-035, and ADR-036.

The smallest coherent revision boundary is one persisted non-root Git commit and its first parent.
The repository already imports commit chronology and changed-file metadata and already constructs a
bounded `CommitDiffAnalysisContext` for exactly this boundary. Story 0022 should activate that
dormant context through an explicit `analyze-engineering-event-v1` Intent. Arbitrary commit ranges,
root commits, multi-commit grouping, semantic patch parsing, and passive detection should remain
future increments.

This vertical slice is feasible, but it crosses Core, AI Engine, PostgreSQL, Angular, and the
agent-context projection. It also requires a new ADR because the current Intent contract is
Insight-specific while the accepted proposal architecture is intentionally generic.

## Repository and workflow state

* Canonical repository: `/home/ludo/Bureau/workspace/devlog-ai`.
* Branch: `main`.
* Baseline at analysis start: `2e6c71e` (`deterministic project freshness`), synchronized with
  `origin/main`.
* Initial worktree: clean.
* Story directory created by this workflow:
  `docs/stories/0022-validated-engineering-event/`.
* DevLog mapping resolved exactly to Project `f3d56247-aada-4a76-982b-e6802c0b309c`.
* Live DevLog contains no `KnowledgeEvent` and no legacy `Decision` for this Project, so no real
  data proves a migration need. Compatibility must nevertheless be preserved for other databases.
* The six real proposals from Analysis `bd71ca14-88fa-4028-b3f9-91365d931b44` remain pending and
  are outside this Story's mutation authority.

## DevLog context outcome

The body-based DevLog adapter completed successfully. It returned 31 evidence items from 124
candidates with the compact-projection and repository-budget warnings expected for a large Story.
Its strongest useful evidence was historical rather than implementation-specific:

* commit evidence for the Repository Context and evidence-selection increments;
* changed-file evidence for repository ranking, enrichment, tests, and roadmap evolution;
* Git provenance rooted in Source `7819103b-37e7-4e15-95ec-fff9a12d21e4`.

The context did not select the promotion, validation, Intent, KnowledgeEvent, or AI Engine files
needed to decide exact behavior. Targeted repository reads were therefore authoritative for the
analysis below. This is a healthy example of DevLog providing navigation evidence without replacing
repository inspection.

## Existing architecture

### 1. Proposal and validation governance is already generic

`ValidatableProposal` persists `ProposalType`, JSON payload, confidence, Fact/Observation IDs,
repository evidence references, source order, and decision timestamps. `ProposalType` already
contains `ENGINEERING_EVENT`, `ENGINEERING_DECISION`, `CHALLENGE`, and `DOCUMENTATION` in addition
to `INSIGHT`.

`ValidationServiceImpl` obtains a pessimistic proposal lock through
`findByIdForValidation`, rejects already-decided proposals, writes one `Validation`, changes the
proposal state, and invokes knowledge promotion inside one transaction. The database already has a
unique Validation per proposal.

Evidence:

* `backend/src/main/java/com/hopeful117/devlogai/proposal/entity/ValidatableProposal.java`
* `backend/src/main/java/com/hopeful117/devlogai/proposal/entity/ProposalType.java`
* `backend/src/main/java/com/hopeful117/devlogai/validation/service/ValidationServiceImpl.java`
* `backend/src/main/resources/db/migration/V10__create_validations_table.sql`
* ADR-006 and ADR-004.

### 2. Promotion is implemented only for Insights

`InsightPromotionService.promote` immediately returns for every proposal type except `INSIGHT`.
Consequently, if an Engineering Event proposal reached the current validation endpoint, accepting
it would persist an accepted proposal and Validation but create no trusted event. This is a dormant
violation of ADR-006's atomic-promotion rule.

The same behavior affects other declared but unimplemented proposal types. Story 0022 should
replace the implicit no-op with explicit proposal-type dispatch:

* `INSIGHT` → existing Insight promotion;
* `ENGINEERING_EVENT` → new Engineering Event promotion;
* every still-unsupported type → rejection before any decision is persisted.

This preserves existing Insight behavior while preventing false successful acceptance.

Evidence:

* `backend/src/main/java/com/hopeful117/devlogai/insight/service/InsightPromotionService.java`
* `backend/src/main/java/com/hopeful117/devlogai/validation/service/ValidationServiceImpl.java`
* `backend/src/test/java/com/hopeful117/devlogai/insight/service/InsightPromotionServiceTest.java`.

### 3. The current Intent and AI path is Insight-specific

`IntentCatalog` exposes only `describe-project-v1`, `generate-readme-v1`, and
`architecture-overview-v1`. `IntentDefinition` describes `supportedInsightTypes`; its output schema
is fixed around Insight payload fields. The AI Engine mirrors this contract, and
`InsightGenerationService` always emits `ProposalType.INSIGHT`.

Although `AiTaskType.EVENT_PROPOSAL_GENERATION` exists, the Core currently maps
`PROJECT_EVOLUTION` to `INSIGHT_GENERATION`, while the AI Engine accepts only
`INSIGHT_GENERATION`. The Event task type is therefore a placeholder, not an implemented path.

Story 0022 needs a provider-independent generalization of the Intent boundary, not an event encoded
as an `InsightType.EVOLUTION`. Reusing the Insight schema would lose the trusted-domain distinction
and make later Decision/Challenge support harder.

Recommended contract direction:

* add an explicit output proposal type or supported proposal types to the versioned Intent;
* preserve the current `supportedInsightTypes` semantics for existing Intent versions;
* define a separate versioned Engineering Event payload schema and prompt builder;
* route `PROJECT_EVOLUTION` to `EVENT_PROPOSAL_GENERATION` only for the dedicated event execution;
* add Event processing to the AI Engine's supported task set without changing existing builders.

Because ADR-028 defines Intent specifically in terms of Insights, this generalization requires a new
ADR rather than a silent reinterpretation.

Evidence:

* `backend/src/main/java/com/hopeful117/devlogai/intent/service/IntentCatalog.java`
* `backend/src/main/java/com/hopeful117/devlogai/intent/model/IntentDefinition.java`
* `backend/src/main/java/com/hopeful117/devlogai/analysis/workflow/AnalysisAiTaskTypeResolver.java`
* `backend/src/main/java/com/hopeful117/devlogai/ai/task/entity/AiTaskType.java`
* `ai-engine/app/prompts/insight.py`
* `ai-engine/app/services/insight_generation_service.py`
* `ai-engine/app/services/task_processing_service.py`
* `ai-engine/app/api/ai_tasks.py`
* ADR-028, ADR-030, and ADR-031.

### 4. The exact bounded historical evidence already exists

Story 0017-era history foundations persist commits, ordered parents, changed files, author/time,
messages, and diff statistics per Source. `CommitDiffContextBuilder` builds a deterministic bounded
projection for one commit against its first parent, including:

* complete commit and parent hashes;
* root/merge flags;
* commit message and time;
* bounded changed-file metadata;
* languages and source/test/configuration/documentation categories;
* binary/generated exclusions;
* ADR and roadmap candidates;
* stable `git:<source>:<commit>:<path>` evidence references;
* truncation and merge/root warnings.

Its class comment explicitly calls it evidence for a future versioned Intent. This is the strongest
existing seam for Story 0022.

Limits are important: the current history model contains file paths and line statistics, not patch
hunks or changed-symbol semantics. The initial event Intent may describe only what those bounded
inputs support. It must not claim method behavior, causality, motivation, or architectural impact
from filenames alone.

Evidence:

* `backend/src/main/java/com/hopeful117/devlogai/history/entity/ProjectCommit.java`
* `backend/src/main/java/com/hopeful117/devlogai/history/entity/ChangedFile.java`
* `backend/src/main/java/com/hopeful117/devlogai/history/context/CommitDiffAnalysisContext.java`
* `backend/src/main/java/com/hopeful117/devlogai/history/context/CommitDiffContextBuilder.java`
* `backend/src/main/java/com/hopeful117/devlogai/history/service/ProjectHistoryServiceImpl.java`
* ADR-035 and ADR-036.

### 5. The current Analysis contract does not preserve an evolution boundary

`Analysis` has one optional `targetRevision`, selected Source, Source snapshot, and generic type.
`targetRevision` is the requested revision string; it is not guaranteed to be a complete resolved
commit and there is no persisted base revision. `AnalysisContext.AnalysisSnapshot` omits Source and
revision fields entirely.

Generic Analysis creation also has no Source ID. Only the dedicated Project Understanding flow
performs Source ownership validation, synchronization, history import, immutable Source snapshot,
concurrency-safe claim, and workflow launch.

Story 0022 should follow that dedicated application-flow pattern rather than expand the generic
`POST /analyses` contract. A Project-scoped explicit endpoint should accept Source ID and target
commit, then:

1. validate Project-owned active Git Source;
2. synchronize and resolve the requested target to a complete commit;
3. import history once through the synchronized workspace;
4. require the persisted target commit and exactly one first parent;
5. persist an immutable evolution scope containing Source, base, target, merge policy, and context
   version;
6. claim/deduplicate equivalent active executions;
7. launch the normal Analysis workflow.

Root commits should be rejected in v1 because they have no comparison boundary. Merge commits may
use the already documented first-parent policy but must remain explicit in the scope and warnings.
Arbitrary multi-commit ranges and grouping are deferred.

Evidence:

* `backend/src/main/java/com/hopeful117/devlogai/analysis/entity/Analysis.java`
* `backend/src/main/java/com/hopeful117/devlogai/analysis/dto/request/CreateAnalysisRequest.java`
* `backend/src/main/java/com/hopeful117/devlogai/projectunderstanding/ProjectUnderstandingPreparationService.java`
* `backend/src/main/java/com/hopeful117/devlogai/projectunderstanding/ProjectUnderstandingClaimService.java`
* `backend/src/main/java/com/hopeful117/devlogai/collection/service/KnowledgeCollectionServiceImpl.java`.

### 6. Existing selection does not carry the commit-diff projection

`AnalysisContextServiceImpl` treats `PROJECT_EVOLUTION` specially only by adding related Analyses and
Milestones. `SelectedKnowledge` contains Project/Profile, selected Facts and Observations,
validated Insights, and Repository Context, but no explicit evolution scope or
`CommitDiffAnalysisContext`.

Repository Context can expose commit and changed-file evidence, but that is selected from broad
Project history and is not a sufficient authoritative statement of the exact requested comparison.
The immutable evolution scope and its bounded commit context must be a dedicated selected-knowledge
section included in digest and prompt accounting. Generic Repository Context remains complementary.

The Core callback validates Fact and Observation ownership, while repository evidence-reference
subset validation currently occurs only in Python. For Engineering Events, Core must revalidate the
payload contract and every evidence reference against the persisted selected-knowledge snapshot
before saving proposals. Python validation remains defense in depth, not authority.

Evidence:

* `backend/src/main/java/com/hopeful117/devlogai/analysis/context/AnalysisContextServiceImpl.java`
* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/SelectedKnowledge.java`
* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceImpl.java`
* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiTaskResultServiceImpl.java`
* `ai-engine/app/services/insight_generation_service.py`.

### 7. KnowledgeEvent must remain the lower raw layer

The existing `KnowledgeEvent` has Project, title, description, type, and mutable audit timestamps.
It is created directly through `POST /api/v1/knowledge-events`, has no Analysis/proposal/Validation,
Source, revision, or evidence provenance, and is described by ADR-016 as source material for Facts
and Observations.

It cannot satisfy Story 0022 by adding proposal links because that would mix unvalidated input and
trusted interpretation in one table. Recommended model:

```text
ProjectCommit / KnowledgeEvent (raw evidence)
                 ↓
       bounded deterministic context
                 ↓
      EngineeringEventProposal
                 ↓ human Validation
        EngineeringEvent (trusted)
```

The new `EngineeringEvent` should be immutable and reference exactly one Project, Analysis,
proposal, Validation, Source, base revision, and target revision. Its payload should carry a stable
category, title, summary, and significance rationale. Supporting Fact/Observation/repository
evidence remains on the immutable proposal and is exposed through the event projection instead of
being copied into an unconstrained second JSON blob.

No migration of legacy `knowledge_events` rows is semantically valid because their human-validated
meaning and provenance cannot be fabricated. Keep the table/API unchanged and clarify naming in
documentation and UI.

Evidence:

* `backend/src/main/java/com/hopeful117/devlogai/knowledge/entity/KnowledgeEvent.java`
* `backend/src/main/java/com/hopeful117/devlogai/knowledge/service/KnowledgeEventServiceImpl.java`
* `backend/src/main/java/com/hopeful117/devlogai/knowledge/controller/KnowledgeEventController.java`
* `backend/src/main/resources/db/migration/V2__create_knowledge_events_table.sql`
* ADR-016 and ADR-040.

### 8. Review UI is reusable but currently assumes Insights

The guided review workspace already renders a generic payload and bounded evidence, and it already
supports every `ProposalType` in its data contract. However, its labels default to “Insight
proposal”, always show Insight severity, and expose only `resultingInsight` after acceptance.

The review projection and Angular model need an additive resulting Engineering Event link. Severity
must be required and shown only for accepted Insight proposals. Event category and revision boundary
should be rendered explicitly; raw JSON remains an audit fallback. Existing pagination,
confirmation, reviewer session, concurrency refresh, and per-proposal decision flow should be
reused unchanged.

The cockpit has no Engineering Event section. A bounded recent-events list and link to a stable
Project events route is sufficient for this Story; a timeline or relationship graph is premature.

Evidence:

* `backend/src/main/java/com/hopeful117/devlogai/proposal/review/ProposalReviewResponse.java`
* `backend/src/main/java/com/hopeful117/devlogai/proposal/review/ProposalReviewService.java`
* `frontend/src/app/features/insights/proposal-review-page.ts`
* `frontend/src/app/features/insights/proposal-review-page.html`
* `frontend/src/app/features/projects/project-detail-page.ts`.

## Recommended Story boundary

### In scope for implementation planning

* One explicit Source-scoped execution for one persisted non-root target commit.
* First-parent base derivation, including an explicit merge warning/policy.
* Immutable persisted evolution scope and bounded `CommitDiffAnalysisContext` in selected knowledge.
* `analyze-engineering-event-v1` Intent and a versioned event proposal schema.
* Dedicated Event prompt builder/generation service and task routing.
* Core-authoritative payload and evidence validation.
* Atomic type-dispatched promotion into a new immutable `EngineeringEvent` entity.
* Stable Project/event read APIs and additive review-result projection.
* Minimal Angular launch, review, and recent-event/detail surfaces.
* Bounded validated-event inclusion in Project/Engineering Story Context and selection digest.
* A new ADR generalizing Intent output beyond Insights and fixing the Event domain boundary.

### Explicitly deferred

* Arbitrary revision ranges or multi-commit event grouping.
* Root-commit interpretation.
* Patch hunks, changed-symbol extraction, dependency semantic diffs, or causal inference.
* Event editing, deletion, supersession, relationships, or timeline visualization.
* Decision and Challenge promotion.
* Passive monitoring, significance prefiltering, schedulers, webhooks, or AgentJobs.
* Automatic refresh, analysis, validation, or documentation generation.

## Proposed event taxonomy

Use a new semantic enum aligned with the product's validated V1 Engineering Event taxonomy rather
than reusing raw `KnowledgeEventType`:

* `FEATURE_INTRODUCTION`
* `BUG_RESOLUTION`
* `ARCHITECTURE_CHANGE`
* `TECHNOLOGY_CHANGE`
* `ENGINEERING_IMPROVEMENT`
* `INFRASTRUCTURE_CHANGE`

The schema should reject a generic `OTHER` category: the provider may return zero proposals when
evidence does not support one of the governed meaningful categories. This is safer than preserving
low-value interpretations merely to force output.

## Main implementation risks

### High — Story size and cross-service contract evolution

The vertical slice affects five runtime boundaries. Planning must separate contract-first changes,
Core persistence/promotion, AI processing, and UI integration while keeping one deployable slice.
Avoid opportunistic refactors of all Intent or proposal code.

### High — unsupported semantic claims from weak diff evidence

The current commit context has paths and statistics but no patch content. Prompt/schema language and
review UI must state this limitation. A zero-proposal result is valid. Representative validation
should use a commit whose message, changed paths, ADR/story files, and deterministic repository
evidence jointly support the event.

### High — false acceptance without promoted knowledge

Promotion dispatch must run before committing proposal state. Unsupported proposal types must fail
atomically. Database uniqueness on event proposal and Validation references must defend against
concurrency after the existing proposal lock.

### Medium — duplicated repository synchronization

The dedicated preparation flow will synchronize/import history before Analysis creation, while the
normal collection workflow synchronizes again. Planning should either pass an immutable resolved
scope safely or accept and measure the duplicate operation; it must not weaken workspace locking or
hold database transactions during Git/network work.

### Medium — selected-knowledge compatibility

Adding evolution context and validated events changes canonical digest input and the Python request
schema. The extension must be versioned/additive and historical AiTask snapshots must remain
readable. Existing Intent prompt digests will naturally differ only for new executions if the
canonical snapshot changes; this needs explicit compatibility tests.

### Medium — event visibility versus context budgets

Validated events are trusted memory but must not crowd out repository evidence or existing Insights.
Use a small deterministic limit and explicit accounting rather than unbounded Project history.

### Low — legacy KnowledgeEvent ambiguity

The names are close enough to confuse users and developers. Documentation and UI copy must call the
legacy object a raw/manual Project occurrence and the new object a validated Engineering Event.
Renaming tables/classes is unnecessary and risky in this Story.

## Validation strategy required by the plan

* Core unit tests for evolution scope validation, first-parent derivation, Intent resolution,
  payload validation, evidence-subset validation, dispatch, rollback, unsupported types, taxonomy,
  mapping, ordering, and bounded selection.
* PostgreSQL integration tests for migration, foreign keys, Project deletion, uniqueness, concurrent
  validation, and exactly-one promoted Event.
* AI Engine tests for task acceptance, prompt identity, structured schema, allowed categories,
  reference grounding, corrective retry, failure callback, and deterministic mock output.
* Angular tests for explicit launch, generic/event review labels, conditional severity, confirmation,
  resulting-event navigation, empty/loading/error states, keyboard focus, and narrow layout.
* Regression tests for current Insight generation/promotion, proposal review, Deliverables, Project
  Understanding, freshness, and Engineering Story Context digests.
* Full Maven/JaCoCo, Python, Angular/build/format, Docker, migration, API/UI, repository hygiene, and
  authenticated SonarQube Quality Gate validation.
* Live validation should use a disposable DevLog Project connected to the existing public repository
  and a deliberately chosen non-root commit. The deterministic Mock provider must receive a
  Story-owned event fixture or the configured OpenAI provider may be used only if its credentials are
  already authorized. Real pending DevLog proposals must remain untouched.

## Documentation impact

Implementation will materially change the trusted knowledge pipeline and needs reconciliation of:

* `README.md` current capabilities and workflow;
* `docs/architecture.md` and `docs/pipeline.md`;
* `docs/knowledge-model.md` and `docs/data-model.md`;
* `docs/ui-ux.md`;
* `docs/roadmap.md`;
* AI Engine and frontend manuals where the new execution is exposed;
* a new ADR for proposal-type-aware Intent output and validated Engineering Event ownership.

No documentation should claim passive monitoring, semantic patch understanding, Decisions,
Challenges, or continuously updated events.

## Gate 1 recommendation

Approve Repository Analysis with the following implementation-planning decisions fixed:

1. one non-root target commit per Analysis, base derived from its first parent;
2. a new immutable `EngineeringEvent`, while legacy `KnowledgeEvent` remains the raw layer;
3. a dedicated event Intent/task/schema rather than encoding events as Insights;
4. Core-authoritative payload/evidence validation and atomic type-dispatched promotion;
5. no arbitrary ranges, grouping, patch semantics, Decisions, Challenges, or passive execution.

After explicit approval, Implementation Planning may define the concrete API, persistence schema,
compatibility/versioning strategy, execution-key semantics, event projection, test fixtures, and
ordered delivery plan.
