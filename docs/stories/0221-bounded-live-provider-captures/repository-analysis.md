# Repository Analysis - Story 0221

## Status

REPOSITORY ANALYSIS APPROVED.

This analysis defines the current repository boundary and the evidence used by
the approved implementation plan.

## Context and Limitations

The DevLog Story Agent preflight was attempted for this Story but returned
`401 Unauthorized`. Repository evidence is therefore authoritative for current
physical state. No Obsidian vault context was consulted because the repository
artifacts and accepted ADRs provide the relevant project context.

## Epic Position

The epic previously listed the third loss-accounting Story as undefined. Story
0220 now occupies that position. The next direction selected by the human owner
is bounded live provider capture and attribution, before the later specificity,
abstention and confidence-calibration Story.

## Existing Implementation Evidence

### Existing provider trace boundary

`AiInteractionTraceRequest` already carries bounded provider-attempt metadata:

- provider and model identifier;
- attempt and timing;
- validation and failure category/code;
- prompt/grounding fingerprints and selected evidence counts;
- token usage where supplied;
- trace/span correlation fields;
- execution phase, provider status, incomplete reason, measurement unit and
  measurement status.

`AiInteractionTracePersistenceService` persists this data in the existing
`AiInteractionTrace` aggregate. Normal mode excludes prompt, response and
parsed-output payloads. Diagnostic mode is explicitly configured and receives
an expiry timestamp. This is the existing production trace boundary and should
be reused rather than duplicated.

### Existing Core authority

Core owns task identity, callback acceptance, execution scope, freshness,
grounding and `ContextLossLedger`. Story 0220 already validates the evaluation
projection against `executionScope`, freshness, context/projection digests and
ledger identity. The next Story should consume those outputs and add live
source/provider attribution only at the evaluation boundary.

### Existing evaluation boundary

Story 0220 provides:

- bounded capture validation;
- technical terminal-state normalization;
- provider visibility rules;
- semantic denominator handling;
- deterministic JSON/text reporting;
- provider-free fixtures and digest expectations.

The new Story should extend or compose this evaluator without changing its
deterministic baseline.

## Required Design Boundary

The smallest coherent implementation is an evaluation adapter that projects an
existing `AiInteractionTrace` plus the associated Core snapshot into the Story
0220 capture contract. A new production entity, provider retriever, or runtime
loop is not justified by the current evidence.

The adapter must fail closed when any of these are absent or contradictory:

- expected project/source/revision;
- context/projection/ledger digests;
- terminal callback identity;
- provider attribution;
- bounded provider status or incomplete reason.

Live captures must not be compared to deterministic baselines unless capture
source, revision, intent, configuration identity and expected digests match.

## Risks

- Existing diagnostic trace fields can contain rich payloads; the evaluation
  projection must not export them by default.
- Provider usage fields are optional and provider-specific; missing values must
  remain unknown rather than estimated as zero.
- A provider trace may be present while callback acceptance fails; terminal
  normalization must preserve both facts without marking the analysis complete.
- Existing `AiInteractionTrace` retention is not an evaluation export policy;
  the Story must define export retention separately without silently changing
  the production trace setting.
- Credentials may be unavailable in local or CI environments; this must produce
  `NOT_EVALUATED`, not a synthetic live success.

## Required Artifacts for Implementation Planning

- Current Story 0221 definition.
- This Repository Analysis after human approval.
- Approved retention, attribution and usage decisions.
- Story 0220 evaluator and deterministic fixture contract.
- Story 0126 trace DTO, persistence and query behavior.
- ADR-079 provider diagnostic requirements.

## Validation Plan

### AI Engine

```bash
uv run pytest ai-engine/tests/test_story_context_runtime_loss.py \
  ai-engine/tests/test_evaluation_harness.py -q
python3 -m compileall -q ai-engine/evaluations ai-engine/tests
```

Add focused tests for live-source projection, attribution, redaction,
contradictory terminal states, missing measurements and `NOT_EVALUATED`.

### Backend

```bash
./backend/mvnw -pl backend -am \
  -Dtest=AiInteractionTracePersistenceServiceTest,AiInteractionTraceQueryServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test -B
```

The exact test classes must be rechecked during planning because the current
repository may expose trace coverage through differently named integration
tests.

### Compatibility

```bash
./backend/mvnw -pl mcp-server -am \
  -Dtest=StoryContextAnalysisToolTest \
  -Dsurefire.failIfNoSpecifiedTests=false test -B
git diff --check
```

## Documentation Reconciliation

Expected outcome is Story-local documentation plus any evaluation-harness
README or runbook update required by the final capture command. No REST/MCP,
architecture, provider configuration or credential documentation should change
unless the approved Implementation Plan proves a public or operational contract
change is unavoidable.

## Recommendation

Approve this Repository Analysis only after confirming the five human decisions
listed in Story 0221. Then produce the Implementation Plan. Implementation is
not authorized by this analysis.
