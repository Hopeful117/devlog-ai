# Story 0037 — Incremental Architecture Knowledge Evolution — Code Review

## Status

Reviewed

## Review Scope

Review of the first ADR-050 implementation slice:

* ADR-050
* architecture existing-knowledge selection
* architecture delta payload contract
* AI-engine prompt/schema updates
* Core-side contract validation
* enrichment promotion traceability
* no-significant-delta behavior

## Findings

### 1. Existing trusted knowledge is now explicit and bounded ✅

`SelectedKnowledge` now contains a dedicated
`existingArchitectureKnowledge` section, separate from generic
`selectedInsights`.

This resolves the previous ambiguity where trusted knowledge existed in the
prompt but had no distinct comparison semantics.

### 2. The Core validates lifecycle intent structurally ✅

`AiProposalContractValidator` now validates:

* `deltaType`
* `targetInsightId`
* target membership in selected trusted architecture knowledge

This is the correct boundary. The Core does not infer enrichment intent by
parsing prose.

### 3. No-significant-delta is handled without synthetic persistence ✅

Architecture analyses may now return `proposals: []`.

This preserves idempotence and avoids fake validation work or redundant trusted
knowledge creation.

### 4. ADR-006 boundaries remain intact ✅

The AI still only proposes.

The Core still:

* validates;
* persists proposals;
* promotes only after acceptance;
* owns trusted-knowledge mutation.

No direct AI mutation of trusted knowledge was introduced.

### 5. Enrichment traceability is minimal but coherent ✅

Accepted enrichments create:

* a new accepted `Insight`
* a `KnowledgeRelation` from the new Insight to the enriched trusted Insight

Using `DERIVED_FROM` is a pragmatic V1 choice that preserves history without
forcing a new relation taxonomy prematurely.

### 6. Legacy insight selection remains imperfect but acceptable ⚠️

Because older trusted insights may lack `sourceType`, architecture relevance can
only be approximated via normalized `InsightType`.

This is a real limitation, but it is explicitly documented and does not block
the vertical slice.

### 7. Prompt evolution is architecture-only and non-invasive ✅

The new prompt instructions and payload metadata are limited to
`architecture-overview-v1`.

Non-architecture intents do not inherit unwanted delta metadata behavior.

### 8. Tests cover the right failure modes ✅

The new coverage exercises:

* bounded trusted-knowledge selection;
* invalid enrichment targets;
* accepted enrichment traceability;
* empty proposal completion for no-significant-delta;
* AI-engine prompt and payload integrity.

This is the right test shape for an incremental-knowledge slice.

## Gate Results

* Backend `./mvnw verify`: **PASS**
  - 566 tests
  - JaCoCo checks met
* AI engine `./.venv/bin/python -m pytest -q`: **PASS**
  - 48 tests

## Conclusion

Approve.

The implementation is narrow, consistent with ADR-050 and ADR-006, and solves
the observable problem without over-design. No blocking findings remain.
