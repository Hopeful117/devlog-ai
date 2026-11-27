import assert from "node:assert/strict";
import test from "node:test";

import { deriveTuiProjection } from "../src/tui-projection.js";

function assistant(...content) {
  return {
    id: "assistant-message",
    type: "assistant",
    time: { created: 1 },
    content,
  };
}

function directTool(status, content = []) {
  return {
    type: "tool",
    id: "native-call-id",
    name: "devlog_story_agent",
    state: { status, content },
    time: { created: 2 },
  };
}

test("TUI distinguishes unavailable data, an empty inspected window, and no activity", () => {
  const unavailable = deriveTuiProjection(undefined);
  assert.equal(unavailable.window, "unavailable");
  assert.equal(unavailable.presence, "UNKNOWN");
  assert.deepEqual(unavailable.history, []);

  const empty = deriveTuiProjection([]);
  assert.equal(empty.window, "empty");
  assert.equal(empty.presence, "UNKNOWN");

  const inspectedWithoutDevLog = deriveTuiProjection([
    assistant({ type: "tool", id: "other", name: "unrelated_tool", state: { status: "completed" } }),
  ]);
  assert.equal(inspectedWithoutDevLog.window, "available");
  assert.equal(inspectedWithoutDevLog.presence, "IDLE");
  assert.equal(inspectedWithoutDevLog.activity, "UNKNOWN");
  assert.equal(inspectedWithoutDevLog.latest, undefined);
});

test("TUI projection reuses native DevLog evidence without inferring task success", () => {
  const projection = deriveTuiProjection([
    assistant(directTool("completed", [{ type: "text", text: '{"status":"COMPLETED"}' }])),
  ]);

  assert.equal(projection.presence, "UNKNOWN");
  assert.equal(projection.activity, "COMPLETED");
  assert.equal(projection.latest.toolName, "devlog_story_agent");
  assert.equal(projection.latest.taskOutcome, undefined);
  assert.equal(projection.history.length, 1);
  assert.equal("content" in projection.history[0], false);
});

test("outer Code Mode completion does not create nested task outcomes", () => {
  const projection = deriveTuiProjection([
    assistant({
      type: "tool",
      id: "outer-execute-id",
      name: "execute",
      state: {
        status: "completed",
        metadata: { toolCalls: [{ tool: "devlog_story_agent", status: "completed" }] },
      },
    }),
  ]);

  assert.equal(projection.activity, "COMPLETED");
  assert.equal(projection.latest.source, "code-mode");
  assert.deepEqual(projection.latest.toolNames, ["devlog_story_agent"]);
  assert.equal(projection.latest.taskOutcome, undefined);
});
