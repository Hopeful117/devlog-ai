# Story 0055 — Detect Trusted Knowledge Duplicate Debt Through Maintenance Findings — Implementation Plan

## Overview

Implement Story `0055` as the **trusted-knowledge duplicate-debt evaluation
slice** on top of the `contextmaintenance` foundation and the `0054`
maintenance-evaluation seam.

The goal is to produce explicit maintenance findings for:

* exact duplicate trusted knowledge;
* one bounded class of near-duplicate overlap;
* richer-successor or review-required overlap that should remain human-reviewed
  rather than collapsed automatically.

This Story should stay intentionally narrow.

It should not add:

* trusted-knowledge deletion or merge behavior;
* remediation workflows;
* broad semantic search infrastructure;
* contradiction or supersession lifecycle changes.

## Final Implementation Strategy

The preferred implementation is:

1. reuse `TrustedKnowledgeDuplicateAuditService` as the authoritative detector
   for duplicate and near-duplicate clusters;
2. extend `MaintenanceFindingIssueType` minimally to represent duplicate-debt
   findings clearly;
3. map duplicate-audit clusters to one maintenance finding per cluster rather
   than one finding per raw insight;
4. integrate duplicate-debt evaluation into the existing
   `MaintenanceEvaluationService`;
5. preserve idempotency by suppressing repeated equivalent open findings for the
   same cluster;
6. keep richer-successor and ambiguous overlap reviewable and non-destructive;
7. add focused tests and documentation aligned with ADR-051 and Story `0040`.

## Step 1 — Reuse the existing duplicate-audit service as detector authority

Targets:

* `TrustedKnowledgeDuplicateAuditService`
* duplicate audit DTOs under `insight/dto/response`
* `contextmaintenance` evaluation orchestration

Goals:

* avoid duplicate matching logic drift;
* keep the Story deterministic and bounded;
* leverage existing tested duplicate clustering behavior.

Implementation direction:

Use `TrustedKnowledgeDuplicateAuditService.audit(projectId)` as the only
duplicate/near-duplicate detection source for this Story.

Important rule:

* `contextmaintenance` should consume duplicate clusters;
* it should not recreate title-token heuristics, fingerprinting, or cluster
  classification inside a second service.

Rationale:

The repository already has a deterministic audit implementation with clear
categories and recommendations. Story `0055` should adapt that output into
maintenance findings, not fork it.

## Step 2 — Extend maintenance issue typing minimally for duplicate debt

Targets:

* `MaintenanceFindingIssueType`
* any related tests or serialization contracts

Goals:

* represent duplicate debt explicitly in the maintenance domain;
* distinguish exact duplicate debt from bounded near-duplicate review cases;
* avoid over-modeling future duplicate workflows prematurely.

Implementation direction:

Add a minimal set of issue types sufficient for this Story, likely along these
lines:

* exact trusted-knowledge duplicate debt;
* likely semantic duplicate debt;
* review-required trusted-knowledge overlap or likely richer-successor overlap.

Preferred design:

* keep the number of new issue types small;
* mirror the bounded distinctions already emitted by the audit service rather
  than inventing a richer taxonomy than the detector supports.

Rationale:

The current maintenance issue types are freshness-only, so Story `0055` needs a
small extension to keep duplicate-debt findings interpretable.

## Step 3 — Map duplicate clusters to maintenance findings

Targets:

* `MaintenanceEvaluationServiceImpl`
* finding summary/details generation helpers

Goals:

* satisfy AC-1, AC-2, and AC-3;
* keep findings operational and reviewable;
* avoid flooding the system with pairwise duplicate noise.

Implementation direction:

Create one maintenance finding per meaningful duplicate cluster.

Each finding should be derived from:

* `clusterKey`
* `category`
* `recommendation`
* `rationale`
* cluster members and their metadata

Recommended mapping shape:

* `EXACT_DUPLICATE` → exact duplicate maintenance finding;
* `LIKELY_SEMANTIC_DUPLICATE` → likely semantic duplicate maintenance finding;
* `LIKELY_RICHER_SUCCESSOR` and `REVIEW_REQUIRED` → review-oriented overlap
  finding that preserves the distinction from safe duplicate collapse.

Important rule:

* do not create one finding per member pair;
* do not create findings for empty or singleton clusters.

Rationale:

The audit service already reasons in cluster form, which better matches how
duplicate debt appears in trusted knowledge and keeps the maintenance surface
compact.

## Step 4 — Keep richer-successor and ambiguous overlap explicitly reviewable

Targets:

* finding severity / suggested action mapping
* summary/details wording

Goals:

* satisfy AC-3 and AC-4;
* preserve ADR-051’s distinction between duplicate debt and legitimate
  enrichment;
* avoid presenting richer-successor cases as silent cleanup candidates.

Implementation direction:

For `LIKELY_RICHER_SUCCESSOR` and `REVIEW_REQUIRED` clusters:

* use `humanReviewRequired = true`;
* prefer suggested actions such as `REVIEW` or `INVESTIGATE`;
* explain why the cluster is reviewable rather than automatically actionable.

Recommended wording behavior:

* exact duplicate findings may state that members share the same normalized
  trusted fingerprint;
* semantic duplicate findings may state that members appear semantically close
  under bounded clustering rules;
* richer-successor findings should emphasize that one record appears richer in
  provenance or detail, but no trusted knowledge is mutated by this Story.

Rationale:

This Story surfaces maintenance debt. It does not authorize destructive
remediation.

## Step 5 — Extend the existing maintenance evaluation flow instead of adding a second trigger

Targets:

* `MaintenanceEvaluationService`
* existing
  `POST /api/v1/projects/{projectId}/maintenance-findings/evaluations` route

Goals:

* reuse the `0054` evaluation seam;
* keep maintenance production unified across surfaces;
* avoid parallel orchestration paths.

Implementation direction:

Add duplicate-debt evaluation to the existing project-scoped maintenance
evaluation flow.

Preferred behavior:

* one evaluation run can produce freshness-related findings and duplicate-debt
  findings together when both conditions exist;
* the response can continue to return created/skipped counts plus created
  findings without introducing a second endpoint.

Rationale:

`contextmaintenance` already has the right orchestration seam after Story
`0054`. Reusing it keeps the capability coherent.

## Step 6 — Preserve idempotency for stable duplicate clusters

Targets:

* duplicate-open-finding suppression logic
* stable cluster-based summary/detail generation

Goals:

* avoid repeated identical findings for the same duplicate cluster;
* keep the cockpit signal clean across repeated evaluations;
* make future review workflows easier to reason about.

Implementation direction:

Treat cluster identity as the deduplication anchor.

Preferred first-slice behavior:

* use the cluster’s stable `clusterKey` plus issue type and surface semantics
  when deciding equivalence;
* skip creating a new finding when an equivalent open finding already exists for
  that cluster;
* allow a new finding only after resolution or dismissal if the cluster
  reappears.

Rationale:

Duplicate debt is naturally cluster-scoped. Idempotency should follow the same
shape.

## Step 7 — Generate actionable finding details from cluster members

Targets:

* finding detail builder logic
* potential helper methods in `contextmaintenance`

Goals:

* satisfy AC-4;
* make findings useful without forcing readers to call the duplicate-audit
  endpoint immediately;
* preserve traceability to trusted records.

Implementation direction:

Each duplicate-debt finding should include details such as:

* duplicate cluster category;
* duplicate recommendation;
* member count;
* member insight ids;
* member titles;
* the detector rationale from the audit cluster.

Important rule:

* keep details bounded and deterministic;
* do not dump entire trusted-insight payloads into the maintenance finding.

Rationale:

Maintenance findings should explain enough to drive review while remaining
operational records, not a second full duplicate-audit payload.

## Step 8 — Add focused tests for exact duplicates, bounded near-duplicates, and non-match cases

Targets:

* `MaintenanceEvaluationServiceTest`
* any new duplicate-debt-specific service tests if the implementation extracts
  helpers
* controller tests only if the evaluation response contract changes materially

Goals:

* satisfy AC-5;
* prove the mapping from duplicate-audit clusters to maintenance findings;
* preserve conservative no-noise behavior.

Implementation direction:

Add tests covering at least:

* exact duplicate clusters create duplicate-debt findings;
* one bounded near-duplicate class such as `LIKELY_SEMANTIC_DUPLICATE` creates
  a reviewable finding;
* richer-successor or review-required clusters are classified as reviewable
  rather than destructive;
* empty audit results create no duplicate-debt findings;
* repeated evaluation skips equivalent open findings for the same cluster.

Preferred style:

* mock the duplicate-audit service output in maintenance evaluation tests rather
  than rebuilding all clustering fixtures again unless necessary.

Rationale:

The audit service already owns duplicate detection tests. Story `0055` mainly
needs mapping and orchestration coverage.

## Step 9 — Update canonical documentation for duplicate-debt maintenance policy

Targets:

* relevant canonical docs such as `README.md`, `docs/knowledge-model.md`, or a
  duplicate-policy-adjacent doc already serving as repository authority

Goals:

* satisfy AC-6;
* record how duplicate maintenance findings relate to ADR-051 and Story `0040`;
* make the first duplicate-debt slice and its limits explicit.

Implementation direction:

Document:

* that duplicate-debt maintenance findings are produced from the trusted
  duplicate audit;
* that exact duplicates and bounded near-duplicate clusters can now surface as
  maintenance findings;
* that no trusted knowledge is deleted, merged, or rewritten by this Story;
* that richer-successor and ambiguous overlap remain review-oriented.

Rationale:

The repository already documents maintenance findings and trusted knowledge
separately. Story `0055` should connect them without blurring their roles.

## Expected Implementation Shape

Repository evidence supports an implementation centered on:

* duplicate-cluster detection in `insight`;
* maintenance-finding production in `contextmaintenance`;
* minimal issue-type extension;
* cluster-scoped reviewable findings;
* documentation and tests that keep the policy bounded.

## Validation Plan

Before requesting Code Review approval, validate with:

* targeted backend tests for duplicate-debt evaluation mapping;
* the relevant existing duplicate-audit tests if any touched code requires
  confidence beyond mocks;
* WebMvc verification only if the evaluation endpoint contract changes
  materially;
* manual endpoint verification if needed to confirm combined freshness and
  duplicate-debt findings in one evaluation flow.

## Deferred Work

Explicitly defer to later Stories:

* delete/merge/archive remediation workflows;
* trusted-knowledge mutation tooling;
* contradiction or supersession lifecycle expansion;
* embeddings-based or open-ended similarity infrastructure;
* bulk human-reviewed remediation actions.
