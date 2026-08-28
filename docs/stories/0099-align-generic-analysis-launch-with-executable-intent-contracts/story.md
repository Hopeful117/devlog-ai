# Story 0099 — Align Generic Analysis Launch with Executable Intent Contracts

## Status

**DESIGNED**

HUMAN_REVIEW = **PENDING**

## Priority

**P0-A — ANALYSIS V1 LAUNCH CONTRACT**

This Story supersedes Story 0098 in execution priority. Story 0098 remains preserved and unchanged
as deferred CATEGORY_SELECTION work.

## Objective

Make generic Analysis launch coherent for a human engineer by presenting executable engineering
objectives with objective-derived Project or repository scope instead of independent internal
`AnalysisType`, Intent, and scope mechanics.

The human chooses what they want DevLog to do. Core continues to resolve and persist the governed
`IntentDefinition`, internal `AnalysisType`, proposal type, AiTask type, prompt template, and context
profiles deterministically.

Governed by ADR-006, ADR-017, ADR-020, ADR-021, ADR-028, ADR-030, and ADR-063.

## Human Story

As a human engineer working in a Project,
I want to choose a supported engineering objective and bounded guidance, with repository source and
revision controls only when that objective is repository-scoped,
so that every offered Analysis launch is executable and I do not need to understand DevLog's
internal Analysis, Intent, scope, Prompt, or AiTask vocabulary.

## Approved Product Findings

This Story implements the first dependency identified by the approved investigations:

```text
P0-A Launch Contract
  -> P0-B Canonical Analysis Results projection
  -> P0-C Validation/result navigation
  -> Analysis V1
  -> Product Benchmark
  -> CATEGORY_SELECTION / Story 0098
```

Relevant accepted artifacts:

- `docs/investigations/analysis-product-benchmark-path.md`
- `docs/investigations/analysis-product-gap-analysis.md`
- Story 0097 benchmark investigation and completed Story artifacts

## LAUNCH_CONTRACT_INVESTIGATION

### Current Angular contract

The generic launch form currently asks for:

| Field | Current presentation | Current behavior |
|---|---|---|
| `projectId` | Hidden, inherited from project route | Required backend scope |
| `type` | Raw selector: `ARCHITECTURE_REVIEW` / `PROJECT_EVOLUTION` | Sent independently from Intent |
| `intentKey` | Raw Intent ID/version/objective selector | Sent as `intentId` composite key |
| `targetRevision` | Optional branch, tag, or SHA | Applied to every implicitly selected source |
| `focus` | Optional guidance | Selection boost + prompt |
| `priorities` | Optional guidance | Selection boost + prompt |
| `outputContext` | Optional guidance | Selection boost + prompt |
| `audience` | Optional guidance | Prompt only |
| `levelOfDetail` | Optional guidance | Prompt only |
| `writingStyle` | Optional guidance | Prompt only |

The form displays Intent ID, version, prompt-template identifier, and supported Insight-type enums.
It does not model the `executionMode`, `outputProposalType`, or `contextProfiles` already returned by
Core.

The launch remains two-step:

```text
POST /api/v1/analyses
  -> persist PENDING Analysis
POST /api/v1/analyses/{id}/workflow
  -> collect, select, create AiTask, submit to AI Engine
```

Changing that lifecycle is outside this Story.

### Current backend creation contract

`CreateAnalysisRequest` currently requires:

- `projectId`
- `type`
- `intentId` (actually the complete catalog key, for example `architecture-overview-v1`)

and optionally accepts:

- `targetRevision`
- `userGuidance`

Core resolves the Intent and rejects non-`GENERIC` execution modes before persistence. It does not
validate an Intent/AnalysisType compatibility matrix. A direct client can create one of the three
currently unsupported Analysis types and only discover the failure after workflow start.

Generic creation does not set `Analysis.selectedSource`. Collection therefore runs against all
active project sources and applies the same optional revision string to each source.

### Current execution derivation

```text
request.intentId
  -> IntentCatalog.resolve(key)
  -> IntentDefinition.outputProposalType
  -> AnalysisAiTaskTypeResolver
  -> AiTaskType

IntentDefinition.promptTemplate
  -> AI Engine prompt builder

IntentDefinition.outputSchema
  -> structured provider contract

IntentDefinition.contextProfiles
  -> RepositoryContext planning/ranking
```

`AnalysisType` is not used to select those values.

## PRODUCT_VS_INTERNAL_CONCEPTS

| Product concept | Internal concepts retained by Core | V1 visibility decision |
|---|---|---|
| Engineering objective | Intent ID/version, execution mode, proposal type, prompt template, context profiles | Show only human objective and description; retain internals in Core/diagnostics |
| Project | Project UUID | Derived from current route; not a form control |
| Objective scope | Intent scope policy, selected Source when applicable | Show read-only Project scope or repository controls derived from the objective |
| Repository source | Source UUID, source snapshot | Show and persist only for repository-scoped objectives |
| Revision | Requested ref and resolved commit | Show only for repository scope; expose resolved identity after launch in diagnostics |
| Guidance | `UserGuidance` record and snapshot | Show bounded product labels; keep priority semantics and persisted snapshot |
| Analysis run | Analysis UUID/status | Show after launch/history, not as launch mechanics |
| Result type | `ProposalType` | Describe expected reviewed outcome in objective copy; hide enum |
| AI execution | `AiTaskType`, prompt version, provider metadata | Hidden at launch; retained in execution diagnostics |

Internal concept decisions:

| Internal concept | Hidden from launch UI | Indirectly represented | Still required by backend | Still available in diagnostics |
|---|---|---|---|---|
| `AnalysisType` | Yes | Objective-derived internal classification | Yes | Yes |
| Intent ID/version | Yes | Selected objective identity | Yes | Yes |
| Prompt template | Yes | Governed by objective | Yes | Yes |
| Execution mode | Yes | Determines launch channel eligibility | Yes | Optional diagnostic/catalog metadata |
| Proposal type | Yes | Human copy explains proposed knowledge type | Yes | Yes |
| `AiTaskType` | Yes | Derived execution | Yes | Yes |
| Context profiles | Yes | Objective-specific context behavior | Yes | Yes |
| Collector mechanics | Yes | Progress/completeness only | Yes | Yes |

## ANALYSISTYPE_AUDIT

### Why AnalysisType exists

ADR-017 distinguishes an Analysis's analytical-purpose classification from specialized AiTask
operations. The type is persisted for history, diagnostics, filtering, and context construction.

### Current behavioral influence

| Concern | AnalysisType influence |
|---|---|
| Persistence/history/filtering | Persisted and exposed; project/type query supported |
| Generic workflow gate | Only `ARCHITECTURE_REVIEW` and `PROJECT_EVOLUTION` accepted at workflow start |
| AnalysisContext | Architecture Review includes related analyses, architecture artifacts, decisions; Project Evolution includes related analyses and milestones |
| Prompt selection | None; Intent prompt template controls it |
| Output schema | None; Intent output schema controls it |
| AiTask type | Allow-list gate only; Intent proposal type selects actual AiTask type |
| Context profiles | Fallback only when Intent has no profiles; all current catalog Intents have profiles |
| Knowledge collectors | No direct influence; optional AnalysisContext groups indirectly change candidates |
| Fact/observation scoring | None; Intent and guidance control scoring |
| Source/revision | None |
| Diagnostics | Identity/display only |
| Deliverables | None |

### Product decision

**Does AnalysisType belong in the human generic launch experience? NO.**

It survives as an internal persisted classification and diagnostic field. Core derives it from the
approved generic launch policy. The human does not select it independently.

### V1 derivation policy

| Product objective | Intent | Derived AnalysisType | Fixed scope |
|---|---|---|---|
| Understand this project | `describe-project-v1` | `ARCHITECTURE_REVIEW` | `PROJECT_SCOPE` |
| Prepare README information | `generate-readme-v1` | `ARCHITECTURE_REVIEW` | `REPOSITORY_SCOPE` |
| Review the architecture | `architecture-overview-v1` | `ARCHITECTURE_REVIEW` | `PROJECT_SCOPE` |
| Analyze engineering decisions | `analyze-engineering-decision-v1` | `ARCHITECTURE_REVIEW` | `PROJECT_SCOPE` |

Rationale:

- these are project-state, architecture, documentation, and decision review objectives whose scope
  is fixed by their human meaning rather than chosen independently;
- the Intent already controls task type, prompt, schema, and context profile;
- current `ARCHITECTURE_REVIEW` context adds related architecture/decision knowledge useful to these
  objectives;
- `PROJECT_EVOLUTION` remains the internal classification of the dedicated, commit-bounded
  Engineering Event workflow;
- no new Analysis type or AI behavior is introduced.

This mapping is a V1 launch policy, not a claim that all future generic objectives are architecture
reviews. Any future mapping change requires a separately reviewed product contract change.

## INTENT_EXPOSURE_AUDIT

| Intent | Generic executable | Generic launcher V1 | Dedicated workflow | Deprecated/internal |
|---|---|---|---|---|
| `describe-project-v1` | Yes | Include as “Understand this project” | Also used by Project Understanding | No |
| `generate-readme-v1` | Yes | Include as “Prepare README information” | No | No |
| `architecture-overview-v1` | Yes | Include as “Review the architecture” | No | No |
| `analyze-engineering-event-v1` | **No** | **Exclude** | Engineering Event source+commit execution only | No |
| `analyze-engineering-decision-v1` | Yes | Include as “Analyze engineering decisions” | No | No |

No registered Intent is marked deprecated. `analyze-engineering-event-v1` is the only catalog Intent
that is not launchable through generic Analysis.

`generate-readme-v1` must not be labelled “Generate a README.” Its existing governed behavior is to
propose structured information needed for a README; actual documentation generation remains
downstream of validated knowledge.

## SCOPE_POLICY_AUDIT

ADR-021 defines Project as the software product or engineering initiative and Source as an evidence
origin. It explicitly allows an Analysis to target the complete Project or one Source and requires
Project-level analyses to remain possible. Repository scope is therefore valid when the objective is
repository-specific, but it must not become a blanket replacement for Project scope.

V1 uses fixed objective policies rather than a user-selectable scope:

| Objective | V1 scope | Source semantics | Revision semantics |
|---|---|---|---|
| Understand this project | `PROJECT_SCOPE` | `selectedSource == null`; collect all applicable active Project sources | Explicit `targetRevision` rejected; each source resolves its own default revision |
| Prepare README information | `REPOSITORY_SCOPE` | One validated active Git source persisted in `selectedSource` and snapshot | Optional ref resolved only within the selected source |
| Review the architecture | `PROJECT_SCOPE` | `selectedSource == null`; collect all applicable active Project sources | Explicit `targetRevision` rejected; each source resolves its own default revision |
| Analyze engineering decisions | `PROJECT_SCOPE` | `selectedSource == null`; collect all applicable active Project sources | Explicit `targetRevision` rejected; each source resolves its own default revision |

`BOTH` is not exposed for any existing V1 objective. A future repository-specific architecture or
decision objective may be introduced as a distinct governed Intent after its prompts, context
composition, and result provenance become scope-aware.

The existing runtime `resolvedRevisions` diagnostic remains the available multi-source provenance.
Core must not promote one requested or observed revision as the baseline for a Project-scoped
Analysis. ADR-061 is still proposed, but its independent-baseline analysis corroborates this rule.

## LAUNCH_CONTRACT_V1

### Product-facing fields

#### Required

| Field | Product meaning |
|---|---|
| Objective | One of the four Core-supported generic objectives |

The Project is required but derived from the current project route rather than chosen again. Scope
is fixed by the objective and is not a separate human choice.

#### Conditionally required

| Field | Product meaning |
|---|---|
| Source | One active Git repository for `REPOSITORY_SCOPE`; auto-selected in Angular when exactly one exists, explicit when several exist |

#### Optional

| Field | Product meaning |
|---|---|
| Revision | For `REPOSITORY_SCOPE` only: branch, tag, or full commit within the selected source; source default when absent |
| Focus | What aspect should receive additional evidence emphasis |
| Priorities | Up to ten evidence/output priorities |
| Output context | Intended use or boundary for the output |
| Audience | Model-facing audience preference |
| Level of detail | Model-facing detail preference |
| Writing style | Model-facing style preference |

Guidance remains subordinate to the selected objective and cannot change output schema, proposal
type, grounding, or human validation.

### Wire compatibility

The existing `/api/v1/analyses` endpoint remains. No new endpoint or API version is required.

The V1 request evolves additively:

```text
projectId          required, existing
intentId           required opaque catalog key selected through objective UI, existing
sourceId           required for REPOSITORY_SCOPE, rejected for PROJECT_SCOPE, additive
targetRevision     optional for REPOSITORY_SCOPE, rejected for PROJECT_SCOPE, existing
userGuidance       optional, existing
type               optional legacy field; hidden from new Angular UI
```

Core behavior:

1. Resolve the opaque `intentId` key to a canonical `IntentDefinition`.
2. Require `executionMode == GENERIC`.
3. Resolve the V1 internal `AnalysisType` from the approved mapping above.
4. If legacy `type` is supplied, require it to equal the derived type; reject mismatches before
   persistence with a contract error.
5. Resolve the fixed scope policy from the canonical Intent key.
6. Require at least one applicable active Git source for the current four objectives.
7. For `PROJECT_SCOPE`, reject `sourceId` and `targetRevision`, keep `selectedSource == null`, and
   collect all applicable active Project sources at their independently resolved default revisions.
8. For `REPOSITORY_SCOPE`, require `sourceId`, validate that the active Git source belongs to the
   Project, and persist the selected source plus immutable source snapshot.
9. For `REPOSITORY_SCOPE`, persist the optional requested revision and resolve it only within the
   selected source.
10. Persist canonical Intent ID/version and derived AnalysisType.
11. Continue the existing workflow endpoint and lifecycle unchanged.

### Hidden/backend-derived values

| Value | Derivation owner |
|---|---|
| IntentDefinition | `IntentCatalog.resolve(intent key)` |
| Intent ID/version | Resolved IntentDefinition |
| AnalysisType | Generic Analysis launch policy mapping |
| Scope policy | Generic Analysis launch policy mapping from canonical Intent key |
| ProposalType | `IntentDefinition.outputProposalType` |
| AiTaskType | `AnalysisAiTaskTypeResolver` from proposal type |
| Prompt template | `IntentDefinition.promptTemplate` |
| Output schema | `IntentDefinition.outputSchema` |
| Context profiles | `IntentDefinition.contextProfiles` |
| Selected source snapshot | Core from validated Source for `REPOSITORY_SCOPE` only |
| Resolved revision(s) | Workspace collection; one selected-source revision or source-to-revision diagnostics |

## USER_GUIDANCE_AUDIT

| Field | Deterministic selection | AI prompt | V1 recommendation |
|---|---|---|---|
| Focus | Yes | Yes | Keep, primary guidance |
| Priorities | Yes | Yes | Keep, primary guidance |
| Output context | Yes | Yes | Keep, primary guidance |
| Audience | No | Yes | Keep under optional output preferences |
| Level of detail | No | Yes | Keep under optional output preferences |
| Writing style | No | Yes | Keep under optional output preferences |

No field is globally ignored. V1 groups guidance without changing semantics:

- **Evidence emphasis:** focus, priorities, output context.
- **Output preferences:** audience, level of detail, writing style.

The UI must not promise deterministic behavior for output preferences. Existing validation limits and
normalization remain unchanged.

## MULTI_REPOSITORY_SCOPE

| Workflow | Source rule | Revision rule | V1 status |
|---|---|---|---|
| Generic Project-scoped Analysis | All applicable active Project sources; `selectedSource == null` | No requested ref; each source resolves its own default | Preserve Project semantics and remove ambiguous shared-ref input |
| Generic repository-scoped Analysis | One explicitly requested active Git source | Optional ref resolved only within that source | Add for README objective |
| Project Understanding | One required active Git source; auto-select sole source | Optional ref resolved before Analysis claim | Preserve |
| Engineering Event | One required active Git source | Required complete target commit; first-parent scope | Preserve dedicated workflow |

Generic V1 derives scope from the objective. Project-scoped objectives preserve the ADR-021 Project
boundary and must not fabricate one Source/revision baseline. The repository-scoped README objective
uses existing `Analysis.selectedSource`, source snapshot, and single-source revision semantics.

Current multi-source RepositoryContext composition is not fully source-consistent: some context is
project-wide, some repository structure can come from one source, and selected prompt evidence may
lose visible source provenance. Correcting retrieval, ranking, and composition belongs to a separate
product-quality slice and remains required before declaring multi-repository Analysis V1 complete.

## ANGULAR_IMPACT

Design impact only:

- introduce a generic Analysis launch ViewModel that separates product objective, derived scope,
  conditional repository controls, and guidance from raw API metadata;
- model `executionMode`, `outputProposalType`, and context profiles returned by Intent catalog;
- include only `GENERIC` Intent entries;
- map the four approved Intent keys to human objective labels/descriptions;
- remove the editable AnalysisType control;
- hide Intent ID/version, prompt template, supported Insight enums, execution mode, proposal type,
  AiTask type, and context profiles from the primary form;
- display read-only Entire Project scope and active-source count for Project-scoped objectives;
- show source selection and revision only for the repository-scoped README objective;
- auto-select the sole active source in Angular and require deliberate choice when several exist;
- rename `Intent` to `Objective` and explain that output remains proposals requiring human review;
- group guidance into evidence emphasis and output preferences without changing fields;
- keep the existing create-then-workflow launch sequence and navigation behavior;
- preserve internal metadata on Analysis detail/diagnostic surfaces.

## BACKEND_IMPACT

### Safe projection changes

- no IntentDefinition or AI Engine schema change is required;
- continue returning the complete Intent catalog for diagnostic/dedicated consumers;
- Angular uses existing `executionMode` metadata to determine generic eligibility;
- existing Analysis responses and history remain readable.

### Creation/validation changes

- make request `type` optional for the new product path while retaining legacy input compatibility;
- add optional `sourceId` to the request contract;
- centralize generic launch policy: generic Intent eligibility, derived AnalysisType, and fixed scope;
- validate legacy type consistency before persistence;
- reject `sourceId` and `targetRevision` for Project scope and preserve `selectedSource == null`;
- require and validate source ownership, active status, and Git type for repository scope;
- persist `selectedSource`/snapshot only for repository scope;
- return 400/422 product contract errors for non-generic Intent, incompatible legacy type, absent
  applicable sources, invalid scope fields, absent required source, and invalid source rather than
  creating a doomed Analysis.

### Metadata/compatibility

- no database migration is required because Analysis already represents Project scope with a null
  selected source and repository scope with a selected source and snapshot;
- existing persisted Analyses, routes, diagnostics, proposal review, Deliverables, and AiTask
  snapshots remain unchanged;
- old clients that send matching `ARCHITECTURE_REVIEW` remain compatible only when their source and
  revision fields satisfy the objective-derived scope policy;
- old README callers must add `sourceId`; old Project-scoped callers must stop sending
  `targetRevision`;
- old clients that send `PROJECT_EVOLUTION` with a generic Intent receive a deliberate pre-persist
  contract rejection instead of creating a semantically ambiguous run;
- no API version change is required for this pre-V1 contract correction, but validation becomes
  deliberately stricter and must be called out in client release notes.

## Scope

### IN SCOPE

- Generic Analysis launch contract
- Intent exposure and generic eligibility
- Four executable objective choices
- AnalysisType product visibility and deterministic derivation
- Objective-specific fixed Project or repository scope
- Repository-only source and revision controls
- UserGuidance presentation using existing semantics
- Pre-persistence validation of launch combinations
- Angular/backend contract alignment
- Preservation of internal diagnostics

### OUT OF SCOPE

- Analysis Results redesign (P0-B)
- Proposal Review redesign
- Validation/result navigation (P0-C)
- CATEGORY_SELECTION and Story 0098
- Benchmark harness
- Product benchmark execution
- Engineering Query or arbitrary objectives
- New IntentDefinitions
- New Analysis types
- Prompt/output-schema redesign
- AI Engine changes
- Atomic replacement of the existing two-call lifecycle
- RAG, vectors, embeddings, ContextPack, or retrieval improvements

## ACCEPTANCE_CRITERIA

1. The generic launcher presents exactly the four approved executable objectives and does not expose
   `analyze-engineering-event-v1`.
2. The primary launch UI does not display or ask the human to choose AnalysisType, Intent ID/version,
   prompt template, execution mode, proposal type, AiTask type, context profiles, or collector
   mechanics.
3. Selecting each displayed objective deterministically resolves its existing versioned
   IntentDefinition and derives `ARCHITECTURE_REVIEW` before Analysis persistence.
4. Generic launch rejects a non-`GENERIC` Intent before creating an Analysis.
5. Legacy explicit AnalysisType is accepted only when it matches the derived V1 type; incompatible
   combinations are rejected before persistence.
6. Each objective deterministically resolves the fixed scope policy defined by this Story; the UI
   does not offer an independent scope selector.
7. Project-scoped objectives show Entire Project, submit neither source nor revision, preserve
   `selectedSource == null`, and collect all applicable active sources at independent defaults.
8. The repository-scoped README objective requires one active Project Git source; Angular
   auto-selects the sole source and requires deliberate selection when several exist.
9. Repository scope persists the selected source and immutable snapshot; optional revision is
   resolved only against that source.
10. Core rejects source or revision fields for Project scope and rejects absent or invalid source for
    repository scope before persistence.
11. With no applicable active Git source, launch is unavailable and Core rejects direct requests
    before persistence.
12. All six existing UserGuidance fields remain available with unchanged validation and priority
    semantics; the UI distinguishes deterministic evidence emphasis from model-only output
    preferences.
13. Core still derives ProposalType, AiTaskType, prompt template, output schema, and context profiles
    exclusively from the resolved IntentDefinition.
14. The existing Analysis workflow, AiTask snapshot, provider submission, proposal validation,
    Deliverable, and history contracts remain behaviorally compatible.
15. Analysis detail and diagnostics continue to expose internal type, Intent, AiTask, prompt/version,
    scope, source/revision provenance, and execution metadata separately from launch UX.
16. Existing Project Understanding and dedicated Engineering Event launch behavior remains
    unchanged.

## RISKS

| Risk | Impact | Design mitigation |
|---|---|---|
| Existing clients send `PROJECT_EVOLUTION` with generic Intents | Behavioral compatibility break | Reject before persistence with explicit contract error; preserve existing history |
| Existing clients send README without source or Project scope with revision | Behavioral compatibility break | Return an explicit scope-contract error and document the pre-V1 request correction |
| Product objective labels drift from Intent semantics | Misleading launches | Bind each label to one immutable Intent key; never imply README generation |
| A single revision is applied to several Project sources | Incorrect or failed collection | Reject explicit revision for Project scope; resolve every source default independently |
| Current multi-source context loses source consistency | Incomplete or weakly attributable results | Preserve the correct Project boundary; track retrieval/composition correction separately before multi-repository V1 completion |
| Repository scope is accidentally applied to Project objectives | Partial result presented as Project understanding | Fix scope by canonical Intent and reject incompatible source fields |
| AnalysisType still affects optional AnalysisContext groups | Derived mapping changes context | Centralize and test the approved V1 mapping; keep type in diagnostics |
| Intent catalog remains shared with dedicated workflows | Accidental future exposure | Generic UI filters by execution mode and Core revalidates mode |
| Guidance copy overpromises audience/style guarantees | User expectation mismatch | Label these as model-facing preferences, not deterministic controls |
| Two-step launch can still leave PENDING records | Existing lifecycle gap | Explicitly out of scope; no regression introduced |
| Existing Angular routes/history contain raw type/Intent metadata | Mixed terminology persists | Preserve for diagnostics/history; P0-B owns result presentation |
| Project Understanding overlaps “Understand this project” | Duplicate entry points | Preserve convenience workflow; generic objective remains valid but uses same governed Intent |
| Engineering Event incorrectly re-enters generic launch later | Guaranteed failure | Dedicated mode excluded in UI and rejected by Core |
| Deliverables/Proposal Review depend on Intent and Analysis identity | Downstream regression | Persist the same canonical Intent/type fields and do not alter result contracts |

## RECOMMENDED_STORY_SCOPE

One vertical product-contract slice:

```text
generic Analysis form
  -> four executable engineering objectives
  -> fixed Project or repository scope derived from objective
  -> conditional repository source/revision controls and existing guidance
  -> hidden Intent key
  -> Core generic type/scope launch policy
  -> canonical Intent + derived AnalysisType persisted
  -> unchanged AnalysisWorkflowService
```

Do not combine P0-B Results projection, P0-C validation navigation, Story 0098, or benchmark tooling
into this Story.

## NEW_ADR_REQUIRED

**NO**

The Story creates a product projection over existing governed concepts. It applies ADR-021's existing
complete-Project and one-Source Analysis scopes without introducing a new scope cardinality or
persisted provenance model. It preserves the ownership boundaries established by ADR-006, ADR-017,
ADR-020, ADR-021, ADR-028, ADR-030, and ADR-063.

## Design Repository State

- Design branch: not created; repository conventions do not require branch isolation for uncommitted
  Story design artifacts.
- Story 0098 artifacts remain untouched.
- No production code, tests, benchmark harness, commit, push, or merge is part of this design.

ANALYSIS_LAUNCH_CONTRACT_STORY_DESIGNED_AWAITING_HUMAN_REVIEW
