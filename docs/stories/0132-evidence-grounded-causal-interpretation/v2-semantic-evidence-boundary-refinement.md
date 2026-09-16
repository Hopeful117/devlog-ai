# Story 0132 - V2 Semantic Evidence Boundary Refinement

## Status

`V2 IMPLEMENTATION AUTHORIZED - IMPLEMENTED LOCALLY`

The first GREEN implementation remains preserved. The first GREEN live result
remains RED and is not reinterpreted as acceptance:

- positive causal accuracy: `0.15555555555555556`;
- CASE-04: `0/3 NOT_ESTABLISHED`;
- grounding: `PASS`;
- stability: `0.7983058608058609`;
- causal overclaim rate: `1.0`;
- abstention accuracy: `0.0`;
- role admissibility rate: `0.43333333333333335`;
- causal claim stability: `0.5833333333333334`;
- evidence-removal: `FAIL`.

No code, benchmark, oracle, model, retrieval path or evaluation artifact was
changed by this refinement.

## Finding: Semantic Circularity

The first GREEN contract is structurally coherent but semantically circular:

```text
LLM declares DIRECT_RELATIONSHIP_STATEMENT
  -> validator accepts the declared role
  -> role permits EXPLICITLY_DOCUMENTED
```

Java proves reference identity, authorization, role enum validity and support
counts. It does not prove that the referenced content has the declared role.
The model therefore controls the semantic input to the deterministic ceiling.

The first GREEN evidence-removal failure confirms that implementation code can
be relabeled as documentary causal evidence after the ADR, Story and commit
evidence are removed. This is not a CASE-04-specific defect.

## Primary V2 Question

The smallest useful next boundary is not another role enum. It is an inspectable
assertion tied to an authorized source location:

```text
fixed candidate relationship
  -> authorized existing reference
  -> authoritative locator
  -> Core-resolved source content and digest
  -> model interpretation of that assertion
  -> bounded assessment
```

This makes a causal assessment inspectable and replayable. It does not make
arbitrary natural-language causality deterministic.

## Proposed Contract

Names remain provisional until human approval.

```text
CausalQuestion {
  source
  target
  relationAsked
  answerRequired
}

EvidenceAssertion {
  evidenceReference          // existing AiReference identity
  locator                    // source-kind-specific bounded location
  resolvedContentDigest      // Core-authored, not model-authored
  assertionRole              // model interpretation, not authority
}

CausalAssessment {
  relationship               // exact CausalQuestion identity
  classification
  evidenceAssertions
  explanation
}
```

The minimum `locator` should support the current evidence kinds without a
generic document language:

- file/document line range;
- document section or heading;
- commit message range or message identifier;
- structured evidence field path.

The model may return a locator and an optional excerpt for convenience, but the
excerpt is never authoritative. Java resolves the locator against the immutable
authorized context and either supplies the authoritative content/digest or
rejects the assertion. A generated quotation that does not match the resolved
content is invalid.

The existing typed reference registry remains the only identity and
authorization system. `EvidenceAssertion` is content-level support over an
existing authorized identity, not a second registry.

## Authority Boundary

### Deterministically Verifiable

| Property | Authority |
|---|---|
| reference exists | Java/Core |
| reference is authorized and in task scope | Java/Core |
| locator is valid for the reference kind | Java/Core |
| locator resolves in the immutable context | Java/Core |
| resolved content belongs to the reference | Java/Core |
| resolved content digest matches the context snapshot | Java/Core |
| generated excerpt matches resolved content | Java/Core, if an excerpt is transported |
| candidate source/target equals the task question | Java/Core |
| exactly one assessment exists for one causal question | Java/Core |
| duplicate assertions are rejected | Java/Core/Python |

### Structurally Verifiable

| Property | Boundary |
|---|---|
| assertion count meets the contract | Java/Python |
| assertion references are distinct | Java/Python |
| source and target fields are present | Python/Java |
| a commit message, Story section or ADR span was selected | Java/Core |
| assertions come from distinct artifact identities | Java/Core |
| code-only evidence is not promoted to historical motivation by policy | Java/Core contract policy |

Structural verification must not be described as semantic proof.

### Probabilistic / Semantic

- whether a resolved sentence directly states the requested relationship;
- whether an assertion materially supports that exact relationship;
- whether two assertions independently corroborate one relationship;
- whether an implementation is compatible with an ADR;
- whether an ADR caused a later implementation;
- whether a Story's requirement and acceptance criterion bridge the decision to
  the implementation;
- whether an explanation stays semantically bounded by the assertions;
- confidence.

### Human-Verifiable

- final documentary-causality judgment when the model's semantic label is
  disputed;
- whether the selected span is the right interpretation of a domain-specific
  statement;
- whether multiple assertions are genuinely independent rather than copies;
- promotion of the assessment into trusted knowledge.

## Documentary Causality Rules

`EXPLICITLY_DOCUMENTED` should mean that an authoritative resolved assertion
explicitly connects the fixed source and target relationship. It must not mean
that the source and target merely co-occur in an artifact.

Examples that may qualify after semantic review:

- “This implementation satisfies ADR-X requirement Y.”
- “Component X was introduced because of decision Y.”
- “Story Z implements requirement R from ADR-X.”
- “This refactor is required to preserve invariant Y.”

Examples that do not qualify by themselves:

- a related ADR field;
- source and target existence;
- a same-commit change;
- chronology;
- architectural compatibility;
- “implements PAPER settlement” when the requested relationship is an ADR's
  historical cause;
- a code path that demonstrates behavior without stating historical motivation.

Source code remains valid evidence for current behavior, structure,
dependencies and implemented invariants. It is normally insufficient alone for
historical motivation. A source comment or structured implementation statement
may be documentary evidence if its resolved span explicitly states the
relationship; this is not a categorical ban on code evidence.

Commit file changes establish modification and chronology. A commit message
that explicitly states the decision-to-change relationship may be a documentary
assertion. A changed-file list alone is not causal evidence.

An ADR directly establishes its documented decision and constraints. It does
not establish that every later compatible implementation detail was caused by
the ADR.

An Engineering Story can be a strong bridge when its requirement or acceptance
criterion explicitly connects the architectural decision to the implementation.
“Related ADR” metadata alone is not that bridge.

## Candidate Relationship and Cardinality

For causal-required questions, candidate source, target and requested relation
must be fixed before model classification. The preferred owner is Java/Core
task construction from the user question or an explicit task request. The model
may not replace the requested relationship with several adjacent claims.

The preferred cardinality is exactly one `CausalAssessment` per
`CausalQuestion`. A non-causal analysis may still contain zero causal
assessments. A causal-required task must end with one affirmative assessment,
one explicit `NOT_ESTABLISHED` assessment, or a fail-closed generation error.

`NOT_ESTABLISHED` should cite the strongest relevant resolved assertions where
available and explain:

1. what those assertions establish;
2. what exact relationship remains unestablished;
3. what category of evidence is missing, only when that can be stated without
   speculation.

## Strong Support

`STRONGLY_SUPPORTED` remains probabilistic in meaning. Its structural minimum
should require two distinct authorized assertions with different evidence
identities and resolved content digests. The assertions should each address the
fixed source/target relationship, not merely different facts about the same
topic.

ADR + Story and Story + explicit commit message may count. Two code files may
count only if both contain relationship-specific documentary content; two files
that merely demonstrate the same behavior do not automatically count. No
provenance scoring or generic ontology is required.

## Architecture Options

### Option A: Single-Call Assertion-Led Assessment

One model call returns the fixed assessment, locators, roles and explanation.
Java resolves every locator and rejects invalid spans.

- Verifiable: identity, locator, content and quote integrity.
- Probabilistic: semantic role and causal sufficiency.
- Cost: one call and minimal contract expansion.
- Failure: model can still misinterpret a valid span.
- CASE-04: better inspectability; no guarantee against semantic overclaim.
- Evidence removal: remaining behavior spans become visibly distinguishable from
  removed documentary spans.
- Usefulness: a human can inspect claim -> source -> exact content.

### Option B: Two-Stage Assertion Extraction Then Assessment

Stage 1 extracts resolved assertions. Stage 2 assesses the fixed relationship
over those assertions.

- Verifiable: stage boundaries, locators and content resolution.
- Probabilistic: extraction relevance and causal assessment.
- Cost: two calls, more latency and persisted intermediate state.
- Failure: extraction omissions or errors propagate; same-model stages are not
  independent verification.
- CASE-04: may reduce confirmation bias, but can also preserve a wrong stage-1
  label.
- Evidence removal: stronger reusable regression surface.
- Usefulness: good audit trail, higher implementation complexity.

### Option C: Core-Fixed Relationship Plus Authoritative Locator Assessment

Java constructs one `CausalQuestion` and its authorized evidence universe. One
model call selects bounded locators and produces one assessment. Java resolves
content, validates cardinality and persists the authoritative assertion snapshot.

- Verifiable: candidate identity, authorization, locator, exact content,
  digest, cardinality and assertion provenance.
- Probabilistic: whether resolved content documents or materially supports the
  relationship.
- Cost: one call with a modest contract extension.
- Failure: semantic misclassification remains visible and human-reviewable,
  rather than silently becoming a role-based deterministic fact.
- CASE-04: prevents relationship substitution and fabricated excerpts; a human
  can see that the selected span does not state ADR-043 caused the refactor.
- Evidence removal: code/context spans remain but cannot inherit removed
  documentary identity; the test can inspect the surviving assertions.
- Usefulness: smallest path to claim -> assertion -> authoritative source.

## Preferred Architecture

Choose **Option C**.

It is the smallest extension that addresses the measured failure without adding
a second retrieval system or pretending Java can understand arbitrary prose. It
fixes the current free-form relationship/cardinality instability, makes every
causal claim inspectable, reuses typed references, and preserves the
probabilistic semantic boundary explicitly.

Option B may be a later measured experiment if Option C shows that single-call
assertion extraction remains unstable. Same-model critique is a probabilistic
second pass, not verification. A second model is not justified by the current
evidence.

## Keep / Refine / Remove

### Keep

- three causal levels;
- first-class causal assessment concept;
- causal-required task semantics;
- Java-owned context and typed-reference authorization;
- Python defensive validation and one corrective retry;
- ADR-006 non-trust boundary;
- historical metrics and new diagnostic metrics;
- frozen benchmark, oracle, Ground-Truth context and evidence-removal tests.

### Refine

- `CausalClaim` into fixed `CausalQuestion` plus one `CausalAssessment`;
- roles into model interpretation metadata, not deterministic semantic proof;
- evidence references into assertions with Core-resolved locators/content digests;
- `NOT_ESTABLISHED` to cite inspectable evidence and bounded absence;
- strong support to require distinct resolved assertions;
- evaluator to measure assertion validity and locator coverage.

### Remove

- role-based authority as the deterministic source of causal truth;
- free `0..N` causal claims for causal-required questions;
- any generated excerpt treated as evidence without Core resolution;
- any claim that structural role admissibility proves semantic causality.

## Decision Gates

```text
CAN_REFERENCE_PLUS_ROLE_BE_RETAINED_AS_SUFFICIENT = NO
IS_EVIDENCE_ASSERTION_REQUIRED = YES
IS_AUTHORITATIVE_SOURCE_LOCATOR_REQUIRED = YES
SHOULD_CANDIDATE_RELATIONSHIP_BE_FIXED_BEFORE_MODEL_CLASSIFICATION = YES
SHOULD_CAUSAL_REQUIRED_CARDINALITY_BE_DETERMINISTIC = YES
SHOULD_EVIDENCE_EXTRACTION_AND_CAUSAL_ASSESSMENT_BE_SEPARATE = NO FOR V2
CAN_JAVA_SEMANTICALLY_PROVE_CAUSALITY = NO
DOES_THE_LLM_REMAIN_SEMANTIC_AUTHORITY = PARTIALLY
IS_A_MODEL_CHANGE_JUSTIFIED_NOW = NO
IS_RETRIEVAL_WORK_JUSTIFIED_NOW = NO
IS_RAG_JUSTIFIED_NOW = NO
```

The LLM remains the semantic interpreter of resolved assertions. Java is the
authority for whether the assertion exists, is authorized and is inspectable;
it does not authorize a semantic causal conclusion merely because the model
labels it direct.

## Future Deterministic Tests

Before a v2 live run, add tests for:

- fixed source/target cannot be replaced by model output;
- exactly one assessment is required for one causal question;
- unknown reference, out-of-scope reference and invalid locator fail closed;
- line/section/commit-message locators resolve to the authorized source;
- resolved content digest is stable and revision-bound;
- fabricated excerpt mismatches are rejected or ignored in favor of Core text;
- duplicate assertions and duplicate content digests are rejected;
- code behavior and same-commit evidence cannot be silently relabeled as
  historical documentary support by structural metadata alone;
- explicit commit-message, Story requirement and ADR assertion shapes remain
  inspectable;
- `NOT_ESTABLISHED` with cited context assertions is valid;
- empty causal output is invalid only when causal answer is required;
- retry cannot add references or locators outside the authorized context;
- confidence and `relationType` cannot change the assessment;
- evidence-removal leaves only surviving resolved assertions and cannot retain an
  affirmative assessment without the relationship-establishing assertions.

## Future RED/GREEN Protocol

Do not execute this protocol in the current refinement:

1. Compare the first GREEN contract with the approved v2 contract.
2. Keep the same Ground Truth, provider, model, cases, repetitions, oracle and
   thresholds.
3. Score the preserved historical metrics and diagnostics.
4. Add only one new deterministic metric if required:
   `evidenceAssertionValidityRate`, the proportion of emitted assertions whose
   references, locators, resolved content and digests validate. This measures a
   new structural property and does not score semantic causality.
5. Require CASE-04 `3/3 NOT_ESTABLISHED` and evidence-removal success.
6. Inspect one assertion per case manually for claim -> locator -> source
   traceability, without promoting the result to trusted knowledge.

The v2 experiment succeeds architecturally only if it improves inspectability
and removes fabricated or unresolved support. It succeeds on the Story gate
only if the unchanged frozen thresholds also pass. A failure with valid,
inspectable assertions would identify model semantic capability as the remaining
limitation; a failure with invalid/missing locators would identify contract
design as the remaining limitation.

## Product Boundary

The preferred architecture could let a human or future coding agent inspect:

```text
causal assessment
  -> fixed relationship
  -> existing typed evidence identity
  -> exact authoritative locator
  -> resolved source content and revision digest
```

That is a product advantage over plausible prose, but Human Utility remains
`NOT_YET_ESTABLISHED`. AI interpretation remains untrusted and cannot directly
create an Insight, Decision, EngineeringEvent or other trusted knowledge.

## Authorization

`IMPLEMENTATION_AUTHORIZED = YES`

The approved implementation uses Option C: Core fixes the causal relationship,
the model selects bounded locators over authorized repository evidence, and Core
resolves the snapshot content and digest before persisting the untrusted
assessment.

Human architecture review and explicit implementation authorization are
required before any production contract or evaluation code changes.
