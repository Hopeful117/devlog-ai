# Implementation Plan - Story 0215

## 1. Java MCP diagnostic contract

- Add an allowlisted diagnostic data map in `CoreCallPolicy`.
- Preserve existing MCP codes and non-divulgating messages.
- Add stable `code`, `layer`, `retryable` and `action` fields to categorized Core
  errors.
- Cover Core authentication, authorization, conflict, rate limit and transient
  infrastructure mappings.

## 2. Python runtime projection

- Extend JSON-RPC category-to-reason mapping for all known Core, delegation,
  provider and infrastructure categories.
- Add a small allowlisted diagnostic mapping for runtime events.
- Keep transport authentication retry behavior unchanged.
- Preserve bounded attempt, generation and operation metadata.

## 3. Status and doctor

- Project diagnostic category, code and recommended action from event records.
- Add deterministic checks for configuration, token endpoint, backend reachability,
  orphaned parent and missing child.
- Keep secret redaction and existing JSON compatibility.

## 4. Tests and documentation

- Extend Java mapping and non-disclosure tests.
- Extend Python runtime and status tests for all categories and operator states.
- Run affected frontend tests only if the existing Activity contract changes;
  otherwise preserve the existing frontend model unchanged.
- Document the diagnostic fields and recovery actions in `TOOLS.md`.

## Validation

```bash
python3 -m unittest discover -s tools/devlog_mcp/tests -v
./backend/mvnw -pl mcp-server test -B
./backend/mvnw -pl backend -am test -Dtest='*Mcp*' -Dsurefire.failIfNoSpecifiedTests=false -B
git diff --check
```

Live credential validation is excluded.
