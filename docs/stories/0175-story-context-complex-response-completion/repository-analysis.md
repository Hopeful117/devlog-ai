# Repository Analysis - Story 0175

## Status

Repository Analysis completed. Human approval is required before producing the
Implementation Plan. No production implementation is authorized by this
artifact.

## Scope

Story 0175 addresses the remaining execution gap after Story 0173: a complex
Story Context request can pass the Core budget preflight while the provider
still terminates generation at the configured output ceiling. The objective is
not to hide or retry incomplete output, but to establish a measured completion
policy or reject unsupported requests before provider invocation.

## Governance and Context

- Story 0175 is the canonical Story for complex Story Context response
  completion policy.
- ADR-079 requires distinct selection, input, output, model-capacity and safety
  budgets, but does not guarantee that an accepted output allowance is adequate
  for every response complexity.
- Story 0173 owns diagnostic classification and Core preflight. Its current
  behavior correctly records `response.incomplete(max_output_tokens)` as a
  terminal provider failure.
- ADR-067 and ADR-069 require Java/Core authority for context, grounding and
  provider-call authorization. MCP and REST remain thin adapters.
- AI output remains untrusted and incomplete output must never be parsed or
  persisted as a successful analysis.

The DevLog Story Agent applicability check was attempted for `devlog-ai`. The
focused and broader requests both failed at the LLM provider with
`response.incomplete; reason=max_output_tokens`. The repository is therefore
the authoritative source for the current physical implementation. No Obsidian
vault note was consulted.

## Current Execution Path

```text
MCP devlog_story_agent
  -> Java Core Story Context submission
  -> Core-owned projection and budget preflight
  -> authorized maxOutputTokens in PromptRequest
  -> AI Engine prompt construction
  -> provider adapter request
  -> structured result or terminal provider failure
```

`mcp-server/.../DevLogStoryAgentTool.java` delegates to the same Core Story
Context capability used by other transports. It does not own provider budgets
and contains no transport-specific completion workaround.

`StoryContextSubmissionService` constructs the `PromptRequest`, carries Core
authorized provider/model parameters, invokes `StoryContextBudgetPreflight`,
records `budgetAccounting` and submits only after an accepted preflight.

`StoryContextBudgetPreflight` measures a serialized prompt estimate, adds a
fixed prompt-policy overhead and safety margin, and verifies that the effective
output allowance fits the catalogued model capacity. It does not estimate the
minimum output required by the requested intent, guidance or response schema.

The preflight therefore proves capacity safety, not response-completion
sufficiency.

## Relevant AI Engine Behavior

`ai-engine/app/core/config.py` defaults `LLM_MAX_OUTPUT_TOKENS` to `2,000`.
`app/core/dependencies.py` supplies this value to the ChatGPT provider unless
Core sends an authorized `maxOutputTokens` parameter.

`app/providers/chatgpt.py` sends the request allowance as
`max_output_tokens`. It raises a bounded `ChatGptProviderError` when the stream
reports `response.incomplete`, including the normalized reason. This behavior
is correct for Story 0173 and must remain terminal.

The current provider test proves that Core-authorized model and output values
are forwarded, and that `max_output_tokens` is diagnosed. It does not prove the
full MCP-to-Core-to-AI Engine configuration path or that a representative
complex response completes within the configured allowance.

## Confirmed Gap

The observed MCP failure is consistent with the current contracts:

1. Core sees a known model capacity and an output allowance that fits the total
   estimated context budget.
2. Core authorizes the provider call.
3. The provider receives the configured output ceiling, currently commonly
   `2,000` tokens.
4. ChatGPT needs more output for the structured complex analysis and emits
   `response.incomplete(max_output_tokens)`.
5. The system correctly reports failure but returns no usable analysis.

This is not evidence that the Core preflight was bypassed. It is evidence that
the accepted policy is insufficient to establish completion for complex
responses.

## Affected Surfaces

- `backend/.../storycontextanalysis/usecase/StoryContextBudgetPreflight.java`
  for the completion-policy decision and bounded policy metadata.
- `backend/.../storycontextanalysis/usecase/StoryContextSubmissionService.java`
  and `StoryAgentFollowUpService.java` for consistent policy application.
- Core provider catalog and user configuration for the effective allowance and
  model-policy source.
- `devlog-contracts` and Story Context callback/diagnostic contracts if new
  policy fields are required.
- `ai-engine/app/providers/chatgpt.py` and other supported adapters for exact
  allowance propagation and normalized provider behavior.
- `ai-engine/app/prompts/story_context_analysis.py` for bounded response-shape
  and complexity inputs, without moving authority to Python.
- MCP and REST contract tests for transport parity.
- Runtime/operator documentation for policy rejection versus provider
  interruption.

## Existing Tests and Gaps

Existing tests cover:

- known and unknown model capacity preflight;
- invalid or insufficient configured output allowance;
- ChatGPT incomplete reason formatting;
- direct provider propagation of an authorized model and output allowance;
- Core Story Context submission regression behavior.

Missing validation includes:

- end-to-end MCP request to effective provider allowance correlation;
- deterministic completion-policy outcomes for broad, targeted and reduced
  projections;
- proof that policy rejection happens before provider invocation;
- representative complex structured response completion under the selected
  policy;
- transport parity between MCP and REST;
- measured completion, latency, cost and quality evidence for any policy change;
- Python behavioral suite execution in the current environment, which Story
  0173 reported as unavailable because `pytest` was not installed.

## Constraints

- Do not silently increase production output or context limits.
- Do not infer actual provider usage from `maxOutputTokens`.
- Do not parse, retry as valid or persist incomplete provider output.
- Keep Core authoritative for policy, scope, grounding and authorization.
- Keep provider-specific details inside adapters and use `UNKNOWN` when values
  are unavailable.
- Keep diagnostics bounded and free of credentials, prompts, repository payloads
  and raw provider output.
- Preserve context, projection, grounding and task identities.
- Define additive/versioned mixed-version behavior before changing strict
  contracts.
- Do not alter MCP bearer renewal or delegation semantics owned by Story 0174.

## Decisions Required in the Implementation Plan

1. Whether the first policy uses a bounded intent/schema complexity class, a
   deterministic response-size estimate, an approved allowance catalog, or a
   combination of these inputs.
2. Whether a supported complex policy changes an existing configured allowance
   or introduces an explicitly configured per-intent allowance. Any production
   change requires measured evidence and separate human review.
3. Which Core-owned policy identity and measurement certainty fields are needed
   for audit and caller diagnostics.
4. How provider/model differences and unknown capabilities fail closed.
5. How complete-response evidence is replayed without live credentials in CI.
6. Which exact REST/MCP parity tests prove that transport adapters cannot bypass
   the policy.

## Risks

- A larger allowance may increase cost and latency without improving quality.
- Prompt length alone is not a reliable predictor of structured response size.
- Provider tokenization and schema overhead differ across adapters.
- An over-conservative policy may reject useful requests.
- Configuration drift may cause direct provider tests to pass while MCP runtime
  requests still use the default ceiling.

## Recommendation

Proceed to Implementation Planning only after human approval of this Repository
Analysis. The plan should first establish an end-to-end measurement and
propagation test, then define the smallest Core-owned completion policy. It
should not begin by increasing `LLM_MAX_OUTPUT_TOKENS` globally.

## Approval Required

Repository Analysis completed.

Human approval required before Implementation Planning.
