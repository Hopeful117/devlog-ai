# Implementation Plan - Story 0183

## Status

`READY_FOR_HUMAN_APPROVAL`

Implementation is not authorized by this plan.

## Objective

Make the Story 0182 candidate fixture report deterministic Core trust metadata
for the complete bounded candidate set, including rejected candidates, without
changing production selection or the public `EngineeringContext` trust contract.

## Design

### 1. Reuse one Core classification rule set

Extract or adapt the existing private trust classification logic from
`EngineeringContextContractMapper` into a small Core-internal classifier that
accepts the candidate metadata available before selection:

- candidate kind;
- candidate source type.

The existing `RepositoryEvidence` mapping must call the same classifier. This
prevents the fixture and the public EngineeringContext projection from drifting
into separate trust policies.

### 2. Preserve public trust semantics

The public `TrustTier` enum remains unchanged:

- `TRUSTED`;
- `HUMAN_AUTHORED`;
- `TECHNICAL_EVIDENCE`;
- `SYSTEM_METADATA`.

The existing public mapper continues to exclude `VALIDATABLE_PROPOSAL` and
`AI_ENGINE` evidence from EngineeringContext.

Overlapping metadata keeps the existing public mapper precedence. Fixture-only
classification may expose `UNVALIDATED` or `TRANSIENT_AI`, but this does not
alter the public result for an overlapping public kind/source combination.

### 3. Use explicit fixture-only markers for excluded candidates

The evaluation fixture already permits the bounded string markers
`UNVALIDATED`, `TRANSIENT_AI`, and `UNKNOWN`. The fixture classifier will use:

- `UNVALIDATED` for `VALIDATABLE_PROPOSAL`;
- `TRANSIENT_AI` for `AI_ENGINE` source metadata;
- `UNKNOWN` for unsupported or incomplete metadata;
- one of the four public tiers for classified evidence.

These values remain evaluation metadata only. They do not become `TrustTier`
members, EngineeringContext sections, trusted knowledge, or provider-visible
authority.

### 4. Classify at fixture construction time

`ContextLossLedger.candidateFixture(...)` will derive each row's trust from the
Core candidate metadata classifier rather than looking up only
`canonical.trustByReference()`. The selected/projection/grounding state remains
independent and continues to come from the existing canonical references.

### 5. Keep Python validation passive

Python will retain allowlist validation and aggregation of fixture trust values.
No Python-side classification or fallback inference will be added. The existing
fixture digest and telemetry digest will naturally change when corrected trust
values change.

## Files Expected To Change

### Backend

- `backend/src/main/java/com/hopeful117/devlogai/engineeringcontext/mapper/EngineeringContextContractMapper.java`
  - reuse the shared Core classification rule set while preserving filtering.
- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/ContextLossLedger.java`
  - classify every candidate fixture row through the Core helper.
- A narrowly scoped Core classifier file may be added only if extraction from
  the mapper is required to avoid duplicated rules.
- `backend/src/test/java/com/hopeful117/devlogai/storycontextanalysis/usecase/ContextLossLedgerTest.java`
  - verify selected and rejected candidate trust, excluded markers, unknown
  cases, and digest sensitivity.
- A focused mapper test may be updated or added if extraction changes direct
  public mapping coverage.

### AI Engine

- `ai-engine/evaluations/test_story_context_runtime_readiness.py`
  - extend fixture cases for the complete allowed trust vocabulary if needed.
- `ai-engine/evaluations/test_story_context_policy_comparison.py`
  - verify alternative replay consumes Core-provided trust without inference.
- `ai-engine/evaluations/story_context_runtime_report.py`
  - change only if the current validation cannot represent the approved fixture
  contract; no classification logic belongs here.

### Story Documentation

- `docs/stories/0183-authoritative-candidate-trust-fixture/implementation-report.md`
  - record implementation, tests, documentation reconciliation, and limitations.
- `docs/stories/0183-authoritative-candidate-trust-fixture/evaluation-report.md`
  - record the enriched replay observations.
- `docs/stories/0183-authoritative-candidate-trust-fixture/decision-report.md`
  - separate measured trust coverage from any policy recommendation.

## Test Strategy

### Backend unit tests

Cover:

- selected technical evidence;
- rejected technical evidence with the same deterministic classification;
- trusted Core knowledge;
- human-authored and system metadata candidates;
- `VALIDATABLE_PROPOSAL` as `UNVALIDATED` in the fixture only;
- `AI_ENGINE` as `TRANSIENT_AI` in the fixture only;
- public precedence for overlapping `AI_ENGINE` and proposal metadata;
- unsupported or incomplete metadata as `UNKNOWN`;
- projected and grounded flags remaining independent of trust;
- fixture digest changing when a candidate trust value changes;
- existing public mapper behavior still excluding proposal/AI evidence.

### Python tests

Cover:

- all allowed fixture trust markers;
- malformed or unauthorized trust values rejected;
- policy comparison counts trust from fixture rows exactly;
- no fallback classification from candidate kind, score, path, or selection
  state.

### Focused commands

```bash
./backend/mvnw -pl backend test -Dtest=ContextLossLedgerTest -B
cd ai-engine && python -m pytest -q \
  evaluations/test_story_context_runtime_readiness.py \
  evaluations/test_story_context_policy_comparison.py
```

### Broader validation

After focused tests pass:

```bash
./backend/mvnw -pl backend -am verify -B
cd frontend && npm run lint && npm run format:check && npm run build
```

The frontend checks are expected to be non-impacted, but repository CI scope
will determine whether they are required for final delivery.

### Evaluation validation

- Run the bounded Story 0182/0183 fixture validation against the existing target
  revision.
- Replay `current`, `balanced`, and `unrestricted` against identical enriched
  fixtures.
- Confirm requested-file survival, grounding, selected/rejected counts, trust
  distributions, and digests are reported separately.
- Keep provider timeout/incomplete outcomes separate from fixture trust results.

## Documentation Reconciliation

The implementation changes an evaluation-only fixture contract and its
story-local reports. No general REST, MCP, deployment, operator, or user-facing
documentation should change unless implementation reveals an existing documented
contract is affected.

The Implementation Report must explicitly record this conclusion and include the
required Vault Outcome section. The curated Obsidian vault remains read-only.

## Risks and Mitigations

- **Duplicated trust policy:** centralize or delegate both call sites to one
  classifier and add regression tests.
- **Public contract expansion:** do not modify `TrustTier`; keep excluded markers
  fixture-local strings.
- **False trust promotion:** never derive trust from relevance, selection,
  grounding, provider visibility, or model output.
- **Digest drift:** use the existing canonical fixture serialization and validate
  the resulting digest in both Core tests and Python reports.
- **Scope expansion into production behavior:** keep changes confined to metadata
  classification, telemetry, evaluation tests, and Story-local reports.

## Acceptance Evidence

The completed implementation must show:

- rejected candidates no longer receive `UNKNOWN` when existing Core metadata
  authoritatively classifies them;
- legitimate unsupported/incomplete candidates remain `UNKNOWN`;
- excluded candidate markers remain non-public and non-trusted;
- the three policy replays consume the same enriched fixture;
- no production policy or retrieval behavior changed.

## Gate

This Implementation Plan is complete and ready for human approval. After
explicit approval, implementation may begin. No production code has been changed
for Story 0183.
