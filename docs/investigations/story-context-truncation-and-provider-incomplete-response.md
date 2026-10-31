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
