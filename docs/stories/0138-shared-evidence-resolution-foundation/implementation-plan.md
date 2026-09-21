# Implementation Plan - Story 0138

## Status

`SLICE 6 IMPLEMENTED - NEXT FAMILY EXPANSION NOT AUTHORIZED`

Slice 0 source-selection alignment, Slice 1's explicit resolution seam, Slice
2's Git/document adapters, Slice 3's Fact adapter, Slice 4's snapshot boundary
verification, Slice 5's consumer projections and Slice 6's six persisted-family
adapters were implemented. Human acceptance remains pending.

## Preconditions

These were historical preconditions for implementation. The source-selection
policy and explicit facade decisions were made and implemented in Slices 0 and
1; they are retained here for provenance, not as current blockers.

The previous source-selection contradiction is resolved for the affected paths.

## Proposed Slices

### Slice 0 - Source policy alignment

Document and test deterministic source selection. No shared resolver should
silently inherit `sources.getFirst()` behavior.

### Slice 1 - Resolution seam

Define the smallest shared boundary for:

- canonical reference recognition;
- evidence-family dispatch;
- structured success metadata;
- structured deterministic failures;
- explicit current versus task-snapshot mode.

Do not create a universal domain `Evidence` type. Use family-specific payloads
behind a common metadata envelope only if human design review accepts it.

Status: implemented with an explicit facade, canonical Git/document parser,
family dispatch seam, metadata envelope, family-owned payload marker and stable
failure codes. Git, document, Fact and Slice 6 family adapters are registered.

### Slice 2 - Git and pinned document resolution

Adapt existing capabilities for:

- `git:{sourceId}:{sha}`;
- `document:{sourceId}:{path}@{revision}`;
- optionally `file:{sourceId}:{path}@{revision}`.

Preserve explicit source and revision. A missing pinned revision must not fall
back to HEAD/current state.

Status: implemented with source-scoped persisted Git commit resolution and
revision-pinned document content resolution. Workspace revision mismatches are
rejected; no current-revision fallback is permitted.

### Slice 3 - One persisted domain family

Add exactly one domain family after the seam is accepted. Candidate choices are
Fact or Insight, selected by the human design decision according to scope and
historical requirements.

Status: implemented for `fact:{uuid}` with an explicit `analysisId` scope.
Cross-analysis access and missing analysis scope fail as `UNAUTHORIZED`.

### Slice 4 - Snapshot compatibility

Keep `TaskSnapshotEvidenceResolver` as an explicit task-snapshot path. Add only
the adapter or tests needed to prove it cannot bypass the originating snapshot.

Status: implemented as an explicit boundary verification. Current-state Git,
document and Fact adapters reject `TASK_SNAPSHOT` before repository access;
`TaskSnapshotEvidenceResolver` is verified against the originating snapshot.

### Slice 5 - Consumer adapters

Add thin REST/MCP projection tests after Core resolution semantics are stable.
MCP must not become the canonical resolver.

Status: implemented. REST projects `EvidenceResolutionFacade` through
`/api/v1/evidence/resolve`; MCP proxies the canonical reference to that
endpoint in `CURRENT` mode without adding project identity or resolution logic.

### Slice 6 - Repetitive family adapters

Only after the pattern is reviewed, add additional domain and compatibility
adapters for Decisions, Events, Stories, Analyses, Artifacts and Challenges.

Status: implemented. Each family has an explicit parser family, resolver,
family-owned payload and repository-backed current-state read. Task snapshots
remain delegated to `TaskSnapshotEvidenceResolver` and lazy ownership fields
are loaded through explicit repository graphs.

## Expected Tests

- Canonical family dispatch and malformed-reference tests.
- Unknown and unsupported family tests.
- Ambiguous source tests with no first-source fallback.
- Missing source, revision, commit, file and document tests.
- Explicit revision preservation and no HEAD fallback tests.
- Task snapshot versus current-state separation tests.
- One domain-family scope and not-found tests.
- Structure aggregate `UNSUPPORTED_EXPANSION` tests.
- Provenance/source/revision preservation tests.
- MCP/REST projection tests that prove transport does not define identity.

## Explicitly Deferred

- Story 0139 legacy migration.
- Historical reference rewriting.
- Complete application authorization architecture.
- Universal retrieval, ranking or composition changes.
- RAG, vector search and ContextPack.
- MCP resource redesign.
- Human evidence attachment persistence.

## Final Acceptance Boundary

The implementation and Core architecture are accepted. Resolution does not
imply authorization, and the current deployment does not establish a complete
application-user authentication and authorization boundary. Evidence Resolution
must remain within the current trusted deployment boundary until a dedicated
application-security Story defines caller identity, authentication,
project/analysis ownership, endpoint authorization and REST/MCP propagation.

Security classification:

```text
FOLLOW_UP_SECURITY_STORY_REQUIRED
```

Story 0139 remains `USEFUL_BUT_DEFERRABLE`. No authentication or authorization
implementation is part of Story 0138.

## Validation Commands After Authorization

```bash
./backend/mvnw -pl backend test -Dtest=<focused-resolution-tests> -B
./backend/mvnw -pl backend -am clean verify -B
git diff --check
```

Validation executed after implementation:

- Focused Slice 6 tests: 22 passed.
- Backend `clean verify`: 1,385 tests passed; JaCoCo passed.
- MCP suite: 54 tests passed; production package passed.
- `git diff --check`: passed.
