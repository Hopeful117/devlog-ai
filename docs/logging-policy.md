# Production logging policy

DevLog AI logs operational events at system boundaries and state transitions.
Routine entity reads and writes are not logged individually.

## Correlation

- HTTP callers may provide `X-Correlation-ID` using letters, digits, `.`, `_`,
  `:`, or `-`, with a maximum length of 128 characters.
- Invalid or absent values are replaced with a UUID.
- The backend propagates the identifier to the AI Engine, which propagates it
  back with result callbacks.
- Every HTTP response includes the effective identifier.

## Levels

- `DEBUG`: diagnostic details disabled by default.
- `INFO`: successful HTTP requests and important workflow transitions.
- `WARN`: expected client errors, rejected contracts, retries, and failed AI
  tasks that do not compromise service availability.
- `ERROR`: unexpected exceptions, HTTP 5xx responses, and failed recovery
  transitions. Unexpected exceptions include their stack trace once at the
  application boundary.

## Data handling

Logs contain stable identifiers, statuses, counts, durations, and exception
types. Request bodies, generated prompts, model output, credentials, database
URLs, tokens, source contents, and exception response bodies must not be logged.

## Story Context Diagnostics

Normal interaction traces may record one bounded primary failure code:

- `GROUNDING_UNKNOWN_REFERENCE`: an output cites an unauthorized evidence reference;
- `GROUNDING_REQUESTED_FILE_SCOPE`: an output cites evidence outside the requested file scope;
- `GROUNDING_RELATION_TYPE`: relationship metadata is missing or invalid;
- `GROUNDING_CAUSAL_SUPPORT`: causal evidence roles or support are invalid;
- `GROUNDING_MISSING_EVIDENCE`: a factual or interpretive finding has no evidence reference;
- `GROUNDING_CONTRACT_INVALID`: the Core-owned grounding contract cannot be used safely;
- `GROUNDING_MAPPING_INVALID`: an authorized reference cannot be mapped safely;
- `PROVIDER_INCOMPLETE_RESPONSE`: the provider terminated before a complete response.

Unknown-reference and semantic grounding failures may receive one corrective retry.
Contract, mapping and provider-incomplete failures are terminal and must not trigger
another provider call. REST and MCP use the same Core task and AI Engine policy; no
transport-specific retry behavior is permitted.

Callback errors expose only the bounded code and a generic recovery message. Detailed
validation text remains in bounded interaction-trace diagnostics and must not include
raw prompts, repository payloads, credentials or provider response bodies. Operators
should inspect the task ID, context/projection digests, failure code and trace attempt
count before correcting the input or retrying the task through the normal workflow.

## Formats and configuration

The backend uses readable key-value logs by default and ECS JSON when the
Spring `prod` profile is active. The AI Engine always emits JSON. Levels are
controlled through `BACKEND_LOG_LEVEL_ROOT`, `BACKEND_LOG_LEVEL_APP`, and
`AI_ENGINE_LOG_LEVEL` in Docker Compose.
