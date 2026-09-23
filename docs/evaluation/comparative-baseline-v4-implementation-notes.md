# Comparative Baseline V4 Implementation Notes

## Implemented

V4 is isolated under `ai-engine/evaluations/comparative_baseline_v4/` and has a
new manifest identity: `comparative-baseline-v4-1.0.0`. It reuses the frozen
V3 benchmark, questions, oracle, repository identity, and revision without
copying or modifying them.

The implementation provides:

- `V4CollectionRuntime` with `DEVLOG` and `AGENT_DIRECT_OPEN` condition paths;
- no direct repository byte budget derived from DEVLOG context size;
- separate direct tool/resource counters and stopping reasons;
- no automatic malformed-tool-call recovery;
- deterministic structural, grounding, semantic-only, and grounded-primary
  evaluation dimensions;
- immutable raw-output hashing and offline replay validation;
- V4 version identities for answer, grounding, semantic, resource, and safety
  contracts;
- provider-free preflight qualification and fixtures.

The official collection remains blocked because V4 safety-ceiling values are
explicitly `PENDING_HUMAN_APPROVAL`. The implementation does not select final
values silently.

## Offline Verification

From `ai-engine/`:

```bash
python -m evaluations.comparative_baseline_v4.preflight
python -m pytest tests/test_comparative_baseline_v4.py -q
python -m pytest tests/test_comparative_baseline.py tests/test_comparative_collection_runtime.py tests/test_comparative_live_adapters.py -q
```

The preflight must report `status: PASS`, `providerCalls: 0`,
`networkCalls: 0`, and `officialCollectionAllowed: false` until ceilings are
approved.

## Unresolved Decisions

- Final values for V4 operational safety ceilings.
- Whether any future safety-ceiling revision should be approved after offline
  budget adequacy qualification.
- Whether to retain the no-recovery policy for official collection, or approve
  a separately versioned deterministic recovery policy. No recovery is currently
  implemented.

## Proposed Future Collection Command

Do not execute until the manifest ceilings, provider configuration, repository
path, grounding bridge, and collection authorization have been human-approved:

```bash
LLM_PROVIDER=openai LLM_MODEL=gpt-4.1-mini LLM_API_KEY=<approved-key> \
python -m evaluations.comparative_baseline_v4.collect \
  --repository <pinned-repository> \
  --run-id <new-v4-run-id> \
  --authorized
```

The `collect` entry point is intentionally not provided in this cleanup/plan
slice. A future collection implementation must first receive the unresolved
ceiling and authorization decisions; no provider call was made here.
