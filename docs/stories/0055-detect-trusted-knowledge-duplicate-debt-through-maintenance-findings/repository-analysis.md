# Story 0055 — Detect Trusted Knowledge Duplicate Debt Through Maintenance Findings — Repository Analysis

## Purpose

Analyze how DevLog should extend context maintenance to trusted-knowledge
duplicate debt without:

* redefining duplicate semantics already established by ADR-051;
* rebuilding duplicate detection logic that already exists in the `insight`
  domain;
* mutating or collapsing trusted knowledge records;
* weakening the human-review boundary for ambiguous overlap cases.

This Story is a **maintenance finding producer** slice for duplicate debt.

It is not:

* a remediation workflow Story;
* a trusted-knowledge mutation Story;
* an embeddings or semantic-search infrastructure Story;
* a contradiction or supersession lifecycle Story.

## Repository Context

### Current Git state

Repository branch at analysis time:

* `main`

Observed local nuance:

* Story `0054` is merged into local `main`;
* the local feature branch for `0054` was cleaned up safely after PR merge;
* story directories `0055` through `0059` remain local untracked planning
  inputs;
* no implementation artifact exists yet for Story `0055` besides `story.md`.

Impact:

* the repository is ready for the next maintenance producer slice;
* Story `0055` can plan directly against the existing `contextmaintenance`
  domain and the merged `0054` evaluation pattern.

### DevLog lifecycle

Story registration succeeded:

* DevLog story id: `44c61b7a-0a4d-4bde-be53-79a475e1b073`

DevLog engineering-story context preparation fell back cleanly:

* `DEVLOG_CONTEXT_ERROR: DevLog request timed out. Repository Analysis continues without DevLog.`

Impact:

* this analysis proceeds from direct repository inspection;
* DevLog context unavailability is not a blocker for Story `0055`.

### Vault context

Vault was not consulted for this analysis.

Reason:

* the Story is tightly constrained by repository-local ADRs, an existing audit
  service, and the recent maintenance-finding model;
* no transverse vault knowledge was needed to identify the correct seams.

## Story Understanding

Story `0055` asks DevLog to turn trusted-knowledge duplicate debt into explicit,
reviewable maintenance findings.

The Story does not ask DevLog to invent duplicate policy or to remediate
duplicates automatically.

The requested value is:

* generate findings for exact duplicate trusted knowledge;
* generate findings for at least one bounded class of near-duplicate overlap;
* distinguish likely duplicate debt from likely enrichment/review cases;
* keep the results non-destructive and reviewable.

This makes the Story an adapter between:

* existing duplicate-audit semantics in the `insight` domain;
* the cross-surface operational findings model in `contextmaintenance`.

## Business Ownership

The capability still belongs primarily to Java Core, but it spans two existing
backend subdomains:

* `insight` owns trusted-knowledge duplicate detection semantics;
* `contextmaintenance` owns project-scoped maintenance findings and evaluation
  flow.

The AI Engine is not the natural owner because:

* the repository already has deterministic duplicate-audit logic;
* ADR-051 explicitly prefers structured, bounded comparison signals;
* Story `0055` is about surfacing duplicate debt safely, not free-form semantic
  reasoning.

## Relevant Existing Architecture

### `docs/decisions/ADR-051.md`

This ADR is the policy baseline for the Story.

It establishes that:

* proposal history and trusted knowledge are different policy domains;
* exact trusted-knowledge duplicates are not acceptable steady-state behavior;
* semantic near-duplicates are undesirable and should be bounded carefully;
* legitimate enrichment must not be misclassified as duplication;
* human review must not be the primary duplicate-control mechanism, but remains
  necessary for ambiguity;
* remediation of existing duplicate stock is explicit debt, not target
  behavior.

Important implication:

Story `0055` should map existing duplicate-audit signals into maintenance
findings without changing the underlying trusted-knowledge lifecycle.

### Story `0040` duplicate-audit work

Relevant artifact:

* `docs/stories/0040-audit-and-remediate-existing-trusted-knowledge-duplicates/repository-analysis.md`

Important repository conclusion from that Story:

* duplicate trusted knowledge already existed as remediation debt;
* the right first technical move was deterministic clustering and reviewable
  categorization rather than destructive cleanup.

Important implication:

Story `0055` should reuse that audit-oriented interpretation rather than
starting a second incompatible duplicate taxonomy.

### `TrustedKnowledgeDuplicateAuditService`

Relevant file:

* `backend/src/main/java/com/hopeful117/devlogai/insight/service/TrustedKnowledgeDuplicateAuditService.java`

Current behavior:

* scans trusted insights project-wide;
* forms exact-duplicate groups from normalized fingerprints;
* forms topic clusters using bounded title-token overlap heuristics;
* classifies clusters into:
  * `EXACT_DUPLICATE`
  * `LIKELY_SEMANTIC_DUPLICATE`
  * `LIKELY_RICHER_SUCCESSOR`
  * `REVIEW_REQUIRED`
* emits recommendations such as:
  * `KEEP_NEWEST_AS_CANONICAL`
  * `KEEP_RICHEST_AS_CANONICAL`
  * `REVIEW_MANUALLY`

Important implication:

the repository already contains the core duplicate/near-duplicate detector
Story `0055` needs.

The likely implementation seam is to reuse this service as the producer input
for `contextmaintenance`, not to reimplement matching rules there.

### Existing duplicate-audit API

Relevant files:

* `backend/src/main/java/com/hopeful117/devlogai/insight/controller/InsightController.java`
* `backend/src/main/java/com/hopeful117/devlogai/insight/service/InsightServiceImpl.java`

Current behavior:

* DevLog already exposes `GET /api/v1/insights/project/{projectId}/duplicate-audit`
  as a read path for duplicate clusters.

Important implication:

Story `0055` does not need a new read-oriented duplicate detector API. The
read-side audit already exists.

What is missing is:

* operational maintenance-finding production;
* finding classification aligned with duplicate-debt semantics;
* bounded evaluation trigger or reuse of the new `0054` evaluation pattern.

### `contextmaintenance/*`

Relevant files:

* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceEvaluationServiceImpl.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceFindingService.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/entity/MaintenanceFindingIssueType.java`

Current behavior after Story `0054`:

* maintenance findings can be created and listed;
* explicit evaluation is triggered through
  `POST /api/v1/projects/{projectId}/maintenance-findings/evaluations`;
* the current issue types are freshness-oriented:
  * `STALE_PROJECT_UNDERSTANDING`
  * `PROJECTION_REFRESH_GAP`
  * `MISSING_PROJECTION_REFRESH`

Important implication:

Story `0055` can likely extend the same evaluation architecture, but the
current issue-type model is too narrow for duplicate debt.

Some model extension will probably be required.

## Architectural Constraints

### Do not duplicate duplicate detection logic

The repository already has a deterministic duplicate-audit service with tests.

Therefore Story `0055` should avoid:

* creating a second duplicate matcher inside `contextmaintenance`;
* inventing a second cluster taxonomy;
* diverging from the bounded heuristics already accepted in `insight`.

Preferred direction:

reuse `TrustedKnowledgeDuplicateAuditService` output and map cluster categories
into maintenance findings.

### Do not mutate trusted knowledge

Both the Story text and ADR-051 explicitly forbid silent delete/merge behavior
here.

Therefore Story `0055` must not:

* delete insights;
* archive insights;
* rewrite insight content;
* auto-merge likely duplicates;
* imply that a maintenance finding is itself a remediation action.

Preferred direction:

create reviewable findings that point at candidate duplicate clusters and their
recommended action posture.

### Keep near-duplicate detection conservative

The Story requires at least one bounded near-duplicate slice, but warns against
noise.

Repository evidence suggests the conservative bounded choice already exists:

* `LIKELY_SEMANTIC_DUPLICATE`
* `LIKELY_RICHER_SUCCESSOR`
* `REVIEW_REQUIRED`

Important implication:

Story `0055` should likely treat these existing categories differently in the
finding model rather than collapsing them into a single “duplicate” bucket.

## Likely Design Direction

### 1. Extend the maintenance-finding issue taxonomy minimally

The existing issue types are freshness-specific.

Repository evidence suggests Story `0055` will need one or more additional
issue types, likely along the lines of:

* exact trusted-knowledge duplicate debt;
* likely semantic duplicate debt;
* review-required or likely enrichment overlap.

The extension should stay minimal and aligned with actual cluster categories
already emitted by the audit service.

### 2. Reuse the `0054` maintenance evaluation seam

Story `0054` already introduced:

* a maintenance evaluation service;
* an explicit project-scoped evaluation endpoint;
* duplicate-open-finding suppression patterns.

Important implication:

Story `0055` should probably extend the same evaluation flow to add
duplicate-debt producers rather than adding a disconnected second mechanism.

### 3. Map duplicate cluster categories to findings, not one insight per finding

The audit service produces **clusters**, not isolated pairwise flags.

That is likely the right maintenance unit because:

* duplicate debt is often many-to-one or many-to-many;
* recommendations are cluster-scoped;
* cluster output already includes member insight IDs and metadata.

Preferred direction:

create one maintenance finding per meaningful duplicate cluster, with details
listing:

* cluster category;
* recommendation;
* involved insight IDs / titles;
* why the overlap was classified as exact duplicate, likely semantic duplicate,
  richer successor, or review-required.

### 4. Keep “likely enrichment” reviewable rather than accusatory

Story `0055` explicitly wants to distinguish likely duplicate debt from likely
enrichment cases.

Repository evidence suggests `LIKELY_RICHER_SUCCESSOR` is the clearest bounded
signal for this distinction.

Important implication:

the finding wording and classification should avoid presenting richer-successor
cases as if they were safe destructive cleanup candidates.

These should likely be marked as:

* operational duplicate debt with human review;
* or a distinct review-oriented issue type if the model extension allows it.

## Testing Implications

The repository already has deterministic tests for the duplicate-audit service:

* exact duplicates;
* likely richer successor clusters;
* ambiguous review-required clusters;
* empty and isolated-project behavior.

Story `0055` should likely add:

* maintenance evaluation service tests proving exact-duplicate clusters become
  findings;
* tests proving at least one bounded near-duplicate class becomes findings;
* conservative non-match tests where no cluster should create a finding;
* controller tests only if the evaluation endpoint contract changes.

The Story does not need to retest the full clustering algorithm from scratch if
it reuses the already-tested audit service.

## Risks And Open Questions

### Risk: taxonomy drift between `insight` and `contextmaintenance`

If Story `0055` invents different labels than the audit service, maintenance
findings will be harder to interpret and maintain.

Preferred mitigation:

keep the mapping explicit and small.

### Risk: duplicate finding churn on stable clusters

As with Story `0054`, repeated evaluations could flood the maintenance surface
if cluster-based findings are not deduplicated against existing open findings.

Preferred mitigation:

reuse the open-finding suppression pattern, likely with a stable cluster-based
summary or rule identity.

### Open question: issue-type granularity

The current maintenance issue taxonomy may be too coarse to cleanly represent:

* exact duplicates;
* semantic duplicates;
* likely enrichment / richer-successor review.

Implementation planning must decide whether to:

* add one broad duplicate-debt issue type with detail-level classification;
* or add a small set of issue types that mirror the bounded cluster
  distinctions.

## Recommended Planning Direction

Repository evidence supports the following plan for Story `0055`:

* reuse `TrustedKnowledgeDuplicateAuditService` as the authoritative detector;
* extend `contextmaintenance` issue typing minimally for duplicate debt;
* produce one maintenance finding per duplicate cluster, not per raw insight;
* preserve ambiguous or richer-successor cases as reviewable, non-destructive
  findings;
* reuse the `0054` maintenance evaluation seam and duplicate-suppression
  pattern instead of creating a parallel workflow.

That direction aligns with ADR-051, ADR-053, Story `0040`, and the current
repository architecture.
