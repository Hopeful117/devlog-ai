# Repository Analysis — Story 0101

## Story

**0101 — Connect Analysis Results to Trusted Engineering Artifacts**

Status entering this mission: `READY_FOR_REPOSITORY_ANALYSIS`.

This analysis is repository-analysis only. It introduces no production code, test, endpoint,
Angular change, migration, implementation plan, commit, or remote mutation.

## Human Repository Analysis Review

**HUMAN_REPOSITORY_ANALYSIS_REVIEW = APPROVED**

The repository findings are accepted. No additional repository investigation is required before
planning.

## Repository State

| Item | Observed value |
|---|---|
| Branch | `story-101-analysis-trusted-artifact-navigation` |
| HEAD | `d122f894870d317d1c1b025c74d4cddd7c069cc2` |
| Worktrees | One: `/home/ludo/Bureau/workspace/devlog-ai` |
| Remote state | `main...origin/main` before the fetch attempt |
| Fetch | Attempted; failed because the SSH security-key agent refused signing |
| Worktree | Dirty before this mission |

Pre-existing changes include the completed Story 0101 implementation on this branch. The only
files created by this mission are this artifact and the other Story 0101 lifecycle documents.

## Governing Architecture

Story 0101 extends the canonical Analysis Result read model with query-time trusted-artifact
resolution. The authoritative source of truth for trusted artifact provenance remains the persisted
reverse references on the trusted artifacts themselves:

- `Insight.proposal`
- `Decision.proposal`
- `EngineeringEvent.proposal`

ADR-006 governs the work. `ADR_REQUIRED = NO`.

## Source of Truth Matrix

| Concern | Source of truth |
|---|---|
| Proposal lifecycle state | `ValidatableProposal.status` |
| Proposal type | `ValidatableProposal.type` |
| Trusted artifact provenance | Reverse reference on trusted artifact entity |
| Analysis Result composition | Query-time read model in `AnalysisResultQueryServiceImpl` |
| Trusted artifact identity | Persisted entity id on promoted artifact |
| SPA route construction | Angular frontend |

No `promotedArtifactId` or promoted artifact type is persisted on `ValidatableProposal`. No
provenance table is introduced. Trusted-artifact resolution occurs at query time by grouping
accepted proposal ids by `ProposalType` and batch querying the relevant repository.

## Existing Reverse Provenance

| Trusted artifact type | Reverse reference field | Repository batch method |
|---|---|---|
| `Insight` | `Insight.proposal` | `InsightRepository.findByProposalIdIn(Collection<UUID>)` |
| `Decision` | `Decision.proposal` | `DecisionRepository.findByProposalIdIn(Collection<UUID>)` |
| `EngineeringEvent` | `EngineeringEvent.proposal` | `EngineeringEventRepository.findByProposalIdIn(Collection<UUID>)` |

The `DecisionRepository.findByProposalIdIn` method was added as part of this Story to support the
batch resolution contract. The other two batch methods already existed.

## Query / Performance Contract

Accepted promotable proposal ids are grouped by `ProposalType` and resolved in batch:

- one proposal query for the Analysis
- at most one Insight batch lookup
- at most one Decision batch lookup
- at most one Engineering Event batch lookup

No N+1 trusted-artifact lookup is introduced.

## Functional Behavior

### PROPOSED

`trustedArtifact = null`. No promoted trusted artifact is implied.

### ACCEPTED + resolved

Expose:

- actual artifact id
- artifact type
- `availability = AVAILABLE`
- `detailAvailable = true`

### ACCEPTED + unresolved

Expose:

- `id = null`
- expected artifact type derived from `ProposalType`
- `availability = UNAVAILABLE`
- `detailAvailable = false`

This is an explicit read-model integrity state. No artifact id or link is fabricated.

### REJECTED

Rejected proposals remain excluded from the canonical Analysis Result according to Story 0100
semantics.

## Navigation Contract

Trusted artifact navigation is a frontend responsibility:

- Insight → `/insights/:id`
- EngineeringEvent → `/engineering-events/:id`
- Decision → `/decisions/:id`

The backend does not emit Angular URLs.

## Decision Surface

Story 0101 adds the smallest coherent human-facing Decision detail surface needed to inspect a
trusted Decision reached from Analysis provenance:

- route: `/decisions/:id`
- source API: `GET /api/v1/decisions/{id}`
- behavior: read-only detail only

The Decision response exposes nullable `proposalId`, mapped from the existing persisted
`Decision.proposal.id`, to preserve human provenance navigation without introducing any new
persistence.

## No Architectural Conflict

No `ARCHITECTURAL_CONFLICT` or `SCOPE_CONFLICT` was found. The implementation extends the existing
read model with query-time composition only. No new persistence, migration, prompt, provider, MCP,
RAG, vector, or retrieval behavior was introduced.
