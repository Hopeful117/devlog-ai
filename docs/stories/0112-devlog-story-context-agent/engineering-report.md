# Story 0112 - Engineering Report

## Status

**IMPLEMENTED_AND_MERGED - NOT READY FOR ACCEPTANCE**

## Delivered Architecture

```text
Human invocation
    -> REST controller or MCP tool
    -> Java/Core AnalyzeStoryContextUseCase
    -> scoped EngineeringContext
    -> AiTask with engineering-story-context-analysis-v1
    -> Python structured generation and defensive validation
    -> Java/Core callback
    -> durable StoryContextAnalysis
```

The repository now contains each structural component of this path. `StoryContextAnalysis` is modeled as a dedicated durable artifact rather than trusted knowledge, and `AiTask` remains the technical execution record.

## Contract Model

The implementation introduces explicit objective, architecture, decision, evidence, history, constraint, impact, uncertainty, missing-information, implementation-question, confidence, provenance, and classification areas. Evidence uses canonical references with optional navigational resources, and relationship-bearing findings use the ADR-067 relation vocabulary.

The Java and Python representations are not yet transport-compatible for all fields. Contract parity must be restored and protected by a shared serialization fixture.

## Authority Assessment

Target authority model:

```text
JAVA_CORE = context + trust + grounding + validation + durability
PYTHON = generation + defensive validation + one corrective retry
REST_AND_MCP = thin adapters
```

Observed merged behavior diverges from this target because the Java grounding contract is not delivered to Python and Core does not authoritatively validate the returned Story Context result before persistence.

## Execution And Durability Assessment

The migration `V46__create_story_context_analyses_table.sql`, entity, repository, and callback persistence path exist. However, the current task lifecycle does not transition submitted work out of `CREATED`, and callback persistence lacks the Story identity it expects. The REST adapter also acknowledges submission before durable completion while MCP expects an immediate result.

Therefore the D16 success boundary is not currently achieved end to end.

## Evaluation Assessment

The ADR-066 harness recognizes `STORY_CONTEXT_ANALYSIS` and contains a versioned scenario/replay. The recorded scenario passes its implemented gate, but that gate is narrower than D21 and uses synthetic context. It is useful regression scaffolding, not evidence of the required real-Story human usefulness acceptance.

## Quality Evidence

- final implementation commit records 1,099 backend tests and 168 Python tests passing;
- both recorded ADR-066 scenarios passed;
- PR #95 reports successful backend/JaCoCo, frontend unit/build, frontend E2E, and aggregate checks;
- retrospective review found critical gaps not exercised by those suites.

## Required Corrective Work

1. Make nullable Story scope serialization safe and preserve `storyId` in task provenance.
2. Complete the task submission state transition before accepting callbacks.
3. Align Java and Python result contracts and add cross-language contract tests.
4. Transport the Core-owned grounding contract unchanged to Python.
5. Add authoritative Core validation for grounding, trust, relationships, classification, digest, and forbidden outputs.
6. Choose coherent REST/MCP completion semantics that satisfy D16.
7. Forward human guidance without granting it authority.
8. Enforce snapshot immutability in the entity and schema.
9. Expand deterministic tests and perform the required real Story qualitative evaluation.

## Final Assessment

```text
STRUCTURAL_VERTICAL_SLICE = PRESENT
PRIMARY_END_TO_END_FLOW = BLOCKED
TRUST_AND_GROUNDING_AUTHORITY = INCOMPLETE
MERGE_CI = PASSED
STORY_ACCEPTANCE_GATE = NOT_PASSED
NEXT_STATE = CORRECTIVE_IMPLEMENTATION_REQUIRED
```
