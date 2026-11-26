# Story 0243 - Requested Evidence Content Delivery

## Status

**IMPLEMENTED - HUMAN ACCEPTANCE PENDING.**

Requested by the human on 2026-11-25 ("Evidence content first"). Decisions
D1 and D2 were taken by the human the same day.

## Epic

[Fiabiliser les reponses DevLog pour les agents](../../epics/agent-response-reliability/epic.md)
(objective 3: preserve critical evidence through selection, enrichment and projection)

## Problem

With the right question, scope and revision, DevLog selected the right files
but delivered no content, so every answer was an honest abstention.
Evidence: campaign Q1 gate 1 and the Story 0242 pinned check.

Diagnosis on real projections:

- **R1 (defect).** `RepositoryStructureCollector` classifies every requested
  file as `CONFIG_FILE` (`FileEvidenceKind.REQUESTED`). `SelectedFileContentEnricher`
  only enriches `CONFIG_FILE` when its path appears in the objective text. So
  requested Java and Python files were selected (10 requested files in gate A)
  but never received content, although only 4392 of 12000 tokens were used.
- **R3 (design rule).** When a run is bound to a Story without a base/target
  commit window, `filterByStoryScope` applies `filterToNonTechnical` after
  selection, which removes every file, diff and commit. A Story-bound run
  (run 28; campaign cases Q5 and Q8) therefore has no technical evidence at all.
- **R4 (design rule, Story 0119 section 5.2 note 3).** An oversized document
  is excluded rather than truncated. ADR-067 (24,877 characters, above the
  20,000 limit), which is Q1 oracle evidence, never arrives.

## Decisions (human, 2026-11-25)

- **D1 (R4).** Admit oversized documents truncated at the limit, with an
  explicit `TRUNCATED` status and reason, instead of excluding them.
  Supersedes Story 0119 section 5.2 note 3.
- **D2 (R3).** Without a Story commit window, keep the explicitly requested
  files and their content; only Git history (commits, diffs) is removed.

## Design

- **R1:**
  - A requested file keeps the evidence kind of what it is (`SOURCE_FILE`,
    `TEST_FILE`, otherwise `CONFIG_FILE`). Its `file:` reference and its
    `requested-file` identity are unchanged.
  - The enricher treats any explicitly requested file as eligible for content,
    whatever its kind.
- **R3:** `filterByStoryScope` receives the requested files. In both the
  no-window and the commit-window paths, file evidence (`SOURCE_FILE`,
  `TEST_FILE`, `CONFIG_FILE`) whose path matches a requested file or prefix is kept.
- **R4:** `DocumentBodyCollector` reads non-Story documents with the truncating
  reader. The content status is `TRUNCATED` with reason `CONTENT_TRUNCATED`.
  Admission bounds and the aggregate budget are unchanged.
- No budget value changes (epic invariant 6). The content policy stays at
  6 files, 4000 characters per file and 12000 in total.

## Acceptance Criteria

1. A requested source or test file is typed `SOURCE_FILE` / `TEST_FILE` and
   receives content within the existing content budget.
2. A requested non-source file (for example an ADR `.md`) receives content even
   if its path is not in the objective; an unrequested config file does not.
3. A Story without a commit window keeps the requested files and drops commits,
   diffs and unrequested files.
4. An oversized document is admitted truncated with status `TRUNCATED`.
5. Live: the pinned Q1 execution receives requested file content.

## Implementation Report

### Files Changed
- `backend/.../repositorycontext/collector/RepositoryStructureCollector.java` (R1)
- `backend/.../repositorycontext/enrichment/SelectedFileContentEnricher.java` (R1)
- `backend/.../projectcontext/RepositoryContextAdapter.java` (R3)
- `backend/.../repositorycontext/collector/DocumentBodyCollector.java` (R4)
- Tests:
  - `RepositoryStructureCollectorTest`: +1 (requested files keep their kind);
  - `SelectedFileContentEnricherTest`: +1 (requested document enriched,
    unrequested config file not);
  - `RepositoryContextAdapterStoryScopeTest`: +1 (requested files survive the
    Story filter);
  - `DocumentBodyCollectorTest` and `DocumentBodyCollectorIntegrationTest`:
    `read` and `readComplete` stubbed separately. The aggregate-budget test now
    expects the truncated admission required by D1.

### Validation
- Focused: 58 tests, 0 failures.
- Full backend `clean verify`: BUILD SUCCESS, 1966 tests, 0 failures.
- Live, through MCP (backend rebuilt from the worktree):
  - Projection, Story 0223 path, Q1 scope: requested files are `SOURCE_FILE`
    with content (`COMPLETE` or `TRUNCATED`) and survive the Story filter (R3).
    ADR-063 is admitted `TRUNCATED` (R4). Accounting: 11305 of 12000 tokens.
  - Pinned Q1 execution `c7145007` (unique `executionIdentity`): COMPLETED at
    `0673d1d3`, with 11013 tokens used (against 6350 before). Five requested
    source files carry content; the rest are `SKIPPED` (`ENRICHED_FILE_LIMIT`,
    policy of 6 files).
  - The answer is still `NOT_ESTABLISHED` / LOW, but it now states grounded
    code facts: the Story Context controller delegates to the Core use case,
    the persisted entity stores the snapshot and metadata, and ADR-006 splits
    Core and AI Engine responsibilities.

### Observations
- Core deduplicates identical submissions by content digest, regardless of the
  idempotency key. A repeated identical request returns the earlier task. The
  harness avoids this with `guidance.evaluation.executionIdentity`.

### R5 - limit found after the first live check (addressed below)
- Inside a broad requested directory, file candidates are ordered by objective
  term matches over the full path, then alphabetically. Every file under
  `storycontextanalysis/` matches the same terms through the package name, so
  `controller/`, `entity/`, `history/` and `investigation/` win over `usecase/`
  (`AnalyzeStoryContextUseCase`, `StoryContextSubmissionService`,
  `StoryContextCallbackService`, `StoryContextResultValidator`), which carry
  the Q1 flow. Content is delivered for the wrong files.

## R5 - In-scope ranking (requested by the human, 2026-11-25)

### Additional diagnosis
- The file-evidence cap (`MAX_FILE_EVIDENCE_ITEMS=40`) is allocated round-robin
  across requested, source, test and configuration files. Only about 10
  requested files survive, and they were sorted by objective terms over the
  full path, then by path. Under one package, every file matches the same terms.
- On the path the MCP tool uses (`POST /story-context-agent/projects/{slug}/tasks`),
  `AnalyzeStoryContextUseCase.execute` prepared the context without the
  question. The question only travelled in `guidance.focus` to the prompt, so
  retrieval could not use it.

### Design
- **(a)** The protocol `execute` path passes `guidance.focus` as the retrieval
  question when it is non-blank, at most 2000 characters, and not the intent id.
- **(b)** `RequestedScopeRelevance` (new, deterministic):
  - query stems come from the objective words minus stop words and requested
    paths, reduced to 5-character prefixes;
  - a requested file scores 15 per stem in its file name plus 8 per stem among
    its content identifiers (camelCase/snake_case split), capped at 100.
- **(c)** `RepositoryStructureCollector` scores requested candidates before the
  cap and sorts them by score, then path.
  - Content is read through `SecureRepositoryContentReader`, for at most 200
    files of at most 96 KB each.
  - The score is exposed as `extractionMetadata.requestedScopeRelevance`.
- **(d)** `DeterministicEvidenceRanker` uses that score as a floor for
  `SEMANTIC_RELEVANCE`. Malformed values never raise relevance.
- No budget, content-policy or selection-limit value changes.

### Tests
- `RequestedScopeRelevanceTest` (new, 4)
- `RepositoryStructureCollectorTest`: +1 (relevant use case before an alphabetically earlier controller)
- `DeterministicEvidenceRankerTest`: +1 (relevance floor; malformed metadata ignored)

### Validation (R5)
- Full backend `clean verify`: BUILD SUCCESS, 1972 tests, 0 failures.
- Live, pinned Q1 execution `173b0ecb` (unique `executionIdentity`, backend rebuilt):
  - The question now reaches retrieval: the projection request carries the Q1 question.
  - The selection is ranked by relevance. `AnalyzeStoryContextUseCase` (100),
    `StoryContextCallbackService` (94), `StoryContextPreparationService` (94),
    the generation service and prompt (100) are selected, instead of
    alphabetically first controllers and `investigation/*`. Six carry content;
    the others are `SKIPPED` (`ENRICHED_FILE_LIMIT`).
  - **New failure mode:** the task FAILED with `GROUNDING_UNKNOWN_REFERENCE` on
    both attempts (corrective retry included). The model cited scope files that
    were not selected, notably `ai-engine/app/schemas/story_context_analysis.py`,
    probably seen through imports in the delivered code. Attempt 2 also produced
    a malformed reference (`/` instead of `:`). Grounding validation rejected
    them as designed (fail closed).

### Open points
- With real code content, the model tends to cite neighbouring files it only
  sees mentioned. Options:
  1. give the model an explicit, exact list of citable references and require
     verbatim copies (prompt contract);
  2. make the corrective retry quote the rejected references and the allowed
     list;
  3. widen content slots (a content-policy change, which needs evaluation under
     epic invariant 6).
- Python files outside a `src` root are classified `CONFIG_FILE`. They are
  still enriched when requested.

## Citable references and unknown-reference retry (requested by the human, 2026-11-25)

### Diagnosis
- The prompt's `REQUESTED FILE SCOPE` mapped references to requested files by
  exact match only. With directory scopes, every mapping was empty, while the
  prompt required each finding to cite a reference mapped to a requested
  file. The model then built references from paths it saw in delivered code.
- The corrective retry repeated the error text but did not single out the
  rejected references.

### Changes (AI Engine prompt only; Java authoritative validation unchanged)
- `_requested_file_scope`: a requested directory maps to the references whose
  originating file lies under it.
- New `CITABLE REFERENCES (EXACT, EXHAUSTIVE, AUTHORITATIVE)` block built from
  the typed `allowedGroundingReferences` in Core's order. A file merely
  mentioned in evidence content is not citable, and references are never
  built from paths.
- `corrective_retry`: on an unknown-reference failure, adds an
  `UNKNOWN REFERENCE CORRECTION` section listing the rejected references and
  requiring a verbatim replacement from the list, or dropping the statement.
- Evaluation scenario `engineering-story-context-analysis-v1`: `scenario.json`
  `promptContentDigest` updated to the new production prompt digest, as done
  for previous prompt changes (#245). `replay.json` is left unchanged as a
  historical record.

### Validation
- AI Engine: 945 passed, 2 skipped before the new tests. Prompt tests: 17
  passed, including 3 new ones (citable list, directory mapping, retry quote).
- Live, pinned Q1 execution `c3b32fbc` (AI Engine rebuilt): COMPLETED, no
  grounding failure. Three grounded architecture findings cite the selected
  files:
  - the controller authenticates and dispatches to the Core use case;
  - Core preparation owns the project, story, context and projection inputs;
  - snapshot authorization precedes materialization.
  The summary remains `NOT_ESTABLISHED`, confidence LOW: the use case,
  callback, generation and persistence bodies are only partially retained
  (4000-character files, 6-file content policy).

### Remaining limit
- The answer now depends on the content policy (6 files, 4000 characters per
  file, 12000 in total), which is well below the token budget. Changing it is
  a budget change and needs evaluation evidence (epic invariant 6). The
  72-cell campaign can now provide that evidence.
