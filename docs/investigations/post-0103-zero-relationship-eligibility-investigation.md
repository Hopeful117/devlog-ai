# Investigation: Zero Policy-A Relationship Eligibility After Story 0103

**Date**: 2026-08-30
**Scope**: Why do 44 canonical KnowledgeRelations produce zero Policy-A-eligible RelationshipHighlights?
**Baseline**: `main` at `24d5bb2` (post-Story-0103 merge)
**Status**: Complete — root cause identified, no code change required

---

## Executive Summary

All 44 canonical `KnowledgeRelationSnapshot` records in the test project are **duplicate-resolution `RESOLVES` links** between insights. Every single source endpoint is a `SUPERSEDED` insight, and every target endpoint — while mostly active — ranks **outside the 10-insight selection budget** (ranks 15–21). Because Policy A requires *both* endpoints to be independently selected/projected, and zero selected insight IDs intersect with the relation endpoints, the result is 44 → 0 → 0 → 0 → 0 across all three V1 objectives.

**This is expected behavior, not a defect.** The current selection pipeline correctly preserves all 44 relations in `SelectedKnowledge.knowledgeRelations`, and Policy A correctly rejects them because neither endpoint survives the budget + recency filter.

---

## Canonical Relationship Inventory

| Property | Value |
|---|---|
| Total relations | 44 |
| Source entity type | 44× `INSIGHT` |
| Target entity type | 44× `INSIGHT` |
| Relation type | 44× `RESOLVES` |
| Structurally Policy-A capable | 44 (both endpoints are INSIGHT) |

**All 44 relations are identical in structure**: `INSIGHT → INSIGHT`, `RESOLVES`, "Insight superseded during duplicate resolution".

---

## Endpoint Status Distribution

| Side | Status | Count |
|---|---|---|
| Source | SUPERSEDED | 44 |
| Target | ACTIVE | 9 |
| Target | SUPERSEDED | 35 |

The source of every relation is a superseded insight. The targets are mostly superseded (35/44), with 9 being active insights.

---

## Selected Insight Budget vs. Relationship Targets

The selection pipeline selects the **10 most recent ACTIVE insights** (ordered by `created_at DESC, id ASC`):

| Rank | ID | Title | Referenced by relationship? |
|---|---|---|---|
| 1 | `75227cb9` | Project Documentation Structure | No |
| 2 | `f25f5408` | Automated and Integration Testing Present | No |
| 3 | `030c7f0e` | Use of Architecture Decision Records (ADR) for Documentation | No |
| 4 | `0c4f1e1d` | Project Containerization with Docker and Docker Compose | No |
| 5 | `3e66fd4f` | Spring Boot REST API Application | No |
| 6 | `c5e4658e` | Multi-module Build System Using Maven | No |
| 7 | `ce6912d5` | Overview of the 'devlog-ai' Project | No |
| 8 | `a6feb7e9` | Use of Architecture Decision Records (ADR) for Documentation | No |
| 9 | `00b1b41d` | Automated and Integration Testing Present | No |
| 10 | `48b8cef8` | REST API Exposure via Spring Boot Controllers | No |

**None of the 10 selected insights appear as any endpoint in any of the 44 relations.**

---

## Relationship Endpoint Intersection with Selection

The 44 relations reference **31 distinct insight IDs**. Of these:

- **7 are ACTIVE** but ranked **15–21** (beyond the 10-budget):
  - `aef822a0` (rank 15) — Project Documentation Structure
  - `f944853b` (rank 16) — REST API Exposure via Controllers
  - `d5b516ae` (rank 17) — Use of Architecture Decision Records (ADR)
  - `aff2cff1` (rank 18) — Project Containerization with Docker and Docker Compose
  - `4a91334f` (rank 19) — Automated Testing and Integration Tests
  - `ffb61668` (rank 20) — Use of Spring Boot Framework
  - `06d34458` (rank 21) — Overview of the 'devlog-ai' Project

- **24 are SUPERSEDED** — excluded by the `status = ACTIVE` filter entirely.

**Zero selected insight IDs appear as any relation endpoint.** The rejection reason for all 44 relations is `BOTH_NOT_SELECTED`.

---

## Policy A Evaluation Trace

For each of the 44 relations:

1. **Structural eligibility**: ✅ Both endpoints are `INSIGHT` → `INSIGHT` (eligible pair type)
2. **Source check**: `selectedInsightIds.contains(sourceEntityId)` → **false** (source is SUPERSEDED)
3. **Target check**: `selectedInsightIds.contains(targetEntityId)` → **false** (target ranks 15–21, beyond budget)
4. **Result**: `isPolicyAEligible = false && false = false`
5. **Projection**: Not emitted as `PromptRelationshipHighlight`

---

## Engineering Events — Confirmed Absent

All three analysis contexts report `engineeringEventCount: 0` and `validatedEngineeringEvents: []`. No cross-type relationships (`INSIGHT → ENGINEERING_EVENT`, etc.) exist, confirming the V2 story (AI-proposed engineering events) is not yet active in the benchmark project.

---

## Root Cause Analysis

### Why zero eligibility is correct

1. **Selection budget is 10 insights max** — the pipeline selects the 10 most recent ACTIVE insights
2. **The 44 RESOLVES relations point to insights ranked 15–21** — 7 of the 9 active targets are budget-excluded
3. **The remaining 35 targets are SUPERSEDED** — excluded by the `status = ACTIVE` filter
4. **All 44 sources are SUPERSEDED** — excluded by the `status = ACTIVE` filter
5. **No engineering events exist** — cross-type eligibility is impossible

### What this means for ADR-064

The **Relationship Preservation** slice (ADR-064 Slice 1) is functioning correctly:
- All 44 canonical relations survive selection into `SelectedKnowledge.knowledgeRelations`
- The `knowledgeRelationPreservation` strategy tag is present in `SelectedKnowledge.metadata.strategies`
- Policy A correctly filters at projection time based on actual selection

The zero-eligibility result is a **data characteristic of the benchmark project**, not a code defect. The duplicate-resolution RESOLVES links create a "superseded → active" topology where the active targets consistently fall outside the 10-insight selection budget.

---

## Recommended Next Steps

### Immediate (no code change)
- **Accept the benchmark result as expected** — zero eligible highlights is the correct output for this dataset
- **Document the finding** — this investigation serves as evidence that the preservation layer works

### ADR-064 Slice 2 (Benchmark)
- Establish a **regression baseline** with these numbers: `canonical=44, eligible=0, projected=0, COMMIT_DIFF=12`
- The benchmark should verify that the canonical count remains ≥ 44 and that no false positives appear

### ADR-064 Slice 3+ (Future)
- **Relationship-aware selection** could increase eligibility: if the selection budget considered relation endpoints, the 7 active-but-rank-15+ insights could be promoted, yielding up to 9 eligible highlights (source still SUPERSEDed → 0 source-selected)
- **Cross-type relations** (when V2 engineering events exist) would be the first true test of Policy A's dual-endpoint requirement
- **`ENGINEERING_EVENT → INSIGHT`** relations are the most likely to produce eligible highlights once V2 is active, since events have their own 10-event budget separate from the insight budget

---

## Appendix: Database Queries Used

```sql
-- Canonical inventory
SELECT source_entity_type, target_entity_type, relation_type, count(*)
FROM knowledge_relations WHERE project_id = '...' GROUP BY 1,2,3;

-- Endpoint status distribution
SELECT side, status, count(*) FROM (
  SELECT 'source', i.status FROM knowledge_relations kr JOIN insights i ON i.id = kr.source_entity_id
  UNION ALL
  SELECT 'target', i.status FROM knowledge_relations kr JOIN insights i ON i.id = kr.target_entity_id
) GROUP BY 1,2;

-- Active insight ranking vs. relation references
SELECT row_number() OVER (ORDER BY created_at DESC, id ASC) as active_rank,
       id, title, created_at
FROM insights WHERE project_id='...' AND status='ACTIVE';

-- Selected vs. endpoint intersection
WITH selected AS (SELECT id FROM insights WHERE ... LIMIT 10)
SELECT count(*) FILTER (WHERE source_entity_id IN (SELECT id FROM selected)) as source_selected,
       count(*) FILTER (WHERE target_entity_id IN (SELECT id FROM selected)) as target_selected
FROM knowledge_relations WHERE ...;
```
