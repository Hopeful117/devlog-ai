git # Implementation Plan

## Overview

Implement Story 0016 as a new deterministic post-selection symbol-enrichment phase in the Java
backend. The phase will run after `BudgetedDiverseEvidenceSelector` and before bounded text
enrichment. It will consider only selected Java `SOURCE_FILE` and `TEST_FILE` evidence, order it
through a versioned symbol-allocation policy, safely read complete revision-pinned files within
symbol-specific bounds, parse declarations with JavaParser Core, and attach an additive structured
symbol result to each eligible selected evidence item.

The plan intentionally uses a syntax-only parser rather than the symbol-solver module. JavaParser
Core 3.27.0 is the current Maven Central release inspected during planning, explicitly supports a
Java 21 language level, and does not require the `jdk.compiler` module absent from DevLog's runtime
JRE image. The implementation will not configure type solving, classpaths, annotation processing,
compilation, plugins, or network resolution.

Symbol enrichment will receive a bounded share of the existing 6,000-token Repository Context
budget before text enrichment. This gives structured declarations priority over additional raw
excerpt characters without increasing the global limit. Existing content enrichment will then use
the remaining budget and retain its own policy, allocation metadata, security behavior, and
failure fallback. Final decisions, warnings, token estimates, and digest will continue to be
assembled by `RepositoryContextEngine` from the fully enriched evidence.

## Planned Changes

### 1. Add the maintained syntax-parser dependency

Add `com.github.javaparser:javaparser-core:3.27.0` to the backend Maven configuration through a
named property. Do not add `javaparser-symbol-solver-core` or compiler integrations. Configure the
extractor explicitly for Java 21 instead of relying on a mutable static parser default.

This choice supports the Story's modern Java declarations and malformed-source handling while
remaining compatible with the existing Java 21 JRE runtime. Dependency resolution occurs when
DevLog itself is built; extraction of a target repository must never resolve that repository's
dependencies or access the network.

### 2. Define the additive structured symbol contract

Introduce immutable repository-context records for a file-level symbol result and individual Java
declarations. The file result will contain:

* status: `EXTRACTED`, `NO_SUPPORTED_SYMBOLS`, `SKIPPED`, `UNSUPPORTED`, `UNAVAILABLE`, or `FAILED`;
* bounded reason when status is not `EXTRACTED`;
* symbol policy/extractor identity and version;
* exact synchronized revision;
* allocation rank and bounded allocation reasons;
* truncation flag and returned/available symbol counts when known;
* immutable ordered structured symbols.

Each symbol will contain only bounded declaration facts:

* declaration kind: class, interface, record, enum, annotation declaration, constructor, or method;
* simple name and bounded owning-type path for executable and nested declarations;
* deterministic modifier names;
* method return type where applicable;
* bounded ordered parameters containing declared type and name;
* bounded annotation names;
* begin/end line and column when the parser supplies a reliable range.

Generic/type text will be normalized from the parser AST and capped by policy. Annotation arguments,
comments, bodies, initializers, thrown-expression details, resolved types, calls, and behavioral
summaries will not be serialized.

Add an optional symbol-result field to `RepositoryEvidence` and a `withSymbols` copy operation.
Update token estimation so every serialized symbol status, reason, policy field, allocation reason,
location, and declaration string contributes to `estimatedTokens`. Preserve existing constructors
so existing collectors and tests remain source-compatible.

### 3. Introduce explicit symbol limits and validation

Add a versioned `RepositorySymbolPolicy` configuration under
`devlog.repository-context.symbols`. Use conservative defaults independent from content limits:

* maximum inspected files: 6;
* maximum input characters per file: 200,000, still subordinate to the existing one MiB collection
  file-size limit;
* maximum symbols per file: 40;
* maximum symbols across the response: 120;
* maximum characters for any emitted name/type/owner/signature component: 300;
* maximum aggregate symbol token allocation: 1,500;
* maximum parse duration per file: 500 ms;
* maximum aggregate symbol duration per request: 2 seconds.

All values must be positive and validated consistently with existing configuration-property
classes. The limits are defaults to validate through tests and the real benchmark; they do not
change the 60-item or 6,000-token global context budgets.

### 4. Reuse confined complete-file access

Extend the secure repository reader with a complete-text operation suitable for structured
extraction. It will reuse the existing normalization, repository-root confinement, excluded-path,
regular-file, symlink, size, binary, UTF-8, timeout, interruption, and failure behavior. Unlike the
display excerpt operation, it will return a deterministic skipped result when the decoded source
exceeds the symbol input-character limit; it must never return a truncated string as parser input.

Keep the existing excerpt-read contract unchanged. Factor shared safety/read logic only as needed to
avoid two divergent security implementations. The symbol enricher will continue to resolve sources
with `findByIdAndProject_IdAndActiveTrue` and synchronize the exact `resolvedRevision`; no working-
tree path or caller-supplied absolute path will be accepted.

Workspace lookup will be cached per symbol-enrichment request by source and revision, matching the
content enricher's request-local approach. A broader shared cache between symbol and content phases
is not required for V1 because `WorkspaceManager.synchronize` is revision-pinned and workspace-safe;
avoiding a stateful cross-request cache is more important than eliminating one bounded synchronization
call. Documentation and the ADR will record this trade-off.

### 5. Extract deterministic Java declarations

Create a focused `JavaDeclarationExtractor` with no Spring, repository, ranking, or API ownership.
It will accept complete bounded UTF-8 Java text plus policy/deadline context and return a structured
extraction outcome.

For each file it will:

* create an isolated JavaParser configured for Java 21;
* parse only the supplied compilation unit without symbol solving;
* treat unsuccessful/problematic parse results as `UNSUPPORTED` and return no partial declarations;
* collect top-level and nested supported type declarations, constructors, and methods;
* derive facts only from syntax nodes directly present in the compilation unit;
* normalize/cap individual strings and annotations;
* sort explicitly by source location, declaration category, owning type, name, and normalized
  parameter signature;
* apply per-file and aggregate symbol limits after deterministic ordering;
* return `NO_SUPPORTED_SYMBOLS` for a valid compilation unit without supported declarations;
* isolate unexpected parser/extractor exceptions as `FAILED`.

Run parsing inside a cancellable virtual-thread task with a per-file deadline and check the
aggregate request deadline before each file. Because cancellation cannot make arbitrary parser code
cooperatively interruptible, the strict complete-input size bound remains the primary CPU/memory
guard; timeout still produces an explicit outcome and prevents the request from waiting indefinitely.

### 6. Allocate and attach symbols after global selection

Create a versioned `SelectedJavaSymbolEnricher` and a symbol-specific allocation policy. Eligibility
requires selected `SOURCE_FILE` or `TEST_FILE` evidence, a normalized `.java` provenance path, a
repository source identifier, and a non-blank resolved revision.

Order eligible evidence deterministically using the same meaningful ranking signals established in
Story 0015—final score, uncapped semantic match strength, uncapped guidance match strength, then
reference—but expose a distinct symbol policy ID/version and rank. Do not reuse or mutate
`SelectedContentAllocationPolicy`; content and symbol allocation remain independently explainable.

The first six eligible entries are inspected subject to aggregate duration, symbol count, and symbol
token limits. Remaining eligible selected Java evidence receives `SKIPPED` with an explicit file-
limit, token-limit, symbol-limit, or duration-limit reason as applicable. Workspace/revision/file
failures map to `UNAVAILABLE`; unsafe, oversized, binary, unsupported-encoding, or policy-excluded
inputs map to `SKIPPED`; parser problems map to `UNSUPPORTED`; unexpected extraction failures map to
`FAILED`. Path and existing content evidence always remain present.

The enricher will reconcile evidence estimates and selection-decision estimates after every symbol
result. It must never exceed the lesser of the 1,500-token symbol allocation and remaining global
context capacity. If even status metadata cannot fit, preserve the original evidence and emit a
bounded symbol-metadata-budget warning rather than falsifying `usedTokens`.

### 7. Compose symbol and content enrichment in the context engine

Inject the symbol enricher into `RepositoryContextEngine` and run the phases in this order:

1. global collection, ranking, and diverse budgeted selection;
2. bounded selected Java symbol enrichment;
3. existing bounded selected-file content enrichment using the symbol-enriched selection and
   remaining global tokens;
4. final ordering, diagnostics, warnings, decisions, token accounting, and digest.

This ordering makes symbols an information-density layer rather than an addition after content has
already consumed nearly the entire budget. The content enricher requires no ranking responsibility
change: it receives higher base estimates and naturally allocates only the remaining budget.

Add distinct warnings for symbol truncation, file/symbol/token/duration limits, unsupported source,
unavailability, and extraction failure. Deduplicate warnings deterministically. Ensure the final
digest includes structured symbol results automatically through selected evidence serialization and
also includes symbol policy configuration/version where the context contract records enrichment
policies.

### 8. Preserve API compatibility and expose the new result

Keep both Engineering Story Context endpoint mappings and request semantics unchanged. Jackson will
serialize the optional symbol result inside each evidence item. Evidence without symbol eligibility,
including configuration and non-Java files, must omit/null the field and retain current JSON.

Update Web MVC fixtures to assert one structured extracted result, one skipped outcome, provenance,
policy/version, deterministic symbol order, and continued content/allocation fields. Preserve shared
error responses and large POST-body behavior.

### 9. Document the architectural boundary and configuration

Create ADR-045 for bounded selected Java symbol enrichment because it adds a parser dependency, a
new post-selection phase, and a deliberate symbol-before-content budget order. Update `README.md`,
`docs/roadmap.md`, application configuration documentation/comments, and relevant architecture/API
sections to describe:

* selected-only deterministic Java declaration extraction;
* exact revision and file provenance;
* symbol statuses, limits, warnings, and additive response shape;
* the syntax-only trust boundary;
* Java-only V1 support;
* continued need for direct repository verification of behavior, relationships, and current working
  tree state.

Do not rewrite ADR-044 or historical Story reports; reference them from the new decision.

### 10. Validate with automated, operational, and benchmark evidence

Run focused tests first, then the complete backend quality baseline. Rebuild the Docker backend and
issue a real Engineering Story Context POST using the complete Story 0016. Store benchmark request,
response, timings, and observations outside Git, compare declarations with targeted direct reads,
and report only the single-run facts required by AC-14 in the normal implementation/review artifacts.

## Files to Modify

* `backend/pom.xml` — JavaParser Core version property and runtime dependency.
* `backend/src/main/resources/application.properties` — bounded symbol-policy defaults.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryEvidence.java` — optional
  symbol result and truthful token estimation.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextEngine.java` —
  post-selection phase composition, warnings, policy/digest reconciliation.
* `backend/src/main/java/com/hopeful117/devlogai/collection/collector/SecureRepositoryContentReader.java`
  — complete bounded text-read operation sharing existing confinement.
* `backend/src/test/java/com/hopeful117/devlogai/collection/collector/SecureRepositoryContentReaderTest.java`
  — complete-read safety, limits, and unchanged excerpt behavior.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextServiceTest.java` —
  symbol/content phase order, accounting, decisions, warnings, determinism, and digest.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/enrichment/SelectedFileContentEnricherTest.java`
  — compatibility when evidence already carries symbol metadata and remaining tokens are reduced.
* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextControllerWebMvcTest.java`
  — additive symbol serialization with GET/POST compatibility.
* `README.md` — capability, API example, configuration, trust model, and limitations.
* `docs/roadmap.md` — implemented repository-memory capability after completion.

Additional existing repository-context tests may be adapted only where constructor fixtures require
the additive field or final token estimates change.

## Files to Create

Expected new components, with final names allowed to follow package conventions:

* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryEvidenceSymbols.java` —
  immutable file-level outcome and structured Java declaration contract.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/enrichment/RepositorySymbolPolicy.java`
  — validated symbol limits and policy identity/version.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/enrichment/SelectedSymbolAllocationPolicy.java`
  — deterministic symbol-file priority and explainability.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/enrichment/JavaDeclarationExtractor.java`
  — JavaParser-based syntax-only declaration extraction.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/enrichment/SelectedJavaSymbolEnricher.java`
  — selected evidence, workspace, budget, outcome, and failure orchestration.
* Corresponding unit tests for the policy, allocation policy, extractor, and selected symbol
  enricher under `backend/src/test/java/.../repositorycontext/enrichment/`.
* Generic Java fixture resources under `backend/src/test/resources/repositorycontext/symbols/` for
  modern declarations, nesting, overloads, annotations, no-symbol, and malformed cases.
* `docs/decisions/ADR-045.md` — bounded selected Java symbol enrichment decision.

## Dependencies

### External dependency

* `com.github.javaparser:javaparser-core:3.27.0` — syntax-only Java 21 parsing. The version was
  verified against Maven Central and its parser configuration exposes `JAVA_21`. The symbol-solver
  artifact is deliberately excluded.

### Internal dependencies

* Global selection remains owned by `DeterministicEvidenceRanker` and
  `BudgetedDiverseEvidenceSelector`.
* File identity/revision comes from repository-structure evidence provenance and extraction
  metadata.
* Source ownership and synchronization use `SourceRepository` and `WorkspaceManager`.
* Safe complete input uses `SecureRepositoryContentReader` and `CollectorLimits`.
* Symbol ordering uses a separate versioned allocation policy but the typed score strengths already
  present on `RepositoryEvidence`.
* Content enrichment runs after symbols and must consume the remaining total token budget.
* `RepositoryContextEngine` remains the sole owner of final warnings, decisions, token accounting,
  and digest construction.

No database, frontend, AI Engine, Engineering-Skills, OpenClaw, or repository-under-analysis
dependency is added.

## Test Plan

### Extractor tests

Create generic fixtures and tests covering:

* top-level and nested classes, interfaces, records, enums, and annotation declarations;
* constructors, overloaded methods, abstract/default/static methods, test methods, modifiers,
  parameters, return types, generics, and annotations;
* bounded annotation/type/name normalization without bodies or comments;
* explicit owning-type paths and source ranges;
* deterministic ordering independent of parser traversal collection;
* per-file truncation after deterministic sorting;
* valid package/import-only and empty files producing `NO_SUPPORTED_SYMBOLS`;
* malformed and unsupported source producing `UNSUPPORTED` without partial symbols;
* unexpected extractor failure and deadline behavior.

These tests primarily validate AC-2, AC-4, AC-7, AC-10, and AC-11.

### Policy and allocation tests

Verify positive configuration validation, the declared default bounds, final-score/semantic/
guidance/reference ordering, deterministic ranks and reasons, file/symbol/token/duration limits, and
stable tie-breaking. These tests validate AC-1, AC-8, and AC-10.

### Secure input and enricher tests

Extend reader tests for complete bounded UTF-8 input and explicit oversized-input rejection while
retaining traversal, symlink, excluded-directory, regular-file, one-MiB, binary, encoding, timeout,
and interruption behavior.

Test selected symbol enrichment for:

* only selected `.java` source/test evidence being inspected;
* configuration, non-Java, and unselected candidates remaining symbol-free;
* exact revision/source/project lookup and normalized originating file;
* extracted, no-symbol, skipped, unsupported, unavailable, and failed outcomes;
* file-limit statuses on lower-priority selected Java evidence;
* individual failure preserving path/content evidence and allowing later files;
* aggregate symbol/token/duration bounds;
* final evidence and selection-decision token estimates matching;
* stable results under repeated identical input.

These tests validate AC-1, AC-3, AC-5, AC-6, AC-9, AC-10, AC-11, and AC-12.

### Engine and API regression tests

Update `RepositoryContextServiceTest` to prove symbols run only after global selection, cannot alter
the selected references, consume at most their bounded token share, leave room for existing content,
remain under 6,000 total tokens, update decision estimates, emit bounded warnings, and change the
digest when declaration facts change. Repeat identical inputs to verify the same ordered response and
digest.

Update `EngineeringStoryContextControllerWebMvcTest` to verify additive structured symbol outcomes,
locations, provenance/policy fields, truncation metadata, and continued GET/POST, large-story,
content, error, and null/blank behavior. These tests validate AC-8, AC-9, AC-10, and AC-12.

### Validation commands

From `backend/`:

* focused Maven tests for the parser, symbol policy/allocation/enricher, secure reader, content
  enricher, Repository Context service, selector/ranker, repository structure, and controller;
* `./mvnw test`;
* `./mvnw verify` for JaCoCo's 80% line-coverage rule;
* authenticated `./mvnw verify sonar:sonar -Dsonar.qualitygate.wait=true` using the existing local
  non-versioned token configuration.

Operationally:

* rebuild and restart the backend through the existing Docker Compose workflow;
* verify service health and both Engineering Story Context transports;
* POST the complete Story 0016, assert non-empty selected Java symbol outcomes, a truthful token
  count at or below 6,000, selection decisions, digest, and expected warnings;
* keep benchmark payloads and observations under `/tmp/devlog-story-0016-benchmark/` or another
  location outside every Git working tree;
* directly inspect the files represented by returned symbols to record accuracy, omissions,
  remaining native reads, and whether the symbols add useful structure beyond truncated excerpts.

Expected success means all focused and complete tests pass, JaCoCo remains at least 80%, SonarQube
reports a passing Quality Gate with no new unresolved issue, Docker/API validation succeeds, and the
single benchmark reports factual declaration accuracy without claiming general productivity gains.

## Risks

* **Parser compatibility:** JavaParser Core could reject newer or unusual syntax. Pinning 3.27.0,
  configuring Java 21 explicitly, failing closed on parse problems, and testing representative
  fixtures prevents silent partial truth.
* **Runaway or expensive parsing:** deadlines alone cannot guarantee interruption inside arbitrary
  parser code. The complete-input size bound, six-file limit, aggregate request deadline, isolated
  virtual-thread tasks, and explicit timeout outcomes limit exposure.
* **Token starvation of content:** symbol statuses and declarations run before content. The separate
  1,500-token symbol ceiling, per-file/global symbol caps, truthful estimates, and content regression
  tests preserve a meaningful remaining context budget.
* **Duplicate synchronization/I/O:** symbol and content phases may independently resolve the same
  revision. Request-local caches bound duplication and preserve stateless components; a shared
  cross-phase access coordinator is intentionally deferred unless implementation demonstrates that
  duplication breaks the duration requirements.
* **False semantic confidence:** structured syntax may look authoritative beyond its scope. The
  contract omits resolved relationships and bodies, and documentation will state that repository
  verification remains mandatory for behavior.
* **API fixture churn:** adding a record component can affect equality and hand-built test fixtures.
  Backward-compatible constructors and focused serialization tests reduce unnecessary broad edits.
* **Outcome ambiguity:** reader, parser, policy, and unexpected failures can otherwise collapse into
  one status. A documented mapping with bounded machine-readable reasons keeps fallback explainable.
* **Single-run benchmark bias:** the benchmark will validate only this repository/revision/Story and
  will be reported as evidence of concrete declaration utility, not proof of autonomous analysis.

## Validation Checklist

* [ ] JavaParser Core is pinned and configured for Java 21; no symbol solver, compiler plugin,
      target-repository build, or network resolution is used during extraction.
* [ ] Only globally selected Java `SOURCE_FILE` and `TEST_FILE` evidence is considered.
* [ ] Type and executable declarations are structured and contain only approved bounded syntax
      facts.
* [ ] Every eligible selected Java file receives an explicit extracted/no-symbol/skipped/
      unsupported/unavailable/failed outcome within metadata budget.
* [ ] File, revision, repository source, evidence reference, extractor, policy, allocation, and
      source-location provenance is present and truthful.
* [ ] Complete parser input preserves path, symlink, directory, file-size, binary, UTF-8, deadline,
      project ownership, and exact-revision boundaries.
* [ ] No truncated excerpt is parsed and no repository code or dependency is executed/resolved.
* [ ] File, input, symbol, component-string, token, per-file duration, and aggregate duration limits
      are deterministic and tested.
* [ ] Symbol ordering and truncation use explicit comparators and remain stable under repeated input.
* [ ] Symbols do not alter global ranking/selection and use no opaque second global ranker.
* [ ] Symbol allocation remains distinct from content allocation and consumes at most 1,500 of the
      existing 6,000 total tokens.
* [ ] Existing bounded content remains functional with the remaining budget and retains allocation
      metadata.
* [ ] Configuration and non-Java evidence remain symbol-free.
* [ ] Individual read/parser failures preserve path/content evidence and do not fail the request.
* [ ] Selection-decision estimates, evidence estimates, `usedTokens`, warnings, and digest reflect
      the final enriched response.
* [ ] GET and POST request contracts and shared errors remain compatible; symbol JSON is additive.
* [ ] Generic fixtures cover all Story-required declaration and failure cases.
* [ ] Focused tests, complete backend tests, JaCoCo verification, authenticated SonarQube Quality
      Gate, Docker/API validation, and the external benchmark succeed.
* [ ] README, ADR-045, roadmap, configuration, API, and operational documentation are reconciled
      honestly without rewriting historical artifacts.
* [ ] No dependency graph, source/test inference, cross-file resolution, method behavior, AI,
      persistence, frontend, workflow, project-resolution, or global-limit expansion enters scope.

## Recommendation

Ready for implementation

The parser, post-selection placement, bounded contract, budget order, failure mapping, validation
strategy, and compatibility constraints are sufficiently defined. No unresolved product or
architectural decision blocks implementation.

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.
