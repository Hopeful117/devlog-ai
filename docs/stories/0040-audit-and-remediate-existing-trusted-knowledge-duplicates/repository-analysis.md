# Story 0040 — Audit And Remediate Existing Trusted Knowledge Duplicates — Repository Analysis

## Purpose

Analyze how DevLog can safely identify and remediate **existing** duplicate
trusted knowledge already present in the database, without:

* redefining duplicate policy already established by ADR-051;
* weakening ADR-006 lifecycle history;
* silently mutating or deleting historically meaningful records;
* conflating prevention of future duplicates with cleanup of existing debt.

This Story addresses the backlog of duplicate trusted knowledge that predates
the safeguards added in Story 0039.

## Relevant Components

### `ADR-051`

Provides the policy baseline:

* duplicate trusted knowledge is not acceptable target-state behavior;
* existing duplicates are remediation debt;
* remediation must preserve traceability;
* no silent destructive cleanup should occur.

**Implication**:

Story 0040 does not need to decide *whether* duplicate cleanup matters.

It needs to decide:

* how to find candidates;
* how to classify them;
* how to remediate them safely.

### `Story 0039`

Now prevents new exact duplicate trusted insights from being accepted through
the normal validation flow.

**Implication**:

The trusted layer is now better protected going forward, so Story 0040 can
focus on historical debt instead of racing against ongoing duplicate creation.

### `InsightRepository`

Current role:

* project-scoped retrieval of trusted insights;
* ordering by recency and coarse type / severity filters.

Strength:

* enough repository surface already exists to scan current trusted knowledge
  deterministically.

Limitation:

* no built-in duplicate audit query model;
* no classification API;
* no persistence model for remediation decisions.

### `KnowledgeRelation`

Current role:

* generic project-scoped relationship model between trusted entities.

Strength:

* already provides traceable relation persistence;
* can potentially support remediation provenance later.

Limitation:

* current relation taxonomy contains:
  - `RESOLVES`
  - `CAUSED_BY`
  - `RELATES_TO`
  - `DERIVED_FROM`
  - `ADDRESSES`
  - `INFORMED_BY`
* there is no dedicated relation type for:
  - duplicate-of
  - superseded-by
  - archived-as-redundant

**Implication**:

Story 0040 should be cautious about using `KnowledgeRelation` for remediation
marking unless the chosen meaning is explicit and not misleading.

### `ProjectContextProviderImpl` / projections

Current role:

* expose trusted insights and knowledge relations to downstream consumers.

Implication:

* any remediation approach that mutates, archives, or marks trusted insights
  will eventually affect:
  - project context;
  - future knowledge selection;
  - timeline / state projections;
  - human interpretation.

That raises the bar for safe cleanup.

## Observed Current Data State

Direct inspection of current project insights for `devlog-ai` shows clear
duplicate-like stock already present in the trusted layer.

Representative examples include:

### Architecture cluster

Older insight:

* `REST Spring Boot Application Architecture`
* `sourceType = null`
* concise content

Newer insight:

* `RESTful Spring Boot Application Architecture`
* `sourceType = ARCHITECTURE_DESCRIPTION`
* richer rationale and provenance

These are not meaningfully separate steady-state trusted facts.

They are better interpreted as:

* historical earlier statement
* followed by a richer successor / refinement

### Docker / containerization cluster

Older insight:

* `Containerized Deployment Setup`

Newer insight:

* `Containerized Deployment Using Docker and Docker Compose`

These appear to express substantially the same architectural fact with richer
later phrasing and provenance.

### ADR cluster

Older insight:

* `Architecture Decision Records (ADR) Documentation`

Newer insight:

* `Use of Architecture Decision Records (ADR)`

Again, likely duplicate / successor debt rather than intentionally distinct
trusted knowledge.

### Testing cluster

Older insights include:

* `Automated Testing Structure`
* `Automated and Integration Testing Present`

Newer insight:

* `Automated and Integration Testing Infrastructure`

This cluster likely contains both:

* semantic duplicates;
* and possibly a richer later statement that should be retained as the primary
  trusted form.

## Important Current Limitation

The current `knowledge-relations` project endpoint returns no relations for the
project.

**Implication**:

Existing duplicate stock is currently unannotated:

* no relation explains that one insight supersedes or enriches another;
* no remediation metadata exists;
* downstream consumers see only flat trusted records.

That means cleanup cannot safely rely on pre-existing remediation traceability.

## Duplicate Candidate Categories For This Story

Based on the current data shape and ADR-051, Story 0040 should classify
candidates into at least four groups.

### 1. Exact duplicate

Trusted insights whose normalized trusted payload is materially identical.

These are the safest cleanup candidates.

### 2. Likely semantic duplicate

Trusted insights with different wording but the same practical meaning.

These require review or a carefully defined remediation action.

### 3. Likely enrichment / richer successor currently represented as duplicate

An earlier trusted insight appears to be replaced in practice by a later, more
complete statement with better provenance.

This is common in the currently observed data.

These are not just “bad duplicates”.

They are evidence that the system historically stored incremental refinement as
flat additional trusted records.

### 4. Ambiguous / human review required

Similar insights where the data alone cannot prove whether the records are:

* duplicates;
* historical truth variants;
* legitimate distinct scope statements.

## Recommended Direction

### 1. Start with a read-only audit artifact, not destructive cleanup

The first safe step is a deterministic scan that produces candidate groups and
recommended actions.

Preferred initial artifact:

* repository-local report or JSON export listing duplicate candidate clusters
  with:
  - project
  - insight IDs
  - titles
  - normalized signatures
  - proposed category
  - remediation recommendation

This allows review before any data mutation.

### 2. Prefer deterministic clustering over vague semantic search in V1

Given current repository state, the most pragmatic approach is:

* project-scoped scan of trusted insights;
* normalization on:
  - type;
  - `sourceType`;
  - title;
  - content;
  - rationale;
* deterministic grouping for exact duplicates;
* narrow heuristic grouping for obvious semantic duplicates / richer successors.

Do not introduce embeddings or broad semantic tooling in this Story.

### 3. Separate audit from mutation

The safest workflow shape is:

1. scan trusted knowledge
2. classify duplicate candidates
3. review candidate report
4. explicitly approve remediation action
5. apply traceable mutation only for approved candidates

This should remain true even if Story 0040 later implements a remediation API
or admin workflow.

### 4. Prefer archival / resolution semantics over hard deletion by default

Hard deletion is risky because it can erase:

* proposal linkage;
* validation provenance;
* evidence history;
* historical understanding of how the knowledge evolved.

Preferred default direction:

* mark, archive, or otherwise exclude redundant records from “active trusted
  view” before considering physical deletion.

If deletion is ever allowed, it should be:

* explicit;
* auditable;
* narrowly scoped;
* human-approved.

### 5. A remediation workflow likely needs a small explicit domain surface

Current model lacks a dedicated concept for duplicate remediation decisions.

Therefore Story 0040 may need either:

* a report-only first slice;
* or a minimal explicit remediation record / workflow if mutation is approved.

Repository analysis suggests not jumping straight to mass-write behavior unless
the review artifact alone proves insufficient.

## Recommended Implementation Boundary

The safest first slice for Story 0040 is likely:

* backend read-side audit service;
* deterministic duplicate candidate classifier;
* output artifact or endpoint for human review;
* no destructive mutation in the first implementation unless explicitly
  re-approved after the analysis and plan.

That would satisfy:

* AC-1
* AC-2
* most of AC-3
* AC-4 by design

and still leave room for a later mutation step if the resulting audit proves
useful and trustworthy.

## Test Impact

Likely needed tests:

* scan returns no candidates for empty trusted knowledge
* exact duplicate cluster detection
* richer successor / likely enrichment cluster detection
* project isolation
* deterministic ordering of clusters and members
* report generation does not mutate trusted knowledge

If mutation is later approved in-scope:

* explicit remediation action auditability
* no silent destructive cleanup
* preserved traceability after remediation

## Risks

### 1. Over-cleaning

If the Story treats all similar insights as duplicates, it may erase
historically meaningful distinctions.

### 2. Under-cleaning

If classification is too cautious, the audit may be too noisy or too weak to
be useful.

### 3. Mutating too early

If the Story jumps directly to destructive remediation, the repository may lose
traceability before the classification model is trusted.

### 4. Using the wrong traceability mechanism

If `KnowledgeRelation` is overloaded carelessly for remediation state, the
result may be semantically confusing rather than clarifying.

## Recommendation

Proceed with Story 0040 as a read-first remediation slice:

* scan the current trusted knowledge base;
* classify candidate duplicate clusters deterministically;
* produce a reviewable remediation artifact;
* defer destructive or archival mutation until the scan quality is validated or
  explicitly re-approved.
