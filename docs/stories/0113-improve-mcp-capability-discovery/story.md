# Story 0113 — Improve MCP Capability Discovery

## Status

**IMPLEMENTED — AWAITING HUMAN REVIEW**

## Baseline

- Baseline SHA: `a36d7eb`
- Baseline branch: `main`
- Working branch: `story/0113-improve-mcp-capability-discovery`

## Problem

The `devlog://server/info` resource exposes only static server metadata. An MCP
consumer must inspect or probe separate protocol operations before it can select
the appropriate DevLog tool, prompt, or resource. The normal MCP surface also
contains `echo_message` and `explain_code`, demonstration capabilities with no
DevLog domain responsibility.

## Goal

Make `devlog://server/info` a concise orientation entry point over the
operational MCP surface so an agent can identify the correct capability without
blind probing, while removing obsolete demonstration capabilities.

## Current Surface

Before this Story, the registered surface is:

- tools: `echo_message`, `get_engineering_context`,
  `search_project_history`, `analyze_story_context`;
- prompts: `explain_code`;
- resources: `server-info`, `projects`, `project-context`, `project-story`,
  `project-decision`, `project-insight`, `project-engineering-event`,
  `project-commit-context`, `project-freshness`.

Repository usage confirms that `echo_message` and `explain_code` have only
isolated demonstration tests and no production DevLog consumer or backend
integration. The remaining capabilities are operational DevLog capabilities.

## Scope

### In Scope

- retain the existing `devlog://server/info` resource and required server
  metadata;
- add concise tool, prompt, and resource discovery information;
- derive discovery entries from the initialized Spring AI MCP server's runtime
  capability inventory;
- include names, concise registered descriptions, and resource URIs/templates;
- remove `echo_message`, `explain_code`, and tests/documentation dedicated only
  to those demonstrations;
- add registration-level drift tests for discovery and the intended operational
  surface.

### Explicit Non-Goals

- MCP architecture, authorization, protocol, or capability-registry redesign;
- detailed tool schemas or duplicated MCP protocol metadata;
- Story Context or EngineeringContext semantic changes;
- trusted knowledge, ingestion, repository, or local-worktree changes;
- new retrieval, RAG, agent runtime, prompt, or debug capability;
- changes to historical Story or investigation records that accurately describe
  their original repository revision.

## Design

`serverInfoResource` consumes the registered synchronous tool, prompt, static
resource, and resource-template inventories exposed by the initialized Spring AI
MCP server. It projects only stable orientation fields and sorts each collection
deterministically.

```text
annotated DevLog capabilities
        -> Spring AI registered specifications
        -> MCP protocol surface
        -> serverInfoResource discovery projection
```

This avoids a second manually maintained capability list while keeping detailed
input schemas and protocol metadata authoritative in normal MCP list operations.
Server name and version come from the existing MCP server properties rather than
duplicated literals. An empty prompt collection explicitly communicates that no
operational DevLog prompt is currently registered.

No architectural decision is introduced:

`NEW_ADR_REQUIRED = NO`

## Acceptance Criteria

### AC1 — Tools Are Discoverable

`serverInfoResource` reports every registered operational tool with its name and
concise registered purpose, including `analyze_story_context`.

### AC2 — Prompts Are Discoverable

`serverInfoResource` contains a prompts collection corresponding to the
registered operational prompts, including an empty collection when none exist.

### AC3 — Resources Are Discoverable

`serverInfoResource` reports every registered static resource and resource
template with its name, URI or URI template, and concise registered purpose.

### AC4 — Registration Is Authoritative

Tests compare the discovery projection with the actual MCP list responses so a
capability registration change cannot silently leave server information stale.

### AC5 — Demonstration Capabilities Are Removed

`echo_message` and `explain_code` are absent from the registered MCP surface and
their implementation-only tests are removed.

### AC6 — Operational Capabilities Are Preserved

`get_engineering_context`, `search_project_history`, `analyze_story_context`,
and all nine existing DevLog resources remain registered. Story Context behavior
is unchanged.

### AC7 — Existing Server Metadata Is Preserved

The server information response retains configured `name`, `version`, and
existing `status` metadata.

### AC8 — Scope And Quality Gates Hold

Targeted MCP tests, the full `mcp-server` verification suite, and
`git diff --check` pass without unrelated architectural or domain changes.

## Lifecycle State

- Story materialization: completed by this task
- Repository analysis: completed
- Human design review: approved by task request
- Human implementation authorization: granted by task request
- Implementation: completed
- Verification: completed (`2` targeted tests, `49` full MCP tests)
- Human acceptance: pending
- Commit: authorized only after all implementation and validation gates pass
- Push: not authorized
- Merge: human-only

Terminal implementation state:

`STORY_0113_IMPLEMENTED_AWAITING_HUMAN_REVIEW`
