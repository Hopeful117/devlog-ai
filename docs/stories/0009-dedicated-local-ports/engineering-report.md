# Engineering Report

## Story

Story 0009 — Dedicated Local Runtime Ports.

The Story moved DevLog's persistent local runtime away from conventional application ports and, after a human-approved scope amendment, included the Angular frontend as the fourth Docker Compose service.

## Objective

Allow DevLog to remain running continuously as Kiko's local engineering-context provider without blocking development and testing of other Spring Boot, FastAPI, PostgreSQL, or Angular applications.

The completed runtime uses dedicated, configurable host ports while preserving stable container-internal service contracts. It also removes the requirement to run the frontend through a separate host Node.js process for normal Docker usage.

## Repository Analysis Summary

Repository Analysis identified four application/runtime components: Java Core, Python AI Engine, PostgreSQL, and Angular. Compose originally managed only the first three and published conventional ports `8080`, `8000`, and `5432`; Angular was started separately on `4200`.

Host-facing values were distributed across Compose, Spring properties, Python settings, the Angular development proxy, and operational documentation. Internal traffic already used correct service-name URLs and had to remain unchanged.

Angular already produced `dist/frontend/browser`, used relative `/api` URLs, and defined client-side routes. This supported a bounded multi-stage build plus an unprivileged static server with SPA fallback and internal `/api` forwarding. ADR-005 allowed that deployment adapter while preserving Java Core as the only business API boundary.

The approved host range was:

* Core: `18080`;
* AI Engine: `18081`;
* PostgreSQL: `18082`;
* frontend: `18083`.

## Implementation Plan Summary

The approved plan separated host and container contracts before modifying individual modules. It then aligned standalone defaults, created and independently validated the frontend image, added the frontend to Compose, updated current documentation, and finished with full runtime and coexistence validation.

The plan explicitly preserved:

* Core `8080`, AI Engine `8000`, and PostgreSQL `5432` inside Compose;
* `postgres:5432`, `ai-engine:8000`, and `backend:8080` service communication;
* Angular's optional `npm start` workflow;
* all APIs, persistence, Repository Context, AI task, and Human Approval contracts;
* external ownership of Kiko's DevLog configuration.

## Implementation Summary

Compose now starts all four services and publishes `18080–18083` by default with independent environment overrides. Core explicitly listens on `8080` in its container while standalone Core, database, and AI Engine defaults use the dedicated host range. The AI Engine standalone callback and Angular development proxy use Core on `18080`.

The new frontend image builds Angular with Node 22 and `npm ci`, then copies only static browser output into `nginxinc/nginx-unprivileged`. Nginx runs as user `101`, serves `/health`, returns `index.html` for Angular routes, and forwards `/api` to `backend:8080`.

Live validation initially exposed an IPv6 resolution mismatch in the frontend health check. Replacing `localhost` with `127.0.0.1` removed the false unhealthy state. Code Review later found two stale README statements; both were returned to implementation and corrected before the final review.

After Gate 3 approval, Kiko's external DevLog Base URL was updated to `http://localhost:18080` in the OpenClaw workspace configuration. The repository mapping and project UUID were preserved.

## Modified Files

* `.env.example` — four documented host-port variables.
* `docker-compose.yml` — dedicated publications, explicit Core listener, frontend service, and health check.
* `backend/src/main/resources/application.properties` — standalone Core/database/AI defaults.
* `ai-engine/app/core/config.py` — standalone Core callback default.
* `frontend/proxy.conf.json` — standalone Core proxy target.
* `README.md` — complete runtime, port contract, overrides, and current workflows.
* `frontend/README.md` — Docker and standalone frontend workflows.
* `frontend/docs/manual-mvp-test.md` — four-service validation instructions.
* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/README.md` — updated standalone AI URL.
* `docs/stories/0009-dedicated-local-ports/story.md` — approved frontend Docker scope and completed workflow status.
* OpenClaw workspace `TOOLS.md` — operational DevLog Base URL updated to port `18080`; this file remains externally owned and is not part of the DevLog repository diff.

## Created Files

* `frontend/Dockerfile` — deterministic Angular build and unprivileged static runtime.
* `frontend/nginx.conf` — health, static content, SPA fallback, and Core proxy configuration.
* `frontend/.dockerignore` — bounded frontend build context.
* `ai-engine/tests/test_config.py` — default and override callback configuration tests.
* Story 0009 Repository Analysis, Implementation Plan, Implementation Report, Code Review Report, and Engineering Report artifacts.

## Architecture Impact

No domain, API, persistence, Repository Context, or AI workflow architecture changed. No new application abstraction or database migration was introduced.

The local deployment topology gained a bounded frontend container. Angular remains the user entry point, Java Core remains the business API and orchestration boundary, and the browser cannot access the AI Engine or PostgreSQL directly. Docker service dependencies continue to point through established internal names and ports.

The change is compatible with ADR-005 and preserves the contracts governed by ADR-019, ADR-037, ADR-038, and ADR-040. No new ADR was required.

## Validation

Validation completed successfully for the Story behavior:

* backend: 375 tests, 0 failures, 0 errors;
* AI Engine: 43 tests passed, including 2 new settings tests;
* frontend: 73 tests across 21 files passed;
* Angular production build passed;
* Maven verify and JaCoCo generation passed;
* frontend image built and runtime user `101` was verified;
* default Compose mapping `18080–18083` passed;
* independent override mapping `19080–19083` passed;
* complete four-service startup passed without deleting named volumes;
* frontend health, static assets, deep links, and `/api` forwarding passed;
* Core API, Engineering Story Context, AI health, and PostgreSQL reachability passed;
* Engineering Story Context returned 58 evidence items and a non-empty context digest through `18080`;
* coexistence passed with simultaneous listeners on `8080`, `8000`, `5432`, and `4200`;
* `git diff --check` passed.

SonarQube was running, but the Maven scanner received HTTP 401 because no scanner token was available. No analysis or Quality Gate result was produced. This was recorded as unavailable validation, not as a passing or failing Quality Gate.

## Review Outcome

The final Code Review verified all acceptance criteria as passing. It found no Blocker, Major, or Minor implementation issue after the stale documentation corrections were completed.

Technical recommendation: Ready for human approval with minor follow-up.

The follow-up is an authenticated SonarQube analysis when credentials are available. The current repository's Angular dependency audit output also remains part of normal dependency maintenance; the static runtime image does not contain the Node build dependencies.

Human Code Review approval: granted.

## Workflow Approvals

* Repository Analysis: Human approved
* Implementation Plan: Human approved
* Code Review: Human approved

## Remaining Work

Optional non-blocking follow-up:

* run an authenticated SonarQube analysis and record the resulting Quality Gate when a scanner token is available.

The required Kiko Base URL update is complete. No required Story implementation work remains.

## Lessons Learned

* Host publications and container listeners are different contracts; explicitly classifying every URL prevented dedicated host ports from leaking into service-to-service traffic.
* Relative frontend API URLs made Docker packaging possible without changing Angular application code or backend CORS policy.
* A healthy static endpoint and a working host request are not equivalent to a passing container health check. IPv4/IPv6 resolution must be validated inside the runtime image.
* Coexistence is best demonstrated with conventional ports actively occupied rather than merely observing that DevLog chose different ports.
* Operational consumers outside a provider repository require an explicit post-deployment configuration handoff.

## Final Status

Completed with Follow-up
