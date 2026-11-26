import assert from "node:assert/strict";
import test from "node:test";
import serverPlugin from "../src/server.js";
import { DevLogCompanionRpc } from "../src/rpc.js";
import {
  createScopedRequest,
  scopedSnapshot,
  sessionScope,
  updateScopedSnapshot,
} from "../src/ui-state.js";
import {
  createProjectionStore,
  decodeDevLogResult,
  derivePresence,
  isDevLogToolName,
  projectionLimits,
} from "../src/model.js";

const TASK_ID = "a9f45319-471b-4d73-bcf5-94b3aa14f3ca";
const OPERATION_ID = "d1e7f1b8-2a10-4a4f-a356-24ccfda9a460";

function beforeEvent(overrides = {}) {
  return {
    tool: "devlog_story_agent",
    sessionID: "session-a",
    id: "call-a",
    input: { prompt: "must not be retained", token: "secret" },
    ...overrides,
  };
}

test("matches only known DevLog MCP tool names", () => {
  assert.equal(isDevLogToolName("devlog_story_agent"), true);
  assert.equal(isDevLogToolName("mcp__devlog__get_engineering_context"), true);
  assert.equal(isDevLogToolName("devlog_search_project_history"), true);
  assert.equal(isDevLogToolName("unrelated_story_agent"), false);
  assert.equal(isDevLogToolName(undefined), false);
});

test("defines a strict, server-owned snapshot RPC with no broadcast events", () => {
  assert.equal(DevLogCompanionRpc.id, "devlog-companion");
  assert.deepEqual(Object.keys(DevLogCompanionRpc.methods), ["snapshot"]);
  assert.deepEqual(DevLogCompanionRpc.events, {});
  const validate = DevLogCompanionRpc.methods.snapshot.input["~standard"].validate;
  assert.deepEqual(validate({ sessionID: "session-a" }), {
    value: { sessionID: "session-a" },
  });
  assert.deepEqual(validate({ sessionID: "session-a", unexpected: true }), {
    issues: [{ message: "Unexpected input property" }],
  });
  assert.deepEqual(validate({}), {
    issues: [{ message: "sessionID must be a string of 1 to 128 characters" }],
  });
  assert.deepEqual(validate({ sessionID: 7 }), {
    issues: [{ message: "sessionID must be a string of 1 to 128 characters" }],
  });
  assert.deepEqual(validate(null), {
    issues: [{ message: "Expected an object" }],
  });
});

test("UI projection scope changes across server, project, location, and session routes", () => {
  const base = {
    key: "server-a\nsession-a",
    id: "session-a",
    server: { id: "server-a" },
    project: { id: "project-a" },
    location: { directory: "/workspace-a", workspaceID: "workspace-a" },
  };
  const baseScope = sessionScope(base);
  assert.ok(baseScope);
  assert.notEqual(sessionScope({ ...base, server: { id: "server-b" } }), baseScope);
  assert.notEqual(sessionScope({ ...base, project: { id: "project-b" } }), baseScope);
  assert.notEqual(sessionScope({ ...base, location: { directory: "/workspace-b" } }), baseScope);
  assert.notEqual(
    sessionScope({
      ...base,
      location: { directory: "/workspace-a", workspaceID: "workspace-b" },
    }),
    baseScope,
  );
  assert.notEqual(sessionScope({ ...base, key: "server-a\nsession-b", id: "session-b" }), baseScope);

  const first = updateScopedSnapshot(undefined, baseScope, baseScope, {
    value: { sessionID: "session-a" },
    error: false,
  });
  assert.equal(scopedSnapshot(first, baseScope), first);
  const nextScope = sessionScope({ ...base, id: "session-b", key: "server-a\nsession-b" });
  const replacement = updateScopedSnapshot(first, nextScope, nextScope, {
    value: { sessionID: "session-b" },
    error: false,
  });
  assert.equal(scopedSnapshot(replacement, baseScope), undefined);
  assert.equal(scopedSnapshot(replacement, nextScope).value.sessionID, "session-b");
  assert.equal(
    updateScopedSnapshot(replacement, baseScope, nextScope, {
      value: { sessionID: "stale-response" },
      error: false,
    }),
    replacement,
  );
  const otherWorkspaceScope = sessionScope({
    ...base,
    location: { directory: "/workspace-a", workspaceID: "workspace-b" },
  });
  assert.equal(
    updateScopedSnapshot(replacement, baseScope, otherWorkspaceScope, {
      value: { sessionID: "stale-workspace-response" },
      error: false,
    }),
    replacement,
  );
  assert.equal(scopedSnapshot(first, undefined), undefined);
});

test("route-scoped requests abort on cleanup and reject late cross-route responses", () => {
  const first = createScopedRequest("route-a");
  assert.equal(first.accepts("route-a"), true);
  const second = createScopedRequest("route-b");
  first.dispose();
  assert.equal(first.signal.aborted, true);
  assert.equal(first.accepts("route-a"), false);
  assert.equal(first.accepts("route-b"), false);
  assert.equal(second.accepts("route-b"), true);
  second.dispose();
  assert.equal(second.accepts("route-b"), false);
});

test("server hooks observe without mutating tool inputs/results and clean up", async () => {
  const hooks = new Map();
  const hookRegistrations = [];
  let registeredContract;
  let handlers;
  let disposed = false;
  const ctx = {
    location: {
      directory: "/workspace",
      workspaceID: "workspace-1",
      project: { id: "project-1" },
    },
    session: {
      async get({ sessionID }) {
        return {
          id: sessionID,
          projectID: "project-1",
          location: {
            directory: "/workspace",
            workspaceID: "workspace-1",
          },
        };
      },
    },
    tool: {
      async hook(name, callback) {
        hooks.set(name, callback);
        const registration = { disposed: false };
        registration.dispose = async () => {
          registration.disposed = true;
        };
        hookRegistrations.push(registration);
        return registration;
      },
    },
    rpc: {
      async register(contract, implementations) {
        registeredContract = contract;
        handlers = implementations;
        return { async dispose() { disposed = true; } };
      },
    },
  };

  const cleanup = await serverPlugin.setup(ctx);
  assert.equal(typeof cleanup, "function");
  assert.equal(registeredContract, DevLogCompanionRpc);

  const input = Object.freeze({ prompt: "private prompt", apiToken: "secret" });
  const before = Object.freeze({
    tool: "devlog_story_agent",
    sessionID: "session-a",
    id: "call-a",
    input,
  });
  const result = Object.freeze({
    output: Object.freeze({
      aiTaskId: TASK_ID,
      aiTaskStatus: "SUBMITTED",
      privateOutput: "must not be retained",
    }),
  });
  const after = Object.freeze({
    ...before,
    status: "completed",
    result,
  });

  await hooks.get("execute.before")(before);
  await hooks.get("execute.after")(after);
  assert.deepEqual(input, { prompt: "private prompt", apiToken: "secret" });
  assert.deepEqual(result.output.privateOutput, "must not be retained");

  const snapshot = await handlers.snapshot({ sessionID: "session-a" });
  assert.equal(snapshot.calls[0].taskId, TASK_ID);
  assert.equal(snapshot.calls[0].taskStatus, "SUBMITTED");
  assert.equal(JSON.stringify(snapshot).includes("private prompt"), false);
  assert.equal(JSON.stringify(snapshot).includes("secret"), false);
  assert.equal(JSON.stringify(snapshot).includes("must not be retained"), false);

  ctx.location.workspaceID = undefined;
  assert.deepEqual(await handlers.snapshot({ sessionID: "session-a" }), {
    sessionID: "session-a",
    connectivity: "UNKNOWN",
    calls: [],
  });

  ctx.session.get = async ({ sessionID }) => ({
    id: sessionID,
    projectID: "other-project",
    location: { directory: "/other-workspace" },
  });
  assert.deepEqual(await handlers.snapshot({ sessionID: "session-a" }), {
    sessionID: "session-a",
    connectivity: "UNKNOWN",
    calls: [],
  });

  await cleanup();
  assert.equal(disposed, true);
  assert.equal(hookRegistrations.every((registration) => registration.disposed), true);
  assert.deepEqual(await handlers.snapshot({ sessionID: "session-a" }), {
    sessionID: "session-a",
    connectivity: "UNKNOWN",
    calls: [],
  });
});

test("server setup disposes already registered hooks when RPC registration fails", async () => {
  const registrations = [];
  const ctx = {
    location: {
      directory: "/workspace",
      workspaceID: "workspace-1",
      project: { id: "project-1" },
    },
    tool: {
      async hook() {
        const registration = { disposed: false };
        registration.dispose = async () => {
          registration.disposed = true;
        };
        registrations.push(registration);
        return registration;
      },
    },
    rpc: {
      async register() {
        throw new Error("registration failed");
      },
    },
  };

  await assert.rejects(serverPlugin.setup(ctx), /registration failed/);
  assert.equal(registrations.length, 2);
  assert.equal(registrations.every((registration) => registration.disposed), true);
});

test("decodes explicit task identity without promoting wrapper status", () => {
  const decoded = decodeDevLogResult({
    output: {
      aiTaskId: TASK_ID,
      status: "COMPLETED",
    },
  });
  assert.deepEqual(decoded, {
    mcpOutcome: "UNKNOWN",
    taskId: TASK_ID,
    mcpOperationId: undefined,
    taskStatus: "UNKNOWN",
  });
});

test("decodes explicit task status and MCP errors, rejecting malformed identifiers", () => {
  assert.deepEqual(
    decodeDevLogResult({
      content: [
        {
          type: "text",
          text: JSON.stringify({
            aiTaskId: "not-a-uuid",
            mcpOperationId: OPERATION_ID,
            aiTaskStatus: "POLL_TIMEOUT",
            isError: true,
          }),
        },
      ],
    }),
    {
      mcpOutcome: "ERROR",
      taskId: undefined,
      mcpOperationId: OPERATION_ID,
      taskStatus: "POLL_TIMEOUT",
    },
  );
});

test("separates attempted call, reported result, MCP result, and task state", () => {
  let now = 1000;
  const store = createProjectionStore({ location: "/workspace", now: () => now });
  store.before(beforeEvent());
  assert.equal(store.snapshot("session-a").calls[0].platformOutcome, "ATTEMPTED");
  assert.equal(derivePresence(store.snapshot("session-a").calls), "WORKING");

  now = 1250;
  store.after({
    tool: "devlog_story_agent",
    sessionID: "session-a",
    id: "call-a",
    status: "completed",
    result: {
      output: { aiTaskId: TASK_ID, aiTaskStatus: "SUBMITTED" },
    },
  });

  const [call] = store.snapshot("session-a").calls;
  assert.equal(store.snapshot("session-a").connectivity, "UNKNOWN");
  assert.equal(call.platformOutcome, "RESULT_REPORTED");
  assert.equal(call.mcpOutcome, "UNKNOWN");
  assert.equal(call.taskId, TASK_ID);
  assert.equal(call.mcpOperationId, undefined);
  assert.equal(call.taskStatus, "SUBMITTED");
  assert.equal(derivePresence([call]), "WORKING");
  assert.equal(JSON.stringify(call).includes("secret"), false);
  assert.equal(JSON.stringify(call).includes("must not be retained"), false);
});

test("does not correlate across sessions or without platform call identifiers", () => {
  const store = createProjectionStore({ location: "/workspace", now: () => 500 });
  store.before(beforeEvent());
  store.after({
    tool: "devlog_story_agent",
    sessionID: "session-b",
    id: "call-a",
    status: "completed",
    result: { output: { aiTaskId: TASK_ID, aiTaskStatus: "COMPLETED" } },
  });
  store.before(beforeEvent({ id: undefined }));
  assert.equal(store.snapshot("session-a").calls[0].platformOutcome, "ATTEMPTED");
  assert.equal(store.snapshot("session-b").calls.length, 0);
});

test("keeps concurrent calls separate and orders terminal results by observation", () => {
  const store = createProjectionStore({ location: "/workspace", now: () => 500 });
  store.before(beforeEvent({ id: "call-first" }));
  store.before(beforeEvent({ id: "call-second" }));
  store.after({
    tool: "devlog_story_agent",
    sessionID: "session-a",
    id: "call-second",
    status: "completed",
    result: { output: { aiTaskId: TASK_ID, aiTaskStatus: "SUBMITTED" } },
  });
  store.after({
    tool: "devlog_story_agent",
    sessionID: "session-a",
    id: "call-first",
    status: "completed",
    result: { output: { aiTaskId: OPERATION_ID, aiTaskStatus: "COMPLETED" } },
  });

  const calls = store.snapshot("session-a").calls;
  assert.deepEqual(calls.map((call) => call.callID), ["call-first", "call-second"]);
  assert.equal(calls[0].taskStatus, "COMPLETED");
  assert.equal(calls[1].taskStatus, "SUBMITTED");
  assert.equal(store.counts().activeCalls, 0);
});

test("keeps tool errors separate from DevLog task outcomes", () => {
  const store = createProjectionStore({ location: "/workspace", now: () => 750 });
  store.before(beforeEvent());
  store.after({
    tool: "devlog_story_agent",
    sessionID: "session-a",
    id: "call-a",
    status: "error",
    error: new Error("must not be retained"),
  });
  const [call] = store.snapshot("session-a").calls;
  assert.equal(call.platformOutcome, "ERROR");
  assert.equal(call.taskStatus, "UNKNOWN");
  assert.equal(JSON.stringify(call).includes("must not be retained"), false);
  assert.equal(derivePresence([call]), "ERROR");
});

test("expires active attempts to incomplete rather than success", () => {
  let now = 1000;
  const store = createProjectionStore({
    location: "/workspace",
    now: () => now,
    retentionMs: 100,
  });
  store.before(beforeEvent());
  now = 1200;
  const [call] = store.snapshot("session-a").calls;
  assert.equal(call.platformOutcome, "INCOMPLETE");
  assert.equal(derivePresence([call]), "UNKNOWN");
});

test("bounds retained history and active calls", () => {
  let now = 1;
  const store = createProjectionStore({
    location: "/workspace",
    now: () => now++,
    maxHistoryPerSession: 2,
    maxActivePerSession: 2,
  });

  for (let index = 0; index < 4; index += 1) {
    const id = `call-${index}`;
    store.before(beforeEvent({ id }));
    store.after({
      tool: "devlog_story_agent",
      sessionID: "session-a",
      id,
      status: "completed",
      result: { output: { aiTaskId: TASK_ID, aiTaskStatus: "COMPLETED" } },
    });
  }

  assert.equal(store.snapshot("session-a").calls.length, 2);
  assert.equal(projectionLimits.maxSessions, 100);

  store.before(beforeEvent({ id: "active-1" }));
  store.before(beforeEvent({ id: "active-2" }));
  store.before(beforeEvent({ id: "active-3" }));
  assert.equal(store.counts().activeCalls, 2);
});

test("unknown and incomplete results never derive READY", () => {
  assert.equal(
    derivePresence([{ platformOutcome: "RESULT_REPORTED", taskStatus: "UNKNOWN" }]),
    "UNKNOWN",
  );
  assert.equal(
    derivePresence([{ platformOutcome: "INCOMPLETE", taskStatus: "COMPLETED" }]),
    "UNKNOWN",
  );
  assert.equal(
    derivePresence([{ platformOutcome: "RESULT_REPORTED", taskStatus: "COMPLETED" }]),
    "READY",
  );
  assert.equal(
    derivePresence([{ platformOutcome: "ERROR", mcpOutcome: "UNKNOWN", taskStatus: "COMPLETED" }]),
    "ERROR",
  );
  assert.equal(
    derivePresence([{ platformOutcome: "RESULT_REPORTED", mcpOutcome: "ERROR", taskStatus: "COMPLETED" }]),
    "ERROR",
  );
  assert.equal(
    derivePresence([{ platformOutcome: "INCOMPLETE", mcpOutcome: "UNKNOWN", taskStatus: "COMPLETED" }]),
    "UNKNOWN",
  );
  assert.equal(
    derivePresence([{ platformOutcome: "UNKNOWN", mcpOutcome: "UNKNOWN", taskStatus: "COMPLETED" }]),
    "UNKNOWN",
  );
});
