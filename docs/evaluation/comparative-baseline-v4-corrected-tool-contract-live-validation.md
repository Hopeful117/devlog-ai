# Comparative Baseline V4 Corrected Tool Contract Live Validation

## Observation

- Attempt: `corrected-v4-tool-contract-live-validation-20260918T000000Z`
- Timestamp: `2026-09-17T23:21:28Z` (UTC)
- Condition: `AGENT_DIRECT_OPEN`
- Question: `CASE-03@1.0.0`
- Repository revision: `18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149`
- Provider/model: `openai` / `gpt-4.1-mini`
- This was exactly one validation observation, not an official dataset result.

## Frozen Contract

- Runtime: `comparative-v4-live-runtime-contract-3.0.0`
- Runtime digest: `9d80f6677c37fadbc743b1bc523d781c771872b655b38affde5ac540c228dbb0`
- Execution identity: `f40a1dfd969f148ee8654f48189a2b78882ff89f9f8d517dccf0f9385e9add73`
- Experiment identity: `3c9b60e071cab1342b78ab8bf3d448d6e6d3b673a9c3b317c3b96070fdf4a1fe`
- Pilot identity: `2e2313c50a06fbc07ca3695abe89d960298e5a26bd814c28b8d21a838203dfe8`
- Preflight: `PASS`; six typed provider tools were active.
- Natural completion was active; resource equalization and the historical
  12-operation ceiling were inactive.
- Emergency guards remained 96 tool operations, 48 model turns, 48 provider
  calls, 300 seconds, and 65536 result bytes per operation.

## Result

- Termination: `COMPLETED`.
- Final answer produced: `YES`.
- Structural validity: `YES`.
- Semantic eligibility/evaluation: `YES` / evaluated.
- Semantic correctness: `false`.
- Grounding validity: `YES`.
- Correct-grounded result: `false`.
- Guard triggered: `NO`; stopping reason `NOT_STOPPED`.

### Resources

- Tool attempts: `15`.
- Valid executed operations: `15`.
- Invalid requests: `0`.
- Skipped requests: `0`.
- Searches: `15`.
- Reads: `0`.
- Git operations: `0`.
- Model turns: `16`.
- Provider calls: `16`.
- Final-answer calls: `1`.
- Repository bytes produced/delivered: `993`.
- Input tokens: `16,096`.
- Output tokens: `580`.
- Total tokens: `16,676`.
- Latency: `26,437 ms`.
- Cost: unavailable.

The complete ordered tool trajectory is preserved in the RAW artifact. All 15
typed calls were accepted and executed as `EXECUTED_SUCCESS`; no Policy A
contract error occurred. The model obtained search results but did not request
a repository read before synthesizing its final response.

## Comparison With Historical Pathology

The historical observation had `50/72` invalid requests, including 40
`arguments_not_object` and 10 `operation_not_string`. The corrected observation
had `0/15` invalid requests. The malformed-envelope pathology therefore did not
recur in this validation. This is evidence about interface execution only; it
does not establish answer quality, efficiency, or DevLog value.

## Integrity and Isolation

- RAW count: `1`.
- DERIVED count: `1`.
- RAW hash and wrapper hash: `PASS`.
- RAW-to-DERIVED linkage: `PASS`.
- Deterministic replay: `PASS`; replay provider/network calls `0/0`.
- Resource accounting: `PASS`.
- Termination classification: `PASS`.
- Contamination: `PASS`.
- Condition isolation: `PASS`; no DEVLOG context or prior DIRECT trajectory was
  supplied to the provider input.
- Live provider calls: `16`.
- Live network transports: `16`.
- No retry or replacement observation occurred.

## Primary Questions

- A. Corrected six-tool provider-visible contract received: `YES`.
- B. Schema/runtime parity during live interaction: `YES`.
- C. Historical malformed-envelope pathology materially recurred: `NO`.
- D. Realistic repository inspection opportunity: `YES`.
- E. Natural final answer reached: `YES`.
- F. If no final answer, primarily caused by the old contract defect: `N/A`.
- G. Emergency guard triggered: `NO`.
- H. Resources observed rather than equalized: `YES`.

## Classification

`VALIDATION_PASS`

The typed contract was active, every emitted tool request was valid and
executed, the agent reached a natural final answer, and replay/integrity/
isolation checks passed. The answer’s semantic result is diagnostic only and is
not a comparative conclusion.

Exact next human decision: decide whether to authorize the corrected V4 official
18-slot collection. This report does not authorize or start that collection.

No runtime, schema, prompt, evaluator, guard, benchmark, or historical artifact
was modified after the observation.
