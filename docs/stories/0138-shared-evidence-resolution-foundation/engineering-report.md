# Story 0138 Engineering Refinement Report

## Status

`SLICE_6_COMPLETE`

`SOURCE_POLICY_ALIGNED`

`EXPLICIT_FACADE_SEAM_IMPLEMENTED`

`GIT_DOCUMENT_ADAPTERS_IMPLEMENTED`

`FACT_ADAPTER_IMPLEMENTED`

`SNAPSHOT_BOUNDARY_VERIFIED`

`CONSUMER_PROJECTIONS_IMPLEMENTED`

`REPETITIVE_FAMILY_ADAPTERS_IMPLEMENTED`

`STORY_0138_IMPLEMENTATION_COMPLETE`

`STORY_0138_TECHNICALLY_VALIDATED`

`STORY_0138_ARCHITECTURE_ACCEPTED`

`KNOWN_APPLICATION_SECURITY_LIMITATION_RECORDED`

`FOLLOW_UP_SECURITY_STORY_REQUIRED`

`STORY_0139_DEFERRABLE`

`HUMAN_GIT_ACTION_REQUIRED`

Slice 0 aligned the previously conflicting SCA, repository-structure and MCP
source-selection paths with the approved `0 / 1 / N` policy. Slice 1 adds an
explicit Core facade with canonical Git/document parsing, strict family
dispatch, an envelope plus family-owned payload marker, and stable failure
codes.

Slice 2 adds source-scoped persisted Git commit and revision-pinned document
adapters. Slice 3 adds the first persisted domain adapter for Fact, requiring
an explicit analysis scope and rejecting cross-analysis access. Slice 4
verifies that current-state adapters reject `TASK_SNAPSHOT` before repository
access while `TaskSnapshotEvidenceResolver` reads only the originating task
snapshot. Slice 5 adds thin REST/MCP projections and Slice 6 adds current-state
adapters for Decisions, Engineering Events, Stories, Analyses, Artifacts and
Challenges. Explicit source and revision are preserved, and a workspace that
resolves a different revision fails deterministically.

The complete application authorization architecture remains outside this
Story. Successful resolution is not an authorization decision.

## 1. Current Resolution Architecture

There is no shared project-wide canonical evidence resolver today. Resolution
is distributed across several existing capabilities:

| Component | Current responsibility | Identity accepted | Result / scope | Authorization | Ranking / transport |
|---|---|---|---|---|---|
| `RepositoryEvidenceResolverImpl` | Reconstructs repository file paths from an `Insight` proposal lineage | An `Insight` entity, not a canonical reference | `RepositoryEvidenceProjection` containing source, baseline revision and resolved paths; analysis-scoped | Validates lineage belongs to the Insight Analysis; not general authorization | No ranking; domain service |
| `TaskSnapshotEvidenceResolver` | Resolves model-selected repository evidence assertions | Task-scoped `AiReference` plus evidence locator | Exact content and digest from immutable `AiTask.selectedKnowledgeSnapshot` | Uses `AiReferenceResolver` and task mapping capabilities | No ranking; use-case-local |
| `AiReferenceResolver` | Resolves typed AI references through one persisted execution mapping | `(type, ref, scope)` | `BindingSnapshot`, including canonical source identity and grounding capabilities | Yes, for task scope and grounding capability | No ranking; Core mapping boundary |
| `AiReferenceRegistryFactory` | Creates task-scoped typed mappings from `SelectedKnowledge` | Domain identities and `RepositoryEvidence.reference` values | `AiReferenceRegistry` and persisted mapping snapshot | Encodes task-visible identity and grounding capabilities | No ranking; projection infrastructure |
| `HistoricalSelectedEvidenceSnapshotProjector` | Parses and validates persisted selected-knowledge snapshots | Snapshot version, task, analysis and project IDs | Typed historical projection, including repository content and symbols | Validates task/analysis/project association | No ranking; application read projection |
| `RepositoryEvidenceResolverImpl` | Resolves Fact/Observation lineage into repository paths | Proposal Fact/Observation UUID lists | Current repository source/revision plus path list | Analysis lineage and data-integrity checks | No ranking; temporal-service dependency |
| `DocumentBodyCollector` | Discovers and reads related repository documents | Story relationships and explicit revision scope | Bounded `RepositoryEvidence` with `DocumentReference` | Source/workspace/path safety; no user authorization | Collector; no transport |
| `GitWorkspaceManager` | Synchronizes a source at a requested revision and checks file presence | `Source` plus revision/path | Workspace, resolved commit, or file-presence boolean | Rejects inactive/unsupported sources; not application authorization | Repository infrastructure |
| `ProjectHistoryService` / `ProjectCommitRepository` | Reads persisted commit and changed-file history | Project/source plus commit hash | Commit context or `ProjectCommit` aggregate | Project/source query scoping varies by endpoint | REST service; search is separate |
| MCP `ResourceSupport` and resources | Maps known resource IDs to backend endpoints | UUIDs, commit SHA, project slug | JSON transport payloads | Project ownership checks for some global IDs; active-source ordering for commits | Transport adapter, but contains lookup policy |

### Existing canonical identity primitives

- `DocumentReference` is a validated record for
  `document:{sourceId}:{normalizedPath}@{revision}` and can parse that form.
- `RepositoryRevisionScope` carries project, source, resolved revision,
  workspace path and revision-selection provenance for one SCA execution.
- `RepositoryEvidence.reference` remains a string identity field. It is
  consumed by ranking, selection, enrichment, projections, grounding mappings,
  resource mapping and temporal checks.
- `AiReference` is explicitly task-scoped and must remain separate from public
  canonical project evidence identity, as clarified by ADR-068.

### Existing resolution-like behavior by scope

#### Current repository context

`RepositoryContextEngine` collects all candidates, then ranks, selects and
enriches them. It is a bounded composer, not a resolver or complete evidence
universe. `RepositoryContextService.retrieveCandidates` is a shared retrieval
primitive, but it returns candidate `RepositoryEvidence`; it does not expand a
canonical reference to authoritative content.

#### Task snapshot

`TaskSnapshotEvidenceResolver` correctly refuses current-context lookup. It
requires the originating task mapping snapshot, checks the typed repository
reference, finds the matching snapshot item, checks content status and revision,
then resolves a line/section/hunk locator and digest. This is the strongest
existing historical resolution behavior, but it is specific to Story Context
Analysis/comparative task snapshots and is not a project-wide resolver.

#### Repository lineage

`RepositoryEvidenceResolverImpl` reconstructs file paths from Facts and
Observations supporting an Insight. It does not accept `RepositoryEvidence`
references, does not resolve arbitrary evidence families, and intentionally
returns a projection used by temporal assessment. Its failure model is limited
to `LINEAGE_UNAVAILABLE` and `DATA_INTEGRITY_ERROR`.

#### MCP resources

MCP resources resolve known objects by calling REST endpoints. Commit resources
map a project slug to an active source and then call the history endpoint;
Insight resources list active project Insights and search locally; Decision and
Event resources call global-ID endpoints and then verify project ownership.
These are transport projections, not a canonical resolution architecture.

## 2. Duplicated and Ad-Hoc Resolution

The following behaviors currently reconstruct or resolve evidence independently:

1. `EngineeringContextContractMapper` maps selected evidence kinds to MCP URIs
   by parsing identifiers and kind-specific provenance.
2. MCP `ResourceSupport` performs active-source selection, active-Insight
   filtering, project ownership checks and error translation.
3. `CommitContextResource` resolves a commit through a source selected by MCP
   ordering rather than through the canonical reference's explicit source.
4. `RepositoryContextAdapter` parses commit and diff reference strings to
   filter evidence by a commit window.
5. `TemporalAssessmentServiceImpl` resolves lineage paths, current known
   revisions and baseline/current file presence independently of the reference
   family semantics.
6. `DocumentBodyCollector` constructs revision-pinned document references and
   reads workspace files, but no corresponding general expansion capability
   exists.
7. `TaskSnapshotEvidenceResolver` scans a persisted snapshot by string
   reference after task-scoped AI authorization.
8. Domain services and MCP handlers independently perform UUID lookup,
   project ownership or active-status checks for Insights, Decisions, Events,
   Stories and other entities.

The duplicated behavior is not identical in authority or scope. It must not be
flattened into one universal lookup service. The shared capability should
provide common parsing/dispatch/result semantics while delegating authoritative
resolution to family-specific components.

## 3. Reference-Family Resolution Matrix

| Family | Canonical form | Legacy forms | Authoritative backing | Existing capability | Desired capability | Expandable / historical | Ambiguity and failure notes |
|---|---|---|---|---|---|---|---|
| Git commit | `git:{sourceId}:{sha}` | `git:{sha}` and older source-less commit/resource forms where present | `ProjectCommit` plus source-scoped Git workspace | `ProjectCommitRepository`; history context endpoint | Source-scoped persisted commit lookup, with optional Git verification | Expandable; persisted history supported, workspace verification supported; historical body is source/revision dependent | Same SHA across sources is ambiguous; missing source or commit is distinct |
| Changed file / diff | `diff:{sourceId}:{sha}:{path}` | `diff:{sha}:{path}` | `ProjectCommit` + `ChangedFile`; Git revision for deeper content | `CommitDiffEvidenceCollector`, `ProjectHistoryService`, temporal helpers | Resolve exact source/commit/path and changed-file record; only expand body when supported | Diff metadata historically supported; full hunk/content is not currently persisted as a general object | Legacy source-less form must not infer source; path may be renamed/deleted |
| Repository file | `file:{sourceId}:{path}@{revision}` | `file:{path}` | Source-scoped Git workspace at revision | `RepositoryStructureCollector`; `RepositoryStatePort`; content reader | Verify source, revision and path, then return bounded file projection/content if capability allows | Current and pinned historical Git reads supported in principle; no public shared resolver exists | Missing source/revision/path; structure aggregate must not be treated as a file |
| Repository document | `document:{sourceId}:{path}@{revision}` | `document:{path}` or document-like legacy paths | Source-scoped workspace plus document metadata/status | `DocumentReference`, `DocumentBodyCollector`, `SecureRepositoryContentReader` | Resolve exact pinned body and document metadata without changing status into authority | Revision-pinned body supported for bounded related documents; general arbitrary document expansion not yet exposed | File unavailable, too large, unsafe, unsupported encoding and status ambiguity are distinct |
| Fact | `fact:{uuid}` | Bare UUID / legacy raw IDs | `Fact` and `FactRepository`, analysis-scoped | `AiReferenceResolver` for task mapping; Fact service | Resolve Fact by identity and required analysis/project scope | Current persisted entity supported; historical task snapshot supported only through snapshot projection | Same UUID outside task/analysis is scope failure, not unknown project identity |
| Observation | `observation:{uuid}` | Bare UUID / legacy raw IDs | `Observation` and `ObservationRepository`, analysis-scoped | `AiReferenceResolver`; Observation service | Resolve Observation and preserve supporting Fact relationships | Current persisted entity supported; historical snapshot only through persisted task projection | Cross-analysis reference and incomplete supporting facts are integrity failures |
| Insight | `insight:{uuid}` | Bare UUID / resource UUID | `Insight` and `InsightRepository`, project-scoped | Insight service; MCP active-only lookup; AI registry mapping | Resolve domain Insight with explicit project/status semantics separate from identity | Current ACTIVE read supported; historical state is not a generic capability | Archived/superseded status must not be silently presented as current trusted knowledge |
| Decision | `decision:{uuid}` | Bare UUID / resource UUID | Decision entity/repository/service | Decision REST endpoint and MCP ownership check | Project-scoped domain lookup with explicit current/historical status | Current entity supported; historical revision not established | Global endpoint currently requires post-read project ownership validation |
| Engineering Event | `event:{uuid}` | Bare UUID / resource UUID | Engineering Event entity/repository/service | REST endpoint and MCP ownership check; AI registry mapping | Project-scoped event resolution with commit provenance retained | Current entity supported; historical event snapshot not established | Event identity must not resolve merely from referenced commits |
| Engineering Story | `story:{uuid}` | Story number, path, bare UUID | Engineering Story entity/repository plus repository Markdown body | Story service; `DocumentBodyCollector`; MCP project-scoped endpoint | Distinguish registry row from revision-pinned Story document | Registry current state supported; document body pinned by SCA scope; generic historical entity state unsupported | Story number/path can be ambiguous without project/source/revision |
| Analysis | `analysis:{uuid}` | Bare UUID | Analysis entity/repository | Current-analysis collector; snapshot projector; analysis service | Resolve Analysis record with project scope and optionally its persisted snapshot | Current record supported; task snapshot is historical projection, not rehydration of current Analysis | Task snapshot must not be replaced by current Analysis context |
| Artifact | `artifact:{uuid}` | Bare UUID / path | Artifact entity/repository/service | Artifact REST service; project context collector | Resolve Artifact entity, with repository path expansion only if explicitly supported | Current entity supported; historical body is repository revision-dependent and not general | `repository:{path}` provenance is not an Artifact identity |
| Challenge | `challenge:{uuid}` | Bare UUID | Challenge entity/repository/service | Challenge REST/service; project context collector | Resolve Challenge entity if still part of current evidence universe | Current entity likely supported; historical state not established | No canonical revision semantics currently visible |
| Structure aggregate | `module:{name}`, `module:summary`, `source:directories`, `test:directories`, `config:files`, `extensions:distribution` | Older unscoped aggregate tokens | Bounded `RepositoryStructureCollector` projection | Collector only | Resolve only as bounded projection metadata, not expansion to arbitrary files | Non-expandable by Story 0137 classification; revision metadata is provenance | `module:summary` and similar values are not globally unique; source/revision is required for any future identity |
| Human context / project note / milestone | Current collector-specific identity or `human-context:{uuid}` where typed AI mapping exists | Raw IDs and paths | Project human-context/milestone entities or repository documents | Project context collectors; AI registry for selected inputs | Resolve only where a stable current domain backing is confirmed | Current entity may be supported; historical body/state not established | Must not conflate provenance tier with current authority |

### Collector inventory

Current Repository Context collectors are `CurrentAnalysisContextCollector`,
`DeterministicKnowledgeContextCollector`, `GitHistoryContextCollector`,
`CommitDiffEvidenceCollector`, `RepositoryStructureCollector`,
`ProjectKnowledgeContextCollector`, and `DocumentBodyCollector`. Their outputs
mix expandable identities, domain identifiers, source/revision-pinned files,
and intentionally bounded projections in the same `RepositoryEvidence` record.

## 4. Resolution Responsibility Boundary

Shared resolution should answer:

> What authoritative evidence, domain object, repository object, historical
> snapshot or bounded projection does this canonical reference represent?

The boundary should own only deterministic syntax recognition, family dispatch,
scope validation required for identity, specialized resolver invocation, and a
structured resolution outcome. It must preserve the canonical reference,
family, source, provenance, applicable revision and capability metadata without
normalizing all families into one domain object.

It must not own ranking, budgeting, composition, semantic retrieval, vector
retrieval, RAG, ContextPack construction, AI interpretation, trust promotion,
human validation or transport formatting.

Authorization remains distinct. ADR-063 requires authorization before shared
retrieval, but the repository does not yet have a complete application-user
authorization model. Story 0138 may carry an opaque authorization scope or
require authorization to be performed by the caller, but must not invent
principals, roles or policies. `AiReferenceResolver` authorization is execution
mapping/capability authorization and must not be reused as general project
authorization.

## 5. Failure-Semantics Analysis

| Proposed class | Required now? | Repository equivalent | Classification | Families / caller need |
|---|---|---|---|---|
| `UNKNOWN_REFERENCE` | Yes | `UNKNOWN_AI_REFERENCE`; `Optional.empty()` in some services | Parsing/identity failure | Any family; callers need the original reference and parsed family if available |
| `UNSUPPORTED_REFERENCE_TYPE` | Yes | No shared equivalent; `AiReference` enum rejects unknown values during mapping | Capability/dispatch failure | Unknown or intentionally unsupported family; distinct from a missing object |
| `AMBIGUOUS_SOURCE` | Yes | `RepositoryStructureCollector.AmbiguousRepositorySourceException`; not honored by SCA/MCP source selection | Identity/scope failure | Git, diff, file, document, structure; must stop selection rather than choose first |
| `SOURCE_UNAVAILABLE` | Yes | Workspace/source exceptions and empty collector output | Source availability failure | Repository families; callers need source ID and availability reason |
| `REVISION_UNAVAILABLE` | Yes | Git command failures, `SourceRevisionUnavailableException`, workspace sync failures | Temporal/source failure | Git/file/document/diff; must not fall back to HEAD for a pinned request |
| `EVIDENCE_NOT_FOUND` | Yes | JPA `EntityNotFoundException`, history service failures, file reader `UNAVAILABLE` | Resolution absence | All expandable families; distinguish missing object/path from unsupported expansion |
| `EVIDENCE_NO_LONGER_RESOLVABLE` | Yes, but needs definition | Snapshot resolver rejects missing/incomplete content; historical projector rejects malformed snapshots | Temporal/projection lifecycle failure | Pinned document/file and task snapshots when identity existed but backing content is unavailable |
| `UNAUTHORIZED` | Semantically required, implementation deferred | `AiReferenceResolver` capability codes; MCP project ownership checks | Authorization failure | Task-scoped AI and future shared callers; must not be represented as not-found internally |
| `UNSUPPORTED_EXPANSION` | Yes | Structure metadata currently has no expansion; no common equivalent | Capability boundary | Non-expandable structure aggregates, summary-only evidence and unsupported family bodies |

The existing code proves that structured failures are needed. Returning
`Optional.empty()` is appropriate only for explicitly optional legacy lineage
(`RepositoryEvidenceResolverImpl` when an Insight has no proposal), not for
unknown, unauthorized, ambiguous or unsupported references. Story 0138 should
not introduce an exception hierarchy merely for symmetry. A small result/error
contract with stable semantic codes and family/reference metadata is sufficient;
existing family exceptions can be adapted at the boundary.

## 6. Temporal Resolution

| Category | Current behavior | Classification |
|---|---|---|
| Git commit identity | Persisted by `(sourceId, commitHash)`; history context reads stored commit data | Supported for persisted history; Git object verification is source-scoped |
| Diff identity | Changed files are persisted under a source-owned commit; no general hunk body resolver | Metadata supported; content expansion partial |
| Repository file | Structure collector synchronizes a workspace and records resolved revision; `RepositoryStatePort` checks presence at a source/revision | Supported for bounded Git reads, but no shared expansion boundary |
| Document body | `DocumentBodyCollector` resolves one `RepositoryRevisionScope`, synchronizes exact revision and reads bounded files | Supported for the SCA-related slice; general arbitrary resolution partial |
| Current domain entity | JPA services/repositories return current state | Supported, current-state only |
| Historical domain entity | No generic temporal repository projection found | Unsupported unless represented in an immutable task snapshot or domain-specific historical model |
| Persisted AI/task snapshot | Snapshot projector and `TaskSnapshotEvidenceResolver` validate the persisted version and task association | Supported as task-scoped historical projection; must not call current context |
| Baseline/current temporal comparison | `TemporalAssessmentServiceImpl` compares source-scoped file presence at baseline and latest known commit | Supported for its narrow temporal assessment; it reconstructs paths from lineage and is not a generic resolver |

The most important temporal rule is negative: a requested historical revision
must never silently become HEAD/current state. `GitWorkspaceManager.synchronize`
can validate and materialize an explicit revision, but `AnalyzeStoryContextUseCase`
currently falls back through current revision and HEAD when no target is known.
That fallback may be valid for a new current-context operation, but it must not
be used to resolve a reference that contains an explicit revision.

## 7. Java Design Options

### Option A - Shared facade plus specialized resolvers

```text
shared resolution facade
    -> Git resolver
    -> repository file/diff resolver
    -> document resolver
    -> domain knowledge resolver
```

Pros: explicit family ownership, strong type boundaries, easy focused tests,
small conceptual surface. Cons: facade can become a god-service if it starts
owning family policy, and adding families may require direct facade changes.

### Option B - Registry/dispatch model

Each resolver declares supported reference families and the dispatcher selects a
single capability.

Pros: isolated family registration, extensibility and explicit unsupported
families. Cons: more framework shape, dispatch ambiguity must be tested, and a
registry can obscure ownership or become premature infrastructure.

### Option C - Existing-capability composition

Introduce only a thin shared entry point that parses canonical references and
delegates to existing `ProjectCommitRepository`, `WorkspaceManager`,
`DocumentReference`, domain services, `AiReferenceResolver` (only for task
scope), and snapshot projection capabilities.

Pros: smallest change, preserves existing authority, avoids a resolver
framework, lower migration risk. Cons: existing APIs have inconsistent result
and failure semantics; a thin facade may expose those inconsistencies until
they are adapted; family dispatch may initially be explicit and less
extensible.

### Comparison

| Criterion | A | B | C |
|---|---|---|---|
| Change size | Medium | Large | Smallest |
| Type safety | High if family results are typed | High if registry is typed | Medium initially |
| Determinism | High | High, with dispatch tests | High if adapters are strict |
| Family isolation | High | High | Depends on adapters |
| Testability | High | High but broader | High for first slice |
| Compatibility | Medium | Medium/low | Highest |
| Extensibility | Medium | High | Medium |
| God-service risk | Medium | Medium/high framework risk | Low initially, rising if facade grows |
| Premature framework risk | Medium | Highest | Lowest |

No option is selected in this report. Repository evidence supports evaluating
Option C for the first vertical slice, with a human decision on whether its
dispatch seam is sufficient before introducing a registry.

## 8. Resolution-Result Options

### Common envelope with family-specific payload

The smallest credible result is a common metadata envelope containing the
requested canonical reference, family, source/provenance, revision state,
expandability and a family-specific payload. The payload must remain a sealed
or discriminated family result rather than a universal `Evidence` entity.

### Existing projections only

Returning `RepositoryEvidenceProjection`, `RepositoryEvidence`, domain entities
or snapshot DTOs directly would minimize new types but would leak family and
consumer semantics into the shared boundary. It is acceptable for an adapter,
not a complete shared result contract.

### Discriminated result

A sealed result family could model commit, diff, file, document, domain object,
snapshot and bounded projection separately. This is type-safe but should be
introduced only after the first family contracts are understood.

The report recommends a common metadata envelope plus family-specific results
as the design direction to review, not as an implementation authorization. The
envelope must distinguish identity, resolved content, provenance, temporal
state and capabilities. It must not expose authorization as if it were identity
or trust.

## 9. Compatibility and Migration Implications

- Legacy source-less `diff:{sha}:{path}` and `file:{path}` references remain
  readable only under explicitly defined legacy behavior; they must never infer
  source or revision.
- Existing `RepositoryEvidence.reference` strings, Fact/Observation evidence
  references, persisted Insights, analyses and AI task snapshots must remain
  readable without rewriting.
- `AiReferenceResolver` remains authoritative for task-scoped mapping. Shared
  resolution must not replace it or expose `canonicalSourceIdentity` as a
  provider-facing project identity.
- `TaskSnapshotEvidenceResolver` must continue resolving only the originating
  immutable snapshot. A future shared capability can be used beneath it only if
  the snapshot scope remains explicit.
- MCP resources should become thin projections over Core capabilities in a later
  slice. Story 0138 must not move canonical resolution into MCP or change MCP
  resource URI contracts.
- No database migration or historical reference rewrite is justified by this
  Story. A future migration Story may address legacy references only after the
  compatibility contract is approved.
- Current active-status and project-ownership checks remain caller/security
  concerns until the application authorization boundary is designed.

## 10. Refined Story 0138

### Objective

Define and implement, after human design approval, the smallest Core-owned
deterministic shared capability that dispatches a canonical reference to the
authoritative family-specific resolver while preserving source, provenance,
revision, snapshot and capability semantics.

### Scope

1. Resolve the source-selection contradiction before implementation.
2. Define parser/dispatch and structured result/error semantics without a
   universal `Evidence` aggregate.
3. Implement one vertical slice covering source-scoped Git commit resolution,
   revision-pinned document/file resolution, and one persisted domain family
   only after the boundary is approved.
4. Preserve task-snapshot resolution as a separate explicit mode.
5. Add deterministic no-fallback and failure tests.
6. Keep MCP as an adapter and keep ranking, composition, trust, transport and
   authorization policy outside the resolver.

### Explicit non-goals

- No universal Evidence entity or ContextPack.
- No resolver-owned ranking, relevance, retrieval, composition, grounding,
  trust promotion, human validation or AI behavior.
- No complete authorization architecture.
- No MCP-specific canonical resolver.
- No historical data rewrite or Story 0139 migration.
- No generic resolver framework until the first slice demonstrates the need.
- No expansion for bounded non-expandable structure aggregates.

### Invariants

1. Canonical identity never implies authorization, trust, relevance, ranking or
   composition.
2. An explicit source or revision is never replaced by active-source ordering or
   HEAD/current state.
3. Ambiguous source selection fails deterministically.
4. Task snapshot resolution cannot silently use current context.
5. Every successful result preserves the requested canonical reference and
   applicable source/provenance/revision metadata.
6. Every family retains authority over its own model or repository backing.
7. Legacy source-less references are not upgraded by inference.
8. Structure aggregates remain bounded projections unless a future Story
   explicitly defines expansion.
9. Shared resolution does not produce trusted knowledge or broaden access.

### Acceptance criteria to review

1. A human-approved shared boundary dispatches at least Git commit, pinned
   document/file and one persisted domain family.
2. Resolver family ownership and result discriminators are explicit.
3. Unknown, unsupported, ambiguous, unavailable, revision, not-found,
   no-longer-resolvable, unauthorized and unsupported-expansion outcomes are
   distinguishable where applicable.
4. Tests prove no fallback to another source, HEAD, current context or another
   task snapshot.
5. Results preserve canonical identity, provenance, source and revision state.
6. `AiReferenceResolver` remains the task-mapping authority.
7. MCP handlers remain thin projections and no new transport-specific identity
   is introduced.
8. Bounded structure projections resolve only as non-expandable results.

### Dependencies

- Human resolution of the active-source ambiguity contradiction.
- Story 0137 canonical reference semantics.
- ADR-063 retrieval/expansion and Human Context Supremacy boundaries.
- ADR-068 separation of canonical identity from task-scoped `AiReference`.
- Existing `DocumentReference`, `RepositoryRevisionScope`, workspace manager,
  history repositories and snapshot resolver.

## 11. Proposed Implementation Slices

| Slice | Scope | Mode recommendation |
|---|---|---|
| 0 | Resolve source ambiguity policy and align all callers with it | LEARN / PAIR |
| 1 | Define family parsing, result metadata and stable failure semantics; add no-fallback tests only | LEARN |
| 2 | Adapt existing source-scoped Git commit and pinned document/file capabilities | PAIR |
| 3 | Add one persisted domain resolver, preferably Fact or Insight after scope decision | LEARN / PAIR |
| 4 | Integrate task snapshot as an explicit resolution mode without replacing `AiReferenceResolver` | PAIR |
| 5 | Add thin Core-facing adapter tests for MCP/REST projections | DELEGATE after seam review |
| 6 | Add repetitive family adapters and compatibility fixtures for additional families | DELEGATE |

The slices deliberately do not migrate every evidence family at once. Story
0139 and later work remain unauthorized by this refinement.

## 12. LEARN / PAIR / DELEGATE Recommendations

- **LEARN:** source/revision scope, dispatch semantics, identity versus
  authorization, family-specific result modeling and deterministic failures.
  These are the architectural decisions that should remain understandable and
  substantially human-owned.
- **PAIR:** the first result/error seam, Git/document vertical slice and task
  snapshot boundary. Pairing reduces the risk of a god-service or accidental
  current-state fallback while preserving the learning value.
- **DELEGATE:** repetitive resolver adapters, repository fixtures, parser tables,
  compatibility fixtures and negative tests after the family contract is
  reviewed and accepted.

These are recommendations, not the final pedagogical decision.

## 13. Remaining Human Decisions

1. Should `EVIDENCE_NO_LONGER_RESOLVABLE` be reserved for immutable snapshots,
   or also cover deleted historical Git paths and unavailable documents?
2. What authorization-scope value can be passed now without inventing the
   future principal/role model?
3. Which current domain entities have approved historical semantics versus
   current-state-only resolution?

The source policy, explicit facade choice and envelope-plus-family-payload
contract were decided for Slice 1. Slice 2 uses the existing repository,
workspace and secure-reader authorities without duplicating them.
Fact was selected as the first persisted family for Slice 3, with analysis
scope carried explicitly in the resolution request.

## 14. Next Vertical Slice

After review of the Slice 6 seam, continue with human review and any separately
authorized compatibility work:

1. Review the six persisted-family adapters and their scope semantics.
2. Preserve `TaskSnapshotEvidenceResolver` as the originating-snapshot path.
3. Preserve `AiReferenceResolver` as the task-mapping authority.

The next slice must preserve the same explicit scope and no-fallback semantics;
MCP remains a later thin adapter over the Core boundary.

## Repository and DevLog Evidence

Repository inspection covered:

- `RepositoryEvidence`, all current Repository Context collectors, ranker,
  selector, enrichers and MCP mapper;
- `DocumentReference`, `RepositoryRevisionScope`, `WorkspaceManager`,
  `GitWorkspaceManager`, `RepositoryStatePort` and secure content reading;
- `AiReference`, registry/factory/snapshot/resolver and
  `TaskSnapshotEvidenceResolver`;
- `RepositoryEvidenceResolverImpl`, Fact/Observation lineage and temporal
  assessment;
- persisted history, domain services/repositories and MCP resource handlers.

DevLog applicability was checked for project `devlog-ai`. The targeted history
search returned no matching commits, and the Story context endpoint could not
be used because it requires a DevLog UUID while the repository Story identifier
is the local number `0138`. No DevLog historical claim was used in place of
repository evidence.

## Final Status

```text
SLICE_4_COMPLETE
SOURCE_POLICY_ALIGNED
EXPLICIT_FACADE_SEAM_IMPLEMENTED
GIT_DOCUMENT_ADAPTERS_IMPLEMENTED
FACT_ADAPTER_IMPLEMENTED
SNAPSHOT_BOUNDARY_VERIFIED
SLICE_5_COMPLETE
CONSUMER_PROJECTIONS_IMPLEMENTED
SLICE_6_COMPLETE
REPETITIVE_FAMILY_ADAPTERS_IMPLEMENTED
STORY_0138_IMPLEMENTATION_COMPLETE
STORY_0138_TECHNICALLY_VALIDATED
STORY_0138_ARCHITECTURE_ACCEPTED
KNOWN_APPLICATION_SECURITY_LIMITATION_RECORDED
FOLLOW_UP_SECURITY_STORY_REQUIRED
STORY_0139_DEFERRABLE
HUMAN_GIT_ACTION_REQUIRED
```
