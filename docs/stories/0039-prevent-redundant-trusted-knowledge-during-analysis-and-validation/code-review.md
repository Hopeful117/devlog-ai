# Story 0039 — Prevent Redundant Trusted Knowledge During Analysis And Validation — Code Review

## Status

Reviewed

## Review Scope

Review of the first operational trusted-duplicate prevention slice:

* `TrustedKnowledgeDuplicateGuard`
* `ValidationServiceImpl`
* `InsightPayloadSupport`
* `InsightPromotionService`
* backend tests for duplicate conflicts and enrichment preservation

## Findings

No blocking findings.

### 1. The guard is placed at the correct business boundary ✅

Duplicate prevention now runs from `ValidationServiceImpl` before promotion.

That is the right place because:

* ADR-006 proposal persistence remains intact;
* acceptance conflict is explicit;
* trusted persistence is protected before mutation.

### 2. Exact duplicate blocking is narrow and deterministic ✅

The V1 duplicate fingerprint uses:

* trusted type
* `sourceType`
* normalized title
* normalized content
* normalized rationale

This is strict enough to hard-block obvious duplicates without pretending to
solve full semantic deduplication.

### 3. Legitimate enrichments remain preserved ✅

The tests prove that `ENRICHES` proposals with materially new content still
pass, while exact restatements are blocked.

That keeps Story 0037’s incremental-evolution behavior intact.

### 4. Shared payload interpretation reduces drift ✅

Extracting `InsightPayloadSupport` is a good move.

Without it, duplicate detection and promotion could have silently diverged on:

* type mapping;
* text extraction;
* normalization assumptions.

### 5. Legacy source-type fallback is still imperfect ⚠️

When an older trusted insight has no `sourceType`, the fallback mapping is
necessarily coarse, especially for `DOCUMENTATION`-family insights.

This does not block the current Story, but it means V1 duplicate prevention is
strongest for insights with preserved provenance and weaker for some legacy
records.

That limitation is acceptable and should remain documented.

## Gate Results

* targeted backend tests: **PASS**
* backend `./mvnw verify`: **PASS**
* JaCoCo coverage checks: **PASS**
* `git diff --check`: **PASS**

## Conclusion

Approve.

The implementation is intentionally narrow, respects ADR-006 / ADR-050 /
ADR-051, and closes the most important gap: accepted exact duplicate trusted
knowledge can no longer slip through as a normal path.
