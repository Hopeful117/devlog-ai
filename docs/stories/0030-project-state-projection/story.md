# Story 0030 — Project State Projection

## Status

Completed

## Objective

Expose a deterministic, low-latency, LLM-free endpoint that projects DevLog's existing knowledge into a human-readable representation of a project's current state, consumable by both engineers and agents.

## Motivation

DevLog maintains rich project knowledge — Engineering Stories, ADRs, Challenges, Proposals, Milestones, Commits, Knowledge Events — but this data is scattered across entities. An engineer must currently ask a LLM "where is the project?" which requires expensive context reconstruction.

The knowledge already exists. What's missing is a deterministic projection that answers 5 questions in < 30 seconds:

1. What are we trying to achieve now?
2. What is currently being developed?
3. What is blocked or needs my action?
4. What just changed?
5. What comes next?

This projection must be:
- computed on demand from persisted data (no new tables, no new storage);
- deterministic (same data → same result, no LLM);
- fast (< 100ms response);
- consumed identically by humans (via Angular UI) and agents (via API).

## Acceptance Criteria

### Backend

- AC-1: `ProjectStateProjectionService` assembles a `ProjectStateResponse` from existing repositories (Project, EngineeringStory, Challenge, ValidatableProposal, Decision, Milestone, KnowledgeEvent, ProjectCommit). No new persistence.
- AC-2: `ProjectStateController` exposes `GET /api/v1/projects/{projectId}/state` returning the projection.
- AC-3: `ProjectStateResponse` contains 5 sections: `objective`, `activeWork`, `recentChanges`, `roadmapProgress`, `pendingActions`.
- AC-4: `objective` section includes: project description, current milestone (if IN_PROGRESS), active story (if IN_PROGRESS), open challenges (if any).
- AC-5: `activeWork` section includes: in-progress stories, open challenges, proposed proposals.
- AC-6: `recentChanges` section includes: recently completed stories (last 5), recent decisions (last 5), recent commits (last 10).
- AC-7: `roadmapProgress` section includes: planned milestones, registered (not started) stories.
- AC-8: `pendingActions` section includes: proposed proposals, open challenges, unstarted stories.
- AC-9: Endpoint returns 404 when project does not exist.
- AC-10: Endpoint responds in < 100ms (no N+1 queries).
- AC-11: Jakarta validation on path variable (UUID format).
- AC-12: MapStruct mapper for entity → DTO conversion.
- AC-13: Unit tests for ProjectStateProjectionService covering each section with populated data and empty data.
- AC-14: Integration test for GET /api/v1/projects/{projectId}/state endpoint.

### Frontend

- AC-15: Angular `ProjectOverviewComponent` displays the 5 sections from the API response.
- AC-16: Each section has a clear heading matching the question it answers.
- AC-17: Stories, challenges, proposals, milestones, and decisions are displayed as clickable items linking to their detail views.
- AC-18: Empty sections display a meaningful placeholder (e.g., "No active work").
- AC-19: Loading state displayed while fetching.
- AC-20: Error state displayed when API fails.
- AC-21: Component is routed at `/projects/{slug}/overview`.
- AC-22: Responsive layout (works on desktop and tablet).
- AC-23: No LLM call is made at any point in the projection or display chain.

## Technical Context

### Existing entities and their relevant fields

| Entity | Key Fields | Relevant Statuses |
|---|---|---|
| `Project` | name, slug, description, status | ACTIVE, PAUSED, ARCHIVED |
| `EngineeringStory` | storyNumber, title, status, baseCommit, targetCommit, completedAt | REGISTERED, IN_PROGRESS, COMPLETED |
| `Challenge` | title, description, impact, status, resolution | OPEN, RESOLVED, ACCEPTED, MITIGATED |
| `ValidatableProposal` | type, status, confidence, payload | PROPOSED, ACCEPTED, REJECTED |
| `Decision` | title, context, choice, rationale, consequences | — |
| `Milestone` | name, description, status | PLANNED, IN_PROGRESS, COMPLETED, CANCELLED |
| `KnowledgeEvent` | title, description, type | — |
| `ProjectCommit` | hash, message, committedAt | — |

### Existing repositories

All required repositories already exist:
- `ProjectRepository`
- `EngineeringStoryRepository` (findByProjectId, findByProjectIdAndStatus)
- `ChallengeRepository` (findByProjectIdAndStatus)
- `ValidatableProposalRepository` (findByProjectIdAndStatus)
- `DecisionRepository` (findByProjectId)
- `MilestoneRepository` (findByProjectIdAndStatus)
- `KnowledgeEventRepository` (findByProjectId)
- `ProjectCommitRepository` (findByProjectIdOrderByCommittedAtDesc)

### Performance constraints

- No N+1 queries: each section uses a single repository query.
- No caching: projection is recalculated on demand.
- Target < 100ms response time.
- Max items per section: stories (5 recent), decisions (5 recent), commits (10 recent), others (all matching status).

## Out of Scope

- Knowledge Graph API
- Graph database
- Caching layer
- Real-time updates / WebSocket
- LLM interpretation
- Edit/write operations on the projection
- History of projections
- Notification system
- Dashboard analytics
- Multi-project aggregation

## Dependencies

- Existing DevLog backend (Spring Boot, JPA, Flyway)
- Existing Angular frontend
- Existing repositories and entities (no new persistence)
- No new NPM packages or Maven dependencies expected
