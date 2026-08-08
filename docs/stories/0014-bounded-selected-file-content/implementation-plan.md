# Implementation Plan

## Overview

Implement a deterministic selected-evidence enrichment phase between path-level evidence selection
and final `RepositoryContext` assembly. The phase will consider only selected `SOURCE_FILE` and
`TEST_FILE` evidence, synchronize the exact revision recorded by repository-structure collection,
read each eligible relative path through a dedicated confined reader, and attach an explicit bounded
content result to the immutable evidence.

The design preserves the existing ranker and selector as the authorities for path relevance and
diversity. Enrichment consumes only the remaining global token budget, applies separate file-count,
per-file, and aggregate-content limits, and then reconciles selected evidence, token estimates,
selection decisions, warnings, and digest before the public response is built. Configuration and
non-file evidence remain content-free.

The new pipeline seam and trust/security boundary will be documented in a focused ADR. The existing
Engineering Story Context GET and POST request contracts remain unchanged; the evidence response
evolves additively.

## Planned Changes

1. Define a versioned bounded-content policy in the Repository Context configuration boundary.
   Introduce explicit defaults for maximum enriched files, maximum characters per file, and maximum
   aggregate characters. Validate positive/coherent values at startup and always combine these
   limits with the remaining `RepositoryContext` token budget. Keep these limits separate from the
   existing collection scanner and summary limits because they govern returned context rather than
   repository discovery.

2. Add an explicit immutable content result to `RepositoryEvidence`. The representation will carry
   an optional whitespace-preserving excerpt plus a deterministic status (`COMPLETE`, `TRUNCATED`,
   `SKIPPED`, or `UNAVAILABLE`), a bounded reason code where applicable, the enrichment policy
   identifier/version, and the resolved revision. Non-eligible evidence will retain a null/absent
   content result, preserving existing JSON consumers. Add a compatibility constructor or update
   factories centrally so current evidence producers remain path-only without duplicating changes.

3. Extend repository-structure provenance at collection time with the exact resolved revision
   returned by `SynchronizedWorkspace`. Keep the source identifier and normalized originating path
   already present. Do not attach content during the repository walk and do not change lexical
   ranking inputs; this prevents content reads across all candidates and avoids circular ranking.

4. Introduce a targeted secure repository-content reader in the collection/workspace boundary. It
   will accept a synchronized workspace and repository-relative path, normalize and confine the
   path, reject traversal, symlinks, excluded/generated/vendor paths, non-regular files, oversized
   files, binary/unsupported UTF-8, and unreadable/disappeared files, and return deterministic
   status/reason data without absolute paths or raw failures. Reuse the existing exclusion and size
   policy from `SecureRepositoryScanner` through a shared helper where this avoids duplicating
   security rules; do not broaden the scanner into an unrestricted content loader.

5. Add a `SelectedFileContentEnricher` (or equivalently named Repository Context component) invoked
   by `RepositoryContextEngine` after `EvidenceSelector.select(...)`. It will:

   * filter selected evidence to `SOURCE_FILE` and `TEST_FILE` only;
   * process eligible items deterministically by final relevance/order with a reference tiebreaker;
   * group/resolve the persisted active Git source from provenance;
   * synchronize the exact recorded commit hash rather than a moving branch;
   * apply enriched-file-count, per-file, aggregate-character, and remaining-token limits;
   * preserve path evidence with `SKIPPED` or `UNAVAILABLE` metadata for individual failures;
   * never read or expose `CONFIG_FILE` content;
   * return enriched immutable evidence and bounded warning codes without failing otherwise usable
     context.

6. Reconcile final accounting inside `RepositoryContextEngine`. Recalculate every enriched item's
   token estimate from the exact returned excerpt in addition to its existing summary/reference,
   calculate `usedTokens` from the final selected list, and update selected decision token estimates
   without changing their selection status or rank reason. Content must be shortened or skipped
   before it can exceed `maximumTokens`; selected path evidence must not be removed merely because
   content does not fit. Add deterministic context warnings for applied enrichment bounds and
   unavailable content while keeping raw filesystem/error details internal.

7. Include the additive content result, final estimates, revised decisions, enrichment warnings,
   and policy version in the existing digest input. Preserve deterministic ordering and verify that
   fixed timestamps, revision, content, and inputs yield the same digest. Do not expand this Story
   into changing the synthetic live-request timestamps identified by the benchmark.

8. Preserve API compatibility. Keep both
   `GET /api/projects/{projectId}/engineering-story-context?description=...` and the preferred POST
   body unchanged. Verify JSON serialization for complete, truncated, skipped, unavailable, and
   path-only evidence. Ensure no absolute workspace path, exception message, binary data, or
   configuration content appears in responses or logs.

9. Record the architecture in `ADR-044 — Bounded Selected File Content Enrichment`. Document the
   post-selection phase, path-ranking authority, exact-revision resynchronization, additive evidence
   contract, security boundary, budget reconciliation, and the continued distinction between
   transient repository evidence and validated knowledge. Reconcile the root README and relevant
   collection/Repository Context documentation with the new limits and actual API behavior.

10. Validate with focused unit/web tests first, then the complete backend quality lifecycle. Use a
    fixed representative Story/fixture to show that selected source/test evidence receives bounded
    content, configuration remains path-only, provenance and token accounting are truthful, and
    identical deterministic fixtures produce identical digests. Run Maven verification and the
    authenticated pinned Sonar scanner with Quality Gate wait; do not claim workflow efficiency
    improvements from this implementation-only fixture.

## Files to Modify

* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryEvidence.java` — add the
  optional bounded-content result and immutable copy/update helpers.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextEngine.java` —
  invoke post-selection enrichment and reconcile final tokens, decisions, warnings, and digest.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/collector/RepositoryStructureCollector.java`
  — retain the synchronized revision in file-evidence extraction/provenance metadata while keeping
  candidates path-only.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/collector/EvidenceFactory.java` —
  centralize path-only construction and final content-aware token estimation as appropriate.
* `backend/src/main/java/com/hopeful117/devlogai/collection/collector/SecureRepositoryScanner.java`
  and/or its shared path-policy helper — expose reusable confinement/exclusion checks without
  weakening current scan behavior.
* `backend/src/main/java/com/hopeful117/devlogai/collection/collector/CollectorLimits.java` only if
  shared file-suitability limits need a read-only accessor; enrichment-specific limits remain in
  Repository Context configuration.
* `backend/src/main/resources/application.properties` — define bounded-content defaults.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextServiceTest.java`
  — cover final composition, budgets, decisions, warnings, provenance, and digest.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/collector/RepositoryStructureCollectorTest.java`
  — cover revision metadata and unchanged Story 0013 candidate diversity/path-only behavior.
* `backend/src/test/java/com/hopeful117/devlogai/collection/collector/SecureRepositoryScannerTest.java`
  or the new reader test — preserve scanner safety and cover shared path-policy behavior.
* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextControllerWebMvcTest.java`
  — verify additive serialization and unchanged GET/POST requests.
* `README.md` and `backend/src/main/java/com/hopeful117/devlogai/collection/README.md` — document
  bounded selected content, configuration, trust boundary, and limitations.
* `docs/roadmap.md` if the implemented Repository Memory boundary needs status reconciliation.

## Files to Create

* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryEvidenceContent.java` —
  explicit immutable content/status contract, unless kept as a nested record in
  `RepositoryEvidence` after implementation review.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/enrichment/SelectedFileContentEnricher.java`
  — deterministic selected-evidence enrichment and budget reconciliation boundary.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/enrichment/RepositoryContentPolicy.java`
  — validated versioned enrichment configuration/policy.
* `backend/src/main/java/com/hopeful117/devlogai/collection/collector/SecureRepositoryContentReader.java`
  — targeted confined text reader, if scanner safety cannot be cleanly reused through a small
  shared helper.
* Focused unit tests matching the new components, likely
  `SelectedFileContentEnricherTest.java`, `RepositoryContentPolicyTest.java`, and
  `SecureRepositoryContentReaderTest.java`.
* `docs/decisions/ADR-044.md` — architectural decision for bounded selected-file content.

## Dependencies

The enricher depends internally on selected `RepositoryEvidence`, the persisted active `Source`,
`WorkspaceManager`, synchronized revision metadata, the secure content reader, and the existing
Repository Context budget. `RepositoryContextEngine` depends on the enricher's immutable result
before diagnostics/digest assembly.

The secure reader depends only on Java NIO and existing collection safety policy. No new external
library, service, database table, Flyway migration, frontend change, AI Engine change, or
Engineering-Skills change is required.

Implementation ordering is: define policy/contract; establish safe targeted reading and revision
metadata; implement selected enrichment; reconcile engine accounting/digest; protect API
serialization; reconcile architecture/documentation; run complete validation.

## Test Plan

* Add content-contract tests for absent/path-only, complete, truncated, skipped, and unavailable
  states, including immutable copying and JSON shape (AC-5, AC-8, AC-10).
* Add secure-reader tests for normalized multi-module paths, traversal, symlink escape, excluded
  paths, regular-file enforcement, binary/invalid UTF-8, oversized, missing, and unreadable files.
  Assert that returned diagnostics contain reason codes but no absolute path or exception detail
  (AC-2 through AC-4, AC-10, AC-11, AC-13).
* Add enrichment-policy tests for deterministic ordering, source/test eligibility, configuration
  exclusion, maximum enriched files, per-file truncation, aggregate-character limit, and exhausted
  global token budget (AC-1, AC-5 through AC-7, AC-11).
* Update structure-collector tests to assert resolved revision metadata and unchanged balanced
  source/test/configuration candidates across modules, including empty/unavailable workspace cases
  (AC-2, AC-12).
* Extend Repository Context service tests with a fixed synchronized workspace fixture. Assert final
  estimated tokens equal the returned evidence, `usedTokens <= maximumTokens`, selected decisions
  carry final estimates, warnings are truthful, path evidence survives read failures, non-file
  evidence is unchanged, and identical fixed inputs/content/revision/timestamps produce identical
  digests (AC-6, AC-9, AC-10, AC-12, AC-13, AC-16).
* Extend controller tests for unchanged GET and POST requests and additive response serialization;
  explicitly assert no content for `CONFIG_FILE` and no unsafe diagnostic fields (AC-8, AC-11,
  AC-13).
* Run focused tests for collection scanner/reader, Repository Structure, enrichment, ranking,
  selection, Repository Context service, adapter, and controller.
* Run `cd backend && ./mvnw clean verify` and require the full suite plus the existing JaCoCo bundle
  threshold to pass.
* Load the ignored root `.env` containing the rotated project-analysis token, then run the pinned
  scanner with `./mvnw sonar:sonar -Dsonar.qualitygate.wait=true` (or the documented combined
  `clean verify sonar:sonar` command) against the configured local SonarQube. Require a passing
  Quality Gate and no new unresolved issue.
* Exercise the deterministic Story fixture/API and record only factual output correctness. A later
  real Story, not this implementation fixture, must measure whether Kiko reads fewer files (AC-16).

## Risks

### Double synchronization can add latency

The post-selection enricher may synchronize a source already synchronized by the structure
collector. Pinning the second operation to the recorded commit guarantees consistency; grouping by
source and enriching in one pass bounds the cost. Avoiding this synchronization would require a
larger request-scoped workspace refactor outside the Story.

### Content metadata itself affects accounting

Excerpt text dominates the added context, but status and policy metadata also affect the serialized
response. The final estimate and digest must be calculated from the final immutable representation,
and content must be clipped before budget overflow.

### Source files may contain sensitive literals

Eligibility, confinement, excluded paths, strict size/encoding limits, and logging tests reduce the
surface but do not constitute secret detection. The plan excludes configuration entirely and keeps
heuristic redaction out of scope; Kiko and the repository retain the existing local trust boundary.

### Failed reads could regress discovery

The reader/enricher must return status metadata while preserving the original path evidence. Tests
must explicitly prevent the scanner's current omit-on-content-failure behavior from propagating to
Repository Context.

### API consumers may assume the old evidence shape

An optional additive field and unchanged endpoint requests limit compatibility risk. Controller
serialization tests and the Engineering-Skills adapter's existing tolerant extraction must verify
that path-only evidence remains valid.

### Ranked lexical noise will consume some enrichment budget

Selected paths are not guaranteed relevant. Strict file-count/character/token limits contain the
cost; this Story must not introduce content-based reranking or semantic interpretation.

## Validation Checklist

- [ ] Only selected `SOURCE_FILE` and `TEST_FILE` evidence can receive content.
- [ ] `CONFIG_FILE` and all non-file evidence remain content-free.
- [ ] Content is read from the exact recorded synchronized revision.
- [ ] Relative paths are confined; traversal, symlink escape, excluded paths, binary/invalid UTF-8,
      oversized, missing, and unreadable files cannot leak content.
- [ ] Per-file, enriched-file-count, aggregate-character, and global-token limits are enforced
      deterministically.
- [ ] Complete, truncated, skipped, unavailable, and path-only states are explicit.
- [ ] Failed extraction preserves usable path evidence and does not fail the complete context.
- [ ] Ranking and diverse selection behavior remain path-level and unchanged.
- [ ] Final evidence token estimates, `usedTokens`, decisions, warnings, and digest are truthful.
- [ ] Fixed deterministic inputs yield the same evidence and digest.
- [ ] Story 0013 multi-module source/test/configuration candidate diversity remains covered.
- [ ] Engineering Story Context GET and POST requests remain compatible.
- [ ] No absolute workspace path, exception detail, raw bytes, or configuration content is exposed
      or logged.
- [ ] ADR-044 and user/developer documentation describe the implemented behavior and limits.
- [ ] No persistence migration, AI interpretation, symbol/dependency analysis, or workflow change is
      introduced.
- [ ] Focused tests, complete Maven verification, JaCoCo rule, authenticated SonarQube analysis, and
      Quality Gate all pass.
- [ ] Validation claims only response correctness; productivity remains unproven until a later real
      Story benchmark.

## Recommendation

Ready for implementation

## Approval Required

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.
