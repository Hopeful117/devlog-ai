# DevLog AI A/B/C Provider-Free Preparation Report

## Status

`Q1_RUNNER_DRY_RUN_READY`

- Normalization: `NORMALIZATION_READY`
- Q1 capabilities: `Q1_CAPABILITIES_BLOCKED_ASYMMETRY_ACCEPTED`
- Q1 answers: `Q1_ANSWERS_EVALUABLE`
- Q1 oracle: `Q1_ORACLE_PENDING_HUMAN_REVIEW`
- Q1 runner: `Q1_RUNNER_DRY_RUN_READY`
- Q1 live execution: `Q1_LIVE_NOT_AUTHORIZED`
- Q1 live integration: `Q1_LIVE_INTEGRATION_PARTIAL`
- Q1 live preflight: `Q1_LIVE_PREFLIGHT_BLOCKED`
- Capability comparison: `CAPABILITY_COMPARISON_BLOCKED`
- Q1 answer evaluability: `Q1_ANSWERS_EVALUABLE` for technical/evidence/
  uncertainty review; narrative prose quality is not equivalent for A/C.
- Oracles: `ORACLES_PENDING_HUMAN_REVIEW`
- Live experiment: `LIVE_EXPERIMENT_NOT_AUTHORIZED`

Preparation artifacts and deterministic checks are ready for human review. The
Q1 pilot intentionally uses an asymmetric end-to-end comparison, so capability
parity is not a launch requirement. The provider-free runner now binds the
three frozen observations to one manifest and artifact contract. Live execution
remains forbidden until oracle approval, runner/artifact approval, and separate
provider authorization are granted.

## Repository State

- Branch: `story/0184-bounded-story-context-investigation`
- HEAD: `60fae3e0e1104d0ffbe3832c1f578ed532b1268a`
- Remote branch: exact HEAD match
- Historical V4 Trading OS manifest: unchanged
- Provider calls: `0`
- Production files changed: `0`

## Checks Performed

| Check | Result | Evidence |
|---|---|---|
| Eight-case manifest loads | PASS | `devlog-ai-abc-benchmark-v1.json` |
| Eight oracle entries present | PASS | `devlog-ai-abc-oracle-proposals-v1.json` |
| All cases are DevLog AI | PASS | Manifest repository identity and paths |
| Revision pin is explicit | PASS | `60fae3e0...` in both manifests |
| Oracle schema fields present | PASS | Deterministic test |
| Evidence paths and line locators resolve | PASS | Deterministic test |
| Question wording unchanged | PASS | Compared with strategic specification |
| Existing V4 artifacts unmodified | PASS | Git status/diff scope |
| Oracle material absent from treatment input | PASS by construction | Treatment manifests contain no oracle payload |
| Provider-free V4 contracts/replay tests | PASS | V4 preflight: `configurationReady=true`, `deterministicReplay=READY`, `providerCalls=0`, `networkCalls=0`; V4 tests: `65 passed` |
| A/B/C full technical parity | BLOCKED | Story 0184 has two actions; B has six tools |
| Common output contract | PASS | Evaluation-only `devlog-abc-claim-evidence-normalization-v1` adapter covers A, B and C without changing production contracts |
| Oracle human-review sheets | READY_FOR_REVIEW | Eight case sheets; all semantic review statuses remain `PENDING` |
| Q1 frozen-commit oracle references | PASS | Q1 references resolved with `git show <revision>:<path>` |
| Q1 B/C operation comparison | BLOCKED | Direct B Git search and Core-composed C search have different semantics; bounded read matches only after C evidence exists |
| Q1 answer evaluability | PASS_WITH_LIMITATION | Technical/evidence/uncertainty review is possible; A/C narrative prose quality is not equivalent to B |
| Q1 asymmetric protocol | PASS | `END_TO_END_SYSTEM_COMPARISON`; controlled context-value comparison deferred |
| Q1 observation manifest | PASS | Three explicit Q1 observation IDs, one repetition each, all `PREPARED_NOT_EXECUTED` |
| Q1 live entrypoint integration | BLOCKED | Live A/B/C adapters are identified but not wired to the provider-free evaluation runner |
| Q1 dry-run runner | PASS | `devlog_ai_abc_runner` produced three isolated RAW/DERIVED observations with provider/network calls at zero |
| Q1 corrected oracle | PASS | Four claims verified against the frozen revision; digest `464fcd1b50b785e78ddd76e0b4eea3e65ac6a85fb124b31cea9c355951fcc457` |

## Existing Runtime Reuse

Reusable without production modification:

- `comparative_baseline_v4.protocol` for deterministic identities, schema,
  leakage and replay boundaries;
- `comparative_baseline_v4.runtime` for A-like context and B-like direct
  execution;
- Story 0184 Pydantic contracts and deterministic behavioral fakes;
- existing tool/evidence/resource traces and integrity checks.

## Blocking Findings

1. B exposes Git history/diff operations that C does not.
2. The V4 runtime's current manifest is Trading OS-specific and must not be
   silently repointed to DevLog AI.
3. Human oracle review is outstanding.
4. Historical V4 provider failures make a paid first run a separate human
   authorization decision; they cannot be resolved by the preflight.
5. A/C and B do not share a response contract. This is resolved for evaluation
   through a deterministic adapter; the production contracts remain unchanged.
6. Live A/B/C adapters are identified but not wired to this dry-run runner.
   The V4 runner must not be repointed from Trading OS.

## Test Commands

```bash
python3 -m json.tool docs/evaluation/devlog-ai-abc-benchmark-v1.json
python3 -m json.tool docs/evaluation/devlog-ai-abc-oracle-proposals-v1.json
uv run --project ai-engine --extra dev pytest ai-engine/tests/test_devlog_ai_abc_preflight.py -q
uv run --project ai-engine --extra dev pytest ai-engine/tests/test_devlog_ai_abc_normalization.py -q
uv run --project ai-engine --extra dev pytest ai-engine/tests/test_devlog_ai_abc_capability_comparison.py -q
uv run --project ai-engine --extra dev pytest ai-engine/tests/test_comparative_baseline_v4.py -q
uv run --project ai-engine --extra dev pytest ai-engine/tests/test_story_context_investigation.py ai-engine/tests/test_core_callback_client.py ai-engine/tests/test_story0184_bounded_behavioral_baseline.py -q
./backend/mvnw -pl backend test -Dtest=InvestigationCapabilityActionValidatorTest,InvestigationCapabilityExecutionServiceTest,StoryContextInvestigationActionServiceTest -B
```

These commands are provider-free. Passing them proves preparation integrity,
not semantic answer quality. The live A/B/C entrypoint integration remains
intentionally unauthorized and outside this oracle correction.

Results:

- ABC preflight tests: `6 passed`.
- Q1 runner tests: `9 passed`.
- Q1 live preflight tests: `10 passed`.
- Normalization tests: `15 passed`.
- B/C capability tests: `10 passed`.
- V4 tests: `65 passed`.
- Story 0184 focused tests: `26 passed`.
- Core capability tests: `21 passed`.

## Next Human Decision

Review and accept/correct/reject the Q1 oracle. Separately approve the dry-run
artifact contract and, only afterward, decide whether to authorize a future
provider-backed live pilot. No live execution is authorized by this report.
