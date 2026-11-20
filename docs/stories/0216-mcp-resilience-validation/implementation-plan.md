# Implementation Plan - Story 0216

## 1. Freeze the validation contract

- Reconcile ADR-081 with Stories 0172, 0212, 0213, 0214 and 0215.
- Turn each required scenario into a row with layer, category, code, retry
  decision, idempotency condition, terminal outcome and evidence.
- Mark any contradiction as a human decision instead of inferring a policy.

## 2. Build the provider-free matrix

- Reuse existing Python test seams for token, child process, generation,
  operation and lifecycle failures.
- Reuse existing Java mocks for Core HTTP responses and MCP tool boundaries.
- Add only test fixtures or test helpers required to express missing scenarios.
- Keep external endpoints, credentials and real provider calls excluded.

## 3. Validate runtime invariants

- Assert one bounded transport-auth renewal and retry where authorized.
- Assert no transport renewal for Core, delegation or provider failures.
- Assert fail-closed behavior for authorization, protocol, configuration and
  ambiguous mutation outcomes.
- Assert stable operation identity during an authorized retry.
- Assert stale generations and concurrent terminal transitions are ignored.
- Assert shutdown prevents replacement child creation and new reservations.

## 4. Validate idempotency and diagnostics

- Assert replay only for operations with the required explicit guarantee.
- Assert no replay for mutations with an ambiguous result.
- Assert structured diagnostic fields are deterministic and allowlisted.
- Assert secrets, payloads, provider responses and backend messages are absent
  from events, status and test evidence.

## 5. Produce the validation report

- Record command, fixture, expected decision and observed result per scenario.
- Classify findings as confirmed regression, residual risk or human decision.
- Create no production fix in this Story unless separately authorized.
- Link any required corrective work to a separate Story or approved scope.

## Validation commands

```bash
python3 -m unittest discover -s tools/devlog_mcp/tests -v
./backend/mvnw -pl mcp-server test -B
git diff --check
```

Live credentials, real Keycloak, real Core and real provider validation remain
excluded.
