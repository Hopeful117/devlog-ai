# Story 0109 — Produce Deterministic Architectural Relationship Evidence

## Status

**IMPLEMENTED_AWAITING_FINAL_HUMAN_REVIEW — PRODUCT_GATE_NOT_PASSED AFTER RELATIONSHIP-AWARE RETRY (0/3 STRONG, 3/3 ACCEPTABLE, 0/3 WEAK)**

## Problem

Story 0108 implemented mandatory current-state synthesis for `architecture-overview-v2`. Technical verification passed. Product-quality gate failed: 0/6 STRONG, 6/6 ACCEPTABLE.

Post-0108 investigation confirmed:

```text
HYPOTHESIS_RESULT = CONFIRMED
FIRST_LOSS_POINT = B_EXTRACTION_GAP
CURRENT_COLLECTOR_CAPABILITY = ENTITY_PRESENCE_FACTS
RELATIONSHIP_FACTS_PRODUCED = 0
RELATIONSHIP_CAPABLE_OBSERVATION_TYPES = 5 (dead code)
PRODUCING_RULES = 0
```

The model cannot synthesize architectural relationships that were never deterministically collected.

## Scope

Smallest deterministic vertical slice: Docker Compose service wiring extraction → ARCHITECTURE_MODULARIZATION observation.

### In Scope

- New FactTypes: `DOCKER_SERVICE_DEPENDS_ON`, `DOCKER_SERVICE_ENV_REFERENCE`
- Enhanced DockerCollector: parse `depends_on` and environment variable cross-references between services
- New ObservationRule: derive `ARCHITECTURE_MODULARIZATION` from multi-service compose with inter-service references
- Deterministic tests: positive, negative, false-positive, provenance, determinism
- Pipeline verification: full trace from extraction to prompt
- Story 0108 benchmark rerun (3 fresh analyses)

### Non-Goals

- HTTP client detection (SpringCollector enhancement)
- Async/messaging pattern detection
- Authentication layer detection
- Test coverage detection
- Prompt tuning (already corrected in Story 0108)
- Knowledge selection modification (Story 0107)
- ADR-064 resumption
- Generic repository graph construction

## Functional Requirements

1. DockerCollector SHALL parse `depends_on` directives from docker-compose files
2. DockerCollector SHALL parse environment variable values that reference other service names
3. Each extracted relationship SHALL be persisted as a Fact with provenance
4. DeterministicObservationEngine SHALL derive ARCHITECTURE_MODULARIZATION when multiple services exist with inter-service references
5. The observation SHALL be selectable by existing knowledge selection pipeline
6. The observation SHALL be visible to the model via existing prompt projection

## Acceptance Criteria

1. Deterministic tests pass for all new extraction rules
2. Full backend test suite passes
3. 3 fresh architecture-overview-v2 benchmark runs complete
4. At least 2/3 runs achieve STRONG synthesis quality
5. Delta correctness remains 3/3 CORRECT
6. Trust boundary violations remain 0
7. No prompt changes (isolates evidence effect)

## Verification Outcome

The deterministic extraction criteria passed: relationship evidence traverses
collection, selection and prompt projection. The product criterion did not pass.
The edge-aware validator correction forced `gpt-4.1-mini` to reconsider the
previous false `NO_MATERIAL_DELTA`, but two completed runs emitted the required
relationship as `NEW` instead of `ENRICHES`, and a third run failed after its
corrective retry still omitted the required delta. No run met the complete
STRONG contract.

Detailed evidence is recorded in `implementation-report.md`.
