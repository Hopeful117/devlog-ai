# DevLog AI Frontend

Angular 22 standalone application for the DevLog AI MVP. Project list and detail pages read real
data from the Java Core. A Project workspace can also list and manage its Git repository Sources;
authentication is not implemented.

## Development

```bash
npm install
npm start
```

The development server is available at `http://localhost:4200/`.

The Java Core must be running locally on `http://localhost:8080`. Angular's development server uses
`proxy.conf.json` to forward `/api` requests to that address, avoiding browser CORS concerns without
changing backend CORS policy. Run the backend using the repository's documented Maven or Docker
Compose workflow before opening a Project page.

The typed `AppEnvironment` is supplied through Angular dependency injection. Development values use
Angular CLI file replacement (`environment.development.ts`); the default production configuration
uses same-origin relative API paths so a future reverse proxy or container deployment can route
Java Core traffic without service changes. Update the environment value and deployment routing when
a different Core origin is required.

## Implemented routes

- `/projects` — read-only Project list
- `/projects/:id` — read-only Project detail (the current backend expects the Project slug in the
  `:id` URL segment), with Project Source management
- `/analyses/:id` — Analysis execution diagnostics and monitoring
- `/deliverables/:id` — immutable generated Deliverable and complete generation traceability

The Project feature composes route parameters and HTTP calls as Observables. Components expose a
typed view-state Observable and templates consume it with Angular's `async` pipe; component code does
not manually subscribe or copy HTTP results into imperative state.

## Project Sources

The Project detail workspace uses the Project UUID returned by Core to call the supported Source
endpoints:

- `GET /api/v1/sources/project/{projectId}` — list all Sources for a Project
- `POST /api/v1/sources` — create a Git repository Source
- `PATCH /api/v1/sources/{sourceId}/activation` — activate or deactivate a Source

For local development, a Source can point to a repository reachable by the Java Core process, for
example:

```text
Name: DevLog AI
Repository URL: file:///workspace/repositories/devlog-ai
Default branch: main
Provider: GENERIC_GIT
```

A filesystem location is local to the Java Core process or container, so that path must be mounted
and readable there; it is not resolved from the browser machine.

Create and activation actions are composed through an Observable mutation stream. On success they
trigger a new `GET` request, keeping the backend list as the screen's source of truth instead of
appending or editing a local array.

Current MVP limitations: the visible form supports only `GIT_REPOSITORY`; Sources cannot be renamed,
otherwise edited, deleted, or authenticated with a Git provider. Repository accessibility is not
tested during creation, and there is no standalone Source synchronization control.

## Analysis and AI execution workflow

The Project workspace lists existing Analyses and provides a typed create-and-launch form. Core
deliberately separates the workflow into two requests, which the frontend preserves:

```text
POST /api/v1/analyses
POST /api/v1/analyses/{analysisId}/workflow
GET  /api/v1/analyses/{analysisId}/diagnostics
```

The launchable V1 Analysis types are `ARCHITECTURE_REVIEW` and `PROJECT_EVOLUTION`. Intent choices
come from `GET /api/v1/intents`; the current catalog exposes `describe-project-v1`,
`generate-readme-v1`, and `architecture-overview-v1`.

Optional User Guidance supports focus, audience, level of detail, writing style, output context, and
up to ten priorities. Guidance refines the selected Intent and is never sent as an arbitrary prompt.
An optional target revision may be a branch, tag, or SHA; omitting it uses each active Source's
configured default or `origin/HEAD`.

The Analysis detail page polls diagnostics and `GET /api/v1/ai-tasks/analysis/{analysisId}`
immediately and then every two seconds in development
(five seconds in the production environment configuration). Polling uses non-overlapping requests
and stops after `COMPLETED`, `FAILED`, or a request error; an inline action restarts polling after a
transient failure. Profile, warning, and safely escaped context data also come from Java Core.

For a local end-to-end run, start PostgreSQL, Java Core, and the AI Engine with `docker compose up`;
configure at least one active Source whose repository is reachable inside the backend container.
The frontend never calls the AI Engine directly and never constructs a prompt. Core supplies a
typed `PromptRequest` containing the versioned Intent, structured User Guidance, and selected
deterministic knowledge. The Python service resolves the versioned template and returns structured
proposals to Core.

The collapsible **AI execution details** view exposes only persisted, safe metadata: Intent and
Guidance snapshots, selection and prompt versions/digests, provider/model, correlation ID, attempts,
and bounded failure diagnostics. Core has no rendered-prompt diagnostic endpoint, so the browser
does not reconstruct or display one. Proposal and validated-Insight queries refresh from Core when
the AI Task reaches a terminal state.

The default local provider is Mock (`deterministic-v1`) and returns zero proposals unless a test
fixture configures output. For a real OpenAI run, set these existing Python configuration variables
in a local ignored environment source, then rebuild/restart only `ai-engine`:

```text
LLM_PROVIDER=openai
LLM_MODEL=gpt-4.1-mini
LLM_API_KEY=<local secret>
LLM_TIMEOUT_SECONDS=30
LLM_MAX_OUTPUT_TOKENS=2000
```

Never commit the API key. Create a new Analysis after changing providers, and verify provider/model
in AI execution details. Set `LLM_PROVIDER=mock` again for deterministic credential-free runs.

Current Analysis limitations: only the two workflow-supported types are launchable, there is no
workflow cancel or automatic resubmission action, and provider configuration is server-side only.
There is no rendered-prompt endpoint or pre-flight provider-health business endpoint. Core currently exposes no REST
endpoints for the diagnostics-advertised Fact and Observation links, so the UI shows their typed
diagnostic counts rather than fetching invented or unbounded collections.

## Human-in-the-Loop Insight review

Analysis detail separates AI-generated **Insight Proposals** from trusted **Validated Insights**.
Proposals follow the immutable `PROPOSED → ACCEPTED | REJECTED` lifecycle defined by ADR-006.
Nothing is accepted automatically: reviewers open `/proposals/:id`, inspect rationale, confidence,
Fact and Observation identifiers, and evidence references, then confirm one explicit decision.

Core records decisions through `POST /api/v1/validations`. The current no-authentication MVP
requires the reviewer to supply their UUID; an optional note is limited to 2000 characters.
Accepting an Insight proposal also requires the human to select `INFO`, `WARNING`, or `CRITICAL`
severity. Only Core promotes an accepted proposal into an Insight. Rejected proposals remain visible
as immutable history and never appear in Insight queries.

After a decision, the frontend reloads the proposal, Validation, and Analysis Insights from Core.
An HTTP 409 caused by another reviewer also performs a fresh proposal read without retrying the
decision. `/insights` lists validated Insights for a supplied Project UUID and uses Core's type and
severity filter endpoints; `/insights/:id` displays immutable Insight provenance.

Evidence navigation links to the source Analysis and displays Fact/Observation IDs. Direct Fact and
Observation links remain unavailable because Core does not yet expose their detail endpoints.
Current limitations include manual reviewer UUID entry, no bulk review, no proposal or Insight
editing, no deletion, and no authentication.

## Deliverable generation

The Project workspace can generate the six ADR-034 V1 Deliverable types from validated Insights.
The primary workflow action also appears directly below **Validated Insights** on an Analysis: once
at least one proposal has been accepted, **Generate a Deliverable** opens an inline form and scopes
the generation to that Analysis. No action is offered while there is no validated knowledge.
The browser sends only type, audience, style, language, optional additional guidance, and Project
identity to Java Core. Core resolves the actual Insight input and calls the AI Engine; Angular never
constructs the prompt or supplies repository knowledge.

Generation is synchronous in V1 and protected against duplicate clicks. On success, the UI opens
`/deliverables/:id`, where content is rendered as escaped pre-wrapped text alongside source Insight
IDs, prompt version/digest, provider/model, and generation time. Projects without validated Insights
receive an explicit conflict instead of an ungrounded document.

Current limitations: no editing, export, deletion, background job status, partial Insight selection,
or comparison view. Project-wide generation uses all current validated Insights; the Core API also
supports optional Analysis-scoped generation.

## Visual conventions

The frontend uses a compact SCSS-only visual foundation. Global semantic CSS variables in
`src/styles.scss` define page and panel surfaces, borders, text hierarchy, restrained blue accent,
status colors, spacing, radii, shadow, focus ring, and maximum content width. Feature styles consume
these semantic tokens instead of maintaining independent palettes. Shared badge presentation maps
proposal status and Insight severity deterministically while retaining visible text labels.

The application shell provides responsive Projects and Insights navigation with an active-route
indicator. Cards, metadata grids, inline messages, controls, and keyboard focus share consistent
accessible styling without a UI framework.

For the complete create, analyze, accept, reject, conflict, reload, and troubleshooting walkthrough,
see [the manual MVP test guide](docs/manual-mvp-test.md).

## Verification

```bash
npm run build
npm test
```

## Application structure

```text
src/app/
├── core/
│   ├── config/       Environment and endpoint contracts
│   ├── http/         Application-wide HttpClient providers
│   └── layout/       Root application shell
├── shared/
│   ├── components/   Reusable presentation components
│   ├── models/       Cross-feature view models
│   └── utils/        Framework-independent helpers
└── features/
    ├── dashboard/
    ├── projects/
    ├── analyses/
    ├── insight-proposals/
    └── insights/
```

Feature pages are standalone components loaded lazily by `app.routes.ts`. Configuration contracts
and endpoint values are supplied centrally through Angular dependency injection.
