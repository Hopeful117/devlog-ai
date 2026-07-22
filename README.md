# DevLog AI

> Build traceable, human-validated knowledge from a software repository.

DevLog AI analyzes Git repositories through deterministic collectors, builds an immutable project
context, and asks an AI engine for structured interpretations. AI output is never trusted directly:
it becomes a proposal that must be accepted by a human before it is promoted to an immutable
Insight.

The project is currently a backend-first reference implementation. It exposes REST APIs and
OpenAPI documentation; a user interface and document projections are not implemented yet.

## Current capabilities

- Manage projects and Git repository sources.
- Clone and synchronize repository workspaces at a branch, tag, or commit.
- Collect versioned Facts about repository metadata, builds, Spring, Docker, documentation, and
  test structure.
- Derive deterministic, traceable Observations from Facts.
- Produce immutable Project Profile and AnalysisContext snapshots.
- Diagnose partial collection and expose analysis warnings.
- Run an asynchronous Core-to-AI workflow with correlation IDs, retries, and structured logs.
- Generate structured Insight Proposals from versioned Intents.
- Validate proposals manually and promote accepted Insight Proposals to immutable Insights.
- Retrieve Insights by project, analysis, type, and severity.

## Knowledge pipeline

```text
Git repository
      ↓
Evidence → Facts → Observations → Project Profile
                                      ↓
                              AnalysisContext + Intent
                                      ↓
                                  AI Engine
                                      ↓
                              Insight Proposal
                                      ↓
                              Human validation
                                      ↓
                            Trusted, immutable Insight
```

The Java Core owns repositories, deterministic knowledge, workflow state, validation, and trusted
Insights. The Python AI Engine only interprets the immutable context supplied by the Core. It does
not read repositories or create trusted knowledge directly.

## Architecture

| Component | Technology | Responsibility |
| --- | --- | --- |
| Core backend | Java 21, Spring Boot 4.1, JPA | Domain model, collection, orchestration, validation, API |
| AI Engine | Python 3.12, FastAPI | Intent-driven structured interpretation |
| Database | PostgreSQL 17, Flyway | Durable domain state and migration history |
| Runtime | Docker Compose | Local orchestration of all services |

Important documentation:

- [Architecture](docs/architecture.md)
- [Knowledge model](docs/knowledge-model.md)
- [Pipeline](docs/pipeline.md)
- [Logging policy](docs/logging-policy.md)
- [Architecture Decision Records](docs/decisions/)
- [AI Engine documentation](ai-engine/README.md)

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

| Service | URL |
| --- | --- |
| Core API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| AI Engine health | http://localhost:8000/health |
| PostgreSQL | `localhost:5432` |

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

| Variable | Default | Purpose |
| --- | --- | --- |
| `LLM_PROVIDER` | `mock` | `mock` or `openai` |
| `LLM_MODEL` | `gpt-4.1-mini` | Model used by the OpenAI provider |
| `LLM_API_KEY` | empty | Required when `LLM_PROVIDER=openai` |
| `LLM_TIMEOUT_SECONDS` | `30` | LLM request timeout |
| `LLM_MAX_OUTPUT_TOKENS` | `2000` | Maximum structured output size |
| `CORE_CALLBACK_MAX_ATTEMPTS` | `5` | Maximum callback attempts |
| `BACKEND_LOG_LEVEL_APP` | `INFO` | Application log level |
| `AI_ENGINE_LOG_LEVEL` | `INFO` | AI Engine log level |
| `POSTGRES_DB` | `devlog_ai` | Database name |
| `POSTGRES_USER` | `devlog` | Database user |
| `POSTGRES_PASSWORD` | `devlog` | Local database password |

Collector limits can be adjusted with `COLLECTION_MAX_FILES`, `COLLECTION_MAX_FILE_SIZE`,
`COLLECTION_MAX_TOTAL_BYTES`, `COLLECTION_MAX_FACTS_PER_TYPE`, and
`COLLECTION_COLLECTOR_TIMEOUT`.

For production-like backend behavior, set `SPRING_PROFILES_ACTIVE=prod`. This disables Swagger and
OpenAPI, disables JPA Open Session in View, and emits ECS-compatible structured JSON logs. Secrets
and non-default database credentials must be supplied by the deployment environment.

## Running an analysis

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

## Local development without Docker

### Requirements

- Java 21
- PostgreSQL 17 available on `localhost:5432`
- Python 3.10 or newer
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

## Project status and next steps

Implemented foundations include the domain model, deterministic repository collection, diagnostic
reporting, immutable project profiles, AnalysisContext construction, intent-driven AI processing,
human validation, production-oriented correlation logging, and immutable Insight promotion.

The main remaining product work is:

- document projections built from validated Insights;
- a user interface for analysis and validation;
- authentication and authorization;
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
