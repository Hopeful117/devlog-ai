# Story 0179 - Decision Report

## Decision

The next bottleneck to investigate is Core selection composition, specifically
the category concentration limit. The runtime evidence is stable enough to make
this the next investigation target, but not sufficient to change the policy.

## Evidence

- 12 revision-pinned captures were valid.
- Category concentration accounted for 5670 normalized losses, substantially
  more than any other reason.
- Selected and projected counts remained fixed at 60 per capture.
- Enrichment truncation occurred in 9 captures but accounted for only 9 ledger
  loss records.
- Provider execution produced 10 successful analyses, one timeout, and one
  failed terminal result.
- A deterministic-input rerun exposed task reuse across repetitions despite
  distinct idempotency keys. The runner now rejects those duplicate rows rather
  than treating them as independent evidence.

## Next Action

Define a separate bounded Story to evaluate category concentration policy
alternatives offline against answer-critical evidence and the existing grounding
and trust constraints. That Story must compare the current policy with
instrumented alternatives without changing production behavior, and must include
the provider timeout rate as an independent metric rather than attributing it to
selection.

Before relying on a future repeated runtime sample, fix or explicitly contract
the task-idempotency behavior so repeated identical scenario inputs produce
distinct, independently correlated captures.

## Deferred Actions

- Do not introduce hybrid retrieval, embeddings, RAG, or vector search.
- Do not increase selection, projection, prompt, or provider budgets.
- Do not infer provider-visible references from interaction traces.
- Do not change the category concentration policy based only on this runtime
  sample.
