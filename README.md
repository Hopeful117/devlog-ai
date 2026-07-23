# DevLog AI

> Build traceable, human-validated knowledge from a software repository.

DevLog AI analyzes Git repositories through deterministic collectors, builds an immutable project
context, and asks an AI engine for structured interpretations. AI output is never trusted directly:
it becomes a proposal that must be accepted by a human before it is promoted to an immutable
Insight.

The project now includes a usable Angular MVP for the complete traceable interaction flow, alongside
the REST APIs and OpenAPI documentation. Document projections remain future work.

## Current capabilities

- Manage projects and Git repository sources.
- Clone and synchronize repository workspaces at a branch, tag, or commit.
- Collect versioned Facts about repository metadata, builds, Spring, Docker, documentation, and
  test structure.
- Derive deterministic, traceable Observations from Facts.
- Produce immutable Project Profile and AnalysisContext snapshots.
- Select intent-relevant knowledge deterministically with explicit budgets, metadata, and SHA-256 digests.
- Diagnose partial collection and expose analysis warnings.
- Run an asynchronous Core-to-AI workflow with correlation IDs, retries, and structured logs.
- Generate structured Insight Proposals from versioned Intents.
- Build deterministic, versioned Prompts with output contracts, traceability metadata, and content digests.
- Validate proposals manually and promote accepted Insight Proposals to immutable Insights.
- Retrieve Insights by project, analysis, type, and severity.
- Use an Angular engineering dashboard to manage Projects and Sources, launch guided Analyses,
  monitor deterministic and AI execution, review evidence, decide Proposals, and consult Insights.
- Generate traceable user-facing Deliverables exclusively from human-validated Insights.

## Knowledge pipeline

```text
Git repository
      ↓
Evidence → Facts → Observations → Project Profile
                                      ↓
                              AnalysisContext + Intent
                                      ↓
                           SelectedKnowledge snapshot
                                      ↓
                                  AI Engine
                                      ↓
                              Insight Proposal
                                      ↓
                              Human validation
                                      ↓
                            Trusted, immutable Insight
                                      ↓
                          Deliverable Generation
                                      ↓
                         Generated Deliverable
```

The Java Core owns repositories, deterministic knowledge, workflow state, validation, and trusted
Insights. Its Knowledge Selection Engine ranks, deduplicates, and budgets project knowledge for the
resolved Intent. The Python AI Engine only interprets the immutable `SelectedKnowledge` supplied by
the Core. It does not read repositories or create trusted knowledge directly.

At the AI boundary, the Core sends a provider-independent `PromptRequest`. The AI Engine builds an
immutable `Prompt`, and the configured provider only adapts it to its API. Prompt versions,
SHA-256 content digests, provider/model identifiers, and selection digests are persisted with AI Tasks
without logging complete prompt content.

Prompt Version expresses the governed semantics of a template, while Prompt Digest identifies its
exact normalized rendered content. Both are retained on historical AI Tasks for audit and replay
verification.

## Architecture

| Component    | Technology                    | Responsibility                                           |
| ------------ | ----------------------------- | -------------------------------------------------------- |
| Core backend | Java 21, Spring Boot 4.1, JPA | Domain model, collection, orchestration, validation, API |
| AI Engine    | Python 3.12, FastAPI          | Intent-driven structured interpretation                  |
| Frontend     | Angular 22, RxJS, SCSS        | Reactive project workspace and Human-in-the-Loop review  |
| Database     | PostgreSQL 17, Flyway         | Durable domain state and migration history               |
| Runtime      | Docker Compose                | Local orchestration of all services                      |

Important documentation:

- [Architecture](docs/architecture.md)
- [Knowledge model](docs/knowledge-model.md)
- [Pipeline](docs/pipeline.md)
- [Logging policy](docs/logging-policy.md)
- [Architecture Decision Records](docs/decisions/)
- [AI Engine documentation](ai-engine/README.md)
- [Frontend documentation](frontend/README.md)
- [Manual MVP test guide](frontend/docs/manual-mvp-test.md)

## Quick start with Docker

### Requirements

- Docker Engine with the Compose plugin
- Git, only for cloning this repository
- Approximately 2 GB of free memory for the three services

No LLM API key is required for the default deterministic mock provider.

### Installation

```bash
git clone <repository-url>
cd devlog-ai
docker compose up --build -d
docker compose ps
```

Wait until `postgres` and `ai-engine` are healthy and the backend is running. Flyway applies the
database migrations automatically when the backend starts.

Available services:

| Service          | URL                                   |
| ---------------- | ------------------------------------- |
| Core API         | http://localhost:8080                 |
| Swagger UI       | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON     | http://localhost:8080/v3/api-docs     |
| AI Engine health | http://localhost:8000/health          |
| PostgreSQL       | `localhost:5432`                      |

Docker Compose starts PostgreSQL, Java Core, and the AI Engine. Start Angular separately:

```bash
cd frontend
npm install
npm start
```

The dashboard is then available at http://localhost:4200. Its development proxy forwards `/api`
to Java Core on port 8080 without weakening backend CORS.

Useful commands:

```bash
docker compose logs -f backend ai-engine
docker compose stop
docker compose down
```

`docker compose down` preserves the database and repository workspaces in named volumes. Adding
`--volumes` deletes that local data.

## Configuration

Docker Compose accepts environment variables from the shell or a local `.env` file.

### AI provider

The default provider is deterministic and suitable for local development:

```bash
LLM_PROVIDER=mock docker compose up --build -d
```

To use OpenAI:

```bash
LLM_PROVIDER=openai \
LLM_MODEL=gpt-4.1-mini \
LLM_API_KEY=<api-key> \
docker compose up --build -d
```

Main settings:

| Variable                     | Default        | Purpose                             |
| ---------------------------- | -------------- | ----------------------------------- |
| `LLM_PROVIDER`               | `mock`         | `mock` or `openai`                  |
| `LLM_MODEL`                  | `gpt-4.1-mini` | Model used by the OpenAI provider   |
| `LLM_API_KEY`                | empty          | Required when `LLM_PROVIDER=openai` |
| `LLM_TIMEOUT_SECONDS`        | `30`           | LLM request timeout                 |
| `LLM_MAX_OUTPUT_TOKENS`      | `2000`         | Maximum structured output size      |
| `CORE_CALLBACK_MAX_ATTEMPTS` | `5`            | Maximum callback attempts           |
| `BACKEND_LOG_LEVEL_APP`      | `INFO`         | Application log level               |
| `AI_ENGINE_LOG_LEVEL`        | `INFO`         | AI Engine log level                 |
| `POSTGRES_DB`                | `devlog_ai`    | Database name                       |
| `POSTGRES_USER`              | `devlog`       | Database user                       |
| `POSTGRES_PASSWORD`          | `devlog`       | Local database password             |

Collector limits can be adjusted with `COLLECTION_MAX_FILES`, `COLLECTION_MAX_FILE_SIZE`,
`COLLECTION_MAX_TOTAL_BYTES`, `COLLECTION_MAX_FACTS_PER_TYPE`, and
`COLLECTION_COLLECTOR_TIMEOUT`.

For production-like backend behavior, set `SPRING_PROFILES_ACTIVE=prod`. This disables Swagger and
OpenAPI, disables JPA Open Session in View, and emits ECS-compatible structured JSON logs. Secrets
and non-default database credentials must be supplied by the deployment environment.

## Running an analysis from the dashboard

The normal MVP path is:

1. Ensure a Project exists in Core, then open http://localhost:4200/projects and select it.
2. Register and activate a Git repository Source in the Project workspace.
3. Choose a Core-provided Intent and optional structured User Guidance.
4. Create and explicitly launch the Analysis.
5. Monitor deterministic diagnostics and AI Task status on `/analyses/:id`.
6. Inspect provider/model, Guidance snapshot, selection and prompt versions/digests, and proposals.
7. Open a Proposal, inspect its rationale and evidence, then explicitly accept or reject it.
8. Confirm that only a Core-accepted Proposal appears as an immutable validated Insight.

The frontend calls Java Core exclusively. It never contacts the Python service directly, constructs
prompts, accepts Proposals automatically, or exposes provider credentials. The default Mock provider
identifies itself as `mock` / `deterministic-v1` and returns zero Proposals unless deterministic test
output is configured.

For a complete Mock/OpenAI walkthrough and troubleshooting, use the
[manual MVP test guide](frontend/docs/manual-mvp-test.md).

## Running an analysis through the API

The complete API is documented in Swagger. The minimal workflow is:

1. Create a project with `POST /api/v1/projects`.
2. Register a public Git repository with `POST /api/v1/sources`.
3. List available Intents with `GET /api/v1/intents`.
4. Create an analysis with `POST /api/v1/analyses`.
5. Start it with `POST /api/v1/analyses/{analysisId}/workflow`.
6. Inspect proposals with `GET /api/v1/proposals/analysis/{analysisId}`.
7. Accept or reject a proposal with `POST /api/v1/validations`.
8. Retrieve trusted knowledge with `GET /api/v1/insights/analysis/{analysisId}`.

Example validation of an Insight Proposal:

```json
{
  "proposalId": "<proposal-uuid>",
  "decision": "ACCEPTED",
  "comment": "Reviewed against the referenced evidence.",
  "validatedBy": "<validator-uuid>",
  "insightSeverity": "INFO"
}
```

`insightSeverity` is mandatory when accepting an Insight Proposal because severity expresses human
business importance, not model confidence. Rejecting a proposal never creates an Insight.

Currently registered Intents are `describe-project-v1`, `generate-readme-v1`, and
`architecture-overview-v1`. Arbitrary free-form prompts are intentionally rejected.

An analysis may include optional, structured User Guidance without changing its Intent:

```json
{
  "projectId": "<project-uuid>",
  "type": "ARCHITECTURE_REVIEW",
  "intentId": "architecture-overview-v1",
  "userGuidance": {
    "focus": "Distributed architecture",
    "audience": "Recruiters",
    "levelOfDetail": "Concise",
    "writingStyle": "Pedagogical",
    "outputContext": "Portfolio",
    "priorities": ["Explain Docker before Spring Boot"]
  }
}
```

Guidance only controls emphasis and presentation. It cannot introduce Insight categories, alter
the output schema, weaken grounding rules, or override the selected Intent.

## Importing deterministic project history

The first vertical slice of [ADR-035](docs/decisions/ADR-035.md) and
[ADR-036](docs/decisions/ADR-036.md) imports the Git history of a configured repository into Java
Core. It persists immutable factual commit metadata, ordered parents, first-parent file changes,
line statistics and binary-file flags. Repository identity plus the full commit hash is unique, so
re-importing the same history is idempotent.

Core also constructs a bounded `CommitDiffAnalysisContext` containing deterministic file
categories, language hints, directly modified ADR/roadmap references, evidence references and
truncation warnings. It stores no unrestricted raw patch and creates no trusted interpretation.
Future AI interpretation must enter through a versioned Intent and the existing immutable Proposal
validation lifecycle.

API endpoints:

- `POST /api/v1/project-history/repositories/{repositoryId}/imports`
- `GET /api/v1/project-history/projects/{projectId}/commits`
- `GET /api/v1/project-history/repositories/{repositoryId}/commits/{commitHash}/context`

The optional `revision` query parameter selects an explicit import target. Context limits are
configured with `HISTORY_CONTEXT_MAX_FILES` and `HISTORY_CONTEXT_MAX_CHANGED_LINES`.

## Repository-first analysis context

[ADR-037](docs/decisions/ADR-037.md) is implemented by a deterministic
`RepositoryContext` assembled during knowledge selection. It layers the current Analysis,
repository Facts and Observations, recent Git history, directly identified ADR and roadmap
knowledge, validated Insights, previous analyses and project documentation. Repository evidence is
ranked using the versioned Intent and optional User Guidance, but guidance cannot create evidence or
override repository facts.

The context is stored inside the immutable selected-knowledge snapshot of each AI task. Every item
contains an evidence reference, its repository layer, a bounded factual summary and related
references. The AI Engine accepts only those exact references in generated Proposals. No full
repository, unrestricted source file or raw Git diff is added to the prompt.

Repository-context limits are configurable with:

- `REPOSITORY_CONTEXT_MAX_EVIDENCE_ITEMS` (default `60`);
- `REPOSITORY_CONTEXT_MAX_SUMMARY_CHARACTERS` (default `500`);
- `REPOSITORY_CONTEXT_MAX_HISTORY_ITEMS` (default `20`);
- `REPOSITORY_CONTEXT_MAX_TOKENS` (default `6000`).

### Repository Context Engine

[ADR-038](docs/decisions/ADR-038.md) turns repository-first selection into an explicit Core
pipeline:

```text
Context Profile
→ independent Context Collectors
→ deterministic Evidence Ranking
→ diverse Evidence Selection
→ token budget
→ RepositoryContext
```

The initial collectors cover the current Analysis, deterministic Facts and Observations, Git
history, and existing Core knowledge such as ADR decisions, roadmap milestones, validated Insights,
previous analyses and documentation artifacts. Collectors implement a common interface and carry
their identifier and version in extraction metadata, so adding a collector does not require changes
to the engine.

Profiles currently distinguish project state, architecture review, documentation, README
generation, release summary, knowledge extraction and history analysis. The context snapshot records
the selected profile, token allocation, ranking reasons, selected/discarded decisions and evidence
provenance. Context construction remains entirely inside Java Core; the AI Engine only interprets
the bounded result.

### Context Intelligence and Evidence scoring

[ADR-039](docs/decisions/ADR-039.md) adds a deterministic decision layer before the Context Engine.
Each versioned Intent references one or more predefined Context Profiles. The initial compositions
are:

- `describe-project-v1` → `project-state-v1` + `history-v1`;
- `architecture-overview-v1` → `architecture-v1` + `history-v1`;
- `generate-readme-v1` → `documentation-v1` + `project-state-v1`.

Context Intelligence composes the profile weighting and diversity policies into a versioned
`ContextPlan`. Evidence is then scored independently on semantic relevance, architectural
relevance, historical relevance, recency, deterministic source confidence, and User Guidance
alignment. The final 0–100 score is the weighted result of those visible criteria; collectors no
longer assign an arbitrary final relevance score.

For V1, semantic relevance is a deterministic lexical and metadata comparison. It does not use
embeddings or a vector store. Evidence confidence describes the reliability of its deterministic
source (for example Git or validated knowledge); it is not model confidence, business severity, or
a truth probability. Every snapshot records the profile composition, policy versions, criterion
values, weights, final score and explanations.

## Generating Deliverables

ADR-034 adds a second, deliberately separate AI responsibility. Analysis AI proposes knowledge;
Deliverable AI communicates knowledge that humans have already accepted. From a Project workspace,
choose one of `PROJECT_DESCRIPTION`, `README`, `ARCHITECTURE_SUMMARY`,
`PORTFOLIO_DESCRIPTION`, `TECHNICAL_SUMMARY`, or `BLOG_ARTICLE`, then specify audience, style,
language, and optional bounded guidance.

For the shortest user flow, accept one or more proposals, return to the source Analysis, then use
**Generate a Deliverable** immediately below its Validated Insights. This sends the Project and
Analysis scope to Core and opens the persisted result when generation completes.

Core selects only persisted `Insight` records. Facts, Observations, AnalysisContext, source code,
pending Proposals, and rejected Proposals never enter the Deliverable request. Every generated
result stores its source Insight IDs, generation time, prompt version/digest, provider, and model.

API endpoints:

- `POST /api/v1/deliverables`
- `GET /api/v1/deliverables/{id}`
- `GET /api/v1/deliverables/project/{projectId}`
- `GET /api/v1/deliverables/analysis/{analysisId}`

The Angular detail route is `/deliverables/:id`. Generated Markdown-compatible content is displayed
as safe text rather than injected HTML.

## Local development without Docker

### Requirements

- Java 21
- PostgreSQL 17 available on `localhost:5432`
- Python 3.10 or newer
- Node.js 22 and npm for the Angular frontend
- Git

Create the PostgreSQL database and credentials expected by
`backend/src/main/resources/application.properties`, or override `DB_URL`, `DB_USERNAME`, and
`DB_PASSWORD`.

Start the AI Engine:

```bash
cd ai-engine
python -m venv .venv
. .venv/bin/activate
python -m pip install -e ".[dev]"
LLM_PROVIDER=mock uvicorn app.main:app --reload --port 8000
```

In another terminal, start the Core:

```bash
cd backend
./mvnw spring-boot:run
```

In another terminal, start Angular:

```bash
cd frontend
npm install
npm start
```

### Tests

```bash
cd backend
./mvnw test
```

Backend integration tests require PostgreSQL with the configured development credentials.

```bash
cd ai-engine
. .venv/bin/activate
pytest
```

```bash
cd frontend
npm test
npm run build
```

## Project status and next steps

Implemented foundations include the domain model, deterministic repository collection, diagnostic
reporting, immutable project profiles, AnalysisContext construction, deterministic knowledge
selection, intent-driven AI processing, human validation, production-oriented correlation logging,
immutable Insight promotion, and a reactive Angular dashboard covering the first complete
Project-to-Insight workflow.

The main remaining product work is:

- richer editable/exportable projections built from generated Deliverables;
- authentication-backed reviewer identity and authorization;
- richer comparison and document-projection interfaces;
- durable AI job execution instead of in-process background tasks;
- credential management for private repositories;
- production deployment, monitoring, and backup configuration;
- vector retrieval and local-model support, if justified by future ADRs.

The authoritative roadmap is maintained in [docs/roadmap.md](docs/roadmap.md). Architectural changes
must remain consistent with the accepted ADRs in [docs/decisions](docs/decisions/).

## Design principles

- Deterministic evidence before probabilistic interpretation.
- The Core remains the source of truth.
- The AI Engine never reads repositories directly.
- AI output is a proposal, never trusted knowledge.
- Human validation is mandatory.
- Every Insight remains traceable to its Project, Analysis, proposal, Intent, context, Facts, and
  Evidence.
- Validated Insights are immutable and independent from their future presentation.
