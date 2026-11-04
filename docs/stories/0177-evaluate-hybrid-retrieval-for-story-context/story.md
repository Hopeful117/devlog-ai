# Story 0177 - Evaluate Hybrid Retrieval for Story Context

## Status

**IMPLEMENTATION AUTHORIZED - AWAITING HUMAN ACCEPTANCE**

This Story authorizes an offline retrieval evaluation only. It is not an
authorization to implement RAG, embeddings, vector search, or production
retrieval changes. Story acceptance and deployment remain subject to the
established human approval gates.

## Problem

Story Context currently uses deterministic structured and lexical retrieval,
followed by Core-owned ranking, selection, enrichment, and projection. The
recent budget experiments show that increasing the selected item ceiling from
60 to approximately 120 increases the estimated provider input from roughly
70k to 150k tokens and causes targeted scenarios to time out.

The evidence currently establishes a composition and projection bottleneck,
but it does not yet establish whether lexical retrieval also misses relevant
evidence because the wording of a question differs from the wording of the
repository evidence. A semantic or hybrid retrieval channel may improve recall,
but it could also increase noise, prompt size, latency, revision leakage, or
grounding risk.

The project needs measured evidence before deciding whether a future
revision-aware hybrid retrieval capability, potentially including vector
similarity, is justified.

## Goal

Build a replayable offline evaluation that compares the current deterministic
retrieval baseline with one or more experimental hybrid retrieval candidates.
The evaluation must determine whether semantic retrieval improves evidence
recall without weakening scope, trust, grounding, reproducibility, or bounded
latency.

The result is a decision record with one of these outcomes:

1. lexical and structural retrieval are sufficient;
2. a bounded hybrid retrieval channel is justified for further architectural
   design; or
3. the observed problem is composition/projection rather than retrieval and
   RAG is not justified.

## Governing Artifacts

- ADR-006 - AI Proposal and Knowledge Promotion Workflow
- ADR-063 - Engineering Context Retrieval and Composition Architecture
- ADR-066 - Replayable AI Intent Evaluation Harness
- ADR-067 - DevLog Engineering Story Context Agent
- Story 0111 - Structured Engineering Context
- Story 0112 - DevLog Engineering Story Context Agent
- Story 0158 - DevLog Story Agent Response Specificity
- Story 0173 - Story Context Budget Policy and Provider Failure Diagnostics
- Story 0176 - Story Context Grounding and Retry Efficiency
- `docs/investigations/analysis-context-composition-design.md`
- `docs/mcp-server-audit.md`

## Architectural Constraints

1. Java/Core remains the sole authority for project scope, source/revision
   scope, authorization, trust tiers, candidate identity, composition,
   grounding, and final acceptance.
2. Python AI Engine receives only the authorized evaluation context. It must
   not access the database, repository, retrieval index, or embedding service.
3. A similarity score never changes a trust tier, currentness status, or
   grounding eligibility.
4. Every evaluated candidate must carry a canonical reference, provenance,
   source/revision scope, category, trust tier, and citability metadata.
5. Repository and document evidence must remain revision-pinned. A candidate
   from another revision invalidates the capture.
6. Evaluation indexes or embeddings, if used, are rebuildable technical
   projections and are never authoritative project knowledge.
7. The existing MCP and REST contracts are not changed by this Story.
8. No production default, selection policy, provider policy, or grounding rule
   is changed by this Story.

## In Scope

- Define a frozen evaluation corpus containing targeted, broad, semantically
  indirect, insufficient-evidence, truncated-context, and revision-bound
  scenarios.
- Record selected and discarded candidate references, selection reasons,
  candidate metadata, budget accounting, projection digests, and effective
  revision for every replay.
- Establish the current structured/lexical retrieval and composition behavior
  as the control condition.
- Provide an offline candidate-producer seam for comparing:
  - current lexical retrieval;
  - lexical plus structural/path signals;
  - an experimental semantic candidate producer;
  - a bounded hybrid producer combining eligible channels.
- Evaluate semantic candidates only after deterministic project, source,
  revision, trust, and evidence-family filters have been applied.
- Measure evidence recall at bounded candidate sizes and final composition
  sizes rather than treating a larger context as an improvement.
- Measure grounding validity, requested-file coverage, revision isolation,
  trust-tier preservation, selected/discarded counts, estimated input size,
  latency, timeout rate, and replay reproducibility.
- Include negative controls where the answer is absent, the requested file is
  outside scope, the evidence belongs to another revision, or a superseded
  document is the nearest semantic match.
- Reuse the ADR-066 replayable evaluation harness and existing Story Context
  scenarios where possible.
- Produce a decision report identifying whether the next bottleneck is
  retrieval recall, candidate selection, content enrichment, document
  retrieval, projection size, or provider execution.

## Out of Scope

- Production RAG or retrieval changes.
- Adding embeddings, a vector database, pgvector, an external retrieval
  service, or a new production dependency.
- Changing `RepositoryContextEngine`, Story Context selection policy, prompt
  projection, grounding validation, or MCP/REST behavior for production use.
- Python-side context construction, retrieval, indexing, authorization, or
  repository access.
- Autonomous follow-up retrieval or generic agent loops.
- Treating embedding similarity as evidence, trust, authority, or grounding.
- Increasing production token, item, document, or content budgets as a result
  of a passing experiment.
- Replacing deterministic retrieval merely because a semantic candidate scores
  higher.
- Selecting a vector technology or approving an implementation architecture.

## Evaluation Model

The evaluation must compare candidates through the following bounded pipeline:

```text
frozen project/revision scope
        -> authorized candidate producers
        -> canonical metadata and trust filters
        -> bounded candidate union
        -> consumer-owned composition
        -> projection and grounding contract
        -> offline metrics
```

The semantic producer may be implemented as an offline adapter or test double
for the evaluation. Its implementation must be replaceable and must not be
wired into the production application.

## Acceptance Criteria

### AC-1 - Frozen and reproducible corpus

The evaluation corpus contains at least one scenario for each required class:

- exact targeted file or document;
- semantically indirect question;
- multi-source architecture question;
- insufficient evidence;
- intentionally truncated context;
- revision mismatch or superseded document negative control.

Each scenario records its repository revision, project/source scope, question,
requested files, expected evidence references, expected abstention behavior,
and evaluation version. Replaying the same corpus and candidate-producer
versions produces the same candidate identities and metrics.

### AC-2 - Evidence-level capture

Every replay records, for each candidate condition:

- candidate and selected canonical references;
- selection and rejection reasons;
- evidence family, trust tier, citability, provenance, and revision;
- requested-file coverage;
- selected/discarded counts and estimated token accounting;
- context and projection digests;
- grounding outcome;
- latency, timeout, and provider-call status when applicable.

Captures contain no credentials, authorization headers, full provider prompts,
or raw provider secrets.

### AC-3 - Control baseline

The current structured/lexical pipeline is replayed unchanged as the control.
Its recall, precision or noise proxy, selected-context size, grounding outcome,
latency, and timeout rate are reported separately from experimental conditions.

### AC-4 - Hybrid candidates are safely filtered

Semantic or hybrid candidates are excluded when they violate project scope,
source scope, revision scope, trust policy, evidence-family policy, or
canonical-reference requirements. A high similarity score cannot make an
unauthorized or non-citable candidate eligible.

### AC-5 - Recall and quality are measured together

The report includes at least:

- evidence recall at candidate and final-context levels;
- requested-file/document coverage;
- false-positive or noise rate using the frozen negative controls;
- grounding acceptance rate;
- correct-abstention rate;
- selected item and token counts;
- projection size and truncation warnings;
- p50/p95 latency and timeout rate;
- replay reproducibility.

No condition is considered better based on recall alone.

### AC-6 - No safety regression

All conditions preserve fail-closed behavior for unknown references, wrong
revisions, out-of-scope files, superseded documents, unsupported relationship
claims, and insufficient evidence. Existing grounding and truncation tests
remain passing.

### AC-7 - Decision report and architectural boundary

The evaluation produces a report that explicitly states:

- whether semantic retrieval solved a measured recall gap;
- whether the gain survives final Core composition;
- the cost in prompt size, latency, and timeouts;
- which evidence types benefit or regress;
- whether the issue is retrieval, selection, enrichment, projection, or
  provider execution;
- whether a future hybrid retrieval Story is justified.

The report must not present a vector technology, embedding model, or RAG
implementation as approved architecture.

## Validation

- Replay the offline corpus through the ADR-066 evaluation harness.
- Run deterministic tests for scope, revision, trust, canonical references,
  negative controls, and metric aggregation.
- Run the existing Story Context and grounding test suites.
- Verify that the production runtime, Docker configuration, MCP contract, and
  Python retrieval boundary are unchanged.
- Inspect the complete diff and confirm that no vector store, embedding
  dependency, production retrieval path, or unauthorized artifact was added.

## Expected Deliverables

- Versioned evaluation scenarios and expected evidence manifest.
- Offline candidate-producer comparison and metric capture.
- Replayable evaluation tests.
- Evaluation result and decision report.
- Recommendation for a future architecture Story, or explicit evidence that
  no RAG/hybrid retrieval work is currently justified.

## Human Decisions Required After Evaluation

- Whether the measured recall gap justifies a new retrieval architecture
  decision.
- Whether a future hybrid channel may use embeddings and which operational
  constraints it must satisfy.
- Whether the project should prioritize retrieval recall or composition and
  projection improvements based on the measured result.

## Non-Authorization Notice

Completion of this Story, a passing evaluation, or a positive recommendation
does not authorize production RAG, embeddings, vector search, budget changes,
or changes to the Java Core/Python AI Engine boundary. Any such work requires
separate human architectural approval and implementation authorization.
