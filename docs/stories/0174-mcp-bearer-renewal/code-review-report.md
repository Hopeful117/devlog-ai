# Code Review Report - Story 0174

## Review Result

The initial independent review found five major gaps. All five were corrected
and the focused validation was rerun. The corrected implementation keeps renewal
in the launcher boundary, preserves actor and provider ownership, does not replay
forwarded operations, and exposes only bounded diagnostic metadata.

## Initial Findings And Resolution

- Duplicate replacement handshake response: fixed by consuming the internal
  response and replaying only `notifications/initialized` to the child.
- First-request expiry bypass: fixed by applying lease gating before the first
  request as well.
- Malformed/non-object token response escape: fixed by normalizing JSON parsing
  and response-shape failures to `TOKEN_RESPONSE_INVALID`.
- Non-finite and too-short leases: fixed with finite-value and minimum-duration
  validation.
- JSON secret redaction gap: fixed with JSON key/value redaction patterns and
  expanded tests.

## Reviewed Areas

- Token lease expiry extraction and fail-closed malformed responses.
- Renewal single-flight boundary and cooldown behavior.
- Owned child replacement and initialize-only replay.
- Outstanding request tracking and no-replay semantics.
- STDOUT/STDERR separation and redaction behavior.
- Read-only status diagnostics and operator documentation.
- Story 0172 ownership invariants and ADR-077/078 boundaries.

## Residual Risks

- Live Keycloak renewal was not executed with disposable credentials.
- The implementation relies on the existing newline-delimited MCP STDIO framing;
  a future framing change would require an explicit protocol update.
- Renewal occurs on the next request after expiry/near-expiry rather than
  restarting an idle child proactively.
- Existing warnings from Java/Mockito test execution are unrelated to this
  Story and did not fail the MCP test suite.

## Validation Evidence

- Python runtime tests: 15 passed.
- MCP module tests: 70 passed.
- Python syntax compilation: passed.
- Read-only runtime status: passed.
- `git diff --check`: passed.

The independent review initially identified additional issues during iterative
review. They were corrected before this final report:

- replacement readiness now avoids holding the session lock while waiting for
  the replacement child;
- generation checks cover complete and partial stale stdout output;
- integer overflow and malformed JWT claim shapes are normalized;
- explicit MCP transport authentication errors have one bounded retry;
- the implementation has controlled tests for replay, generation isolation,
  duplicate IDs and bounded authentication retry.
- non-object JSON-RPC input is rejected before it can bypass request tracking.

## Review Decision

No blocking finding remains in the corrected diff. The final independent review
also found no remaining material finding. Residual risks are limited to the
absence of live Keycloak validation, lack of a stress test for concurrent
renewal callers, and the existing newline-delimited STDIO framing assumption.

READY_FOR_HUMAN_REVIEW
