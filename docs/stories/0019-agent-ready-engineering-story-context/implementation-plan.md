# Implementation Plan — Story 0019

## Status

Ready for human approval.

## Overview

Implement a dedicated, versioned HTTP projection for the Engineering Story integration while
leaving the authoritative `RepositoryContext` domain model and engine unchanged. The existing GET
and POST endpoint will return the compact agent representation by default. An explicit
`detail=full` query mode will return the current complete `EngineeringStoryContext` representation
for diagnostics.

The compact response will preserve `repositoryContext.evidence[].reference` and `.summary`, so the
current Engineering Story adapter remains compatible without a skill change. It will retain the
outer Project Context, selected evidence, essential provenance, resolved revision, final score,
bounded non-duplicated reasons, content, and Java declarations. Individual rejected candidate
decisions and full score maps will be replaced by aggregate counts.

A separate projection policy will enforce a 32 KiB canonical semantic-payload limit and an 8,192
deterministic-token estimate. Mechanical degradation will remove optional transport detail in a
fixed order and only remove selected evidence from the existing final tail as the last resort. It
will never re-rank evidence or promote a rejected candidate.

## Approved Inputs

Implementation will use:

* Story 0019;
* the human-approved Repository Analysis;
* current Repository Context ADRs 037, 038, 039, 044, and 045;
* the existing Engineering Story adapter contract;
* the disposable 164,445-byte baseline captured with the complete Story 0019 request.

No implementation work may begin before explicit approval of this plan.

## Planned Changes

### 1. Freeze the disposable pre-implementation benchmark

Before modifying production code, rerun the complete Story 0019 request against project
`f3d56247-aada-4a76-982b-e6802c0b309c` at revision
`7d32a5c85ada9f7f5a22d0691881fe6a028312bf` and store disposable evidence outside the repository,
under a temporary directory created with `mktemp -d`.

Record:

* exact request-description digest and repository revision;
* cold-first-request and at least three warm-request durations;
* complete HTTP response bytes;
* approximate delivered tokens using the same deterministic byte/4 convention used for the final
  comparison;
* Project Context and Repository Context byte contributions;
* candidate, selected, discarded, and per-layer counts;
* evidence, score/reason, selection-decision, content, and symbol byte contributors;
* selected evidence references grouped by layer/kind;
* warnings and authoritative Repository Context digest.

Do not commit benchmark payloads or generated scripts. Reuse the exact description and revision for
the post-implementation comparison. If the live Source advances during implementation, pin the
benchmark request through the same synchronized revision where possible or explicitly report that
the comparison is no longer exact.

### 2. Define the response-mode contract

Add a small `EngineeringStoryContextDetail` enum or equivalent parser in the `projectcontext`
boundary with two values:

* `agent` — compact projection and default when the query parameter is absent;
* `full` — unchanged rich diagnostic representation.

Both GET and POST accept optional `detail`. Unknown or blank-explicit unsupported values return the
standard `INVALID_PARAMETER` 400 response. POST remains canonical for complete Story bodies; GET
retains existing description-query compatibility.

The default response retains the outer shape:

```json
{
  "projectContext": {},
  "generatedAt": "...",
  "projectId": "...",
  "repositoryContext": {
    "projectionVersion": "engineering-story-agent-projection-v1",
    "evidence": []
  }
}
```

This preserves the adapter's only required path. Diagnostic mode returns the current JSON shape
without projection-only fields or loss of individual selection decisions.

Use one controller route with explicit mode dispatch rather than duplicating Repository Context
construction in separate endpoints.

### 3. Introduce immutable agent projection DTOs

Create focused records under a `projectcontext.projection` package (or an equivalently cohesive
package). The top-level projected Repository Context will contain:

* `projectionVersion` and projection policy/version;
* authoritative `repositoryContextVersion`, active profile keys, and context-plan version;
* selected evidence in the exact existing final order;
* selected counts by layer and kind;
* candidate, selected, discarded, duplicate, and truncation counts;
* stable rejected counts grouped by reason;
* Repository Context warnings plus projection warnings;
* authoritative `repositoryContextDigest`;
* `projectionDigest`;
* accounting with configured maximum bytes/tokens, canonical semantic bytes, estimated tokens, and
  degradation counters.

Each projected evidence item will contain only:

* layer, kind, reference, summary, and occurred-at timestamp when present;
* final relevance score;
* at most three stable unique reasons;
* at most three related references;
* compact provenance: source type, repository location, originating file, and identifier;
* compact extraction metadata: collector id/version and resolved revision when present;
* compact content and symbol outcomes.

Content projection retains status, text, reason, revision, policy/version, allocation rank, and
allocation policy/version. It omits duplicated allocation-reason arrays because final score and
bounded evidence reasons are already present.

Symbol projection retains status, reason, revision, policy/extractor versions, allocation rank,
truncation/counts, and Java declarations. Declarations preserve kind, name, owning type, modifiers,
return type, parameters, annotations, and source location. It omits duplicated symbol-allocation
reason arrays.

Do not annotate or modify `RepositoryContext`, `RepositoryEvidence`, `EvidenceScore`, content, or
symbol domain records for transport behavior.

### 4. Define the projection policy and configuration

Add an immutable `AgentContextProjectionPolicy` configured through existing Spring property
conventions:

* `devlog.engineering-story.agent-context.max-bytes` — default `32768`;
* `devlog.engineering-story.agent-context.max-estimated-tokens` — default `8192`;
* `devlog.engineering-story.agent-context.max-reasons-per-evidence` — default `3`;
* `devlog.engineering-story.agent-context.max-related-references-per-evidence` — default `3`.

Validate positive limits and sensible minimums during construction. Bytes are the hard,
provider-neutral authority. Estimated tokens use `ceil(canonicalUtf8Bytes / 4)` and must also fit
the configured token bound.

The initial limits intentionally target a fivefold reduction from the 164,445-byte baseline while
remaining large enough for selected navigation evidence. They are configuration, not an assertion
that every repository needs the same effective amount of context.

Do not add a tokenizer dependency or Docker override unless live validation demonstrates a need.

### 5. Build compact evidence without re-ranking

Add `AgentRepositoryContextProjectionService` consuming the final rich `RepositoryContext`.

For each already selected evidence item, construct its compact DTO in existing list order. Build
bounded reasons as a stable unique union of:

1. the selected `SelectionDecision.reason` for the same reference;
2. existing `rankingReasons` in their existing order;

excluding raw mechanical entries already represented structurally, such as repeated
`FINAL_SCORE=...`, criterion-weight arithmetic, policy markers, and allocation-reason duplicates.
The service may classify known prefixes but must not calculate a new relevance meaning. Unknown
reasons remain eligible rather than being silently discarded.

Aggregate only `selected=false` decisions into a lexically sorted map of rejection reason to count.
Never serialize their individual references, scores, or token estimates in agent mode.

Copy selected-by-layer and diagnostics counts into sorted stable maps. Do not recalculate collector
availability or selection outcomes from repository files.

### 6. Define canonical semantic serialization and dual digests

Create an internal canonical semantic record containing the projected Project Context and compact
Repository Context fields that affect agent meaning, but excluding:

* `generatedAt`;
* `projectionDigest`;
* canonical byte/token accounting fields;
* request latency/timing values.

Serialize it with the configured deterministic Jackson `ObjectMapper`. Use the resulting UTF-8
bytes to:

* enforce the projection policy;
* calculate `canonicalBytes`;
* calculate `estimatedTokens = ceil(bytes / 4)`;
* calculate lowercase SHA-256 `projectionDigest`.

Retain the Core-generated digest separately as `repositoryContextDigest`. Golden-vector tests will
prove stable digests, identical-input determinism, and sensitivity to evidence, content, symbols,
warnings, and Project Context changes.

The complete HTTP response can be larger than canonical bytes by the fixed accounting envelope and
`generatedAt`. Measure complete wire bytes in HTTP/benchmark tests; do not claim the canonical count
is `Content-Length`.

### 7. Apply deterministic mechanical degradation

Construct the richest bounded DTO first, then serialize and check both limits. When it exceeds a
limit, apply these stages in order, reserializing after every complete stage:

1. remove related references from evidence in reverse existing order, while retaining aggregate
   removal counts;
2. reduce evidence reasons to the first reason, preserving selection reason where available;
3. replace Java declaration lists from evidence in reverse order with the existing symbol outcome
   and counts only;
4. remove content text from evidence in reverse order while retaining content status, reason,
   revision, policy, and allocation rank;
5. remove complete evidence items from the existing final tail until the canonical payload fits.

Every degradation stage adds one stable projection warning and exact counters for related
references, reasons, declaration payloads, content payloads, and evidence items removed. The
remaining evidence retains its original order. No rejected evidence can enter the projection and no
remaining evidence is re-scored.

Require at least one usable evidence item. If even the minimal one-evidence projection cannot fit,
throw a bounded projection exception mapped through the existing standard 500 failure contract;
the adapter will emit `DEVLOG_CONTEXT_ERROR` and Repository Analysis will fall back safely.

The projector must be pure with respect to persistence and must never mutate domain lists or
records.

### 8. Preserve full diagnostics exactly

For `detail=full`, return the rich `EngineeringStoryContext` built by the existing service. Add
serialization regression tests comparing representative legacy fields, including:

* complete score criteria, weights, explanations, and match strength;
* complete ranking reasons;
* every selection decision;
* diagnostics;
* content and symbol allocation reasons;
* authoritative Repository Context digest.

No agent projection budget or degradation applies in full mode. The route is intended for humans,
debugging, and audit tooling, and documentation must warn that it is not appropriate for direct
agent-context injection.

### 9. Reuse one Project Context snapshot

Refactor `RepositoryContextAdapter` with a narrow overload accepting the already built immutable
`ProjectContextSnapshot`. `EngineeringStoryContextServiceImpl` will:

1. build the snapshot once;
2. pass it to Repository Context construction;
3. assemble the rich internal context once;
4. either return it unchanged for full mode or pass it to the projector for agent mode.

Retain the current adapter method as a compatible delegating overload if tests or internal callers
use it. Verify exactly one `ProjectContextProvider.build` call per endpoint request.

This refactor changes neither database contents nor Repository Context inputs.

### 10. Add bounded phase observability

Use the existing structured SLF4J conventions to log one INFO completion event for the Engineering
Story request with:

* project id and response mode;
* snapshot-construction duration;
* Repository Context construction duration;
* projection duration when applicable;
* candidate/selected counts;
* canonical and complete serialized bytes where known;
* authoritative and projection digests;
* total request duration.

Do not include the Story description, file content, symbol payload, repository credentials, or
unbounded exception data. Timings are observational and excluded from response digests.

Controller serialization occurs after service return, so complete wire-byte logging should use a
bounded response serialization seam only if it does not serialize twice in production. Otherwise,
log canonical bytes in the service and collect complete bytes in integration/benchmark evidence.

Do not add tracing, metrics, or caching infrastructure solely for this Story.

### 11. Preserve the adapter and fallback contract

Do not directly edit the installed Engineering Story skill. The default compact response will
remain compatible with its current validation:

* `repositoryContext` is an object;
* `repositoryContext.evidence` is an array;
* at least one evidence item has string `reference` and `summary`.

Run the existing Node adapter tests unchanged. Add a disposable live contract check that invokes
the real adapter with the complete Story and confirms it accepts the compact default.

Backend WebMvc tests will cover `detail=full`; the adapter itself never needs to request full mode.
If controlled Docker validation still shows routine first-request duration above three seconds,
stop and report the evidence. Any durable timeout change must be proposed through the governed skill
workflow and is not silently folded into this repository implementation.

### 12. Add focused projection coverage

Create focused unit tests for:

* representative SOURCE_FILE, TEST_FILE, COMMIT, CHANGED_FILE, ADR/decision,
  DOCUMENTATION/MILESTONE, analysis, and validated-knowledge evidence;
* compact final score and unique reason selection;
* absence of raw criteria/weight maps, duplicate ranking reasons, individual rejected references,
  and allocation-reason arrays;
* compact provenance and resolved revision;
* complete bounded content and Java declaration projection;
* sorted rejection counts and diagnostic maps;
* exact canonical byte and deterministic-token accounting;
* golden projection digest;
* identical-input determinism and input semantic-change sensitivity;
* each degradation stage independently and in combination;
* original evidence order and immutable domain input;
* minimum usable projection and impossible-limit failure;
* retained authoritative Repository Context digest.

Prefer fixtures/builders inside the focused test package over inflating shared production
constructors.

### 13. Add service, HTTP, and compatibility coverage

Update Engineering Story service/controller tests to cover:

* POST and GET defaulting to agent mode;
* explicit `detail=agent` equality with the default;
* explicit `detail=full` legacy JSON;
* invalid detail values using the standard error response;
* complete untruncated Story body transport;
* one Project Context snapshot build per request;
* compact adapter-required reference/summary fields;
* projection policy/accounting/digests/warnings serialization;
* omitted individual rejected candidates and rich score maps;
* projection failure producing a non-success standard response;
* unknown project and malformed/media-type compatibility.

Retain or extend Repository Context service and AI-task serialization regressions to prove the rich
domain object, stored selected-knowledge snapshot, context digest, and prompt inputs remain
unchanged.

Run the installed adapter's Node tests and a real live request; do not copy its implementation into
the repository.

### 14. Reconcile canonical documentation and architecture decision

Update:

* `README.md` — compact default response, `detail=full`, projection policy/limits, dual digests,
  failure behavior, and current-repository verification;
* `docs/architecture.md` — explicit boundary between rich Core Repository Context and agent-facing
  transport projection;
* relevant Repository Context documentation references where they currently imply the complete
  rich model is always returned by the Engineering Story endpoint.

Add ADR-046 because the Story intentionally separates a stable external agent projection from the
authoritative internal domain representation and changes the endpoint's default JSON contract. The
ADR should record:

* why compression, Jackson views, custom serializers, and provider tokenizers were rejected;
* compact-default/full-diagnostic split;
* dual budgets and dual digests;
* deterministic degradation without re-ranking;
* compatibility and current-repository trust boundary.

Do not update the roadmap: Story 0019 improves the already implemented Engineering Story Context
capability and does not complete a new product phase.

Record the documentation outcome in the Implementation Report.

### 15. Validate the complete behavior

Run validation in this order:

1. focused projection policy, mapper, canonical serialization, digest, and degradation tests;
2. Engineering Story service and WebMvc tests;
3. Repository Context engine, enrichment, AI-task snapshot, and digest regression tests;
4. existing installed adapter Node tests;
5. complete backend `./mvnw -q verify` with JaCoCo;
6. authenticated SonarQube analysis with Quality Gate wait;
7. rebuild and start the Docker backend on the dedicated local port;
8. controlled first-request and repeated warm-request measurements;
9. default compact API validation and `detail=full` diagnostic validation;
10. real adapter invocation using the complete Story 0019 body;
11. exact before/after benchmark using the same project, revision, and Story digest;
12. `git diff --check` and final repository review.

The post-implementation benchmark must report:

* complete response bytes and reduction percentage;
* deterministic estimated tokens and approximate adapter-delivered tokens;
* canonical versus wire bytes;
* cold/warm durations without claiming universal latency improvement;
* candidate/selected/discarded counts from the authoritative context;
* projected evidence count and degradation counters;
* retained key source/test/configuration, history/diff, ADR/documentation, and validated-knowledge
  references;
* full diagnostic preservation;
* any remaining selection-composition weakness.

Completion is blocked by any failing test/build, malformed adapter response, missing full
diagnostics, loss of all usable evidence, non-deterministic digest, wire-size regression, failed
Quality Gate, or new unresolved Story issue.

## Expected Files to Modify

Likely existing files:

* `backend/src/main/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContext.java`
* `backend/src/main/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextController.java`
* `backend/src/main/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextService.java`
* `backend/src/main/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextServiceImpl.java`
* `backend/src/main/java/com/hopeful117/devlogai/projectcontext/RepositoryContextAdapter.java`
* `backend/src/main/resources/application.properties`
* Engineering Story service/controller tests
* Repository Context/AI-task compatibility tests where required
* `README.md`
* `docs/architecture.md`

Likely new files:

* response-mode enum/parser;
* agent Engineering Story response record;
* compact Repository Context/evidence/content/symbol DTOs;
* projection policy;
* projection service and bounded exception;
* focused projection/policy/digest tests;
* `docs/decisions/ADR-046.md`;
* Story 0019 implementation, review, and engineering reports at their permitted stages.

No database migration, frontend production file, AI Engine file, ranking/selection implementation,
Repository Context domain-record change, or installed skill-file change is expected.

## Plan Compliance Boundaries

Implementation must stop and return for human guidance if it would require:

* changing ranking weights, candidate selection, layer diversity, or Repository Context limits;
* changing stored AnalysisContext, AI-task snapshots, or prompt construction;
* editing the installed Engineering Story skill or increasing its timeout without governed skill
  approval;
* adding a model-specific tokenizer or new external service;
* reading the local uncommitted working tree through DevLog;
* removing full diagnostics or weakening repository security;
* adding caching, durable jobs, monitoring infrastructure, or frontend scope;
* changing the approved 32 KiB/8,192-token defaults because the projection cannot fit a useful
  representative response without first presenting benchmark evidence.

## Recommendation

Ready for human plan approval.

After approval, implementation may begin. The workflow must then complete Documentation
Reconciliation and Code Review before stopping at Gate 3.
