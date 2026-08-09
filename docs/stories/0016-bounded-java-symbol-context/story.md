# Story 0016 — Bounded Java Symbol Context

## Story ID
0016

## Title
Expose deterministic Java symbols for selected repository evidence

## Status
Draft

## Priority
High

## Date
2026-08-09

---

## User Story

As Kiko preparing an Engineering Story,
I want DevLog to expose bounded Java symbols for the most relevant selected source and test files,
So that I can identify the classes, interfaces, methods, and annotations that deserve direct
repository verification without reconstructing basic code structure from paths or truncated text.

---

## Context

Stories 0012 and 0013 improved evidence precision and multi-module file diversity. Story 0014 added
safe, revision-traceable source/test excerpts after global selection. Story 0015 corrected scarce
content allocation so central selected files receive content before weaker alphabetical
distractors, while preserving the existing 6,000-token budget.

The final Story 0015 benchmark demonstrated that the central production enricher and its test now
receive content with explicit allocation reasons. It also showed the next concrete limitation: all
six returned excerpts were truncated. DevLog can identify relevant paths and show bounded text, but
Kiko must still inspect files directly to determine basic declarations such as:

* which classes, interfaces, records, enums, and annotations exist;
* which methods or constructors define the public implementation surface;
* which annotations govern framework behavior;
* which test types and test methods cover the selected area.

Increasing the global context limits would return more text without making that text structurally
denser. The next increment should therefore extract bounded deterministic symbol metadata before
considering larger default budgets or an autonomous DevLog Repository Analyst.

---

## Objective

Add deterministic Java symbol context for already selected relevant repository evidence.

The capability must expose useful declaration-level structure with revision and file provenance,
strict item/token/size/time bounds, explicit extraction outcomes, and compatible Repository Context
serialization.

Repository Analysis and Implementation Planning must determine the smallest maintainable parsing
and integration design. They must evaluate existing maintained Java parsing facilities before
introducing custom parsing logic. The Story must not infer runtime behavior or relationships that
cannot be proven from deterministic source structure.

---

## Acceptance Criteria

### AC-1: Symbol extraction remains selected and bounded

Symbol extraction may inspect only Java source/test evidence that has already passed the existing
global Repository Context selection boundary.

It must not parse every Java candidate or walk the repository independently to improve relevance.
It must define deterministic limits for files inspected, symbols returned, characters or file size,
duration, and final Repository Context tokens.

### AC-2: Relevant Java declaration kinds are represented

For supported selected Java files, expose bounded structured metadata where present for:

* classes;
* interfaces;
* records;
* enums;
* annotation declarations;
* constructors;
* methods;
* relevant declaration annotations.

Repository Analysis may refine the exact V1 field set, but the response must distinguish type-level
and executable-level declarations without requiring Kiko to parse a prose summary.

### AC-3: Symbols retain precise provenance

Every symbol must remain traceable to:

* the selected evidence reference;
* repository source/location;
* normalized repository-relative file;
* exact synchronized revision;
* deterministic extractor identity and version;
* declaration location when reliably available.

The current repository remains authoritative if synchronized evidence is stale or conflicts with the
working tree.

### AC-4: Public signatures are useful but bounded

Type and executable metadata should expose enough declaration information for targeted navigation,
such as name, declaration kind, modifiers, parameter types, return type, and annotations when
available.

Method bodies, comments, full source text, inferred behavior, and unbounded generic/type detail must
not be copied into the symbol contract.

### AC-5: Extraction outcomes are explicit

For every selected Java file considered by the symbol phase, the response must distinguish at least:

* symbols extracted;
* no supported symbols;
* skipped by a deterministic bound or policy;
* unsupported or malformed source;
* unavailable workspace/revision/file;
* extraction failure.

An individual failure must preserve the existing path/content evidence and must not fail the
complete Engineering Story Context request.

### AC-6: Existing security and revision boundaries are preserved

Reuse the Story 0014 workspace confinement, project ownership, exact-revision, path normalization,
symlink, file-size, binary/encoding, and duration principles where applicable.

Symbol extraction must not follow arbitrary filesystem paths, inspect configuration content, execute
repository code, resolve external dependencies from the network, or invoke a compiler/plugin from
the repository.

### AC-7: No speculative semantic relationships

The V1 extractor may report declarations directly present in a file. It must not claim:

* call relationships;
* dependency graphs;
* runtime dispatch;
* implementation behavior;
* source-to-test relationships;
* framework wiring beyond explicit annotations or declarations;
* semantic similarity.

Those capabilities require separate evidence and later Stories.

### AC-8: Existing ranking and allocation ownership remains clear

DeterministicEvidenceRanker, BudgetedDiverseEvidenceSelector, SelectedContentAllocationPolicy, and
Repository Content limits retain their current responsibilities.

Repository Analysis must determine whether symbols attach to selected evidence, content-allocated
evidence, or another narrowly bounded post-selection representation. The implementation must not
introduce a second opaque global ranker or allow extracted symbols to retroactively change which
candidates were globally selected.

### AC-9: API evolution remains additive and explainable

Engineering Story Context GET/POST requests remain compatible.

The response must expose symbol extraction policy/version, outcome, limits or truncation reason, and
structured symbols additively. Selection decisions, warnings, provenance, final token estimates, and
context digest must remain truthful.

### AC-10: Determinism is verified

The same selected evidence, source revision, policy, and limits must produce the same ordered symbol
representation and digest input.

Ordering and truncation must use explicit deterministic rules rather than parser traversal
accidents, filesystem order, or hash-map iteration.

### AC-11: Representative Java coverage is mandatory

Tests must cover representative source and test fixtures containing:

* nested and top-level types;
* classes, interfaces, records, enums, and annotation declarations;
* constructors and overloaded methods;
* modifiers, parameters, return types, generics, and annotations within the approved V1 contract;
* malformed or partially supported Java;
* empty/no-symbol files;
* deterministic ordering and truncation;
* unavailable revision/workspace and individual extraction failure.

Fixtures must remain generic and must not special-case DevLog production filenames.

### AC-12: Existing Repository Context behavior remains compatible

Path evidence, bounded content, allocation metadata, source/test/configuration diversity, ADR and
history evidence, warnings, selection decisions, GET/POST serialization, workspace failure fallback,
and total budget enforcement must continue to work.

Configuration and non-Java evidence must remain symbol-free.

### AC-13: Quality baseline remains healthy

Run focused extractor, enrichment, Repository Context, API, workspace/reader, ranking, and selection
tests; the complete backend suite; JaCoCo verification; authenticated SonarQube analysis with
Quality Gate wait; and a local Docker/API validation.

Completion requires no new unresolved SonarQube issue and a passing Quality Gate.

### AC-14: A real benchmark validates information density

Before Code Review, run one normal Engineering Story Context request using the complete Story 0016
after rebuilding the local backend.

Keep observations outside Git and record factually:

* request duration, candidates, selected evidence, tokens, digest, and warnings;
* selected Java files with symbol outcomes;
* symbols that correctly guided targeted repository reads;
* relevant declarations DevLog missed or represented incorrectly;
* direct file reads still required;
* whether structured symbols provide useful information beyond the existing truncated excerpts.

Do not run a synthetic native workflow twice, manipulate selection for favorable results, or claim
general productivity gains from one benchmark.

### AC-15: Documentation reconciliation records the capability honestly

Update canonical architecture, API, configuration, and operational documentation when affected.

Documentation must state that DevLog exposes deterministic declaration structure, not method
behavior, call graphs, dependency resolution, or complete repository understanding.

---

## Scope

### In Scope

* Deterministic bounded Java declaration extraction after global evidence selection.
* Structured type, constructor, method, modifier, parameter, return-type, and annotation metadata.
* File/revision/extractor provenance and explicit outcomes.
* Deterministic ordering, truncation, budgeting, warnings, digest, and additive API representation.
* Safe handling of malformed, unsupported, unavailable, or partially extracted files.
* Focused, regression, complete-suite, SonarQube, Docker/API, and disposable benchmark validation.
* Documentation reconciliation.

### Out of Scope

* Method bodies or behavioral summaries.
* Call graphs, control-flow, data-flow, or runtime dispatch.
* Dependency graphs or external dependency resolution.
* Source-to-test relationship inference.
* Cross-file type resolution or symbol linking.
* Semantic embeddings or AI interpretation.
* Non-Java language extraction.
* Configuration-file content.
* Increased global context/content limits solely to make the benchmark pass.
* Persistence or trusted-knowledge promotion.
* Automatic project resolution.
* A DevLog Repository Analyst agent.
* Engineering-Skills, Kiko, workflow-gate, or Human Approval changes.

---

## Architectural Ownership

### DevLog

Owns deterministic selected-file symbol extraction, policies, provenance, bounds, outcomes,
warnings, token accounting, digest correctness, tests, and additive API representation.

### Engineering-Skills / engineering-story

Owns DevLog invocation, optional fallback, benchmark discipline, workflow sequencing, and Human
Approval Gates.

### Kiko

Owns reasoning over declarations, targeted repository verification, uncertainty, and Repository
Analysis production.

### Repository

Remains the source of truth for exact implementation behavior and the current working tree.

### Human

Owns all three workflow approvals and decides whether extracted symbols are useful enough to justify
the next analytical increment.

---

## Risks

### R1: A custom Java parser becomes fragile

Regex or partial grammar logic can misrepresent nested types, generics, annotations, records, or
modern Java syntax. Repository Analysis must evaluate maintained parser options and justify any
custom implementation.

### R2: Parser capability expands into semantic claims

A declaration parser can expose syntax but cannot prove runtime behavior, calls, or resolved
dependencies without additional analysis. The contract and documentation must keep that boundary
explicit.

### R3: Symbols consume budget without reducing verification

Verbose signatures could displace more useful evidence while still requiring full file reads.
Strict structured limits and the real benchmark must measure information density rather than raw
symbol count.

### R4: Truncated content is mistaken for parser input

Parsing an arbitrary excerpt may create false syntax failures or incomplete declarations.
Repository Analysis must decide whether to use a separate confined full-file parse within stricter
bounds or another safe selected-only source, without weakening Story 0014 controls.

### R5: Synchronized revision differs from the working tree

Symbol provenance must expose the exact revision. Kiko must verify implementation facts against the
current repository whenever evidence may be stale.

### R6: The first benchmark is overgeneralized

One DevLog Story can validate concrete declaration usefulness but cannot prove autonomous Repository
Analysis readiness across repositories or languages.

---

## Definition of Done

* [ ] All acceptance criteria are implemented.
* [ ] Symbol extraction is selected-only, bounded, deterministic, and revision-traceable.
* [ ] Structured Java declarations and explicit outcomes are exposed additively.
* [ ] Existing security, ranking, selection, content, API, and budget behavior remains compatible.
* [ ] Representative, failure, determinism, accounting, and digest tests pass.
* [ ] Complete backend tests and JaCoCo verification pass.
* [ ] Authenticated SonarQube analysis and Quality Gate pass with no new unresolved issue.
* [ ] Docker/API validation succeeds.
* [ ] Disposable benchmark remains outside Git and demonstrates the concrete information-density
      outcome without inflated claims.
* [ ] Canonical documentation is reconciled.
* [ ] Implementation Report and Code Review are complete.
* [ ] Human Approval Gates are respected.
