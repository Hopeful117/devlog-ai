# Story 0131 - Implementation Plan

1. Preserve `benchmark-suite-v1.json` as the sole case-definition input.
2. Add strict loader validation for suite identity, case uniqueness, revisions,
   negative-control semantics and required identifier arrays.
3. Add manifest validation that requires exact pinned-revision agreement and an
   explicit resolution for every expected evidence artifact.
4. Added pure scoring for exact set recall/precision, negative control, grounding,
   typed references, context budget, follow-up searches and stability.
5. Added a CLI and live Core-context adapter that emit machine-readable JSON and use non-zero status for
   blocked or failed evaluation states.
6. Add tests for candidate-oracle refusal, revision mismatch, exact scoring,
   manifest defects and invalid benchmark input.
7. Execute the full AI Engine test suite and leave formal RED execution pending
   oracle approval and real capture data.

## Safety Rules

The harness never infers causal support from co-occurrence, never treats
recommendations as facts, never resolves aliases heuristically and never fills
human measurements with defaults.
