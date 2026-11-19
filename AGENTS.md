# DevLog AI — AGENTS.md

Root operating guide for AI coding agents and automated engineering runtimes working on DevLog AI.

---

## 1. What is DevLog AI

DevLog AI analyzes Git repositories through deterministic collectors, builds an immutable project context, and asks an AI engine for structured interpretations. AI output is **never trusted directly**: it becomes a proposal that must be accepted by a human before it is promoted to immutable trusted knowledge (an Insight or, for a bounded Git evolution, an Engineering Event).

**Core principle**: Java Core owns deterministic domain authority. Python AI Engine owns probabilistic execution.

---

## 2. Architecture

| Component | Technology | Responsibility |
|-----------|------------|----------------|
| **Core Backend** | Java 21, Spring Boot 4.1, JPA | Domain model, repository collection, orchestration, deterministic context construction, validation, API, trusted knowledge persistence |
| **AI Engine** | Python 3.12, FastAPI | Intent-driven structured interpretation (probabilistic generation, structured output, defensive validation, corrective retry) |
| **Frontend** | Angular 22, RxJS, SCSS | Reactive project workspace, Human-in-the-Loop review |
| **Database** | PostgreSQL 17, Flyway | Durable domain state and migration history |
| **MCP Server** | Java, Spring AI MCP | Exposes `get_engineering_context`, `search_project_history`, and resources (`devlog://...`) |
| **Runtime** | Docker Compose | Local orchestration of all services |

### Architectural Boundary (ADR-067 D1, D3)

```
Java Core (deterministic authority)
  ├── EngineeringContext construction (sole authority)
  ├── Trust tier assignment, scoping, validation
  ├── AiTask lifecycle, grounding contract, authoritative acceptance
  └── Trusted knowledge persistence (Insight, Decision, EngineeringEvent)

Python AI Engine (probabilistic execution)
  ├── Intent-driven structured generation
  ├── Defensive validation, corrective retry (max 1)
  └── Callback to Java Core with structured output

MCP / REST → thin transport adapters over same Java capability
```

**Boundary rule**: Java Core constructs, scopes, and authorizes `EngineeringContext`. Python receives it as immutable authorized input. Python must NOT independently reconstruct project context (no direct DB/repo access, no `KnowledgeSelectionService` duplication, no parallel retrieval model, no ContextPack).

---

## 3. Repository Map

```
devlog-ai/
├── backend/                    # Java Core (Spring Boot 4.1, Java 21, Maven)
│   ├── src/main/java/com/hopeful117/devlogai/
│   │   ├── ai/                 # AI task lifecycle, callbacks, contracts
│   │   ├── analysis/           # AnalysisContext, diagnostics, collection
│   │   ├── engineeringcontext/ # EngineeringContext facade, mapper, controller
│   │   ├── repositorycontext/  # Context engine, collectors, ranking, selection
│   │   ├── projectcontext/     # Project context provider, adapter
│   │   ├── intent/             # IntentCatalog, definitions
│   │   ├── story/              # Engineering Story lifecycle
│   │   ├── insight/            # Insight entity, repository
│   │   ├── proposal/           # ValidatableProposal lifecycle
│   │   ├── engineeringevent/   # Engineering Event entity
│   │   ├── decision/           # Engineering Decision entity
│   │   ├── challenge/          # Challenge entity
│   │   ├── artifact/           # Artifact entity
│   │   ├── fact/               # Fact entity, repository
│   │   ├── observation/        # Observation entity
│   │   ├── knowledge/          # KnowledgeEvent, KnowledgeRelation
│   │   ├── project/            # Project entity, service
│   │   ├── profile/            # Project profile
│   │   ├── repositorycontext/  # RepositoryContextEngine, collectors
│   │   ├── repositoryevidence/ # Evidence resolution, projection
│   │   ├── shared/             # Shared exceptions, logging
│   │   └── ...
│   ├── src/test/               # Unit + integration tests
│   ├── mvnw, pom.xml           # Maven wrapper + POM
│   └── Dockerfile
│
├── ai-engine/                   # Python AI Engine (FastAPI, Python 3.12)
│   ├── app/
│   │   ├── api/                # /api/v1/ai/tasks, /deliverables, /health
│   │   ├── services/           # Generation services (insight, event, decision, deliverable)
│   │   ├── services/task_processing_service.py  # Routes AiTask to generation service
│   │   ├── prompts/            # Prompt builders (insight, event, decision, deliverable)
│   │   ├── providers/          # LLM providers (openai, mock)
│   │   ├── schemas/            # Pydantic contracts (PromptRequest, AiTaskResultRequest, etc.)
│   │   ├── models/             # Enums (AiTaskType, ProposalType, InsightType, etc.)
│   │   ├── clients/            # Core callback client
│   │   └── prompts/structured_context.py  # Shared grounding rules
│   ├── evaluations/            # ADR-066 replayable evaluation harness
│   ├── tests/                  # pytest (unit + integration)
│   ├── pyproject.toml
│   └── Dockerfile
│
├── mcp-server/                  # MCP Server (Spring AI MCP, Java)
│   └── src/main/java/.../mcp_server/
│       ├── tool/               # get_engineering_context, search_project_history, analyze_story_context
│   │   ├── resource/           # devlog:// resources (stories, decisions, insights, events, commits, freshness)
│   └── ...
│
├── frontend/                    # Angular 22
│   ├── src/
│   └── ...
│
├── devlog-contracts/           # Shared Java records (EngineeringContext, Evidence, TrustTier, etc.)
│
├── docs/
│   ├── decisions/              # ADR-001 through ADR-067
│   ├── stories/                # Engineering Stories (0001-0113)
│   ├── discoveries/            # Investigation documents
│   └── architecture.md, pipeline.md, knowledge-model.md, etc.
│
├── data/                       # Workspace data (git worktrees, cloned repos)
├── docker-compose.yml
├── pom.xml                      # Parent POM (backend is module)
└── README.md
```

---

## 4. Trust and AI Governance (ADR-006, ADR-067)

### Core Rule

**AI output is never trusted project knowledge.** It becomes a `ValidatableProposal` that must be accepted by a human before promotion to immutable trusted knowledge (Insight, Decision, EngineeringEvent).

```
AI Interpretation → ValidatableProposal → Human Validation → ACCEPTED/REJECTED → Trusted Knowledge
```

### Trust Tiers (ADR-063, Story 0111)

| Tier | Content | Authority |
|------|---------|-----------|
| `TRUSTED` | Insights, Decisions, EngineeringEvents from accepted proposals | Human-promoted |
| `HUMAN_AUTHORED` | Project notes, milestones, artifacts, stories, challenges | Provenance only, NOT authoritative |
| `TECHNICAL_EVIDENCE` | Commits, diffs, Facts, Observations, structure, profiles | Deterministic extraction |
| `SYSTEM_METADATA` | Analysis execution metadata, freshness, diagnostics | System-generated |
| `UNVALIDATED` / `TRANSIENT_AI` | Pending proposals, raw AI output | **Excluded from context** |

### Critical Invariants

| Invariant | Enforcement |
|-----------|-------------|
| `AI output != trusted knowledge` | Java Core owns `ValidatableProposal` lifecycle; AI never persists trusted knowledge |
| `persisted != trusted` | `AiTask.synthesisSnapshot` and `StoryContextAnalysis` are non-trusted snapshots |
| `confidence != evidence` | High confidence ≠ grounded fact (ADR-067 D7) |
| `known A + known B != A → B` | Only explicit `KnowledgeRelation` edges establish relationships (ADR-067 D8) |
| Java authoritative acceptance | Python validation is defensive; Java Core revalidates authoritatively on callback |

---

## 5. ADR and Engineering Story Authority

### ADRs

- **Accepted ADRs are architectural constraints**. Do not silently change or reinterpret them.
- ADR-006 (proposal governance), ADR-063 (context architecture), ADR-067 (agent capability) are foundational.
- If implementation reality contradicts an accepted ADR: **stop and report the conflict**. Do not silently resolve it.

### Engineering Stories

- Stories have explicit **design approval** → **implementation authorization** → **implementation** → **acceptance** gates.
- **Design approval ≠ implementation authorization**. A consolidated design (e.g., Story 0112) is not implementation authorization.
- Frozen decisions (D12–D21 for Story 0112) must not be silently reopened.
- Implementation must remain within authorized Story scope.
- Check repository reality before assuming architecture.
- If implementation reality contradicts accepted architecture: **report the conflict**. Do not silently change architecture.

### Gates

| Gate | Who | What |
|------|-----|------|
| Design approval | Human architect | Story design consolidated (e.g., Story 0112) |
| Implementation authorization | Human | Explicit go-ahead to write production code |
| Implementation | Agent | Write code, tests, verify |
| Story acceptance | Human | Explicit acceptance after verification |

**An agent must never infer authorization** from: ADR existence, Story existence, design completion, passing tests, or another AI's recommendation.

---

## 6. Human Authorization Boundaries

**An agent must never implement, commit, or push without explicit human authorization.**

Explicit human authorization is required for:

- Implementation of a Story (after design consolidation)
- Architectural decisions (ADR acceptance)
- Promotion of proposals into trusted knowledge (ADR-006)
- Story acceptance
- Any commit/push to the repository

An agent must **never infer authorization** from:
- ADR existence
- Story existence
- Design completion
- Tests passing
- Another AI's recommendation

---

## 7. Story Implementation Workflow

For future coding agents implementing authorized Stories:

1. **Inspect repository state**: `git status`, `git log --oneline -5`, verify branch/HEAD
2. **Read governing ADRs**: Identify and read all ADRs referenced by the Story
3. **Read the authorized Engineering Story**: Full story.md including acceptance criteria
4. **Inspect affected implementation and tests**: Find existing code, tests, contracts
5. **Identify repository/design conflicts**: Compare design with actual code; report conflicts
6. **Implement smallest coherent scope**: Implement only what the Story authorizes
7. **Add/update tests**: Unit tests for new logic, integration tests for flows
8. **Run focused verification first**: Module tests before full suite
9. **Run broader verification**: Full relevant test suites
10. **Inspect final diff**: `git diff` — ensure scope is limited
11. **Produce structured implementation report** (see §13)
12. **Wait for human acceptance**: Do not commit/push without explicit authorization

---

## 8. Testing and Verification Commands

### Backend (Java / Maven)

```bash
# Run all backend tests with coverage (used in CI)
./backend/mvnw -pl backend -am clean verify -B

# Run specific test class
./backend/mvnw -pl backend test -Dtest=EngineeringContextFacadeTest

# Run tests for a specific module
./backend/mvnw -pl devlog-contracts test

# Build only (skip tests)
./backend/mvnw -pl backend -DskipTests clean install

# Run with specific profile
./backend/mvnw -pl backend -Psome-profile test
```

### Python AI Engine (pytest)

```bash
# Run all AI Engine tests
cd ai-engine && python -m pytest -q

# Run specific test file
cd ai-engine && python -m pytest tests/test_insight_generation_service.py -v

# Run evaluation harness (ADR-066)
cd ai-engine && python -m evaluations.runner architecture-overview-v2-enriches-v1

# Run evaluation tests
cd ai-engine && python -m pytest tests/test_evaluation_harness.py -v

# Run with verbose output
cd ai-engine && python -m pytest tests/test_insight_generation_service.py -vv
```

### Frontend (Angular)

```bash
cd frontend
npm ci                    # Install dependencies
npm run lint              # Lint check
npm run format:check      # Format check
npm run test              # Unit tests (watch mode)
npx ng test --coverage --watch=false  # CI mode
npm run build             # Production build
```

### Docker / Full Stack

```bash
# Start all services (ChatGPT OAuth provider by default; use LLM_PROVIDER=mock for tests)
docker compose up --build -d

# With OpenAI provider
LLM_PROVIDER=openai LLM_MODEL=gpt-5.6-luna LLM_API_KEY=<key> docker compose up --build -d

# View logs
docker compose logs -f backend ai-engine

# Stop services
docker compose stop
docker compose down       # Preserves volumes
docker compose down --volumes  # Destroys local data
```

### Focused Test First

**Always run focused tests for changed modules before full suite.**

```bash
# Example: changed repositorycontext collector
./backend/mvnw -pl backend test -Dtest=CommitDiffEvidenceCollectorTest

# Example: changed insight generation
cd ai-engine && python -m pytest tests/test_insight_generation_service.py -v
```

---

## 8. Git Discipline

- **Before work**: `git status`, `git log --oneline -5`, verify branch and HEAD
- **Scope**: Keep changes limited to the authorized Story/task
- **Unrelated changes**: Do not silently include unrelated files
- **Final diff**: `git diff` — inspect before any commit
- **No silent inclusion**: Do not stage unrelated files
- **Authorization**: Do not commit/push without explicit human authorization
- **This task**: Do not commit or push while creating AGENTS.md

---

## 10. Prohibited Behavior

Based on DevLog governance and accepted architecture:

| Prohibited | Reason |
|------------|--------|
| Implement outside authorized Story | Violates Story scope and authorization gates |
| Silently change accepted ADR decisions | ADRs are architectural constraints |
| Treat AI output as trusted knowledge | ADR-006: AI output = `ValidatableProposal`, not trusted knowledge |
| Fabricate repository relationships | `known A + known B != A → B`; only explicit `KnowledgeRelation` edges |
| Weaken grounding or trust validation | Grounding is mandatory (ADR-006, D7) |
| Bypass Java/Core authority from Python | Java owns authoritative validation; Python is defensive only |
| Introduce RAG, OpenClaw, generic agent frameworks | Not authorized in current Stories/ADRs (ADR-067 Non-Goals) |
| Refactor unrelated code opportunistically | Keeps scope clean and diffs reviewable |
| Hide failing tests | Undermines verification |
| Claim tests passed unless executed | Integrity of verification |
| Claim Story acceptance on behalf of human | Human acceptance is a separate gate |

---

## 11. Implementation Report Template

After implementation, agents must produce a structured report:

```markdown
## Implementation Report

### Repository State
- Branch: <branch>
- HEAD: <commit SHA>
- Worktree: Clean / Modified files listed

### Governance
- Governing ADR(s): <ADR numbers>
- Authorized Story: <Story number and title>

### Files Changed
- <file1>: <brief description>
- <file2>: <brief description>

### Implementation Summary
<2-3 sentences on what was implemented>

### Architectural Decisions Preserved
- <Decision X: how it was preserved>
- <Decision Y: how it was preserved>

### Tests Added/Modified
- <test file>: <what it tests>

### Commands Executed
```bash
<command 1>
<command 2>
```

### Test Results
```
<test output summary>
```

### Known Limitations
- <limitation 1>

### Implementation-Level Decisions Made
- <decision 1: rationale>

### Blockers / Repository Inconsistencies
- <blocker or inconsistency if any>

### Git Diff Summary
<git diff --stat>

### Commit/Push
- Commit created: YES/NO
- Push performed: YES/NO

### Readiness
READY_FOR_HUMAN_REVIEW / BLOCKED
```

**The agent must not declare human acceptance itself.**

---

## 11. Key Commands Reference

| Command | Purpose | Verified In |
|---------|---------|-------------|
| `./backend/mvnw -pl backend -am clean verify -B` | Full backend test + coverage (CI) | `.github/workflows/quality.yml` |
| `./backend/mvnw -pl backend test -Dtest=<Class>` | Focused backend test | `pom.xml`, CI |
| `cd ai-engine && python -m pytest -q` | Full AI Engine tests | `pyproject.toml` |
| `cd ai-engine && python -m pytest tests/<file>.py -v` | Focused AI Engine test | `ai-engine/tests/` |
| `cd ai-engine && python -m evaluations.runner <scenario>` | ADR-066 evaluation replay | `ai-engine/evaluations/runner.py` |
| `docker compose up --build -d` | Start all services | `docker-compose.yml` |
| `LLM_PROVIDER=openai ... docker compose up --build -d` | With OpenAI | `docker-compose.yml` |
| `docker compose logs -f backend ai-engine` | View service logs | `docker-compose.yml` |
| `cd frontend && npm run lint` | Frontend lint | `.github/workflows/quality.yml` |
| `cd frontend && npx ng test --coverage --watch=false` | Frontend unit tests | `.github/workflows/quality.yml` |

---

## 11. Governance Verification Checklist

When reviewing an implementation, verify:

- [ ] **Java/Core deterministic authority preserved**: No Python-side context reconstruction
- [ ] **Python probabilistic boundary respected**: No trusted knowledge persistence in Python
- [ ] **EngineeringContext sole authority**: Java constructs/scoping/authorization only
- [ ] **MCP/REST as adapters**: No duplicate implementations
- [ ] **No ValidatableProposal in V1** (Story 0112): Analysis + recommendations only
- [ ] **Grounding mandatory**: Facts/interpretations require evidence references
- [ ] **Trust boundaries**: `persisted != trusted`, `confidence != evidence`
- [ ] **Relationship semantics**: Explicit relation types, no confidence promotion
- [ ] **ADR/Story authority**: No silent reopening of D1–D11 or D12–D21
- [ ] **Human gates respected**: No self-authorized implementation/commit/push
- [ ] **Scope discipline**: Changes limited to authorized Story

---

## 12. Future Considerations

Nested `AGENTS.md` files may eventually be useful for:

- `backend/AGENTS.md` — Java/Core specific patterns (Spring, JPA, MapStruct, testing)
- `ai-engine/AGENTS.md` — Python AI Engine patterns (FastAPI, Pydantic, providers, prompts)
- `mcp-server/AGENTS.md` — MCP tool/resource conventions
- `ai-engine/evaluations/AGENTS.md` — ADR-066 evaluation harness conventions

**Not created in this task** — root `AGENTS.md` is sufficient for now.

---

## 13. Quick Reference: Key Files for Implementation

| Purpose | File/Location |
|---------|---------------|
| EngineeringContext contract | `devlog-contracts/src/main/java/.../engineeringcontext/` |
| EngineeringContext construction | `backend/.../engineeringcontext/EngineeringContextFacadeImpl.java` |
| Evidence trust tier classification | `backend/.../engineeringcontext/mapper/EngineeringContextContractMapper.java` |
| AiTask lifecycle | `backend/.../ai/task/entity/AiTask.java` |
| AI Engine callback | `backend/.../ai/engine/service/AiTaskResultServiceImpl.java` |
| Grounding validation (Java) | `backend/.../ai/engine/service/AiProposalContractValidator.java` |
| Grounding validation (Python) | `ai-engine/app/services/insight_generation_service.py` |
| Prompt building | `ai-engine/app/prompts/insight.py` |
| Intent catalog | `backend/.../intent/service/IntentCatalog.java` |
| ADR-066 evaluation harness | `ai-engine/evaluations/` |
| Story 0111 (EngineeringContext) | `docs/stories/0111-structured-engineering-context/story.md` |
| Story 0112 (Agent) | `docs/stories/0112-devlog-story-context-agent/story.md` |
| ADR-067 (Agent architecture) | `docs/decisions/ADR-067.md` |
| ADR-006 (Trust) | `docs/decisions/ADR-006.md` |
| ADR-066 (Evaluation) | `docs/decisions/ADR-066.md` |

---

## 14. Scope Discipline

This `AGENTS.md` captures **repository-wide durable rules**. Story-specific implementation details (e.g., exact DTO field names for Story 0112) belong in the Story document, not here.

Do not encode temporary implementation state as permanent repository policy.

---

## 15. MCP Tool Usage Guidelines

This section applies to **all agent types** (Claude Code, Claude claude, custom runners) that interact with DevLog via MCP. It documents which tools exist, how to pick the right one, authentication constraints, and error handling.

### Available MCP Tools

| Tool | Purpose | Transport auth |
|------|---------|----------------|
| `devlog_story_agent` | Submit a Story context agent task (investigation, planning, implementation analysis) | Bearer token via `McpSyncRequestContext` transport context |
| `search_project_history` | Keyword search over imported git commits (messages + changed paths) | Bearer token via `McpSyncRequestContext` transport context |
| `sync_project_history` | Trigger a git history import for a project's active source | Bearer token (required — unauthenticated calls rejected) |
| `get_engineering_context` | Retrieve structured `EngineeringContext` for a project + intent | Bearer token via `McpSyncRequestContext` transport context |
| `analyze_story_context` | Submit a Story-scoped context analysis task | Bearer token via `McpSyncRequestContext` transport context |

### Available MCP Resources (`devlog://…`)

Resources provide read-only access to Stories, Decisions, Insights, Engineering Events, Commits, and Freshness. **Important constraint:** MCP resources do NOT receive a per-request `McpSyncRequestContext` (MCP framework limitation — only Tools do). They rely on `SecurityContextHolder` (populated in HTTP transport) or the statically configured `devlog.security.oidc.bearer-token`. In pure stdio transport without that static token, resources will return 401.

**Consequence:** prefer tools over resources when both can answer the question.

### Tool Selection by Use Case

| Use case | Tool to use |
|----------|-------------|
| Start work on an authorized Story | `devlog_story_agent` with `storyId` (UUID) + `intent` |
| Investigation / planning with no specific Story | `devlog_story_agent` with `intent` and relevant `files` |
| "Which commits touched this area?" | `search_project_history` |
| "Is history imported for this project?" | `search_project_history` with a broad query |
| Trigger history import before first use | `sync_project_history` |
| Read a specific Story or Decision by URI | Resources (`devlog://projects/{slug}/stories/{uuid}`, etc.) |

### Authentication Architecture

DevLog MCP tools operate in one of two transport modes:

**HTTP transport** (typical in Claude Code desktop app — MCP server reachable over HTTP):
- Spring Security processes the HTTP `Authorization` header → `SecurityContextHolder` is populated
- All tools and resources work correctly without special handling

**stdio transport** (some CLI MCP clients):
- `SecurityContextHolder` is NOT populated (no HTTP request)
- Each MCP tool call receives a `McpSyncRequestContext` carrying the `authorization` key in its transport context
- Tools that call `requireProjectId(slug, requestContext)` extract the token correctly
- Resources fall back to `SecurityContextHolder` (empty) → 401 unless `devlog.security.oidc.bearer-token` is set in the server's environment

**Key invariant:** `devlog_story_agent` does not use `requireProjectId()` — it passes the bearer token directly as a request header. It is not affected by transport mode and is therefore the most reliable entry point.

### Error Handling

| Error | Diagnosis | Action |
|-------|-----------|--------|
| `401 UNAUTHORIZED` on first call | Likely intermittent (token refresh, service restart) | Retry once after 5–10 s; if it persists report to the user |
| Persistent `401` on tools | Transport context missing `authorization` key | Check MCP client configuration; report to the user |
| Persistent `401` on resources | stdio mode without static token | Set `devlog.security.oidc.bearer-token` in MCP server env, or use tool equivalents |
| `POLL_TIMEOUT` with `lastKnownStatus=SUBMITTED` | Job still active | Resume with the **same** idempotency key — do not create a new request |
| `404 / ENTITY_NOT_FOUND` | Story UUID not found in DevLog | One retry omitting `storyId` is permitted; then stop Story-scoped work and report |

### Usage Rules

1. **Call `devlog_story_agent` before any substantial work.** For an authorized Story: include `storyId` (DevLog UUID). For investigation or planning: use `intent` with relevant `files`. Do not substitute filesystem/git reads for DevLog context — this deprives future sessions of accumulated engineering memory.

2. **Call DevLog before writing code, not after.** Once files exist in the worktree, DevLog cannot see them (it indexes only the main branch). The call must precede the first line of implementation.

3. **Provide a stable `idempotencyKey`** for every `devlog_story_agent` call that may need resuming across context windows or session crashes.

4. **Never fabricate project knowledge.** If DevLog returns no context or an error, surface the gap with a visible `DEVLOG_CONTEXT_ERROR` — do not invent architecture, history, or Story decisions from inference.

5. **Skip DevLog only for**: single-file mechanical edits, syntax fixes, simple command lookups — tasks where no Engineering Story, ADR, or historical decision can affect the answer.

6. **Use an orchestrator handoff for delegated exploration.** The parent agent must complete the DevLog preflight before delegating substantial work and pass a `DEVLOG_PREFLIGHT` summary containing the status, task/snapshot identity, freshness, bounds, warnings, trust tiers, and limitations. An exploration agent must reject a missing handoff with `DEVLOG_PREFLIGHT_REQUIRED`.

7. **Report the preflight.** Final reports must state which DevLog tools were used, what they contributed, whether the context was fresh or bounded, and which conclusions rely only on repository evidence.

---

*Generated from repository evidence. If repository reality contradicts this guide, repository reality wins.*
