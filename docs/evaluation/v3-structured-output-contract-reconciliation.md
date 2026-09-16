# V3 Structured Output Contract Reconciliation

## Status

```text
PHASE = V3_STRUCTURED_OUTPUT_CONTRACT_RECONCILIATION
MODE = PAIR
STATUS = COMPLETE
NEW_PROVIDER_CALLS = 0
V3_SMOKE_RUN_AUTHORIZED = NO
V3_FULL_RUN_AUTHORIZED = NO
STORY_0132_REOPENED = NO
```

## Confidence By Layer

| Layer | Representation | Classification | Authority |
|---|---|---|---|
| Python `Confidence` model | `{level, rationale}` | `DOMAIN_INTERNAL` | Python internal analysis model |
| Current `StoryContextAnalysisResult` passed to `responses.parse` | JSON object with `level` and `rationale` | `PROVIDER_WIRE` as currently implemented | Current Python model, but incorrect for the canonical boundary |
| Story0132/Core callback wire | `"HIGH"`, `"MEDIUM"`, or `"LOW"` | `JAVA_CANONICAL` / production callback wire | Story0132 closure and Java DTO |
| Java `StoryContextAnalysisResult.Confidence` | `HIGH`, `MEDIUM`, `LOW` enum | `JAVA_BRIDGE` and `JAVA_CANONICAL` | `devlog-contracts` |
| V3 artifact parsed response | Captured provider output plus validation state | `EVALUATION_ARTIFACT` | V3 protocol |

Story0132 closure explicitly states that Python keeps rationale internally and
serializes Core callback confidence as the canonical level string. Java defines
`StoryContextAnalysisResult.Confidence` as the `HIGH`, `MEDIUM`, `LOW` enum.

The mapping belongs at the provider/Python boundary: canonical provider string
to internal `Confidence(level, rationale)`. The mapping is lossy because the
canonical string carries no rationale. The deterministic internal mapping uses
the model's declared empty-rationale default; it does not invent rationale.

```text
PROVIDER_WIRE_CONFIDENCE_AUTHORITY = STRING_ENUM
CONFIDENCE_CONTRACT_AUTHORITY_ESTABLISHED = YES
CONFIDENCE_CONTRACT_DEFECT_OWNER = V3_EVALUATION_PROVIDER_SCHEMA_BOUNDARY
```

No Story0132 production contract change is required by this finding.

## Generated Schema Finding

The exact current schema was generated with:

```python
StoryContextAnalysisResult.model_json_schema()
```

Its confidence definition is:

```json
{
  "type": "object",
  "properties": {
    "level": {"type": "string", "pattern": "^(HIGH|MEDIUM|LOW)$"},
    "rationale": {"type": "string"}
  },
  "required": ["level"]
}
```

The generated schema therefore requested an object, not a string. The observed
`"HIGH"` did not conform to that exact generated schema, but it did conform to
the authoritative Java/Core canonical wire representation. This is a provider
schema/harness boundary mismatch, not evidence that the canonical value itself
was invalid.

Evaluation-only `ProviderStoryContextAnalysisResult` now generates the canonical
string schema and maps it explicitly into the internal model. It is not wired
into production or the current smoke manifest.

## Rationale Ownership

`rationale` is present only on the Python internal `Confidence` model. It is not
part of the Java canonical confidence enum and is not available when the
provider wire value is only a string. The adapter preserves the declared empty
default rather than fabricating explanatory text.

## Raw Capture Boundary

The current path is:

```text
responses.parse(..., text_format=...)
→ SDK post-parser
→ parse/validation exception
→ exception reaches V3 runner
```

The SDK's `responses.parse` installs its parser internally. When that parser
raises, the raw SDK `Response` object is not returned to the caller. The current
provider then exposes only the exception, which caused the revised artifact to
retain `HIGH` as field-level input instead of the complete provider response.

The required V3 boundary is:

```text
responses.create(..., text=<provider-wire-schema>)
→ capture complete SDK Response model, output_text, usage, status,
  incomplete_details, id, and model
→ SDK parse_response(..., text_format=<provider-wire-model>)
→ deterministic mapping and validation
```

Raw HTTP bytes are not required by the approved V3 artifact protocol. The SDK
Response model plus exact `output_text` and safe response metadata are sufficient
to reproduce truncation, invalid JSON, wrong field representation, schema
failure, status, incompleteness, and token usage where supplied.

The evaluation helper captures the SDK Response before parsing and wraps parser
failures without replacing the captured output with the exception's field-level
input. Tests cover valid output, invalid confidence representation, invalid JSON,
truncation, schema failure, and parser exceptions.

```text
RAW_CAPTURE_DEFECT_CONFIRMED = YES
PREVIOUS_CAPTURE_BOUNDARY = responses.parse internal post-parser
CORRECT_CAPTURE_BOUNDARY = SDK Response capture before parse_response
RAW_HTTP_BODY_REQUIRED = NO
COMPLETE_PRE_VALIDATION_PROVIDER_OUTPUT_PRESERVED = YES
PARSER_FAILURE_PRESERVES_PROVIDER_OUTPUT = YES
```

## Configuration Consequence

The current approved smoke configuration used `responses.parse` with the
internal model and schema digest. Switching to a provider-wire model and explicit
pre-parse capture changes the provider-facing schema and invocation boundary.
Therefore a future smoke requires a new execution-configuration identity. The
current 3,000-token configuration is not silently reused or marked equivalent.

```text
STRUCTURED_OUTPUT_CONTRACT_CHANGED = YES
EXECUTION_CONFIGURATION_IDENTITY_CHANGE_REQUIRED = YES
PRODUCTION_CONTRACT_CHANGE_REQUIRED = NO
MAX_OUTPUT_TOKENS = 3000
MODEL = gpt-4.1-mini
MAPPING_HASH = 67474f09e41c07899c8c7117754b21e794f285380ffd50675e701a0a3c2c40c0
```

No provider call, smoke rerun, prompt change, model change, semantic change,
retrieval change, or RAG change was made.
