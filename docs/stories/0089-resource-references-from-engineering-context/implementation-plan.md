# Story 0089 — Implementation Plan

- **Branch**: `story/0089-resource-references-from-engineering-context` (from
  `main` @ 4d16138 containing merged Stories 0087+0088).

## 1. devlog-contracts

1. `EngineeringEvidence`: append `String resource` (nullable) after `symbols`.
2. New `DevlogResourceUriFactory` (final class, static methods, no state):
   - `projects()`, `decision(slug, uuid)`, `insight(slug, uuid)`,
     `story(slug, uuid)`, `engineeringEvent(slug, uuid)`, `commit(slug, sha)`
   - output shape: `devlog://projects/{slug}/…/{lowercase id}`
   - rejects blank slug / null id with `IllegalArgumentException`; UUIDs
     normalized lowercase; SHA validated 40/64 hex.

## 2. backend

`EngineeringContextContractMapper`: resolve `projectSlug` from
`projectContext.project().slug()`; per evidence compute resource:

```text
switch kind:
  DECISION, INSIGHT, ENGINEERING_STORY, ENGINEERING_EVENT -> factory(kind, identifier as UUID)
  COMMIT -> parse internal reference ^git:{uuid}:{sha40|64}$ -> factory.commit
  default -> null
any failure -> null (never throw)
```

Engine untouched ⇒ digest semantics unchanged (documented; engine tests stay
green as guard).

## 3. Test updates

- contracts: `DevlogResourceUriFactoryTest` (each artifact type, casing,
  invalid inputs).
- backend: mapper test — addressable kinds carry exact URIs with the snapshot
  slug; CHANGED_FILE/CHALLENGE/MILESTONE/ARTIFACT/ANALYSIS/structure kinds →
  null; malformed DECISION identifier → null (no exception); commit reference
  parsing incl. invalid → null. Controller WebMvc fixture + JSON assertions.
- mcp-server: tool unit-test fixture + new sync test asserting each
  `@McpResource` template equals the factory-built pattern (drift guard);
  existing tests updated for the appended constructor argument.

## 4. Validation

Full pipeline (`./backend/mvnw -pl backend -am clean verify -B`, mcp-server
suite), then live stdio session:

```text
initialize → get_engineering_context(devlog-ai, …)
→ pick evidence with resource != null
→ resources/read(that exact URI)
→ assert full artifact (e.g. decision title/rationale)
```

The URI must be used verbatim (acceptance criterion). Repeat for a second
category (commit or insight).

## 5. Closure

implementation-report.md + engineering-report.md, granular commits, branch
left unmerged.
