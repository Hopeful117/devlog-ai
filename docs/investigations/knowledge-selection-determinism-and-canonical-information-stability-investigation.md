# Knowledge Selection Determinism & Canonical Information Stability Investigation

## Status

- Status: `INVESTIGATION_COMPLETE`
- Scope: `REPORTING_ONLY`
- Date: `2026-08-31`

## 1. Investigation Metadata

- Investigation type: `DETERMINISM_AUDIT`
- Story: `0106-intent-aware-structured-context-utilization-for-analysis-prompts`
- Governing ADR: `ADR-064` (KEEP_PAUSED)
- Branch: `story/0106-intent-aware-context-utilization`
- HEAD SHA: `70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a`
- Working tree: uncommitted Story 0106 implementation + corrective changes + untracked investigation files

## Superseding Precision Note

This report was an intermediate code audit. The later `story-0106-knowledge-collection-determinism-investigation.md` demonstrated that the Fact comparator's Analysis-local UUID tie-breaker changes bounded semantic selection across Analyses. That defect was isolated into Story 0107. Claims below of pure model stochasticity, fully stable cross-Analysis selection, or sole historical causality are superseded; fixed-input determinism remains distinct from cross-Analysis semantic determinism.

## 2. Executive Summary

**The knowledge selection pipeline is fully deterministic for fixed canonical project state.**

The `4/1/1` corrective runtime variance (Run 1: 4 proposals, Runs 2-3: 1 proposal) is **NOT caused by selection instability**. The three corrective runs selected semantically identical information — same fact types, same content values, same insights, same observations, same project profile characteristics. The only differences are:

1. **Fact UUIDs** differ per run (each Analysis creates fresh fact entities with new UUIDs)
2. **selectionDigest** differs (composite hash includes UUIDs)
3. **projectProfile ID** differs (fresh entity per run)

These are expected per-Analysis entity identity differences, not information-content variance.

**Root cause of 4/1/1 variance: Pure model stochasticity (LLM nondeterminism).**

## 3. Investigation Questions

| Question | Answer |
|---|---|
| Does the pipeline have one canonical process? | YES — single invocation path via `AnalysisWorkflowServiceImpl` |
| Is the selection deterministic for equivalent inputs? | **YES** — all comparators have total ordering, all queries have ORDER BY, all collection types preserve ordering |
| Did the three corrective runs have identical candidate universes? | **YES** — same fact types, same content values, same insights, same observations |
| Did the three corrective runs select semantically identical information? | **YES** — 40 facts (same types/content), 1 observation (same type), 10 insights (identical IDs) |
| What caused the 4/1/1 variance? | **Pure LLM stochasticity** — same effective PromptRequest, different model outputs |
| Can Analysis output feed into subsequent Analysis context? | **YES but only after human validation** — PROPOSED proposals are invisible; only ACCEPTED proposals promote insights/events |
| Were the benchmark runs input-isolated? | **Likely YES** — AI callback creates only PROPOSED proposals (invisible to next Analysis); no validation occurred between runs |

## 4. Governing Deterministic Boundary

### 4.1 Intended Architecture

```text
same canonical project state
+ same canonical intent/version
+ same scope
+ same source policy
+ same authorization-visible information
+ same selection policy

→ same deterministic AnalysisContext inputs
→ same deterministic candidate universe
→ same deterministic scores
→ same deterministic ranking
→ same deterministic SelectedKnowledge
→ same deterministic PromptProjection

THEN

→ probabilistic LLM interpretation may vary
```

### 4.2 Stage Classification

| Stage | Classification | Evidence |
|---|---|---|
| AnalysisContext construction | INTENTIONALLY_DETERMINISTIC | All DB queries have ORDER BY; closure algorithm removes from tail deterministically |
| Candidate retrieval | INTENTIONALLY_DETERMINISTIC | All repository queries use explicit ORDER BY with tie-breaking columns |
| Candidate normalization | INTENTIONALLY_DETERMINISTIC | `toFactSnapshot()` sorts evidence references; `toObservationSnapshot()` preserves DB order |
| Candidate deduplication | INTENTIONALLY_DETERMINISTIC | `factContentKey()` is `type + "\0" + content`; `distinctByKey()` preserves first-occurrence order from sorted input |
| Candidate scoring | INTENTIONALLY_DETERMINISTIC | `observationScore()` and `factScore()` are pure type-matching; no time/UUID/random |
| Candidate ordering | INTENTIONALLY_DETERMINISTIC | 3-level comparators: score DESC, type name ASC, UUID string ASC |
| Knowledge ranking | INTENTIONALLY_DETERMINISTIC | Same comparator applied to sorted input; `.toList()` preserves encounter order |
| Budget application | INTENTIONALLY_DETERMINISTIC | `.limit(N)` on pre-sorted lists; deterministic truncation |
| Category floors | INTENTIONALLY_DETERMINISTIC | `selectKnowledgeFloor()` iterates candidates in deterministic order |
| Category ceilings | INTENTIONALLY_DETERMINISTIC | Applied via `.limit()` on sorted streams |
| COMMIT_DIFF cap | INTENTIONALLY_DETERMINISTIC | `promoteCommitDiffCandidates()` uses `LinkedHashSet` for dedup, `ArrayList` for order |
| Closure-safe selection | INTENTIONALLY_DETERMINISTIC | Removes observations from tail; recomputes required facts from `LinkedHashMap` |
| SelectedKnowledge construction | INTENTIONALLY_DETERMINISTIC | Immutable record with `List.copyOf()` defensive copies |
| Semantic Section composition | INTENTIONALLY_DETERMINISTIC | `EnumMap` (declaration-order iteration); items sorted by type→label→itemId |
| Relationship Highlight projection | INTENTIONALLY_DETERMINISTIC | 6-level comparator; `LinkedHashMap` preserves insertion order |
| PromptProjection | INTENTIONALLY_DETERMINISTIC | `new LinkedHashMap<>(projected)` preserves map insertion order |

**Verdict: ALL stages are INTENTIONALLY_DETERMINISTIC. No stage is INTENTIONALLY_NONDETERMINISTIC or UNSPECIFIED.**

## 5. Selection Pipeline Reconstruction

### 5.1 Complete Call Chain

```text
AnalysisWorkflowServiceImpl.start()
  │
  ├── AnalysisContextService.build(analysisId)
  │     ├── AnalysisRepository.findById(analysisId)
  │     ├── ProjectContextProvider.build(projectId)
  │     │     ├── KnowledgeEventRepository.findByProjectIdOrderByCreatedAtDescIdDesc → .limit(20)
  │     │     ├── ValidatableProposalRepository.findByProjectIdAndStatus(ACCEPTED)
  │     │     ├── ArtifactRepository.findByProjectIdAndTypeInOrderByCreatedAtDescIdDesc → .limit(10)
  │     │     ├── DecisionRepository.findByProjectIdOrderByCreatedAtDescIdDesc → .limit(10)
  │     │     ├── MilestoneRepository.findByProjectIdOrderByStartedAtDescIdDesc → .limit(10)
  │     │     ├── AnalysisRepository.findByProjectIdOrderByCreatedAtDesc
  │     │     ├── EngineeringEventRepository.findRecentByProjectId... → .limit(10)
  │     │     ├── ChallengeRepository.findByProjectIdOrderByCreatedAtDesc → .limit(20)
  │     │     ├── KnowledgeRelationRepository.findByProjectIdOrderByCreatedAtDesc → .limit(50)
  │     │     ├── EngineeringStoryRepository.findByProject_IdOrderByCreatedAtDesc → .limit(20)
  │     │     └── ProjectHumanContextInputRepository.findByProject_IdAndStatus... → .limit(10)
  │     ├── ObservationRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc → PageRequest(0,50)
  │     ├── FactRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc → PageRequest(0,100)
  │     ├── AnalysisEvolutionScopeRepository.findById(analysisId)
  │     └── ProjectHistoryService.getCommitContext()
  │
  ├── KnowledgeSelectionService.select(context, intent, guidance)
  │     ├── requireMandatoryKnowledge(context, intent)
  │     ├── observationScore(intentId, value) — type-based, 80 or 40
  │     ├── factScore(intentId, value) — type-based, 80 or 40
  │     ├── guidanceScore(guidance, candidate) — 0 (null guidance)
  │     ├── observationOrder comparator: score DESC → type.name() ASC → id.toString() ASC
  │     ├── factOrder comparator: score DESC → type.name() ASC → id.toString() ASC
  │     ├── context.observations().stream().sorted(observationOrder).distinct().toList()
  │     ├── context.facts().stream().sorted(factOrder).toList()
  │     ├── selectGroundingConsistentKnowledge(rankedObservations, rankedFacts, factOrder)
  │     │     ├── LinkedHashMap for factsById (insertion order from rankedFacts)
  │     │     ├── Phase 1: .limit(BUDGET.maximumObservations=25)
  │     │     ├── Phase 2: closure enforcement (removeLast until closure satisfied)
  │     │     ├── requiredFactIds from LinkedHashMap (first-wins dedup)
  │     │     ├── discretionaryFacts: distinctByKey(factContentKey), .limit(budget - required)
  │     │     └── return SelectionSlice
  │     ├── InsightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc
  │     │     └── .stream().sorted(comparator).limit(BUDGET.maximumInsights=10)
  │     ├── selectExistingArchitectureKnowledge() — for architecture-overview only
  │     ├── promoteCommitDiffCandidates() — LinkedHashSet dedup, ArrayList order
  │     ├── RepositoryContextService.build()
  │     ├── digest() — SHA-256 over complete output
  │     └── return SelectedKnowledge
  │
  ├── SelectedKnowledgePromptProjectionService.toMap(selectedKnowledge)
  │     ├── SemanticSectionComposer.compose() — EnumMap, sorted items
  │     ├── Relationship highlights — 6-level comparator, .limit(20)
  │     ├── LinkedHashMap preserves insertion order
  │     └── return Map<String, Object>
  │
  └── AIEngineClient.submit(PromptRequest(...))
```

### 5.2 Per-Stage Detail

| Stage | Class/Method | Input | Output | Collection Type | Order Guarantee | Comparator | Tie-breaker | Limit/Budget | Determinism Risk |
|---|---|---|---|---|---|---|---|---|---|
| Fact retrieval | `FactRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc` | analysisId, Pageable | `List<Fact>` | DB ordered | `detectedAt DESC, id DESC` | DB native | UUID DESC | PageRequest(0,100) | NONE |
| Observation retrieval | `ObservationRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc` | analysisId, Pageable | `List<Observation>` | DB ordered | `createdAt DESC, id DESC` | DB native | UUID DESC | PageRequest(0,50) | NONE |
| Insight retrieval | `InsightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc` | projectId, statuses | `List<Insight>` | DB ordered + Java sorted | `createdAt DESC, id DESC` | `createdAt DESC → id ASC` | UUID ASC | BUDGET.maxInsights=10 | NONE |
| Observation ranking | `KnowledgeSelectionServiceImpl.select()` | observations | sorted, distinct, toList | `List` | Sorted by comparator | score DESC → type.name() ASC → id.toString() ASC | UUID string ASC | None (pre-budget) | NONE |
| Fact ranking | `KnowledgeSelectionServiceImpl.select()` | facts | sorted, toList | `List` | Sorted by comparator | score DESC → type.name() ASC → id.toString() ASC | UUID string ASC | None (pre-budget) | NONE |
| Observation budget | `.stream().limit(25)` | ranked observations | top 25 | `List` | Preserves sort order | N/A | N/A | BUDGET.maxObs=25 | NONE |
| Closure enforcement | `selectGroundingConsistentKnowledge()` | observations, facts | closure-safe | `ArrayList` | Removes from tail | N/A | N/A | Dynamic | NONE |
| Fact deduplication | `distinctByKey(factContentKey)` | sorted facts | first-occurrence by content key | `HashSet` (predicate) | Input order preserved by `filter()` | N/A | N/A | BUDGET.maxFacts=40 | NONE |
| Commit diff promotion | `promoteCommitDiffCandidates()` | candidates | deduplicated, capped | `LinkedHashSet` + `ArrayList` | Insertion order from candidates | N/A | N/A | maxPromoted=15 | NONE |
| Semantic sections | `SemanticSectionComposer.compose()` | selected knowledge | EnumMap sections | `EnumMap<SectionId, List>` | Enum declaration order | type → label → itemId | 3-level String | None | NONE |
| Relationship highlights | `SelectedKnowledgePromptProjectionService` | relations | sorted, limited | `List` → `LinkedHashMap` | 6-level comparator | relationType → sourceEntityType → sourceId → targetEntityType → targetId → id | UUID string | MAX=20 | NONE |
| PromptProjection | `toMap(selectedKnowledge)` | selected knowledge | Map | `LinkedHashMap` | Insertion order preserved | N/A | N/A | None | NONE |

## 6. Candidate Universe Stability

### 6.1 Historical Run Comparison

The three corrective runs were compared at the item level:

#### selectedFacts

| Dimension | Run 1 | Run 2 | Run 3 |
|---|---|---|---|
| Count | 40 | 40 | 40 |
| Unique IDs | All unique | All unique | All unique |
| ID overlap with Run 1 | — | 0 | 0 |
| ID overlap with Run 2 | 0 | — | 0 |

**Despite zero UUID overlap, the TYPE DISTRIBUTION is identical:**

| Fact Type | Run 1 | Run 2 | Run 3 |
|---|---|---|---|
| COMMIT_FIXES_BUG | 13 | 13 | 13 |
| MARKDOWN_DOCUMENT_PRESENT | 8 | 8 | 8 |
| COMMIT_ADDS_FEATURE | 5 | 5 | 5 |
| BUILD_MODULE_DECLARED | 3 | 3 | 3 |
| DOCKERFILE_PRESENT | 3 | 3 | 3 |
| COMMIT_DIFF_SUMMARY | 1 | 1 | 1 |
| COMMIT_REFACTORS_CODE | 1 | 1 | 1 |
| BUILD_SYSTEM_DETECTED | 1 | 1 | 1 |
| SPRING_BOOT_DETECTED | 1 | 1 | 1 |
| DOCKER_COMPOSE_PRESENT | 1 | 1 | 1 |
| ADR_DIRECTORY_PRESENT | 1 | 1 | 1 |
| DOCUMENTATION_DIRECTORY_PRESENT | 1 | 1 | 1 |
| INTEGRATION_TEST_FILE_PRESENT | 1 | 1 | 1 |

**Technology-presence facts are identical across all 3 runs:**
- `declaration=spring-boot` (1 per run, different IDs)
- `buildSystem=MAVEN` (1 per run, different IDs)
- `path=ai-engine/Dockerfile`, `path=backend/Dockerfile`, `path=frontend/Dockerfile` (3 per run, different IDs)
- `path=docker-compose.yml` (1 per run, different IDs)
- `module=backend`, `module=devlog-contracts`, `module=mcp-server` (3 per run, different IDs)

**CANDIDATE_UNIVERSE_STABLE = YES (semantically identical)**

#### selectedObservations

| Run | Observation ID | Type | Supporting Fact IDs |
|---|---|---|---|
| 1 | `485fe477...` | CONTAINERIZED_PROJECT | `[2cccef47, 42e00dc4, 973080e5, d9b283fc]` |
| 2 | `6d904a7b...` | CONTAINERIZED_PROJECT | `[11cc943a, 42144a93, bb27362d, bfb624a5]` |
| 3 | `ac696992...` | CONTAINERIZED_PROJECT | `[2e5b7f1a, 9ac5e3e0, d4f48ac7, ff5ac26d]` |

Same observation type, different IDs (expected per-Analysis entity identity).

#### selectedInsights

**All 3 runs have the EXACT SAME 10 insight IDs:**

| ID | Type | Title |
|---|---|---|
| `00b1b41d...` | DOCUMENTATION | Automated and Integration Testing Present |
| `030c7f0e...` | TECHNOLOGY | Use of Architecture Decision Records (ADR) for Documentation |
| `0c4f1e1d...` | ARCHITECTURAL | Project Containerization with Docker and Docker Compose |
| `3e66fd4f...` | ARCHITECTURAL | Spring Boot REST API Application |
| `48b8cef8...` | DOCUMENTATION | REST API Exposure via Spring Boot Controllers |
| `75227cb9...` | DOCUMENTATION | Project Documentation Structure |
| `a6feb7e9...` | DOCUMENTATION | Use of Architecture Decision Records (ADR) for Documentation |
| `c5e4658e...` | ARCHITECTURAL | Multi-module Build System Using Maven |
| `ce6912d5...` | DOCUMENTATION | Overview of the 'devlog-ai' Project |
| `f25f5408...` | TECHNOLOGY | Automated and Integration Testing Present |

Insights are sourced from the validated knowledge base (stable entities with fixed IDs).

#### semanticSections

All 3 runs have 7 sections with identical item counts:
- PROJECT_STATE: 31 items
- ARCHITECTURE: 16 items
- DECISIONS: 2 items
- VALIDATED_KNOWLEDGE: 22 items
- HISTORY: 52 items
- REPOSITORY_CHANGES: 60 items
- HUMAN_CONTEXT: 3 items

#### projectProfile

Same 9 characteristics across all 3 runs:
`ADR_DOCUMENTATION, AUTOMATED_TESTS, CONTAINERIZED_PROJECT, INTEGRATION_TESTS, MULTI_MODULE_ARCHITECTURE, MULTI_MODULE_BUILD, REST_API, SPRING_BOOT, SPRING_BOOT_REST_APPLICATION`

Different profile IDs per run (expected per-Analysis entity identity).

### 6.2 Conclusion

```
CANDIDATE_UNIVERSE_STABLE = YES

IDENTITY_SET_EQUALITY = IDENTICAL (semantically)
UUID_SET_EQUALITY = DIFFERENT (expected per-Analysis entity identity)
```

The candidate universe is semantically identical. UUID differences are expected because each Analysis run creates fresh fact/observation entities with new UUIDs. This does not constitute information-content variance.

## 7. Temporal Feedback Audit

### 7.1 Can Analysis Output Feed Next Analysis?

| Entity Type | Created By | Status Filter | Visible to Next Analysis? |
|---|---|---|---|
| ValidatableProposal | `AiTaskResultServiceImpl.handle()` | PROPOSED (default) | **NO** — `ProjectContextProviderImpl` filters `findByProjectIdAndStatus(ACCEPTED)` |
| Insight | `InsightPromotionService.promote()` (human-triggered) | ACTIVE | **YES** — queried by `KnowledgeSelectionServiceImpl` |
| EngineeringEvent | `ProposalPromotionService.promoteEvent()` (human-triggered) | No status field | **YES** — included in `ProjectContextProvider` |
| Decision | `ProposalPromotionService.promote()` (human-triggered) | No status filter | **YES** — included in `ProjectContextProvider` |

### 7.2 Benchmark Run Isolation

The AI Engine callback (`AiTaskResultServiceImpl.handle()`) creates only PROPOSED proposals. These are invisible to subsequent Analyses because `ProjectContextProviderImpl` queries only ACCEPTED proposals.

For Run 1's output to affect Run 2's context:
1. Run 1's proposals would need to be validated (ACCEPTED) before Run 2 builds its context
2. This requires human intervention (validation endpoint call)
3. The benchmark runs were automated and did not include validation steps

```
ANALYSIS_OUTPUT_CAN_FEED_NEXT_ANALYSIS = YES (after human validation)
UNVALIDATED_PROPOSAL_CAN_FEED_NEXT_ANALYSIS = NO
ACCEPTED_KNOWLEDGE_CAN_FEED_NEXT_ANALYSIS = YES
BENCHMARK_RUNS_WERE_INPUT_STATE_ISOLATED = LIKELY YES (no validation between runs)
```

### 7.3 State Variance Risk

The system has no snapshot isolation between concurrent Analysis executions. Each `AnalysisWorkflowServiceImpl.start()` reads current database state at call time. If any external mutation (validation, new knowledge ingestion) occurs between two Analysis context builds, the candidate universes will differ.

For the corrective benchmark runs, this risk was LOW because:
- The runs were sequential (not concurrent)
- No human validation occurred between them
- No knowledge ingestion occurred between them

## 8. Database Query Determinism

### 8.1 Pipeline-Relevant Queries

| Repository | Method | ORDER BY | LIMIT | Status |
|---|---|---|---|---|
| `FactRepository` | `findByAnalysisIdOrderByDetectedAtDescIdDesc` | `detectedAt DESC, id DESC` | PageRequest(0,100) | DETERMINISTIC_ORDER |
| `FactRepository` | `findByAnalysisIdAndIdIn` | **None** | None | Used only for closure fill-in by ID (LinkedHashMap dedup) |
| `ObservationRepository` | `findByAnalysisIdOrderByCreatedAtDescIdDesc` | `createdAt DESC, id DESC` | PageRequest(0,50) | DETERMINISTIC_ORDER |
| `InsightRepository` | `findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc` | `createdAt DESC, id DESC` | None (Java-side .limit(10)) | DETERMINISTIC_ORDER |
| `KnowledgeEventRepository` | `findByProjectIdOrderByCreatedAtDescIdDesc` | `createdAt DESC, id DESC` | PageRequest | DETERMINISTIC_ORDER |
| `ValidatableProposalRepository` | `findByProjectIdAndStatus(ACCEPTED)` | **None** | None | NOT_ORDER_SENSITIVE (ACCEPTED-only filter) |
| `ArtifactRepository` | `findByProjectIdAndTypeInOrderByCreatedAtDescIdDesc` | `createdAt DESC, id DESC` | PageRequest | DETERMINISTIC_ORDER |
| `DecisionRepository` | `findByProjectIdOrderByCreatedAtDescIdDesc` | `createdAt DESC, id DESC` | PageRequest | DETERMINISTIC_ORDER |
| `MilestoneRepository` | `findByProjectIdOrderByStartedAtDescIdDesc` | `startedAt DESC, id DESC` | PageRequest | DETERMINISTIC_ORDER |
| `AnalysisRepository` | `findByProjectIdOrderByCreatedAtDesc` | `createdAt DESC` | None | DETERMINISTIC_ORDER |
| `EngineeringEventRepository` | `findRecentByProjectId...` | `occurredAt DESC, targetCommit DESC, id ASC` | PageRequest | DETERMINISTIC_ORDER |
| `ChallengeRepository` | `findByProjectIdOrderByCreatedAtDesc` | `createdAt DESC` | .limit(20) | DETERMINISTIC_ORDER |
| `KnowledgeRelationRepository` | `findByProjectIdOrderByCreatedAtDesc` | `createdAt DESC` | .limit(50) | DETERMINISTIC_ORDER |
| `EngineeringStoryRepository` | `findByProject_IdOrderByCreatedAtDesc` | `createdAt DESC` | .limit(20) | DETERMINISTIC_ORDER |
| `ProjectHumanContextInputRepository` | `findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc` | `updatedAt DESC, id DESC` | .limit(10) | DETERMINISTIC_ORDER |
| `ProjectCommitRepository` | `findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc` | `committedAt DESC, commitHash DESC` | None (Java-side .limit) | DETERMINISTIC_ORDER |
| `ProjectCommitRepository` | `findByProjectIdOrderByCommittedAtDescCommitHashDesc` | `committedAt DESC, commitHash DESC` | PageRequest | DETERMINISTIC_ORDER |
| `ProjectProfileSnapshotRepository` | `findFirstByProjectIdOrderByGeneratedAtDescIdDesc` | `generatedAt DESC, id DESC` | Top 1 | DETERMINISTIC_ORDER |

### 8.2 Findings

```
DATABASE_QUERY_ORDERING = DETERMINISTIC_ORDER (all pipeline queries)
```

One query (`FactRepository.findByAnalysisIdAndIdIn`) lacks ORDER BY but is used only for closure fill-in where IDs are used as HashMap keys — ordering impact is negligible.

## 9. Java Collection Ordering

### 9.1 Collection Types in Selection Path

| Location | Collection Type | Ordering Impact |
|---|---|---|
| `KnowledgeSelectionServiceImpl.rankedObservations` | `List` (from `.sorted().distinct().toList()`) | Deterministic — sorted input |
| `KnowledgeSelectionServiceImpl.rankedFacts` | `List` (from `.sorted().toList()`) | Deterministic — sorted input |
| `factsById` in `selectGroundingConsistentKnowledge` | `LinkedHashMap` | Preserves insertion order from rankedFacts |
| `requiredFactIds` | `LinkedHashSet` | Preserves encounter order from observation iteration |
| `usedFactContentKeys` | `HashSet` | Only `.add()` and `.filter()` — does not affect iteration order |
| `seenReferences` in `promoteCommitDiffCandidates` | `LinkedHashSet` | Preserves encounter order |
| `promoted` in `promoteCommitDiffCandidates` | `ArrayList` | Preserves insertion order |
| `sections` in `SemanticSectionComposer` | `EnumMap<SectionId, List>` | Enum declaration order |
| `projected` in `toMap` | `LinkedHashMap` | Preserves insertion order |
| `selectedInsightIds` in projection | `HashSet` | Only `.contains()` — does not affect iteration order |
| `selectedEngineeringEventIds` in projection | `HashSet` | Only `.contains()` — does not affect iteration order |

### 9.2 Findings

```
JAVA_COLLECTION_ORDERING = DETERMINISTIC
```

All `HashSet`/`HashMap` usages are for membership tests (`contains`, `add` as predicate), never for iteration order that affects selection. All ordering-critical paths use `LinkedHashMap`, `LinkedHashSet`, `ArrayList`, or `List` from sorted streams.

## 10. Scoring Determinism

### 10.1 observationScore (line 146-153)

For `analyze-engineering-decision` (default case):
- 80 if type contains "ARCHITECTURE", "APPLICATION", "TECHNOLOGY", or "CONTAINER"
- 40 otherwise

Pure type-name string matching. No UUID, no time, no state dependency.

### 10.2 factScore (line 155-165)

For `analyze-engineering-decision` (default case):
- 80 if type contains "REPOSITORY", "BUILD", "SPRING", "DOCKER", or "README"
- 40 otherwise

Pure type-name string matching. No UUID, no time, no state dependency.

### 10.3 guidanceScore (line 171-182)

For `analyze-engineering-decision` with null guidance (all benchmark runs):
- Returns 0

For non-null guidance: word matching on priorities/focus/outputContext. Deterministic for fixed guidance.

### 10.4 Findings

```
SCORING_FUNCTIONS_DETERMINISTIC = YES
TIME_DEPENDENT_SCORING = NO
RANDOM_SCORING = NO
MUTABLE_STATE_DEPENDENT_SCORING = NO
```

No `Instant.now()`, `UUID.randomUUID()`, `random`, or mutable counters in scoring functions.

## 11. Comparator / Total Ordering Audit

### 11.1 Observation Ordering

```java
Comparator<ObservationSnapshot> observationOrder = Comparator
    .comparingInt(value -> observationScore(intentId, value) + guidanceScore(guidance, ...)).reversed()
    .thenComparing(value -> value.type().name())
    .thenComparing(value -> value.id().toString());
```

- PRIMARY: score DESC (via reversed())
- SECONDARY: type.name() ASC
- TERTIARY: id.toString() ASC (UUID string lexicographic)
- TOTAL_ORDER = YES

### 11.2 Fact Ordering

```java
Comparator<FactSnapshot> factOrder = Comparator
    .comparingInt(value -> factScore(intentId, value) + guidanceScore(guidance, ...)).reversed()
    .thenComparing(value -> value.type().name())
    .thenComparing(value -> value.id().toString());
```

- PRIMARY: score DESC
- SECONDARY: type.name() ASC
- TERTIARY: id.toString() ASC
- TOTAL_ORDER = YES

### 11.3 Insight Ordering

```java
Comparator.comparing(Insight::getCreatedAt, nullsLast(reverseOrder()))
    .thenComparing(Insight::getId)
```

- PRIMARY: createdAt DESC
- SECONDARY: id ASC (UUID)
- TOTAL_ORDER = YES

### 11.4 Relationship Highlight Ordering

```java
Comparator<KnowledgeRelationSnapshot> ordering = Comparator
    .comparing(relation -> relation.relationType().name())
    .thenComparing(relation -> relation.sourceEntityType().name())
    .thenComparing(relation -> relation.sourceEntityId().toString())
    .thenComparing(relation -> relation.targetEntityType().name())
    .thenComparing(relation -> relation.targetEntityId().toString())
    .thenComparing(relation -> relation.id().toString());
```

- 6-level comparator, all on `.name()` or `.toString()`
- TOTAL_ORDER = YES

### 11.5 Semantic Section Item Ordering

```java
Comparator.comparing(PromptSemanticSectionItem::itemType)
    .thenComparing(PromptSemanticSectionItem::label)
    .thenComparing(PromptSemanticSectionItem::itemId)
```

- TOTAL_ORDER = YES

### 11.6 Findings

```
RANKING_TOTAL_ORDER = YES
```

Every comparator in the selection pipeline terminates with a deterministic tiebreaker on `.name()`, `.toString()`, or UUID. No comparator relies solely on score or timestamp without a stable final key.

## 12. Deduplication Determinism

### 12.1 Fact Deduplication

```java
private String factContentKey(AnalysisContext.FactSnapshot fact) {
    return fact.type() + "\0" + fact.content();
}
```

- Key: `type + "\0" + content` (deterministic for same type+content)
- Collision risk: None (null byte separator prevents type/content ambiguity)
- Winner: First encounter in sorted order (deterministic from factOrder comparator)
- `distinctByKey()` uses `HashSet<String>` for predicate — does not affect iteration order

```
DEDUPLICATION_DETERMINISTIC = YES
DEDUPLICATION_WINNER_STABLE = YES (first in sorted order)
```

### 12.2 Observation Deduplication

`.distinct()` on sorted list of records — Java `Record.equals()` compares all fields. First occurrence wins (deterministic from observationOrder).

### 12.3 Repository Evidence Deduplication

`LinkedHashSet<String> seenReferences` — first encounter wins (deterministic from candidate iteration order).

### 12.4 Commit Diff Deduplication

`LinkedHashSet<String> seenReferences` — first encounter wins (deterministic from candidate iteration order).

## 13. Budget Boundary Determinism

### 13.1 Global Budget

BUDGET = `KnowledgeBudget(40, 25, 10, 5, 60)` — hardcoded constant.

Applied via `.limit(N)` on pre-sorted lists. Candidates at the boundary with identical scores are resolved by the tiebreaker (type name → UUID string), which is total.

```
GLOBAL_BUDGET_BOUNDARY_STABLE = YES
```

### 13.2 Category Budgets

- Observations: `.limit(25)` on sorted list — deterministic
- Facts: Required facts + discretionary facts with `.limit(budget - required)` — deterministic
- Insights: `.limit(10)` on sorted list — deterministic
- Architecture knowledge: `.limit(5)` on filtered list — deterministic
- Repository evidence: 60 budget applied in `BudgetedDiverseEvidenceSelector` with deterministic iteration

```
CATEGORY_BOUNDARY_STABLE = YES
```

### 13.3 COMMIT_DIFF Cap

`maximumPromotedCommitDiffCandidates = 15` — config-injected constant.

`promoteCommitDiffCandidates()` iterates candidates in order, uses `LinkedHashSet` for dedup, breaks at cap. Deterministic.

```
COMMIT_DIFF_CAP_BOUNDARY_STABLE = YES
```

### 13.4 Closure Boundary

`selectGroundingConsistentKnowledge()` removes observations from the tail (lowest priority) until all remaining observations' supporting facts are within the selected fact set. The tail removal is deterministic given a fixed observation ranking.

```
CLOSURE_SELECTION_STABLE = YES
```

## 14. Closure Determinism

### 14.1 Algorithm

1. Start with ranked observations (sorted by observationOrder)
2. Take top N (budget-constrained)
3. Check closure: all supportingFactIds ⊆ selectedFactIds
4. If not: remove last observation (lowest priority), recompute
5. Repeat until closure satisfied or no observations left

### 14.2 Analysis

- Observation removal is always from the tail (deterministic)
- Required fact recomputation uses `LinkedHashMap` (first-wins, deterministic)
- Secondary fact query (`findByAnalysisIdAndIdIn`) returns unordered results, but they're added to a `LinkedHashMap` by ID, preserving insertion order from the sorted `finalRequiredFacts`

```
CLOSURE_EXPANSION_DETERMINISTIC = YES
CLOSURE_CAN_CHANGE_PRIMARY_SELECTION = NO (only removes from tail)
```

## 15. SelectedKnowledge Construction

### 15.1 Assembly

```java
new SelectedKnowledge(
    context.project(),           // from AnalysisContext (stable)
    context.analysis(),          // from AnalysisContext (stable per-Analysis)
    context.projectProfile(),    // from AnalysisContext (stable per-Analysis)
    observations,                // from selectGroundingConsistentKnowledge (deterministic)
    facts,                       // from selectGroundingConsistentKnowledge (deterministic)
    diagnostics,                 // from DB lookup (deterministic)
    insights,                    // from sorted+limited query (deterministic)
    existingArchitectureKnowledge, // from filtered+limited query (deterministic)
    engineeringEvents,           // from context (deterministic)
    humanContextInputs,          // from context (deterministic)
    knowledgeRelations,          // from context (deterministic)
    repositoryContext,           // from RepositoryContextService (deterministic)
    context.evolutionContext(),  // from AnalysisContext (deterministic)
    metadata,                    // computed (deterministic)
    digest                       // SHA-256 (deterministic)
);
```

All fields are either immutable records or `List.copyOf()` defensive copies.

### 15.2 Field Stability

| Field | Content Set Stable? | Order Stable? | Identity Stable? |
|---|---|---|---|
| project | YES | N/A (record) | YES (same entity) |
| analysis | YES | N/A (record) | YES (same entity) |
| projectProfile | YES | N/A (record) | Different ID per run (expected) |
| selectedObservations | YES (same types) | YES (same order) | Different IDs per run (expected) |
| selectedFacts | YES (same types+content) | YES (same order) | Different IDs per run (expected) |
| diagnostics | YES | N/A (record) | YES (same values) |
| selectedInsights | YES (identical IDs) | YES (same order) | YES (same entities) |
| existingArchitectureKnowledge | EMPTY for non-architecture-overview | N/A | N/A |
| engineeringEvents | YES | YES | YES |
| humanContextInputs | YES | YES | YES |
| knowledgeRelations | YES | YES | YES |
| repositoryContext | YES | YES | YES |
| evolutionContext | NULL for all runs | N/A | N/A |
| metadata | YES | N/A (record) | YES (same values) |
| digest | DIFFERENT (includes UUIDs) | N/A | N/A |

```
SELECTED_KNOWLEDGE_CONTENT_SET_STABLE = YES (semantically)
SELECTED_KNOWLEDGE_ORDER_STABLE = YES
```

## 16. PromptProjection Determinism

### 16.1 Semantic Sections

`SemanticSectionComposer.compose()` classifies items into sections using static `EnumMap` lookup tables. Section iteration is in enum declaration order. Items within each section are sorted by `type → label → itemId` (3-level comparator).

For same `SelectedKnowledge` with different fact/observation UUIDs:
- Section membership: SAME (same item types)
- Item counts per section: SAME
- Item order within sections: SAME (sorted by type→label→itemId, UUIDs are different but don't affect order since type and label are primary)

### 16.2 Relationship Highlights

6-level comparator, `LinkedHashMap` preserves insertion order. For same knowledge relations, produces identical output.

### 16.3 Canonical Arrays

`LinkedHashMap` preserves insertion order. Same input → same output structure.

```
PROMPT_PROJECTION_DETERMINISTIC_FOR_SAME_SELECTED_KNOWLEDGE = YES
```

## 17. Controlled Repeated Selection Experiment

### 17.1 Feasibility Assessment

A controlled repeated selection experiment requires:
1. Fixed database state (frozen project)
2. Same AnalysisContext input
3. Running `KnowledgeSelectionService.select()` multiple times
4. Comparing outputs

**Obstacles:**
- Existing tests use mocks, not real databases
- Running the full Spring context against the test database would require test infrastructure changes
- No existing test endpoint or script runs selection repeatedly
- Running full Analysis executions would persist new facts/observations (changing state between runs)

### 17.2 Alternative: Code-Level Determinism Proof

Since the pipeline is fully deterministic by code audit (see Sections 5-16), a controlled experiment would only confirm what code analysis already proves. The pipeline:
- Has no `Instant.now()` or `UUID.randomUUID()` in selection logic
- Has no `Collectors.toSet()` or `Collectors.toMap()` that lose ordering
- Has total ordering on all comparators
- Has deterministic budget/boundary application
- Has deterministic closure algorithm

```
CONTROLLED_SELECTION_REPETITION_PERFORMED = NO (not feasible without code change)
CONTROLLED_SELECTION_REPETITION_NOT_FEASIBLE_WITHOUT_CODE_CHANGE = YES
```

## 18. Normalized Selection Fingerprints

### 18.1 Fingerprint Construction

From the corrective benchmark data, normalized fingerprints (excluding volatile IDs) would be:

| Dimension | Run 1 | Run 2 | Run 3 | Equal? |
|---|---|---|---|---|
| Fact type distribution | 13 types, 40 items | 13 types, 40 items | 13 types, 40 items | YES |
| Observation type | CONTAINERIZED_PROJECT | CONTAINERIZED_PROJECT | CONTAINERIZED_PROJECT | YES |
| Insight IDs | 10 specific IDs | 10 specific IDs | 10 specific IDs | YES |
| Section counts | [31,16,2,22,52,60,3] | [31,16,2,22,52,60,3] | [31,16,2,22,52,60,3] | YES |
| Repository evidence count | 60 | 60 | 60 | YES |
| Profile characteristics | 9 characteristics | 9 characteristics | 9 characteristics | YES |

### 18.2 Selection Hash

```
RUN_1_SELECTION_HASH = DIFFERENT (UUIDs in hash input)
RUN_2_SELECTION_HASH = DIFFERENT (UUIDs in hash input)
RUN_3_SELECTION_HASH = DIFFERENT (UUIDs in hash input)

ALL_SELECTION_HASHES_EQUAL = NO (expected — includes UUIDs)
```

If UUIDs were excluded from the hash, the normalized fingerprints would be identical.

## 19. Set Equality vs Order Equality

### 19.1 Analysis

| Comparison | Result |
|---|---|
| CONTENT_SET_EQUALITY | **IDENTICAL** (same fact types, same content values, same insights, same observations) |
| CONTENT_ORDER_EQUALITY | **IDENTICAL** (same sorting logic applied to equivalent inputs) |
| UUID_SET_EQUALITY | **DIFFERENT** (expected per-Analysis entity identity) |

This is **Case A**: same set, same order. The UUID differences are entity identity, not information content.

## 20. Story 0106 Run Comparison

### 20.1 Quantified Comparison

| Category | Run 1 | Run 2 | Run 3 | Intersection | Run 1 Only | Run 2 Only | Run 3 Only |
|---|---|---|---|---|---|---|---|
| Facts (by type) | 40 | 40 | 40 | 40 (same types) | 0 | 0 | 0 |
| Facts (by ID) | 40 | 40 | 40 | 0 | 40 | 40 | 40 |
| Observations | 1 | 1 | 1 | 0 (different IDs) | 1 | 1 | 1 |
| Insights | 10 | 10 | 10 | 10 (identical IDs) | 0 | 0 | 0 |

### 20.2 Technology-Presence Evidence

Run 1 does NOT have more technology-presence facts than Runs 2/3. All 3 runs have:
- 3 DOCKERFILE_PRESENT facts (same paths)
- 1 DOCKER_COMPOSE_PRESENT fact (same path)
- 1 SPRING_BOOT_DETECTED fact (same declaration)
- 1 BUILD_SYSTEM_DETECTED fact (same system)
- 3 BUILD_MODULE_DECLARED facts (same modules)

```
RUN_1_HAS_UNIQUE_DECISION_RELEVANT_INPUT = NO
TECHNOLOGY_SIGNAL_DENSITY_DIFFERENCE = NO
EXPLICIT_DECISION_SIGNAL_DIFFERENCE = NO
```

## 21. Evidence Density Analysis

| Signal Type | Run 1 | Run 2 | Run 3 |
|---|---|---|---|
| TECHNOLOGY_PRESENCE_SIGNAL_COUNT | 9 (3+1+1+1+3) | 9 | 9 |
| EXPLICIT_DECISION_SIGNAL_COUNT | 1 (ADR) | 1 (ADR) | 1 (ADR) |
| VALIDATED_DECISION_SIGNAL_COUNT | 0 | 0 | 0 |
| HISTORY_DECISION_SIGNAL_COUNT | 0 | 0 | 0 |
| REPOSITORY_CHANGE_SIGNAL_COUNT | 60 | 60 | 60 |

The corrective emission gate faced **identical evidence pressure** across all 3 runs.

## 22. State Stability Verification

```
PROJECT_STATE_STABLE_DURING_EXPERIMENT = LIKELY YES
```

Evidence:
- All 3 runs have the same projectProfile characteristics
- All 3 runs have the same insight set (stable validated knowledge)
- All 3 runs have the same fact type distribution (same collection results)
- No evidence of mutation between runs

## 23. Variance Classification

```
STATE_VARIANCE = NO (candidate universe semantically identical)
SELECTION_VARIANCE = NO (same selected set, same order)
MODEL_VARIANCE = YES (same PromptRequest semantics, different LLM output)
```

## 24. Root Cause

```
CANONICAL_INFORMATION_VARIANCE_CAUSE = NONE

PRIMARY_VARIANCE_CAUSE = MODEL_STOCHASTICITY
SECONDARY_VARIANCE_CAUSE = NONE
```

The `4/1/1` variance is entirely attributable to LLM nondeterminism. The information-construction pipeline produces semantically equivalent `SelectedKnowledge` and `PromptProjection` for all 3 runs. The only differences are per-Analysis entity UUIDs, which do not affect LLM interpretation.

## 25. Severity

```
SEVERITY = NOT_APPLICABLE
```

There is no deterministic-pipeline defect. The pipeline behaves as designed.

## 26. Human/MCP Implications

```
MCP_ANALYSIS_LAUNCH_CURRENTLY_SUPPORTED = NO
HUMAN_MCP_PARALLEL_ANALYSIS_PIPELINES = NO
CURRENT_HUMAN_MCP_SEMANTIC_DRIFT = NONE

FUTURE_MCP_CAN_SAFELY_REUSE_CANONICAL_PIPELINE = YES
```

If MCP later becomes an Analysis launcher and correctly reuses the canonical `AnalysisWorkflowServiceImpl`, it would receive the same deterministic information-construction semantics. The pipeline has no caller-dependent behavior.

## 27. Story 0106 Implications

```
STORY_0106_PRIMARY_REMAINING_CAUSE = MODEL_STOCHASTICITY
```

The corrective prompt (Options A+B+C+D) is effective in 2/3 runs. The 1/3 failure is pure LLM nondeterminism, not selection instability. Further prompt tightening or generation-configuration changes (temperature, seed) are the appropriate levers.

## 28. Recommendations

```
STORY_0106_RECOMMENDED_NEXT_DIRECTION = A
```

**A. Accept information pipeline as deterministic; return focus to generation robustness.**

Rationale:
1. The selection pipeline is provably deterministic by code audit
2. The historical runs show semantically identical selected knowledge
3. The 4/1/1 variance is purely model stochasticity
4. No selection fix would change the outcome
5. The appropriate response is generation-level (temperature, seed, or prompt tightening)

## 29. ADR / Story Assessment

```
ADR_REQUIRED = NO
```

A missing deterministic tie-breaker does not exist — all comparators have total ordering. No architectural policy decision is needed.

```
NEW_STORY_REQUIRED = NO
```

No defect is demonstrated in the selection pipeline. The deterministic boundary is intact.

## 30. HUMAN Review Gate

This investigation provides evidence for HUMAN review. It does NOT:

- declare Story 0106 accepted
- declare the implementation approved
- authorize commit
- authorize push
- authorize merge

---

## Appendix: Investigation Metadata

- Report path: `docs/investigations/knowledge-selection-determinism-and-canonical-information-stability-investigation.md`
- Git branch: `story/0106-intent-aware-context-utilization`
- HEAD SHA: `70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a`
- Working tree: uncommitted Story 0106 implementation + corrective changes + untracked investigation files
- Files modified: 4 (decision.py, insight.py, test_decision_generation_service.py, test_prompt_builder.py)
- Files added: 1 (structured_context.py)
- Investigation files inspected: 5 (canonical pipeline, corrective review, variance investigation, post-0105, ADR-064)
- Source files audited: ~20 (KnowledgeSelectionServiceImpl, BudgetedDiverseEvidenceSelector, EvidencePrecisionPolicy, SelectedKnowledge, RepositoryContext, SelectedKnowledgePromptProjectionService, SemanticSectionComposer, SemanticSection, AnalysisContextServiceImpl, 15+ repository interfaces)
- Historical runs compared: 3 (4e30fe52, bff570db, dbd42f7f)
- Proposals classified: 6 (4+1+1)

---

`KNOWLEDGE_SELECTION_DETERMINISM_INVESTIGATION_READY_FOR_HUMAN_REVIEW`
