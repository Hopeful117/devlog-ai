import assert from "node:assert/strict";
import test from "node:test";

import {
  contextSelectionForScope,
  contextInspectorLimits,
  deriveLocalContextInspection,
  deriveSelectableContextOperations,
  isContextHistoryRowSelectable,
  localContextProjectionKey,
} from "../src/context-inspector.js";

const REVISION_A = "a".repeat(40);
const REVISION_B = "b".repeat(64);
const SCOPE = '["server-a","session-a","/workspace"]';

function text(value) {
  return { type: "text", text: JSON.stringify(value) };
}

function evidence(trustTier, sensitive = {}) {
  return {
    kind: "ENGINEERING_STORY",
    trustTier,
    summary: sensitive.summary ?? "private evidence summary",
    identifier: "private-evidence-id",
    content: { text: "private evidence body" },
    ...sensitive,
  };
}

function dto(overrides = {}) {
  const trusted = evidence("TRUSTED");
  const technical = evidence("TECHNICAL_EVIDENCE");
  return {
    project: {
      id: "private-project-id",
      name: "private project name",
      slug: "private-project",
      description: "private project description",
    },
    intent: "private prompt and request text",
    evidence: [trusted, technical],
    metadata: {
      candidateCount: 5,
      selectedCount: 2,
      rejectedCount: 3,
      truncated: false,
      usedTokens: 900,
      contextDigest: "private-context-digest",
      warnings: [],
      freshness: {
        status: "STALE",
        repositoryRevision: REVISION_A,
        contextRevision: REVISION_B,
        sources: [],
      },
    },
    sections: [
      { name: "trusted_knowledge", trustTier: "TRUSTED", evidence: [trusted], rationale: "private rationale" },
      { name: "human_context", trustTier: "HUMAN_AUTHORED", evidence: [], rationale: "private rationale" },
      { name: "technical_evidence", trustTier: "TECHNICAL_EVIDENCE", evidence: [technical], rationale: "private rationale" },
      { name: "system_metadata", trustTier: "SYSTEM_METADATA", evidence: [], rationale: "private rationale" },
    ],
    requestEcho: {
      projectSlug: "private-project",
      intent: "private request echo",
      files: ["private/file/path"],
      storyId: "private-story-id",
    },
    ...overrides,
  };
}

function part(value, overrides = {}) {
  return {
    type: "tool",
    id: "native-call-id",
    name: "mcp__devlog__get_engineering_context",
    state: {
      status: "completed",
      content: [text(value)],
    },
    ...overrides,
  };
}

function transcript(...parts) {
  return [{ type: "assistant", id: "assistant-message", content: parts }];
}

test("projects the supported direct EngineeringContext result only", () => {
  const result = deriveLocalContextInspection(
    transcript(part(dto())),
    "native-call-id",
    SCOPE,
  );

  assert.equal(result.state, "AVAILABLE");
  assert.equal(result.provenance, "OBSERVED_TOOL_RESULT");
  assert.equal(result.toolName, "get_engineering_context");
  assert.equal(result.hostCallId, "native-call-id");
  assert.equal(result.scope, SCOPE);
  assert.equal(result.freshness, "STALE");
  assert.equal(result.repositoryRevision, REVISION_A);
  assert.equal(result.contextRevision, REVISION_B);
  assert.equal(result.truncated, false);
  assert.equal(result.countConsistency, "CONSISTENT");
  assert.deepEqual(result.evidenceCountsByTrustTier, {
    TRUSTED: 1,
    HUMAN_AUTHORED: 0,
    TECHNICAL_EVIDENCE: 1,
    SYSTEM_METADATA: 0,
    UNKNOWN: 0,
  });
  assert.deepEqual(result.sectionCountsByTrustTier, {
    TRUSTED: 1,
    HUMAN_AUTHORED: 0,
    TECHNICAL_EVIDENCE: 1,
    SYSTEM_METADATA: 0,
  });
  assert.equal(result.empty, false);
});

test("does not retain prompt, project, evidence, request echo, or arbitrary payloads", () => {
  const result = deriveLocalContextInspection(
    transcript(part(dto())),
    "native-call-id",
    SCOPE,
  );
  const serialized = JSON.stringify(result);

  for (const sensitive of [
    "private prompt",
    "private project",
    "private-evidence",
    "private evidence",
    "private rationale",
    "private/file/path",
    "private-context-digest",
  ]) {
    assert.equal(serialized.includes(sensitive), false, sensitive);
  }
});

test("preserves freshness states and omits confidence", () => {
  for (const freshness of [
    "CURRENT",
    "STALE",
    "NO_BASELINE",
    "UNKNOWN",
    "PARTIALLY_FRESH",
  ]) {
    const result = deriveLocalContextInspection(
      transcript(part(dto({
        metadata: {
          ...dto().metadata,
          freshness: { status: freshness },
        },
      }))),
      "native-call-id",
      SCOPE,
    );
    assert.equal(result.freshness, freshness);
    assert.equal("confidence" in result, false);
  }
});

test("marks unknown freshness, missing revisions, and count conflicts partial", () => {
  const result = deriveLocalContextInspection(
    transcript(part(dto({
      metadata: {
        ...dto().metadata,
        selectedCount: 8,
        freshness: { status: "NEW_STATUS" },
      },
    }))),
    "native-call-id",
    SCOPE,
  );
  assert.equal(result.state, "PARTIAL");
  assert.equal(result.freshness, "UNKNOWN");
  assert.equal(result.repositoryRevision, undefined);
  assert.equal(result.contextRevision, undefined);
  assert.equal(result.countConsistency, "MISMATCH");
});

test("handles stale and truncation warnings without exposing arbitrary messages", () => {
  const result = deriveLocalContextInspection(
    transcript(part(dto({
      metadata: {
        ...dto().metadata,
        truncated: false,
        warnings: [
          "PROJECT_CONTEXT_STALE",
          "RESPONSE_SIZE_TRUNCATED",
          "private warning text",
        ],
      },
    }))),
    "native-call-id",
    SCOPE,
  );
  assert.equal(result.state, "PARTIAL");
  assert.equal(result.truncated, true);
  assert.deepEqual(result.diagnostics, [
    "PROJECT_CONTEXT_STALE",
    "RESPONSE_SIZE_TRUNCATED",
    "OTHER_WARNING_PRESENT",
  ]);
  assert.equal(JSON.stringify(result).includes("private warning text"), false);
});

test("requires direct completed results and the exact native text envelope", () => {
  const valid = dto();
  const cases = [
    [part(valid, { name: "mcp__devlog__devlog_story_agent" }), "UNVERIFIED"],
    [part(valid, { name: "execute" }), "UNVERIFIED"],
    [part(valid, { state: { status: "running", content: [text(valid)] } }), "UNAVAILABLE"],
    [part(valid, { state: { status: "completed", content: [text(valid), text(valid)] } }), "UNVERIFIED"],
    [part(valid, { state: { status: "completed", content: [{ type: "text", text: JSON.stringify(valid), isError: true }] } }), "UNVERIFIED"],
    [part(valid, { state: { status: "completed", content: [{ type: "image", data: "ignored" }] } }), "UNVERIFIED"],
    [part(valid, { state: { status: "completed", content: [{ type: "text", text: "assistant says " + JSON.stringify(valid) }] } }), "UNVERIFIED"],
  ];

  for (const [toolPart, expectedState] of cases) {
    const result = deriveLocalContextInspection(
      transcript(toolPart),
      "native-call-id",
      SCOPE,
    );
    assert.equal(result.state, expectedState);
  }
});

test("does not infer eligibility from arbitrary JSON or unsupported DTO shapes", () => {
  for (const payload of [
    { aiTaskId: "unassociated-task", context: dto() },
    { project: {}, intent: "x", evidence: [], metadata: {}, sections: [] },
    { ...dto(), evidence: "not-an-array" },
    { ...dto(), requestEcho: null },
  ]) {
    const result = deriveLocalContextInspection(
      transcript(part(payload)),
      "native-call-id",
      SCOPE,
    );
    assert.equal(result.state, "UNVERIFIED");
  }
});

test("rejects malformed metadata and section contracts", () => {
  const malformedMetadata = dto();
  malformedMetadata.metadata.selectedCount = -1;
  const malformedSections = dto();
  malformedSections.sections[0].evidence = null;
  for (const payload of [malformedMetadata, malformedSections]) {
    assert.equal(
      deriveLocalContextInspection(
        transcript(part(payload)),
        "native-call-id",
        SCOPE,
      ).state,
      "UNVERIFIED",
    );
  }
});

test("marks unsupported trust tiers and flattened/section conflicts partial", () => {
  const unknownTier = dto();
  unknownTier.evidence[0].trustTier = "FUTURE_TIER";
  const unknownResult = deriveLocalContextInspection(
    transcript(part(unknownTier)),
    "native-call-id",
    SCOPE,
  );
  assert.equal(unknownResult.state, "PARTIAL");
  assert.equal(unknownResult.evidenceCountsByTrustTier.UNKNOWN, 1);

  const conflict = dto();
  conflict.sections[0].evidence[0].trustTier = "HUMAN_AUTHORED";
  const conflictResult = deriveLocalContextInspection(
    transcript(part(conflict)),
    "native-call-id",
    SCOPE,
  );
  assert.equal(conflictResult.state, "PARTIAL");
  assert.equal(conflictResult.countConsistency, "MISMATCH");
});

test("keeps verified empty, truncated, and unavailable results distinct", () => {
  const emptyDto = dto({
    evidence: [],
    metadata: {
      candidateCount: 0,
      selectedCount: 0,
      rejectedCount: 0,
      truncated: false,
      warnings: [],
      freshness: { status: "NO_BASELINE" },
    },
    sections: [
      { trustTier: "TRUSTED", evidence: [] },
      { trustTier: "HUMAN_AUTHORED", evidence: [] },
      { trustTier: "TECHNICAL_EVIDENCE", evidence: [] },
      { trustTier: "SYSTEM_METADATA", evidence: [] },
    ],
  });
  const empty = deriveLocalContextInspection(
    transcript(part(emptyDto)),
    "native-call-id",
    SCOPE,
  );
  assert.equal(empty.state, "AVAILABLE");
  assert.equal(empty.empty, true);

  const truncatedDto = dto();
  truncatedDto.metadata.truncated = true;
  const truncated = deriveLocalContextInspection(
    transcript(part(truncatedDto)),
    "native-call-id",
    SCOPE,
  );
  assert.equal(truncated.state, "PARTIAL");
  assert.equal(truncated.truncated, true);
  assert.equal(truncated.empty, false);

  const unavailable = deriveLocalContextInspection(undefined, "native-call-id", SCOPE);
  assert.equal(unavailable.state, "UNAVAILABLE");
  assert.equal(unavailable.reason, "TRANSCRIPT_UNAVAILABLE");
});

test("enforces JSON, call-ID, warning and evidence projection bounds", () => {
  assert.equal(contextInspectorLimits.maxResultChars, 25_000);
  assert.equal(contextInspectorLimits.maxEvidenceCounted, 1_000);

  const oversize = part(dto());
  oversize.state.content[0].text = " ".repeat(contextInspectorLimits.maxResultChars + 1);
  assert.equal(
    deriveLocalContextInspection(
      transcript(oversize),
      "native-call-id",
      SCOPE,
    ).reason,
    "RESULT_SIZE_UNSUPPORTED",
  );

  const oversizedEvidence = dto();
  oversizedEvidence.evidence = Array.from(
    { length: contextInspectorLimits.maxEvidenceCounted + 1 },
    () => ({ trustTier: "TRUSTED" }),
  );
  oversizedEvidence.metadata.selectedCount = oversizedEvidence.evidence.length;
  oversizedEvidence.metadata.candidateCount = oversizedEvidence.evidence.length;
  oversizedEvidence.sections = [
    { trustTier: "TRUSTED", evidence: [] },
    { trustTier: "HUMAN_AUTHORED", evidence: [] },
    { trustTier: "TECHNICAL_EVIDENCE", evidence: [] },
    { trustTier: "SYSTEM_METADATA", evidence: [] },
  ];
  const bounded = deriveLocalContextInspection(
    transcript(part(oversizedEvidence)),
    "native-call-id",
    SCOPE,
  );
  assert.equal(bounded.state, "PARTIAL");
  assert.equal(bounded.evidenceCountsByTrustTier.TRUSTED, 1_000);
  assert.equal(bounded.countConsistency, "UNKNOWN");
});

test("uses the exact selected native result and refuses duplicate IDs", () => {
  const first = dto();
  const second = dto({
    metadata: {
      ...dto().metadata,
      freshness: { status: "CURRENT" },
    },
  });
  const firstResult = deriveLocalContextInspection(
    transcript(part(first), part(second, { id: "other-call-id" })),
    "native-call-id",
    SCOPE,
  );
  assert.equal(firstResult.freshness, "STALE");

  const updatedResult = deriveLocalContextInspection(
    transcript(part(second), part(first, { id: "other-call-id" })),
    "native-call-id",
    SCOPE,
  );
  assert.equal(updatedResult.freshness, "CURRENT");
  assert.equal(updatedResult.scope, SCOPE);

  const duplicate = deriveLocalContextInspection(
    transcript(part(first), part(second)),
    "native-call-id",
    SCOPE,
  );
  assert.equal(duplicate.state, "UNVERIFIED");
  assert.equal(duplicate.reason, "DUPLICATE_NATIVE_CALL_ID");
});

test("selection is scoped to the routed server/location/session identity", () => {
  const selection = {
    scope: SCOPE,
    hostCallId: "native-call-id",
  };
  assert.deepEqual(contextSelectionForScope(selection, SCOPE), selection);
  assert.equal(contextSelectionForScope(selection, '["server-b","session-a"]'), undefined);
  assert.equal(contextSelectionForScope(selection, undefined), undefined);
  assert.equal(contextSelectionForScope(undefined, SCOPE), undefined);
});

test("only direct get_engineering_context History rows are selectable", () => {
  const messages = transcript(part(dto()));
  assert.equal(isContextHistoryRowSelectable({
    source: "direct-tool",
    toolName: "get_engineering_context",
    hostCallId: "native-call-id",
  }, messages), true);
  assert.equal(isContextHistoryRowSelectable({
    source: "code-mode",
    toolNames: ["get_engineering_context"],
    hostCallId: "outer-execute-id",
  }, messages), false);
  assert.equal(isContextHistoryRowSelectable({
    source: "direct-tool",
    toolName: "get_engineering_context",
  }, messages), false);
  assert.equal(isContextHistoryRowSelectable({
    source: "direct-tool",
    toolName: "search_project_history",
    hostCallId: "native-direct-id",
  }, messages), false);

  const duplicateMessages = transcript(part(dto()), part(dto(), { id: "native-call-id" }));
  assert.equal(isContextHistoryRowSelectable({
    source: "direct-tool",
    toolName: "get_engineering_context",
    hostCallId: "native-call-id",
  }, duplicateMessages), false);

  const malformedMessages = transcript(part({ aiTaskId: "not-a-context" }));
  assert.equal(isContextHistoryRowSelectable({
    source: "direct-tool",
    toolName: "get_engineering_context",
    hostCallId: "native-call-id",
  }, malformedMessages), false);
});

test("Context picker lists only eligible direct results as minimal choices", () => {
  const messages = transcript(
    part(dto()),
    part(dto({ metadata: { ...dto().metadata, freshness: { status: "CURRENT" } } }), {
      id: "second-call",
    }),
    part(dto(), { id: "duplicate-call" }),
    part(dto(), { id: "duplicate-call" }),
  );
  const rows = [
    {
      source: "direct-tool",
      toolName: "get_engineering_context",
      hostCallId: "native-call-id",
      observedAt: 1_700_000_000_000,
      durationMs: 10,
      taskOutcome: "COMPLETED",
    },
    {
      source: "direct-tool",
      toolName: "get_engineering_context",
      hostCallId: "second-call",
    },
    {
      source: "direct-tool",
      toolName: "get_engineering_context",
      hostCallId: "duplicate-call",
    },
    {
      source: "code-mode",
      toolNames: ["get_engineering_context"],
      hostCallId: "outer-execute-id",
    },
    {
      source: "direct-tool",
      toolName: "search_project_history",
      hostCallId: "other-tool",
    },
  ];

  assert.deepEqual(deriveSelectableContextOperations(rows, messages), [
    {
      hostCallId: "native-call-id",
      toolName: "get_engineering_context",
      observedAt: 1_700_000_000_000,
    },
    {
      hostCallId: "second-call",
      toolName: "get_engineering_context",
    },
  ]);
  assert.deepEqual(deriveSelectableContextOperations(rows, undefined), []);
});

test("Code Mode outer result cannot stand in for its nested EngineeringContext", () => {
  const outer = part(dto(), {
    id: "outer-execute-id",
    name: "execute",
    state: {
      status: "completed",
      metadata: {
        toolCalls: [{ name: "devlog.get_engineering_context" }],
      },
      content: [text(dto())],
    },
  });
  const result = deriveLocalContextInspection(
    transcript(outer),
    "outer-execute-id",
    SCOPE,
  );
  assert.equal(result.state, "UNVERIFIED");
  assert.equal(result.reason, "CODE_MODE_UNSUPPORTED");
});

test("sanitized projection changes invalidate a retained selection", () => {
  const oldProjection = deriveLocalContextInspection(
    transcript(part(dto())),
    "native-call-id",
    SCOPE,
  );
  const changedProjection = deriveLocalContextInspection(
    transcript(part(dto({
      metadata: {
        ...dto().metadata,
        freshness: { status: "CURRENT" },
      },
    }))),
    "native-call-id",
    SCOPE,
  );
  assert.notEqual(
    localContextProjectionKey(oldProjection),
    localContextProjectionKey(changedProjection),
  );
});

test("does not join by task ID, tool name, or a call outside the transcript window", () => {
  const selected = deriveLocalContextInspection(
    transcript(part(dto(), { id: "different-call-id" })),
    "native-call-id",
    SCOPE,
  );
  assert.equal(selected.state, "UNAVAILABLE");
  assert.equal(selected.reason, "SOURCE_NO_LONGER_AVAILABLE");

  const messages = Array.from({ length: 41 }, (_, index) =>
    index === 0
      ? { type: "assistant", content: [part(dto())] }
      : { type: "assistant", content: [] },
  );
  assert.equal(
    deriveLocalContextInspection(messages, "native-call-id", SCOPE).reason,
    "SOURCE_NO_LONGER_AVAILABLE",
  );
});
