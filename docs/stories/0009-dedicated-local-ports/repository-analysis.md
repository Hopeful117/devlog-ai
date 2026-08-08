# Repository Analysis

## Story Understanding

Story 0009 requests a dedicated, configurable host-port namespace for DevLog's local runtime so that the platform can remain active without occupying the conventional Spring Boot, FastAPI, PostgreSQL, and Angular development ports. The amended Story also requests that the production-built Angular frontend become a fourth Docker Compose application service.

The requested behavior concerns the boundary between the host and DevLog services. Docker-internal communication must continue to use the existing service names and container ports. Host-side consumers—including the Angular development proxy, processes launched outside Docker, local diagnostic commands, and Kiko's separately owned configuration—must use the new host-facing contract.

The Story includes Docker Compose defaults and overrides, standalone local-development defaults, a bounded frontend static-serving and same-origin `/api` proxy boundary, directly affected tests and documentation, and coexistence validation. It excludes API changes, Docker-internal port changes without demonstrated necessity, persistence changes, a general-purpose API gateway, new networking infrastructure, automatic service discovery, and modifications to Engineering-Skills or OpenClaw configuration.

Repository inspection found no architectural conflict. A coherent currently unused range is available and satisfies the Story constraints:

* Java Core host/default standalone port: `18080`;
* AI Engine host/default standalone port: `18081`;
* PostgreSQL host port used by DevLog: `18082`;
* Angular frontend host port: `18083`.

The existing Core, AI Engine, and PostgreSQL container ports remain `8080`, `8000`, and `5432` respectively. The frontend can listen on unprivileged container port `8080` because it has its own network namespace.

## Repository Summary

DevLog is composed of a Java 21/Spring Boot Core, a Python/FastAPI AI Engine, an Angular frontend, and PostgreSQL. Docker Compose currently orchestrates only Core, AI Engine, and PostgreSQL; the Angular application is started separately with `npm start` on host port `4200`.

`docker-compose.yml` currently publishes `${BACKEND_PORT:-8080}:8080`, `${AI_ENGINE_PORT:-8000}:8000`, and `${POSTGRES_PORT:-5432}:5432`. Its service-to-service URLs correctly use the Compose network: the backend connects to `postgres:5432` and `ai-engine:8000`, while the AI Engine calls `backend:8080`. The AI Engine health check calls `localhost:8000` inside its own container. Both Dockerfiles expose the established container ports.

Host-facing defaults are distributed across Compose, Spring configuration, Python settings, the Angular proxy, README files, and current manual-test documentation. The amended scope also introduces a frontend packaging boundary, but remains a local runtime/configuration change rather than a domain or API change.

The current Docker runtime was verified as occupying host ports `8080`, `8000`, and `5432`; standalone Angular conventionally occupies `4200`. Candidate host ports `18080` through `18083` were not listening at analysis time. Compose already renders explicit port overrides correctly, which confirms that the existing environment-variable mechanism can be preserved and extended for the frontend.

## Affected Modules

### Root runtime configuration

`docker-compose.yml` owns the published host bindings and Docker-internal service wiring. Its host-side defaults are directly affected. The internal destinations, container ports, health checks, dependency conditions, named volumes, and service names must remain stable.

`.env.example` is the tracked, secret-free configuration example. It currently documents AI provider variables but not the already supported host-port overrides. It is relevant to documenting the local port contract without committing the developer's ignored `.env` file.

### Java Core (`backend`)

`backend/src/main/resources/application.properties` owns standalone defaults for the datasource and AI Engine client. It currently targets `localhost:5432` and `localhost:8000`. Spring Boot has no explicit `server.port` property, so standalone Core currently uses Spring's conventional `8080` default.

Docker overrides the datasource and AI Engine URLs with `postgres:5432` and `ai-engine:8000`; those values are internal and must remain unchanged. If the standalone Core default moves to `18080`, Compose must continue to run the Core container on `8080` explicitly rather than allowing a host-facing default to alter the container contract.

`AIEngineProperties` and `AIEngineClientConfig` consume the configured AI Engine URL but contain no hardcoded port behavior. No controller, API contract, entity, repository, migration, or Repository Context component is affected.

### Python AI Engine (`ai-engine`)

`ai-engine/app/core/config.py` owns the standalone callback target and currently defaults `CORE_BASE_URL` to `http://localhost:8080`. `ai-engine/app/core/dependencies.py` passes that value to `CoreCallbackClient`.

The Docker image intentionally runs Uvicorn on container port `8000`, and Compose intentionally supplies `CORE_BASE_URL=http://backend:8080`. Those internal contracts must remain unchanged. The host-facing standalone launch command and callback default are affected by the dedicated-port contract.

The current Python tests validate callback behavior with injected test URLs but do not assert the environment-derived default `core_base_url`. Configuration-default coverage is therefore missing for this change.

### Angular frontend (`frontend`)

`frontend/proxy.conf.json` sends development `/api` requests to `http://localhost:8080` and must follow the Core host port. `frontend/angular.json` already references the proxy configuration for development. Both Angular environments use a relative backend base URL, so production/same-origin API behavior is unaffected.

No frontend Dockerfile or static-server configuration exists. The tracked `package-lock.json` supports a deterministic `npm ci` build, and the current production build emits browser assets under `frontend/dist/frontend/browser`. Angular defines client-side routes, so a static runtime must fall back to `index.html` for non-file routes. Because application API URLs are relative, the runtime server can forward `/api` to `http://backend:8080` without changing Angular application code.

A standard multi-stage Node build followed by a maintained unprivileged Nginx runtime is sufficient; no custom application server or new frontend dependency is required. This is compatible with ADR-005 because the frontend remains the user entry point and Java Core remains the only business API boundary. The proxy is a bounded deployment adapter, not a general-purpose gateway.

No existing frontend test directly asserts the development proxy target or container behavior. The frontend README and manual MVP test contain host-facing URLs and commands that are affected.

### Documentation and local operations

The root `README.md`, `frontend/README.md`, `frontend/docs/manual-mvp-test.md`, and the backend AI Engine client README describe current local URLs. Current operational documentation must be aligned while historical Story reports must retain the ports that were valid when those validations occurred.

Kiko's DevLog base URL is stored outside this repository. This Story may document the required post-deployment adjustment but may not update Engineering-Skills or OpenClaw files.

## Existing Implementation

### Existing behavior

* Compose exposes backend `8080`, AI Engine `8000`, and PostgreSQL `5432` on the host by default.
* `BACKEND_PORT`, `AI_ENGINE_PORT`, and `POSTGRES_PORT` already override only the published host side. A rendered configuration using `18080`, `18081`, and `18082` correctly retains container targets `8080`, `8000`, and `5432`.
* Backend-to-database and backend-to-AI traffic uses Docker service names and container ports.
* AI Engine callbacks use `backend:8080` in Docker.
* The Angular development proxy targets the backend's current host port.
* Angular production builds already produce static browser assets and application APIs use same-origin relative URLs.
* The frontend is currently launched separately with a host Node.js process on port `4200`.
* The ignored local `.env` file can provide machine-specific values; `.env.example` is safe to version and contains no credentials.
* Docker volumes preserve PostgreSQL data and collected workspaces independently of published host ports.

### Missing behavior

* Default Compose startup still claims all three conventional host ports.
* Standalone Core and AI Engine defaults still assume conventional host ports.
* The Angular proxy and current operational documentation still target port `8080`.
* Compose has no frontend service, `FRONTEND_PORT`, frontend image, runtime health check, SPA fallback, or internal `/api` proxy.
* The tracked environment example does not advertise the existing port overrides.
* There is no automated assertion for the Python callback URL default or a repository-level check of default and overridden Compose mappings.
* Coexistence with unrelated listeners on `8080`, `8000`, and `5432` has not been demonstrated.
* Coexistence with an unrelated listener on `4200` has not been demonstrated.

### Behavior that must remain unchanged

* API paths and payloads, including Engineering Story Context GET and POST operations.
* Container ports and Docker service-name URLs.
* AI task submission and callback contracts governed by ADR-019.
* Repository Context collection, ranking, provenance, selection, and budgets governed by ADR-037 and ADR-038.
* Repository Evidence and Trusted Knowledge separation governed by ADR-040.
* Database schema, credentials contract, named volumes, and persisted data.
* Human Approval workflows and external Kiko project mapping ownership.
* The browser-to-Core-only responsibility boundary established by ADR-005.

### Relevant tests and validation surfaces

* `DevlogAiBackendApplicationTests` exercises the complete Spring context and therefore the configured datasource default when running outside Compose.
* `RestAIEngineClientIntegrationTest` validates the backend HTTP client with an ephemeral server and is not tied to a fixed port.
* `ai-engine/tests/test_core_callback_client.py` validates callback behavior with an injected URL but not the settings default.
* `ai-engine/tests/test_health.py` validates the AI Engine health contract independently of its published port.
* `SmallClassCoverageTest.aiEngineProperties` uses `localhost:8080` as constructor data, not as an application-default assertion; it is not evidence that this value must change.
* Angular tests use relative application URLs and do not currently validate `proxy.conf.json`, static serving, SPA fallback, or container proxying.
* `docker compose config` already provides a deterministic way to inspect default and overridden host mappings without starting services.

Relevant repository validation commands include the backend Maven test/verify lifecycle, the AI Engine pytest suite, Angular tests and production build, an independent frontend image build, Compose configuration rendering, and live checks for static content, deep-link fallback, health, and proxied `/api` access through the selected host ports.

## Relevant Documentation

* `README.md`
* `frontend/README.md`
* `frontend/docs/manual-mvp-test.md`
* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/README.md`
* `docs/architecture.md`
* ADR-005 — Frontend to Backend Communication Strategy
* ADR-019 — Core to AI Engine REST Submission Contract
* ADR-037 — Repository-First Context Extraction
* ADR-038 — Repository Context Engine
* ADR-040 — Knowledge and Evidence Separation
* Story 0009 — Dedicated Local Runtime Ports
* Engineering Story workflow and Repository Analysis role documentation

No repository `AGENTS.md` or repository-local `docs/workflow/` documents exist.

## Constraints

* The selected host defaults are `18080`, `18081`, `18082`, and `18083` for Core, AI Engine, PostgreSQL, and frontend respectively; the existing backend container targets remain `8080`, `8000`, and `5432`.
* Existing `BACKEND_PORT`, `AI_ENGINE_PORT`, and `POSTGRES_PORT` overrides must remain supported, and `FRONTEND_PORT` must control the new frontend publication.
* Docker-internal URLs must continue to use service names, not host ports or `localhost` across containers.
* Container health checks must continue to address the service from inside its own container.
* Standalone defaults and Docker defaults must be kept distinct where necessary so changing a host contract does not silently change a container listener.
* The Angular development proxy must follow the Core host default; production relative-URL behavior must remain unchanged.
* The frontend image must build from the tracked lock file, serve `dist/frontend/browser`, run unprivileged, support SPA fallback, proxy `/api` to `backend:8080`, and expose a container health check.
* The frontend proxy must not contact the AI Engine or PostgreSQL and must not acquire business workflow responsibilities.
* The ignored `.env` and any credentials or machine-specific project identifiers must not be committed.
* Historical Story artifacts must not be rewritten.
* Engineering-Skills and OpenClaw configuration are externally owned and out of scope.
* The existing database volumes must not be deleted or reset during validation.
* Coexistence validation must not stop or modify unrelated services merely to free conventional ports.
* No ADR is required because the change specializes the existing local-development configuration without changing service boundaries or network architecture.

## Risks

### Host/container boundary regression

Applying the new host ports to Docker-internal URLs, Dockerfile listeners, or container health checks would break service communication even though the new published mappings appear correct.

### Partial consumer migration

Updating Compose without updating the proxy, standalone defaults, and current documentation would produce a healthy Docker runtime that local tools and Kiko cannot reach using the documented contract.

### Standalone versus container server-port drift

Spring currently inherits port `8080`. Introducing a dedicated standalone default without explicitly preserving container `8080` could make Compose publish a port where no backend process listens.

### External mapping drift

The Kiko mapping cannot be changed by this Story. After deployment, `TOOLS.md` will still point to `http://localhost:8080` until its owner performs the documented operational update.

### Incomplete test signals

Several relevant values live in configuration files rather than typed production code. Existing tests do not directly cover every default or the Angular proxy target, so configuration rendering and live validation are necessary in addition to application tests.

### Frontend packaging and routing regression

An incorrect build output path, static root, or fallback rule could produce a healthy container that fails to serve Angular assets or deep links. An incorrect proxy target could serve the UI while all API operations fail.

### Build reproducibility or runtime privilege regression

Using an unlocked dependency install, copying host build artifacts, or selecting a root-only runtime would violate the bounded and reproducible container contract even if local startup succeeds.

### Misleading coexistence validation

Stopping the current DevLog containers or unrelated applications before the final coexistence check could make the result meaningless. Validation must distinguish the transition restart from the actual proof that conventional ports remain usable while DevLog runs on its dedicated ports.

## Open Questions

None.

The host-port range is selected by this analysis, ownership is clear, and the repository contains the configuration boundaries and validation mechanisms needed for safe planning.

## Recommendation

Ready for planning

This is a technical recommendation only. It does not approve the Repository Analysis or authorize Implementation Planning.

## Implementation Readiness

Story 0009 can be implemented using the current repository. No missing API, domain ownership, persistence change, migration, application dependency, or ADR is required. The frontend can use established maintained Node and unprivileged Nginx images rather than introducing custom runtime code.

The important planning concern is coordination across the host-facing configuration surfaces while explicitly preserving container-internal listeners and URLs, plus verification of the frontend build/static/proxy boundary. Existing environment overrides, relative Angular API URLs, production build output, and Compose rendering provide the necessary technical foundation. The only external operational prerequisite is updating Kiko's separately owned base URL after the new backend host port is deployed; that update is not part of this Story.

## Approval Required

Repository Analysis completed.

Human approval required before Implementation Planning.

Awaiting explicit human approval.
