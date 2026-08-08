# Repository Analysis

## Story Understanding

Story 0014 requests a bounded deterministic capability that enriches a small set of relevant
`SOURCE_FILE` and `TEST_FILE` Repository Context evidence with text from DevLog's synchronized Git
workspace. The objective is to expose enough implementation context to improve Kiko's navigation
before direct verification, without turning DevLog into a source-code interpreter or replacing the
repository as the source of truth.

The enrichment must be confined to eligible source and test evidence, tied to a traceable source
and revision, safe against traversal and symlink escape, and bounded per file and for the complete
context. Its returned content and extraction state must be represented explicitly. Final token
estimates, selection information, warnings, provenance, and digest must describe the response that
was actually returned.

Configuration content, unrestricted repository ingestion, AST or symbol extraction, dependency and
source-to-test inference, embeddings, AI summarization, new trusted knowledge, automatic project
resolution, and changes to Kiko's workflow authority are explicitly excluded.

## Repository Summary

The affected implementation is in the Java Core backend. Repository Context is constructed by a
deterministic pipeline: Context Intelligence produces a plan, independent collectors produce
immutable evidence candidates, `DeterministicEvidenceRanker` scores them, and
`BudgetedDiverseEvidenceSelector` selects a diverse subset within item and token budgets.
`RepositoryContextEngine` then assembles diagnostics, warnings, selection decisions, token usage,
and a SHA-256 context digest.

Repository structure is collected from an isolated synchronized Git workspace. The existing secure
scanner already confines paths, rejects symbolic links, excludes generated/vendor directories, and
applies file, byte, count, and time limits. Story 0013 changed file-candidate allocation so
multi-module source, test, and configuration evidence is represented before ranking. The current
structure collector deliberately requests no file content, and file evidence therefore contains
paths and metadata only.

The public Engineering Story Context API wraps the resulting `RepositoryContext` and supports both
the preferred POST body for complete Stories and the compatibility GET query parameter. The API is
already consumed by Engineering-Skills; this Story changes provider output only and must preserve
those request contracts.

## Affected Modules

### Backend Repository Context engine

Package `com.hopeful117.devlogai.repositorycontext` owns the final context contract, orchestration,
budgets, diagnostics, digest, and public evidence model. `RepositoryContextEngine`,
`RepositoryContext`, `RepositoryEvidence`, and their tests are affected because enrichment changes
the final evidence payload and its token/digest accounting.

### Repository Context collection

Package `com.hopeful117.devlogai.repositorycontext.collector` owns repository-structure discovery
and evidence construction. `RepositoryStructureCollector` currently creates path-only module,
source, test, and configuration candidates. `EvidenceFactory` owns summary normalization,
provenance/extraction metadata, and current token estimation. The new content boundary must compose
with these responsibilities without reading all candidates merely to rank them.

### Deterministic ranking and selection

Packages `repositorycontext.ranking` and `repositorycontext.selection` own relevance scoring and
budgeted diverse selection. Their policy authority must remain unchanged, but their outputs must
remain truthful if enrichment changes an evidence item's final size. The selector currently makes
decisions and calculates `usedTokens` from pre-enrichment `estimatedTokens`.

### Secure repository collection and workspace synchronization

Package `com.hopeful117.devlogai.collection` supplies `SecureRepositoryScanner`, `RepositoryFile`,
and `CollectorLimits`; package `collection.workspace` supplies `GitWorkspaceManager` and
`SynchronizedWorkspace`. These are the existing safety and revision boundaries that any content
read must reuse or preserve.

### Engineering Story Context API

Package `com.hopeful117.devlogai.projectcontext` exposes `EngineeringStoryContext` through GET and
POST operations and creates the synthetic analysis/intent used by the Repository Context Engine.
Its serialization tests are affected by any additive evidence representation, while its request
contract must remain compatible.

### Configuration and documentation

Backend configuration may be affected if distinct content limits become configurable. The root
README and relevant architecture documentation require reconciliation if public representation,
configuration, or the deterministic pipeline changes.

## Existing Implementation

`RepositoryContextEngine.build(...)` currently performs collection, ranking, selection, final
ordering, diagnostics, warning generation, and digest construction in one deterministic service.
There is no content-enrichment phase. The engine receives only the selected evidence and token count
returned by `EvidenceSelector`.

`RepositoryEvidence` is an immutable record containing layer, kind, reference, bounded summary,
timestamp, relevance score, related references, provenance, extraction metadata, estimated tokens,
and ranking reasons. It has no explicit content excerpt, truncation state, or content-skip reason.
Using the existing summary as source content would be inadequate because `EvidenceFactory`
normalizes whitespace and applies a summary-character limit intended for factual summaries rather
than code excerpts.

`EvidenceFactory.create(...)` builds the current provenance and extraction metadata and estimates
tokens from the summary and reference. Therefore the current estimate cannot account for additional
content. `RepositoryContext.ContextBudget` contains maximum evidence items, summary characters,
history items, and total tokens, but no file-content count, per-file, or aggregate content limit.

`BudgetedDiverseEvidenceSelector` deduplicates by reference, reserves preferred-layer diversity,
selects within the evidence-item and token budgets, and emits decisions based on candidate token
estimates. Adding content after this point without reconciliation would make its selected decisions,
`usedTokens`, and potentially the global budget inaccurate.

`RepositoryStructureCollector` synchronizes the first active project source through
`GitWorkspaceManager`, scans its module layout, and produces bounded `SOURCE_FILE`, `TEST_FILE`, and
`CONFIG_FILE` candidates. It invokes `SecureRepositoryScanner.scan(context, path -> false)`, so
`RepositoryFile.content` is always absent for this use. Its evidence provenance records the source
identifier and relative originating file, but the resolved synchronized revision is not currently
carried by the final file evidence.

`SecureRepositoryScanner` already provides the main filesystem safety properties required by the
Story: normalized workspace confinement, symbolic-link rejection, excluded-directory policy,
regular-file checks, maximum file size, aggregate byte limit, deadline, file-count limit, and UTF-8
reading controlled by a content predicate. However, when a requested content read is oversized,
unreadable, or not valid UTF-8, the current scan may omit that repository file rather than preserve
its path-only evidence with a content status. Its current predicate also operates while walking the
repository, before Repository Context ranking and selection are known.

`GitWorkspaceManager` serializes synchronization per source, cleans and fetches the workspace,
checks out the requested revision in detached mode, and returns `SynchronizedWorkspace` with the
resolved revision. This is the correct existing source-of-truth boundary for DevLog content; no
working-directory access is needed.

The current GET and POST controller operations return the same `EngineeringStoryContext`, which
contains the project snapshot and optional full `RepositoryContext`. Controller tests already
protect request compatibility and diagnostic serialization.

Existing tests cover secure scanning with and without content, directory exclusions, symlinks,
file/byte limits, repository-structure candidate diversity, deterministic ranking and selection,
Repository Context budgets/digests under fixed inputs, and Engineering Story Context web
serialization. They do not cover a selected-only content phase, preservation of path evidence when
content extraction fails, separate enrichment limits, or final accounting after enrichment.

The pre-implementation DevLog-assisted run for this complete Story returned 59 candidates and 53
selected items within 2,229 of 6,000 estimated tokens. Its candidates included 14 source files, 13
test files, and 13 configuration files across seven modules, confirming that Story 0013 corrected
the previous category-concentration defect. The ranked evidence directly identified the secure
scanner, ranking/selection policies, and relevant tests. It did not identify several central
contracts—including `RepositoryContextEngine`, `RepositoryEvidence`, `EvidenceFactory`,
`RepositoryStructureCollector`, and workspace synchronization—so targeted repository inspection
remained necessary. No broad repository discovery was required after DevLog context was received.

## Relevant Documentation

* `README.md`
* `backend/src/main/java/com/hopeful117/devlogai/collection/README.md`
* `docs/decisions/ADR-037.md` — Repository-First Context Extraction
* `docs/decisions/ADR-038.md` — Repository Context Engine
* `docs/decisions/ADR-039.md` — Context Intelligence
* `docs/decisions/ADR-040.md` — Knowledge and Evidence Separation
* Story 0013 artifacts under `docs/stories/0013-multi-module-file-candidate-diversity/`
* Engineering-Skills `engineering-story` Repository Analysis prompt and DevLog integration reference

## Constraints

* Context construction must remain deterministic; AI may consume but must not build or promote the
  evidence.
* Repository evidence remains distinct from validated knowledge under ADR-040. Source excerpts are
  transient factual evidence, not Insights.
* Repository Context must stay bounded, traceable, explainable, and compatible with ADR-037 and
  ADR-038.
* Existing ranking and diversity-selection ownership must not be replaced or bypassed.
* Content reads must use the synchronized source workspace and retain revision-level traceability.
* Only selected or equivalently bounded `SOURCE_FILE` and `TEST_FILE` evidence is eligible;
  configuration and all other evidence kinds remain content-free.
* The existing global evidence-item and token budgets remain authoritative. Additional per-file,
  enriched-file-count, and aggregate-content limits are required.
* GET and POST Engineering Story Context request contracts must remain compatible, and response
  evolution must be additive or explicitly versioned.
* Path evidence must survive non-fatal content failure; content extraction must not turn one bad
  file into a failed context request.
* Absolute workspace paths, exception details, configuration content, secrets, binary bytes, and
  decoding failures must not be returned or logged.
* Source content may be stale relative to Kiko's uncommitted working tree; the current repository
  remains authoritative for verification.
* Documentation Reconciliation and all Human Approval Gates remain mandatory.

## Risks

### Final token and selection state can become inconsistent

Selection is currently completed using path-only estimates. Enrichment can exceed the token budget
or invalidate `usedTokens`, decisions, warnings, and the digest unless the final response is
explicitly reconciled.

### The existing scanner is safe but not directly selected-only

Its content predicate is evaluated during the repository walk, while final selection is known only
later. Reusing it carelessly could read too many candidate files or cause failed content reads to
remove otherwise useful path evidence.

### Revision provenance is incomplete at the final evidence boundary

The synchronized revision exists in `SynchronizedWorkspace`, but current structure evidence does
not expose it. Content without this traceability could appear more current or authoritative than it
is.

### API representation may create compatibility or confidentiality regressions

Adding code text to a generic evidence structure affects serialization, digest inputs, logging
boundaries, and consumers. Source/test-only eligibility reduces exposure but cannot prove source
files never contain hard-coded sensitive values.

### A new pipeline seam needs explicit architectural ownership

ADR-038 defines collection, ranking, diverse selection, token budgeting, and context assembly. A
selected-content phase is compatible with its bounded-context goals, but its precise place and
ownership are not yet documented. Documentation Reconciliation must determine whether an ADR
amendment or a focused new ADR is required.

### Lexical ranking can select noisy files

The benchmark surfaced relevant scanner and policy files but also unrelated Git-history models and
configuration files due to vocabulary overlap. Content enrichment must remain bounded and must not
be interpreted as proof that every selected file is relevant.

### Live digest stability has an existing timestamp boundary

Two equivalent live Engineering Story Context requests returned different digests because the
synthetic analysis and collected evidence use request-time timestamps. Story 0014 requires content
and enrichment metadata to participate truthfully in the digest and deterministic fixtures to stay
stable; it must not accidentally claim that the broader live-request timestamp behavior is solved.

## Open Questions

None. The response representation, exact bounded enrichment seam, and whether its architecture is
documented by amending ADR-038 or by a focused new ADR are legitimate Implementation Planning
decisions constrained by the Story, not missing product requirements.

## Recommendation

Ready for planning

## Implementation Readiness

The current repository contains the necessary synchronization, secure-scanning, evidence,
selection, budget, diagnostics, digest, API, and testing boundaries to implement the Story. No new
database entity, external service, AI capability, or Engineering-Skills change is required.

The missing content representation, enrichment limits, and selected-only composition contract are
the intended scope of Story 0014 rather than blocking prerequisites. Planning must preserve path
evidence on extraction failure, revision provenance, final budget truthfulness, and API
compatibility. No blocking ADR conflict or unavailable repository dependency was identified.

## Approval Required

Repository Analysis completed.

Human approval required before Implementation Planning.

Awaiting explicit human approval.
