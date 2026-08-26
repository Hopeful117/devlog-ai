# Implementation Report — Story 0094

## Branch

`feature/story-0094-harden-engineering-event-grounding` (base: `main @ 491d0cf`)

## Story

0094 — Harden Engineering Event AI Grounding Contract and Failure Diagnostics

## Status

READY_FOR_COMMIT_APPROVAL

## Production Changes

### Modified Files (AI Engine only)

1. `ai-engine/app/prompts/engineering_event.py`
   - GROUNDING CONTRACT section rebuilt: three explicit allow-list blocks
     (`ALLOWED_SUPPORTING_FACT_IDS` / `ALLOWED_SUPPORTING_OBSERVATION_IDS` /
     `ALLOWED_EVIDENCE_REFERENCES`, one `- id` per line) followed by copy-exact
     prose: never derive/shorten/extend identifiers; empty arrays allowed per
     field while every proposal keeps ≥1 grounding element overall;
     identifiers appearing elsewhere in SelectedKnowledge (project/source/
     analysis/event IDs, commit hashes) are NOT citable unless allow-listed.
   - `corrective_retry` now appends a structured CORRECTIVE RETRY block:
     rejection reason (offender-naming), restatement that ONLY the
     ALLOWED_* lists above are valid sources, unrelated-ID prohibition, and
     permission to return an empty proposals array.
   - Template identifier unchanged (`analyze-engineering-event-prompt-v1`,
     backend-owned intent identity); content digest changes per call as before.
2. `ai-engine/app/services/engineering_event_generation_service.py`
   - `_generate` raises offender-naming errors via `_require_subset` helper:
     `"<field> contains identifiers absent from the corresponding allowed
     list: [...]"`; duplicate-proposal detection unchanged.
   - Second-attempt exception handling split (mirrors insight service):
     output-invalid types → terminal `INVALID_LLM_OUTPUT`;
     any other exception → `LLM_PROVIDER_ERROR`. First-attempt behavior
     already classified provider failures correctly and is unchanged.

### Change Description

The model can no longer confuse the ~dozens of non-citable UUIDs in
SelectedKnowledge with citation targets: the only IDs presented as citable are
the three labeled allow-lists, under an explicit contract. When grounding
still fails, the corrective attempt receives the offending values plus the
restated contract instead of the opaque string `"Unknown supportingFactIds"`.
Provider outages during retry are reported as provider failures.

## What Is NOT Changed

- Backend Java/DB/schema: NONE (verified assumption from investigation).
- Grounding validator strictness: unknown IDs remain rejected.
- Bounded retry: exactly one corrective attempt (unchanged).
- insight.py / Insight generation: untouched.
- MCP, ranking/budget, RepositoryContextAdapter, freshness/sync, deliverables,
  Cockpit UI: untouched.

## Test Results

### New suite `tests/test_engineering_event_grounding.py` (12 tests)

```
prompt presents separated allow lists ............... PASS
prompt states copy-exact grounding rules ............ PASS
prompt states empty-list domain rule ................ PASS
accepts single allowed fact id ...................... PASS
accepts multiple allowed fact ids ................... PASS
rejects unknown fact id and names it in retry ....... PASS
rejects source identifier cited as fact id .......... PASS
empty fact ids accepted when observations ground .... PASS
fully ungrounded proposal rejected, bounded retry ... PASS
persistent invalid output ends INVALID_LLM_OUTPUT ... PASS
first-attempt provider timeout stays PROVIDER_ERROR . PASS
second-attempt provider failure stays PROVIDER_ERROR  PASS
```

Matrix coverage per mission §17: [A]→accepted · [A,B]→accepted ·
[random]→rejected+named · [sourceId]→rejected+named · []+observations→
accepted · [] everywhere→schema-rejected (domain rule verified first).

### Full AI Engine suite

```
python3 -m pytest tests/
83 passed in 5.33s
```

(71 pre-existing + 12 new; includes all prior engineering-event and
insight-service tests — non-regression green.)

An intermediate failure of six unrelated insight/provider tests was caused by
class-level monkey-patching in a draft test leaking shared state; fixed by an
instance-scoped wrapper provider before final green run.

## Invariant Verification

- [x] Validator strictness unchanged (unknown IDs always rejected)
- [x] Unrelated UUIDs cannot be cited (subset check against explicit allow-lists)
- [x] Retry is bounded at one; diagnostics include offenders + allow-lists + rules
- [x] INVALID_LLM_OUTPUT ≠ provider failure (both attempts classified separately)
- [x] No secrets/internal dumps in diagnostics (only ID/path values already in prompt scope)
- [x] Insight flow untouched and green
- [x] Backend compatibility: zero Java/DB diffs (git-verified)

## Runtime Validation (post-deploy)

Rebuilt/redeployed: `docker compose up -d --build ai-engine` → healthy.
Five live Analyze Evolution runs against legitimate commits:

| Run | Target | Outcome |
|---|---|---|
| 1 | de890f8 | COMPLETED (0 proposals — insufficient evidence path, valid) |
| 2 | 491d0cf | COMPLETED |
| 3 | de890f8 | **FAILED — INVALID_LLM_OUTPUT, diagnostic:** `evidenceReferences contains references absent from the allowed list: ['backend/src/main/java/com/hopeful117/devlogai/repositorysync/RepositorySyncJobExecutor.java', …]` |
| 4 | 491d0cf | COMPLETED (1 proposal) |
| 5 | de890f8 | COMPLETED |

Before (same session, pre-fix): 3 runs → 1 COMPLETED, 2 FAILED with opaque
`Unknown supportingFactIds`. After: 5 runs → 4 COMPLETED, 1 FAILED whose
message names every offending reference. Small sample — no statistical claim.

## Grounding Proof (runtime)

Task `3db2b48e…` (COMPLETED, proposalCount=1): cited
`supportingFactIds=["a090fe00-68f4-47e8-9fd4-007f96c49c93"]` — present in that
task's persisted `selected_knowledge_snapshot.selectedFacts` allow-list
(40 facts); observation/evidence citations also validated by the untouched
backend subset checks. Zero unknown IDs.

## ADR Assessment

NO NEW ADR — repair within existing AI-proposal grounding architecture
(ADR-038/042 lineage); no durable architectural decision introduced.
