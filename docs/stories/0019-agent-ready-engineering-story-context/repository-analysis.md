# Repository Analysis — Story 0019

## Status

Ready for human review.

## Executive Summary

Story 0019 addresses a confirmed transport-budget defect rather than a missing Repository Context
capability. The Core already constructs a deterministic, revision-pinned, ranked, enriched, and
digestible `RepositoryContext`. The Engineering Story endpoint currently serializes that complete
internal object directly. Consequently, the 6,000-token Repository Context budget does not bound
the payload actually delivered to Kiko.

The representative Story 0019 request returned 60 selected items from 124 candidates and reported
3,186 used tokens, but serialized 164,445 bytes. Evidence occupied 131,539 bytes; repeated
`score`/`rankingReasons` metadata alone occupied 70,455 bytes; 124 individual selection decisions
occupied another 26,094 bytes. Content and symbol payloads represented only 1,921 bytes in this
specific response. The external context therefore spends most of its space explaining selection,
not conveying implementation evidence.

The correct architectural seam is a dedicated, versioned projection inside the `projectcontext`
boundary. It should consume the final authoritative `RepositoryContext`, create a compact DTO for
the default Engineering Story response, and leave the domain object unchanged for AI-task
snapshots and explicit diagnostics. The existing adapter already requires only
`repositoryContext.evidence` with string `reference` and `summary`; a compact default can preserve
that contract without consumer-side re-ranking or an immediate skill migration.

Technical recommendation: approve Story 0019 for planning with a dedicated DTO/projection service,
default compact POST/GET response, and explicit `detail=full` diagnostic mode. Do not use Jackson
views or mutate the Repository Context engine to solve a transport concern.

## Inputs and Repository State

Analysis used:

* the complete Story 0019 description;
* DevLog Repository Context from project `f3d56247-aada-4a76-982b-e6802c0b309c`;
* current repository revision `7d32a5c85ada9f7f5a22d0691881fe6a028312bf`;
* `README.md`, roadmap, architecture and Repository Context ADRs;
* current project-context, Repository Context, ranking, selection, content, and symbol code;
* current WebMvc/service tests;
* the live Engineering Story adapter and its contract tests;
* three repeated live baseline measurements using the complete Story.

The repository was on branch `main`. Before creation of Story 0019, the working tree was clean.
The only current uncommitted files are the new Story 0019 workflow artifacts.

## DevLog Context Outcome

DevLog returned usable Repository Context and therefore served as the primary discovery input.

Context metadata:

* context engine: `repository-context-engine-v1`;
* active profile: `engineering-story-v1`;
* context intelligence: `context-intelligence-v2`;
* candidates: 124;
* selected evidence: 60;
* discarded evidence: 64;
* reported used tokens: 3,186;
* warnings: `REPOSITORY_CONTEXT_BUDGET_APPLIED` and `EVIDENCE_SUMMARY_TRUNCATED`;
* digest: `1e712dbb4697366b7c71d26271fe7fea4f8629206820770e8b09b1ed9df0bf50`.

The context correctly prioritized the Repository Context engine, evidence ranker, evidence model,
symbol/content enrichment, controller tests, ADR-era commits, and previous implementation reports.
Targeted repository reads confirmed those navigation signals.

It also demonstrated the Story defect directly: selection was dominated by Git-history and
commit-diff evidence, and the adapter output repeated extensive ranking metadata. This observation
does not authorize Story 0019 to change ranking semantics. It does establish that the compact
projection must retain layer counts and bounded warnings so a later precision Story can diagnose
selection composition independently.

## Current Request Path

The live call follows this path:

```text
Engineering Story adapter
  → POST /api/projects/{projectId}/engineering-story-context
  → EngineeringStoryContextController
  → EngineeringStoryContextServiceImpl
  ├─ ProjectContextProvider.build(projectId)
  └─ RepositoryContextAdapter.buildRepositoryContext(projectId, description)
       ├─ ProjectContextProvider.build(projectId)  [second call]
       ├─ synthesize AnalysisContext
       ├─ load validated Insights
       └─ RepositoryContextService.build(...)
            → collect → rank → select → symbols → content → digest
  → serialize complete EngineeringStoryContext
       ├─ complete ProjectContextSnapshot
       └─ complete RepositoryContext
```

There is no HTTP-specific response DTO. `EngineeringStoryContext` embeds the domain
`RepositoryContext` directly, so every domain field becomes public transport data by default.

`EngineeringStoryContextServiceImpl` also builds the same `ProjectContextSnapshot` once for the
outer response and again inside `RepositoryContextAdapter`. This duplicate read is unnecessary and
is a safe latency optimization within the project-context boundary.

## Quantified Baseline

Three consecutive requests using the complete Story 0019 description produced identical byte
counts:

| Measurement | Run 1 | Run 2 | Run 3 |
|---|---:|---:|---:|
| HTTP status | 200 | 200 | 200 |
| Duration | 811 ms | 812 ms | 738 ms |
| Response bytes | 164,445 | 164,445 | 164,445 |

Payload contributors from the final response:

| Contributor | Bytes | Share of response |
|---|---:|---:|
| Project Context | 4,698 | 2.9% |
| Repository Context | 159,609 | 97.1% |
| Selected evidence array | 131,539 | 80.0% |
| Score plus repeated ranking metadata | 70,455 | 42.8% |
| Selection decisions | 26,094 | 15.9% |
| Content plus symbols | 1,921 | 1.2% |

The categories overlap where score/ranking data is inside evidence; they identify contributors, not
an additive accounting table.

An earlier request immediately after stack activity exceeded the adapter's three-second timeout.
Later requests completed in roughly 0.7–2.0 seconds. This proves a bounded cold-path risk, not a
stable performance regression. Implementation planning should benchmark a controlled rebuilt-stack
first request and warm requests before changing the consumer timeout.

## Relevant Architecture and Decisions

### Repository Context ownership

ADR-037, ADR-038, and ADR-039 establish that the Java Core owns repository-first context,
collector execution, deterministic intelligence, ranking, selection, budgets, diagnostics, and
traceability. ADR-044 and ADR-045 add post-selection content and Java-symbol enrichment under the
same global context budget.

Story 0019 must operate after these phases. It may omit transport-only duplication but must not
re-rank evidence, select rejected candidates, recalculate domain diagnostics, or alter stored AI
task context.

### Project-context boundary

The `projectcontext` package is already the integration boundary for the external Engineering Story
workflow. `RepositoryContextAdapter` synthesizes an AnalysisContext without a persisted Analysis,
and `EngineeringStoryContextController` owns the product-specific endpoint. A projection in this
package preserves dependency direction: project-context depends on Repository Context, not the
reverse.

### Trust boundary

README and the skill reference state that DevLog supplies navigation and prioritization context;
the current repository remains authoritative. The compact response must preserve resolved revision
and warnings so the agent can identify synchronized-versus-current-repository differences.

## Detailed Findings

### Finding 1 — The domain token budget does not cover transport metadata

`RepositoryEvidence.estimatedTokens()` estimates summary, reference, content, and symbol
characters. It does not account for complete `EvidenceScore` maps, weights, explanations,
provenance field names, extraction metadata, duplicated `rankingReasons`, context diagnostics,
selection decisions, or the outer Project Context.

`RepositoryContextEngine` correctly reports the selector/enrichment budget it owns. Renaming that
field or forcing the engine to absorb HTTP serialization would conflate two responsibilities. The
agent projection needs its own policy, budget, and accounting.

### Finding 2 — Ranking explanations are serialized twice

`RepositoryEvidence.score.explanations` and `RepositoryEvidence.rankingReasons` contain overlapping
or identical values. Content and symbol allocation reasons may repeat final score and match
strength again. This duplication is useful during deep diagnostics but unnecessary in the default
agent view.

A compact evidence DTO should retain the final score and a bounded, stable reason list selected
from existing final decisions. It must not derive a new score.

### Finding 3 — Rejected-candidate detail has low default consumer value

`RepositoryContext.selectionDecisions` contains one row for every candidate, including 64 rejected
items in the representative response. The agent needs to know that a budget was applied and how
many candidates were rejected for each stable reason; it does not need every rejected path during
normal Story preparation.

Diagnostic mode should preserve the current complete list. The compact view should expose sorted
aggregate rejection counts.

### Finding 4 — Selected evidence still requires careful compaction

Removing rejected decisions alone saves only about 16% of the response. Most bytes are inside the
60 selected evidence objects. The projection therefore needs explicit compact DTOs for score,
provenance, content, and symbols rather than merely hiding `selectionDecisions`.

Source/test content and symbol declarations remain high-value and must be preserved preferentially.
The representative response contained little content because its selected set was history-heavy;
Story 0019 should not use that accident to design a content-free contract.

### Finding 5 — Exact accounting is self-referential unless canonical input is defined

Embedding a byte count or digest and then measuring the complete object containing that value has a
self-reference. The Story was refined during analysis: canonical semantic projection bytes exclude
the digest and accounting fields themselves. The response may additionally rely on HTTP
`Content-Length` for complete wire bytes.

The same canonical bytes should feed the projection digest and deterministic token estimate. Field
ordering must be stable through records, sorted maps/lists, and the configured Jackson mapper.

### Finding 6 — Token counts are model-dependent

There is no provider-neutral exact token count. The repository consistently uses deterministic
character-based estimates. Story 0019 should expose that result explicitly as an estimate and make
UTF-8 bytes the provider-independent hard bound. Adding a provider tokenizer would couple the Core
to one model vocabulary and is not justified.

### Finding 7 — The adapter contract can remain compatible without editing the skill

The current adapter validates only that `repositoryContext.evidence` is a non-empty array with
string `reference` and `summary`. It ignores extra fields and does no ranking. A compact default
response retaining those fields is compatible.

The adapter has independent Node tests for body transport, response validation, errors, and timeout.
Story implementation should run those tests against fixtures representing the new response. A
timeout change would be durable skill behavior and should not be performed by directly editing the
skill from this repository workflow; it would require the governed skill-update path if benchmark
evidence proves it necessary.

### Finding 8 — Project Context is built twice

`EngineeringStoryContextServiceImpl` and `RepositoryContextAdapter` independently call
`ProjectContextProvider.build(projectId)` for one request. Passing the already built immutable
snapshot into a narrow adapter overload removes duplicate database reads and keeps existing public
behavior. This is a proportionate latency correction inside Story scope.

### Finding 9 — Projection alone cannot correct evidence relevance

The representative selected set contains many historical Story files and few direct current source
files. Compacting those items makes transport efficient but does not make them more relevant.
Changing ranking weights, layer caps, selection diversity, or history quotas would invalidate the
Story's architectural constraint and the evidence engine's established behavior.

The post-Story benchmark must separately report transport reduction and evidence composition. If
actionable source/test evidence remains insufficient, create a later precision Story rather than
hiding a selection defect inside the projector.

## Existing-Solution Preflight

The relevant maintained mechanisms already exist in the stack:

* Jackson records and the configured deterministic `ObjectMapper` for stable DTO serialization;
* Spring MVC query parameters or a dedicated path for explicit diagnostic mode;
* SHA-256 through the JDK, already used for Repository Context digests;
* application-property configuration for bounded policies;
* existing Repository Context DTOs and adapter contract tests.

Options considered:

### Jackson `@JsonView` or property filters

Rejected as the primary design. Views hide fields but do not model versioned projection semantics,
deterministic degradation, aggregate rejection counts, canonical byte accounting, or a separate
digest cleanly. They also couple the rich domain model to one HTTP consumer.

### Custom Jackson serializer for `RepositoryContext`

Rejected. A context-sensitive serializer would make the same domain object serialize differently
by endpoint, obscure tests, and risk changing AI-task snapshot serialization.

### Compression only

Rejected. Gzip reduces network bytes but the adapter still expands the same verbose JSON into the
agent context window. It does not solve delivered-token cost or semantic noise.

### New tokenization library

Rejected for V1. Exact model tokenization is provider-specific and would add coupling without
controlling the provider-independent payload. A UTF-8 byte bound plus documented deterministic
estimate is sufficient.

### Dedicated immutable projection DTO

Recommended. It makes the contract explicit, versioned, testable, budgetable, and isolated from the
domain model using only existing dependencies.

No new production dependency or paid service is justified.

## Recommended Design Direction

Planning should use these boundaries:

1. Keep `RepositoryContext`, `RepositoryEvidence`, engine digests, AI-task snapshots, ranking, and
   enrichment unchanged.
2. Add an `AgentRepositoryContextProjectionService` or equivalently focused component under
   `projectcontext`.
3. Define compact immutable DTOs for context metadata, evidence, provenance/navigation, score,
   content outcome, symbol outcome, and aggregate selection diagnostics.
4. Build the projection from final selected evidence in its existing order.
5. Retain `reference`, `summary`, layer/kind, resolved revision, final score, bounded reasons,
   actionable provenance, content text/status, and Java declarations.
6. Replace rejected-candidate rows with stable sorted reason counts.
7. Calculate canonical semantic bytes, deterministic estimate, and projection digest after
   deterministic compaction.
8. Apply a separate projection byte/estimated-token policy without modifying the domain context
   budget. Prefer compacting optional explanations/related references before content or complete
   evidence removal.
9. Return the compact projection by default and the unchanged full representation only through an
   explicit `detail=full` mode.
10. Reuse one `ProjectContextSnapshot` throughout the request.
11. Add phase timing around snapshot construction, Repository Context construction, projection, and
    serialization using existing structured logging conventions.
12. Keep the adapter unchanged unless controlled cold/warm measurements demonstrate a remaining
    timeout incompatibility.

## Contract Considerations

The endpoint is externally consumed even though no Angular component calls it. Changing
`repositoryContext` from the full domain JSON to a compact projection is an intentional API
evolution. Compatibility is defined at two levels:

* default agent consumers retain `repositoryContext.evidence[].reference` and `.summary`;
* diagnostic clients opt into `detail=full` to retain the complete previous JSON.

The response should expose an explicit projection version so consumers never infer its shape from
the internal engine version. Unknown `detail` values should use the standard invalid-parameter
error rather than silently choosing a mode.

GET compatibility can remain for existing short descriptions, but POST remains canonical for
complete Stories.

## Expected Impacted Areas

Likely production changes:

* `projectcontext/EngineeringStoryContext.java` or a new agent-specific response record;
* `projectcontext/EngineeringStoryContextController.java`;
* `projectcontext/EngineeringStoryContextService` and implementation;
* `projectcontext/RepositoryContextAdapter.java` for snapshot reuse;
* new compact projection DTOs, policy, service, and digest/accounting component;
* `application.properties` for projection byte/token/reason/reference limits;
* optionally Docker environment mappings only if overrides are required.

Likely tests:

* focused projector/policy/digest tests;
* Engineering Story service and WebMvc tests;
* compatibility tests proving the domain Repository Context and AI-task snapshot remain complete;
* existing Node adapter contract tests using the compact fixture;
* representative disposable benchmark scripts/results outside Git.

Canonical documentation likely requiring reconciliation:

* `README.md` Engineering Story Context and limits;
* `docs/architecture.md` external projection boundary;
* ADR-038/ADR-044/ADR-045 references if clarification is sufficient;
* a new ADR only if planning changes the public/default diagnostic contract more broadly than this
  isolated projection.

No frontend production change is required. This is an agent-integration and backend contract Story.

## Test and Validation Strategy

Planning must include:

* golden compact JSON for every relevant evidence family;
* tests proving rejected paths and duplicate explanations are absent;
* deterministic order, canonical bytes, estimates, and digest vectors;
* boundary tests immediately below/at/above every projection limit;
* degradation tests preserving evidence identity and warnings;
* full diagnostic-mode equality with the existing Repository Context;
* unchanged Repository Context engine and AI-task serialization tests;
* POST complete-Story and GET compatibility WebMvc tests;
* real adapter fixture tests without consumer re-ranking;
* complete Maven/JaCoCo/SonarQube validation;
* Docker first-request and repeated-request latency measurements;
* before/after byte and delivered-token comparison using the exact same Story and project revision;
* targeted human verification that key implementation files, tests, ADRs, commits, and validated
  knowledge remain discoverable.

Benchmark artifacts should remain disposable and outside the repository. A single local result may
prove this response-size regression; it must not be generalized into universal productivity or
latency claims.

## Risks and Mitigations

### Risk: compacting away necessary provenance

Mitigation: define mandatory navigation fields by evidence family and golden-test each family.
Retain full diagnostic mode.

### Risk: accidental mutation of internal snapshots

Mitigation: introduce separate immutable DTOs and test existing Analysis/AI-task serialization
unchanged.

### Risk: two incompatible digests confuse consumers

Mitigation: name both explicitly: authoritative `repositoryContextDigest` and transport-specific
`projectionDigest`, with separate versions and documented canonical inputs.

### Risk: projection budget creates a hidden second ranker

Mitigation: preserve selected order and use only mechanical, documented compaction. If complete
evidence removal is unavoidable, remove from the deterministic tail and report counts; never
re-score or promote rejected items.

### Risk: byte budget is correct but agent tokens remain high

Mitigation: enforce bytes as the hard provider-neutral bound, report a conservative deterministic
estimate, and measure actual delivered representation in the adapter benchmark.

### Risk: endpoint remains slower than the adapter timeout

Mitigation: remove duplicate Project Context construction, measure controlled cold/warm phases, and
only then propose a governed adapter timeout change. Do not add unbounded retries.

### Risk: default contract change surprises diagnostic users

Mitigation: document the compact default, provide explicit full mode, and test both. No current
frontend consumer exists.

## Open Questions for Implementation Planning

These questions are bounded implementation decisions, not blockers for analysis approval:

1. Should diagnostic mode use `?detail=full` on the current endpoint or a dedicated `/diagnostics`
   path? The query mode minimizes routing duplication; a dedicated path is more explicit.
2. What initial hard byte limit best matches the target agent window? The baseline supports testing
   32–40 KB before choosing a final configured value.
3. Should the outer `ProjectContextSnapshot` also receive a compact projection? It is only 4.7 KB in
   the baseline, so repository evidence offers the dominant first gain.
4. Which evidence reason has the highest audit value: final selection reason, top score terms, or
   allocation reasons? The contract should retain one bounded representation, not all three.
5. Can controlled first-request latency remain below three seconds after duplicate snapshot removal
   and smaller serialization, or is a separately governed adapter timeout proposal required?

## Acceptance-Criteria Feasibility

All acceptance criteria are feasible with existing repository architecture and dependencies.

The most important planning constraints are:

* AC-4 must use a non-self-referential canonical semantic payload;
* AC-5 mechanical compaction must not become new ranking;
* AC-6 must preserve exact rich diagnostics;
* AC-8 timeout changes outside this repository require governed skill handling;
* AC-14 must compare identical Story/revision inputs and report evidence composition as well as
  bytes.

No missing external credential, paid service, new database migration, or frontend capability blocks
implementation.

## Recommendation

Proceed to Implementation Planning after explicit human approval.

Recommended Story boundary: build a compact, versioned transport projection at the Engineering
Story endpoint, preserve full diagnostics explicitly, reuse one Project Context snapshot, and prove
the gain with actual serialized bytes. Defer ranking/selection relevance changes and proposal-review
workflow improvements to later Stories.
