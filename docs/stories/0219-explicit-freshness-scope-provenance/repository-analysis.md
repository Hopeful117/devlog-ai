# Story 0219 - Repository Analysis

## Status

REPOSITORY ANALYSIS COMPLETE - AWAITING HUMAN APPROVAL

This artifact is the result of the Repository Analysis stage. It does not
authorize implementation or establish human approval.

## Baseline and Repository State

- Epic baseline: `agent-response-reliability` at design materialized status.
- Authoritative analysis baseline: `origin/main` at `6fb3a962`.
- Current worktree checkout: `story/0210-agent-response-acceptability-contract`.
- Current checkout is stale relative to the merged main line and its remote
  branch is deleted.
- Unrelated pre-existing worktree modifications must remain untouched:
  - `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/client/DevlogResourceClient.java`
  - `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/tool/SearchProjectHistoryTool.java`
  - `mcp-server/src/test/java/hopefull117/devlogai_mcp/mcp_server/tool/SearchProjectHistoryToolTest.java`

## DevLog Preflight

- Tool: `devlog_devlog_story_agent`.
- Project: `devlog-ai`.
- Task: `f04ead38-bd15-4660-8a63-f86c615ff463`.
- Snapshot: `f04ead38-bd15-4660-8a63-f86c615ff463`.
- Status: `COMPLETED`.
- Freshness: `UNKNOWN`.
- Accounting: 60 selected from 774 candidates, 5,407 of 6,000 tokens used,
  `truncated=true`.
- Contribution: confirmed that Stories 0114 and 0118 are complementary concerns
  and that ADR-067 preserves deterministic Core context authority.
- Limitation: the retained context did not establish the complete integration
  contract, so the repository was used as the authority for current physical
  state.

## Repository Evidence

### Epic ordering

The epic explicitly orders the work as:

1. contract and baseline;
2. explicit freshness and scope;
3. loss-aware composition;
4. specificity, abstention and calibration;
5. qualitative evaluation and real Story pilot.

Story 0210 covers the first step. Story 0219 covers the second step. Story
0219 must not absorb the later loss-ledger or evaluation work.

### Story 0114

The planned and implemented topology is:

```text
EngineeringContext.metadata.freshness
    -> AiTask.contextSnapshot["contextFreshness"]
    -> StoryContextAnalysis.contextFreshness
    -> StoryContextAnalysisResponse
    -> REST and MCP consumers
```

The implementation is present in repository history, but the analysis recorded
the following acceptance risks:

1. `Map.copyOf(contextFreshness)` can reject legitimate null revision values
   for `NO_BASELINE` and `UNKNOWN`.
2. Aggregate warnings are not serialized into the captured snapshot.
3. Direct tests for use-case capture and callback persistence are missing.
4. REST response compatibility has no direct controller or external-consumer
   verification.

These are in scope for reconciliation, not reasons to redesign freshness.

### Story 0118

The current Story 0118 design defines an immutable `RepositoryRevisionScope`
resolved once per SCA execution from project, source and revision. It defines
revision-aware document identity:

```text
document:{sourceId}:{normalizedPath}@{revision}
```

The implementation commit `db50dfbe` is contained in `main`. Its lifecycle
documents still say `IMPLEMENTED - COMMITTED ON BRANCH, AWAITING HUMAN REVIEW`,
which is stale relative to the repository graph. This discrepancy must be
recorded, not silently rewritten as part of Story 0219.

### Architectural constraints

- ADR-063 separates retrieval, composition, projection, grounding and
  consumer-specific behavior. The new Story must preserve those boundaries.
- ADR-067 keeps Java Core as the sole authority for EngineeringContext and
  grounding authorization. Python must not reconstruct freshness, scope or
  provenance.
- ADR-079 distinguishes selection truncation, prompt/provider budgets and
  provider failures from freshness state. Story 0219 must not merge those
  diagnostics.

## Proposed Contract Boundary

The minimum deterministic contract should carry, as separate concepts:

- execution project identity;
- repository source identity;
- resolved revision and resolution source;
- aggregate freshness status;
- per-source observed and context revisions;
- bounded aggregate and source warnings;
- evidence reference, source type, path and revision provenance;
- explicit unknown/unavailable state;
- verification guidance derived from those states.

The final transport shape remains an Implementation Plan decision. This
analysis does not authorize adding fields to production contracts.

## Scope Decision

### Included

- Reconcile 0114 freshness capture, persistence, null handling, warnings and
  tests.
- Verify 0118 revision scope and document provenance on the current execution
  path.
- Define additive REST/MCP projection behavior and grounding-preserving tests.
- Define explicit unknown and verification semantics.

### Excluded

- Retrieval or ranking redesign.
- Budget changes or automatic budget remediation.
- Loss ledger implementation from the next epic Story.
- AI prompt or model behavior changes.
- Frontend implementation without a later contract decision.
- Changes to the unrelated dirty MCP files listed above.

## Risks

1. **Contract duplication:** 0114 freshness and 0118 document provenance may be
   exposed through separate envelopes unless the Implementation Plan defines a
   single additive projection boundary.
2. **Mixed revisions:** freshness metadata and document evidence must not report
   a single current state when their resolved revisions differ.
3. **Unknown-state loss:** nullable fields must remain representable without
   causing serialization failures or being replaced by guessed values.
4. **Stale lifecycle documentation:** Story status files may not reflect the
   commit graph; repository history is authoritative for physical integration.
5. **Compatibility regression:** replacing an existing response shape would
   exceed the Story scope unless explicitly approved.

## Test Strategy for the Next Stage

- Unit tests for nullable freshness snapshots and warning preservation.
- Use-case tests proving one resolved scope is carried through execution.
- Persistence tests for freshness and provenance metadata.
- REST/MCP tests for additive serialization and unknown states.
- Revision-aware evidence reference tests using source, path and SHA.
- Grounding subset regression tests proving metadata does not authorize new
  evidence.
- Focused backend suite followed by the repository-required broader checks.

## Documentation Reconciliation

No existing historical Story or ADR should be rewritten during this stage.
Story 0219 records the stale lifecycle wording in 0114/0118 as a repository
finding. Any correction to those historical statuses requires a separate
documentation decision or explicit scope addition.

## Recommendation

Approve this Repository Analysis as the basis for an Implementation Plan that
defines the additive contract and test sequence. Do not begin implementation,
change budgets, or alter retrieval until that plan receives its separate human
approval.

## Human Approval Gate

Repository Analysis is complete and awaits explicit human approval. Approval
must apply to this artifact version only; it does not approve the future
Implementation Plan or implementation.
