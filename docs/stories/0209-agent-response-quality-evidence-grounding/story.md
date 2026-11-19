# Story 0209 - DevLog Agent Response Quality and Evidence Grounding

## Status

**IMPLEMENTATION IN PROGRESS**

Implementation is explicitly authorized by the human owner. This Story does not
authorize a commit, push, merge, or pull request.

## Problem

The DevLog Story Context Agent already preserves Core-owned context digests,
revision-scoped grounding references, trust metadata, uncertainty fields and
structured findings. The current response-quality controls do not fully measure
or enforce whether the principal objective summary is grounded, whether output
classification remains aligned with findings, or whether evaluation distinguishes
technical validity from useful and faithful engineering context.

The pipeline must be auditable from selected context through projection, prompt,
model output, validation and MCP/REST response. Quality improvements must not be
reduced to prompt wording changes or to a claim that a structurally valid answer
is necessarily a correct answer.

## Goal

Improve the measurable quality of DevLog Agent responses through one bounded
slice:

* add explicit grounding metadata to `objectiveUnderstanding`;
* validate the new grounding deterministically in Java Core and defensively in
  Python;
* preserve Core authority, trust tiers, provenance and revision semantics;
* extend the existing ADR-066 evaluation harness with response-quality checks;
* provide deterministic fixtures for grounded, insufficient and truncated
  context cases;
* keep MCP and REST contracts compatible through additive changes.

## Governing Artifacts

* ADR-006 - AI Proposal and Knowledge Promotion Workflow
* ADR-063 - Engineering Context Retrieval and Composition Architecture
* ADR-066 - Persist a Replayable AI Intent Evaluation Harness
* ADR-067 - DevLog Engineering Story Context Agent
* ADR-079 - Multi-Layer Context Budgets and Provider-Incomplete Diagnostics
* Story 0112 - DevLog Engineering Story Context Agent
* Story 0173 - Story Context Budget Observability
* `docs/evaluation/answer-quality-benchmark-specification.md`

## Architectural Constraints

1. Java/Core remains the sole authority for context construction, scope,
   grounding, trust and final acceptance.
2. Python remains responsible for probabilistic generation and defensive
   validation only.
3. `EvidenceRef.reference` remains the canonical grounding identity.
4. `EvidenceRef.resource` remains optional navigation metadata and cannot grant
   grounding authority.
5. AI output remains non-trusted and must not create or modify
   `ValidatableProposal` or trusted knowledge.
6. MCP and REST remain thin adapters over the same Core capability.
7. No Python-side retrieval, RAG redesign, new vector system, generic agent
   framework or persistent conversational memory is introduced.
8. Existing incomplete-provider fail-closed behavior is preserved.
9. Evaluation artifacts remain outside production behavior and do not become
   trusted project knowledge.

## In Scope

### Contract

Add an explicit `grounding` field to `objectiveUnderstanding`, using the same
grounding semantics as findings:

```json
{
  "summary": "...",
  "grounding": {
    "evidenceReferences": [],
    "classification": "FACTUAL_EXTRACTION",
    "grounded": true
  }
}
```

The field must support `FACTUAL_EXTRACTION`, `AI_INTERPRETATION` and
`RECOMMENDATION`, with empty references allowed only when the content is an
explicitly ungrounded recommendation or an abstention stating that evidence is
not established.

### Deterministic validation

* Validate objective-summary references against the Core-issued allow-list.
* Validate objective-summary classification and grounding semantics.
* Validate output-classification entries against actual findings and summary
  references where applicable.
* Preserve revision, provenance and digest checks.
* Preserve truncated-context abstention requirements.
* Reject unknown or unauthorized references.

### Evaluation

Extend the existing replay harness with deterministic response-quality fields
and scenario expectations for:

* architecture decision understanding;
* component history reconstruction;
* mandatory constraint preservation;
* current versus historical decision distinction;
* insufficient evidence and correct abstention;
* complementary evidence sources;
* deliberately truncated context;
* structurally valid but semantically incomplete output.

Deterministic metrics and human-reviewed qualitative metrics must remain
separate. No LLM judge is required for normal CI.

### Documentation and reporting

Produce an implementation-level audit and evaluation report covering:

* actual response pipeline;
* contract and provenance boundaries;
* baseline results or explicit execution blockers;
* before/after comparison for executed scenarios;
* known limitations and unresolved qualitative review.

## Out of Scope

* RAG or hybrid retrieval redesign.
* New vector or embedding infrastructure.
* New autonomous agent loops or multi-agent frameworks.
* Fine-tuning or model replacement.
* Governance or `ValidatableProposal` workflow changes.
* Full MCP contract refactoring.
* Automatic human-quality judgment.
* Increasing provider budgets without separate measured evidence.

## Acceptance Criteria

- [ ] `objectiveUnderstanding` has explicit grounding metadata in Java and
      Python contracts.
- [ ] Core rejects objective-summary references outside its authorized
      grounding contract.
- [ ] Core rejects invalid summary classification/grounded combinations.
- [ ] Output classification cannot silently diverge from classified findings.
- [ ] Existing finding, causal, digest, revision and trust validation remains
      compatible.
- [ ] MCP and REST responses remain backward-compatible through additive fields.
- [ ] Deterministic tests cover valid, missing, unknown, unauthorized and
      truncated-summary cases.
- [ ] The replay harness evaluates response-quality dimensions separately from
      technical execution status.
- [ ] At least the defined insufficient-evidence and complementary-evidence
      fixtures are deterministic and provider-free.
- [ ] Baseline and comparative results are reported only for scenarios actually
      executed; blocked evaluations are marked `NOT_EVALUATED`.
- [ ] No production trusted knowledge or proposal lifecycle is modified.
- [ ] Relevant technical documentation is updated and the final report records
      the documentation outcome.

## Validation Plan

### Backend

```bash
./backend/mvnw -pl backend -am test -Dtest=StoryContextResultValidatorTest,StoryContextCoreIntegrationTest
```

### AI Engine

```bash
python3 -m pytest ai-engine/tests/test_story_context_analysis_generation_service.py ai-engine/tests/test_evaluation_harness.py -q
```

### Contract and MCP compatibility

```bash
./backend/mvnw -pl mcp-server -am test -Dtest=StoryContextAnalysisToolTest
```

The exact commands may be adapted to the available local runtime, but every
blocked or unavailable check must be reported rather than treated as passed.

## Known Limitations

* Human qualitative review remains necessary for faithfulness, relevance,
  temporal accuracy, actionability and context efficiency.
* Existing evaluation fixtures may reference historical repository revisions and
  must not be presented as current production baselines without verification.
* The Story 0209 implementation must not silently rewrite unrelated workspace
  changes.
