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

## Formats and configuration

The backend uses readable key-value logs by default and ECS JSON when the
Spring `prod` profile is active. The AI Engine always emits JSON. Levels are
controlled through `BACKEND_LOG_LEVEL_ROOT`, `BACKEND_LOG_LEVEL_APP`, and
`AI_ENGINE_LOG_LEVEL` in Docker Compose.
