# Decision Report - Story 0183

## Decision

The trust-coverage blocker for offline candidate-policy comparison is resolved.
No production selection-policy change is recommended from this campaign.

The next decision may evaluate category-composition alternatives using the
enriched fixture, but it must remain a separate human-approved policy decision.

## Evidence

- `2052` candidate rows received Core-derived fixture trust values.
- `0` fixture rows were `UNKNOWN` in the captured campaign.
- `current`, `balanced`, and `unrestricted` all replayed `180` selected rows
  across three captures.
- All three policies grounded `3` selected references in the replay.
- Trust composition differed materially: `current` retained `33 TRUSTED` rows,
  `balanced` retained `4`, and `unrestricted` retained none.
- One provider outcome remained incomplete, so this evidence does not establish
  model-output quality.

## Boundary

The campaign supports honest trust-aware offline comparison. It does not prove
that a policy alternative is better, does not justify changing category
concentration limits, and does not authorize increasing context or provider
budgets.

## Deferred Actions

- Do not change the production selection policy automatically.
- Do not infer provider-visible attribution from fixture trust.
- Do not introduce hybrid retrieval, embeddings, RAG, or vector search.
- Do not treat the single-repetition sample as variance evidence.
- Obtain separate human approval before defining or implementing a policy change.
