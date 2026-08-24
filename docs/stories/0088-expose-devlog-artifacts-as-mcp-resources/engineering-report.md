# Engineering Report — Story 0088

## Architecture Decisions Verified

### 1. Facade, not a second business layer
Every resource proxies an existing backend application capability through the
established HTTP adapter pattern (same as tools). The mcp-server holds zero
business rules: it resolves slugs to ids, validates identifiers, enforces
project ownership using data already present in the responses, and maps errors.
No repository access, no persistence knowledge, no ranking logic.

### 2. Slug-based URI space
`devlog://projects/{projectSlug}/…` chosen over `{projectId}`:
- consistency with the pre-existing `devlog://projects/{projectSlug}/context`
  resource (no dual addressing);
- DevLog's documented principle: slug = stable integration identifier, UUID =
  implementation detail;
- artifact segments use the exact UUIDs/SHAs already exposed by
  `get_engineering_context` evidence, making the navigation path mechanical.

### 3. Governance preserved for trusted knowledge
Insights are read exclusively through the project-scoped ACTIVE-only service
query (`InsightServiceImpl.getByProject`), so SUPERSEDED/ARCHIVED insights are
indistinguishable from unknown ones and can never be served as trusted
knowledge — the resource cannot weaken ADR-006 authority boundaries.

### 4. Isolation without global ambiguous lookup
- Stories: server-side `(storyId, projectId)` check reused as-is.
- Insights: membership by construction (project-scoped list).
- Commits: source-scoped commit lookup; source selected among the project's
  active sources with the engine's own ordering rule (createdAt asc, id asc).
- Decisions/Events: payload `projectId` asserted equal to the resolved project;
  mismatch surfaces as not-found rather than foreign content.

### 5. Deterministic error semantics at the boundary
All expected absences and validation failures surface as MCP errors carrying
explicit, deterministic messages ("Project 'x' not found", "Invalid commit
SHA", "… does not belong to project '…'"). Backend 5xx never leaks raw stack
behavior. SDK limitation noted: JSON-RPC codes are normalized by the
annotation layer (-32602) while messages are preserved.

### 6. Contract reuse over new taxonomies
Payloads pass through existing backend response representations
(`DecisionResponse`, `InsightResponse`, `EngineeringStoryResponse`,
`EngineeringEventResponse`, `CommitDiffAnalysisContext`). Only one field is
extracted (`projectId`) for ownership checks. No new DTO module was created.

## Acceptance Criteria Review

1. ✅ resources/list + resources/templates/list expose the new surface
   (verified via stdio session).
2. ✅ discover → select → read flow works end-to-end on devlog-ai.
3. ✅ clean errors for unknown/invalid/cross-project identifiers.
4. ✅ non-ACTIVE insights unreachable (ACTIVE-only query rule tested).
5. ✅ pipelines green (backend 856 tests + coverage; mcp-server 28 tests).
6. ✅ real evidence identifier (decision ae47a47d…) navigated to its full
   artifact through the resource.

## Out-of-scope observations

- Spring AI annotation layer normalizes JSON-RPC error codes (see remaining
  issues in implementation report).
- `resources/list` does not embed templates on this SDK version; clients must
  also call `resources/templates/list` (spec-era behavior of java-sdk 2.0.0).
