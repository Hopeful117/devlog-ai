# Design: Validated Knowledge Integration into Story Context Analysis

Design artifact. No production code was modified.

## Status

```text
VALIDATED_KNOWLEDGE_SCA_DESIGN = REVISION_COMPLETE_AWAITING_HUMAN_REVIEW
PRIMARY_DIRECTION              = ACCEPTED
GROUNDING_MODEL                = PROVENANCE_INTERSECTION
SELECTION_IS_STORY_AWARE       = NO (current), YES_WITH_SMALL_CHANGE (designed V1)
PROJECT_WIDE_CANDIDATES        = INSUFFICIENTLY_STORY_AWARE
ADR_REQUIRED                   = NO
RAG_READINESS                  = NOT_READY
IMPLEMENTATION                 = NOT_AUTHORIZED
```

## Revision Summary

This revision preserves the accepted direction to reuse `KnowledgeSelectionService` and
include Facts, Observations, and Insights in V1. It corrects two claims from the original
design:

1. A selected Fact's historical `evidenceReferences` do not automatically expand the
   grounding universe. They may ground a new finding only when the same canonical
   reference is present in the authorized current `EngineeringContext`.
2. `KnowledgeSelectionService` is intent-aware but does not currently rank Facts,
   Observations, or Insights against the current Engineering Story. Project-wide newest-N
   retrieval would add material recency bias and cross-analysis ambiguity.

The previously proposed Python grounding expansion and project-wide Fact/Observation
repository methods are removed from the implementation plan.

---

## 1. Current Pipeline and Disconnect

```text
Engineering Story
        |
        v
EngineeringContextFacade
        |
        v
authorized EngineeringContext
        |
        +--> repositoryContext.evidence -> current grounding candidates
        |
        v
AnalyzeStoryContextUseCase.buildSelectedKnowledge()
        |
        +--> selectedFacts = []
        +--> selectedObservations = []
        +--> selectedInsights = []
        |
        v
Python prompt + defensive validation
        |
        v
StoryContextAnalysis snapshot
```

`AnalyzeStoryContextUseCase.buildSelectedKnowledge()` currently hard-codes all three
selected knowledge lists as empty. The Python prompt already requires these sections, so
the integration point remains valid.

The current grounding path has additional repository inconsistencies relevant to this
design:

- Java builds a grounding contract from `EngineeringEvidence.identifier` and
  `relatedReferences`, but `AiTaskServiceImpl.createForStoryContextAnalysisEntity()`
  accepts and then ignores that contract.
- Python reconstructs an allow-list from `repositoryContext.evidence[].reference` and
  `relatedReferences`.
- `RepositoryEvidence.reference` is the canonical evidence identity, but
  `EngineeringContextContractMapper` maps only `provenance.identifier` into
  `EngineeringEvidence.identifier`; the contract has no `reference` field.
- Story Context Analysis callbacks do not perform the authoritative Java grounding
  revalidation required by Story 0112 D14 and ADR-067.

These are pre-existing grounding-boundary defects. The future implementation must repair
them rather than broaden Python authority.

---

## 2. Repository Grounding Semantics

### 2.1 Existing concepts are distinct

| Concept | Repository representation | Semantic role |
|---|---|---|
| Source evidence | `RepositoryEvidence.reference`, content, symbols, provenance | Repository-observed material available to a consumer |
| Knowledge provenance | `Fact.evidenceReferences`, `Observation.supportingFacts`, `Insight.evidenceReferences` and proposal lineage | Explains why persisted knowledge exists |
| Knowledge identity | Fact/Observation/Insight UUID; `fact:`, `observation:`, `insight:` references | Identifies a knowledge object, not automatically its source |
| Citation | Story Context Analysis `EvidenceRef.reference` | Pointer emitted by generated output |
| Grounding evidence | Java-authorized canonical references in the execution grounding contract | Exact references a new finding may cite |
| Resource | `EvidenceRef.resource` | Optional navigation metadata, never identity or trust authority |

ADR-063 explicitly separates retrieval, composition, projection, grounding, and expansion.
It also requires each citable element to carry one canonical reference and visible
non-citable elements to be distinguishable. Story 0112 D13 defines
`EvidenceRef.reference` as that canonical grounding key. Story 0112 D14 makes Java/Core
the sole grounding authority.

Therefore:

```text
knowledge is visible
!= knowledge is citable
!= its provenance is current grounding evidence
```

### 2.2 Existing standard-analysis model

Standard Insight generation admits selected current-analysis Fact IDs, Observation IDs,
Fact `evidenceReferences`, and selected repository evidence references. That model is safe
there because the Facts and Observations belong to the Analysis being generated and the
selection service enforces Observation-to-Fact closure.

It does not establish that arbitrary historical Fact provenance is current evidence for a
later Story Context Analysis. `RepositoryEvidenceResolverImpl` can traverse persisted
Insight/Proposal/Observation/Fact lineage, but resolution capability is not grounding
authorization.

### 2.3 Corrected Story Context Analysis model

The selected model is **PROVENANCE_INTERSECTION**, governed by current evidence:

```text
authorized current EngineeringContext evidence references = C
selected historical Fact provenance references           = P

admissible Fact-derived grounding = P intersect C
```

Operationally, Java constructs the allow-list from canonical references of evidence
actually present in the authorized `EngineeringContext` and admitted by the SCA
consumer's citability policy. It does not add `P` separately. The intersection happens
naturally: a historical provenance value is usable only if the same canonical value is
already in `C`.

For V1, selected Insights remain context-only even if another projection exposes their
identity. Current scoped technical evidence and current human-authored repository evidence
may be citable when Java admits the item for the finding category. Trust tier alone does
not decide citability; ADR-063 defines these as separate properties.

`relatedReferences` are relationship/expansion links. They must not automatically become
grounding references merely because they are attached to selected evidence. A related
reference is citable only when it is independently present as a canonical citable
reference in the authorized context.

This preserves the safety property:

> A newly generated finding cannot claim grounding in evidence that the analysis context
> cannot actually justify.

### 2.4 Authoritative contract flow

```text
RepositoryEvidence.reference
        |
        v
EngineeringEvidence.reference (canonical, additive contract field)
        |
        v
Java grounding contract (immutable per execution)
        |
        +--> Python prompt and defensive validation
        |
        v
Java callback validation (authoritative)
```

Python must consume the Java contract, not reconstruct it from selected knowledge. This
is not a new architectural decision; it restores approved Story 0112 D13/D14 semantics.

---

## 3. Knowledge Semantics for V1

### 3.1 Facts

Facts remain deterministic `TECHNICAL_EVIDENCE` and remain included in V1. Their identity,
content, source, detection time, and provenance are useful context.

For Story Context Analysis:

- A selected historical Fact may inform reasoning.
- Its `evidenceReferences` remain historical provenance.
- The selected Fact does not directly expand the grounding allow-list.
- A provenance reference may be cited only when it intersects current authorized evidence.
- A Fact identity may be cited only if that identity is independently represented as a
  canonical citable item in the authorized `EngineeringContext`.

Classification:

```text
FACTS_CAN_DIRECTLY_GROUND_NEW_FINDINGS = CONDITIONAL
HISTORICAL_PROVENANCE_EXPANDS_ALLOWED_EVIDENCE = NO
```

### 3.2 Observations

Observations remain deterministic derived knowledge and remain included in V1.
`supportingFacts` preserve explicit derivation closure but do not convert an Observation
into raw current evidence.

For Story Context Analysis:

- A selected Observation may inform reasoning.
- All selected supporting Facts must remain in selected Facts.
- Supporting Fact provenance is subject to the same current-evidence intersection.
- The Observation itself may be cited only if its canonical identity is independently
  present as citable authorized evidence.
- Deterministic derivation does not imply automatic grounding inheritance.

Classification:

```text
OBSERVATIONS_CAN_DIRECTLY_GROUND_NEW_FINDINGS = CONDITIONAL
```

### 3.3 Insights

Insights remain included as trusted contextual knowledge. They may influence
interpretation, identify prior human-promoted understanding, and reduce repeated analysis.

Their historical evidence references do not become current technical evidence, and their
human validation does not transfer trust to newly generated output.

```text
Insight
  -> trusted contextual knowledge
  -> may influence interpretation
  -> does not directly ground a new finding
  -> does not make new output trusted
```

Classification:

```text
INSIGHTS_CAN_DIRECTLY_GROUND_NEW_FINDINGS = NO
```

### 3.4 V1 knowledge set

| Type | V1 | Role |
|---|---|---|
| Facts | Include | Deterministic contextual knowledge; conditional grounding by current-evidence intersection |
| Observations | Include | Deterministic derived context; preserve supporting-Fact closure |
| Insights | Include | Trusted context only; no direct technical grounding or trust inheritance |
| HumanContextInputs | Include | Human-authored context; not automatic technical evidence |
| KnowledgeRelations | Include | Explicit relationships; no confidence-based promotion |
| Decisions | Defer | Accepted review decision; different selection semantics |
| EngineeringEvents | Defer | Accepted review decision; evolution-specific semantics |

---

## 4. Selection Trace

### 4.1 Candidate retrieval, ranking, and final budget

These are separate stages:

```text
candidate retrieval -> ranking/composition -> final context budget
```

In the standard analysis workflow, `AnalysisContextServiceImpl` retrieves the newest 100
Facts and 50 Observations from the current Analysis before selection. It repairs
Observation-to-Fact closure. `KnowledgeSelectionServiceImpl` then selects at most 40 Facts
and 25 Observations. Active Insights are loaded project-wide and the newest 10 are selected.

The previous design incorrectly proposed applying the standard 100/50 windows across all
project analyses. No current repository method or invariant supports that interpretation.

### 4.2 Signals actually used

| Signal | Available? | Used by direct Fact/Observation/Insight ranking? | How | Story-specific? |
|---|---|---|---|---|
| Intent ID | Yes | Yes / Yes / No | Hard-coded type-name score groups for Facts and Observations | No |
| Intent objective | Yes | No | Used only by repository-evidence ranker | No unless objective contains Story text |
| Context profiles | Yes | No | Weight repository-evidence ranking | No |
| Fact/Observation type | Yes | Yes | Principal direct score | No |
| Fact/Observation content | Yes | Yes | User-guidance term overlap only | Only if guidance contains Story terms |
| Fact source/evidence paths | Yes | Tie only / No | Deterministic ordering; not semantic ranking | No |
| Observation supporting Facts | Yes | Closure only | Required Facts retained or Observation removed | No |
| Insight title/content/type/severity | Yes | No | Main list is ACTIVE, newest-first, top 10 | No |
| Recency | Yes | Candidate gate / top-10 order | Newest 100/50 upstream; newest Insights | No |
| User priorities/focus/outputContext | Yes | Facts and Observations only | Lexical boost over type/content | Potentially, but caller-controlled |
| Story ID | Yes | No | Post-ranking commit-window filter in `RepositoryContextAdapter` | Scope only |
| Story title | Yes | No | Story is an independent ROADMAP evidence item | No candidate-to-Story comparison |
| Story path | Yes | No | Originating path of independent Story evidence | No candidate-to-Story comparison |
| Base/target commits | Yes | No | Post-ranking technical-evidence filter | Scope only |
| Story objective/description/acceptance criteria | No structured fields | No | Persisted `EngineeringStory` does not contain them | No |
| Requested files | Yes | No | Post-ranking technical-evidence filter | Scope only |
| Knowledge relations | Yes | No | Preserved/projected, not used to rank knowledge | No |
| Repository evidence | Yes | Separate path | Multi-criterion intent, architecture, history, recency, provenance, guidance | Weakly Story-adjacent only |

### 4.3 Engineering Story path

`AnalysisContext.engineeringStories` exists, but `KnowledgeSelectionServiceImpl.select()`
does not consume it when ranking Facts, Observations, or Insights. Stories are converted
into independent `ROADMAP` evidence using title, path, status, and commit metadata. Their
presence does not increase another candidate's score.

`storyId` and requested files are applied after repository ranking as scope filters. They
can remove evidence but cannot promote relevant candidates or refill unused budget.

Additionally, `EngineeringContextFacadeImpl` currently passes the intent ID as the
adapter's `storyDescription`. Therefore the adapter's lexical Fact/Observation filtering
is intent-aware, not aware of the requested Story title or contents.

Current classification:

```text
SELECTION_IS_INTENT_AWARE = YES
SELECTION_IS_STORY_AWARE = NO
```

The repository-context path has Story-based post-selection scope, but that is not
Story-aware ranking.

---

## 5. Candidate Retrieval Decision

### 5.1 Rejected strategy

Reject:

```text
newest 100 Facts across project
newest 50 Observations across project
        -> KnowledgeSelectionService
```

Reasons:

1. Relevant older knowledge can be excluded before ranking; the selector cannot recover it.
2. Facts and Observations are owned by an Analysis; global rows mix baselines and revisions.
3. Observation supporting-Fact closure is analysis-scoped and would need cross-analysis
   reconstruction not represented in current snapshots.
4. Repeated deterministic extraction across analyses introduces duplicate/staleness
   ambiguity.
5. Breadth across a project does not create Story relevance.

The hidden candidate recency bias is **MATERIAL**.

### 5.2 Existing safer repository pattern

`RepositoryContextAdapter` already provides a bounded deterministic pattern for Story
preparation:

```text
latest ProjectProfile Analysis (single comparable baseline)
        -> newest 200 Facts / 200 Observations
        -> deterministic term overlap
        -> relevance order, then recency tie-break
        -> bounded candidates (currently 8 Facts / 6 Observations)
```

This preserves one Analysis provenance boundary and applies relevance before the final
small cap. It still has a bounded 200-row recency window, but it is safer than global
newest-N retrieval and follows ADR-063's bounded candidate-polling model.

### 5.3 Corrected V1 candidate semantics

Reuse that baseline-analysis pattern, with a small Story-aware correction:

1. Resolve the requested persisted Story before candidate construction.
2. Build deterministic selection terms from fields already authorized and available:
   Story title, Story path, requested files, intent objective, and normalized user
   guidance focus/priorities/output context.
3. Retrieve a bounded overfetch window from the latest ProjectProfile Analysis only.
4. Rank term overlap before the candidate cap; use recency and stable identity only as
   tie-breakers.
5. Repair Observation-to-Fact closure within that same baseline Analysis before invoking
   `KnowledgeSelectionService`.
6. Pass only the requested Story in the SCA `AnalysisContext.engineeringStories` section.
7. For the SCA intent, add the same deterministic Story-term relevance signal to the
   selector's Fact, Observation, and Insight ordering. In particular, Insights must no
   longer be newest-only for this intent.
8. Apply commit-window and requested-file scope before final composition where the
   canonical evidence model permits it; retain existing fail-closed behavior.
9. Invoke the existing `KnowledgeSelectionService` for closure, budgets, repository
   composition, diagnostics, metadata, and digest.

No Story Markdown parser is introduced. Objective, description, and acceptance criteria
are not structured persisted Story fields today; claiming to rank on them would be false.

### 5.4 Cross-Story knowledge

Cross-story/project knowledge remains allowed. The corrected semantics do not mean
"current Story only". They mean:

```text
single coherent baseline
  + current Story relevance signals
  + project knowledge allowed to compete
  + deterministic budgets
```

General architecture or build knowledge can still rank highly. Unrelated recent project
knowledge no longer wins eligibility solely because it is newer.

Classification of the rejected project-wide newest-N strategy:

```text
PROJECT_WIDE_CANDIDATE_STRATEGY = INSUFFICIENTLY_STORY_AWARE
```

The smallest safe correction is relevance-before-cap over one baseline plus SCA-specific
Story-term ranking, not project-wide newest-N queries.

---

## 6. Corrected Minimal V1 Design

```text
Engineering Story + files + guidance
        |
        v
Java resolves Story and constructs deterministic Story selection terms
        |
        v
latest comparable baseline Analysis
        |
        +--> bounded Fact/Observation overfetch
        +--> Story relevance before cap
        +--> Observation/Fact closure
        |
        v
SCA AnalysisContext (current Story only; coherent baseline candidates)
        |
        v
KnowledgeSelectionService
        +--> SCA Story-term ordering
        +--> existing budgets and closure
        +--> relevant ACTIVE Insights, not newest-only
        +--> SelectedKnowledge projection
        |
        v
authorized EngineeringContext canonical evidence references
        |
        v
Java immutable grounding contract
        |
        +--> Python defensive validation
        +--> Java authoritative callback validation
        |
        v
persist non-trusted StoryContextAnalysis snapshot
```

### Lifecycle requirement

`KnowledgeSelectionService` requires a matching Analysis, ProjectProfile, and persisted
`AnalysisExecutionDiagnostic`. Today `createForStoryContextAnalysisEntity()` creates or
reuses the SCA Analysis only after selected knowledge has been built, and accepts selected
knowledge in the same call. Future implementation must separate "establish SCA analysis
and diagnostics" from "create task with selected knowledge" so selection has valid inputs.
It must not fabricate a synthetic persisted Analysis ID or weaken mandatory diagnostics.

### Budget

The existing final budget remains unchanged:

```text
maximumFacts                 = 40
maximumObservations          = 25
maximumInsights              = 10
maximumArchitectureKnowledge = 5
maximumRepositoryEvidence    = 60
```

Candidate overfetch is not the final budget. Exact SCA overfetch/candidate caps should use
the existing adapter constants initially and remain configurable only if repository-scale
evidence later justifies adjustment.

---

## 7. Grounding Contract Design

### Java authority

Java constructs `allowedEvidenceReferences` exclusively from canonical references of
citable evidence in the authorized, scoped `EngineeringContext`. Scope and SCA citability
remain Java decisions; trust tier is preserved but does not alone imply citability.
Selected knowledge cannot broaden this set, and Insights are context-only in V1.

The contract must be persisted with the task or its immutable execution snapshot, sent in
`PromptRequest`, and reused for authoritative callback validation.

### Python responsibility

Python receives the explicit contract and uses it for prompt instructions, defensive
subset validation, and corrective retry. It must not scan `selectedFacts`,
`selectedObservations`, `selectedInsights`, or `repositoryContext` to create additional
allowed references.

The previous changes proposed for `_grounding_contract()` and `_validate_output()` to add
all Fact `evidenceReferences` are therefore removed. Those functions instead need to
consume the Java-provided contract.

### Finding requirements

- Every `FACTUAL_EXTRACTION` and `AI_INTERPRETATION` must have `grounded=true` and at
  least one evidence reference from the Java allow-list.
- `RECOMMENDATION` may be ungrounded but must remain explicitly classified.
- Unknown or fabricated references are rejected defensively in Python and authoritatively
  in Java.
- `EvidenceRef.resource` remains navigation-only and does not enter subset validation.
- Related references do not become citable unless independently authorized canonically.

No Story Context Analysis output-schema change is needed.

---

## 8. Freshness and Historical Analyses

Freshness semantics remain unchanged:

```text
ProjectFreshnessStatus != specific knowledge validity
```

Project freshness does not prove every selected historical Fact is currently true, and a
STALE project does not automatically invalidate every knowledge item. The persisted Story
0114 freshness snapshot remains project-level execution context.

Historical Story Context Analyses are not regenerated or backfilled. Future analyses use
the revised selection and grounding contract; prior snapshots preserve what was supplied
at their execution time.

---

## 9. ADR-066 Evaluation Strategy

The evaluation must make context influence and grounding authorization independently
observable.

### Required scenarios

1. A selected Fact is present in prompt knowledge.
2. A selected Observation and all its selected supporting Facts are present.
3. A selected Insight is present as trusted context.
4. Relevant selected knowledge measurably influences interpretation without requiring it
   to be cited as evidence.
5. A Fact historical provenance reference absent from current authorized evidence is
   rejected as grounding.
6. A Fact historical provenance reference also present as canonical current evidence is
   accepted.
7. An Observation identity/supporting Fact identity absent from the current evidence
   allow-list is rejected.
8. Insight identity and `Insight.evidenceReferences` do not automatically become allowed
   grounding.
9. Legitimate canonical current evidence grounds a factual or interpretative finding.
10. Fabricated evidence is rejected in Python and Java validation tests.
11. A factual/interpretative finding with an empty evidence list is rejected.
12. Empty selected knowledge remains valid and does not cause fabrication.
13. Story-relevant older baseline knowledge outranks newer irrelevant candidates inside
    the overfetch window.
14. A candidate outside the bounded window remains excluded and truncation is observable.
15. Insight selection for the SCA intent uses Story relevance before recency.
16. Freshness snapshot capture and persistence remain unchanged.
17. Historical analyses remain unchanged.

### Fixture correction

The ADR-066 fixture must include separate values for:

```text
selected knowledge provenance references
authorized current grounding references
disallowed historical-only provenance references
```

The evaluator must check non-empty grounding for factual/interpretative findings, not only
set inclusion when references happen to be present.

---

## 10. Future Implementation Impact Map

Implementation remains unauthorized.

### MUST_CHANGE

| Component | Required future change | Reason |
|---|---|---|
| `AnalyzeStoryContextUseCase` | Replace empty knowledge map with projected `SelectedKnowledge`; orchestrate selection after SCA Analysis/diagnostics exist | Integration point and valid selector lifecycle |
| SCA Analysis/task creation in `AiTaskService` or a focused lifecycle service | Separate Analysis/diagnostic establishment from task creation; persist grounding contract | Removes current ordering cycle and ignored contract parameter |
| `RepositoryContextAdapter` or extracted shared bounded-candidate capability | Reuse single-baseline overfetch; add current Story/files/guidance terms; preserve Fact closure | Story-aware candidates without global queries |
| `KnowledgeSelectionServiceImpl` | Add SCA-specific deterministic Story relevance to Fact, Observation, and Insight ordering while preserving existing budgets | Current direct selection is not Story-aware; Insights are newest-only |
| `EngineeringEvidence` and `EngineeringContextContractMapper` | Preserve canonical `RepositoryEvidence.reference` distinctly from provenance identifier | Story 0112 D13 and ADR-063 canonical identity |
| Java `PromptRequest` plus Python request schema | Carry explicit immutable Java grounding contract | Story 0112 D14; Python must not reconstruct authority |
| `StoryContextAnalysisPromptBuilder` | Render supplied grounding contract; do not expand from knowledge provenance | Defensive generation contract |
| `StoryContextAnalysisGenerationService` | Validate against supplied contract; require non-empty refs for factual/interpretative findings | Defensive validation and safety property |
| Dedicated Java SCA callback validator / callback path | Authoritatively validate evidence subset and required grounding before persistence | ADR-067 and Story 0112 D14 |
| Selection metadata | Expose baseline/window/relevance strategy and truncation | Makes candidate recency limits auditable |
| Backend, AI Engine, ADR-066 tests | Cover provenance intersection, Story ranking, closure, empty context, freshness regression | Verification |

### MUST_NOT_CHANGE

| Component or decision | Constraint |
|---|---|
| `KnowledgeSelectionService` role | Reuse it; do not create a parallel selector |
| Final `KnowledgeBudget` | Preserve 40/25/10/5/60 for V1 |
| Fact/Observation/Insight V1 inclusion | Preserve accepted review decision |
| Decision/EngineeringEvent deferral | Preserve accepted review decision |
| `StoryContextAnalysisResult` output schema | No change required |
| `EvidenceRef` shape | Keep `{reference, resource?}`; resource remains navigation-only |
| Insight trust semantics | Context does not transfer trust to generated output |
| Story 0114 freshness | No redesign or per-item freshness system |
| Historical analyses | No backfill or regeneration |
| Java/Core authority | No Python-side authority reconstruction |
| Architecture | No RAG, vectors, new agent, microservice, or event-driven redesign |

### Removed from the previous plan

- Do not add project-wide `FactRepository.find...ByProjectId...` or
  `ObservationRepository.find...ByProjectId...` newest-N methods for this integration.
- Do not add every selected Fact provenance value to Python's grounding allow-list.
- Do not treat `relatedReferences` as automatically citable.
- Do not leave `AiTaskService`, `EngineeringContext`, or Java callback validation unchanged;
  repository evidence shows they participate in the current grounding defect.

---

## 11. Final Decisions

### Grounding

```text
GROUNDING_MODEL = PROVENANCE_INTERSECTION

new finding grounding
  -> canonical current evidence authorized by Java
  -> historical provenance usable only on exact intersection
  -> selected knowledge cannot expand authority
```

### Selection

```text
CURRENT KNOWLEDGE SELECTION
  -> intent-aware
  -> not Story-aware for direct Facts/Observations/Insights
  -> Story scope is primarily post-ranking filtering

DESIGNED V1
  -> one comparable baseline Analysis
  -> bounded overfetch
  -> current Story relevance before final candidate cap
  -> SCA-specific deterministic Story signal in KnowledgeSelectionService
  -> existing closure and final budget
```

### Governance gates

```text
ADR_REQUIRED = NO
```

The grounding corrections restore accepted Story 0112/ADR-063/ADR-067 rules. The
selection correction is a consumer-specific deterministic composition policy already
allowed by ADR-063, not a new architecture.

```text
RAG_READINESS = NOT_READY
```

The current gap is deterministic query construction and ranking, not lack of vector
retrieval. Existing baseline retrieval, lexical terms, commit scope, file scope, and
budgets are sufficient for V1 after the specified small change.

---

## 12. Remaining Limitations

1. Persisted Stories expose title/path/status/commit bounds, not structured objective,
   description, acceptance criteria, or component scope. V1 cannot honestly rank on
   fields it does not have.
2. The bounded baseline window retains a recency eligibility boundary. Relevant knowledge
   older than the window can still be missed; selection metadata must expose truncation.
3. Latest ProjectProfile Analysis is a coherent deterministic baseline, not proof that all
   included historical knowledge remains semantically current.
4. Explicit file and commit scope currently occur after repository ranking. Moving all
   scope before candidate retrieval may require collector-specific work beyond the first
   minimal implementation; fail-closed filtering must remain meanwhile.
5. The current canonical-reference projection and callback-validation defects predate this
   design. Implementation cannot be authorized safely without including their focused
   remediation in the implementation Story.
6. Knowledge can influence an interpretation without a machine-verifiable explanation of
   exactly which context item influenced reasoning. Grounding validation proves cited
   evidence membership, not complete reasoning provenance.

---

## Repository Evidence Reviewed

- `AnalyzeStoryContextUseCase`
- `AiTaskServiceImpl`
- `AnalysisContext` and `AnalysisContextServiceImpl`
- `KnowledgeSelectionServiceImpl`
- `SelectedKnowledge` and `SelectedKnowledgePromptProjectionService`
- `RepositoryContextAdapter`
- `RepositoryEvidence`, collectors, ranker, selector, and resolver
- `EngineeringContextContractMapper` and `EngineeringEvidence`
- Fact, Observation, Insight, Proposal, and Validation entities/services
- Story Context Analysis prompt, schema, generation service, evaluator, and fixtures
- standard Insight prompt and output validation
- ADR-006, ADR-008, ADR-022, ADR-063, ADR-064, ADR-066, ADR-067
- Story 0112 D13/D14 and Story 0114 freshness behavior

---

*Revised from repository evidence on `design/validated-knowledge-sca-integration` after
human architectural review. If repository reality contradicts this design, repository
reality wins.*
