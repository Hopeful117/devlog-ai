# Implementation Plan

## Overview

Implement Story 0009 as a coordinated local-runtime configuration change across Docker Compose, Java Core, the AI Engine, the Angular frontend, tests, and current operational documentation.

The default host contract will use `18080` for Java Core, `18081` for the AI Engine, `18082` for PostgreSQL, and `18083` for the Docker-served frontend. Existing container listeners and service-name URLs will remain stable. The Angular production build will be packaged in a dedicated unprivileged static-server image that supports client-side routing and forwards same-origin `/api` traffic to `backend:8080`.

The standalone Angular development workflow will remain available. It will continue to use relative application API URLs while its development proxy follows the new Core host default. No public API, persistence model, Repository Context behavior, Human Approval behavior, or external Engineering-Skills/OpenClaw configuration will change.

## Planned Changes

1. **Establish the four-port host contract in tracked configuration.**
   * Extend `.env.example` with `BACKEND_PORT=18080`, `AI_ENGINE_PORT=18081`, `POSTGRES_PORT=18082`, and `FRONTEND_PORT=18083` as secret-free, overridable local defaults.
   * Change only the published host sides in `docker-compose.yml` for the existing services.
   * Preserve backend `8080`, AI Engine `8000`, PostgreSQL `5432`, existing service names, volumes, and internal URLs.

2. **Align Java Core standalone defaults without changing its container contract.**
   * Configure the standalone Spring server default as `18080`, the standalone datasource default as `localhost:18082`, and the standalone AI Engine URL as `localhost:18081` through environment-overridable properties.
   * Supply the Core container listener explicitly as `8080` from Compose so the new standalone default cannot move the process away from the existing container target.
   * Keep Docker datasource and AI Engine URLs on `postgres:5432` and `ai-engine:8000`.

3. **Align AI Engine standalone defaults without changing its container contract.**
   * Change the environment-derived standalone Core callback default to `http://localhost:18080`.
   * Keep Compose `CORE_BASE_URL=http://backend:8080` and the Docker/Uvicorn listener on `8000`.
   * Update or add focused settings coverage for the environment-free callback default and explicit environment override.

4. **Package the Angular production build as an independent container.**
   * Add a multi-stage frontend Dockerfile: use the tracked lock file with `npm ci`, run the production build, and copy only `dist/frontend/browser` into a maintained unprivileged static-server runtime image.
   * Add a static-server configuration that listens on an unprivileged container port, serves immutable assets, falls back to `index.html` for Angular routes, and proxies `/api` to `http://backend:8080` while preserving the request path.
   * Add a container-local HTTP health check that verifies the frontend server itself without depending on a host port.
   * Keep the browser isolated from direct AI Engine and PostgreSQL access.

5. **Add the frontend to Docker Compose.**
   * Add a `frontend` service built from the frontend Dockerfile and publish `${FRONTEND_PORT:-18083}` to its internal static-server port.
   * Apply the same restart and health-management conventions as the existing local runtime where appropriate.
   * Do not turn the frontend proxy into a general gateway and do not replace internal service-name addressing with host URLs.

6. **Preserve and align standalone Angular development.**
   * Update `frontend/proxy.conf.json` to target `http://localhost:18080` by default.
   * Keep `npm start` and the Angular development server available as an optional developer workflow on `4200`; Compose must not claim that port.
   * Leave the Angular environment files on relative API URLs so the same application build works through the container's same-origin proxy.

7. **Update current operational documentation.**
   * Update the root and frontend READMEs, the current manual MVP test, and the backend AI Engine client documentation wherever they describe active local defaults.
   * Document the four service URLs, environment overrides, internal-versus-host port distinction, Docker-served frontend, optional standalone frontend workflow, health/API checks, and the post-deployment Kiko base-URL adjustment.
   * Retain historical Story artifacts unchanged.

8. **Validate configuration, applications, and coexistence in increasing scope.**
   * Run focused backend, AI Engine, and frontend tests/builds first.
   * Render Compose with defaults and with explicit overrides; verify published and target ports separately.
   * Build the frontend image independently before starting the complete runtime.
   * Start all four services without deleting volumes, verify health/static content/deep links/proxied API/direct Core/direct AI/PostgreSQL access, and confirm conventional host ports remain available to unrelated listeners.
   * Record the external Kiko configuration update as an operational handoff rather than modifying its owner repository.

## Files to Modify

* `.env.example` — document the four configurable host-port defaults.
* `docker-compose.yml` — change existing host publications, preserve explicit internal listeners/URLs, and add the frontend service.
* `backend/src/main/resources/application.properties` — set environment-overridable standalone Core, datasource, and AI Engine defaults.
* `ai-engine/app/core/config.py` — change the standalone Core callback default.
* AI Engine settings tests under `ai-engine/tests/` — verify default and overridden callback configuration.
* `frontend/proxy.conf.json` — align standalone Angular development with Core on `18080`.
* `README.md` — describe the complete four-service Docker runtime and new URLs.
* `frontend/README.md` — distinguish Docker-served and standalone Angular workflows.
* `frontend/docs/manual-mvp-test.md` — use the new operational URLs and validate the containerized frontend path.
* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/README.md` — align the documented standalone AI Engine URL.
* Directly affected backend configuration tests, if repository implementation reveals a focused existing test surface for the new Spring defaults.

## Files to Create

* `frontend/Dockerfile` — deterministic multi-stage Angular build and unprivileged static runtime.
* `frontend/nginx.conf` (or the equivalent static-server configuration selected during implementation) — static root, SPA fallback, `/api` proxy, and health endpoint behavior.
* `frontend/.dockerignore` — exclude host dependencies, build output, editor metadata, and other unnecessary build-context content while retaining manifests and source files.
* A focused AI Engine settings test file if no existing configuration test module is suitable.

## Dependencies

No new application library is required.

The frontend image depends on established maintained Node and unprivileged Nginx container images, the existing npm lock file, and the Angular production build. Runtime API forwarding depends only on the existing Compose DNS name `backend` and Core container port `8080`.

Implementation ordering matters:

1. define standalone and internal port boundaries;
2. create and independently validate the frontend image/configuration;
3. wire all four services in Compose;
4. align tests and documentation;
5. perform live coexistence validation;
6. update Kiko's external base URL only after this Story is completed, outside the DevLog repository.

The existing Docker volumes and local data are prerequisites for non-destructive runtime validation and must be preserved.

## Test Plan

### Configuration and unit validation

* Add AI Engine settings tests asserting `http://localhost:18080` without `CORE_BASE_URL` and preservation of an explicit override.
* Run the backend tests affected by Spring configuration and the broader Maven verification required by repository quality rules.
* Run the AI Engine pytest suite.
* Run Angular unit tests and `npm run build`; confirm the expected `dist/frontend/browser` output.

### Container validation

* Build the frontend image independently and inspect that the runtime uses a non-root user.
* Start the frontend container and verify its health/static endpoint.
* Request `/` and at least one Angular client-side deep link; both must serve the application rather than a server 404.
* Request a real Core endpoint through frontend `/api`; it must reach `backend:8080` without exposing a host URL to the browser.

### Compose contract validation

* Run `docker compose config` with no port variables and verify host mappings `18080`, `18081`, `18082`, and `18083` while internal targets remain unchanged.
* Render again with explicit `BACKEND_PORT`, `AI_ENGINE_PORT`, `POSTGRES_PORT`, and `FRONTEND_PORT` overrides and verify every host mapping changes independently.
* Start the complete four-service runtime without removing named volumes.
* Verify Core API/health behavior through `18080`, AI Engine health through `18081`, PostgreSQL host reachability through `18082`, and frontend static/proxy behavior through `18083`.
* Verify backend-to-database, backend-to-AI, and AI-to-backend communication still uses internal service names and ports.

### Coexistence validation

* While DevLog is running on its dedicated range, verify harmless unrelated listeners can bind `8080`, `8000`, `5432`, and `4200`, or preserve already-running unrelated services on those ports during startup.
* Do not stop unrelated applications to obtain a successful result and do not reset DevLog persistence.

Expected success means all four services are healthy and usable on the new defaults, explicit overrides render correctly, frontend deep links and `/api` forwarding work, conventional ports remain available, and application test suites show no Story-related regression.

## Risks

### Host and container port leakage

Changing internal URLs or failing to force the Core container listener to `8080` could break service communication. Mitigation: classify every value as host-facing or container-internal, assert rendered target ports, and exercise cross-service traffic.

### Frontend image serves an incomplete application

An incorrect Angular output path, static root, or SPA fallback can pass an image build while failing at runtime. Mitigation: build independently and validate root assets plus a real deep link.

### Frontend proxy masks backend failures

A healthy static server does not prove `/api` routing. Mitigation: validate a real Core endpoint through the frontend separately from the frontend health check.

### Non-reproducible or privileged frontend runtime

Using host `node_modules`, an unlocked install, or a root runtime would violate the Story contract. Mitigation: use `npm ci`, a `.dockerignore`, a multi-stage copy of build output only, and verify the runtime user.

### Partial documentation/configuration migration

Stale host URLs could leave developers or Kiko targeting the old ports. Mitigation: search current operational files for conventional port references, update active instructions, preserve historical artifacts, and explicitly hand off the external Kiko base-URL change.

### Destructive or misleading coexistence validation

Deleting volumes or stopping competing services would either risk data or invalidate the test. Mitigation: inspect state first, retain volumes, and use non-destructive listeners or already-running applications.

No risk requires clarification before implementation.

## Validation Checklist

* [ ] `.env.example` documents all four host-port variables without credentials.
* [ ] Compose defaults publish Core `18080`, AI Engine `18081`, PostgreSQL `18082`, and frontend `18083`.
* [ ] All four host publications can be overridden independently.
* [ ] Core still listens on `8080`, AI Engine on `8000`, and PostgreSQL on `5432` inside Compose.
* [ ] Docker service traffic still uses `postgres:5432`, `ai-engine:8000`, and `backend:8080`.
* [ ] Standalone Core, datasource, AI Engine, and Angular proxy defaults use the selected host contract.
* [ ] The frontend image uses `npm ci`, the tracked lock file, a production build, and an unprivileged runtime.
* [ ] The frontend serves `dist/frontend/browser`, supports Angular deep links, and exposes a health check.
* [ ] Frontend `/api` requests reach Java Core through the Compose network.
* [ ] The browser has no direct AI Engine or PostgreSQL route.
* [ ] Backend, AI Engine, and Angular tests/builds pass.
* [ ] Default and overridden Compose configurations render correctly.
* [ ] The complete four-service runtime starts without deleting existing volumes.
* [ ] Core, AI Engine, PostgreSQL, frontend static content, deep links, and proxied API behavior are verified live.
* [ ] Unrelated processes can use `8080`, `8000`, `5432`, and `4200` while DevLog runs.
* [ ] Current operational documentation uses the new contract consistently.
* [ ] Historical Story artifacts remain unchanged.
* [ ] No API, database, Repository Context, Human Approval, external repository, credential, or machine-specific configuration change is included.
* [ ] The required external Kiko base-URL update is reported for the post-Story operational step.

## Recommendation

Ready for implementation

This is a technical recommendation only. It does not approve the Implementation Plan or authorize Implementation.

## Approval Required

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.
