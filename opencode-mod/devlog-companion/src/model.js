const DEVLOG_TOOL_NAMES = new Set([
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
]);

const TASK_STATUSES = new Set([
  "SUBMITTED",
  "PENDING",
  "RUNNING",
  "COMPLETED",
  "SUCCEEDED",
  "FAILED",
  "CANCELLED",
  "REJECTED",
  "POLL_TIMEOUT",
]);

const UUID =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const MAX_SESSIONS = 100;
const MAX_HISTORY_PER_SESSION = 20;
const MAX_ACTIVE_PER_SESSION = 20;
const MAX_ACTIVE_CALLS = 200;
const RETENTION_MS = 15 * 60 * 1000;
const MAX_RESPONSE_CHARS = 64 * 1024;

function boundedString(value, limit = 128) {
  return typeof value === "string" && value.length > 0 && value.length <= limit
    ? value
    : undefined;
}

function isRecord(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

export function isDevLogToolName(name) {
  if (typeof name !== "string") return false;
  const normalized = name.startsWith("mcp__devlog__")
    ? name.slice("mcp__devlog__".length)
    : name;
  return DEVLOG_TOOL_NAMES.has(normalized);
}

function parseText(value) {
  if (typeof value !== "string" || value.length > MAX_RESPONSE_CHARS) return;
  try {
    const parsed = JSON.parse(value);
    return isRecord(parsed) ? parsed : undefined;
  } catch {
    return undefined;
  }
}

function responseObjects(result) {
  if (!isRecord(result)) return [];
  const candidates = [
    result,
    isRecord(result.output) ? result.output : parseText(result.output),
    isRecord(result.structuredContent)
      ? result.structuredContent
      : parseText(result.structuredContent),
  ];
  if (Array.isArray(result.content)) {
    for (const item of result.content) {
      if (isRecord(item) && item.type === "text") {
        candidates.push(parseText(item.text));
      }
    }
  } else if (typeof result.content === "string") {
    candidates.push(parseText(result.content));
  }
  return candidates.filter(isRecord);
}

/**
 * Extracts only explicit, bounded DevLog identifiers/statuses. Generic
 * `status` fields are intentionally ignored because they may describe the
 * OpenCode tool or the Story Agent wrapper, not the underlying DevLog task.
 */
export function decodeDevLogResult(result) {
  const objects = responseObjects(result);
  const isError = objects.some((item) => item.isError === true);
  let taskId;
  let mcpOperationId;
  let taskStatus;

  for (const item of objects) {
    const candidateId = item.aiTaskId ?? item.taskId;
    if (
      typeof candidateId === "string" &&
      candidateId.length <= 64 &&
      UUID.test(candidateId)
    ) {
      taskId = candidateId;
    }

    const operationId = item.mcpOperationId;
    if (
      typeof operationId === "string" &&
      operationId.length <= 64 &&
      UUID.test(operationId)
    ) {
      mcpOperationId = operationId;
    }

    const candidateStatus = item.aiTaskStatus ?? item.taskStatus;
    if (typeof candidateStatus === "string" && candidateStatus.length <= 40) {
      const normalized = candidateStatus.toUpperCase();
      if (TASK_STATUSES.has(normalized)) taskStatus = normalized;
    }
  }

  return {
    mcpOutcome: isError ? "ERROR" : "UNKNOWN",
    taskId,
    mcpOperationId,
    taskStatus: taskStatus ?? "UNKNOWN",
  };
}

function sessionKey(location, sessionID) {
  return `${location}\u0000${sessionID}`;
}

function safeSnapshot(sessionID, calls, history) {
  return {
    sessionID,
    connectivity: "UNKNOWN",
    calls: [...calls.values(), ...history]
      .sort((left, right) => right.sequence - left.sequence)
      .slice(0, MAX_HISTORY_PER_SESSION)
      .map(({ sequence: _sequence, ...call }) => ({ ...call })),
  };
}

export function createProjectionStore({
  location,
  now = Date.now,
  maxSessions = MAX_SESSIONS,
  maxHistoryPerSession = MAX_HISTORY_PER_SESSION,
  maxActivePerSession = MAX_ACTIVE_PER_SESSION,
  maxActiveCalls = MAX_ACTIVE_CALLS,
  retentionMs = RETENTION_MS,
} = {}) {
  const sessions = new Map();
  let sequence = 0;

  function prune() {
    const cutoff = now() - retentionMs;
    for (const [key, state] of sessions) {
      state.history = state.history.filter((call) => call.startedAt >= cutoff);
      for (const [callID, call] of state.active) {
        if (call.startedAt < cutoff) {
          state.active.delete(callID);
          state.history.unshift({
            ...call,
            sequence: ++sequence,
            platformOutcome: "INCOMPLETE",
            endedAt: undefined,
          });
        }
      }
      state.history = state.history.slice(0, maxHistoryPerSession);
      if (state.active.size === 0 && state.history.length === 0) {
        sessions.delete(key);
      }
    }
    while (sessions.size > maxSessions) {
      sessions.delete(sessions.keys().next().value);
    }
  }

  function forSession(sessionID, create = false) {
    if (!location || !boundedString(sessionID)) return;
    const key = sessionKey(location, sessionID);
    let state = sessions.get(key);
    if (!state && create) {
      state = { active: new Map(), history: [] };
      sessions.set(key, state);
    }
    return state;
  }

  function finish(state, callID, patch) {
    const call = state.active.get(callID);
    if (!call) return;
    state.active.delete(callID);
    state.history.unshift({ ...call, ...patch, sequence: ++sequence });
    state.history = state.history.slice(0, maxHistoryPerSession);
  }

  return Object.freeze({
    before(event) {
      prune();
      if (!isRecord(event) || !isDevLogToolName(event.tool)) return;
      const sessionID = boundedString(event.sessionID);
      const callID = boundedString(event.id);
      if (!sessionID || !callID) return;
      const state = forSession(sessionID, true);
      if (!state) return;
      if (state.active.has(callID)) return;
      if (state.active.size >= maxActivePerSession) return;

      let globalActive = 0;
      for (const entry of sessions.values()) globalActive += entry.active.size;
      if (globalActive >= maxActiveCalls) return;

      state.active.set(callID, {
        callID,
        tool: event.tool,
        sequence: ++sequence,
        startedAt: now(),
        platformOutcome: "ATTEMPTED",
        mcpOutcome: "UNKNOWN",
        taskId: undefined,
        taskStatus: "UNKNOWN",
      });
    },

    after(event) {
      prune();
      if (!isRecord(event) || !isDevLogToolName(event.tool)) return;
      const sessionID = boundedString(event.sessionID);
      const callID = boundedString(event.id);
      if (!sessionID || !callID) return;
      const state = forSession(sessionID);
      if (!state?.active.has(callID)) return;

      if (event.status === "error") {
        finish(state, callID, {
          endedAt: now(),
          platformOutcome: "ERROR",
        });
        return;
      }

      if (event.status !== "completed") return;
      const decoded = decodeDevLogResult(event.result);
      finish(state, callID, {
        endedAt: now(),
        platformOutcome: "RESULT_REPORTED",
        ...decoded,
      });
    },

    snapshot(sessionID) {
      prune();
      const state = forSession(sessionID);
      if (!state) return { sessionID, connectivity: "UNKNOWN", calls: [] };
      return safeSnapshot(sessionID, state.active, state.history);
    },

    clear() {
      sessions.clear();
    },

    counts() {
      prune();
      let activeCalls = 0;
      for (const entry of sessions.values()) activeCalls += entry.active.size;
      return { sessions: sessions.size, activeCalls };
    },
  });
}

export function derivePresence(calls) {
  if (!Array.isArray(calls)) return "UNKNOWN";
  if (calls.some((call) => call.platformOutcome === "ATTEMPTED")) {
    return "WORKING";
  }
  const latest = calls[0];
  if (!latest) return "IDLE";
  if (
    latest.platformOutcome === "ERROR" ||
    latest.mcpOutcome === "ERROR" ||
    ["FAILED", "CANCELLED", "REJECTED"].includes(latest.taskStatus)
  ) {
    return "ERROR";
  }
  if (
    latest.platformOutcome === "INCOMPLETE" ||
    latest.taskStatus === "UNKNOWN" ||
    latest.taskStatus === "POLL_TIMEOUT"
  ) {
    return "UNKNOWN";
  }
  if (latest.platformOutcome !== "RESULT_REPORTED") return "UNKNOWN";
  if (
    latest.taskStatus === "SUBMITTED" ||
    latest.taskStatus === "PENDING" ||
    latest.taskStatus === "RUNNING"
  ) {
    return "WORKING";
  }
  if (
    latest.taskStatus === "COMPLETED" ||
    latest.taskStatus === "SUCCEEDED"
  ) {
    return "READY";
  }
  return "IDLE";
}

export const projectionLimits = Object.freeze({
  maxSessions: MAX_SESSIONS,
  maxHistoryPerSession: MAX_HISTORY_PER_SESSION,
  maxActivePerSession: MAX_ACTIVE_PER_SESSION,
  maxActiveCalls: MAX_ACTIVE_CALLS,
  retentionMs: RETENTION_MS,
});
