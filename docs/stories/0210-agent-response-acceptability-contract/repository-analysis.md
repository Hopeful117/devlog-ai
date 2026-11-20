# Repository Analysis - Story 0210

## Story Understanding

Story 0210 defines an agent-facing acceptability contract and a reproducible
baseline for DevLog Story Context responses. It must distinguish technical
execution validity, grounding, semantic usefulness, freshness, truncation and
abstention without replacing direct repository verification.

The proposed semantic states are `ACCEPTABLE`, `BOUNDED`, `INSUFFICIENT`,
`STALE_OR_UNKNOWN` and `FAILED`. The Story requires explicit rules for
grounding, evidence coverage, contradictions, freshness, truncation, confidence
and the next verification step. It also requires provider-free scenarios and
explicit `NOT_EVALUATED` handling for missing or technically invalid runs.

The Story is evaluation- and contract-definition work. It excludes prompt
redesign, budget changes, retrieval changes, RAG/vector infrastructure,
autonomous loops, trust-boundary changes and production behavior changes unless
separately approved.

## Repository Summary

The relevant pipeline is Core-authorized Story Context construction followed by
Python generation and defensive validation, with Java/Core retaining final
grounding and callback authority. Response-quality evaluation already exists in
the AI Engine and contains deterministic fixtures and separate technical and
semantic outcomes. The current repository also contains an answer-quality
benchmark specification whose oracles still require human review.

The current worktree contains unrelated, uncommitted MCP history-search changes.
They must remain outside Story 0210 scope.

## Affected Modules

### Java Core

- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/StoryContextResultValidator.java`
  owns authoritative validation of objective-summary grounding and output
  classification.
- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/StoryContextSubmissionService.java`
  constructs the Core-owned grounding contract and execution snapshot.
- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/service/StoryContextCallbackService.java`
  preserves callback acceptance and grounding boundaries.
- `devlog-contracts/.../StoryContextAnalysisResult.java` contains the shared
  structured response contract.

These components are relevant for checking whether the acceptability model can
be derived from existing execution metadata without changing production
authority or contracts.

### Python AI Engine

- `ai-engine/app/prompts/story_context_analysis.py` builds the grounded
  response prompt and requires explicit objective-summary grounding.
- `ai-engine/app/services/story_context_analysis_generation_service.py`
  performs provider generation and defensive corrective validation.
- `ai-engine/evaluations/evaluator.py` and related evaluation modules expose
  deterministic response-quality dimensions and technical/semantic separation.
- `ai-engine/evaluations/devlog_ai_abc_normalization/normalizer.py` already
  handles semantic eligibility and `NOT_EVALUATED` outcomes for benchmark
  observations.

### Tests and Evaluation Artifacts

- `ai-engine/tests/test_story_context_response_quality.py`
- `ai-engine/tests/test_story_context_analysis_wire_contract.py`
- `backend/src/test/java/com/hopeful117/devlogai/storycontextanalysis/usecase/StoryContextResultValidatorTest.java`
- `backend/src/test/java/com/hopeful117/devlogai/storycontextanalysis/StoryContextCoreIntegrationTest.java`
- `docs/evaluation/answer-quality-benchmark-specification.md`
- `ai-engine/evaluations/devlog_ai_abc_runner/`

These are the existing locations for provider-free fixtures, structural
validation, benchmark normalization and the human-reviewed benchmark contract.

## Existing Implementation

Story 0209 already provides the closest implementation slice. Its report states
that Java Core rejects non-empty objective summaries without grounding metadata,
Java and Python validate classification against canonical references, and the
replay evaluator exposes separate response-quality dimensions. It also records
fixtures for missing grounding, unauthorized references, truncated summaries,
insufficient evidence and complementary evidence.

The current Story 0209 artifact remains `IMPLEMENTATION IN PROGRESS` with human
acceptance pending, even though DevLog history contains the implementation
commit and report. This is a governance discrepancy that must be resolved by
human acceptance; it is not safe to treat the historical report as current
acceptance.

The repository already distinguishes technical incompleteness from semantic
incorrectness in the benchmark normalization path. The missing Story 0210
behavior is the cross-cutting acceptability classification and its explicit
decision rules, not a new retrieval mechanism.

Behavior that must remain unchanged:

- Java/Core remains the authority for scope, revision, grounding, trust and
  callback acceptance.
- Python remains probabilistic generation plus defensive validation.
- AI output remains non-trusted and read-only.
- Provider-incomplete, invalid or absent observations do not enter the semantic
  quality denominator and are reported as `NOT_EVALUATED` where applicable.
- Existing MCP and REST behavior remains compatible unless a later approved
  decision explicitly changes it.

## Relevant Documentation

- `AGENTS.md`
- `TOOLS.md`
- `docs/epics/agent-response-reliability/epic.md`
- `docs/stories/0210-agent-response-acceptability-contract/story.md`
- `docs/stories/0209-agent-response-quality-evidence-grounding/story.md`
- `docs/stories/0209-agent-response-quality-evidence-grounding/implementation-report.md`
- `docs/stories/0158-devlog-story-agent-response-specificity/story.md`
- `docs/stories/0178-story-context-loss-aware-composition/story.md`
- `docs/evaluation/answer-quality-benchmark-specification.md`
- `docs/decisions/ADR-006.md`
- `docs/decisions/ADR-063.md`
- `docs/decisions/ADR-066.md`
- `docs/decisions/ADR-067.md`
- `docs/decisions/ADR-079.md`

DevLog MCP was used for project history and Story context. The Story Agent
projection completed with `freshness=UNKNOWN` and `truncated=true`; it retained
useful adjacent 0209 evidence but did not retain the 0210, epic or benchmark
contents. Local repository artifacts therefore remain authoritative for this
analysis. Obsidian was not consulted because the repository contains the
canonical Story, ADR and benchmark artifacts required for this analysis.

## Constraints

- The epic remains design-materialized and does not itself authorize production
  implementation.
- Story 0210 must not silently add a production acceptability field or alter
  budgets, retrieval, prompts or provider policy.
- Technical validity, grounding validity, semantic quality and acceptability
  must remain distinct dimensions.
- Truncation, unknown freshness, provider incompleteness and insufficient
  evidence are distinct conditions.
- A technically valid response cannot be classified `ACCEPTABLE` when its main
  conclusion lacks required evidence.
- Confidence cannot promote a response beyond grounding, revision, freshness or
  truncation limits.
- Benchmark oracles require independent human review before quality claims are
  made.
- Baseline results may only be reported for executions actually performed;
  blocked or invalid observations remain `NOT_EVALUATED`.
- Existing uncommitted MCP changes are unrelated and must not be included.

## Risks

1. **Premature production contract change:** placing acceptability directly in
   the production envelope could expand scope beyond the proposed Story and
   create compatibility obligations.
2. **Conflating technical and semantic failure:** counting provider-incomplete
   or invalid captures as bad answers would bias the baseline and violate the
   benchmark rules.
3. **Oracle subjectivity:** thresholds for usefulness, specificity, evidence
   coverage and next-step quality require human-reviewed oracles; deterministic
   fixtures alone cannot establish them.
4. **Stale Story 0209 status:** planning against an unaccepted historical
   implementation could misstate the available baseline or duplicate work.
5. **Loss of uncertainty visibility:** collapsing truncation, freshness
   uncertainty and insufficient evidence into one low-confidence state would
   make the contract unusable for agents.

## Open Questions

1. Should the acceptability status remain evaluation-only in the first slice, or
   should an additive production field be approved?
2. What exact policy maps `freshness=UNKNOWN` to `BOUNDED` versus
   `STALE_OR_UNKNOWN`?
3. Which benchmark oracles and minimum thresholds are approved for human-quality
   scoring?
4. Can Story 0209 be formally accepted based on its recorded provider-free
   validation, with live and qualitative results remaining `NOT_EVALUATED`?

## Recommendation

Ready for planning

The repository is sufficiently understood for an Implementation Plan focused on
the evaluation artifact and baseline contract. The plan must resolve the open
questions above before proposing any production contract change.

## Implementation Readiness

No blocking technical prerequisite or ADR conflict was found for a bounded,
provider-free evaluation implementation. The primary prerequisites are workflow
approvals: Story 0209 acceptance must be clarified, and the open Story 0210
contract/oracle decisions must be fixed before implementation planning. No
database migration, new retrieval system, new provider capability or boundary
change is required by the current Story.

Repository Analysis completed.

Human approval required before Implementation Planning.

Awaiting explicit human approval.
