import assert from "node:assert/strict";
import test from "node:test";

import {
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
    maxResultText: 8192,
    maxResultBlocks: 8,
  });
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
