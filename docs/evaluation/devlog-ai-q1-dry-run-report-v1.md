# DevLog AI Q1 Runner Dry-Run Report

## Status

- `Q1_RUNNER_DRY_RUN_READY`
- `Q1_LIVE_ENTRYPOINTS_IDENTIFIED`
- `Q1_ORACLE_PENDING_HUMAN_REVIEW`
- `Q1_LIVE_NOT_AUTHORIZED`

This report records a deterministic fixture run only. It is not evidence of
semantic answer quality and did not call a provider or network service.

## Identity

- Branch: `story/0184-bounded-story-context-investigation`
- HEAD: `60fae3e0e1104d0ffbe3832c1f578ed532b1268a`
- Manifest: `devlog-ai-abc-first-test-manifest-v1.json`
- Manifest version: `1.1.0`
- Experiment: `END_TO_END_SYSTEM_COMPARISON`
- Artifact root: `data/devlog-ai-q1-dry-run-004/`
- Copied manifest SHA-256: `ecafc08b3f1ccb8aabd972b0aa2daa721bd32f3fbdbb7eb70390c9e487ac1c80`

## Result

| Observation | Treatment | Technical status | Provider calls | Network calls | Retries |
|---|---|---|---:|---:|---:|
| `Q1-A-DEVLOG-CURRENT-r1` | `A_DEVLOG_CURRENT` | `COMPLETED` | 0 | 0 | 0 |
| `Q1-B-DIRECT-INVESTIGATOR-r1` | `B_DIRECT_INVESTIGATOR` | `COMPLETED` | 0 | 0 | 0 |
| `Q1-C-DEVLOG-ENGINEERING-ANALYST-r1` | `C_DEVLOG_ENGINEERING_ANALYST` | `COMPLETED` | 0 | 0 | 0 |

- RAW artifacts: 3 isolated observations, captured exclusively.
- DERIVED artifacts: 3 normalized outputs, each referencing its RAW hash.
- Trace artifacts: 3 isolated trace files.
- Integrity report: `PASS`.
- Replay: `READY` and deterministic.
- Semantic quality evaluation: not performed.

## RAW File Hashes

| Observation | `raw/answer.json` SHA-256 |
|---|---|
| `Q1-A-DEVLOG-CURRENT-r1` | `dd391e18eca1f68b161fdc500133fb850e99f2e47eb98de5ee6143ad269ee9d8` |
| `Q1-B-DIRECT-INVESTIGATOR-r1` | `a9e0719ae175b37e46418fbcc944ddd7d70f4868f78234a278d3979213891a2a` |
| `Q1-C-DEVLOG-ENGINEERING-ANALYST-r1` | `287903968dfd70f2edd94d206b282154ac373837806d427905816c7d6fa57c39` |

## Validation Commands

```bash
uv run --project ai-engine --extra dev pytest ai-engine/tests/test_devlog_ai_abc_runner.py -q
uv run --project ai-engine --extra dev pytest ai-engine/tests/test_devlog_ai_abc_preflight.py ai-engine/tests/test_devlog_ai_abc_normalization.py ai-engine/tests/test_devlog_ai_abc_capability_comparison.py -q
python3 -m compileall -q ai-engine/evaluations/devlog_ai_abc_runner ai-engine/tests/test_devlog_ai_abc_runner.py
python3 -m json.tool docs/evaluation/devlog-ai-abc-first-test-manifest-v1.json
git diff --check
```

Results: runner tests `9 passed`; existing ABC tests `30 passed`; Python
compilation, JSON validation, and whitespace validation passed.

## Remaining Human Decisions

- Review and accept/correct/reject the Q1 oracle.
- Approve the asymmetric pilot and artifact destination.
- Authorize any future provider/model call separately.
- Decide whether a future live adapter should be implemented for the identified
  A/B/C entry points. This dry-run does not make that decision.
