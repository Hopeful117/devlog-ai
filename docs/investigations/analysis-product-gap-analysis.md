# Investigation: Analysis Product Gap Analysis

Investigation-only artifact. No production code, test, benchmark harness, ADR, or Engineering Story
was created or modified.

## Status

**INVESTIGATION_COMPLETE — AWAITING_HUMAN_REVIEW**

## Scope

This investigation starts from the current human-facing Angular workflow and traces the Analysis
feature through Core, persistence, knowledge selection, the Python AI Engine, callback persistence,
proposal validation, and the final Angular presentation.

Story 0098 and CATEGORY_SELECTION remain known technical work. They were not implemented and are
re-prioritized below against product-flow and Analysis-contract gaps. Future Engineering Query is
explicitly out of scope.

## Executive Conclusion

**A human can run a bounded, intent-governed Analysis and review grounded proposals today, but the
feature is not yet a coherent Analysis product.**

The end-to-end machinery works: deterministic collection, context construction, knowledge
selection, immutable AiTask snapshots, grounded structured generation, callback persistence,
individual proposal review, and trusted promotion are all implemented. The primary problem is not
that the chain is absent. The problem is that its product boundary is unclear at both ends:

1. The launch form exposes internal `AnalysisType`, Intent IDs, versions, prompt-template names, and
   all catalog entries even though `AnalysisType` barely affects execution and one exposed Intent is
   guaranteed to be rejected by generic Analysis creation.
2. The completed page has no canonical human-facing Analysis result. It leads with diagnostics,
   pipeline stages, AiTask metadata, hashes, UUIDs, evidence internals, and raw context JSON.
   Generated proposals are embedded inside the AI execution panel rather than presented as the
   Analysis outcome.
3. The AI contract intentionally produces bounded `ValidatableProposal[]`, not an Analysis report.
   This is consistent with ADR-006 and ADR-028, but the UI does not turn those proposals and their
   validation state into an outcome-oriented Analysis result.
4. Evidence is inspectable but fragmented. The review workspace shows rationale and bounded Facts /
   Observations, while the selected-evidence view shows the full execution snapshot. References are
   mostly strings and cannot be followed to canonical source views.
5. Accepted Insights and Engineering Events are visible, but accepted Engineering Decisions have no
   equivalent Angular destination or review-result link.

Therefore a perfect Story 0098 category ceiling would improve one context-quality layer but would
not make Analysis a coherent product. CATEGORY_SELECTION should remain preserved and measured, but
be deferred until the human launch/result flow is functional and benchmarkable.

---

## 1. ANALYSIS_PRODUCT_INTENT

### Intended product behavior

Repository artifacts define Analysis as a **human-triggered, intent-governed knowledge-production
workflow**, not open-ended engineering Q&A:

- DevLog's primary goal is living, trustworthy technical memory (`docs/vision.md`,
  `docs/roadmap.md`).
- `Analysis` is the persisted deterministic historical snapshot boundary (ADR-007).
- `IntentDefinition` owns the versioned objective, allowed output categories, constraints, output
  schema, prompt template, and context profiles (ADR-028).
- `UserGuidance` may refine focus, audience, detail, style, output context, and priorities, but is
  subordinate to Intent and cannot replace it (ADR-030).
- `AiTask` records a specialized probabilistic execution and its immutable input snapshots
  (ADR-017).
- AI output remains `ValidatableProposal`; it does not become trusted knowledge without an explicit
  individual human decision (ADR-006, ADR-020).
- Acceptance atomically creates the supported trusted artifact and marks the proposal accepted.
  Rejection creates no trusted artifact (ADR-006).
- Documentation is downstream of validated knowledge rather than the Analysis result itself
  (`docs/outputs.md`, ADR-063).
- Story 0096 makes the exact selected evidence supplied to an AiTask inspectable by a human, but
  explicitly does not define an Analysis-wide canonical evidence set.

The intended lifecycle is:

```text
human chooses a supported objective and scope
  -> Analysis captures deterministic project state
  -> AiTask receives selected grounded knowledge
  -> AI produces structured proposals
  -> human inspects rationale and evidence
  -> human accepts or rejects each proposal
  -> accepted proposal becomes trusted Insight / EngineeringEvent / Decision
  -> downstream knowledge and documentation can consume it
```

`docs/ui-ux.md` states that internal concepts remain implementation details and that the human flow
should read as `Launch Analysis -> Results -> Validate -> Knowledge Updated`, communicating outcomes
rather than implementation steps (`docs/ui-ux.md:291-339`).

### Current implemented behavior

The internal lifecycle is implemented, but the UI exposes it directly. Analysis currently behaves as
an execution/audit console plus proposal queue, not as an outcome-oriented product surface.

The current registered Intents are:

| Intent | Mode | Output | Context profiles | Actual generic UI exposure |
|---|---|---|---|---|
| `describe-project-v1` | GENERIC | INSIGHT | project-state, history | Exposed; executable |
| `generate-readme-v1` | GENERIC | INSIGHT | documentation, project-state | Exposed; executable; proposes README information, not a README |
| `architecture-overview-v1` | GENERIC | INSIGHT | architecture, history | Exposed; executable |
| `analyze-engineering-event-v1` | DEDICATED_ENGINEERING_EVENT | ENGINEERING_EVENT | history, project-state | Exposed but generic creation rejects it |
| `analyze-engineering-decision-v1` | GENERIC | ENGINEERING_DECISION | history, project-state | Exposed; executable |

The generic UI exposes `ARCHITECTURE_REVIEW` and `PROJECT_EVOLUTION`. Core only checks that the type
is one of these values before resolving the AiTask from the Intent's proposal type
(`AnalysisAiTaskTypeResolver.java:12-28`). No type/Intent compatibility matrix exists, and
`AnalysisType` does not select the prompt, output schema, context profiles, or scoring policy.

---

## 2. CURRENT_USER_JOURNEY

### Discovery and project selection

There is no global Analysis navigation item. The user first chooses a Project, then discovers
Analysis through the project Activity workspace, project cockpit recent activity, project
understanding, repository freshness, or engineering-event execution.

The generic form has no project selector; it inherits `projectId` from the current project route.
The form also has no source selector, although source choice is available in dedicated Project
Understanding and Engineering Event workflows.

### Generic launch

The form (`frontend/src/app/features/analyses/analysis-form.html`) asks the human to:

1. choose raw `AnalysisType` enum value;
2. choose a catalog Intent displayed as ID/version/objective;
3. optionally enter a branch, tag, or SHA;
4. optionally enter all six User Guidance fields;
5. submit `POST /api/v1/analyses`;
6. wait for `POST /api/v1/analyses/{id}/workflow` to complete synchronous preparation;
7. navigate to `/analyses/{id}` only after preparation and AI Engine submission succeed.

The form also displays the prompt-template identifier and supported Insight types. These are useful
for diagnostics but contradict the documented goal that Intent, Prompt, AiTask, and collector
mechanics remain internal.

The user cannot submit an arbitrary engineering objective. `focus` and `priorities` refine one fixed
Intent. This is correct for current Analysis and is not an Engineering Query substitute.

### Progress and failures

During synchronous preparation, the project page only says that context is being prepared and the
Analysis launched. The Analysis cannot be opened until the workflow call returns.

On the detail page, Angular independently polls diagnostics and AiTasks until terminal state. It
shows Analysis, collection, profile, AiTask, provider, model, pipeline, warning, and failure data.

Important inconsistencies:

- the top Analysis header is loaded once and may continue to say `IN_PROGRESS` / `Not completed`
  after the independently polled diagnostics show completion;
- warnings, profile, and raw context are one-shot reads and may be loaded before final state;
- if Analysis creation succeeds but workflow preparation fails, the launcher shows a generic error
  but does not refresh the list or navigate to the persisted failed Analysis;
- there is no retry/relaunch lifecycle for a failed Analysis, consistent with current immutable-run
  semantics but without a clear user recovery action.

### Completed state

The completed Analysis page presents, in order:

1. raw Analysis type, UUID, status, Intent ID/version, and timestamps;
2. diagnostics, revisions as JSON, counts, collector state, and pipeline stages;
3. AiTask status, provider/model, selection/prompt/context digests, correlation ID, attempts, and
   guidance JSON;
4. the complete selected-evidence snapshot;
5. proposal cards and accepted Insights;
6. brief Project Profile summary;
7. warnings;
8. full AnalysisContext as raw JSON.

There is no dedicated Results state or synthesis. The proposal section shows title, type,
confidence, summary, counts, and a Review link. The richer rationale and resolved supporting Facts /
Observations appear only after entering the separate proposal-review workspace.

### History and revisit

Analyses can be revisited from the project Activity list, cockpit recent activity, freshness
baseline, Insight lineage, Proposal lineage, or Engineering Event lineage. There is no global
Analysis history, search, paging, comparison, or side-by-side revision/result comparison. The detail
page returns to `/projects` rather than its originating project workspace.

---

## 3. ANGULAR_TO_REST_TRACE

| Human action | Angular call | HTTP path | Core path / resulting state |
|---|---|---|---|
| Load launch form | `IntentCatalogService.getSupportedIntents()` | `GET /api/v1/intents` | Returns all Intents, including dedicated event Intent |
| Create run | `AnalysisService.createAnalysis()` | `POST /api/v1/analyses` | `AnalysisService.create()` persists PENDING Analysis |
| Launch | `AnalysisService.launchAnalysis()` | `POST /api/v1/analyses/{id}/workflow` | Full collection/selection/submission workflow; returns task IDs |
| Open run | `getAnalysis()` | `GET /api/v1/analyses/{id}` | One-shot Analysis metadata |
| Monitor pipeline | `getDiagnostics()` | `GET /api/v1/analyses/{id}/diagnostics` | Polled until Analysis terminal |
| Monitor AI | `getAiTasksByAnalysis()` | `GET /api/v1/ai-tasks/analysis/{id}` | Polled until newest AiTask terminal |
| Inspect model input | `getSelectedEvidence()` | `GET /api/v1/analyses/{id}/selected-evidence` | Story 0096 historical selectedKnowledge projection |
| Inspect warnings | `getWarnings()` | `GET /api/v1/analyses/{id}/warnings` | One-shot collection warnings |
| Inspect context | `getContext()` | `GET /api/v1/analyses/{id}/context` | One-shot raw context snapshot |
| Inspect profile | `getProfile()` | `GET /api/v1/analyses/{id}/profile` | One-shot profile |
| List outcomes | `getByAnalysis()` | `GET /api/v1/proposals/analysis/{id}` | Raw proposal cards |
| Review outcomes | `getReview()` | `GET /api/v1/analyses/{id}/proposal-review` | Enriched paged proposal/evidence/decision projection |
| Decide | `validateProposal()` | `POST /api/v1/validations` | Immutable decision and atomic promotion |
| Show accepted Insight | `InsightService.getByAnalysis()` | `GET /api/v1/insights/analysis/{id}` | Lists promoted Insights |

Existing Core capabilities unused or incompletely used by Angular include Analysis filters by status
and type, proposal lists by project, direct AiTask/correlation reads, Decision reads, promoted
Decision navigation, diagnostic links, and richer profile content.

Angular expects the generic Intent list to be launchable, but the backend contract returns no
Angular-modeled `executionMode` or product launch compatibility. The backend already serializes
`executionMode`, `outputProposalType`, and context profiles; Angular's `IntentDefinition` interface
omits them.

---

## 4. CURRENT_ANALYSIS_BACKEND_FLOW

```text
POST /analyses
  -> AnalysisService.create
  -> resolve Intent by composite key
  -> reject non-GENERIC mode
  -> persist Analysis(PENDING, type, intent, revision, guidance)

POST /analyses/{id}/workflow
  -> AnalysisWorkflowService.start
  -> Analysis IN_PROGRESS
  -> collect and persist Facts / Observations / diagnostics
  -> deterministic analysis and Project Profile
  -> build AnalysisContext
  -> create AiTask with contextSnapshot
  -> KnowledgeSelectionService.select
  -> attach selectedKnowledgeSnapshot
  -> project SelectedKnowledge into PromptRequest
  -> submit to Python AI Engine
  -> AiTask SUBMITTED

AI Engine background processing
  -> task-specific prompt builder
  -> structured LLM generation
  -> validate grounding and schema
  -> callback

Core callback
  -> validate correlation, grounding, and proposal contract
  -> persist ValidatableProposal[]
  -> persist prompt/provider/model digests on AiTask
  -> AiTask COMPLETED / FAILED
  -> Analysis COMPLETED / FAILED
```

### Creation contract usage

- `projectId`: resolves project and scopes collection.
- `type`: persisted; only checked as one of two supported generic values during task resolution.
- `intentId`: actually determines proposal type, task type, prompt, schema, and context profiles.
- `targetRevision`: persisted and consumed by source resolution/collection.
- `userGuidance`: persisted and sent to the AI Engine.

Knowledge selection uses only `focus`, `priorities`, and `outputContext` for deterministic lexical
boosting. `audience`, `levelOfDetail`, and `writingStyle` do not affect selection. All fields are
included in the prompt as low-priority untrusted guidance, but the prompt does not define stronger
field-specific behavior for them.

### Information continuity

- Collected Facts/Observations persist and reach AnalysisContext.
- Selected knowledge is persisted as JSONB on AiTask and sent to the provider projection.
- RepositoryContext evidence reaches the prompt projection.
- Prompt text and raw LLM response are not persisted; only digests and structured proposals persist.
- Structured proposal fields are not discarded by callback mapping. The limitation is primarily what
  each output schema asks the model to produce.
- Analysis itself has no result payload or relationship that identifies one canonical result
  projection.

---

## 5. CURRENT_AI_OUTPUT_CONTRACT

### Insight-generation Intents

`describe-project`, `generate-readme`, and `architecture-overview` all use
`InsightGenerationOutput`:

```text
proposals[]:
  insightType
  title
  summary
  rationale
  deltaType
  targetInsightId (ENRICHES only)
  confidence
  supportingFactIds[]
  supportingObservationIds[]
  evidenceReferences[]
```

The system prompt explicitly says: "Return only grounded, structured Insight proposals that require
human validation." `generate-readme` explicitly proposes structured README information and does not
generate or modify a README.

This contract supports a bounded finding (`title`/`summary`), a rationale, confidence, and grounding.
It does not support a multi-section Analysis report, explicit risk/recommendation lists, historical
or architectural relationship sections, assumptions, limitations, or an overall synthesis.

### Engineering Decision Intent

`analyze-engineering-decision` returns:

```text
proposals[]:
  title
  context
  choice
  rationale
  consequences
```

This is semantically richer and appropriately decision-shaped, but its schema has no explicit
grounding-ID/reference fields. Persisted historical decision proposals confirm that confidence is
set to `1.0` and supporting IDs/references can be empty even when the text claims evidence. This is a
contract-quality gap independent of CATEGORY_SELECTION.

### Engineering Event Intent

The dedicated event workflow returns category, title, summary, significance, confidence, supporting
IDs, and evidence references. It has a distinct source/commit-scoped launch path and should not be
presented as a generic Analysis Intent.

### Result persistence

The AI Engine returns structured `AiProposalResult` values. Core persists them as
`ValidatableProposal` payload plus confidence, supporting IDs, and evidence references. There is no
raw response or final report discarded after generation because no final report is requested.

---

## 6. CANONICAL_ANALYSIS_RESULT

**NO_CANONICAL_ANALYSIS_RESULT**

There is no `AnalysisResult`, final report, or single backend projection that represents "what this
Analysis concluded." `Analysis` stores lifecycle and scope, not output. `AiTask` stores execution and
input snapshots. The generated output is a set of independently reviewable
`ValidatableProposal`s. Accepted proposals become separate trusted artifacts.

What currently substitutes for a result:

- Analysis status and diagnostics;
- latest AiTask execution metadata;
- selectedKnowledgeSnapshot / selected-evidence projection;
- raw proposal list;
- proposal-review projection;
- promoted Insights (and separately Events/Decisions);
- optional downstream Deliverable after accepted Insights exist.

ADR-006 and ADR-028 make proposals the correct authoritative AI output boundary. Therefore the
smallest V1 should not invent a trusted final report. It should define a **canonical human-facing
Analysis Results projection** over existing Analysis, newest AiTask, proposals, validation outcomes,
and evidence links, explicitly retaining proposals as untrusted until validation.

The absence of this human-facing result is a **P0 product-flow gap** because the completion surface
does not answer the user's basic question: what did this Analysis find and what should I review?

---

## 7. HUMAN_VALIDATION_FLOW

The core lifecycle preserves ADR-006:

```text
ValidatableProposal(PROPOSED)
  -> human inspects payload, rationale, Facts, Observations
  -> POST /validations ACCEPTED or REJECTED
  -> immutable Validation
  -> ACCEPTED: atomic promotion to supported trusted artifact
  -> REJECTED: no trusted artifact
```

Angular offers both sequential Analysis-scoped review and direct proposal audit. The sequential view
shows title, summary, rationale (or event significance), bounded supporting Facts and Observations,
raw payload, immutable confirmation, decision note, and Insight severity.

Coherence gaps:

- top-level proposal evidence references are returned by Core but not rendered in sequential review;
- Fact evidence references and Observation provenance are not fully projected/rendered;
- references cannot be followed to source files, commits, diffs, Facts, or Observations;
- accepted Insights and Engineering Events link to their resulting artifacts;
- accepted Engineering Decisions do not have an equivalent review-result link or Angular Decision
  detail route despite backend promotion and read APIs;
- direct proposal review is Insight-centric and can send Insight severity for non-Insight proposal
  acceptance;
- `validatedBy` is a session-local random UUID supplied by Angular, explicitly documented as MVP
  identity rather than authentication;
- the raw proposal-creation endpoint can create arbitrary ungrounded proposals outside the AI
  callback contract, although the normal Analysis UI does not use it.

The proposal boundary and atomic promotion should be strengthened, not bypassed.

---

## 8. GAP CLASSIFICATION

### ANGULAR_GAPS

#### P0

1. **No outcome-oriented Analysis Results surface.** Completion is presented as diagnostics and
   execution internals; generated findings and review work are secondary.
2. **Launch choices are not guaranteed executable.** Angular exposes the dedicated event Intent in
   the generic form, which Core rejects. The UI model omits execution mode and output type.
3. **The human is asked to combine two overlapping internal selectors without a product contract.**
   `AnalysisType` is almost behaviorally inert while Intent controls execution; arbitrary pairs are
   accepted.

#### P1

1. Workflow-preparation failure leaves the persisted failed Analysis disconnected from the launch
   surface.
2. Generic Analysis cannot deliberately scope a source in multi-repository projects.
3. Accepted Engineering Decisions are not reachable from validation results.
4. Evidence is inspectable but not navigable/resolvable.
5. Proposal review does not combine rationale, top-level references, source evidence, and AiTask
   lineage in one coherent view.
6. Header status and completion timestamps can contradict polled diagnostics.

#### P2

1. No global Analysis history, filters, paging, search, or comparison.
2. Detail back-navigation loses project/activity context.
3. Profile and warnings omit existing richer fields/provenance.
4. Dedicated Project Understanding supports guidance in contracts but its UI supplies none.
5. Raw enum names, mixed language, UUIDs, hashes, and JSON dominate product copy.

### BACKEND_GAPS

#### P0

1. **No explicit launch compatibility contract.** Core knows execution mode and proposal type, but
   does not define which product Analysis objective/type/source combination the generic UI should
   offer.
2. **No canonical Analysis Results projection.** Consumers must independently join Analysis,
   AiTask, proposals, validations, promoted artifacts, and selected evidence.

#### P1

1. `AnalysisType` is persisted but does not materially shape task selection, prompt, output schema,
   or knowledge policy beyond allow-listing two values.
2. Decision-specific deterministic fact/observation ranking is absent; it falls through generic
   scoring.
3. Accepted Decision lineage is not included in proposal-review output.
4. Prompt text/raw provider response are not observable; structured result and digests are.
5. Public raw proposal creation bypasses normal AI grounding/lineage requirements.
6. Client-supplied reviewer UUID is not authenticated identity.

#### P2

1. Existing Analysis type/status filter endpoints are unused by Angular.
2. Raw proposal lists lack the richer deterministic ordering and evidence projection of review.
3. Analysis has no explicit retry/re-run linkage; history is a flat set of immutable runs.

### AI_ENGINE_CONTRACT_GAPS

#### P0

None for the explicitly intended proposal-generation lifecycle. The Engine correctly returns
structured proposals rather than trusted knowledge or an invented report.

#### P1

1. Insight contracts cannot express a coherent multi-finding analysis, explicit risks,
   recommendations, relationships, assumptions, limitations, or overall synthesis.
2. Engineering Decision output lacks explicit supporting Fact/Observation IDs, evidence references,
   and confidence in its versioned schema, weakening the same grounding contract enforced for
   Insights and Events.
3. User Guidance is provided as undifferentiated JSON; audience/detail/style semantics are not
   explicitly translated into task-specific output requirements.

#### P2

1. There is no compact human-readable execution summary separate from proposal payloads.
2. The provider prompt and raw response are intentionally not persisted, limiting forensic review
   to snapshots, structured proposals, and digests.

### CONTEXT_QUALITY_GAPS

1. COMMIT_DIFF consumes roughly 68-72% of the 60-item diagnostic RepositoryContext composition for
   the measured free-text cases.
2. Strong relevance can bypass the current kind allowance without a category ceiling.
3. Story 0095 floors prevent trusted-knowledge starvation but do not prevent dominant-category
   concentration.
4. Decision Intent selection lacks decision-specific fact/observation scoring.
5. Full repository document bodies and richer relationship expansion remain incomplete under
   ADR-063.
6. Evidence references can be coarse (`analysis:{id}`, `repository:/`, directory paths) or absent,
   as confirmed in persisted real proposals.

These are real quality defects, but they occur after the user has successfully selected a coherent
objective and before the product has presented a coherent result.

---

## 9. CATEGORY_SELECTION_PRODUCT_IMPACT

**PARTIALLY**

If Story 0098 worked perfectly tomorrow, the selected RepositoryContext would be more diverse and
some proposals could gain broader historical, decision, and knowledge grounding. Analysis would
still expose invalid launch choices, ambiguous AnalysisType/Intent combinations, internal execution
machinery instead of Results, fragmented evidence, no canonical result projection, and incomplete
Decision validation navigation.

Therefore:

**CATEGORY_SELECTION_PRIORITY = DEFER_UNTIL_PRODUCT_FLOW_FIXED**

Story 0098 and its measured baseline should be preserved as the next context-quality optimization
after a coherent Analysis V1 and real product benchmark cases exist.

---

## 10. MINIMUM_FUNCTIONAL_ANALYSIS_V1

The smallest coherent V1 reuses current architecture and does not add open-ended Query or a trusted
report entity:

```text
project workspace
  -> choose one executable product objective (not incompatible internal mechanics)
  -> choose required source/revision scope when applicable
  -> optionally provide bounded guidance
  -> launch and enter a persistent Analysis lifecycle page
  -> see clear preparation / AI processing / failed / completed state
  -> completed state opens on Analysis Results
       - objective and scope
       - generated proposal count and validation progress
       - proposal title, summary, rationale/significance/choice
       - direct supporting evidence summary and inspectable selected-evidence link
       - explicit untrusted/proposed status
  -> review each proposal
  -> accept or reject individually
  -> navigate to every resulting trusted artifact type
  -> revisit the Analysis from project history
  -> diagnostics, hashes, raw context, and complete snapshot remain available as secondary audit data
```

The V1 result is a projection over proposals and validation outcomes, not a new trusted synthesis.
An Analysis-level narrative report would be a later Analysis-contract decision and must define its
trust, persistence, citation, and relationship to Deliverables.

---

## 11. P0_DEPENDENCY_GRAPH

```text
P0-A: executable Analysis launch contract
  UI + backend contract ownership
  - expose only objectives valid for generic launch
  - stop asking the human to combine behaviorally ambiguous type/Intent choices
  - preserve dedicated event workflow separately
        |
        v
P0-B: canonical human-facing Analysis Results projection
  Angular first; reuse existing Core proposal-review, selected-evidence, task, and validation data
  - outcome before diagnostics
  - grounded proposals and validation state as the result
  - no new trusted report object
        |
        v
P0-C: complete result-to-trusted-artifact navigation for supported proposal types
  Angular + proposal-review projection ownership
  - Insight, EngineeringEvent, and Decision outcomes all visible
        |
        v
minimum functional Analysis V1
        |
        v
real product benchmark over supported objectives
        |
        v
CATEGORY_SELECTION / Story 0098
        |
        v
measure whether context diversity improves human-visible proposals
```

P0-A is first because a run cannot be a reliable product case unless its input is executable and
semantically understandable. P0-B is the highest visible-value gap, but it must consume an
unambiguous run. P0-C completes the already-supported validation lifecycle rather than adding a new
proposal family.

---

## 12. PRODUCT_BENCHMARK_STRATEGY

### Diagnostic benchmark

Continue to use engineering-context / RepositoryContext diagnostics for selector mechanics:

- candidate and selected counts;
- layer/kind distribution;
- COMMIT_DIFF share;
- floors/ceilings;
- item/token budgets;
- discarded counts where observable.

The existing five free-text diagnostic objectives remain valid only for this layer. They are not
Analysis Intent IDs and must not be treated as product cases.

### Product benchmark

After the P0 flow is coherent, use real supported workflows:

1. **Project Understanding / `describe-project-v1`**: canonical project initialization/refresh,
   grounded project/architecture/technology proposals.
2. **Generic `architecture-overview-v1`**: architecture delta/findings, existing architecture
   knowledge interaction, evidence coverage.
3. **Generic `analyze-engineering-decision-v1`**: decision-shaped context/choice/rationale/
   consequences and resulting Decision validation lifecycle, after grounding and result navigation
   gaps are addressed.

`generate-readme-v1` should not be scored as a generated README because its current contract only
produces prerequisite Insight proposals. The dedicated engineering-event flow should have its own
fixed-commit benchmark rather than being mixed with generic Analysis cases.

Measure:

- successful human launch and lifecycle clarity;
- selected evidence reaching the persisted snapshot;
- grounding and category coverage of each proposal;
- technical depth and usefulness of title/summary/rationale or decision/event fields;
- concrete repository/ADR/story/history relationships where the Intent supports them;
- unsupported claims and empty/coarse references;
- reviewability and successful promotion/navigation;
- final human-visible Results state, not only backend proposal JSON.

**PRODUCT_BENCHMARK_READINESS = BLOCKED** until P0-A and P0-B establish stable product inputs and a
canonical human-visible result. The multi-step API is externally executable, but that alone is not a
valid product benchmark contract.

---

## 13. FIRST_NEXT_STORY

**Proposed title: Align Generic Analysis Launch with Executable Intent Contracts**

Proposed scope only:

- make Angular model and use existing Intent execution metadata;
- exclude dedicated Engineering Event Intent from generic Analysis launch;
- replace raw/incompatible type+Intent choices with explicit executable generic objective choices,
  without adding open-ended objectives;
- enforce/document the selected objective's required Analysis type/scope at the existing Core
  creation boundary;
- preserve dedicated Project Understanding and Engineering Event flows.

Do not include Results redesign, CATEGORY_SELECTION, benchmark harness, new Intents, or Engineering
Query in this Story.

---

## 14. ENGINEERING_QUERY_BOUNDARY

**Analysis** is a structured, versioned workflow with known objectives, deterministic scope and
selection, bounded context, task-specific structured proposals, immutable execution snapshots, and
human validation before trusted promotion.

**Engineering Query** is a future interactive investigation capability for arbitrary questions,
follow-up retrieval, iterative exploration, and answer-oriented interaction over DevLog memory.

The inability to enter an arbitrary objective belongs to Engineering Query unless a new governed
Analysis Intent is explicitly created. It should not be used to justify weakening current Intent,
grounding, proposal, or validation contracts. Conversely, invalid launch options, ambiguous
AnalysisType/Intent selection, missing Results presentation, and incomplete proposal promotion
navigation are Analysis defects and must not be deferred to Engineering Query.

---

## 15. NEW_ADR_REQUIRED

**NO**

ADR-006, ADR-017, ADR-020, ADR-028, ADR-030, and ADR-063 are sufficient for the minimum functional
Analysis V1 described here. A future persisted Analysis narrative/report contract could require a
decision because its trust and lifecycle semantics are not currently defined, but that is not the
recommended next slice.

---

## 16. GIT_STATUS

At investigation time:

- Branch: `main`
- HEAD: `9ddbf9d420aa655cde221165f2a149652b4fff0d`
- Existing untracked design/investigation artifacts were present before this artifact:
  - `docs/investigations/analysis-product-benchmark-path.md`
  - `docs/stories/0098-cap-limit-commit-diff-category-dominance/`
- This investigation adds only:
  - `docs/investigations/analysis-product-gap-analysis.md`
- No production code, tests, commit, push, or merge was performed.

---

ANALYSIS_PRODUCT_GAP_INVESTIGATION_AWAITING_HUMAN_REVIEW
