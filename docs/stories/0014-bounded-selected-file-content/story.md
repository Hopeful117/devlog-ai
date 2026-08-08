# Story 0014 — Bounded Selected File Content Evidence

## Story ID
0014

## Title
Enrich relevant source and test evidence with bounded repository content

## Status
Draft

## Priority
High

## Date
2026-08-08

---

## User Story

As Kiko preparing an Engineering Story,
I want selected source and test evidence to include safe, bounded content from the synchronized
repository revision,
So that I can understand likely implementation structure before performing targeted verification in
the current repository.

---

## Context

Stories 0001–0009 established DevLog as an optional deterministic context provider for Kiko's
Repository Analysis workflow. Story 0012 improved Engineering Story evidence precision and
diagnostics. Story 0013 then corrected a critical pre-ranking defect by producing representative
`SOURCE_FILE`, `TEST_FILE`, and `CONFIG_FILE` candidates across multi-module repositories.

DevLog can now identify, rank, select, and trace relevant file paths, but its file-level evidence
contains only paths and metadata. Kiko must still open nearly every selected file to discover basic
implementation facts such as declarations, signatures, annotations, control flow, and existing
patterns.

The next bounded capability is repository content for relevant source and test evidence. This Story
does not attempt semantic interpretation or complete repository understanding. It introduces only
safe deterministic content evidence that can reduce broad discovery and guide targeted reads.

The repository remains the final source of truth. DevLog provides context and navigation; Kiko
continues to reason, verify exact behavior, and produce Repository Analysis.

---

## Objective

Extend the Repository Context pipeline so a small, relevant set of source and test files can carry
bounded text extracted from the same synchronized repository revision used for deterministic
collection.

The implementation must:

* enrich only eligible source and test evidence;
* avoid reading configuration content;
* use secure workspace-confined file access;
* apply explicit per-file and total content bounds;
* preserve deterministic output, provenance, ranking authority, token accounting, and digest
  correctness;
* degrade gracefully when content is unavailable or unsuitable;
* keep public Engineering Story Context request compatibility;
* expose enough metadata for Kiko to distinguish complete, truncated, skipped, and failed content
  extraction.

Repository Analysis and Implementation Planning must determine the smallest compatible enrichment
stage and response representation. The solution must not read every repository file merely to enrich
the final context.

---

## Acceptance Criteria

### AC-1: Only eligible source and test evidence may be enriched

Content enrichment is limited to `SOURCE_FILE` and `TEST_FILE` evidence selected or otherwise
bounded by an equivalent deterministic policy.

`CONFIG_FILE`, aggregate structure evidence, Git history, commit diffs, ADRs, documentation,
validated knowledge, and other evidence kinds must not expose repository file content through this
capability.

### AC-2: Content comes from the synchronized repository revision

Every enriched item must be read from the synchronized workspace associated with its repository
source and revision.

Content provenance must retain:

* repository source location;
* originating relative path;
* evidence identifier/reference;
* collector or enrichment policy identifier and version;
* the revision or equivalent traceable workspace context already available to the pipeline.

The implementation must not read an unrelated working directory or infer files from a path outside
the synchronized workspace.

### AC-3: File access remains confined and safe

Content reads must reuse or preserve the repository's existing safety properties:

* normalized repository-relative paths;
* workspace-root confinement;
* symlink rejection or equivalent safe handling;
* excluded/generated/vendor path policy;
* regular-file checks;
* bounded file size and read duration;
* graceful handling of disappeared or unreadable files.

Path traversal and symlink escape attempts must never expose content outside the synchronized
workspace.

### AC-4: Binary and unsupported content is excluded

Binary, unsupported-encoding, oversized, generated, vendor, or otherwise unsuitable files must not
be returned as text content.

The result must expose a deterministic skip or warning reason where it helps explain why an
otherwise selected file was not enriched. Raw file bytes and decoding failures must not leak into
the API response or logs.

### AC-5: Per-file content is explicitly bounded

Every enriched file must respect a configurable or versioned maximum content size. Truncation must:

* be deterministic;
* be explicitly indicated;
* avoid pretending the excerpt is complete;
* preserve useful text without exceeding the configured bound;
* participate truthfully in estimated-token accounting.

The implementation must not silently rely only on the existing summary-character limit if content
has a distinct size or token role.

### AC-6: Total enrichment is bounded

The complete Repository Context must enforce an explicit limit on:

* the number of enriched files;
* total enriched characters or estimated tokens;
* the existing overall Repository Context token budget.

The policy must behave deterministically when more selected files are eligible than can be enriched.
It must not allow content enrichment to make `usedTokens` exceed `maximumTokens`.

### AC-7: Ranking and selection responsibilities remain clear

Content enrichment must not replace `DeterministicEvidenceRanker` or
`BudgetedDiverseEvidenceSelector`.

The implementation must avoid circular behavior in which full content from every candidate is read
to decide which candidates deserve content. Repository Analysis must determine whether enrichment
occurs after path-level selection or through another bounded two-phase design while preserving final
selection authority and explainability.

### AC-8: Evidence and API representation is explicit and compatible

The Engineering Story Context GET and POST request contracts must remain compatible.

The response must allow Kiko to distinguish:

* path-only evidence;
* evidence with bounded content;
* truncated content;
* skipped or unavailable content.

If the evidence model changes, the change must be additive or explicitly versioned. Existing
consumers must continue to receive valid Repository Context evidence and provenance.

### AC-9: Token estimates, decisions, and digest remain truthful

After enrichment:

* every enriched evidence item's estimated tokens must reflect the returned content;
* `RepositoryContext.usedTokens` must match the final returned evidence;
* token-budget warnings and selection/enrichment decisions must remain truthful;
* the context digest must include all returned content and enrichment metadata that affect the
  context;
* identical normalized inputs, content, revision, and deterministic timestamps used in tests must
  produce identical output and digest.

### AC-10: Failure degrades individual evidence, not the whole context

A missing, unreadable, changed, oversized, binary, or invalid file must not fail the complete
Engineering Story Context request when other usable evidence exists.

The result must retain the path evidence where safe and expose bounded diagnostics or warnings. No
exception detail, absolute workspace path, secret, or raw content may be logged or returned.

### AC-11: Sensitive configuration content remains excluded

No configuration file content may be exposed, including files currently recognized as
`CONFIG_FILE`, `.env` variants, credentials, secrets, tokens, private keys, or deployment
configuration.

This Story must not introduce a heuristic secret-redaction system as a substitute for the explicit
source/test-only boundary.

### AC-12: Existing Repository Context behavior remains compatible

Story 0012 precision and Story 0013 candidate diversity must remain intact:

* multi-module source/test/configuration candidates remain available;
* the ranker and selector retain their current policy ownership;
* evidence-kind concentration, diversity, budgets, reasons, distributions, and preferred-layer
  availability remain accurate;
* non-file evidence remains unchanged;
* empty source and unavailable workspace behavior remains graceful.

### AC-13: Tests cover security, bounds, and composition

Add deterministic tests covering at minimum:

* bounded content for selected source and test evidence;
* no content for configuration evidence;
* per-file truncation and total enrichment limits;
* binary/unsupported/oversized input;
* missing/unreadable file behavior;
* traversal and symlink escape rejection;
* multi-module paths;
* truthful token accounting, decisions/warnings, provenance, and digest;
* compatible Engineering Story Context serialization;
* unchanged path-only and non-file evidence behavior.

### AC-14: Quality baseline remains healthy

The implementation must run focused Repository Context, scanner/workspace, selection, and API tests;
the complete backend suite; JaCoCo verification with the existing bundle rule; authenticated
SonarQube analysis with the pinned scanner; and the Quality Gate wait.

Completion requires no new unresolved Sonar issue and a passing Quality Gate. Existing tests must
not be removed or weakened.

### AC-15: Benchmark Story 0013 candidate diversity during Repository Analysis

Before implementation, use the normal DevLog-assisted Repository Analysis for this complete Story
to observe whether Story 0013 now supplies useful multi-module `SOURCE_FILE`, `TEST_FILE`, and
`CONFIG_FILE` candidates.

Store disposable observations outside Git. Record:

* DevLog resolution/API success;
* candidate and selected distributions;
* relevant files and modules surfaced;
* broad versus targeted repository searches;
* evidence noise, missing context, and stale/conflicting evidence;
* what still required direct repository reads.

Do not run Repository Analysis twice or reduce necessary verification to improve the benchmark.
The benchmark must not alter Human Approval Gate behavior.

### AC-16: Post-implementation content validation is factual

Use a fixed representative Story description or deterministic integration fixture to demonstrate
that selected source/test evidence receives bounded content, configuration remains path-only, and
budgets/digest/provenance remain correct.

Do not claim that content reduces Kiko's repository reads until a later real Engineering Story has
measured that outcome.

---

## Scope

### In Scope

* Safe bounded text extraction for relevant source/test evidence.
* A deterministic post-selection or equivalent bounded enrichment phase.
* Per-file and total enrichment budgets.
* Explicit content/truncation/skip metadata or a compatible versioned representation.
* Token, warning/decision, provenance, and digest correctness after enrichment.
* Multi-module workspace paths.
* Security and failure-mode tests.
* Engineering Story Context compatibility tests.
* Documentation reconciliation for changed configuration, API representation, architecture, or
  user-visible behavior.
* A disposable pre-implementation benchmark of Story 0013 candidate diversity.

### Out of Scope

* Configuration-file content.
* Full repository ingestion into Repository Context.
* AI summaries or interpretations.
* AST parsing or symbol extraction.
* Class, method, interface, annotation, or call-graph models.
* Dependency analysis.
* Source-to-test relationship inference.
* Embeddings, vector search, or semantic indexing.
* New persistence entities or trusted knowledge generated from source content.
* Automatic project resolution.
* A DevLog Repository Analyst agent.
* Changes to Kiko's workflow ownership or Human Approval Gates.
* Claims of productivity or token savings not measured in a real later workflow.

---

## Architectural Ownership

### DevLog

Owns synchronized repository access, deterministic evidence, bounded content extraction, provenance,
ranking/selection integration, budgets, diagnostics, and digest correctness.

### Engineering-Skills / engineering-story

Owns when DevLog is requested, fallback behavior, benchmark discipline, workflow sequencing, and
Human Approval Gates.

### Kiko

Owns reasoning over the context, targeted verification, uncertainty handling, and production of the
Repository Analysis.

### Repository

Remains the final source of truth for exact implementation behavior.

---

## Risks

### Content can invalidate current token accounting

Path-only candidates are cheap. Adding text after selection can exceed the global budget or make
existing decisions and `usedTokens` inaccurate unless final evidence is reconciled.

### Reading before selection can recreate repository-scale noise

Enriching every candidate would increase I/O and context size before relevance is known. The Story
requires a bounded phase rather than general file ingestion.

### Source files can still contain sensitive material

Limiting enrichment to source/test evidence reduces risk but does not prove all source text is safe.
Strict size, path, logging, and failure boundaries remain necessary, and configuration is excluded
entirely.

### Post-selection enrichment can alter evidence identity or ordering

Replacing immutable evidence after selection may affect estimates, decisions, digest, and stable
ordering. The implementation must preserve traceability and explicitly reconcile final values.

### Stale synchronized content can differ from Kiko's current working tree

DevLog content is context from a traceable synchronized revision, not an assertion about uncommitted
local changes. Kiko must verify exact current behavior in the repository.

---

## Definition of Done

* All acceptance criteria are satisfied.
* Focused and complete backend tests pass.
* JaCoCo verification passes.
* Authenticated SonarQube Quality Gate passes.
* Documentation Reconciliation is completed.
* Implementation Report and Code Review are completed.
* All Human Approval Gates are respected.
* No benchmark artifact is committed to the repository.
