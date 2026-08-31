# Story 0106 Corrective Engineering-Decision Consistency Review

## Status

- Status: `INVESTIGATION_COMPLETE`
- Scope: `REPORTING_ONLY`
- Date: `2026-08-31`

## 1. Investigation Metadata

- Investigation type: `CORRECTIVE_BENCHMARK_CONSISTENCY_REVIEW`
- Story: `0106-intent-aware-structured-context-utilization-for-analysis-prompts`
- Governing ADR: `ADR-064` (KEEP_PAUSED)
- Prior investigation: `story-0106-engineering-decision-benchmark-variance-investigation`
- Baseline SHA: `70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a`
- Branch: `story/0106-intent-aware-context-utilization`

## Superseding Precision Note

This report preserves the initial and corrective runtime benchmark history. Later exact-input replay produced five clean results, and later pipeline investigation identified an Analysis-local Fact UUID ranking dependency isolated into Story 0107. Therefore, passages assigning the `4/1/1` inconsistency solely to LLM nondeterminism are superseded; sole historical causality was not demonstrated.

## 2. Scope

This investigation inspects three fresh `analyze-engineering-decision-v1` benchmark runs, classifies every generated proposal, and evaluates whether the corrective implementation (Options A+B+C+D) produces consistent decision-eligibility semantics.

Explicit non-actions:

- no production code changes
- no prompt changes
- no test changes
- no schema changes
- no commits
- no pushes
- no merges

## 3. Corrective Implementation Being Evaluated

### Options Implemented

- **Option A**: Stronger negative guardrails — technology presence alone never qualifies
- **Option B**: Stronger positive emission gate — explicit decision evidence or genuine strong convergence required
- **Option C**: Explicit strong-convergence definition — independent decision signals required, not repeated implementation-state observations
- **Option D**: Selectivity guidance — fewer well-supported decisions preferred over broad coverage

### Technical Verification

- Targeted tests: `34/34 PASS`
- Full AI-engine suite: `97/97 PASS`
- Prompt size: BEFORE=111661, AFTER=116093, DELTA=+4432 (+3.97%)

### CRITICAL DEPLOYMENT NOTE

The corrective prompt changes exist in the worktree (`ai-engine/app/prompts/decision.py`) but are **NOT deployed to the running Docker container**. The ai-engine container was rebuilt before the corrective implementation. The benchmark runs therefore execute the **pre-corrective prompt**, not the corrective version.

This means the benchmark results reflect baseline behavior, not corrective behavior. The investigation evaluates what the current runtime produces, which informs whether the corrective prompt design is likely sufficient but does NOT validate the corrective implementation at runtime.

## 4. Benchmark Source

- Canonical project: `f3d56247-aada-4a76-982b-e6802c0b309c` (devlog-ai)
- Intent: `analyze-engineering-decision-v1`
- Model: `gpt-4.1-mini`
- Provider: `openai`
- Analysis type: `ARCHITECTURE_REVIEW`
- Raw results: `/tmp/decision_benchmark_results.json`

## 5. Classification Model

Each proposal receives one **primary classification**:

| Category | Definition |
|---|---|
| **EXPLICIT** | Directly supported by explicit project decision evidence (ADR, documented choice, trusted knowledge) |
| **CONVERGENT** | No single artifact, but multiple genuinely independent signals converge on the same choice |
| **TECHNOLOGY_ONLY** | Technology/framework presence treated as sufficient proof of engineering decision |
| **GENERIC** | Rationale is primarily generic software-engineering knowledge, not project-specific |
| **CAUSAL** | Invented or overstated historical motivation or causality |

Eligibility verdicts:

| Verdict | Meaning |
|---|---|
| `ELIGIBLE_EXPLICIT` | Explicit decision evidence exists |
| `ELIGIBLE_STRONG_CONVERGENCE` | Genuinely independent signals converge |
| `REJECT_TECHNOLOGY_ONLY` | Only technology presence, no decision evidence |
| `REJECT_GENERIC` | Generic knowledge substituted for project evidence |
| `REJECT_UNSUPPORTED_CAUSALITY` | Causality claimed without support |
| `REJECT_INSUFFICIENT_DECISION_EVIDENCE` | No sufficient decision evidence |

## 6. Run 1 — Proposal-by-Proposal Classification

**Run 1 Analysis ID:** `d2c32147-811c-46ed-ba05-428cbba38fc7`

### Proposal 1/4: Containerization with Docker and Docker Compose

- **ID:** `dffaa8d8-e9ad-4083-b743-5138a9935790`
- **Title:** Containerization of the project using Docker and Docker Compose
- **Context:** "The project contains Dockerfiles for different components and a Docker Compose configuration for orchestrating them."
- **Choice:** "Package and deploy the project components as Docker containers and use Docker Compose for multi-container orchestration during development and testing."
- **Rationale:** "Containerization ensures environment consistency and simplifies deployment by encapsulating dependencies. Docker Compose enables easy management of multiple related containers in local or testing environments."
- **Consequences:** "While adding complexity in build and deployment workflows, containerization improves portability, environment reproducibility, and scalability readiness."
- **Primary Classification:** `TECHNOLOGY_ONLY`
- **Evidence:** DOCKERFILE_PRESENT facts (3), DOCKER_COMPOSE_PRESENT fact (1), CONTAINERIZED_PROJECT observation (1), ARCHITECTURAL insight about Docker (1)
- **Independence:** All evidence signals describe the same implementation state (Docker files exist). Not independent decision signals.
- **Decision supported:** Technology presence is supported. Engineering decision is NOT supported.
- **Rationale supported:** Generic containerization benefits, not project-specific motivation.
- **Causality claimed:** Yes — implies the project chose containerization for consistency/scalability.
- **Causality supported:** No — no evidence of the decision-making process.
- **Eligibility verdict:** `REJECT_TECHNOLOGY_ONLY`

### Proposal 2/4: Spring Boot REST API

- **ID:** `39d69e9b-5755-47eb-8a70-e048f473ea2d`
- **Title:** Implementation of the application as a Spring Boot REST API
- **Context:** "The application is implemented using Spring Boot and exposes REST API controllers."
- **Choice:** "Develop the application as a Spring Boot-based REST API service, using Spring Boot's frameworks and conventions."
- **Rationale:** "Spring Boot facilitates rapid development of RESTful services with embedded servers and extensive ecosystem support, reducing boilerplate code and increasing maintainability."
- **Consequences:** "This choice standardizes the backend technology stack and enables integration via REST APIs but limits the architectural style accordingly and requires team familiarity with Spring Boot."
- **Primary Classification:** `TECHNOLOGY_ONLY`
- **Evidence:** SPRING_BOOT_DETECTED fact (1), ARCHITECTURAL insight about Spring Boot REST (1), TECHNOLOGY insight about Spring Boot (1)
- **Independence:** All evidence describes Spring Boot usage. Not independent decision signals.
- **Decision supported:** Technology presence supported. Engineering decision NOT supported.
- **Rationale supported:** Generic Spring Boot benefits, not project-specific.
- **Causality claimed:** Yes — implies project chose Spring Boot for rapid development.
- **Causality supported:** No.
- **Eligibility verdict:** `REJECT_TECHNOLOGY_ONLY`

### Proposal 3/4: Multi-Module Maven Build

- **ID:** `5feeb11e-4456-45c6-b952-8f1465657e5d`
- **Title:** Adoption of a multi-module build system using Maven
- **Context:** "The project build configuration declares multiple modules and uses Maven as the build system."
- **Choice:** "Structure the project as a Maven multi-module build, splitting functionality across several independently developed modules within the same repository."
- **Rationale:** "A multi-module build enables modularization, separation of concerns, and independent development and testing of different parts of the application. It improves build performance and dependency management at scale."
- **Consequences:** "This approach requires managing inter-module dependencies carefully and can increase build complexity but results in a more maintainable, scalable project structure."
- **Primary Classification:** `TECHNOLOGY_ONLY`
- **Evidence:** BUILD_MODULE_DECLARED facts (3), BUILD_SYSTEM_DETECTED fact (1), ARCHITECTURAL insight about multi-module (1)
- **Independence:** All evidence describes Maven multi-module usage. Not independent decision signals.
- **Decision supported:** Technology presence supported. Engineering decision NOT supported.
- **Rationale supported:** Generic build-system benefits.
- **Causality claimed:** Yes.
- **Causality supported:** No.
- **Eligibility verdict:** `REJECT_TECHNOLOGY_ONLY`

### Proposal 4/4: ADR Documentation

- **ID:** `93b9c02a-1747-4685-95e6-2896ea39bf8e`
- **Title:** Utilization of Architecture Decision Records (ADRs) for documenting architectural choices
- **Context:** "The project documents its architectural decisions using Architecture Decision Records (ADRs), as evidenced by the presence of ADR documentation files and an explicit architectural decision to maintain ADRs as the standard practice."
- **Choice:** "Maintain and use ADRs as the standard method for documenting and managing architectural decisions throughout the project lifecycle."
- **Rationale:** "Using ADRs ensures traceability, clarity, and historical tracking of architectural choices. It aids onboarding, audits, and future evolution of the system, improving communication among team members and stakeholders."
- **Consequences:** "This practice facilitates better documentation quality and project knowledge management. It imposes discipline to write decisions in a structured, accessible format, which can increase initial overhead but pays off via improved maintainability and transparency."
- **Primary Classification:** `EXPLICIT`
- **Evidence:** ADR_DIRECTORY_PRESENT fact (1), DECISION repository evidence (`decision:ae47a47d-65fa-4a30-810c-f114b37755bd`) with explicit summary: "Document Architectural Decisions Using Architecture Decision Records (ADRs) — Maintain ADRs as the standard practice to document and manage architectural decisions throughout the project lifecycle.", multiple ADR-related validated insights (3)
- **Independence:** Genuine independence — ADR directory presence, explicit decision evidence, and validated knowledge are different evidence layers.
- **Decision supported:** YES — explicit decision artifact exists with explicit choice.
- **Rationale supported:** YES — rationale closely matches the explicit ADR decision evidence.
- **Causality claimed:** Limited — accurately reflects documented decision.
- **Causality supported:** YES.
- **Eligibility verdict:** `ELIGIBLE_EXPLICIT`

## 7. Run 2 — Proposal-by-Proposal Classification

**Run 2 Analysis ID:** `9c645089-a006-4433-9ec6-3bdc1062ec97`

### Proposal 1/5: Automated Testing

- **ID:** `e12c9c1e-125e-4389-8e09-15546ca7946f`
- **Title:** Include Automated Unit and Integration Tests in the Project
- **Context:** "Automated unit tests and integration test files are present in the repository, with continuous integration practices to ensure quality assurance during development."
- **Choice:** "Maintain and extend a comprehensive suite of automated unit and integration tests to verify system correctness."
- **Rationale:** "Automated tests provide early defect detection, enable continuous integration, ensure code quality, and support maintainability and refactoring."
- **Consequences:** "Increased confidence in code changes and system stability. Overhead to keep tests updated and relevant. Requires investment in testing infrastructure and developer discipline."
- **Primary Classification:** `TECHNOLOGY_ONLY`
- **Evidence:** INTEGRATION_TEST_FILE_PRESENT fact (1), TECHNOLOGY insight about testing (1), DOCUMENTATION insight about testing (1)
- **Independence:** All evidence describes test file presence. Not independent decision signals.
- **Decision supported:** Technology presence supported. Engineering decision NOT supported.
- **Rationale supported:** Generic testing benefits.
- **Causality claimed:** Yes — implies project chose to "include" testing.
- **Causality supported:** No.
- **Eligibility verdict:** `REJECT_TECHNOLOGY_ONLY`

### Proposal 2/5: Spring Boot REST API

- **ID:** `b549735b-1089-446e-b103-e8639cd6c417`
- **Title:** Develop a Spring Boot REST API Application
- **Context:** "The project uses Spring Boot framework and exposes REST API controllers as the main external communication interface."
- **Choice:** "Implement the application backend using Spring Boot, exposing REST API endpoints via controllers."
- **Rationale:** "Spring Boot accelerates development with built-in defaults and integration, while REST APIs provide a widely accepted, scalable, and interoperable service interface."
- **Consequences:** "Standardized backend architecture with robust ecosystem support. Simplifies integration with other systems and frontend clients. Developers must adhere to best practices for REST API design in Spring Boot."
- **Primary Classification:** `TECHNOLOGY_ONLY`
- **Evidence:** Same Spring Boot presence signals as Run 1.
- **Decision supported:** No.
- **Rationale supported:** Generic.
- **Causality claimed:** Yes.
- **Causality supported:** No.
- **Eligibility verdict:** `REJECT_TECHNOLOGY_ONLY`

### Proposal 3/5: Docker Containerization

- **ID:** `3688a809-28ee-465e-8620-bb1ebfa39115`
- **Title:** Containerize the Project Using Docker and Docker Compose
- **Context:** "The project defines Dockerfiles for backend, frontend, and AI-engine components and includes a Docker Compose file for orchestrating containers."
- **Choice:** "Containerize all application components using Docker and manage multi-container orchestration via Docker Compose."
- **Rationale:** "Containerization ensures consistency across environments, simplifies deployment and scaling, and Docker Compose facilitates multi-service local development and testing."
- **Consequences:** "Easier deployment and environment replication. Enables CI/CD pipelines leveraging container images. Requires maintenance of Dockerfiles and Compose configurations, as well as knowledge of container ecosystem."
- **Primary Classification:** `TECHNOLOGY_ONLY`
- **Evidence:** Same Docker presence signals as Run 1.
- **Decision supported:** No.
- **Rationale supported:** Generic.
- **Causality claimed:** Yes.
- **Causality supported:** No.
- **Eligibility verdict:** `REJECT_TECHNOLOGY_ONLY`

### Proposal 4/5: Multi-Module Maven Build

- **ID:** `4569632d-184c-493b-88eb-0d24bffccab2`
- **Title:** Implement a Multi-Module Maven Build System
- **Context:** "The project is structured with multiple declared build modules and uses Maven as the build system."
- **Choice:** "Use a multi-module Maven build to organize the project into modular components, enabling independent development and build of separate modules within the repository."
- **Rationale:** "A multi-module build facilitates modularization, improves compile-time and deployment management, and supports codebase scalability and maintainability."
- **Consequences:** "Build complexity is managed via modularization. Developers can work on specific modules independently. Requires management of module dependencies and consistent versioning."
- **Primary Classification:** `TECHNOLOGY_ONLY`
- **Evidence:** Same Maven presence signals as Run 1.
- **Decision supported:** No.
- **Rationale supported:** Generic.
- **Causality claimed:** Yes.
- **Causality supported:** No.
- **Eligibility verdict:** `REJECT_TECHNOLOGY_ONLY`

### Proposal 5/5: ADR Documentation

- **ID:** `1cfa5c79-3db2-4c79-b5f5-b71bf6e12cdd`
- **Title:** Use Architecture Decision Records (ADRs) to Document Architectural Decisions
- **Context:** "The project maintains a dedicated 'docs' directory containing Markdown files and documents engineering stories, implementation plans, and repository analyses. It has an 'docs/decisions' directory with ADR files, evidencing the use of ADRs for architecture documentation."
- **Choice:** "Adopt and maintain Architecture Decision Records (ADRs) as the standard practice to document architectural decisions throughout the project lifecycle."
- **Rationale:** "Using ADRs enhances traceability and provides a historical record of design choices impacting the system, which improves communication among team members and stakeholders, facilitates onboarding, audits, and assists future system evolution."
- **Consequences:** "Clear and structured documentation of architectural decisions is maintained, improving knowledge sharing and decision traceability. Requires discipline and process for writing and updating ADRs consistently."
- **Primary Classification:** `EXPLICIT`
- **Evidence:** Same explicit ADR decision evidence as Run 1. ADR_DIRECTORY_PRESENT, `decision:ae47a47d-...`, validated ADR insights.
- **Decision supported:** YES.
- **Rationale supported:** YES.
- **Causality claimed:** Limited — accurately reflects documented decision.
- **Causality supported:** YES.
- **Eligibility verdict:** `ELIGIBLE_EXPLICIT`

## 8. Run 3 — Proposal-by-Proposal Classification

**Run 3 Analysis ID:** `2d020ecb-a770-42fa-8c1e-4cd737a0cf8b`

### Proposal 1/4: Docker Containerization

- **ID:** `dbcfda71-c822-423a-9324-9ddcce4aa3bb`
- **Title:** Containerize the Project with Docker and Use Docker Compose for Orchestration
- **Context:** "Dockerfiles exist at multiple modules (backend, frontend, AI engine), and a Docker Compose configuration is present to orchestrate multi-container setup."
- **Choice:** "Containerize each service/module with Docker images and orchestrate them locally and in deployments using Docker Compose."
- **Rationale:** "Containerization ensures consistent environments across development, testing and production; Docker Compose simplifies management of multi-container applications with defined service relationships."
- **Consequences:** "Improved deployment consistency and environment parity; however, introduces overhead in learning containerization, and adds complexity in orchestration during deployment."
- **Primary Classification:** `TECHNOLOGY_ONLY`
- **Eligibility verdict:** `REJECT_TECHNOLOGY_ONLY`

### Proposal 2/4: Spring Boot REST API

- **ID:** `f587919a-a53c-4f5f-bc0b-bc664e80a132`
- **Title:** Develop REST API Application Using Spring Boot
- **Context:** "The application exposes REST API controllers implemented with Spring Boot framework."
- **Choice:** "Implement the application backend as a Spring Boot REST API service to handle external communication and integration."
- **Rationale:** "Spring Boot provides a convention-over-configuration approach, reducing boilerplate code with integrated REST support, improving developer productivity and maintainability."
- **Consequences:** "Using Spring Boot makes the system more modular and easier to test, but ties the application to the Spring ecosystem and requires familiarity with Spring conventions."
- **Primary Classification:** `TECHNOLOGY_ONLY`
- **Eligibility verdict:** `REJECT_TECHNOLOGY_ONLY`

### Proposal 3/4: Multi-Module Maven Build

- **ID:** `c31470c1-150f-432d-8934-19b0041d4b42`
- **Title:** Adopt Multi-Module Build Architecture Using Maven
- **Context:** "The project is structured into multiple build modules and employs Maven as its build system."
- **Choice:** "Organize the project into a multi-module Maven build setup with separate modules for backend, frontend, ai-engine, and contracts."
- **Rationale:** "This structure promotes modularity and independent development of components, improving maintainability and build efficiency."
- **Consequences:** "Modularization facilitates independent testing and deployment but requires managing module dependencies and coordination between components."
- **Primary Classification:** `TECHNOLOGY_ONLY`
- **Eligibility verdict:** `REJECT_TECHNOLOGY_ONLY`

### Proposal 4/4: ADR Documentation

- **ID:** `dd84468f-e94c-4e3b-9585-8c45a1edbc8a`
- **Title:** Use Architecture Decision Records (ADR) to Document Architectural Decisions
- **Context:** "The devlog-ai project maintains a directory of ADR files and explicitly documents architectural decisions through this format."
- **Choice:** "Establish and maintain ADRs as the standard method for documenting and managing architectural decisions throughout the project lifecycle."
- **Rationale:** "ADRs provide traceability, clear historical tracking of design choices, and improve communication among team members and stakeholders, supporting project evolution and future audits."
- **Consequences:** "A structured documentation of architectural decisions enables easier onboarding, auditing, and evolving the architecture with a clear rationale; however, maintaining ADRs requires discipline and may add overhead to the documentation processes."
- **Primary Classification:** `EXPLICIT`
- **Eligibility verdict:** `ELIGIBLE_EXPLICIT`

## 9. Per-Run Classification Summary

### Run 1

| Metric | Value |
|---|---|
| Total proposals | 4 |
| EXPLICIT | 1 |
| CONVERGENT | 0 |
| TECHNOLOGY_ONLY | 3 |
| GENERIC | 0 |
| CAUSAL | 0 |
| Eligible | 1 |
| Rejected | 3 |
| **RUN_DECISION_ELIGIBILITY_QUALITY** | **FAILED** |

Reason: 3 of 4 proposals are technology-presence-only. Only ADR is legitimately eligible.

### Run 2

| Metric | Value |
|---|---|
| Total proposals | 5 |
| EXPLICIT | 1 |
| CONVERGENT | 0 |
| TECHNOLOGY_ONLY | 4 |
| GENERIC | 0 |
| CAUSAL | 0 |
| Eligible | 1 |
| Rejected | 4 |
| **RUN_DECISION_ELIGIBILITY_QUALITY** | **FAILED** |

Reason: 4 of 5 proposals are technology-presence-only. Only ADR is legitimately eligible. Run 2 also has higher proposal count (5 vs 4), suggesting coverage pressure.

### Run 3

| Metric | Value |
|---|---|
| Total proposals | 4 |
| EXPLICIT | 1 |
| CONVERGENT | 0 |
| TECHNOLOGY_ONLY | 3 |
| GENERIC | 0 |
| CAUSAL | 0 |
| Eligible | 1 |
| Rejected | 3 |
| **RUN_DECISION_ELIGIBILITY_QUALITY** | **FAILED** |

Reason: 3 of 4 proposals are technology-presence-only. Only ADR is legitimately eligible.

## 10. Cross-Run Semantic Comparison

### A. Decision Identity Consistency

| Decision | Run 1 | Run 2 | Run 3 | Semantically Equivalent |
|---|---|---|---|---|
| Docker containerization | Proposal 1 | Proposal 3 | Proposal 1 | YES |
| Spring Boot REST API | Proposal 2 | Proposal 2 | Proposal 2 | YES |
| Multi-module Maven | Proposal 3 | Proposal 4 | Proposal 3 | YES |
| ADR documentation | Proposal 4 | Proposal 5 | Proposal 4 | YES |
| Automated testing | — | Proposal 1 | — | NEW_IN_RUN_2 |

All three runs identify the same four core technology decisions. Run 2 additionally surfaces automated testing. The same underlying decisions are consistently identified across runs.

### B. Eligibility Consistency

All three runs apply the same effective threshold: ADR is eligible (explicit decision evidence), all others are technology-presence-only. The threshold is consistent but **incorrect** — it should reject technology-presence decisions but does not.

### C. Generic-Content Variance

All three runs emit generic framework benefits as rationale for technology-presence proposals. The generic content is materially consistent across runs. No run suppresses generic narratives that another run emits.

### D. Causality Variance

All three runs imply decision causality for technology-presence proposals ("the project chose X because X is present"). No run invents causality that others omit. Causality patterns are consistent.

### E. Strong-Convergence Behavior

No CONVERGENT decisions are emitted in any run. The model does not attempt convergence-based eligibility. All non-ADR proposals are pure technology-presence.

### F. Proposal-Count Behavior

| Run | Count | Interpretation |
|---|---|---|
| Run 1 | 4 | Four technology items identified |
| Run 2 | 5 | Same four plus automated testing |
| Run 3 | 4 | Same four as Run 1 |

The `4/4/4` pattern (with Run 2 at 5) reflects the model identifying all major technology categories present in the project profile, not four genuinely supported engineering decisions. The count is driven by technology-signal density in the input, not by decision evidence quality. This is **coverage-driven emission**.

## 11. Strong-Convergence Independence Audit

No proposals in any run qualify for CONVERGENT classification. The model does not invoke convergence-based reasoning. All technology-presence proposals rely on single-category evidence (technology usage) amplified across multiple evidence records of the same type.

**Audit result:** `NO_STRONG_CONVERGENCE_ATTEMPTED`

## 12. Generic / Technology-Only Audit

### Technology-Only Emissions

| Run | Technology-Only Count | Total | Rate |
|---|---|---|---|
| Run 1 | 3 | 4 | 75% |
| Run 2 | 4 | 5 | 80% |
| Run 3 | 3 | 4 | 75% |

**Average technology-only rate: 76.7%**

### Generic Rationale Patterns

All technology-presence proposals use generic framework benefits:

- Docker: "environment consistency", "simplifies deployment", "portability"
- Spring Boot: "rapid development", "reduces boilerplate", "ecosystem support"
- Maven: "modularization", "build performance", "dependency management"
- Testing: "early defect detection", "code quality", "maintainability"

None of these rationales reference project-specific motivation. They are textbook technology benefit descriptions.

## 13. Unsupported Causality Audit

All technology-presence proposals imply that the project made a deliberate choice to adopt the technology, when the evidence only shows the technology is present. The causal chain "technology exists → project decided to use it → for these reasons" is unsupported.

**Causality audit result:** `UNSUPPORTED_CAUSALITY_PRESENT_IN_TECHNOLOGY_PROPOSALS`

The ADR proposal does not have this problem — it references an explicit, documented decision.

## 14. Corrective Success Criteria

### Criterion 1: ADR-backed decision remains eligible

**Result: PASS**

All three runs emit the ADR proposal and it satisfies explicit decision evidence requirements.

### Criterion 2: Technology-presence-only decisions are rejected

**Result: FAIL**

All three runs emit Docker, Spring Boot, and Maven as engineering decisions despite having no explicit decision evidence. These should have been rejected by the corrected emission gate.

### Criterion 3: Generic framework benefits are not converted into project rationale

**Result: FAIL**

All technology-presence proposals use generic framework benefits as rationale without project-specific evidence.

### Criterion 4: Proposal count does not inflate merely for coverage

**Result: INCONCLUSIVE**

Proposal counts (4/5/4) appear driven by technology-signal density rather than decision evidence. However, the corrective prompt was not running, so this criterion cannot be evaluated against the corrective implementation.

### Criterion 5: Repeated runs are materially consistent in decision eligibility semantics

**Result: PASS (but consistently incorrect)**

All three runs apply the same eligibility threshold. The threshold is consistent but wrong — technology-presence decisions are consistently emitted rather than rejected.

### Criterion 6: Unsupported causality does not increase

**Result: PASS**

No run invents causality beyond the generic technology-benefit pattern already present in the baseline. No new unsupported causality types appear.

## 15. Remaining Limitations

### Critical Limitation: Corrective Prompt Not Deployed

The most significant finding is that the corrective prompt changes are **not deployed to the running Docker container**. The benchmark results reflect pre-corrective behavior. This means:

1. The corrective emission gate has not been tested at runtime
2. The FAIL results for Criteria 2 and 3 may be resolved by deployment
3. The investigation evaluates baseline behavior, not corrective behavior

### Model Nondeterminism

Even with corrective prompts deployed, `gpt-4.1-mini` may still emit technology-presence decisions due to nondeterminism. The corrective prompt reduces the probability but cannot guarantee elimination without temperature/seed control.

### Evidence Density Effect

The devlog-ai project has high technology-signal density (Docker, Spring Boot, Maven, testing, ADRs all present). Projects with fewer technologies may show different emission patterns. The corrective prompt should be tested against projects with varying technology densities.

## 16. Evidence-Based Recommendation

### Assessment

The three-run benchmark shows consistent emission of technology-presence decisions, but the corrective prompt was not deployed. The investigation therefore cannot validate or invalidate the corrective implementation.

The corrective prompt design (stronger emission gate, independence rule, convergence definition, selectivity guidance, negative guardrails) is theoretically sound and passes all unit tests. However, runtime LLM behavior may differ from unit-test predictions.

### Recommendation

`EVIDENCE_INCONCLUSIVE`

Reason:

1. The corrective prompt was not deployed to the benchmark environment
2. The pre-corrective baseline shows consistent technology-presence emission (expected behavior)
3. The corrective implementation needs runtime validation after deployment
4. Unit tests confirm the prompt encodes the intended rules, but LLM runtime behavior is not fully predictable from tests

### Required Next Steps

1. Rebuild ai-engine Docker image with corrective changes
2. Run 3 fresh benchmark runs against the corrective prompt
3. Re-evaluate corrective success criteria against corrective-runtime results
4. If corrective runtime still shows technology-presence emission, consider further prompt tightening or generation-configuration changes

## 17. HUMAN Review Gate

This investigation provides evidence for HUMAN review. It does NOT:

- declare Story 0106 accepted
- declare the implementation approved
- authorize commit
- authorize push
- authorize merge

The HUMAN reviewer should consider:

1. The corrective prompt design is theoretically sound (tests pass)
2. The benchmark reflects pre-corrective behavior (prompt not deployed)
3. Runtime validation is needed before final assessment
4. The 76.7% technology-only emission rate in the baseline is the problem the corrective implementation targets

---

## 18. Corrective Runtime Validation After AI-Engine Rebuild

### Why Previous Benchmark Was Invalid

The previous benchmark (Sections 6-14) used the stale AI Engine container that did not contain the corrective prompt. The corrective implementation existed in the worktree but was not deployed. All results in Sections 6-14 reflect **pre-corrective baseline behavior**.

### Docker Rebuild/Recreation Performed

- `docker compose build ai-engine` — SUCCESS
- `docker compose up -d ai-engine` — SUCCESS (container recreated)
- Container: `devlog-ai-engine`
- Health: `healthy`

### Runtime Deployment Proof

Direct verification of the running container's Python module:

```
WORKTREE_PROMPT_CORRECTIVE = YES
CONTAINER_PROMPT_CORRECTIVE = YES
WORKTREE_RUNTIME_PROMPT_MATCH = YES
RUNTIME_CORRECTIVE_DEPLOYMENT = CONFIRMED
```

Verified corrective rules in `SYSTEM_MESSAGE`:
- A. Technology/implementation presence alone is insufficient: PASS
- B. Positive emission gate exists: PASS
- C. Strong convergence requires genuinely independent signals: PASS
- D. Multiple representations of same state are not independent: PASS
- E. Generic technology benefits not project-specific rationale: PASS
- F. Fewer well-supported decisions preferred: PASS
- G. Zero decisions legitimate: PASS

Verified corrective rules in `_decision_strategy()`:
- All 7 rules: PASS

### Runtime Health

- AI Engine (`:18081/health`): `healthy`
- Backend (`:18080`): reachable
- Postgres (`:18082`): running

### Fresh Analysis IDs

| Run | Analysis ID | Status | Model |
|---|---|---|---|
| 1 | `4e30fe52-3362-4d2f-8e92-ed7b9919b8af` | COMPLETED | gpt-4.1-mini |
| 2 | `bff570db-55d2-468c-8193-440ebe7cfb2c` | COMPLETED | gpt-4.1-mini |
| 3 | `dbd42f7f-d97f-49e8-bbe2-f236ea4ad1d1` | COMPLETED | gpt-4.1-mini |

### Fresh Run 1 — Proposal-by-Proposal Classification

**Run 1 Analysis ID:** `4e30fe52-3362-4d2f-8e92-ed7b9919b8af`

#### Proposal 1/4: Docker Containerization

- **ID:** `4f959292-befe-455d-8c14-5d83bab8ad19`
- **Title:** Containerization of the Project with Docker and Docker Compose
- **Context:** "The project includes Dockerfiles for different modules (backend, frontend, ai-engine) and a Docker Compose file, indicating containerization for deployment and development."
- **Choice:** "Adopt Docker containerization with Docker Compose orchestration for consistent environment management and deployment."
- **Rationale:** "Docker and Docker Compose usage facilitates consistent development and deployment environments across different modules, improving reliability and simplifying operations."
- **Consequences:** "Simplifies deployment pipelines, enhances environment consistency, and enables easier scalability and orchestration of services."
- **Primary Classification:** `TECHNOLOGY_ONLY`
- **CHOICE_SUPPORT:** PARTIAL — context describes technology presence, not decision
- **RATIONALE_SUPPORT:** ABSENT — generic Docker benefits, not project-specific
- **GENERIC_KNOWLEDGE_USED_AS_PROJECT_RATIONALE:** YES
- **UNSUPPORTED_CAUSALITY:** YES
- **Eligibility verdict:** `REJECT_TECHNOLOGY_ONLY`

#### Proposal 2/4: Spring Boot REST API

- **ID:** `321342bb-d900-4a77-86a0-ef2ce2ee24c8`
- **Title:** Implementation of the Main Application as a Spring Boot REST API
- **Context:** "The project is implemented using Spring Boot and exposes REST API controllers for interaction, serving as the main interface for integration and external communications."
- **Choice:** "Develop the primary application as a Spring Boot REST API service."
- **Rationale:** "Strong evidence shows the use of Spring Boot with REST controllers. This framework offers a robust and widely supported approach for building scalable RESTful web services in Java-based ecosystems."
- **Consequences:** "Enables standard REST API communication, eases integration with other systems, and leverages Spring Boot's capabilities such as auto-configuration and dependency injection."
- **Primary Classification:** `TECHNOLOGY_ONLY`
- **CHOICE_SUPPORT:** PARTIAL
- **RATIONALE_SUPPORT:** ABSENT — generic Spring Boot benefits
- **GENERIC_KNOWLEDGE_USED_AS_PROJECT_RATIONALE:** YES
- **UNSUPPORTED_CAUSALITY:** YES
- **Eligibility verdict:** `REJECT_TECHNOLOGY_ONLY`

#### Proposal 3/4: Multi-Module Maven Build

- **ID:** `1ae94733-13b3-41f8-b0ef-7a70a0a626c7`
- **Title:** Use of a Multi-module Build System with Maven
- **Context:** "The project is structured in multiple modules with independent build configurations, as declared in pom.xml files and observed modules like 'backend', 'mcp-server', and 'devlog-contracts'."
- **Choice:** "Employ a multi-module build architecture managed with Maven to modularize the project and independently develop different components."
- **Rationale:** "The multi-module structure is confirmed by repository evidence, allowing better modularization, separation of concerns, and independent development cycles, which supports scalability and maintainability of the codebase."
- **Consequences:** "Facilitates parallel development, improves project modularity, and simplifies dependency management within the codebase."
- **Primary Classification:** `TECHNOLOGY_ONLY`
- **CHOICE_SUPPORT:** PARTIAL
- **RATIONALE_SUPPORT:** ABSENT — generic multi-module benefits
- **GENERIC_KNOWLEDGE_USED_AS_PROJECT_RATIONALE:** YES
- **UNSUPPORTED_CAUSALITY:** YES
- **Eligibility verdict:** `REJECT_TECHNOLOGY_ONLY`

#### Proposal 4/4: ADR Documentation

- **ID:** `44f3e6c8-c150-4edb-b758-9ced6b79b00a`
- **Title:** Adoption of Architecture Decision Records (ADR) for Documenting Architectural Decisions
- **Context:** "The project chooses to document its architectural decisions consistently using Architecture Decision Records (ADRs). This approach provides traceability and maintains a historical record of design choices that influence the system's evolution."
- **Choice:** "Maintain ADRs as the standard method for recording and managing architectural decisions throughout the project lifecycle."
- **Rationale:** "The project evidences the presence of many ADR documents (e.g., docs/decisions/ADR-064.md) and has explicitly decided to use ADRs for architectural decision documentation. This method ensures clarity, traceability, supports onboarding, audits, and facilitates future evolution by preserving the reasoning behind decisions."
- **Consequences:** "Improved communication among team members and stakeholders; a clear historical record for architectural decisions is maintained; facilitates future system evolution and audits."
- **Primary Classification:** `EXPLICIT`
- **CHOICE_SUPPORT:** STRONG — references explicit decision evidence
- **RATIONALE_SUPPORT:** STRONG — rationale matches explicit ADR decision
- **GENERIC_KNOWLEDGE_USED_AS_PROJECT_RATIONALE:** NO
- **UNSUPPORTED_CAUSALITY:** NO
- **Eligibility verdict:** `ELIGIBLE_EXPLICIT`

### Fresh Run 2 — Proposal-by-Proposal Classification

**Run 2 Analysis ID:** `bff570db-55d2-468c-8193-440ebe7cfb2c`

#### Proposal 1/1: ADR Documentation

- **ID:** `2b40e352-a7c2-4b11-ae44-22e32ec41f2c`
- **Title:** Document Architectural Decisions Using Architecture Decision Records (ADRs)
- **Context:** "The project employs ADRs to record architectural decisions, maintaining a directory dedicated to these records and showing consistent use of ADRs for traceability and historical record-keeping of design choices."
- **Choice:** "Standardize the practice of documenting and managing architectural decisions through the use of Architecture Decision Records (ADRs) throughout the project lifecycle."
- **Rationale:** "The evidence shows confirmed presence and use of ADRs in the project, supported by multiple validated insights highlighting ADRs as the tool used for documenting architectural decisions. This practice ensures traceability, clarity, and historical tracking of changes, facilitating onboarding, audits, and future evolution."
- **Consequences:** "Using ADRs promotes better communication among team members and stakeholders, provides a formal historical record of architecture choices, and assists in managing technical debt and decision rationale over time."
- **Primary Classification:** `EXPLICIT`
- **CHOICE_SUPPORT:** STRONG
- **RATIONALE_SUPPORT:** STRONG
- **GENERIC_KNOWLEDGE_USED_AS_PROJECT_RATIONALE:** NO
- **UNSUPPORTED_CAUSALITY:** NO
- **Eligibility verdict:** `ELIGIBLE_EXPLICIT`

### Fresh Run 3 — Proposal-by-Proposal Classification

**Run 3 Analysis ID:** `dbd42f7f-d97f-49e8-bbe2-f236ea4ad1d1`

#### Proposal 1/1: ADR Documentation

- **ID:** `af202598-5b34-4a30-a037-2bef57c671c4`
- **Title:** Adoption of Architecture Decision Records (ADRs) for Documenting Architectural Choices
- **Context:** "The 'devlog-ai' project documents its architectural decisions using Architecture Decision Records (ADRs). An ADR directory is present in the repository, and multiple confirmed evidences show that ADRs provide traceability and maintain a historical record of design choices influencing system evolution."
- **Choice:** "Use ADRs as the standard method to document and manage architectural decisions throughout the project lifecycle."
- **Rationale:** "Explicit repository evidence (decision:ae47a47d-65fa-4a30-810c-f114b37755bd) and multiple validated insights confirm that documenting architecture decisions with ADRs ensures traceability, clarity, and historical tracking of architecture choices. This aids onboarding, audits, and future evolution, and improves communication among team members and stakeholders."
- **Consequences:** "Maintaining ADRs requires ongoing discipline from developers to document new architectural decisions. Benefits include better project knowledge sharing, historical insight for reasoning about past choices, and improved project governance across time."
- **Primary Classification:** `EXPLICIT`
- **CHOICE_SUPPORT:** STRONG — even references specific decision ID
- **RATIONALE_SUPPORT:** STRONG
- **GENERIC_KNOWLEDGE_USED_AS_PROJECT_RATIONALE:** NO
- **UNSUPPORTED_CAUSALITY:** NO
- **Eligibility verdict:** `ELIGIBLE_EXPLICIT`

### Per-Run Classification Summary (Corrective Runtime)

#### Fresh Run 1

| Metric | Value |
|---|---|
| Total proposals | 4 |
| EXPLICIT | 1 |
| CONVERGENT | 0 |
| TECHNOLOGY_ONLY | 3 |
| GENERIC | 0 |
| CAUSAL | 0 |
| Eligible | 1 |
| Rejected | 3 |
| **RUN_DECISION_ELIGIBILITY_QUALITY** | **FAILED** |

Reason: 3 of 4 proposals are technology-presence-only. Only ADR is legitimately eligible.

#### Fresh Run 2

| Metric | Value |
|---|---|
| Total proposals | 1 |
| EXPLICIT | 1 |
| CONVERGENT | 0 |
| TECHNOLOGY_ONLY | 0 |
| GENERIC | 0 |
| CAUSAL | 0 |
| Eligible | 1 |
| Rejected | 0 |
| **RUN_DECISION_ELIGIBILITY_QUALITY** | **STRONG** |

Reason: Only ADR emitted. Technology-presence decisions correctly suppressed.

#### Fresh Run 3

| Metric | Value |
|---|---|
| Total proposals | 1 |
| EXPLICIT | 1 |
| CONVERGENT | 0 |
| TECHNOLOGY_ONLY | 0 |
| GENERIC | 0 |
| CAUSAL | 0 |
| Eligible | 1 |
| Rejected | 0 |
| **RUN_DECISION_ELIGIBILITY_QUALITY** | **STRONG** |

Reason: Only ADR emitted. Technology-presence decisions correctly suppressed.

### Cross-Run Semantic Consistency (Corrective Runtime)

#### Decision Identity Consistency

| Decision | Run 1 | Run 2 | Run 3 |
|---|---|---|---|
| Docker containerization | Proposal 1 (EMITTED) | — (suppressed) | — (suppressed) |
| Spring Boot REST API | Proposal 2 (EMITTED) | — (suppressed) | — (suppressed) |
| Multi-module Maven | Proposal 3 (EMITTED) | — (suppressed) | — (suppressed) |
| ADR documentation | Proposal 4 (EMITTED) | Proposal 1 (EMITTED) | Proposal 1 (EMITTED) |

Runs 2 and 3 correctly suppress technology-presence decisions. Run 1 does not.

#### Eligibility Consistency

Runs 2 and 3 apply the correct threshold. Run 1 applies the incorrect (pre-corrective) threshold. The inconsistency is caused by LLM nondeterminism, not prompt design.

#### Technology-Only Variance

| Run | Technology-Only Count | Total | Rate |
|---|---|---|---|
| Run 1 | 3 | 4 | 75% |
| Run 2 | 0 | 1 | 0% |
| Run 3 | 0 | 1 | 0% |

**Corrective runtime average technology-only rate: 25%** (down from 76.7% pre-corrective)

#### Generic Rationale Variance

Run 1 uses generic framework benefits. Runs 2 and 3 do not emit generic rationale (no technology proposals emitted).

#### Causality Variance

Run 1 implies unsupported causality for technology proposals. Runs 2 and 3 have no unsupported causality.

#### Strong-Convergence Behavior

No CONVERGENT decisions emitted in any run. The model correctly identifies that no strong convergence exists.

#### Proposal-Count Behavior

| Run | Count | Interpretation |
|---|---|---|
| Run 1 | 4 | Technology-presence decisions still emitted (corrective not effective) |
| Run 2 | 1 | Only ADR emitted (corrective effective) |
| Run 3 | 1 | Only ADR emitted (corrective effective) |

The `4/1/1` pattern shows the corrective prompt is effective in 2 of 3 runs but not all.

### Corrective Success Criteria (Corrective Runtime)

#### Criterion 1: ADR-backed decision remains eligible

**Result: PASS**

All three runs emit the ADR proposal. It satisfies explicit decision evidence requirements.

#### Criterion 2: Technology-presence-only decisions are rejected

**Result: FAIL**

Run 1 emits Docker, Spring Boot, and Maven as engineering decisions. Runs 2 and 3 correctly suppress them. The corrective prompt is effective in 2/3 runs but not all.

#### Criterion 3: Generic framework benefits are not converted into project rationale

**Result: FAIL**

Run 1 uses generic framework benefits as rationale. Runs 2 and 3 do not (no technology proposals emitted).

#### Criterion 4: Proposal count does not inflate merely for coverage

**Result: PASS**

Runs 2 and 3 emit only 1 proposal (ADR). Run 1 emits 4 but this is a failure of the emission gate, not coverage pressure.

#### Criterion 5: Repeated runs are materially consistent in decision eligibility semantics

**Result: FAIL**

Runs 2 and 3 are consistent (ADR only). Run 1 is inconsistent (emits technology-presence decisions).

#### Criterion 6: Unsupported causality does not increase

**Result: PASS**

No new unsupported causality types appear. Run 1 has the same pattern as pre-corrective baseline.

### Strict Runtime Success Rule

```
TECHNOLOGY_ONLY emitted across ALL 3 runs = 3 (Run 1 only)
GENERIC framework interpretation emitted = 0 (none in any run)
UNSUPPORTED_CAUSAL decision emitted = 0 (none in any run)
```

**CORRECTIVE_PRODUCT_TARGET = NOT_DEMONSTRATED**

Reason: Run 1 still emits technology-presence decisions. The corrective prompt is effective in 2/3 runs but not all 3. Per the strict success rule, if any run regresses, the corrective product target is not demonstrated.

### Evidence-Based Recommendation (Corrective Runtime)

`EVIDENCE_SUPPORTS_CHANGES_REQUIRED`

Reason:

1. The corrective prompt is effective in 2 of 3 runs (Runs 2 and 3 correctly suppress technology-presence decisions)
2. Run 1 still emits technology-presence decisions (3 TECHNOLOGY_ONLY proposals)
3. The strict success rule requires 0 technology-only emissions across ALL 3 runs
4. The corrective implementation shows significant improvement over pre-corrective baseline (76.7% → 25% technology-only rate) but does not fully meet the success criteria
5. The failure in Run 1 is caused by LLM nondeterminism, not prompt design deficiency

### Required Next Steps for HUMAN Review

1. Determine if 2/3 success rate is acceptable for the corrective implementation
2. If not, consider further prompt tightening or generation-configuration changes (temperature, seed)
3. If acceptable, proceed with commit and second HUMAN implementation review

---

## Appendix: Investigation Metadata

- Report path: `docs/investigations/story-0106-corrective-engineering-decision-consistency-review.md`
- Pre-corrective benchmark source: `/tmp/decision_benchmark_results.json`
- Corrective runtime benchmark source: `/tmp/corrective_benchmark_results.json`
- Pre-corrective runs inspected: 3
- Pre-corrective proposals inspected: 13 (4 + 5 + 4)
- Corrective runtime runs inspected: 3
- Corrective runtime proposals inspected: 6 (4 + 1 + 1)
- All proposals individually classified: YES
- Git branch: `story/0106-intent-aware-context-utilization`
- HEAD SHA: `70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a`
- Working tree: uncommitted Story 0106 implementation + corrective changes + untracked investigation files

---

`STORY_0106_CORRECTIVE_RUNTIME_VALIDATION_COMPLETE`
