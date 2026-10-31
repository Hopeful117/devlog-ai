# Investigation: Story Context Truncation and Provider-Incomplete Responses

## Status

**INVESTIGATION_COMPLETE - ADR-079 AND STORY-0173 PROPOSED FOR HUMAN REVIEW**

This investigation makes no production change and does not authorize implementation.

## Question

Why did the latest live DevLog Story Context Agent test appear to return truncated
context, and how can DevLog distinguish Core context reduction from an incomplete
LLM response in a reproducible, operationally useful way?

## Scope and Sources

The investigation covered:

- Core repository-context collection, ranking, selection and Story Agent projection;
- AI Engine prompt construction, provider limits and interaction traces;
- Docker runtime defaults;
- existing Story Context Agent contracts, tests and evaluation documentation;
- the connected DevLog Story Context Agent execution.

The current repository at `faec4884cc759475486a8f2ab8bfe109f7d59142` is authoritative
for implementation state. DevLog context was useful for discovery but had
`freshness.state=UNKNOWN` and was itself truncated.

## Evidence Classification

### OBSERVED - Core context reduction is intentional

`RepositoryContextEngine` uses bounded defaults:

- maximum evidence items: `60`;
- maximum summary characters: `500`;
- maximum history items: `20`;
- maximum repository-context tokens: `6000`.

The engine sets `truncated=true` whenever candidates are discarded, not only when
the token budget is exceeded. It also emits warnings for item/token budget use,
summary truncation and enrichment warnings.

The Story Agent projection exposes `candidateCount`, `selectedCount`,
`discardedCount`, `usedTokens`, `budget`, `truncated` and warnings. It does not
expose the discarded evidence identities or category-level loss.

### OBSERVED - The latest DevLog context was truncated before generation

The first investigation request returned:

```text
candidateCount=437
selectedCount=60
discardedCount=377
usedTokens=2730
budget=6000
truncated=true
```

The targeted follow-up returned the same class of result:

```text
candidateCount=438
selectedCount=60
discardedCount=378
usedTokens=2758
budget=6000
truncated=true
```

This proves that the context projection was reduced. It does not prove that the
user-visible live incident had the same candidate population.

### OBSERVED - Provider generation was independently incomplete

Both connected Story Agent executions terminated with:

```text
ChatGPT response did not complete (response.incomplete; reason=max_output_tokens)
```

The AI Engine sends `max_output_tokens` to the ChatGPT Responses API and raises a
bounded provider error on `response.incomplete`. The provider test suite already
covers this exact reason.

The Docker default is `LLM_MAX_OUTPUT_TOKENS=2000`. The Story Context prompt
builder declares a `GenerationPolicy` maximum output size of `5000`, but the
actual provider request is controlled by Core-authorized generation parameters or
the AI Engine default. These values are not currently reconciled by a preflight
budget calculation.

### OBSERVED - Selection and generation budgets are independent

The Core repository-context token budget limits selected evidence. It does not
account for the complete AI prompt, which additionally contains:

- system instructions;
- intent and constraints;
- grounding contract;
- selected projection JSON;
- user guidance;
- full expected output JSON schema;
- truncation and grounding rules.

The AI Engine does not currently record a prompt token estimate or compare input
size, output allowance and model context capacity before calling the provider.

### HANDOFF - Why the effective Story Context allowance remains 2000

The effective `maxOutputTokens=2000` is currently intentional Core behavior, not
an MCP connectivity fallback:

1. The active ChatGPT user configuration persists `allowedParameters={}`. It does
   not provide a user-specific `maxOutputTokens` override.
2. Core classifies a projection with `accounting.truncated=true` as `REDUCED`.
3. Migration `V73` originally defined the active `REDUCED` completion policy for
   `openai_chatgpt:gpt-5.6-luna` with `target_output_tokens=2000`.
4. `StoryContextBudgetPreflight.maxOutputTokens(...)` derives the missing
   parameter from that policy and sends `{ "maxOutputTokens": 2000 }` in the
   Core-authorized request.
5. The AI Engine receives that authorized value and the ChatGPT adapter logs
   `outputParameterSource=CORE_AUTHORIZED`; its `LLM_MAX_OUTPUT_TOKENS=2000`
   default is not the source for Story Context executions once Core provides the
   parameter.

The provider log therefore correctly reported an effective allowance of `2000`
before the policy update described below.
Changing repository selection limits alone does not change this value: the
`REDUCED` policy remains selected as long as Core marks the projection truncated.
Changing the policy target or adding a user allowance would be a separate,
measured completion-policy decision and must not be introduced as a connectivity
workaround.

### OBSERVED - Temporary 2x completion-policy runtime experiment

To distinguish policy allowance from connectivity, the three active completion
policies were temporarily doubled and then restored:

| Class | Original target | Test target | Effective provider allowance |
|---|---:|---:|---:|
| `REDUCED` | 2,000 | 4,000 | 4,000 |
| `STANDARD` | 3,000 | 6,000 | not exercised |
| `COMPLEX` | 4,096 | 8,192 | not exercised |

The same runtime request then completed successfully. The AI Engine logs showed
`outputParameterSource=CORE_AUTHORIZED`, `authorizedOutputTokens=4000`, and
`effectiveOutputTokens=4000`; Core received the callback with HTTP `200` and
recorded the task as `COMPLETED`.

The selection projection remained independently truncated (`60/459` selected,
`399` discarded, `2644/6000` selection tokens used). This demonstrates that the
provider failure was caused by the `REDUCED` completion allowance of `2000`, not
by MCP, Core callback connectivity, or the selection-token budget. The temporary
database policy values were restored immediately after the experiment.

### IMPLEMENTED - Validated completion policy values

The runtime experiment validated the doubled values, which are now persisted by
Flyway migration `V74`:

| Class | Minimum | Target | Maximum |
|---|---:|---:|---:|
| `REDUCED` | 2,048 | 4,000 | 8,192 |
| `STANDARD` | 4,096 | 6,000 | 16,384 |
| `COMPLEX` | 8,192 | 8,192 | 32,768 |

The model context capacity remains unchanged. This policy change is independent
of MCP authentication and Core callback connectivity.

### OBSERVED - Failure diagnostics are incomplete at the AI boundary

`InteractionTraceCollector` records provider failure status and a bounded message,
but when the provider raises before returning a `ProviderGenerationResult`, input
and output token usage remain null. The ChatGPT streaming adapter also does not
retain provider response/request identifiers or incomplete response usage in the
Story Context trace.

Core persists projection accounting and digests, and recent interaction-trace
infrastructure persists optional diagnostic fields. However, normal traces do not
include prompt size, effective output limit, provider response state or terminal
phase. The current metrics only count broad events such as budget truncation and
digest mismatch.

### OBSERVED - Existing safety behavior is correct but opaque

The prompt explicitly tells the model to abstain when the authorized projection is
truncated. The provider adapter correctly refuses to parse an incomplete response.
The system therefore avoids silently treating incomplete output as valid
knowledge, but the caller cannot reliably tell whether the failure came from
context reduction, output exhaustion, parsing, validation or transport without
deep runtime inspection.

### NOT VERIFIED - Exact original live-test payload

The original live test did not retain an incident identifier or exact error
message. The current reproduction establishes a provider `max_output_tokens`
failure and a Core `truncated=true` projection, but cannot retroactively prove
which one was observed by the original caller.

## Root-Cause Model

The phrase “contexte tronqué” currently conflates at least two events:

```text
Core candidate collection
  -> bounded selection
  -> projection marked truncated
  -> prompt construction
  -> provider output ceiling
  -> response.incomplete(max_output_tokens)
```

The likely operational failure is therefore a diagnostic gap around a composite
budget problem, not evidence that Core silently dropped data or that the model
invented a partial successful answer.

## Impact

- Broad project questions can lose relevant evidence before generation.
- A large selected projection and verbose structured-output schema can leave too
  little output allowance for a valid response.
- Increasing only the Core context budget can make provider failures more likely.
- Increasing only the output budget can increase cost and still leave the prompt
  too large.
- Operators cannot currently determine which budget was exhausted from the normal
  result surface.

## Recommended Direction

1. Add end-to-end accounting for selection, serialized projection, prompt input,
   output allowance and provider terminal state.
2. Add a preflight check for input budget plus output budget plus safety margin.
3. Make failure phases and provider incomplete reasons explicit in snapshots,
   traces and bounded metrics.
4. Preserve fail-closed behavior: an incomplete provider response is not a
   successful analysis and must not be parsed or persisted as a result.
5. Prefer targeted file/question scopes or bounded follow-up retrieval over
   unconditionally increasing global context limits.
6. Evaluate any budget change against broad, targeted, empty and deliberately
   truncated scenarios.

## Governance Recommendation

An ADR is justified because the change defines cross-boundary semantics for
context budgets, provider limits, failure classification and observability. The
proposed ADR is `ADR-079`.

An implementation Story is justified and is proposed as `Story 0173`. It is
limited to accounting, diagnostics, preflight validation, tests and operator
feedback. It does not authorize RAG, a generic agent loop, Python-side retrieval,
or changes to the Java/Core authority boundary.

## References

- `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextEngine.java`
- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/StoryContextAgentProjectionV1.java`
- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/service/StoryContextAgentMetrics.java`
- `ai-engine/app/prompts/story_context_analysis.py`
- `ai-engine/app/providers/chatgpt.py`
- `ai-engine/app/services/interaction_trace.py`
- `ai-engine/app/schemas/interaction_trace.py`
- `docker-compose.yml`
- `docs/decisions/ADR-067.md`
- `docs/decisions/ADR-069.md`
- `docs/stories/0112-devlog-story-context-agent/story.md`
- `docs/stories/0172-mcp-runtime-observability-and-instance-lifecycle/story.md`
- `docs/evaluation/comparative-baseline-v4-provider-observability-accounting-remediation.md`

## Next Human Gate

Human review is required for `ADR-079` and `Story 0173` before implementation
planning or production code changes begin.
