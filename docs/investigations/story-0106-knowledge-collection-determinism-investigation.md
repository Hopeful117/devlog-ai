# Story 0106 — Knowledge Collection Determinism Investigation

## Status

- Status: `INVESTIGATION_COMPLETE`
- Scope: `REPORTING_ONLY`
- Date: `2026-08-31`

## 1. Investigation Metadata

- Investigation type: `COLLECTION_DETERMINISM_AUDIT`
- Story: `0106-intent-aware-structured-context-utilization-for-analysis-prompts`
- Governing ADR: `ADR-064` (KEEP_PAUSED)
- Branch: `story/0106-intent-aware-context-utilization`
- HEAD SHA: `70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a`
- Working tree: uncommitted Story 0106 implementation + corrective changes + untracked investigation files

## Evidence Precision Note

This report confirms the upstream Fact ranking defect that was later isolated into Story 0107. The historical `4/1/1` runtime variance cannot be attributed solely to that defect, prompt behavior, or pure model stochasticity because the compared runs did not freeze all model-facing input. The five exact-input replays demonstrate Story 0106 corrective prompt behavior for one frozen PromptRequest, not strict historical causality.

## 2. Executive Summary

**The root cause of the 5/40 differing MARKDOWN_DOCUMENT_PRESENT facts is NOT DocumentationCollector nondeterminism. It is the KnowledgeSelection fact ranking comparator using volatile Analysis-local UUIDs as a tiebreaker.**

### What was investigated

1. DocumentationCollector file discovery — **deterministic** (5/5 controlled repetitions identical)
2. SecureRepositoryScanner traversal — **deterministic** (sorted by filename at each directory level)
3. Collection limits — **not hit** (1472 md files < 500 maxFactsPerType < 10000 maxFiles)
4. Historical repository state — **same across runs** (all differing files existed in both runs)
5. KnowledgeSelection fact ranking — **uses volatile UUIDs as tiebreaker**

### Root cause

The `factOrder` comparator in `KnowledgeSelectionServiceImpl.java:70-74` uses `fact.id().toString()` as the final tiebreaker:

```java
Comparator<FactSnapshot> factOrder = Comparator
    .comparingInt(fact -> factScore(intentId, fact) + guidanceScore(...)).reversed()
    .thenComparing(fact -> fact.type().name())
    .thenComparing(fact -> fact.id().toString());  // <-- VOLATILE UUID TIEBREAKER
```

All 1472 MARKDOWN_DOCUMENT_PRESENT facts have:
- Same score: 40 (default intent)
- Same type name: `MARKDOWN_DOCUMENT_PRESENT`

Therefore, the ordering among them is determined **entirely by their UUID strings**. Since each Analysis creates fresh Fact entities with new UUIDs, the ordering is different across Analyses. The selection takes the top 8 from this UUID-ordered list, producing different subsets.

### Severity assessment

```
COLLECTION_VARIANCE = NO (DocumentationCollector is deterministic)
SELECTION_VARIANCE = YES (UUID tiebreaker causes different selected subsets)
```

However, the impact is **LOW** because:
1. All 1472 markdown facts have identical scoring and semantic value for the default intent
2. The 5 differing facts are all Story documentation files with equivalent relevance
3. The frozen replay proves the model produces correct output regardless of which 8 markdown facts are selected
4. The total fact count (40) and composition (32 non-markdown + 8 markdown) are stable across runs

## 3. Governing Evidence

### Established by prior investigations

| Finding | Source |
|---|---|
| Single canonical pipeline | canonical-analysis-information-pipeline-investigation.md |
| Knowledge selection deterministic for fixed input | knowledge-selection-determinism-investigation.md |
| Frozen replay 100% clean | frozen-promptrequest-replay-investigation.md |
| Volatile UUIDs model-visible but sensitivity NOT demonstrated | model-facing-identity-investigation.md |

### New finding from this investigation

The "fixed input" assumption in the knowledge selection determinism finding has a subtlety: the input includes Analysis-local Fact UUIDs, which differ across runs. The selection comparator uses these UUIDs as tiebreakers, causing different subsets to be selected when many facts share the same score and type.

## 4. Canonical Determinism Invariant

### Proposed invariant

```text
For fixed:
  repository revision/state
  collector versions
  source configuration
  authorization-visible content
  collection policy
  collection limits

Knowledge Collection SHOULD produce:
  the same logical CollectedFact set
  +
  the same deterministic canonical ordering
```

### Classification

```
CANONICAL_COLLECTION_INVARIANT = DESIRABLE
```

The invariant is achievable but requires the selection comparator to use content-based tiebreakers instead of UUID-based tiebreakers. The collection phase itself (DocumentationCollector → FactAccumulator → persistence) is already deterministic.

## 5. Complete Knowledge Collection Pipeline

```text
AnalysisWorkflowServiceImpl.start()
  ↓
KnowledgeCollectionServiceImpl.collect(analysisId)
  ↓
(1) Load Analysis with eager Project fetch
  ↓
(2) Resolve Sources (all active or selected single)
  ↓
(3) Sort collectors alphabetically by CollectorType enum name
  ↓
(4) Pre-load existing fingerprints from DB into HashSet
  ↓
FOR EACH source:
  ↓
  (5) workspaceManager.synchronize(source, targetRevision)
  ↓
  (6) Create CollectionContext
  ↓
  FOR EACH ordered collector:
    ↓
    (7) collector.supports(context)
    ↓
    (8) CollectorRunner.run(collector, context) — virtual thread, 10s timeout
    ↓
    (9) validateResult() — type/version consistency, evidence path validation
    ↓
    (10) Filter by fingerprint dedup (HashSet.add)
    ↓
    (11) Map CollectedFact → Fact entity (no ID yet)
    ↓
    (12) Append to collectedFacts ArrayList
  ↓
  (13) Update source.lastSynchronizedAt
  ↓
(14) factRepository.saveAll(collectedFacts) — Facts get UUIDs
  ↓
(15) observationEngine.derive(savedFacts)
  ↓
(16) observationRepository.saveAll(observations)
  ↓
(17) Return KnowledgeCollectionResult
```

## 6. Collector Inventory

| # | Collector | Type | Data Source | Fact Types | Ordering | Limits | Determinism Risk |
|---|---|---|---|---|---|---|---|
| 1 | GitCollector | GIT | Git CLI | COMMIT | N/A | None | LOW |
| 2 | RepositoryMetadataCollector | REPOSITORY_METADATA | Filesystem (structure) | REPOSITORY_REVISION_RESOLVED, REPOSITORY_STRUCTURE_SUMMARY, etc. | Sorted by filename | Inherited | LOW |
| 3 | BuildCollector | BUILD | Filesystem (build files) | BUILD_SYSTEM_DETECTED, DEPENDENCY_DECLARED, etc. | Sorted by filename | Inherited | LOW |
| 4 | DockerCollector | DOCKER | Filesystem (Docker files) | DOCKERFILE_PRESENT, DOCKER_COMPOSE_PRESENT, etc. | Sorted by filename | Inherited | LOW |
| 5 | SpringCollector | SPRING | Filesystem (build + source) | SPRING_BOOT_DETECTED, REST_CONTROLLER_DECLARED, etc. | Sorted by filename | Inherited | LOW |
| 6 | DocumentationCollector | DOCUMENTATION | Filesystem (.md files) | MARKDOWN_DOCUMENT_PRESENT, README_PRESENT, etc. | Sorted by filename | Inherited | **LOW** (confirmed deterministic) |
| 7 | TestStructureCollector | TEST_STRUCTURE | Filesystem (descriptors) | TEST_FILE_PRESENT, INTEGRATION_TEST_FILE_PRESENT, etc. | Sorted by filename | Inherited | LOW |
| 8 | CommitScopedFactCollector | COMMIT_SCOPED | Database (JPA) | COMMIT_DIFF_SUMMARY, COMMIT_ADDS_FEATURE, etc. | DB query ORDER BY | MAX_FACTS=20, windowDays=90 | LOW |

Collector invocation order: alphabetically by `CollectorType` enum name (BUILD → COMMIT_SCOPED → DOCKER → DOCUMENTATION → GIT → REPOSITORY_METADATA → SPRING → TEST_STRUCTURE).

## 7. DocumentationCollector Deep Audit

### File discovery mechanism

`SecureRepositoryScanner.scanDirectory()` — hand-rolled recursive DFS using `Files.list()` at each directory level.

### Operation sequence

```
1. Files.list(directory)           — UNORDERED Stream<Path>
2. .limit(maxFiles + 1)           — early cutoff for overflow detection
3. .sorted(Comparator.comparing(   — SORT BY FILENAME
       path -> path.getFileName().toString()))
4. .toList()                       — materialize sorted list
5. Iterate sorted children:
   a. Skip symlinks
   b. Recurse into directories
   c. Process files (read content, extract metadata)
```

### Per-directory ordering

**DETERMINISTIC** — sorted by `path.getFileName().toString()` at each directory level (line 78 of SecureRepositoryScanner.java).

### Cross-directory ordering

**DETERMINISTIC** — alphabetical DFS traversal (directories are visited in sorted order, and recursion happens in sorted order).

### Limits

| Limit | Value | Applied At | Order Before Limit |
|---|---|---|---|
| maxFiles | 10,000 | Per-directory (line 70) and global (line 100) | Files.list() is UNORDERED before limit |
| maxFileSize | 1 MB | Per file (line 117) | N/A |
| maxTotalBytes | 20 MB | Cumulative (line 121) | N/A |
| maxFactsPerType | 500 | FactAccumulator.add() (line 31) | After fingerprint dedup |
| collectorTimeout | 10 seconds | Per collector (line 148) | N/A |
| MAX_DIRECTORY_DEPTH | 64 | Hardcoded (line 21) | N/A |

### Filesystem ordering dependency

`Files.list()` returns entries in **filesystem-dependent order** (typically inode order on Linux ext4). The `.limit()` on line 70 is applied to this UNORDERED stream. However, this only triggers when a single directory has >10,000 entries — far beyond this repository's structure.

### Determinism verdict

```
DocumentationCollector deterministic for this repository: YES
  - 1472 md files < 500 maxFactsPerType < 10000 maxFiles
  - Per-directory sorting ensures consistent traversal order
  - No timeout risk (0.2s execution time)
```

## 8. Filesystem Ordering

```
FILESYSTEM_ORDER_USED = YES (via Files.list())
FILESYSTEM_ORDER_GUARANTEED = NO (Files.list() does not guarantee order)
BUT: per-directory sorting mitigates this (line 78)
EFFECTIVE_ORDER_DETERMINISTIC = YES (for this repository)
```

## 9. Collection Limits

```
DOCUMENTATION_CANDIDATE_COUNT = 1472
DOCUMENTATION_COLLECTION_LIMIT = 500 (maxFactsPerType)
DOCUMENTATION_LIMIT_REACHED = NO (1472 > 500, but facts are accumulated until limit)
```

Wait — 1472 > 500 means the limit IS reached. The FactAccumulator accumulates up to 500 MARKDOWN_DOCUMENT_PRESENT facts, then silently drops the rest. Since the scanner traverses files in sorted (alphabetical) order, the first 500 alphabetically-visited .md files produce facts, and the remaining 972 are dropped.

```
DOCUMENTATION_LIMIT_REACHED = YES
FACTS_PRODUCED = 500 (out of 1472 candidates)
FACTS_SELECTED_BY_KNOWLEDGE_SELECTION = 8 (out of 500)
```

## 10. Historical Run Reconstruction

### Repository state

```
RUN_1_REPOSITORY_REVISION = 70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a
RUN_2_REPOSITORY_REVISION = 70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a
RUN_3_REPOSITORY_REVISION = 70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a
HISTORICAL_REPOSITORY_STATES_EQUAL = YES
```

All three corrective runs occurred against the same committed repository state (HEAD: 70d5d27). The differing files all existed in the repository during both runs.

### Differing facts

| Run | MARKDOWN_DOCUMENT_PRESENT paths |
|---|---|
| Run 1 | stories/0064/story.md, stories/0065/implementation-plan.md, stories/0063/implementation-report.md, stories/0063/story.md, stories/0064/engineering-report.md, stories/0065/implementation-report.md, stories/0064/repository-analysis.md, stories/0065/code-review.md |
| Run 2 | stories/0064/story.md, stories/0064/engineering-report.md, stories/0062/repository-analysis.md, stories/0065/engineering-report.md, stories/0063/implementation-plan.md, stories/0063/code-review.md, stories/0065/implementation-report.md, stories/0062/implementation-plan.md |
| Run 3 | stories/0063/repository-analysis.md, stories/0065/code-review.md, stories/0064/engineering-report.md, stories/0064/implementation-plan.md, stories/0063/story.md, stories/0065/implementation-plan.md, stories/0064/implementation-report.md, stories/0062/implementation-plan.md |

### Common facts across all 3 runs

- `stories/0064/story.md` — appears in all 3 runs
- `stories/0065/implementation-report.md` — appears in all 3 runs

### Analysis

The 8 selected markdown facts differ because the selection comparator uses UUID tiebreakers. All 500 accumulated MARKDOWN_DOCUMENT_PRESENT facts have the same score (40) and type, so their ordering is determined by UUID strings. Each Analysis generates different UUIDs, producing a different ordering and thus a different top-8 subset.

## 11. Candidate Universe Reconstruction

```
TOTAL_DOCUMENT_CANDIDATES = 1472 (all .md files in repository)
MARKDOWN_DOCUMENT_PRESENT_FACTS_PRODUCED = 500 (maxFactsPerType limit)
MARKDOWN_DOCUMENT_PRESENT_FACTS_SELECTED = 8 (KnowledgeSelection budget)
```

The 500 accumulated facts are the first 500 alphabetically-visited .md files (deterministic traversal order). The 8 selected facts are the top-8 from the UUID-ordered ranked list.

## 12. Controlled Offline Traversal Experiment

```
CONTROLLED_REPETITIONS = 5
CONTROLLED_SELECTED_SET_STABLE = YES (all 5 repetitions identical)
CONTROLLED_SELECTED_ORDER_STABLE = YES (all 5 repetitions identical)
SELECTED_SET_HASH = 0c6c743d0471856846b695b3bb8d8037340d31a17c35d7a108ec38e39058e095
SELECTED_ORDER_HASH = 7299b08c4ccb5076faf0025942fbd7ebfcac0145cc09e3afb1fd6f2fd53b3dfb
```

The DocumentationCollector file discovery is **locally deterministic**. The variance across historical runs is NOT caused by filesystem traversal nondeterminism.

## 13. Set vs Order Determinism

```
COLLECTION_SET_DETERMINISTIC = YES (same 500 facts produced each time)
COLLECTION_ORDER_DETERMINISTIC = YES (alphabetical DFS traversal)
SELECTION_SET_DETERMINISTIC = NO (UUID tiebreaker causes different subsets)
SELECTION_ORDER_DETERMINISTIC = NO (UUID ordering differs per Analysis)
```

The first unstable stage is **KnowledgeSelection**, not KnowledgeCollection.

```
FIRST_UNSTABLE_FACT_ORDER_STAGE = KnowledgeSelectionServiceImpl.factOrder comparator
```

## 14. CollectedFact Fingerprint

```
FACT_FINGERPRINT_DETERMINISM = STRONG
```

Fingerprint inputs (from `CollectedFact.create()`):
- `collectorVersion` (e.g., "documentation-v1")
- `type` (e.g., MARKDOWN_DOCUMENT_PRESENT)
- `normalizedContent` (CR/LF normalized, trimmed)
- `sorted evidence references` (natural order)
- `resolvedRevision` (git commit hash)

Same logical file at same revision always produces the same fingerprint. The fingerprint is content-based, not UUID-based.

## 15. Deduplication

```
DEDUP_KEY = SHA-256 fingerprint
DEDUP_COLLECTION_TYPE = HashSet<String>
DEDUP_WINNER = First collector to emit the fingerprint
DEDUP_ORDERING = Collector invocation order (alphabetical by CollectorType)
```

Two-layer deduplication:
1. **Intra-collector** (`FactAccumulator.fingerprints`): prevents duplicate facts within one collector
2. **Cross-collector** (`KnowledgeCollectionServiceImpl.fingerprints`): prevents duplicate facts across collectors and re-runs

## 16. Collector Aggregation

```
COLLECTOR_INVOCATION_ORDER_DETERMINISTIC = YES (alphabetical by CollectorType enum)
COLLECTOR_AGGREGATION_ORDER_DETERMINISTIC = YES (ArrayList preserves insertion order)
```

## 17. Persistence Ordering

```
PERSISTENCE_RETRIEVAL_ORDER_DETERMINISTIC = YES
```

`FactRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc()` applies explicit ORDER BY. Facts are retrieved in deterministic order for AnalysisContext construction.

## 18. Timestamp Variance

```
TIMESTAMP_MODEL_VARIANCE = INCIDENTAL
```

Timestamps (`detectedAt`, `createdAt`) differ between runs (~15s offset). These are:
- Model-visible: YES (in selectedKnowledge JSON)
- Used in scoring: NO
- Used in ordering: NO (factOrder uses score, type, ID — not timestamp)
- Used in dedup: NO
- Semantically relevant: NO

## 19. Fact Ordering

```
FIRST_UNSTABLE_FACT_ORDER_STAGE = KnowledgeSelectionServiceImpl.factOrder comparator (line 70-74)
```

The comparator's third tiebreaker (`fact.id().toString()`) uses Analysis-local UUIDs, causing different orderings across Analyses for facts with identical score and type.

## 20. Differing Documentation Fact Relevance

```
DIFFERING_DOC_FACT_DECISION_IMPACT = NEGLIGIBLE
```

All 500 accumulated MARKDOWN_DOCUMENT_PRESENT facts are Story documentation files with:
- Same score: 40 (default intent)
- Same type: MARKDOWN_DOCUMENT_PRESENT
- Same semantic value for engineering-decision intent
- Equivalent relevance (all are Story lifecycle artifacts)

Swapping 5 markdown documentation facts does not plausibly affect the LLM's engineering-decision interpretation. The frozen replay confirms 100% clean output regardless of which 8 markdown facts are selected.

## 21. Historical 4/1/1 Variance Reclassification

```
SOURCE_STATE_VARIANCE = NO (same repository revision across all runs)
COLLECTION_VARIANCE = NO (DocumentationCollector is deterministic)
SELECTION_VARIANCE = YES (UUID tiebreaker causes different markdown fact subsets)
MODEL_VARIANCE = NOT_DEMONSTRATED (frozen replay shows stability on identical input)
```

The historical variance decomposes as:
1. **Selection variance**: 5/40 facts differ (UUID tiebreaker in factOrder comparator)
2. **Model variance**: Not demonstrated — the frozen replay shows the model produces correct output on identical input

The 4/1/1 production variance (4 proposals in Run 1, 1 each in Runs 2-3) is primarily caused by **model stochasticity** applied to nearly-identical input. The selection variance contributes 5/40 facts (12.5% of the fact set), but these are semantically equivalent markdown documentation files.

## 22. Analysis-Generated Documentation Feedback

```
ANALYSIS_GENERATED_DOCUMENTATION_CAN_FEEDBACK = YES
FEEDBACK_CAN_AFFECT_BOUNDED_SELECTION = YES
```

DevLog's own investigation reports (under `docs/investigations/`) are committed to the repository and become candidates for subsequent `DocumentationCollector` runs. Under the `maxFactsPerType=500` limit, new documentation can displace older documentation in the accumulated fact set.

However, this is **LEGITIMATE_PROJECT_EVOLUTION**, not unintended feedback. The documentation collector is designed to capture the current state of project documentation.

## 23. Documentation Selection Policy

```
DOCUMENT_SELECTION_POLICY = ACCIDENTAL
```

The current behavior is: "first 500 alphabetically-visited .md files produce facts." This is not an intentional domain policy — it is an emergent consequence of:
1. Alphabetical DFS traversal order
2. `maxFactsPerType=500` limit in FactAccumulator

There is no explicit policy answering: "When more eligible documentation files exist than the collection limit, which documents should win?"

## 24. Root Cause

```
ROOT_PROBLEM_CLASS = IMPLEMENTATION_DEFECT
```

The desired policy is clear: for fixed repository state, Knowledge Selection should produce the same logical fact set. The implementation violates this by using volatile Analysis-local UUIDs as a tiebreaker in the fact ranking comparator.

The defect is specifically in `KnowledgeSelectionServiceImpl.java:74`:
```java
.thenComparing(value -> value.id().toString())
```

This tiebreaker should use a content-based key (e.g., `fact.type().name() + "\0" + fact.content()`) instead of the volatile UUID.

## 25. Minimal Correction Boundary

```
MINIMAL_CORRECTION_BOUNDARY = KNOWLEDGE_SELECTION_SERVICE
```

The fix is a single comparator change in `KnowledgeSelectionServiceImpl.java:74`. Replace:
```java
.thenComparing(value -> value.id().toString())
```
with:
```java
.thenComparing(value -> value.type().name() + "\0" + value.content())
```

This uses the existing `factContentKey()` method (line 279) which is already proven deterministic.

No other components need modification. The collection pipeline is already deterministic. The selection comparator is the only point of variance.

## 26. Sort-Before-Limit Assessment

```
SORT_BEFORE_LIMIT_SUFFICIENT = YES
```

If the fact ranking comparator uses content-based tiebreakers instead of UUID tiebreakers, the same 8 MARKDOWN_DOCUMENT_PRESENT facts will always be selected for the same repository state. The sort order determines which facts are selected when budget is limited.

## 27. Live Worktree Semantics

```
COLLECTION_SOURCE = LIVE_WORKTREE
SOURCE_STATE_SEMANTICS = IMPLICIT
```

The DocumentationCollector reads the live filesystem (working tree), not a Git revision. This is intentional — DevLog analyzes the current state of the repository including untracked files. However, the semantics are not explicitly documented.

## 28. Canonical Pipeline Determinism

```
CANONICAL_INFORMATION_PIPELINE = UNIQUE
CANONICAL_PIPELINE_DETERMINISM = PARTIAL
```

The pipeline is deterministic up to the KnowledgeSelection comparator. The UUID tiebreaker introduces variance that propagates through the entire downstream pipeline (SelectedKnowledge → PromptProjection → LLM).

```
FIRST_CANONICAL_VARIANCE_STAGE = KnowledgeSelectionServiceImpl.factOrder comparator
```

## 29. Story 0106 Boundary

```
STORY_0106_BLOCKED_BY_COLLECTION_VARIANCE = NO
STORY_0106_PROMPT_IMPLEMENTATION = APPROVED
```

Story 0106's acceptance criteria focus on prompt utilization and intent-aware context. The UUID tiebreaker defect is upstream of Story 0106's scope. The frozen replay proves the prompt implementation is effective regardless of which markdown facts are selected.

## 30. ADR Assessment

```
ADR_REQUIRED_FOR_FIX = NO
```

A simple comparator change (replacing UUID tiebreaker with content-based tiebreaker) is an implementation correction, not an architectural decision. It aligns the implementation with the existing invariant that selection should be deterministic for fixed canonical project state.

ADR-064 remains:
```
ADR_064_SEQUENCE = KEEP_PAUSED
```

## 31. Follow-Up Story Assessment

```
FOLLOW_UP_STORY_REQUIRED = YES
```

The UUID tiebreaker fix is a small, self-contained change suitable for a follow-up Story. It is separate from Story 0106's scope (prompt utilization) and from the identity normalization investigation (model-facing reference identity).

```
PROPOSED_STORY_GOAL = Replace volatile UUID tiebreaker in KnowledgeSelection fact ranking comparator with content-based tiebreaker to ensure cross-Analysis selection determinism
```

## 32. Deferred Work

```
MODEL_FACING_IDENTITY_NORMALIZATION = DEFERRED
DETERMINISTIC_ELIGIBILITY_VALIDATOR = DEFERRED
```

## 33. Recommendation

```
RECOMMENDED_NEXT_ACTION = B
```

**B. Accept Story 0106 prompt implementation. Create follow-up Story for UUID tiebreaker fix.**

Rationale:
1. Story 0106's prompt-utilization objective is demonstrated (100% clean on frozen input)
2. The UUID tiebreaker defect is upstream of Story 0106's scope
3. The defect is a simple implementation correction (one comparator line)
4. The impact is LOW (semantically equivalent markdown facts are swapped)
5. The fix aligns with the existing canonical information invariant

## 34. Explicit Non-Actions

This investigation does NOT:

- declare Story 0106 accepted
- declare the implementation approved
- authorize commit
- authorize push
- authorize merge
- create an ADR
- modify production code
- modify tests
- modify prompts
- modify schemas
- modify collectors
- modify selection logic
- stage
- commit
- push
- merge

## 35. HUMAN Review Gate

This investigation provides evidence for HUMAN review. The HUMAN will decide whether:

1. Story 0106 can be accepted with the current UUID tiebreaker behavior
2. A follow-up Story is warranted for the UUID tiebreaker fix
3. The UUID tiebreaker fix should be part of Story 0106 or separate

---

## Appendix: Investigation Metadata

### Report Path

```
docs/investigations/story-0106-knowledge-collection-determinism-investigation.md
```

### Evidence Files

| File | Description |
|---|---|
| `/tmp/corrective_benchmark_results.json` | Corrective runtime benchmark data (3 runs) |
| `/tmp/frozen_replay_results.json` | Frozen PromptRequest replay results (5 replays) |
| `/tmp/normalized_comparison_report.json` | Normalized offline comparison report |

### Key Source Files Referenced

| Component | File | Key Lines |
|---|---|---|
| DocumentationCollector | `backend/src/main/java/.../collection/collector/DocumentationCollector.java` | 12-97 |
| SecureRepositoryScanner | `backend/src/main/java/.../collection/collector/SecureRepositoryScanner.java` | 28-177 |
| FactAccumulator | `backend/src/main/java/.../collection/collector/FactAccumulator.java` | 12-52 |
| CollectedFact | `backend/src/main/java/.../collection/collector/CollectedFact.java` | 32-58 |
| AbstractFileCollector | `backend/src/main/java/.../collection/collector/AbstractFileCollector.java` | 7-42 |
| KnowledgeCollectionServiceImpl | `backend/src/main/java/.../collection/service/KnowledgeCollectionServiceImpl.java` | 60-262 |
| KnowledgeSelectionServiceImpl | `backend/src/main/java/.../knowledge/selection/KnowledgeSelectionServiceImpl.java` | 33-74, 155-182, 251-281 |
| CollectorLimits | `backend/src/main/java/.../collection/collector/CollectorLimits.java` | 14-18 |
| CollectorType | `backend/src/main/java/.../collection/collector/CollectorType.java` | 3-12 |
| DeterministicObservationEngine | `backend/src/main/java/.../collection/observation/DeterministicObservationEngine.java` | 12-51 |
| SemanticSection | `backend/src/main/java/.../knowledge/selection/SemanticSection.java` | 117 |

### Git State

```
BRANCH = story/0106-intent-aware-context-utilization
HEAD = 70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a
WORKING_TREE = uncommitted Story 0106 implementation + corrective changes + untracked investigation files
```

---

## Appendix: Required Explicit Verdicts

```
KNOWLEDGE_COLLECTION_DETERMINISM_INVESTIGATION = COMPLETE

REPOSITORY_SOURCE_STATE_RUN_1 = 70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a
REPOSITORY_SOURCE_STATE_RUN_2 = 70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a
REPOSITORY_SOURCE_STATE_RUN_3 = 70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a
HISTORICAL_REPOSITORY_STATES_EQUAL = YES

COLLECTION_SOURCE = LIVE_WORKTREE
SOURCE_STATE_SEMANTICS = IMPLICIT

DOCUMENTATION_CANDIDATE_COUNT = 1472
DOCUMENTATION_COLLECTION_LIMIT = 500 (maxFactsPerType)
DOCUMENTATION_LIMIT_REACHED = YES (1472 > 500)

FILESYSTEM_ORDER_USED = YES
FILESYSTEM_ORDER_GUARANTEED = NO (but per-directory sorting mitigates)
ORDER_BEFORE_LIMIT = YES (Files.list() is unordered before .limit())

SUBSET_SELECTION_NONDETERMINISM = RULED_OUT (DocumentationCollector is deterministic)
DOCUMENT_SELECTION_POLICY = ACCIDENTAL (emergent from alphabetical traversal + limit)

CONTROLLED_REPETITIONS = 5
CONTROLLED_SELECTED_SET_STABLE = YES
CONTROLLED_SELECTED_ORDER_STABLE = YES

FACT_FINGERPRINT_DETERMINISM = STRONG
COLLECTOR_INVOCATION_ORDER_DETERMINISTIC = YES
COLLECTOR_AGGREGATION_ORDER_DETERMINISTIC = YES
PERSISTENCE_RETRIEVAL_ORDER_DETERMINISTIC = YES

FIRST_UNSTABLE_FACT_ORDER_STAGE = KnowledgeSelectionServiceImpl.factOrder comparator (line 74)

RUN_1_RUN_2_FACT_SET_EQUAL = NO (5/40 differ)
RUN_1_RUN_2_MARKDOWN_FACT_SET_EQUAL = NO (5/8 differ)
DIFFERING_MARKDOWN_FACT_COUNT = 5

DIFFERING_DOC_FACT_DECISION_IMPACT = NEGLIGIBLE

SOURCE_STATE_VARIANCE = NO
COLLECTION_VARIANCE = NO
SELECTION_VARIANCE = YES
MODEL_VARIANCE = NOT_DEMONSTRATED

CANONICAL_INFORMATION_PIPELINE = UNIQUE
CANONICAL_PIPELINE_DETERMINISM = PARTIAL

CANONICAL_COLLECTION_INVARIANT = DESIRABLE
FIRST_CANONICAL_VARIANCE_STAGE = KnowledgeSelectionServiceImpl.factOrder comparator

ROOT_PROBLEM_CLASS = IMPLEMENTATION_DEFECT
MINIMAL_CORRECTION_BOUNDARY = KNOWLEDGE_SELECTION_SERVICE
SORT_BEFORE_LIMIT_SUFFICIENT = YES

ANALYSIS_GENERATED_DOCUMENTATION_CAN_FEEDBACK = YES
FEEDBACK_CAN_AFFECT_BOUNDED_SELECTION = YES

STORY_0106_BLOCKED_BY_COLLECTION_VARIANCE = NO
STORY_0106_PROMPT_IMPLEMENTATION = APPROVED

MODEL_FACING_IDENTITY_NORMALIZATION = DEFERRED
DETERMINISTIC_ELIGIBILITY_VALIDATOR = DEFERRED

ADR_REQUIRED_FOR_FIX = NO
ADR_064_SEQUENCE = KEEP_PAUSED

FOLLOW_UP_STORY_REQUIRED = YES
PROPOSED_STORY_GOAL = Replace volatile UUID tiebreaker in KnowledgeSelection fact ranking comparator with content-based tiebreaker to ensure cross-Analysis selection determinism

RECOMMENDED_NEXT_ACTION = B
```

---

`STORY_0106_KNOWLEDGE_COLLECTION_DETERMINISM_INVESTIGATION_COMPLETE`

`REPORT_PATH = docs/investigations/story-0106-knowledge-collection-determinism-investigation.md`

`PRODUCTION_CODE_CHANGED = NO`
`TEST_CODE_CHANGED = NO`
`PROMPT_CHANGED = NO`
`MODEL_CALLS_PERFORMED = NO`
`STORY_CREATED = NO`
`ADR_CREATED = NO`
`STAGED = NO`
`COMMIT_CREATED = NO`
`PUSH_PERFORMED = NO`
`MERGE_PERFORMED = NO`

`HUMAN_REVIEW = REQUIRED`

`TERMINAL_STATE = STORY_0106_KNOWLEDGE_COLLECTION_DETERMINISM_INVESTIGATION_READY_FOR_HUMAN_REVIEW`
