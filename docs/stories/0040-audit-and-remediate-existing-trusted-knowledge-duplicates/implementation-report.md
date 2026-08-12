# Story 0040 — Audit And Remediate Existing Trusted Knowledge Duplicates — Implementation Report

## Status

Implemented

## Summary

Implemented a safe, read-only duplicate-audit slice for existing trusted
knowledge.

The Story does not mutate historical `Insight` rows. Instead, it exposes a
deterministic audit endpoint and a story-local JSON artifact that classify
duplicate candidates into reviewable clusters.

This keeps remediation explicit and traceable while giving the next Story a
concrete baseline to act on.

## Changes

### 1. Added a dedicated duplicate-audit response model

Added:

* `backend/src/main/java/com/hopeful117/devlogai/insight/dto/response/InsightDuplicateAuditResponse.java`
* `backend/src/main/java/com/hopeful117/devlogai/insight/dto/response/InsightDuplicateClusterCategory.java`
* `backend/src/main/java/com/hopeful117/devlogai/insight/dto/response/InsightDuplicateClusterResponse.java`
* `backend/src/main/java/com/hopeful117/devlogai/insight/dto/response/InsightDuplicateMemberResponse.java`
* `backend/src/main/java/com/hopeful117/devlogai/insight/dto/response/InsightDuplicateRecommendation.java`

Purpose:

* make duplicate-audit output structured and deterministic;
* separate audit semantics from trusted promotion semantics;
* provide a stable contract for later remediation work.

### 2. Added a deterministic trusted-knowledge duplicate audit service

Added:

* `backend/src/main/java/com/hopeful117/devlogai/insight/service/TrustedKnowledgeDuplicateAuditService.java`

Behavior:

* loads trusted insights for one project only;
* groups exact duplicates through a normalized fingerprint;
* groups likely near-duplicates through bounded topic-token heuristics;
* classifies clusters as:
  - `EXACT_DUPLICATE`
  - `LIKELY_RICHER_SUCCESSOR`
  - `LIKELY_SEMANTIC_DUPLICATE`
  - `REVIEW_REQUIRED`
* recommends either:
  - `KEEP_NEWEST_AS_CANONICAL`
  - `KEEP_RICHEST_AS_CANONICAL`
  - `REVIEW_MANUALLY`

This remains intentionally conservative. It identifies review candidates; it
does not delete, merge, or rewrite trusted knowledge.

### 3. Exposed the audit through the insight API

Updated:

* `backend/src/main/java/com/hopeful117/devlogai/insight/service/InsightService.java`
* `backend/src/main/java/com/hopeful117/devlogai/insight/service/InsightServiceImpl.java`
* `backend/src/main/java/com/hopeful117/devlogai/insight/controller/InsightController.java`

Added endpoint:

* `GET /api/v1/insights/project/{projectId}/duplicate-audit`

Outcome:

* duplicate stock can now be inspected without direct database access;
* the audit can be reused by later remediation workflows and story artifacts.

### 4. Added focused regression coverage

Added or updated:

* `backend/src/test/java/com/hopeful117/devlogai/insight/service/TrustedKnowledgeDuplicateAuditServiceTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/insight/service/InsightServiceTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/insight/controller/InsightControllerWebMvcTest.java`

Covered scenarios:

* empty project audit
* exact duplicate clustering
* likely richer-successor clustering
* ambiguous cluster fallback
* project isolation
* service and controller wiring

### 5. Produced a real audit artifact for the current DevLog dataset

Added:

* `docs/stories/0040-audit-and-remediate-existing-trusted-knowledge-duplicates/duplicate-audit.json`

Artifact source:

* built jar on the Story branch
* live project `devlog-ai`
* endpoint `GET /api/v1/insights/project/{projectId}/duplicate-audit`

Observed snapshot:

* `totalInsights = 17`
* `clusterCount = 6`
* all current clusters were classified as `LIKELY_RICHER_SUCCESSOR`

## Behavioral Outcome

### Now possible

* scan trusted knowledge deterministically for duplicate candidates
* inspect duplicate stock through an API instead of manual database inspection
* capture a reviewable remediation baseline as a story artifact

### Intentionally not performed

* deleting trusted insights
* mutating canonical records
* auto-creating remediation decisions or knowledge relations

## Documentation Outcome

Documentation update: Not required.

Reason:

* the Story adds an internal backend audit endpoint and a story-local artifact;
* no user-facing workflow, setup instruction, or canonical architecture
  document required adjustment for this bounded slice.

## Vault Outcome

* Vault consulted during Repository Analysis: No
* Outcome: no vault action
* Rationale: the Story is a repository-local remediation preparatory slice and
  does not introduce a new transverse pattern by itself.

## Validation

Performed:

* targeted backend tests for audit clustering and endpoint wiring
* full backend `./mvnw verify`
* repository diff formatting check
* live endpoint smoke-check from the built jar on port `18084`

Results:

* targeted backend tests: pass
* backend `./mvnw verify`: pass
* JaCoCo coverage checks: pass
* `git diff --check`: pass
* live duplicate-audit endpoint: pass

Not required:

* AI-engine pytest

Reason:

* no AI-engine code, prompt, or schema changed in this Story.
