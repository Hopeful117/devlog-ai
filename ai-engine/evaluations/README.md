# AI Intent Replay Evaluation

This directory contains the small replay-first evaluation harness authorized by
[ADR-066](../../docs/decisions/ADR-066.md). It deterministically checks reviewed,
production-compatible AI Intent output against scenario-owned structural,
semantic, grounding, trust, quality, and gate expectations.

Replay does not call a model and does not establish current live-model quality.
Live execution is future, explicit work and must not enter normal pytest runs.

## Run a replay

From `ai-engine/`:

```bash
python -m evaluations.runner architecture-overview-v2-enriches-v1
python -m evaluations.runner architecture-overview-v2-enriches-v1 --json
```

The command returns zero only when the scenario gate passes.

## Scenario structure

Each explicitly selected directory under `evaluations/scenarios/` contains:

- `scenario.json`: a versioned controlled `PromptRequest`, fixed required input
  IDs, expected delta/target, deterministic grounding boundaries, gate, and
  reproducibility metadata;
- `replay.json`: one reviewed parsed `InsightGenerationOutput`, capture metadata,
  and the separate reviewed qualitative grounding assessment.

The loader validates production compatibility, exact references, target
membership, digests, and artifact identity before evaluation. Expected absence
is `"expectedDelta": null`; it is not a production delta value.

## Add a future reviewed scenario

1. Add one explicitly named scenario directory with `scenario.json` and
   `replay.json` only.
2. Use fixed controlled UUIDs and include only material evidence.
3. Declare required Facts/Observations and exact grounding allowlists in the
   scenario rather than adding answers to evaluator code.
4. Validate the request/output through existing production contracts.
5. Record qualitative claim assessment as human-reviewed data; never infer it
   with keywords.
6. Add a deterministic integration test and review capture data for secrets.

## Boundaries

The dependency direction is `evaluations -> app`; production must never import
evaluation code. Evaluation evidence cannot accept or reject proposals, mutate
production state, promote trusted knowledge, or enter production prompts.

Do not add live calls to replay, provider orchestration, plugin discovery, a
generic benchmark framework, Agent evaluation, RAG evaluation, an LLM judge, or
a keyword grounding scanner. Canonical artifacts must not contain credentials,
raw provider payloads, headers, hidden reasoning, or sensitive production data.
