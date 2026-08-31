# Story 0107 - Engineering Report

## Status

**IMPLEMENTATION_READY_FOR_HUMAN_REVIEW**

## Architecture

Story 0107 remains a bounded Knowledge Selection correction:

```text
Fact candidate universe
        |
        v
existing relevance score DESC
        |
        v
existing FactType order
        |
        v
stable Fact semantic order
        |
        v
existing grounding closure and Fact budget
```

Persistence UUIDs remain unchanged and continue to serve persistence, grounding, membership, and traceability. They no longer decide Fact semantic ranking.

## Canonical Ordering

The final Fact tie-breaker compares:

```text
source
-> content
-> sorted evidence references, lexicographically
```

Fact type remains the preceding comparator dimension. The selected fields are already present in `FactSnapshot`, are Analysis-independent, and contain no operational timestamps or persistence IDs.

Comparator equality is safe: equal type, source, content, and sorted evidence references describe equivalent selection semantics. Existing `factContentKey(type + content)` deduplication is broader and already prevents duplicate discretionary semantics from consuming budget.

## Fingerprint Assessment

- persisted fingerprint exists: YES
- Analysis-independent for equivalent collection input: YES
- available in `FactSnapshot`: NO
- used by Story 0107: NO

Propagating fingerprint would broaden snapshots and model-facing projection and would require handling nullable manual Fact fingerprints. Existing stable semantic snapshot fields were sufficient for the focused correction.

## Observation Assessment

Observation ordering still uses UUID as a final comparator dimension. No current equivalent bounded-selection defect was demonstrated: deterministic rules produce unique current observations and at most six candidates against a budget of 25. Production scope was not expanded.

## RED / GREEN Evidence

### RED

Command:

`mvn test -Dtest="KnowledgeSelectionServiceTest#shouldSelectSameOrderedFactSemanticsAcrossAnalysisUuidPermutations" --no-transfer-progress`

Result before production change:

- tests: 1
- failures: 1
- each UUID permutation selected a different semantic order/subset

### GREEN

The same command after the comparator correction:

- tests: 1
- failures: 0
- errors: 0
- skipped: 0

## Five-Permutation Evidence

All permutations contain 45 same-score, same-type Facts and rotate the same 45 fixed UUID values by seven positions. The Fact budget selects 40.

| Permutation | UUID Assignment | Semantic Selected Facts | Equal to Baseline |
|---|---|---|---|
| 1 | rotation 0 | `fact-00` through `fact-39` | baseline |
| 2 | rotation 7 | `fact-00` through `fact-39` | YES |
| 3 | rotation 14 | `fact-00` through `fact-39` | YES |
| 4 | rotation 21 | `fact-00` through `fact-39` | YES |
| 5 | rotation 28 | `fact-00` through `fact-39` | YES |

```text
ALL_SEMANTIC_SELECTIONS_EQUAL = YES
```

## Quality Gates

### Related Tests

Command:

`mvn test -Dtest="KnowledgeSelectionServiceTest,KnowledgeSelectionServiceAdditionalTest,KnowledgeSelectionServiceImplStatusExclusionTest,SemanticSectionComposerTest,SelectedKnowledgePromptProjectionServiceTest,AnalysisContextServiceTest" --no-transfer-progress`

Result: 57 tests, 0 failures, 0 errors, 0 skipped.

### Full Backend Suite

Command: `mvn test --no-transfer-progress`

Result: 1,050 tests, 0 failures, 0 errors, 0 skipped.

### Verification Gate

Command: `mvn clean verify --no-transfer-progress`

Result: BUILD SUCCESS; 1,050 tests passed; JaCoCo report generated; all coverage checks met.

## Boundary Verification

Unchanged:

- relevance scoring and guidance scoring
- Fact and Observation budgets
- grounding closure and UUID references
- commit-diff cap
- collector behavior and limits
- documentation overflow policy
- prompt builders and AI Engine
- model/provider configuration
- database schema
- frontend and MCP
- ADR-064 sequence

The selection metadata label was corrected from `STABLE_TYPE_AND_ID_ORDER` to `STABLE_TYPE_AND_SEMANTIC_ORDER` so trace metadata accurately describes the implemented behavior.

## Conclusion

Story 0107 enforces persistence-identity-independent Fact ranking with one focused comparator correction and regression evidence across five bounded UUID permutations.
