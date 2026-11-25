# Q1 Technical Gate 3 - Report

## Identity

| Field | Value |
|-------|-------|
| Artifacts | `data/devlog-ai-q1-gate-3/` |
| Manifest | `devlog-ai-abc-first-test` 1.1.0 (historical, repeatable Q1 contract) |
| Runtime revision | `19eb8587` (Stories 0237-0244, pushed, Core-verified) |
| Frozen source revision | `0673d1d3` |
| Core plan / authorization | `1e9d4511...` READY / `893ce573...` APPROVED then CONSUMED |
| Status | `Q1_LIVE_EXECUTION_PARTIAL` |
| Integrity | PASS (all raw, envelope and derived hashes match) |
| Provider calls | 16 |

The live bootstrap flag for B was enabled for this run only (shell override),
then restored to the default `false`, as in gate 2.

## Results per condition

### A - DevLog current: FAILED (`GROUNDING_UNKNOWN_REFERENCE`)
- Task `71907c90...` at `0673d1d3`, two attempts, both rejected by grounding
  validation (fail-closed, as designed):
  1. A finding cited `file:...:ai-engine/app/schemas/story_context_analysis.py@0673d1d3...`,
     a file that was not selected and so not in the citable list.
  2. After the corrective retry, the objective understanding cited ADR-006 with
     a **mistyped commit hash** (`...0673d1d32433cdaff054d5f1defd1d1bedd...`
     instead of `...0673d1d32433cdaff054d5f1defd1bedd...`). The reference was
     in the citable list, but the model did not copy it character for
     character.
- In gate 2 the same condition COMPLETED (honest abstention). A's outcome now
  varies between runs.

### B - Direct investigator: COMPLETED
- Confidence `HIGH`, 4 claims, five-stage flow with invariant ownership. The
  reading is consistent with gate 2: it covers Q1-C1 and Q1-C2, and mostly
  covers Q1-C4. On Q1-C3 it now mentions investigation orchestration, but not
  that it applies only to the investigation-enabled path.

### C - DevLog engineering analyst: FAILED (`INVESTIGATION_ACTION_REJECTED`)
- Task `2d4a4083...`:
  1. `SEARCH_EVIDENCE`: `RESULTS`, 10 in-scope items (D-1 fix confirmed in
     the gate).
  2. `SEARCH_EVIDENCE`: `REJECTED` / `PATH_OUT_OF_SCOPE`. The model requested
     a path outside the authorized prefixes.
- Only budget refusals lead to FINAL (Story 0244). Any other refusal is still
  terminal, even with 10 relevant items gathered.
- The pinned check run `3696e2d1` (Story 0244, same request) COMPLETED with
  `MEDIUM` confidence, so C now completes in 1 of 2 live attempts.

## Findings

1. **The remaining failures are model compliance failures that Core correctly
   rejects.** Retrieval, revision, content and budget now behave as designed.
   The model does not always:
   - copy a 40-character commit hash exactly;
   - restrict citations to the citable list;
   - keep actions within the authorized paths.
   Grounding stays fail-closed, so this costs completion, not correctness.
2. **References are fragile by construction.** A citable reference embeds a
   source UUID and a full commit hash (about 110 characters). One mistyped
   character invalidates the whole answer.
3. **One out-of-scope action ends an investigation** even when gathered
   evidence would support a FINAL.

## Candidate directions (need a human decision)

- **Short reference aliases.** The prompt lists each citable reference under a
  short alias (for example `R1`..`Rn`), and the model cites aliases. Python
  maps them back to the exact references deterministically before validation,
  and an unknown alias is rejected. Java validation is unchanged and still
  checks exact references, so grounding is not weakened. This removes the
  copying failure mode.
- **Finalize on any refused action after gathered evidence.** Extend the Story
  0244 budget rule to all Core refusals (`PATH_OUT_OF_SCOPE`, etc.), or allow
  one corrected action within budget. Core's refusal is unchanged.
- **Pre-validate action paths in Python** against the authorized prefixes
  before sending, as a defensive check (Core stays authoritative).

## Gate verdict

**NOT PASSED** (A and C failed). Per the plan, the gate stops without retry.

What passed:
- integrity;
- revision pinning;
- in-scope retrieval for C;
- B completing on a fresh binding;
- freshness known.

Open from earlier gates:
- the planning wall-clock ceiling (120 s per observation, 3 h campaign) vs
  C's 300 s budget;
- the campaign Q1 slot already consumed;
- the runbook missing the live bootstrap flag;
- the callback FK race.

## Follow-up (Story 0245)

The human chose aliases and finalize. With citable aliases (`R1..Rn`,
resolved deterministically before validation) and FINAL after any refusal
once evidence exists, pinned direct runs of A (`9da8e73a`) and C (`301eb1de`)
both COMPLETED with no grounding retry. A abstained honestly at LOW; C
answered at MEDIUM with 14 grounded findings. A full authorized gate 4 has not
been run yet. See
[Story 0245](../stories/0245-citable-reference-aliases/story.md).
