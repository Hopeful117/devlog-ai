# Implementation Plan - Story 0177

## Status

Implementation Plan completed. Human approval for the offline evaluation was
given in the current session. No production implementation is authorized by
this artifact.

## Objective

Add replayable, offline evaluation infrastructure that compares the current
structured/lexical Story Context retrieval with experimental structural,
semantic, and hybrid candidate conditions. The evaluation will measure recall
and safety together while keeping all candidate authorization, revision
filtering, trust semantics, composition, and grounding authority in Java/Core.

This plan does not implement RAG, embeddings, vector search, a vector store, or
any production retrieval change.

## Governing Constraints

- Java/Core remains authoritative for project/source/revision scope,
  authorization, candidate identity, trust, composition, grounding, and
  acceptance.
- The AI Engine receives only authorized context and remains independent from
  repositories, databases, indexes, and embedding services.
- Evaluation code depends on production contracts; production code must not
  depend on evaluation code.
- Similarity never changes trust, currentness, citability, or grounding
  eligibility.
- All candidates must have canonical references and revision/provenance
  metadata before recall is measured.
- Replay is deterministic and does not call a model. Live capture remains
  optional, manual, and separate from normal CI.
- No production budget, selection, prompt, MCP, REST, provider, or grounding
  policy changes are included.
- Existing uncommitted repository-context, benchmark, Playwright, and OAuth
  changes are pre-existing and must not be reverted or silently included.

## Proposed Implementation Sequence

### 1. Define the evaluation capture contract

Introduce a versioned evaluation-owned capture model for one retrieval
condition. It must represent:

- scenario and condition identity;
- project, source, and resolved revision;
- candidate references and selected references;
- selection/rejection reasons;
- evidence family, trust tier, citability, provenance, and related references;
- requested-file/document coverage;
- candidate/selected/discarded counts and token estimates;
- context/projection digests and warnings;
- grounding, abstention, timeout, and completion outcomes;
- latency and provider-call metadata when available.

The capture must reject credentials, authorization headers, raw provider
payloads, full prompts, and secrets. If the authenticated Core snapshot lacks a
required bounded field, add only an additive diagnostic/evaluation projection;
do not expose a second retrieval universe or alter selection behavior.

Likely areas:

- `ai-engine/evaluations/` capture and metric models;
- `ai-engine/evaluations/story_context_budget_live.py` as a live-capture
  precedent;
- Story Context snapshot/projection classes under
  `backend/.../storycontextanalysis/` only if bounded fields are unavailable;
- corresponding contract tests.

### 2. Freeze the scenario corpus and expected evidence

Add a versioned Story 0177 corpus under `ai-engine/evaluations/scenarios/`.
Reuse the semantic shape of Story 0158 but define explicit evidence-level
expectations and revision scope.

Required cases:

- exact targeted file/document;
- semantically indirect wording;
- multi-source architecture question;
- insufficient evidence with expected abstention;
- deliberately truncated context;
- wrong-revision evidence;
- out-of-scope requested file;
- superseded document nearest-match negative control.

Each case must define project/source/revision identity, question, intent,
requested files, expected evidence references, expected abstention, negative
controls, and corpus version. Expected evidence belongs only to scenario data,
never evaluator code or production prompts.

### 3. Define offline candidate conditions

Create an evaluation-only candidate-producer interface or equivalent protocol
with explicit condition identity and deterministic output.

Implement conditions in this order:

1. `LEXICAL_CONTROL`: current structured/lexical candidate behavior;
2. `STRUCTURAL_CONTROL`: lexical plus path/category/relationship signals;
3. `SEMANTIC_EXPERIMENT`: an offline replaceable semantic test double or
   adapter, not a production provider;
4. `HYBRID_EXPERIMENT`: bounded union/deduplication of eligible conditions.

Before union or ranking, apply deterministic filters for project, source,
revision, trust tier, evidence family, canonical reference, and citability.
The hybrid condition must record which producer surfaced each candidate and
must remain bounded before consumer composition.

No backend production service may import or invoke these evaluation producers.

### 4. Reuse Core composition without changing production policy

Feed each condition through the same bounded composition semantics used by the
consumer, or through a faithful evaluation adapter when a direct invocation is
not possible.

The comparison must preserve:

- requested-file priority;
- category floors/ceilings;
- item and token budgets;
- enrichment limits;
- document limits;
- truncation and warning semantics;
- canonical grounding allow-lists.

The implementation must distinguish candidate-level recall from final-context
recall so that semantic retrieval cannot claim success when composition drops
the expected evidence.

### 5. Implement deterministic safety filters and negative controls

Add tests proving that every condition rejects:

- a candidate from another project;
- a candidate from another source;
- a candidate from another revision;
- an untrusted or excluded candidate;
- an out-of-scope requested file;
- a superseded document when currentness rules exclude it;
- a candidate without canonical citability metadata.

Similarity must never bypass these filters. Insufficient-evidence cases must
remain correct abstentions rather than being filled by semantically similar
content.

### 6. Add metrics and decision reporting

Implement deterministic aggregation for:

- candidate-level and final-context evidence recall;
- requested-file/document coverage;
- false-positive/noise proxy and negative-control violations;
- grounding acceptance and correct-abstention rate;
- selected/discarded counts and estimated token usage;
- projection size and truncation warnings;
- p50/p95 latency and timeout rate when timing data exists;
- condition reproducibility and digest stability.

Reports must keep control and experimental conditions separate and must state
whether any gain survives final Core composition. A recall gain with a material
timeout or grounding regression cannot pass the condition gate.

### 7. Add replay fixtures and harness integration

Extend the ADR-066 harness with the Story 0177 scenario loader, capture
validation, condition comparison, metrics, and JSON/text report generation.

The replay path must be model-free and suitable for deterministic tests. Live
captures may be consumed by the same evaluator only after schema validation and
must be explicitly marked as live, paid, and non-CI evidence.

Likely areas:

- `ai-engine/evaluations/`;
- `ai-engine/tests/test_evaluation_harness.py`;
- new focused evaluation tests;
- existing Story Context scenario validation tests.

### 8. Produce the decision report

The final evaluation report must classify the next bottleneck as one or more of
retrieval recall, candidate selection, content enrichment, document retrieval,
projection size, or provider execution.

It must explicitly state whether a future hybrid retrieval architecture Story
is justified. It must not select a vector database, embedding model, or
production RAG design.

### 9. Documentation reconciliation

Update only the canonical Story 0177 evaluation documentation required by the
implemented capture format and report semantics. Do not modify the historical
generic evaluation README unless the final implementation demonstrably changes
its current scope. If no update is required outside the Story directory,
record that outcome in the Implementation Report.

## Test Plan

### AI Engine evaluation tests

- scenario and expected-evidence manifest validation;
- capture redaction and bounded-size validation;
- deterministic condition and metric aggregation;
- candidate/final-context recall calculations;
- digest and reproducibility checks;
- noise and correct-abstention gates;
- report rendering and condition separation.

### Backend tests

Only if the existing snapshot cannot expose the required bounded capture data:

- additive snapshot field compatibility;
- canonical reference, revision, trust, and citability projection;
- no change to runtime selection or grounding behavior;
- old/new snapshot compatibility.

Existing retrieval boundary, Story Context budget, grounding, and projection
tests must remain passing.

### Validation commands

Focused first:

```bash
cd ai-engine && python -m pytest \
  tests/test_evaluation_harness.py \
  tests/test_story_context_specificity.py -q
./backend/mvnw -pl backend test \
  -Dtest=SharedEvidenceRetrievalBoundaryTest,GitRetrievalResolutionInteroperabilityTest -B
```

Broader validation:

```bash
cd ai-engine && python -m pytest -q
./backend/mvnw -pl backend -am clean verify -B
git diff --check
```

Live validation is optional and must remain outside normal CI. It must use
non-secret bounded captures and must not be the only acceptance evidence.

## Risks and Mitigations

- **Semantic noise:** apply hard scope/trust/revision filters before ranking and
  measure negative controls.
- **Prompt inflation:** cap candidate union and compare final-context size,
  not only candidate recall.
- **Revision leakage:** require exact resolved revision on every candidate and
  invalidate mismatched captures.
- **Grounding drift:** preserve canonical references and run existing
  fail-closed grounding checks.
- **Non-reproducible embeddings:** keep the semantic producer offline,
  versioned, replaceable, and separate from live replay evidence.
- **Confounding composition:** report candidate-level and final-context-level
  recall separately.
- **Worktree contamination:** isolate Story 0177 files and explicitly exclude
  pre-existing uncommitted changes from attribution.

## Implementation Boundary

This plan authorizes no implementation until explicitly approved. It does not
authorize:

- production RAG, embeddings, vector search, or a vector store;
- changes to production retrieval, ranking, selection, enrichment, budgets, or
  grounding;
- Python repository/database/index access;
- MCP, REST, authentication, credential, or provider changes;
- commit, push, deployment, or Story acceptance.

## Next Gate

`IMPLEMENTATION_AUTHORIZED_OFFLINE_EVALUATION`

Human approval of this Implementation Plan is required before implementation.
