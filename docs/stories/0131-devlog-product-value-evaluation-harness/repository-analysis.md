# Story 0131 - Repository Analysis

## Relevant Existing Infrastructure

The existing `ai-engine/evaluations` package is a replay-first deterministic
harness for reviewed AI Intent outputs. It is not a live product-value
benchmark and does not call providers. Story 0131 therefore adds an isolated
`evaluations.product_value` package rather than changing the existing replay
contract.

The frozen source of truth remains:

`docs/stories/0130-devlog-product-value-parity-investigation/evaluation/benchmark-suite-v1.json`

Its expected revision is
`18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149`, and its oracle status is
`CANDIDATE_REQUIRES_HUMAN_VALIDATION`.

## Boundary Check

The implementation imports no application service and has no dependency from
production code to evaluation code. Captures and artifact manifests are inputs
from a separately operated Core/imported-repository adapter. This prevents the
harness from becoming a second retrieval or context-construction path.

## Live Product Path

`POST /api/projects/{projectId}/engineering-story-context?detail=AGENT` was
selected because it is an existing Core-owned, agent-facing REST projection and
accepts each benchmark question as its description. It returned HTTP 200 for
all four cases against the pinned imported revision. This is a context capture,
not a generated answer: no provider/model was involved.

## Independent Repository Verification

The Trading OS checkout at `/home/ludo/Bureau/workspace/trading-os` contains the
exact pinned commit. The checkout has unrelated uncommitted changes, so the
resolver uses only Git objects and the exact tree for
`18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149`.

All 19 expected artifacts resolve independently: commits are checked for object
existence and ancestry, while files are checked with exact `revision:path`
lookups and blob hashes. The dated repository manifest is authoritative for
existence; the dated DevLog manifest remains retrieval measurement only.

## Evidence Limitation

The live path produced an actual exhaustive manifest with 19 unique expected
artifacts: 10 were returned by the projection and 9 were not. `NOT_FOUND` in
that manifest means not returned by this projection, not repository absence.
The direct repository baseline remains unexecuted; it is a separate agent
experiment, not the repository ground-truth resolver.
