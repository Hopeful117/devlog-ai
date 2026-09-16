# V3 Output Envelope Review

## Status

```text
PHASE = V3_OUTPUT_ENVELOPE_REVIEW
MODE = PAIR
STATUS = COMPLETE
NEW_PROVIDER_CALLS = 0
V3_REVISED_OUTPUT_LIMIT_HUMAN_APPROVED = PENDING
V3_SMOKE_RUN_AUTHORIZED = NO
V3_FULL_RUN_AUTHORIZED = NO
```

## Immutable Observation

The authoritative V3 run remains unchanged:

```text
RUN_ID = 5ef0f7af-1903-4041-b887-1693fba9f9a0
ARTIFACT_SHA256 = 2e3b8b5fed74d236e8f51e71ede8135dee4c139df02a42b14311c5bede57be31
MAX_OUTPUT_TOKENS = 2000
RAW_RESPONSE_CHARACTERS = 10131
RAW_RESPONSE_UTF8_BYTES = 10131
PROVIDER_REPORTED_OUTPUT_TOKENS = UNAVAILABLE
RESPONSE_BEARING = YES
STRUCTURAL_RESULT = INVALID_STRUCTURAL_OUTPUT
TECHNICAL_RETRY = NO
```

The response is valid JSON only through `objectiveUnderstanding` and
`architectureFindings`. It then enters `decisionFindings` and ends inside the
second evidence-reference string of the third decision finding:

```text
TRUNCATED_FIELD = decisionFindings[2].grounding.evidenceReferences[1].reference
TRUNCATION_LOCATION = raw character offset 10108, zero-based JSON decoder position
OPEN_OBJECT_DELTA = 4
OPEN_ARRAY_DELTA = 2
```

The captured prefix contains five complete architecture findings and three
decision-findings worth of partial content. Required sections after this point,
including the remaining finding collections, confidence, provenance, output
classification, and causal assessment, are not complete. The response shows no
pathological repeated block or looping marker. It follows normal structured
field progression and is substantially incomplete rather than near completion.

The captured prefix contains no completed evidence excerpts or resolved evidence
content. Evidence text dominates the input prompt, not the observed output
prefix.

## Composition Measurements

```text
COMPLETE_TOP_LEVEL_FIELDS = 2
COMPLETE_COLLECTION_ENTRIES = 5
LARGEST_COMPLETE_COLLECTION = architectureFindings (5 entries, 4922 JSON characters)
ARCHITECTURE_DESCRIPTION_CHARS = 2369
DECISION_CONTEXT_CHARS = 1144
DECISION_CHOICE_CHARS = 825
DECISION_RATIONALE_CHARS = 805
COMPLETED_EVIDENCE_EXCERPT_CHARS = 0
COMPLETED_RESOLVED_CONTENT_CHARS = 0
```

Provider token usage was unavailable because the structured parser failed before
returning a provider result. A rough character-based estimate is approximately
2,500 output tokens, but it is not an authoritative token measurement.

## Schema And Prompt Drivers

The exact frozen `StoryContextAnalysisResult` schema has 27 definitions and ten
top-level collection fields. Those collections have no `maxItems` bound. Nested
findings repeat titles, descriptions, grounding metadata, references, and
relationship metadata. Causal assertions can additionally repeat references,
locators, roles, optional excerpts, and digests. Field maximums permit 10,000
character descriptions and 5,000 character explanations.

The frozen CASE-01 prompt is large because it includes:

```text
SYSTEM_MESSAGE = 4840 characters
USER_MESSAGE = 269428 characters
SELECTED_EVIDENCE = 14 items / 240707 content characters
LARGEST_EVIDENCE_ITEM = 85611 characters
SCHEMA_JSON = 13177 characters
```

The prompt requires complete structured output, grounded findings, output
classification entries, and one exact V2 causal assessment. It permits empty
sections but does not bound the number of findings or the length of generated
rationales and descriptions. It does not request large excerpts explicitly, and
the observed prefix did not contain completed excerpts.

## Historical Comparison

The comparable V2 live artifact is
`ground-truth-v2-live-core-validation-2026-09-16.json`.

It contains 33 provider slots: 6 structurally parsed outputs and 27 unparsed
outputs. Four parsed outputs have provider-reported output tokens of 1809, 1822,
1908, and 1952. Two parsed outputs have no recorded provider token usage. Their
serialized post-parse sizes range from 6730 to 9561 characters, excluding one
62883-character result that includes Core-enriched resolved content and is not a
comparable raw provider output.

The six parsed V2 outputs were not valid semantic benchmark evidence: Core
rejected their evidence assertions. The 27 unparsed outputs are structural
failures, not completed outputs. Test fixtures are excluded as non-empirical.

## Envelope Decision

```text
PREVIOUS_CONFIGURATION = V3 / max_output_tokens=2000
REVISED_LIMIT_CANDIDATE = 3000
```

`3000` is the smallest defensible bounded candidate identified offline, not an
approved value. The current response consumed the full 2,000-token envelope while
still inside a repeated decision/evidence structure, with all causal and
provenance sections still pending. A 3,000-token candidate adds 1,000 tokens,
or 50 percent, of bounded headroom. That is supported by the observed near-cap
completed V2 outputs and the substantial structured tail still required by the
current V3 contract. It is a reasoned capacity candidate, not proof that every
future response will fit.

No larger limit is justified by this evidence, and no output limit was changed in
the manifest. A future approved limit must be tested from a new run identity and
must not repair or reuse the failed response.

## Configuration And Restart Policy

Changing only output capacity leaves the semantic mapping identity unchanged:

```text
SEMANTIC_MAPPING_IDENTITY = UNCHANGED
EXECUTION_CONFIGURATION_IDENTITY = CHANGED
```

The manifest requires a new execution-configuration revision/digest, and the
benchmark manifest or equivalent execution manifest must carry that new identity.
The immutable 2,000-token artifact must not be overwritten. If the candidate is
approved, restart all three smoke slots from `CASE-01` under a new run identity.
The historical V3 calls remain accounted for separately; the revised run has its
own expected three calls and maximum six attempts.

There is no evidence of a `responses.parse(..., text_format=...)` defect. The
failure is response truncation at the configured output envelope. No prompt,
schema, model, retrieval, or RAG change is indicated.

## Boundary

```text
PRODUCTION_CODE_CHANGED = NO
PRODUCTION_SEMANTICS_CHANGED = NO
MANIFEST_UPDATED = NO
NEW_PROVIDER_CALLS = 0
COMMIT = NO
PUSH = NO
MERGE = NO
```
