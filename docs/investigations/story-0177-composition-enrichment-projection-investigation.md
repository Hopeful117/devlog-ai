# Investigation: Composition, Enrichment and Projection Bottlenecks

## Status

**INVESTIGATION_COMPLETE - NO PRODUCTION CHANGE**

This investigation follows the offline results from Story 0177. It does not
authorize a retrieval-hybrid, embedding, vector-store, or production budget
change.

## Question

Is the current Story Context problem primarily a retrieval-recall problem, or
does the system retrieve enough evidence and then lose useful information during
selection, enrichment, projection, or provider execution?

## Scope and Sources

The investigation inspected the current Core pipeline, existing budget and
projection diagnostics, Story 0177 offline results, and the prior truncation /
provider-incomplete investigation.

Relevant implementation path:

```text
collectors
  -> ranking
  -> bounded selection
  -> symbol enrichment
  -> file-content enrichment
  -> RepositoryContext
  -> AgentContextProjectionService
  -> prompt budget preflight
  -> provider
```

The current worktree contains unrelated uncommitted backend, benchmark,
Playwright, and OAuth changes. They were inspected but not modified or
attributed to this investigation.

## Evidence Classification

### OBSERVED - Retrieval already finds a large candidate universe

The prior live investigation observed:

```text
candidateCount=437 or 438
selectedCount=60
discardedCount=377 or 378
repository-context budget=6000 tokens
truncated=true
```

This proves that the system has a substantial candidate population and that the
first material reduction occurs in bounded Core composition. It does not prove
that all discarded candidates were useful.

### OBSERVED - Core selection is multi-stage and budgeted

`RepositoryContextEngine` collects and ranks candidates, then applies
`BudgetedDiverseEvidenceSelector` with limits of 60 evidence items and 6000
estimated tokens by default. Selection also applies requested-file priority,
layer diversity, knowledge floors, category ceilings, relevance thresholds, and
token/item budgets.

The selector records reasons such as:

- `SELECTED_BY_REQUESTED_FILE`;
- `SELECTED_BY_DIVERSITY`;
- `SELECTED_BY_CATEGORY_FLOOR`;
- `CATEGORY_CONCENTRATION_LIMIT`;
- `EVIDENCE_ITEM_BUDGET_EXCEEDED`;
- `TOKEN_BUDGET_EXCEEDED`.

This is a composition problem that can be measured without introducing a new
retrieval channel.

### OBSERVED - Enrichment can increase cost after selection

File and symbol enrichment runs after path selection. File enrichment is
restricted to selected source, test, and configuration files, and applies:

- maximum enriched-file limits;
- maximum characters per file;
- maximum total characters;
- remaining repository-context token budget;
- metadata-token reservation.

Enrichment can return `SKIPPED`, `TRUNCATED`, or `UNAVAILABLE`, with warnings
such as `CONTENT_ENRICHMENT_LIMIT_APPLIED`, `CONTENT_BUDGET_EXHAUSTED`,
`CONTENT_ALLOCATION_METADATA_BUDGET_EXHAUSTED`, and
`CONTENT_ENRICHMENT_TRUNCATED`.

The final evidence token estimate is recalculated after enrichment. Therefore,
the selected item set may remain unchanged while the effective context cost and
the usable content change materially.

### OBSERVED - Projection removes information before generation

`AgentContextProjectionService` has separate limits of 32,768 canonical bytes
and 8,192 estimated tokens. When the projection does not fit, it reduces data
in this order:

1. related references;
2. selection reasons;
3. declarations;
4. file content;
5. summaries;
6. profile details;
7. human-context details;
8. project-context lists;
9. project context to a minimal form;
10. tail evidence items, then all evidence if still necessary.

This ordering preserves evidence identity longer than evidence content, but it
can still remove the very content needed to answer a question while retaining a
selected reference. Projection truncation is therefore not equivalent to
retrieval failure.

The projection already records canonical bytes, estimated tokens, removed
content/declarations/evidence counts, warnings, context digest, and projection
digest. It does not yet expose a complete per-reference loss ledger across all
pipeline stages.

### OBSERVED - Provider execution has an independent budget

`StoryContextBudgetPreflight` estimates prompt input from the serialized
projection plus a fixed prompt-policy overhead of 4096 tokens. It then combines
input estimate, authorized output allowance, and a 256-token safety margin
against model capacity.

The repository-context budget, projection budget, prompt input estimate, and
provider output allowance are therefore distinct budgets. Increasing one can
make another fail or can increase the probability of provider incompletion.

The prior live investigation demonstrated that a temporary increase of the
effective output allowance from 2000 to 4000 allowed generation to complete,
while the Core projection remained independently truncated. This separates
provider output exhaustion from retrieval recall.

### OBSERVED - Story 0177 does not establish a production semantic-retrieval need

The offline results were:

| Condition | Candidate/final recall | Noise rate |
|---|---:|---:|
| Lexical control | 0.875 / 0.875 | 0.250000 |
| Structural control | 1.000 / 1.000 | 0.229167 |
| Semantic experiment | 1.000 / 1.000 | 0.312500 |
| Hybrid experiment | 1.000 / 1.000 | 0.312500 |

The semantic gain is based on a frozen synthetic test double. It demonstrates a
possible recall gap for indirect wording, but the higher noise and absence of
live provider/index measurements make it insufficient evidence for production
hybrid retrieval.

## Bottleneck Classification

| Stage | Evidence | Assessment |
|---|---|---|
| Retrieval recall | Lexical misses one synthetic indirect case | Possible secondary gap, not the dominant demonstrated failure |
| Candidate selection | Hundreds of candidates reduced to a bounded set | Primary measurement target |
| Enrichment | Content is budgeted after selection and can be skipped/truncated | Primary measurement target |
| Projection | Payload compaction/removal and truncation are explicit | Primary measurement target |
| Prompt/provider execution | 2000-token output allowance caused incompletion; 4000 completed | Independent primary operational risk |

## Conclusion

The strongest current explanation is not "retrieval is insufficient". It is a
composite budget and information-loss problem:

```text
large candidate universe
  -> bounded selection
  -> expensive or incomplete enrichment
  -> lossy projection
  -> prompt/output budget contention
  -> provider incompletion or weak answer
```

Composition, enrichment, and projection are more pertinent priorities than
production hybrid retrieval at this stage.

## Recommended Investigation/Implementation Order

1. Add a per-reference stage ledger: candidate, ranked, selected, enriched,
   projected, grounded, and final provider-visible.
2. Attribute every loss to a reason and budget: relevance, category, item,
   repository tokens, enrichment characters, projection bytes/tokens, or
   provider capacity.
3. Compare targeted, broad, empty, multi-source, and deliberately truncated
   requests using the same ledger.
4. Measure whether requested-file prioritization preserves answer-critical
   evidence after enrichment and projection.
5. Optimize projection and enrichment before raising global context limits.
6. Keep the hybrid retrieval experiment as a secondary control until real
   captures show a persistent recall deficit after final projection.

## Decision Recommendation

Do not create a production hybrid-retrieval architecture Story yet. The next
Story should focus on bounded observability and loss-aware composition,
enrichment, projection, and provider preflight. A later hybrid-retrieval Story
would require evidence from revision-pinned live captures showing that the
lexical/structural pipeline still misses answer-critical evidence after these
stages are measured and improved.

## Validation

- Story 0177 offline replay passed.
- Story 0177 safety and grounding assertions passed.
- Backend retrieval-boundary focused tests passed: 18/18.
- `git diff --check` passed.
- Python `pytest` was unavailable in the host and AI Engine container; pytest
  suites remain unexecuted.

No production source, Docker configuration, MCP contract, or retrieval policy
was changed by this investigation.
