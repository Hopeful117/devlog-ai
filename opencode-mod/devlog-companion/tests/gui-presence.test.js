import assert from "node:assert/strict";
import test from "node:test";

import {
  deriveGuiHistory,
  deriveGuiPresence,
  guiPresenceLimits,
  isGuiDevLogToolName,
  subscribeGuiSessionEvents,
} from "../src/gui-presence.js";

const text = (value) => ({ type: "text", text: JSON.stringify(value) });

function directPart(name, status, content = []) {
  return {
    type: "tool",
    id: "native-call-id",
    name,
    state: { status, content },
  };
}

function assistant(...content) {
  return { type: "assistant", id: "message-id", content };
}

test("GUI allowlist accepts direct DevLog aliases and rejects unrelated tools", () => {
  assert.equal(isGuiDevLogToolName("devlog_story_agent"), true);
  assert.equal(isGuiDevLogToolName("mcp__devlog__get_engineering_context"), true);
  assert.equal(isGuiDevLogToolName("unrelated_story_agent"), false);
  assert.equal(isGuiDevLogToolName("mcp__other__devlog_story_agent"), false);
  assert.equal(isGuiDevLogToolName(undefined), false);
});

test("direct tool state is distinct from DevLog task outcome", () => {
  assert.equal(
    deriveGuiPresence([assistant(directPart("devlog_story_agent", "running"))]),
    "WORKING",
  );
  assert.equal(
    deriveGuiPresence([
      assistant(directPart("devlog_story_agent", "completed", [text({ status: "COMPLETED" })])),
    ]),
    "UNKNOWN",
  );
  assert.equal(
    deriveGuiPresence([
      assistant(directPart("devlog_story_agent", "completed", [text({ aiTaskStatus: "SUCCEEDED" })])),
    ]),
    "READY",
  );
  assert.equal(
    deriveGuiPresence([
      assistant(directPart("devlog_story_agent", "completed", [text({ aiTaskStatus: "SUBMITTED" })])),
    ]),
    "WORKING",
  );
  assert.equal(
    deriveGuiPresence([
      assistant(directPart("mcp__devlog__devlog_story_agent", "completed", [
        text({ aiTaskStatus: "POLL_TIMEOUT" }),
      ])),
    ]),
    "UNKNOWN",
  );
  assert.equal(
    deriveGuiPresence([
      assistant(directPart("devlog_story_agent", "completed", [text({ aiTaskStatus: "FAILED" })])),
    ]),
    "ERROR",
  );
  assert.equal(
    deriveGuiPresence([assistant(directPart("devlog_story_agent", "error"))]),
    "ERROR",
  );
});

test("conflicting explicit task statuses remain unknown in Now and History", () => {
  const conflictingContent = [
    text({ aiTaskStatus: "FAILED" }),
    text({ taskStatus: "SUCCEEDED" }),
  ];
  assert.equal(
    deriveGuiPresence([
      assistant(directPart("devlog_story_agent", "completed", conflictingContent)),
    ]),
    "UNKNOWN",
  );
  const rows = deriveGuiHistory([
    assistant(directPart("devlog_story_agent", "completed", conflictingContent)),
  ]);
  assert.equal(rows[0].platformOutcome, "COMPLETED");
  assert.equal(rows[0].taskOutcome, undefined);
});

test("same explicit task status repeated across fields is not treated as a conflict", () => {
  const rows = deriveGuiHistory([
    assistant(directPart("devlog_story_agent", "completed", [
      text({ aiTaskStatus: "SUCCEEDED", taskStatus: "SUCCEEDED" }),
      text({ aiTaskStatus: "SUCCEEDED" }),
    ])),
  ]);
  assert.equal(rows[0].taskOutcome, "SUCCEEDED");
});

test("generic status fields and host completion do not claim business success", () => {
  assert.equal(
    deriveGuiPresence([
      assistant(directPart("devlog_story_agent", "completed", [text({ status: "COMPLETED" })])),
    ]),
    "UNKNOWN",
  );
  assert.equal(
    deriveGuiPresence([
      assistant(directPart("execute", "completed", [text({ aiTaskStatus: "SUCCEEDED" })])),
    ]),
    "IDLE",
  );
});

test("Code Mode recognizes only explicit nested DevLog rows", () => {
  const execute = (outerStatus, toolCalls) => ({
    type: "tool",
    id: "outer-execute-id",
    name: "execute",
    state: { status: outerStatus, metadata: { toolCalls } },
  });

  assert.equal(
    deriveGuiPresence([
      assistant(execute("running", [{ tool: "devlog_story_agent", status: "running" }])),
    ]),
    "WORKING",
  );
  assert.equal(
    deriveGuiPresence([
      assistant(execute("completed", [{ tool: "devlog_story_agent", status: "completed" }])),
    ]),
    "UNKNOWN",
  );
  assert.equal(
    deriveGuiPresence([
      assistant(execute("completed", [{ tool: "devlog_story_agent", status: "error" }])),
    ]),
    "ERROR",
  );
  assert.equal(
    deriveGuiPresence([
      assistant(execute("running", [{ tool: "browser.tabs.list", status: "running" }])),
    ]),
    "IDLE",
  );
  assert.equal(
    deriveGuiPresence([
      assistant(execute("running", [{ tool: "devlog_story_agent" }])),
    ]),
    "UNKNOWN",
  );
});

test("Code Mode name-only results are recognized but remain outcome-unknown", () => {
  const executeResult = (result) => ({
    type: "tool",
    id: "outer-execute-id",
    name: "execute",
    state: { status: "completed", content: [text(result)] },
  });

  assert.equal(
    deriveGuiPresence([
      assistant(executeResult({
        ok: true,
        value: { aiTaskStatus: "SUCCEEDED" },
        toolCalls: [{ name: "devlog.get_engineering_context" }],
      })),
    ]),
    "UNKNOWN",
  );
  assert.equal(
    deriveGuiPresence([
      assistant(executeResult({
        ok: true,
        value: "unrelated",
        toolCalls: [{ name: "browser.tabs.list" }],
      })),
    ]),
    "IDLE",
  );
});

test("active recognized work wins across concurrent direct and Code Mode activity", () => {
  const runningCodeMode = {
    type: "tool",
    id: "execute-id",
    name: "execute",
    state: {
      status: "running",
      metadata: {
        toolCalls: [{ tool: "devlog_story_agent", status: "running" }],
      },
    },
  };
  const failedDirect = directPart("devlog_search_project_history", "error");

  assert.equal(
    deriveGuiPresence([assistant(failedDirect, runningCodeMode)]),
    "WORKING",
  );
});

test("a running part from an older assistant message is not treated as live", () => {
  const staleRunning = assistant(directPart("devlog_story_agent", "running"));
  const laterResult = assistant(
    directPart("devlog_story_agent", "completed", [text({ aiTaskStatus: "SUCCEEDED" })]),
  );

  assert.equal(deriveGuiPresence([staleRunning, laterResult]), "READY");
  assert.equal(
    deriveGuiPresence([staleRunning, assistant({ type: "text", text: "later turn" })]),
    "UNKNOWN",
  );
});

test("interruption and absent session evidence remain unknown", () => {
  const running = [assistant(directPart("devlog_story_agent", "running"))];
  assert.equal(deriveGuiPresence(running, { interrupted: true }), "UNKNOWN");
  assert.equal(deriveGuiPresence(undefined), "UNKNOWN");
  assert.equal(deriveGuiPresence([]), "IDLE");
  assert.deepEqual(guiPresenceLimits, {
    maxMessages: 40,
    maxPartsPerMessage: 80,
    maxCodeModeRows: 80,
    maxHistoryRows: 20,
    maxHistoryToolNamesPerRow: 8,
    maxNativeCallIdLength: 128,
    maxResultText: 8192,
    maxResultBlocks: 8,
  });
});

test("History derives sanitized direct rows with distinct host and task outcomes", () => {
  const part = {
    ...directPart("mcp__devlog__devlog_story_agent", "completed", [
      text({ aiTaskStatus: "SUBMITTED", prompt: "must not escape" }),
    ]),
    time: { ran: 1_000, completed: 1_250 },
  };
  part.id = "native-direct-call";
  part.state.input = { prompt: "private prompt", token: "private token" };

  const rows = deriveGuiHistory([assistant(part)]);
  assert.deepEqual(rows, [{
    source: "direct-tool",
    toolName: "devlog_story_agent",
    hostCallId: "native-direct-call",
    platformOutcome: "COMPLETED",
    taskOutcome: "SUBMITTED",
    observedAt: 1_250,
    durationMs: 250,
  }]);
  assert.doesNotMatch(JSON.stringify(rows), /private prompt|private token|must not escape/);
});

test("History never promotes host completion or generic status to task success", () => {
  const completed = directPart("devlog_story_agent", "completed", [
    text({ status: "COMPLETED", result: "private output" }),
  ]);
  const rows = deriveGuiHistory([assistant(completed)]);

  assert.equal(rows.length, 1);
  assert.equal(rows[0].platformOutcome, "COMPLETED");
  assert.equal(rows[0].taskOutcome, undefined);
  assert.doesNotMatch(JSON.stringify(rows), /private output/);
});

test("Code Mode history groups allowlisted names under the outer execution", () => {
  const execute = {
    type: "tool",
    id: "outer-execute-id",
    name: "execute",
    state: {
      status: "completed",
      content: [text({
        ok: true,
        toolCalls: [
          { name: "devlog.get_engineering_context" },
          { name: "devlog.get_engineering_context" },
          { name: "browser.tabs.list" },
          { name: "devlog.search_project_history" },
        ],
      })],
    },
    time: { ran: 2_000, completed: 2_500 },
  };
  const rows = deriveGuiHistory([assistant(execute)]);

  assert.deepEqual(rows, [{
    source: "code-mode",
    toolNames: ["get_engineering_context", "search_project_history"],
    hostCallId: "outer-execute-id",
    platformOutcome: "COMPLETED",
    taskOutcome: undefined,
    observedAt: 2_500,
    durationMs: 500,
  }]);
  assert.equal(rows[0].taskOutcome, undefined);
});

test("Code Mode metadata and result rows are reconciled without duplicate names", () => {
  const execute = {
    type: "tool",
    id: "outer-execute-id",
    name: "execute",
    state: {
      status: "completed",
      metadata: {
        toolCalls: [{ name: "devlog.get_engineering_context" }],
      },
      content: [text({
        toolCalls: [
          { name: "devlog.get_engineering_context" },
          { name: "devlog.search_project_history" },
        ],
      })],
    },
  };
  const rows = deriveGuiHistory([assistant(execute)]);
  assert.deepEqual(rows[0].toolNames, [
    "get_engineering_context",
    "search_project_history",
  ]);
});

test("name-only Code Mode remains unknown and timestamps require native evidence", () => {
  const execute = {
    type: "tool",
    id: "outer-execute-id",
    name: "execute",
    state: {
      status: "completed",
      content: [text({
        ok: true,
        value: { aiTaskStatus: "SUCCEEDED" },
        toolCalls: [{ name: "devlog.get_engineering_context" }],
      })],
    },
  };
  const rows = deriveGuiHistory([assistant(execute)]);

  assert.equal(rows[0].platformOutcome, "COMPLETED");
  assert.equal(rows[0].taskOutcome, undefined);
  assert.equal(rows[0].observedAt, undefined);
  assert.equal(rows[0].durationMs, undefined);
});

test("History recomputation replaces repeated tool-part updates without duplication", () => {
  const running = directPart("devlog_story_agent", "running");
  const completed = directPart("devlog_story_agent", "completed", [
    text({ aiTaskStatus: "SUCCEEDED" }),
  ]);
  running.id = completed.id = "same-native-call";
  const initial = deriveGuiHistory([assistant(running)]);
  const updated = deriveGuiHistory([assistant(completed)]);

  assert.equal(initial.length, 1);
  assert.equal(updated.length, 1);
  assert.equal(updated[0].platformOutcome, "COMPLETED");
  assert.equal(updated[0].taskOutcome, "SUCCEEDED");
});

test("History is bounded, newest transcript entries first, and distinguishes unavailable", () => {
  const messages = Array.from({ length: 25 }, (_, index) =>
    assistant(directPart(
      index % 2 === 0
        ? "devlog_story_agent"
        : "devlog_search_project_history",
      "running",
    )),
  );
  const rows = deriveGuiHistory(messages);

  assert.equal(rows.length, guiPresenceLimits.maxHistoryRows);
  assert.equal(rows[0].toolName, "devlog_story_agent");
  assert.equal(rows.at(-1).toolName, "devlog_search_project_history");
  assert.deepEqual(deriveGuiHistory([]), []);
  assert.equal(deriveGuiHistory(undefined), null);
});

test("History honors message, part, and nested-row inspection bounds", () => {
  const olderOnly = Array.from({ length: 41 }, (_, index) =>
    index === 0
      ? assistant(directPart("devlog_story_agent", "completed"))
      : assistant({ type: "text", text: `turn ${index}` }),
  );
  assert.deepEqual(deriveGuiHistory(olderOnly), []);

  const partsBeyondBound = assistant(
    directPart("devlog_story_agent", "completed"),
    ...Array.from({ length: 80 }, () => ({ type: "text", text: "not a tool" })),
  );
  assert.deepEqual(deriveGuiHistory([partsBeyondBound]), []);

  const calls = Array.from({ length: 81 }, (_, index) => ({
    name: index === 80
      ? "devlog.get_engineering_context"
      : "browser.tabs.list",
  }));
  const outer = {
    type: "tool",
    name: "execute",
    state: { status: "completed", metadata: { toolCalls: calls } },
  };
  assert.deepEqual(deriveGuiHistory([assistant(outer)]), []);
});

test("Code Mode uses one shared inspection budget across metadata and results", () => {
  const outer = {
    type: "tool",
    name: "execute",
    state: {
      status: "completed",
      metadata: {
        toolCalls: Array.from(
          { length: guiPresenceLimits.maxCodeModeRows },
          () => ({ name: "browser.tabs.list" }),
        ),
      },
      content: [text({
        toolCalls: [{ name: "devlog.get_engineering_context" }],
      })],
    },
  };
  assert.deepEqual(deriveGuiHistory([assistant(outer)]), []);
});

test("History bounds retained Code Mode names and native IDs", () => {
  const names = [
    "devlog_story_agent",
    "devlog_devlog_story_agent",
    "devlog_analyze_story_context",
    "devlog_get_engineering_context",
    "devlog_search_project_history",
    "devlog_sync_project_history",
    "analyze_story_context",
    "get_engineering_context",
    "search_project_history",
    "sync_project_history",
  ];
  const outer = {
    type: "tool",
    name: "execute",
    id: "x".repeat(guiPresenceLimits.maxNativeCallIdLength + 1),
    state: {
      status: "completed",
      metadata: {
        toolCalls: names.map((name) => ({ name: `devlog.${name}` })),
      },
    },
  };
  const row = deriveGuiHistory([assistant(outer)])[0];
  assert.equal(row.hostCallId, undefined);
  assert.equal(
    row.toolNames.length,
    guiPresenceLimits.maxHistoryToolNamesPerRow,
  );
  assert.equal(row.additionalToolNameCount, 2);
});

test("History projection is replaced with the routed session transcript", () => {
  const sessionA = deriveGuiHistory([
    assistant(directPart("devlog_story_agent", "completed")),
  ]);
  const sessionB = deriveGuiHistory([
    assistant(directPart("devlog_search_project_history", "running")),
  ]);

  assert.deepEqual(sessionA.map((row) => row.toolName), ["devlog_story_agent"]);
  assert.deepEqual(sessionB.map((row) => row.toolName), [
    "devlog_search_project_history",
  ]);
});

test("native session subscriptions filter by route and dispose every listener", () => {
  const listeners = new Map();
  const data = {
    on(type, handler) {
      listeners.set(type, handler);
      return () => listeners.delete(type);
    },
  };
  const counts = { refresh: 0, started: 0, interrupted: 0 };
  const dispose = subscribeGuiSessionEvents(data, "session-a", {
    refresh: () => counts.refresh++,
    started: () => counts.started++,
    interrupted: () => counts.interrupted++,
  });

  assert.equal(listeners.size, 5);
  listeners.get("session.tool.called")({ data: { sessionID: "session-b" } });
  listeners.get("session.tool.called")({ data: { sessionID: "session-a" } });
  listeners.get("session.execution.started")({ data: { sessionID: "session-a" } });
  listeners.get("session.execution.interrupted")({
    data: { sessionID: "session-a", reason: "shutdown" },
  });
  assert.deepEqual(counts, { refresh: 1, started: 1, interrupted: 1 });

  dispose.forEach((unsubscribe) => unsubscribe());
  assert.equal(listeners.size, 0);
});

test("projection returns only a state and never retains raw tool payloads", () => {
  const privateText = "private prompt and token payload";
  const state = deriveGuiPresence([
    assistant({
      ...directPart("devlog_story_agent", "completed", [text({ aiTaskStatus: "COMPLETED" })]),
      state: {
        status: "completed",
        content: [text({ aiTaskStatus: "COMPLETED", secret: privateText })],
        input: { prompt: privateText, token: privateText },
      },
    }),
  ]);

  assert.equal(state, "READY");
  assert.equal(JSON.stringify(state).includes(privateText), false);
});
