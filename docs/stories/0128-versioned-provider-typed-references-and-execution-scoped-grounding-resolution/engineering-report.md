# Story 0128 - Engineering Report

## Status

**IMPLEMENTED - UNCOMMITTED, AWAITING HUMAN ACCEPTANCE**

## Delivered Architecture

```text
Authorized SelectedKnowledge
        |
        +--> Core typed projection + groundingCandidates
        |          |
        |          +--> Python v3 typed output
        |                         |
        +--> AiTask mapping snapshot
                                   |
Callback correlationId --> Java snapshot resolver
                                   |
                         proposal-domain identity
                                   |
                         PROPOSED Insight proposal
```

## Authority Assessment

```text
JAVA_CORE = allocation, scope, capability and callback resolution authority
PYTHON = typed schema validation, subset validation and corrective retry
PROVIDER_REFERENCES = opaque execution handles, never domain identities
AI_OUTPUT = proposal input, never trusted knowledge
```

## Compatibility

- New architecture reviews from the Angular UI select `architecture-overview-v3`.
- Existing v1/v2 analyses continue using their stored intent version.
- Legacy callbacks remain supported and typed fields are rejected outside v3.
- A null mapping snapshot is not reconstructed for a legacy task.

## Failure Semantics

The Core resolver distinguishes unknown references, namespace mismatches, scope
mismatches, unauthorized grounding capabilities and mapping failures before
proposal persistence. Resolution uses the exact originating task snapshot and
does not fall back to current selected knowledge.

## Quality Evidence

- Backend full suite: 1,324 tests passed.
- AI Engine full suite: passed.
- Frontend: 260 tests passed; lint and formatting passed.
- `git diff --check`: passed.

## Final Assessment

```text
VERSIONED_V3_CONTRACT = PRESENT
EXECUTION_SCOPED_RESOLUTION = PRESENT
LEGACY_COMPATIBILITY = PRESERVED
JAVA_AUTHORITY = PRESERVED
TRUST_BOUNDARY = PRESERVED
QUALITY_GATES = PASSED
STORY_ACCEPTANCE = AWAITING_HUMAN_REVIEW
```
