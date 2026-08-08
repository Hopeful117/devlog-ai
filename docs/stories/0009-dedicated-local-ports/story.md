# Story 0009 — Dedicated Local Runtime Ports

## Story ID
0009

## Title
Run DevLog continuously without occupying common development ports

## Status
Completed

## Priority
High

## Date
2026-08-08

---

## User Story

As a developer running DevLog continuously as Kiko's local engineering-context service,
I want DevLog to use a dedicated and configurable host-port namespace,
So that its backend, AI Engine and database do not prevent other applications from using common development ports.

---

## Context

DevLog is becoming a persistent local service used by Kiko during Engineering Story preparation. Its current Docker Compose defaults publish common development ports directly on the host:

* Java Core: `8080`;
* AI Engine: `8000`;
* PostgreSQL: `5432`.

These defaults are convenient for an isolated project but create avoidable conflicts when DevLog remains active while other Spring Boot, Python or PostgreSQL applications are developed and tested.

DevLog already allows host-port overrides through `BACKEND_PORT`, `AI_ENGINE_PORT` and `POSTGRES_PORT`, but the default values still occupy the conventional ports. Several local consumers and documents also assume those values, including the Angular development proxy, standalone backend defaults, AI Engine callback defaults, README instructions and manual test commands.

The Angular frontend is currently the only application component excluded from Docker Compose. Developers must install Node dependencies and run `npm start` separately on the conventional Angular development port `4200`. Since the frontend already uses relative `/api` URLs, it can be built as static assets and served by a small container that forwards `/api` to Java Core on the internal Compose network. Including it in the normal runtime would make DevLog a complete continuously running local platform rather than a three-service backend runtime plus a separately managed development server.

Docker-internal service communication already uses service names and container ports:

```text
backend → postgres:5432
backend → ai-engine:8000
ai-engine → backend:8080
```

Those internal ports do not collide with other host applications and should not be changed without a demonstrated need. The intended change concerns the host-facing development contract.

---

## Objective

Give DevLog a coherent, documented and configurable set of non-standard local host ports so its normal Docker runtime can remain active alongside other development environments.

Include the production-built Angular frontend in Docker Compose as a fourth application service, with its own dedicated host port and same-origin internal proxy to Java Core.

The implementation should minimize disruption by separating:

* stable container-internal ports used only inside the DevLog Compose network;
* dedicated host ports used by browsers, Kiko, local tools and processes launched outside Docker.

---

## Acceptance Criteria

### AC-1: Dedicated host-port defaults

The default Docker Compose configuration must no longer bind DevLog services to host ports `8080`, `8000`, `5432` or the conventional Angular development port `4200`.

The Angular frontend, Java Core, AI Engine and PostgreSQL host bindings must use a coherent DevLog-specific port range selected during Repository Analysis and Implementation Planning.

The selected defaults must:

* be distinct from one another;
* avoid the conventional ports used by Spring Boot, FastAPI and PostgreSQL projects;
* remain valid unprivileged TCP ports;
* be documented as DevLog local-development defaults.

### AC-2: Host ports remain configurable

Existing environment-based overrides must remain supported:

* `BACKEND_PORT`;
* `AI_ENGINE_PORT`;
* `POSTGRES_PORT`;
* `FRONTEND_PORT` for the new frontend publication.

A developer must be able to override any published port without editing tracked files.

If a frontend development-port setting is introduced or changed, it must follow the same configurable approach rather than hardcoding a machine-specific value.

### AC-3: Container-internal communication remains stable

Service-to-service communication inside Docker Compose must continue to use Docker service names and the existing container ports unless Repository Analysis demonstrates a concrete reason to change them.

Changing host bindings must not break:

* backend-to-PostgreSQL connectivity;
* backend-to-AI-Engine requests;
* AI-Engine-to-backend callbacks;
* frontend-to-backend `/api` forwarding over the Compose network;
* health checks.

### AC-3a: Angular frontend is part of the Compose runtime

Docker Compose must build and run the Angular frontend without requiring a host Node.js process.

The frontend container must:

* use a deterministic production build based on the tracked lock file;
* serve the generated `dist/frontend/browser` assets from a minimal maintained static web server image;
* run without root privileges in its runtime stage;
* support Angular client-side routes through an `index.html` fallback;
* forward `/api` requests to Java Core through `http://backend:8080` or the equivalent internal service URL;
* expose a container health check;
* remain independently buildable and testable from the other services.

The browser must continue to call Java Core only and must never contact the AI Engine or PostgreSQL directly.

### AC-4: Local non-Docker consumers use the new defaults

Tracked local-development configuration that communicates through host ports must use the new default contract where appropriate.

Repository Analysis must identify and classify at least:

* the Angular development proxy;
* the containerized frontend's internal `/api` proxy;
* standalone backend datasource and AI Engine defaults;
* standalone AI Engine Core callback default;
* test fixtures that intentionally assert configuration defaults;
* local curl commands and manual test instructions.

Internal Docker URLs must not be replaced with host URLs.

### AC-5: Kiko integration remains configurable

The DevLog Engineering Story Context endpoint must remain reachable through the configured Java Core host port.

No Engineering-Skills or OpenClaw workspace file may be modified by this DevLog-owned Story. The implementation documentation must identify that an existing external DevLog base-URL mapping may need its port updated after deployment.

The API path and response contract must remain unchanged.

### AC-6: Persistent local runtime coexists with conventional ports

DevLog's normal Docker Compose runtime must be able to start while unrelated local processes occupy host ports `8080`, `8000`, `5432` and `4200`.

Validation must demonstrate this behavior without modifying or stopping unrelated applications.

### AC-7: Documentation is consistent

Current operational documentation must accurately describe:

* the new default service URLs;
* the Docker-served frontend URL and the optional standalone Angular workflow;
* environment-variable overrides;
* Docker-internal versus host-facing ports;
* Angular proxy expectations;
* commands used for health checks and manual API validation.

Historical Engineering Story reports must not be rewritten merely because they record the ports that were valid when those Stories were executed.

### AC-8: Existing behavior remains unchanged

The port change must not alter:

* API routes or payloads;
* database schema or persisted data;
* authentication or authorization behavior;
* Repository Context collection and ranking;
* AI task semantics;
* Human Approval workflows.

### AC-9: Automated and live validation

Validation must include:

* Docker Compose configuration rendering with default values;
* Docker Compose configuration rendering with explicit overrides;
* startup and health verification using the new defaults;
* a Java Core API request through the new host port;
* an AI Engine health request through its new host port;
* a frontend health/static-content request through its new host port;
* a frontend `/api` request proving internal proxy connectivity to Java Core;
* PostgreSQL reachability where host access is intentionally retained;
* backend tests affected by local configuration defaults;
* frontend tests/build and proxy validation where affected;
* confirmation that conventional ports remain available to unrelated processes while DevLog runs.

### AC-10: No machine-specific configuration is committed

The implementation must not commit personal absolute paths, local project UUIDs, credentials, tokens or a developer-specific `.env` file.

Tracked examples may document supported variables and defaults without containing secrets.

---

## Scope

### In Scope

* Select dedicated default host ports for DevLog's local runtime.
* Add a production-built Angular frontend service to Docker Compose.
* Add the minimal frontend container and static-server/proxy configuration required for SPA routing and `/api` forwarding.
* Update Docker Compose host bindings while preserving environment overrides.
* Align tracked host-facing development configuration and directly affected tests.
* Align the Angular proxy if its backend target uses the changed host port.
* Update current README and operational/manual-test documentation.
* Validate continuous DevLog operation alongside applications using conventional ports.
* Document the external Kiko base-URL adjustment required after the change.

### Out of Scope

* Changing DevLog API paths or response contracts.
* Changing Docker-internal service ports without demonstrated necessity.
* Modifying Engineering-Skills, OpenClaw `TOOLS.md` or other repositories.
* Automatic service discovery.
* Introducing a reverse proxy, service mesh or container orchestrator.
* Adding TLS, authentication or production network exposure.
* Redesigning Docker Compose topology beyond adding the bounded frontend service.
* Database migrations or data resets.
* Rewriting historical Story validation evidence.
* Implementing the proposed DevLog/Delegate Task provider architecture.

---

## Impacted Components

Repository Analysis should confirm the exact affected set. Likely components include:

* `docker-compose.yml` — host-port defaults and existing override variables;
* backend local configuration defaults and their tests;
* AI Engine local Core callback configuration and its tests;
* `frontend/proxy.conf.json` and potentially Angular serve configuration;
* a new frontend Dockerfile and static-server configuration;
* `README.md` and current operational documentation;
* manual test scripts or commands that target host URLs;
* external deployment notes for Kiko's configured DevLog base URL.

Production controller, domain, persistence and Repository Context components are not expected to change.

---

## Architectural Ownership and Boundaries

* Docker Compose owns default local service exposure and internal service wiring.
* Each application owns its standalone local-development defaults.
* Frontend configuration owns the development proxy target.
* The frontend container owns static asset serving, SPA fallback, and same-origin `/api` forwarding; it does not become a business API gateway or own backend workflow logic.
* Environment variables own machine-specific overrides.
* Engineering-Skills/OpenClaw owns Kiko's repository-to-DevLog mapping and must be updated separately by its operator, not by this Story.
* Container-internal ports are implementation details of the DevLog runtime network; host ports are the public local-development contract.

No new ADR is expected unless Repository Analysis discovers a broader networking or service-discovery decision beyond local port allocation.

---

## Risks

### Partial configuration update

Changing Compose bindings without updating host-side consumers could leave Docker healthy while Angular, local backend execution or Kiko still targets the old port.

### Internal and external port confusion

Replacing Docker service URLs with new host ports would break container-to-container communication. The implementation must classify every URL by network boundary before changing it.

### Local test assumptions

Application-context tests and manual commands may rely on conventional localhost ports. Repository Analysis must distinguish deliberate default assertions from incidental historical references.

### External consumer drift

Kiko's base URL is configured outside DevLog and cannot be updated atomically by this Story. The final implementation report must clearly state the required operational adjustment.

### Frontend routing or proxy regression

Serving Angular as static files without SPA fallback would break deep links, while forwarding `/api` to a host URL would fail inside Compose. The frontend runtime must verify both client-side routes and internal service-name proxying.

### False collision validation

Stopping existing applications to make DevLog start would not prove coexistence. Validation must preserve unrelated processes and demonstrate that DevLog no longer claims the conventional ports.

---

## Validation Strategy

Use configuration inspection first, then targeted application tests, followed by a real Compose startup. Verify both default and overridden mappings.

A practical coexistence test should temporarily bind harmless local listeners or use already-running unrelated services on the conventional ports, start the complete four-service DevLog runtime with its new defaults, and confirm both sets remain reachable. The validation must be non-destructive and must not interfere with existing project data.

---

## Definition of Done

* [ ] All acceptance criteria are satisfied.
* [ ] DevLog no longer occupies host ports `8080`, `8000`, `5432` or `4200` by default.
* [ ] The Angular frontend is built and served by Docker Compose on its dedicated host port without requiring host `npm start`.
* [ ] Angular deep links and same-origin `/api` forwarding work through the frontend container.
* [ ] Host-port environment overrides remain functional.
* [ ] Docker-internal service communication and health checks remain functional.
* [ ] Host-side consumers and current documentation use the new defaults consistently.
* [ ] DevLog starts alongside unrelated services using conventional ports.
* [ ] Kiko's required external base-URL update is documented.
* [ ] Relevant backend and frontend validation passes.
* [ ] No persistence data is deleted or migrated.
* [ ] No external repository or machine-specific configuration is modified.
* [ ] Code Review is complete.
* [ ] Engineering Report is produced after all Human Approval Gates.
