# Story 0201 — Lexical Improvement: PostgreSQL Full-Text Search

## Status

**DESIGN APPROVED** — Independent of Stories 0200/0202/0203. Can ship first.
Implementation authorization required before any code is written.

## Problem

The existing `DeterministicEvidenceRanker` tokenizes at the Java level using
a custom term-frequency model. PostgreSQL native full-text search
(tsvector/tsquery) provides stem matching, language-aware ranking, and
sub-millisecond GIN-indexed queries that the in-process term model cannot
replicate — without requiring any new infrastructure.

This is the lowest-risk improvement in the hybrid retrieval stack: it
improves lexical recall on Insights, Engineering Stories, and Decisions with
no embedding cost, no pgvector dependency, and no change to trust or ranking
semantics.

## Goal

Add PostgreSQL full-text search capability to the three knowledge entity
tables that are currently lexically under-served in the context pipeline:
Insights, Engineering Stories, and Knowledge Events (Decisions).

## Governing Artifacts

- ADR-063 — Engineering Context Retrieval and Composition Architecture
- ADR-080 — Hybrid Retrieval Architecture (accepted, Decision D1/D2)
- Story 0177 — Evaluate Hybrid Retrieval for Story Context

## Architectural Constraints

1. Trust tier assignment is unchanged — full-text matches carry the same
   `TrustTier` as the entities they come from.
2. Project isolation: all full-text queries are scoped by `project_id`.
3. The existing `DeterministicEvidenceRanker` criteria weights are
   unchanged.
4. The new collector is opt-in via configuration; existing collectors
   remain active and unchanged.
5. No change to MCP, REST, or AI Engine contracts.

## In Scope

**Database migrations:**
- `V84__add_fulltext_insights.sql`: add `tsvector` generated column
  `search_vector` on `insights` (`to_tsvector('english', title || ' ' ||
  coalesce(content, ''))`); GIN index.
- `V85__add_fulltext_stories.sql`: add `search_vector` on
  `engineering_stories` (`title || ' ' || coalesce(objective, '')`); GIN
  index.
- `V86__add_fulltext_knowledge_events.sql`: add `search_vector` on
  `knowledge_events` for Decision rows; GIN index.

**Repository queries:**
- `InsightRepository.findByProjectIdAndFullText(UUID projectId, String query,
  Pageable pageable)` — `WHERE project_id = :projectId AND search_vector @@
  plainto_tsquery('english', :query) ORDER BY ts_rank(search_vector,
  plainto_tsquery('english', :query)) DESC`.
- Equivalent queries on `EngineeringStoryRepository` and
  `KnowledgeEventRepository`.

**Collector:**
- `LexicalKnowledgeSearchCollector` implementing `RepositoryContextCollector`
  (disabled by default via `devlog.retrieval.lexical-fulltext.enabled`).
- Accepts the `ContextRequest.intent()` objective and `UserGuidance`
  priorities as the full-text query string.
- Returns `RepositoryEvidence` items with the same kind/layer/provenance
  mapping as `ProjectKnowledgeContextCollector`.
- Bounded by `devlog.retrieval.lexical-fulltext.max-results` (default 10).

**Tests:**
- Full-text match on an Insight with a stemmed query (`"architectur"` →
  matches `"architecture"`).
- Project isolation: results from project A not visible to project B.
- Empty query handling.
- `LexicalKnowledgeSearchCollector` disabled: returns empty list without
  querying the DB.
- Existing `DeterministicKnowledgeContextCollector` behavior unchanged.

## Out of Scope

- Embeddings, pgvector, vector queries.
- Full-text on Facts, Commits, Diffs (high volume, deferred).
- Changes to `DeterministicEvidenceRanker` scoring weights.
- Changes to MCP or REST contracts.

## Acceptance Criteria

- [ ] Three migration files applied cleanly in both dev and CI.
- [ ] `InsightRepository`, `EngineeringStoryRepository`,
  `KnowledgeEventRepository` new methods pass integration tests.
- [ ] `LexicalKnowledgeSearchCollector` returns Insight/Story/Decision
  candidates for a query that the term model scores at zero.
- [ ] Project isolation verified in integration tests.
- [ ] `devlog.retrieval.lexical-fulltext.enabled=false` returns empty list.
- [ ] No existing tests broken.
- [ ] `git diff --stat` shows only: three migration files, three repository
  changes, one new collector, and tests.
