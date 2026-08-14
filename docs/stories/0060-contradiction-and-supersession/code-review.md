# Story 0060 — Contradiction and Supersession in Incremental Knowledge Evolution — Code Review

## Status

Reviewed

## Review Scope

Review of the first ADR-054 implementation slice:

* ADR-054
* trust-state marker on trusted knowledge
* `SUPERSEDES` relation vocabulary
* architecture delta contract extension
* AI-engine prompt/schema updates
* Core-side supersession validation
* supersession promotion with history preservation
* idempotence preservation

## Findings

### 1. Historical truth is preserved, not rewritten ✅

`InsightPromotionService` marks the accepted target `SUPERSEDED` and never
deletes or edits its content.

The prior statement remains queryable as historical truth, satisfying ADR-054
point 4 and ADR-050 point 7.

### 2. Supersession is a fully validated lifecycle outcome ✅

A `SUPERSEDES` proposal follows the same human validation path as `NEW` and
`ENRICHES`.

A rejected or invalid supersession leaves the target `ACTIVE` and creates no
relation. There is no automatic acceptance.

### 3. The Core validates supersession structurally ✅

`AiProposalContractValidator` requires a `SUPERSEDES` target that exists in the
selected architecture knowledge.

`InsightPromotionService` additionally enforces same-project and `ACTIVE`
target constraints. No lifecycle intent is inferred by parsing prose.

### 4. ADR-006 boundaries remain intact ✅

The AI still only proposes a supersession; it never mutates trusted knowledge.

The Core performs the trust-state change and relation creation atomically, and
only after acceptance.

### 5. The relation taxonomy gains one explicit type ✅

`KnowledgeRelationType.SUPERSEDES` records successor → predecessor.

This is more precise than overloading `DERIVED_FROM`, which remains the
enrichment relation, keeping the two traceability paths distinct.

### 6. Idempotence and duplicate policy are preserved ✅

A scan with no contradiction and no dominant new evidence returns zero proposals
and changes no trust state.

The `TrustedKnowledgeDuplicateGuard` still rejects equivalent `ACTIVE`
statements, so supersession cannot be used to bypass duplicate policy.

### 7. The change is narrow and architecture-only ✅

The contract, prompt, and promotion changes are limited to
`ARCHITECTURE_REVIEW` and the `SUPERSEDES` outcome.

`CONFIRMS`/`CONTRADICTS` and temporal modeling remain out of scope.

### 8. Tests cover the right failure modes ✅

The new coverage exercises:

* accepted supersession (target `SUPERSEDED`, `SUPERSEDES` relation);
* rejection of a non-active supersession target;
* valid supersession contract acceptance;
* `trustState` mapping.

## Gate Results

* Backend `./mvnw verify`: **PASS**
  - 647 tests
  - JaCoCo checks met
* AI engine `./.venv/bin/python -m pytest -q`: **PASS**
  - 52 tests

## Conclusion

Approve.

The implementation is narrow, consistent with ADR-054 and ADR-006, and closes
the highest-value gap left by Story 0037 without over-designing a temporal
engine. No blocking findings remain.