# Story 0035 — Richer Validated Knowledge — Engineering Report

## Status

Reported

## Purpose

First implementation slice of ADR-049. Verify that accepted knowledge retains enough semantic richness to be genuinely useful for both human and machine consumers, by stopping the lossy promotion identified in the Knowledge Usability Audit (P1 & P2).

## What Was Verified

### Semantic preservation during promotion
- `rationale`, `confidence`, `evidenceReferences`, `sourceType` now survive the trust transition from `ValidatableProposal` to `Insight`.
- Promotion is a **trust transition, not a semantic compression step** (ADR-049 guiding principle).
- The trusted knowledge read API (`InsightResponse`) exposes these fields end-to-end.

### Decision traceability
- `ValidatableProposal.decidedAt` is now populated on accept and reject, so trusted knowledge can be traced to its decision timestamp.

## Alignment With ADR-049

| ADR-049 point | Status |
|---|---|
| §2 rationale preserved | ✅ |
| §3 evidence preserved | ✅ (evidenceReferences) |
| §4 confidence not silently discarded | ✅ |
| §5 semantic types not reduced without recoverability | ✅ (sourceType keeps original) |
| §6 validation provenance preserved | ✅ (decidedAt + existing proposal/validation links) |
| §7 promotion atomic | ✅ (unchanged) |
| §8 structured, no opaque map | ✅ |
| §9 raw AI output not blindly trusted | ✅ |
| §14 KnowledgeEvent not redefined | ✅ (untouched) |
| §15 relations deferred | ✅ (untouched) |
| §16 diff persistence out of scope | ✅ |

## Guidance for Subsequent Slices

1. **Frontend projection** (ADR-049 §11): expose the now-preserved richer fields in the Knowledge UI. This is safe to do next since the data now exists at the lifecycle layer.
2. **`ProjectState` / Timeline consumption**: these read models may now consume richer trusted knowledge; they must not invent missing rationale/evidence/confidence themselves.
3. **Future relations / graph**: richer nodes now exist; a later slice may expand `KnowledgeRelation.EntityType` (P6) on top of them, per ADR-049 §15 ordering.

## Engineering Notes

- Slice intentionally avoids: frontend, KnowledgeEvent redesign, relation expansion, graph DB, diff persistence, taxonomy change, historical backfill — all deferred by ADR-049.
- Non-goals respected: no opaque JSON blob, no weakening of validation boundaries.

## Gate Summary

- Backend `./mvnw verify`: SUCCESS, 556 tests, coverage met.
- Frontend lint/format: clean (unchanged).
- SonarQube gate: OK (new_coverage 80.4%, new_violations 0).

## Conclusion

The first slice of ADR-049 is complete and verified. Accepted knowledge now retains the semantic information needed to answer *what we know, why it matters, why we believe it, where it came from, and which validation accepted it*. This unblocks the frontend/projection slices next.