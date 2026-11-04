# Decision Report - Story 0178

## Evidence

The credential-free Story 0177 replay remains deterministic across eight frozen
scenarios. Structural retrieval reaches full answer-expected candidate recall on
the frozen corpus. Semantic and hybrid conditions do not improve final recall
and introduce additional noise in the broad and truncated scenarios. Grounding
acceptance remains preserved, including the wrong-revision, out-of-scope, and
superseded-document negative controls.

The Core ledger now exposes the bounded selection, enrichment, projection, and
preflight measurements already available at the submission boundary. Provider
terminal outcomes are appended only from the callback trace; they are not
inferred from preflight authorization.

## Dominant Bottleneck

The evidence supports a composition and execution-budget bottleneck rather than
an established retrieval-recall bottleneck. The relevant stages are selection,
enrichment/projection cost, prompt preflight, and provider completion. The
ledger distinguishes these stages without changing their policies or budgets.

## Recommendation

Defer production hybrid retrieval. Do not introduce embeddings, vector search,
RAG, or a new semantic candidate producer based on the current evidence. The
next bounded improvement should use the new ledger to measure projection and
provider-terminal outcomes on revision-pinned runtime captures before any
retrieval architecture decision is reconsidered.

## Boundary

This report is an implementation recommendation, not Story acceptance and not
authorization for production retrieval changes.
