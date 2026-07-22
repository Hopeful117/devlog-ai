# DevLog AI manual MVP test

## Prerequisites

- PostgreSQL, Java Core, and the Python AI Engine are running.
- Angular is running at `http://localhost:4200` with its `/api` proxy.
- At least one Project has an active Git Source reachable by the Java Core process.
- Keep a reviewer UUID available for the unauthenticated MVP decision form.

Start the platform from the repository root:

```bash
docker compose up --build
```

Start the frontend in another terminal:

```bash
cd frontend
npm start
```

Useful diagnostics:

```bash
docker compose ps
docker compose logs backend
docker compose logs ai-engine
curl -sS http://localhost:8080/api/v1/intents
```

## Scenario A — Mock provider

Set `LLM_PROVIDER=mock` (the Docker Compose default), restart the AI Engine, and create a new
Analysis. Expect AI execution details to identify provider `mock`, model `deterministic-v1`, and a
terminal state. The default Mock implementation returns zero proposals, so the UI explicitly says
that no mock proposal was configured and never claims OpenAI was called. If a deterministic mock
fixture supplies proposals, review them with the Human-in-the-Loop steps below.

## Scenario B — OpenAI provider

Put the following values in a local ignored environment source; never commit the key:

```text
LLM_PROVIDER=openai
LLM_MODEL=gpt-4.1-mini
LLM_API_KEY=<your key>
LLM_TIMEOUT_SECONDS=30
LLM_MAX_OUTPUT_TOKENS=2000
```

Rebuild/restart only the AI Engine (`docker compose up -d --build ai-engine`), then create a new
Analysis. Confirm AI execution details show `openai` and the configured model. A valid completion
may produce one or more structured proposals whose Fact, Observation, and evidence references come
from that AnalysisContext. No Insight exists until a reviewer accepts a proposal.

## End-to-end Human-in-the-Loop scenario

1. Open `/projects`, select a Project, and confirm an active Source is listed.
2. In Analyses, choose **New Analysis**, select a supported type and Intent, then create and launch.
3. Open the Analysis and wait until diagnostics report `COMPLETED` or `FAILED`.
   Expand **AI execution details** and record revision, Guidance, selection/prompt digests,
   provider/model, and timestamps. Create two Analyses at the same revision with different Guidance
   to compare results without modifying templates.
4. Under **Insight Proposals**, open a `PROPOSED` proposal.
5. Inspect its rationale, confidence, Fact IDs, Observation IDs, evidence references, and structured payload.
6. Enter the reviewer UUID, choose a severity, optionally add a note, select **Accept proposal**, and confirm.
7. Expect the Proposal to reload as `ACCEPTED`, decision controls to disappear, and the Core-created validated Insight to appear.
8. Open another `PROPOSED` proposal, select **Reject proposal**, and confirm.
9. Expect it to remain visible as immutable `REJECTED` history and never appear under Validated Insights.
10. Reload both pages. Expect identical server-backed states.

To test a decision conflict, open the same proposed item in two tabs. Decide it in the first tab,
then submit a different decision in the second. The second tab should show the Core conflict and
refresh to the actual immutable state without retrying.

## Expected boundaries

- Only accepted `INSIGHT` proposals create Insights.
- Fact and Observation identifiers are displayed but are not links because Core has no detail REST endpoints.
- Reviewer identity is entered manually until authentication supplies it.
- The browser never calls the Python AI Engine directly.

## AI execution troubleshooting

- **Missing API key:** `ai-engine` refuses to start when `LLM_PROVIDER=openai` without
  `LLM_API_KEY`; inspect `docker compose logs ai-engine`.
- **Unsupported provider or model:** verify `LLM_PROVIDER` is exactly `mock` or `openai`, check the
  configured model, restart the AI Engine, and create a new Analysis.
- **Malformed structured output:** inspect the AI Engine logs and the task failure code/message.
  Python performs one corrective structured-output retry; Angular never repairs provider output.
- **Callback failure:** verify `CORE_BASE_URL` is reachable from the AI Engine and inspect callback
  retry logs. Do not resubmit from the browser.
- **Analysis stuck in progress:** compare Analysis diagnostics with AI Task status, then inspect both
  backend and AI Engine logs using the commands above.
- **Zero valid proposals:** distinguish a terminal successful task from failure. Mock returns zero by
  default; OpenAI may validly return zero grounded proposals.
- **Backend unavailable:** verify `docker compose ps`, Core logs, and the Angular `/api` proxy.
