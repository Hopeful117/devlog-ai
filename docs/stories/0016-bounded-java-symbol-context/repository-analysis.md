# Repository Analysis

## Story Understanding

Story 0016 requests an additive, deterministic Java declaration view for repository evidence that
has already passed global Repository Context selection. Its purpose is to make selected source and
test paths more useful to Kiko by exposing bounded type and executable declarations, annotations,
and precise revision/file provenance without asking Kiko to reconstruct that basic structure from
truncated excerpts.

The requested capability is syntactic, not semantic. It includes classes, interfaces, records,
enums, annotation declarations, constructors, methods, modifiers, parameter and return types, and
declaration annotations. It also includes explicit per-file extraction outcomes, deterministic
ordering and truncation, truthful token accounting, warnings, digest participation, compatible
GET/POST serialization, security boundaries, tests, operational validation, and documentation.

The Story explicitly excludes method bodies, behavioral interpretation, call/control/data-flow,
dependency graphs, external resolution, source-to-test inference, cross-file symbol linking,
embeddings, AI interpretation, non-Java languages, configuration content, persistence, larger
global limits, automatic project resolution, and workflow or agent changes.

## Repository Summary

The affected code is in the Java 21 Spring Boot backend. `RepositoryContextEngine` currently
constructs context through deterministic collection, ranking, diverse budgeted path selection, and
selected-file content enrichment. The result is serialized inside `EngineeringStoryContext` by the
existing GET and POST controller operations.

Stories 0014 and 0015 established the closest architectural seam. Only globally selected
`SOURCE_FILE` and `TEST_FILE` evidence is eligible for bounded content. The enrichment phase
resolves a project-owned repository source, synchronizes the exact revision recorded during
structure collection, reads a normalized confined path, records explicit status and policy
metadata, reconciles final token estimates and decisions, and contributes to the context digest.
The content allocation policy ranks eligible selected files independently of global selection and
currently permits content for at most six files.

No Java parser, symbol model, or declaration extractor exists in the backend. The Maven dependencies
contain no JavaParser, Eclipse JDT, or equivalent parsing library. The runtime container uses a Java
21 JRE image, not a JDK image; therefore an implementation based on the JDK compiler tree API would
introduce a runtime-image/module dependency that does not exist today. A regex or handwritten
partial parser would conflict with the Story's modern-Java and malformed-source requirements. A
maintained syntax parser that works without compiling or resolving repository dependencies is the
smallest compatible parser category to evaluate during planning; JavaParser is a direct candidate,
while Eclipse JDT would provide a broader but heavier compiler-oriented surface. No external
dependency resolution may occur while parsing repository files.

The DevLog-assisted inspection for this Story succeeded at revision
`15d3ebc586a7227a5c55034bc84a23ed8eb0a808`. It returned 59 candidates, 45 selected evidence items,
5,974 of 6,000 estimated tokens, a digest, and selected source/test paths. It correctly surfaced
`SelectedContentAllocationPolicy`, `DeterministicEvidenceRanker`,
`BudgetedDiverseEvidenceSelector`, `SelectedFileContentEnricher`, and their tests. All six allocated
content excerpts were truncated. It did not return ADR, Git-history, commit-diff, or project-
documentation candidates, so the governing ADRs and exact implementation contracts were verified
directly in the current repository. The synchronized revision equals the current Git `HEAD`; the
only working-tree addition at analysis time is the uncommitted Story 0016 directory.

## Affected Modules

### Backend — Repository Context domain

Package `com.hopeful117.devlogai.repositorycontext` owns the immutable evidence and final context
contracts. `RepositoryEvidence` currently carries path evidence, score, provenance, extraction
metadata, token estimate, ranking reasons, and optional `RepositoryEvidenceContent`. A structured
symbol result needs an additive domain representation associated with selected file evidence. Its
serialized fields and estimated tokens must participate in equality and the final digest.

### Backend — selected evidence enrichment

Package `com.hopeful117.devlogai.repositorycontext.enrichment` owns the post-selection content phase.
`SelectedFileContentEnricher` is the existing integration seam for selected-only, revision-pinned
file access. `SelectedContentAllocationPolicy` owns content-slot priority, and
`RepositoryContentPolicy` owns the current six-file/character limits. Symbol extraction must not
silently become another global ranker or change the selected candidate set. It should be a distinct,
versioned bounded concern even if implementation reuses the same source/workspace access path.

### Backend — repository collection and workspace safety

Packages `com.hopeful117.devlogai.collection.collector` and
`com.hopeful117.devlogai.collection.workspace` provide `SecureRepositoryContentReader`,
`CollectorLimits`, `WorkspaceManager`, and `SynchronizedWorkspace`. They implement the current
project/revision, path confinement, symlink, excluded-directory, file-size, UTF-8/binary, and read-
deadline boundaries. A symbol phase can reuse these principles and abstractions, but parsing a
truncated `RepositoryEvidenceContent.text` value is unsafe because a valid declaration may be cut
mid-token. The parser requires a separately bounded complete selected file input or an explicit
skipped outcome when the file cannot be safely supplied.

### Backend — context assembly

`RepositoryContextEngine` owns phase orchestration, final selected ordering, diagnostics, warnings,
token reconciliation, decisions, and SHA-256 context digest. It currently calls the content enricher
immediately after `BudgetedDiverseEvidenceSelector`. Symbol results must be incorporated after global
selection and before the final context is assembled, without weakening the 6,000-token ceiling or
making symbols affect global selection retroactively.

### Backend — Engineering Story Context API

Package `com.hopeful117.devlogai.projectcontext` exposes
`GET|POST /api/projects/{projectId}/engineering-story-context`. Both controller operations delegate
to the same service and return `EngineeringStoryContext`, which embeds `RepositoryContext` directly.
An additive evidence field therefore requires no new endpoint or transport service, but controller
serialization tests must prove GET/POST compatibility and structured symbol visibility.

### Backend build and runtime

`backend/pom.xml` owns parser dependencies and quality tooling. `backend/Dockerfile` builds with a
JDK but runs with `eclipse-temurin:21-jre-jammy`. Parser selection must be compatible with that
runtime unless an image change is explicitly justified. PostgreSQL entities and Flyway migrations
are not involved because symbol context is transient repository evidence.

### Documentation

`README.md`, the repository-context architecture documentation, configuration documentation,
roadmap, and an architectural decision are affected if the implementation introduces a new
versioned extraction phase or parser boundary. Historical Story artifacts must remain unchanged.

## Existing Implementation

`RepositoryStructureCollector` produces file candidates with source ownership, normalized
repository-relative path, and `resolvedRevision` extraction metadata. The ranker scores those path
facts, and `BudgetedDiverseEvidenceSelector` enforces the global evidence/item budget before any
content read.

`SelectedFileContentEnricher` filters the selected result to `SOURCE_FILE` and `TEST_FILE`, orders
those entries through `SelectedContentAllocationPolicy`, resolves each source through
`SourceRepository.findByIdAndProject_IdAndActiveTrue`, synchronizes its exact revision through
`WorkspaceManager`, and reads it through `SecureRepositoryContentReader`. It preserves path evidence
when a workspace or file is unavailable and emits `COMPLETE`, `TRUNCATED`, `SKIPPED`, or
`UNAVAILABLE` content states. Its metadata records policy/version, revision, allocation rank, and
bounded reasons.

`RepositoryEvidence.withContent` recalculates estimated tokens from content text and metadata.
`RepositoryContextEngine` then recalculates selection-decision estimates and `usedTokens`, emits
content warnings, and hashes the selected evidence, layers, policies, diagnostics, decisions, and
warnings. These are useful precedents, but no current contract accounts for structured symbols or
distinguishes malformed/unsupported source from general read failure.

The content limits are configured under `devlog.repository-context.content`: six enriched files,
4,000 characters per file, and 12,000 aggregate characters. Collection additionally uses a one MiB
file-size limit and a ten-second collector deadline. Story 0016 requires its own explicit symbol
limits rather than treating those text settings as an implicit symbol policy.

Existing tests already cover confined reads, symlink/path escape, binary/encoding and size handling,
workspace unavailability, content file/character/token limits, score-aware content allocation,
preservation of path evidence, deterministic Repository Context output, content-sensitive digests,
final token limits, selection decisions, and GET/POST serialization. Missing coverage is the symbol
model and extractor itself, Java syntax fixtures, symbol-specific outcomes and bounds, deterministic
symbol ordering/truncation, parser isolation, symbol-sensitive token/digest behavior, and regression
serialization with both content and symbols.

Behavior that must remain unchanged includes global path collection/ranking/selection, content
allocation ownership, configuration remaining content/symbol free, source/project ownership checks,
exact-revision synchronization, warning and fallback semantics, GET/POST inputs, evidence
provenance, and the total Repository Context budget.

## Relevant Documentation

* `README.md`
* `docs/decisions/ADR-037.md` — Repository-First Context Extraction
* `docs/decisions/ADR-038.md` — Repository Context Engine
* `docs/decisions/ADR-039.md` — Context Intelligence
* `docs/decisions/ADR-040.md` — Knowledge and Evidence Separation
* `docs/decisions/ADR-044.md` — Bounded Selected File Content Enrichment
* `docs/roadmap.md`
* `docs/stories/0014-bounded-selected-file-content/engineering-report.md`
* `docs/stories/0015-selected-content-allocation-precision/engineering-report.md`
* Engineering Story Repository Analysis workflow prompt

The repository does not contain `AGENTS.md` or the optional `docs/workflow/*` documents named by the
workflow prompt.

## Constraints

* Extraction must remain deterministic repository evidence under ADR-040, not trusted knowledge or
  AI interpretation.
* Only Java `SOURCE_FILE` and `TEST_FILE` evidence already selected globally may be considered.
  There may be no independent repository walk or pre-selection parsing.
* Symbols must not change candidate ranking or global selection retroactively.
* A selected Java file must receive an explicit symbol outcome even when extraction is skipped,
  unsupported, malformed, unavailable, empty, or fails individually.
* Symbol data must be structured, bounded, versioned, revision-traceable, deterministically ordered,
  and tied to the selected evidence reference and normalized file provenance.
* Parser input must be safe complete input within a symbol-specific size/time policy; a truncated
  display excerpt is not a valid parser input contract.
* Parsing must not execute repository code, annotation processors, compiler plugins, build scripts,
  or network dependency resolution.
* Path confinement, project source ownership, exact-revision synchronization, excluded directories,
  symlink rejection, regular-file checks, binary/encoding rejection, and interruption semantics must
  be preserved.
* Method bodies and unbounded source fragments must not enter the response. Explicit syntax may not
  be promoted into claims about behavior, calls, dependencies, wiring, or test relationships.
* Symbol metadata, status/reasons, warnings, decisions, `usedTokens`, and digest input must agree and
  remain within the existing maximum token budget.
* Existing content and symbol policies must have separate, clear ownership. Reusing content
  allocation must not make its six excerpt slots an undocumented symbol-selection rule.
* GET and POST remain backward compatible; response evolution must be additive.
* The backend runtime is presently a JRE image. A solution requiring `jdk.compiler` is not available
  in the deployed runtime without an explicit container architecture change.
* No database migration, frontend change, Engineering-Skills change, workflow change, or global
  limit increase belongs to this Story.
* Quality completion requires focused and complete backend tests, JaCoCo, authenticated SonarQube,
  Docker/API validation, an external disposable benchmark, and reconciled canonical documentation.

## Risks

* **Parser choice and Java-language coverage:** a partial parser can silently misrepresent nested
  declarations, records, annotations, generics, overloads, or malformed sources. A maintained
  syntax parser reduces this risk but adds a production dependency that must support the project's
  Java language level and JRE runtime.
* **Budget competition:** structured declarations can be verbose. If token estimates are incomplete,
  symbols can exceed the 6,000-token context or displace unrelated selected evidence after selection.
  Bounds must yield truthful partial/skipped outcomes rather than silently overflowing.
* **Duplicate file access and synchronization:** content and symbols need the same exact selected
  files and revision. Independent implementations could duplicate synchronization, file I/O, safety
  logic, and inconsistent failure states.
* **Truncated-input false failures:** feeding display excerpts to a parser would classify valid Java
  as malformed and omit declarations located after the excerpt boundary.
* **Semantic overstatement:** annotations and type names can tempt consumers to infer framework
  wiring or behavior that syntax alone does not prove.
* **Failure isolation:** parser exceptions, unsupported syntax, excessive nesting, or deadline expiry
  must not remove path/content evidence or fail the complete context request.
* **API compatibility and digest stability:** adding nested structured data changes JSON and digest
  inputs. Null/absent handling, ordering, equality, token estimation, and old response consumers need
  explicit regression coverage.
* **Benchmark bias:** the first benchmark uses a Java repository and one Story; it can validate
  declaration accuracy and navigation value but cannot establish autonomous analysis readiness.

## Open Questions

None.

Parser selection, the exact bounded V1 symbol fields, and whether a shared post-selection file-access
coordinator is warranted are technical design decisions that can be resolved during Implementation
Planning from the constraints above. They do not require a product or ownership decision before
planning.

## Recommendation

Ready for planning

The ownership and post-selection boundary are clear, the existing repository provides the required
revision-safe file access and context accounting seams, and no missing persistence or API contract
blocks the work. Planning must select a maintained syntax parser compatible with the Java 21 JRE
runtime, keep symbol policy distinct from content policy, and prevent duplicate or unsafe file
access while preserving the total context budget.

## Implementation Readiness

The Story can be implemented in the current repository. No missing domain ownership, source data,
API endpoint, database schema, or architectural prerequisite blocks planning. The parser dependency
and additive symbol contract do not yet exist, but creating those bounded components is the Story's
intended work rather than a prerequisite.

Repository Analysis completed.

Human approval required before Implementation Planning.

Awaiting explicit human approval.
