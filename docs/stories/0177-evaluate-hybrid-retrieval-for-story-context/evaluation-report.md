# Story 0177 - Offline Evaluation Report

## Status

Preliminary deterministic evaluation report. The semantic condition is an
offline test double over a frozen fixture corpus; it is not a production RAG
result and does not authorize vector or embedding infrastructure.

## Corpus

- Protocol: `story-0177-hybrid-retrieval/v1`
- Corpus version: `1.0.0`
- Scenarios: 8
- Conditions: `LEXICAL_CONTROL`, `STRUCTURAL_CONTROL`,
  `SEMANTIC_EXPERIMENT`, `HYBRID_EXPERIMENT`
- Model/provider calls: none
- Repository/runtime changes: none

## Preliminary Results

| Condition | Candidate recall | Final recall | File coverage | Noise | Grounding | Context tokens | Projection tokens | Timeout |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| `LEXICAL_CONTROL` | 0.875 | 0.875 | 0.875 | 0.250000 | 1.000 | 4.500 | 12.000 | 0.000 |
| `STRUCTURAL_CONTROL` | 1.000 | 1.000 | 1.000 | 0.229167 | 1.000 | 5.375 | 14.750 | 0.000 |
| `SEMANTIC_EXPERIMENT` | 1.000 | 1.000 | 1.000 | 0.312500 | 1.000 | 6.125 | 18.500 | 0.000 |
| `HYBRID_EXPERIMENT` | 1.000 | 1.000 | 1.000 | 0.312500 | 1.000 | 6.125 | 18.500 | 0.000 |

All conditions passed the offline revision, project/source, trust-tier,
citability, out-of-scope, and superseded-document filters in the current
fixture corpus. Grounding acceptance is 1.0 for every condition. Replay
digests are deterministic; one of eight scenarios carries a truncation warning,
and no timeout is modeled in the fixture corpus.

## Interpretation

The synthetic semantic condition recovers the semantically indirect target that
the lexical control misses. It also produces more noise than the structural
control. This is evidence that semantic retrieval can improve recall, but not
evidence that a production RAG system is justified.

The current corpus is not sufficient to decide production architecture because:

- candidates are fixture-defined rather than collected from a live repository;
- the semantic producer is a test double, not an embedding model;
- latency and provider execution are not measured in replay;
- the corpus does not yet include observed live candidate sets;
- the final Story Context prompt/provider path is not exercised by replay.

## Decision

No production retrieval change is recommended from this report. The evaluation
infrastructure is ready to be extended with reviewed live captures containing
Core-selected and discarded references. A future hybrid retrieval Architecture
Story may be considered only if those captures reproduce a material lexical
recall gap after strict revision/trust filtering, and if the gain survives Core
composition without unacceptable noise, prompt growth, or timeout regression.

## Validation Evidence

- `python3 -m py_compile evaluations/hybrid_retrieval.py tests/test_hybrid_retrieval_evaluation.py`
- `python3 -m evaluations.hybrid_retrieval --json`
- manual deterministic safety and metric assertions: passed
- The existing ADR-066 `evaluations.runner` remains unchanged because its
  reviewed-AI-intent artifact contract is not a retrieval-capture contract;
  this Story uses the standalone model-free evaluator under
  `evaluations/hybrid_retrieval.py`.

`pytest` was not available in the host or AI Engine container, so the pytest
suites remain unexecuted and must not be reported as passed.
