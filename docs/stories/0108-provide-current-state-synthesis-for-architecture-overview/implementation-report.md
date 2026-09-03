# Story 0108 — Implementation Report

## Status: IMPLEMENTED_AWAITING_FINAL_HUMAN_REVIEW — PRODUCT_GATE_NOT_PASSED AFTER RELATIONSHIP-AWARE RETRY (0/3 STRONG, 3/3 ACCEPTABLE, 0/3 WEAK)

## Summary

Implemented `architecture-overview-v2` with mandatory current-state synthesis output. Every completion produces a grounded architecture synthesis in addition to optional delta proposals, directly addressing the primary `OUTPUT_SYNTHESIS` bottleneck identified in the post-0106/0107 benchmark.

---

## Automated Verification

### Focused AI Tests

```text
command: python3 -m pytest tests/test_insight_generation_service.py -v
exit status: 0
test count: 19
failure count: 0
warnings: none
```

### Focused Backend Tests

```text
command: mvn test -Dtest="AiTaskResultServiceTest,AnalysisAiTaskTypeResolverTest,IntentCatalogTest"
exit status: 0
test count: 15
failure count: 0
warnings: none
```

### Full AI Engine Suite

```text
command: python3 -m pytest tests/ --tb=short
exit status: 0
test count: 103
failure count: 0
warnings: none
```

### Full Backend Suite

```text
command: mvn test -Dtest="!*Postgres*,!*Integration*,!*DevlogAi*"
exit status: 0
test count: 1016
failure count: 0
warnings: none
```

### Maven Clean Verify

```text
command: mvn clean verify -Dtest="!*Postgres*,!*Integration*,!*DevlogAi*" -DfailIfNoTests=false
exit status: 0
BUILD SUCCESS
test count: 1016
failure count: 0
```

`mvn clean verify` includes compilation, unit tests, and the surefire plugin. It is the project's primary quality gate.

### Required Formatting/Static Analysis Gates

```text
SEPARATE_FORMATTING_STATIC_GATE = NOT_CONFIGURED
```

Repository evidence: `mvn help:effective-pom` shows no checkstyle, spotbugs, pmd, or spotless plugin in the project configuration. The only checkstyle reference is `infinispan-checkstyle` (transitive dependency, not a project gate). No ruff, mypy, or other Python linters are configured in the project.

---

## Canonical Runtime Verification

### Benchmark Configuration

```text
PROJECT_ID = f3d56247-aada-4a76-982b-e6802c0b309c
REPOSITORY = https://github.com/Hopeful117/devlog-ai
PROVIDER = openai
MODEL = gpt-4.1-mini
TEMPERATURE = PROVIDER_DEFAULT
TOP_P = PROVIDER_DEFAULT
SEED = PROVIDER_DEFAULT
MAX_OUTPUT_TOKENS = 2000
SOURCE_STATE_STABLE = YES (same workspace, same HEAD)
PROVIDER_MODEL_CONFIG_STABLE = YES
GENERATION_CONFIGURATION_STABLE = YES
```

### Selected-Knowledge Stability

All 3 runs received identical selected-knowledge semantics:

```text
candidate Facts = 100
candidate Observations = 5
selected Facts = 40
selected Observations = 4
selected Insights = 10
architecture Knowledge = 5
repository Evidence = 60
selectionDigest = STABLE (same input context)
```

### RUN_1

```text
analysisId = ae6e9ade-bd96-4b78-a78d-0830e7256eb4
taskId = cae91b42-0ebf-4366-abdd-e419016afe6c
correlationId = 49af5070-d58a-453d-a29b-9e70816ead9a
Intent key/version = architecture-overview / v2
Analysis status = COMPLETED
durationSeconds = 16
promptVersion = architecture-overview-prompt-v2
promptDigest = 46e1a706bd08d8a30ee3aefefac5dde90f31659f4348e0e1f3b3e4fb5646e7d6
provider = openai
model = gpt-4.1-mini
VALID_PATH_MODEL_CALL_COUNT = 1
CORRECTION_RETRY = NO
synthesis = YES
proposals = 0
```

**Synthesis Output:**

Title: "Current-State Architecture Synthesis of the 'devlog-ai' Project"

Section 1: Overview — The 'devlog-ai' project functions as an AI-powered documentation assistant analyzing Git/GitHub activities to generate Markdown technical articles, preserve engineering decisions, and maintain up-to-date project documentation. This mission defines its structure and architectural choices.

Section 2: Architecture and Build System — The system is organized as a multi-module build system managed with Maven, allowing modular development of multiple components within a unified repository. It follows a multi-module architectural pattern with declared build modules such as 'backend', 'devlog-contracts', and 'mcp-server' supporting independent development and clear modular boundaries (Facts: BUILD_MODULE_DECLARED, MULTI_MODULE_BUILD).

Section 3: Application Layer and API — The core application is built on the Spring Boot framework, exposing REST API controllers for external communication and integration. Numerous REST controllers like AiTaskController, AnalysisController, and ProjectController handle domain-specific interactions, serving as the main interface for clients and other systems (Insights: Spring Boot REST API Application, REST API Exposure via Spring Boot Controllers).

Section 4: Containerization and Deployment — The project employs Docker containerization with Docker Compose for orchestrating multi-container environments. Evidence of Dockerfiles in multiple submodules (backend, ai-engine, frontend) and a central docker-compose.yml confirms this setup, facilitating consistent deployments across environments.

Section 5: Testing and Quality Assurance — Automated testing is integrated, including unit tests and specific integration tests located in dedicated test source directories. This ensures continuous integration practices and maintains quality and reliability through automated validation mechanisms.

Section 6: Architecture Decision Documentation — Architectural decisions are systematically documented using Architecture Decision Records (ADRs). The presence of ADR directories and multiple ADR documents ensures traceability and preserves a historical record of design choices, supporting audits and onboarding.

Grounding references: 14

**CURRENT_STATE_SYNTHESIS_QUALITY = ACCEPTABLE**

Justification: The synthesis communicates the main components (Maven multi-module, Spring Boot REST API, Docker Compose, ADR documentation), what each does, and grounds claims in evidence. However, it is underdeveloped on: (1) relationships — it does not explain how the backend, ai-engine, and frontend interact; (2) boundaries — no mention of the Core/AI-Engine separation or trust boundaries; (3) architectural principles — limited to technology inventory rather than explaining design rationale. A developer can form a basic mental model but lacks interaction patterns and boundary understanding.

**DELTA_CORRECTNESS = CORRECT**

Zero proposals are correct. The existing 5 trusted architecture knowledge items already cover the current architecture state. No genuinely new or enriching architecture knowledge was discovered.

---

### RUN_2

```text
analysisId = 15ef434e-8589-49ff-bbc2-0671bc74ab78
taskId = 43ae6431-37ae-402b-9f53-4a6d004df7ab
correlationId = 568eab9e-6b25-438d-a08e-c4d1895902e1
Intent key/version = architecture-overview / v2
Analysis status = COMPLETED
durationSeconds = 18
promptVersion = architecture-overview-prompt-v2
promptDigest = a6ea96bc07f3d66fed78d393921e004bcdceed36314b4401b7c1a94099ed3d43
provider = openai
model = gpt-4.1-mini
VALID_PATH_MODEL_CALL_COUNT = 1
CORRECTION_RETRY = NO
synthesis = YES
proposals = 0
```

**Synthesis Output:**

Title: "Current State Architecture Synthesis of DevLog AI Project"

Section 1: Project Overview — The 'devlog-ai' project is an AI-powered documentation assistant analyzing Git/GitHub activity to generate up-to-date technical Markdown articles, preserving engineering decisions and project documentation (evidence: insight:ce6912d5).

Section 2: Application Architecture — The project is implemented as a Spring Boot application exposing REST API controllers, which serve as the main interface for external communication and integration (evidence: insights 3e66fd4f and 48b8cef8; observations REST_CONTROLLER_DECLARED).

Section 3: Modularization and Build System — The codebase utilizes a multi-module structure managed with Maven to enable modularization and independent development of several modules within the same repository (facts: BUILD_MODULE_DECLARED and BUILD_SYSTEM_DETECTED; insight c5e4658e). The modules include backend, mcp-server, and devlog-contracts among others (facts: ff3d3eb8, 992975c8, 13824111).

Section 4: Containerization and Deployment — The project is containerized using Docker, with Dockerfiles present in multiple submodules (backend, ai-engine, frontend) and a docker-compose.yml file configured. This facilitates consistent deployment of multi-container environments (evidence: insight 0c4f1e1d; facts DOCKERFILE_PRESENT and DOCKER_COMPOSE_PRESENT).

Section 5: Testing Infrastructure — Automated unit tests and integration test files are present in the repository, supporting continuous integration and quality assurance in the development lifecycle (insight f25f5408; facts TEST_FILE_PRESENT and INTEGRATION_TEST_FILE_PRESENT).

Section 6: Architecture Documentation — The project employs Architecture Decision Records (ADRs) to document architectural decisions. This approach provides traceability and maintains a historical record of design choices impacting system evolution (insight 030c7f0e; decision decision:ae47a47d).

Grounding references: 17

**CURRENT_STATE_SYNTHESIS_QUALITY = ACCEPTABLE**

Justification: This run is slightly stronger than Run 1 because it explicitly cites individual fact and insight IDs as evidence, making grounding more transparent. The component description is clear. However, similar limitations remain: no component interaction/relationship description, no architectural boundaries, no design principles beyond "use ADRs." The synthesis is useful for understanding what exists but not how it works together.

**DELTA_CORRECTNESS = CORRECT**

Same reasoning as Run 1. Existing trusted architecture knowledge covers the current state.

---

### RUN_3

```text
analysisId = 2d3c9257-5361-481d-90dc-36634d5cf7f2
taskId = 7938ecf0-ba1d-44af-b78a-f5363df68da4
correlationId = ad9e2d3c-5170-4ba9-9179-f148b6d1c632
Intent key/version = architecture-overview / v2
Analysis status = COMPLETED
durationSeconds = 49
promptVersion = architecture-overview-prompt-v2
promptDigest = 4b3205bb740803d91a6a7a4910e33c011cb1b70761cfdb94f5da28d245a20f42
provider = openai
model = gpt-4.1-mini
VALID_PATH_MODEL_CALL_COUNT = 1
CORRECTION_RETRY = NO
synthesis = YES
proposals = 0
```

**Synthesis Output:**

Title: "Current State Architecture of the 'devlog-ai' Project"

Section 1: Project Overview — The 'devlog-ai' project is an active AI-powered documentation assistant designed to analyze Git/GitHub activity for generating technical Markdown articles, preserving engineering decisions, and keeping project documentation up to date (insight:ce6912d5).

Section 2: Architecture and Main Components — The project adopts a multi-module Maven build system, enabling modularization and independent development within several modules such as backend, mcp-server, and devlog-contracts (facts:47fe496e, 6364ec23, b565f5f5; insight:c5e4658e). The backend is primarily structured as a Spring Boot REST API application exposing multiple REST controllers that serve as the main interface for external communication and integration (fact:2ca48010; insight:3e66fd4f).

Section 3: Testing and Quality Assurance — Automated unit tests are present throughout the project with a structured test source directory, including specific integration test files, supporting continuous integration and quality assurance practices (fact:506cf2a4, fact:bff48fac; insight:f25f5408).

Section 4: Containerization and Deployment — The project employs containerization using Docker, with Dockerfiles found in backend, ai-engine, and frontend modules, alongside a docker-compose.yml configuration enabling orchestrated multi-container environments for consistent deployment (facts:39bd2acf, 63b5fc77, 90b5e708, a31926d9; insight:0c4f1e1d).

Section 5: Architecture Decision Documentation — Architectural decisions are systematically documented using Architecture Decision Records (ADRs), providing traceability and a historical record of design choices influencing system evolution and project audits (evidence:decision:ae47a47d, insight:030c7f0e).

Grounding references: 17

**CURRENT_STATE_SYNTHESIS_QUALITY = ACCEPTABLE**

Justification: This run has the strongest grounding of the three, citing 4-5 individual fact/insight IDs per section. Components and their responsibilities are clearly described. However, the same structural limitation persists: no inter-component relationships, no boundaries, no architectural principles beyond ADR documentation practice. The synthesis is a well-grounded component inventory, not a full architectural mental model.

**DELTA_CORRECTNESS = CORRECT**

Same reasoning as Run 1 and 2.

---

## Product Assessment Summary

```text
STRONG_COUNT = 0
ACCEPTABLE_COUNT = 3
WEAK_COUNT = 0
DELTA_CORRECT_COUNT = 3
```

### Per-Dimension Assessment

| Dimension | Run 1 | Run 2 | Run 3 |
|-----------|-------|-------|-------|
| A. Components | YES — Maven, Spring Boot, Docker, ADR | YES — same | YES — same |
| B. Responsibilities | PARTIAL — what each does, not how | PARTIAL — same | PARTIAL — same |
| C. Relationships | NO — no inter-component interaction | NO — same | NO — same |
| D. Boundaries | NO — no architectural boundaries | NO — same | NO — same |
| E. Principles | MINIMAL — ADR practice only | MINIMAL — same | MINIMAL — same |
| F. Grounding | YES — evidence cited | YES — explicit IDs | YES — strongest IDs |
| G. Uncertainty | YES — no unsupported claims | YES — same | YES — same |

The synthesis is consistently useful as a component inventory with grounded claims. The missing dimensions (relationships, boundaries, principles) represent the gap between ACCEPTABLE and STRONG.

---

## Canonical Persistence Verification

```text
CANONICAL_PERSISTENCE = PASS
```

Evidence:
- `GET /api/v1/analyses/{id}/result` returns identical synthesis on repeated reads (verified with 2 reads, 2s间隔)
- No model call triggered on result retrieval
- Synthesis tied to owning execution (analysisId)
- No fabricated synthesis for historical/incompatible results (verified: describe-project-v1 returns synthesis=null, analyze-engineering-decision-v1 returns synthesis=null)

---

## Trust-Boundary Verification

```text
SYNTHESIS_CREATES_VALIDATABLE_PROPOSAL = NO
SYNTHESIS_CREATES_TRUSTED_INSIGHT = NO
SYNTHESIS_CAN_BE_PROMOTED = NO
SYNTHESIS_AUTOMATIC_FUTURE_SELECTED_KNOWLEDGE = NO
SYNTHESIS_AUTOMATIC_PROJECT_EVIDENCE = NO
```

Evidence:
- `knowledge/` package: zero references to synthesis
- `collection/` package: zero references to synthesis
- `persistSynthesis()` writes only to `AiTask.synthesisSnapshot` (JSONB on execution-scoped entity)
- `toProposals()` is a separate code path from `persistSynthesis()`
- Synthesis never enters `ValidatableProposal` lifecycle
- ADR-065 architectural invariant #11: excluded from automatic future SelectedKnowledge

---

## Compatibility Verification

```text
V1_COMPATIBILITY = PASS
OTHER_INTENT_COMPATIBILITY = PASS
CALLBACK_ATOMICITY = ALL_OR_NOTHING_AFTER_BOUNDED_CORRECTION
PARTIAL_SUCCESS = NO
NEW_TASK_TYPE = NO
```

Evidence:
- `architecture-overview-v1`: registered, no synthesis requirement, results readable with synthesis=null ✓
- `architecture-overview-v2`: registered, synthesis required on COMPLETED, validated and persisted ✓
- `describe-project-v1`: unaffected, synthesis=null, works normally ✓
- `analyze-engineering-decision-v1`: unaffected, synthesis=null, works normally ✓
- Historical tasks with null synthesis remain readable ✓
- Invalid synthesis triggers bounded corrective retry (1 retry), then fails task ✓
- No PARTIAL_SUCCESS behavior introduced ✓
- No new AiTaskType introduced (v2 uses existing INSIGHT_GENERATION) ✓

---

## Model-Call Verification

```text
RUN_1: VALID_PATH_MODEL_CALL_COUNT = 1 (no corrective retry)
RUN_2: VALID_PATH_MODEL_CALL_COUNT = 1 (no corrective retry)
RUN_3: VALID_PATH_MODEL_CALL_COUNT = 1 (no corrective retry)
```

Evidence from AI Engine logs: each run shows exactly one `Prompt execution completed` entry with `architecture-overview-prompt-v2`. No `CORRECTIVE RETRY` entries observed.

---

## Files Changed (15 modified, 2 new)

### New Files
- `backend/src/main/resources/db/migration/V45__add_synthesis_snapshot_to_ai_tasks.sql`
- `backend/src/main/java/.../ai/engine/dto/AnalysisSynthesisResult.java`

### Modified Files — Backend (7)
- `AiTask.java` — synthesisSnapshot field
- `AiTaskResultRequest.java` — optional synthesis field
- `AiTaskResultServiceImpl.java` — validateSynthesis(), persistSynthesis()
- `IntentCatalog.java` — architecture-overview-v2 registration
- `AnalysisResultResponse.java` — SynthesisSection, SynthesisItem
- `AnalysisResultQueryServiceImpl.java` — buildSynthesis()

### Modified Files — AI Engine (6)
- `schemas/insight.py` — SynthesisSectionOutput, AnalysisSynthesisOutput
- `schemas/ai_task_result.py` — SynthesisSectionResult, AnalysisSynthesisResult
- `prompts/insight.py` — v2 template, synthesis instruction
- `services/insight_generation_service.py` — synthesis handling

### Modified Files — Tests (3)
- `AiTaskResultServiceTest.java` — 5 new tests
- `AnalysisAiTaskTypeResolverTest.java` — 1 new test
- `IntentCatalogTest.java` — updated expected count

### New Tests — Python (7)
- v2 no-delta synthesis, v2 synthesis+delta, v2 missing synthesis, v2 blank title, v2 empty sections, v1 backward compat, v2 fixture

### New Tests — Python Prompt Builder (6)
- v2 cross-evidence integration, v2 evidence enumeration, v2 conservative grounding, v2 synthesis prose, v1 unchanged, v2 delta contract

---

## Correction Cycle (Prompt Tuning)

### Root Cause Diagnosis

The initial product gate failed with 0/3 STRONG syntheses. Investigation confirmed:

1. **Evidence supports relationships/boundaries/principles**: The selected context contains 40 facts, 4 observations, 10 prior insights, 5 architecture knowledge items, and 60 repository evidence items. These independently describe components (backend, ai-engine, frontend), deployment (Docker Compose), build (Maven multi-module), testing, and ADR practice.

2. **Evidence gap is the bottleneck, not the prompt**: The repository evidence does NOT contain facts about backend-ai-engine communication, ai-task lifecycle, or frontend-backend interaction. Without this evidence, the model CANNOT synthesize inter-component relationships — it would be inventing.

3. **Prompt was improved but evidence remains insufficient**: The synthesis instruction was enhanced to explicitly require cross-evidence integration, relationship/boundary/principle communication, and discourage enumeration. However, the model still cannot synthesize relationships it has no evidence for.

### Prompt Correction Applied

**File**: `ai-engine/app/prompts/insight.py` — synthesis instruction for `architecture-overview-prompt-v2`

**Before**: Listed synthesis categories (components, responsibilities, relationships, boundaries, decisions) without teaching how to integrate evidence.

**After**: Added explicit SYNTHESIS OBJECTIVE with:
- Cross-evidence reasoning instruction ("reason across the selected Facts, Observations, Insights, existing trusted architecture knowledge, and repository evidence")
- Relationship/boundary/principle identification requirements
- Inventory vs synthesis prose examples
- Conservative grounding constraints ("Never invent a relationship, boundary, responsibility, or principle merely because it would improve the explanation")
- Evidence enumeration prohibition ("Do not list Fact IDs, Insight IDs, or file names in the prose body")

### Post-Correction Benchmark Results

```text
3 new canonical runs completed after prompt correction

RUN_4 (post-correction):
  analysisId = 7cd302a6-0c62-4137-a12c-842eb7afd6e8
  status = COMPLETED
  synthesis = YES (5 sections)
  proposals = 0
  groundingRefs = 29
  SYNTHESIS_QUALITY = ACCEPTABLE
  justification = Components described with responsibilities. Module boundaries mentioned.
                   Still no inter-component relationships (evidence gap).

RUN_5 (post-correction):
  analysisId = 84ac3c1c-1fb5-4bcc-8328-3465f8c8c38b
  status = COMPLETED
  synthesis = YES (5 sections)
  proposals = 0
  groundingRefs = 14
  SYNTHESIS_QUALITY = ACCEPTABLE
  justification = Module boundaries explicitly mentioned. Separation of concerns referenced.
                   Still no backend-ai-engine communication (evidence gap).

RUN_6 (post-correction):
  analysisId = aaf36038-2abb-4026-873c-4b6fa53f33a2
  status = COMPLETED
  synthesis = YES (4 sections)
  proposals = 0
  groundingRefs = 15
  SYNTHESIS_QUALITY = ACCEPTABLE
  justification = Architecture modularization described. Container boundaries mentioned.
                   Still no cross-component interaction (evidence gap).
```

### Post-Correction Assessment Summary

```text
STRONG_COUNT = 0
ACCEPTABLE_COUNT = 3
WEAK_COUNT = 0
Gate requires: at least 2/3 STRONG, remaining >= ACCEPTABLE
Result: 0 STRONG, 3 ACCEPTABLE — GATE STILL FAILED
```

### Correction Cycle Conclusion

The prompt correction improved the synthesis instruction but could not overcome the fundamental evidence gap. The model has no facts about backend-ai-engine communication, so it can only describe components and their known attributes — not how they interact. This is a knowledge-collection limitation, not a prompt or implementation limitation.

**To reach STRONG quality**, the system would need new evidence about:
- Backend→ai-engine REST callback pattern
- ai-task lifecycle (creation, execution, result persistence)
- Frontend→backend API communication
- Service boundaries in Docker Compose

This evidence would need to be collected by the repository context engine during analysis, which is outside the scope of Story 0108.

---

## Final Product Gate

```text
BENCHMARK_RUNS = 3 (initial) + 3 (post-correction) = 6 total

INITIAL GATE:
  STRONG_COUNT = 0
  ACCEPTABLE_COUNT = 3
  WEAK_COUNT = 0

POST-CORRECTION GATE:
  STRONG_COUNT = 0
  ACCEPTABLE_COUNT = 3
  WEAK_COUNT = 0

COMBINED: 0 STRONG, 6 ACCEPTABLE, 0 WEAK

DELTA_CORRECTNESS:
  6/6 CORRECT

CANONICAL_SYNTHESIS_PERSISTENCE:
  6/6 PASS

TRUST_BOUNDARY_VIOLATIONS:
  0

VALID_PATH_MODEL_CALLS:
  1 per valid-path execution (all 6 runs)
```

**STORY_0108_PRODUCT_GATE = FAILED**

The gate requires at least 2/3 STRONG syntheses. All 6 runs (3 initial + 3 post-correction) are ACCEPTABLE. The synthesis consistently provides a grounded component inventory with responsibilities and module boundaries, but does not communicate inter-component relationships or architectural principles beyond ADR documentation practice.

**Root Cause**: Evidence gap. The repository context engine does not collect facts about backend-ai-engine communication, ai-task lifecycle, or frontend-backend interaction. Without this evidence, the model cannot synthesize relationships — it would be inventing. The prompt was corrected to explicitly require cross-evidence synthesis, but the evidence remains insufficient.

**COMMIT_RECOMMENDATION = DO_NOT_COMMIT**

The implementation is architecturally correct and fully tested. The product-quality gate is not met due to an evidence-collection limitation, not an implementation or prompt defect. To reach STRONG quality, the repository context engine would need to collect additional evidence about service communication patterns, which is outside Story 0108 scope.

**STORY_0108_VERIFICATION_FAILED_PRODUCT_GATE**

---

## Story Artifacts Changed During Verification

```text
docs/stories/0108-provide-current-state-synthesis-for-architecture-overview/implementation-report.md — rewritten
docs/stories/0108-provide-current-state-synthesis-for-architecture-overview/story.md — status updated
ai-engine/app/prompts/insight.py — synthesis instruction corrected (prompt tuning)
ai-engine/tests/test_prompt_builder.py — 6 new regression tests added
```

---

## Self-Review

```text
PRODUCTION_CODE_CHANGED_DURING_THIS_VERIFICATION = YES (prompt tuning only)
TEST_CODE_CHANGED_DURING_THIS_VERIFICATION = YES (6 new regression tests)
DOCUMENTATION_CHANGED = YES (implementation-report.md, story.md status)
```

Git status confirms only pre-existing untracked documentation files plus the implementation changes. No staging, commit, push, or merge was performed.

---

## Terminal State

```text
STORY_0108_VERIFICATION_FAILED_PRODUCT_GATE
```

The implementation is architecturally correct and fully tested. The product-quality gate is not met because the synthesis outputs are ACCEPTABLE (useful component inventory) but not STRONG (missing inter-component relationships). This is an evidence-collection limitation: the repository context engine does not collect facts about service communication patterns, so the model cannot synthesize relationships without inventing. The prompt was corrected to explicitly require cross-evidence synthesis, but the evidence remains insufficient. To reach STRONG quality, new evidence about backend-ai-engine communication would need to be collected, which is outside Story 0108 scope.

---

## Superseding Post-Story-0109 Verification

Story 0109 implemented the previously missing deterministic evidence source by
collecting Docker Compose cross-service references and deriving an
`ARCHITECTURE_MODULARIZATION` observation. No additional prompt change or
selection-policy change was made.

Three fresh canonical `architecture-overview-v2` executions completed against
the final implementation (including hardened `depends_on` support, target
validation, grounding validation, and delta-contract enforcement):

| Run | Analysis ID | Quality | Delta | Model calls |
|---|---|---|---|---:|
| 1 | `1279de00-f99f-43ec-a42f-b9ba747731bc` | ACCEPTABLE | CORRECT (no genuine delta) | 1 |
| 2 | `feeec4fa-79b1-48a5-ba96-a63b73dd1e6d` | ACCEPTABLE | CORRECT (no genuine delta) | 1 |
| 3 | `a7a7ab1d-c642-4931-bde2-bcf49dc95905` | ACCEPTABLE | CORRECT (no genuine delta) | 1 |

Each run selected three `DOCKER_SERVICE_DEPENDS_ON` Facts, two
`DOCKER_SERVICE_ENV_REFERENCE` Facts, and one `ARCHITECTURE_MODULARIZATION`
Observation. The resulting syntheses describe the architecture accurately but
add unsupported scalability/maintainability claims and do not treat the explicit
directional dependency as a genuine enrichment delta. All three runs produced
zero proposals, used one valid path model call, and introduced no
trust-boundary violation.

```text
POST_STORY_0109_STRONG_COUNT = 0
POST_STORY_0109_ACCEPTABLE_COUNT = 3
POST_STORY_0109_WEAK_COUNT = 0
POST_STORY_0109_DELTA_CORRECT_COUNT = 3
STORY_0108_PRODUCT_GATE = NOT PASSED (requires ≥2/3 STRONG)
```

The earlier failed benchmarks in this report remain as historical evidence of
the extraction gap. This section supersedes their terminal product-gate result.
Full implementation and benchmark evidence is recorded in
`../0109-produce-deterministic-architectural-relationship-evidence/implementation-report.md`.

## Post-0109 Edge-Aware Correction Benchmark

Story 0109 resolved the original relationship-evidence gap, and the subsequent
edge-aware validator correction resolved the endpoint-co-occurrence false
negative. The remaining product-quality failure is downstream:
`gpt-4.1-mini` entered the corrective path in all three controlled runs but
produced two incorrectly typed `NEW` deltas and one invalid no-delta retry
result. The blocker is no longer missing relationship evidence.

```text
RUN_1 = ACCEPTABLE, DELTAS_PROPOSED, 1 NEW (incorrect; ENRICHES required)
RUN_2 = ACCEPTABLE, DELTAS_PROPOSED, 1 NEW (incorrect; ENRICHES required)
RUN_3 = WEAK, INVALID_LLM_OUTPUT after corrective retry
STRONG_COUNT = 0
ACCEPTABLE_COUNT = 2
WEAK_COUNT = 1
CORRECT_DELTA_COUNT = 0
TRUST_BOUNDARY_VIOLATIONS = 0
STORY_0108_PRODUCT_GATE = NOT PASSED
```

The valid syntheses now surface the explicit `backend -> ai-engine` dependency,
proving that the evidence reaches and influences generation. However, zero runs
satisfy the complete STRONG contract because delta correctness remains 0/3 and
one run produces no valid result. Detailed task identifiers and validation
evidence are recorded in Story 0109's implementation report.

## Post-Mandatory `deltaType` Benchmark

The implicit Python `NEW` fallback was removed and explicit classification became
mandatory. Deterministic validation passed (AI Engine 119/119; backend 1085/1085),
but the isolated three-run `gpt-4.1-mini` benchmark did not improve product output:

```text
RUN_1 = ACCEPTABLE, explicit NEW, incorrect delta
RUN_2 = WEAK, INVALID_LLM_OUTPUT after retry
RUN_3 = ACCEPTABLE, explicit NEW, incorrect delta
CORRECTIVE_RETRY_COUNT = 3
EXPLICIT_ENRICHES_COUNT = 0
EXPLICIT_NEW_COUNT = 2
STRONG_COUNT = 0
CORRECT_DELTA_COUNT = 0
PRODUCT_GATE = FAILED
```

The schema defect was real but was not materially causal to the two observed
`NEW` classifications: after removal, the model still emitted them explicitly.
The next evidence-supported candidate is a classification-aware and target-aware
corrective retry, reviewed separately before implementation. Full identifiers and
causal evidence are recorded in Story 0109's implementation report.

## Post-Relationship-Aware Retry Benchmark

The relationship-aware, classification-aware and target-aware corrective retry
was deployed without changing the initial prompt, schema, model, context
budgets, selection, or directional validator.

```text
RUN_1 = ACCEPTABLE, ENRICHES with exact target
RUN_2 = ACCEPTABLE, ENRICHES with exact target
RUN_3 = ACCEPTABLE, ENRICHES with exact target
CORRECTIVE_RETRY_COUNT = 3
EXPLICIT_ENRICHES_COUNT = 3
CORRECT_TARGET_COUNT = 3
STRONG_COUNT = 0
ACCEPTABLE_COUNT = 3
WEAK_COUNT = 0
CORRECT_DELTA_COUNT = 3
INCORRECT_DELTA_COUNT = 0
FAILED_DELTA_COUNT = 0
PRODUCT_GATE = FAILED
```

The delta classification and target selection are now correct in all three
runs. The ACCEPTABLE syntheses still extrapolate unsupported runtime
communication claims from Docker Compose `depends_on` evidence, which only
proves startup ordering and service availability, not runtime interaction,
HTTP, API, or data-flow communication.

The remaining product-quality gap is the synthesis grounding overclaim.
Full identifiers and causal evidence are recorded in Story 0109's
implementation report.
