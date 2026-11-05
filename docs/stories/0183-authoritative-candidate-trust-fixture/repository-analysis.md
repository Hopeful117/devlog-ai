# Repository Analysis - Story 0183

## Status

`READY_FOR_HUMAN_APPROVAL`

This analysis is based on the approved Story 0183 scope. It does not authorize
implementation.

## Repository State

- Repository: `devlog-ai`
- Current branch: `story/0182-core-redacted-candidate-fixture`
- Current HEAD: `b0ab4e5b`
- Merged base on `origin/main`: `d6231550` (PR #173)
- Worktree before this analysis: clean apart from the newly created Story 0183
  directory.
- Story 0183 implementation authorization: not granted.
- DevLog Story Agent: unavailable in this session; repository artifacts and
  accepted ADRs were used instead.

## Governing Context

### ADR-006

Java Core remains the authority for trusted project knowledge and AI trust
boundaries. Python output and evaluation results must not promote or reinterpret
trust.

### ADR-063

Retrieval, composition, projection, grounding, and expansion remain distinct.
Core owns authoritative candidate metadata and consumer-specific composition;
the evaluation harness may replay bounded metadata but must not reconstruct
context.

### ADR-066

The evaluation harness is a deterministic, replayable measurement capability
outside production behavior. Its fixtures and results must validate semantics,
grounding, trust boundaries, and reproducibility.

### ADR-067

Java/Core is the sole EngineeringContext authority. Python receives authorized
input and may execute bounded evaluation logic, but cannot independently retrieve
or classify project context.

### ADR-079

Selection reduction and provider-incomplete outcomes are separate bounded
diagnostics. Story 0183 must preserve the existing fixture correlation and must
not use trust coverage as a reason to change context or provider budgets.

## Current Implementation

### Candidate metadata production

`RepositoryContextEngine.build(...)` ranks the collected candidates and stores a
`RepositoryContext.CandidateMetadata` row for every ranked candidate before
selection and enrichment. Each row already carries:

- canonical reference;
- evidence kind;
- originating file;
- relevance score;
- source type;
- related references.

The metadata is preserved in `RepositoryContext` and adapted through the
story-scoped context path. This is sufficient input for a Core-side deterministic
classification without repository or database access from Python.

### Existing trust classification

`EngineeringContextContractMapper.classifyTrustTier(RepositoryEvidence)` already
defines the deterministic rules:

- `CORE_KNOWLEDGE` plus `INSIGHT`, `DECISION`, or `ENGINEERING_EVENT` becomes
  `TRUSTED`;
- human-authored document and project kinds become `HUMAN_AUTHORED`;
- analysis and diagnostic kinds become `SYSTEM_METADATA`;
- repository-derived source types become `TECHNICAL_EVIDENCE`;
- `VALIDATABLE_PROPOSAL` and `AI_ENGINE` are excluded;
- unsupported kinds return no tier rather than being guessed.

The method currently receives `RepositoryEvidence`, while candidate fixture rows
are built from `CandidateMetadata`. The implementation gap is therefore an
authority-preserving reuse/extraction seam, not a new trust policy.

### Current fixture gap

`ContextLossLedger.candidateFixture(...)` currently resolves each candidate's
trust through `canonical.trustByReference()`. That map is populated for the
canonical selected evidence projection, so rejected candidates commonly fall
back to `UNKNOWN`. The fixture already preserves the candidate's kind and source
type upstream, but the fixture builder does not use those fields to classify the
complete candidate set.

The fixture digest includes the trust value. Any corrected classification will
therefore be observable and reproducible through the existing digest contract.

### Python evaluation boundary

`story_context_runtime_report.py` validates the allowed trust vocabulary,
fixture bounds, candidate consistency, and fixture digest. The policy comparator
uses fixture-provided trust values and does not access Core or reconstruct
repository context. The Python side should require no classification algorithm;
it should only validate and aggregate the enriched Core output.

## Findings

1. The required candidate metadata is already available in Core.
2. The current `UNKNOWN` values are caused by lookup scope, not by missing
   candidate fields in the Core candidate pipeline.
3. The existing trust rules are the authoritative source and should be reused or
   extracted once rather than duplicated for the fixture.
4. Excluded or unsupported candidates must remain distinguishable from ordinary
   technical evidence. `UNKNOWN` remains legitimate when metadata is incomplete
   or no existing rule applies.
5. No REST, MCP, persistence, retrieval, ranking, selection, or provider change
   is required by the approved Story scope.
6. The existing `ContextLossLedgerTest` already constructs candidate metadata and
   is the nearest backend test seam. Python fixture and comparator tests already
   cover trust vocabulary and replay behavior.

## Proposed Implementation Boundary

The implementation should be limited to:

- extracting or reusing the existing Core trust classification logic through a
  deterministic metadata-level helper;
- applying that helper to every candidate fixture row;
- preserving explicit excluded/unknown behavior;
- extending backend and Python tests for selected, rejected, projected,
  grounded, excluded, and unsupported cases;
- rerunning the story-local offline comparison and recording a decision report.

No implementation plan is produced until this Repository Analysis is approved.

## Risks and Open Questions

- `CandidateMetadata` currently has `kind` and `sourceType`, but not the full
  `RepositoryEvidence` provenance object. The implementation plan must verify
  whether the existing rules can be applied from those fields alone or whether a
  bounded, non-public classification helper needs a slightly richer Core input.
- The classification rules may currently be private to the context contract
  mapper. Extracting them must not change the public EngineeringContext mapping
  or excluded-evidence behavior.
- `UNKNOWN` and excluded candidates must have an explicit contract representation
  in the fixture; they must not be silently collapsed into a trust tier merely to
  improve comparison coverage.
- The post-0182 replay must be interpreted as evaluation evidence only. A
  production category-concentration decision remains a separate human decision.

## Validation Evidence Reviewed

- Story 0182 evaluation report and implementation report.
- `RepositoryContext` candidate metadata contract.
- `RepositoryContextEngine` candidate capture path.
- `EngineeringContextContractMapper` trust classification rules.
- `ContextLossLedger` telemetry and fixture construction.
- Python runtime-report validation and policy-comparison tests.
- ADR-006, ADR-063, ADR-066, ADR-067, and ADR-079.

## Gate

The Repository Analysis is complete and ready for human approval. After explicit
approval, the next permitted stage is the Implementation Plan. Implementation is
not authorized by this analysis.
