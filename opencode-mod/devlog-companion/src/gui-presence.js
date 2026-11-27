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

const MAX_MESSAGES = 40;
const MAX_PARTS_PER_MESSAGE = 80;
const MAX_CODE_MODE_ROWS = 80;
const MAX_HISTORY_ROWS = 20;
const MAX_HISTORY_TOOL_NAMES_PER_ROW = 8;
const MAX_NATIVE_CALL_ID_LENGTH = 128;
const MAX_RESULT_TEXT = 8192;
const MAX_RESULT_BLOCKS = 8;

/**
 * @param {unknown} value
 * @returns {value is Record<string, any>}
 */
function isRecord(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

/** @param {unknown} name */
export function isGuiDevLogToolName(name) {
  if (typeof name !== "string") return false;
  const normalized = name.startsWith("mcp__devlog__")
    ? name.slice("mcp__devlog__".length)
    : name;
  return DEVLOG_TOOL_NAMES.has(normalized);
}

/** @param {unknown} name */
function isGuiCodeModeDevLogToolName(name) {
  if (isGuiDevLogToolName(name)) return true;
  return typeof name === "string" &&
    name.startsWith("devlog.") &&
    isGuiDevLogToolName(name.slice("devlog.".length));
}

/**
 * Return the allowlisted display name without retaining any host payload.
 * @param {unknown} name
 * @returns {string | undefined}
 */
function normalizedDevLogToolName(name) {
  if (typeof name !== "string") return;
  if (name.startsWith("mcp__devlog__")) {
    const normalized = name.slice("mcp__devlog__".length);
    return DEVLOG_TOOL_NAMES.has(normalized) ? normalized : undefined;
  }
  if (DEVLOG_TOOL_NAMES.has(name)) return name;
  if (name.startsWith("devlog.")) {
    const normalized = name.slice("devlog.".length);
    return DEVLOG_TOOL_NAMES.has(normalized) ? normalized : undefined;
  }
}

/** @param {unknown} value */
function parseText(value) {
  if (typeof value !== "string" || value.length > MAX_RESULT_TEXT) return;
  try {
    const parsed = JSON.parse(value);
    return isRecord(parsed) ? parsed : undefined;
  } catch {
    return undefined;
  }
}

/**
 * @param {unknown} content
 * @returns {{statuses: string[]; isError: boolean}}
 */
function taskStatusEvidence(content) {
  if (!Array.isArray(content)) return { statuses: [], isError: false };
  const statuses = new Set();
  let isError = false;
  const blocks = content.slice(0, MAX_RESULT_BLOCKS);

  for (const block of blocks) {
    if (!isRecord(block)) continue;
    if (block.isError === true) isError = true;
    if (block.type !== "text") continue;
    const decoded = parseText(block.text);
    if (!decoded) continue;
    if (decoded.isError === true) isError = true;
    for (const candidate of [decoded.aiTaskStatus, decoded.taskStatus]) {
      if (typeof candidate !== "string" || candidate.length > 40) continue;
      const normalized = candidate.toUpperCase();
      if (TASK_STATUSES.has(normalized)) statuses.add(normalized);
    }
  }
  return { statuses: [...statuses], isError };
}

/**
 * @param {unknown} content
 * @returns {"WORKING" | "READY" | "ERROR" | "UNKNOWN"}
 */
function decodeTaskOutcome(content) {
  const evidence = taskStatusEvidence(content);
  if (evidence.isError) return "ERROR";
  // Conflicting explicit task statuses are inconclusive, not a terminal result.
  if (evidence.statuses.length !== 1) return "UNKNOWN";
  const [taskStatus] = evidence.statuses;

  if (["FAILED", "CANCELLED", "REJECTED"].includes(taskStatus)) {
    return "ERROR";
  }
  if (["SUBMITTED", "PENDING", "RUNNING"].includes(taskStatus)) {
    return "WORKING";
  }
  if (taskStatus === "COMPLETED" || taskStatus === "SUCCEEDED") return "READY";
  return "UNKNOWN";
}

/**
 * Extract only an explicitly returned, recognized task status.
 * This intentionally does not treat generic `status` or host completion as a
 * DevLog task outcome.
 *
 * @param {unknown} content
 * @returns {string | undefined}
 */
function explicitTaskStatus(content) {
  const evidence = taskStatusEvidence(content);
  if (evidence.isError || evidence.statuses.length !== 1) return;
  return evidence.statuses[0];
}

/**
 * Keep only native IDs within the projection's explicit size budget.
 * Oversized values are omitted rather than truncated into a different ID.
 * @param {unknown} value
 * @returns {string | undefined}
 */
function boundedNativeCallId(value) {
  return typeof value === "string" &&
    value.length > 0 &&
    value.length <= MAX_NATIVE_CALL_ID_LENGTH
    ? value
    : undefined;
}

/**
 * @param {unknown} value
 * @returns {number | undefined}
 */
function nativeTimestamp(value) {
  return typeof value === "number" &&
    Number.isFinite(value) &&
    value > 0 &&
    value <= 8.64e15
    ? value
    : undefined;
}

/**
 * @param {Record<string, any>} part
 * @returns {{observedAt?: number; durationMs?: number}}
 */
function nativePartTiming(part) {
  const timing = isRecord(part.time) ? part.time : {};
  const stateTiming = isRecord(part.state?.time) ? part.state.time : {};
  const startedAt = nativeTimestamp(
    timing.ran ?? stateTiming.ran ?? timing.start ?? stateTiming.start,
  );
  const completedAt = nativeTimestamp(
    timing.completed ?? stateTiming.completed ?? timing.end ?? stateTiming.end,
  );
  return {
    observedAt: completedAt ?? startedAt,
    durationMs:
      startedAt !== undefined &&
      completedAt !== undefined &&
      completedAt >= startedAt
        ? completedAt - startedAt
        : undefined,
  };
}

/**
 * Derive a fresh, bounded History projection from a transcript snapshot.
 * Recomputing from the transcript (rather than appending event notifications)
 * means repeated native updates replace rows instead of duplicating them.
 * The returned rows contain no transcript or tool payload objects.
 *
 * @param {unknown} messages
 * @returns {Array<{
 *   source: "direct-tool" | "code-mode",
 *   toolName?: string,
 *   toolNames?: string[],
 *   additionalToolNameCount?: number,
 *   hostCallId?: string,
 *   platformOutcome: "RUNNING" | "COMPLETED" | "ERROR" | "UNKNOWN",
 *   taskOutcome?: string,
 *   observedAt?: number,
 *   durationMs?: number
 * }> | null}
 */
export function deriveGuiHistory(messages) {
  if (!Array.isArray(messages)) return null;
  /** @type {Array<{
   *   source: "direct-tool" | "code-mode",
   *   toolName?: string,
   *   toolNames?: string[],
   *   additionalToolNameCount?: number,
   *   hostCallId?: string,
   *   platformOutcome: "RUNNING" | "COMPLETED" | "ERROR" | "UNKNOWN",
   *   taskOutcome?: string,
   *   observedAt?: number,
   *   durationMs?: number
   * }>} */
  const rows = [];

  for (const message of messages.slice(-MAX_MESSAGES)) {
    if (!isRecord(message) || message.type !== "assistant") continue;
    const parts = Array.isArray(message.content)
      ? message.content.slice(-MAX_PARTS_PER_MESSAGE)
      : [];
    for (const part of parts) {
      if (!isRecord(part) || part.type !== "tool") continue;
      const status = part.state?.status;
      const timing = nativePartTiming(part);

      if (isGuiDevLogToolName(part.name ?? part.tool)) {
        rows.push({
          source: "direct-tool",
          toolName: normalizedDevLogToolName(part.name ?? part.tool),
          hostCallId: boundedNativeCallId(part.id ?? part.callID),
          platformOutcome:
            status === "running"
              ? "RUNNING"
              : status === "completed"
                ? "COMPLETED"
                : status === "error"
                  ? "ERROR"
                  : "UNKNOWN",
          taskOutcome:
            status === "completed"
              ? explicitTaskStatus(part.state?.content)
              : undefined,
          ...timing,
        });
        continue;
      }

      if (part.name !== "execute" && part.tool !== "execute") continue;
      const toolNames = new Set();
      /** @type {unknown[]} */
      const nestedRows = [];
      let inspectedNestedRows = 0;
      /** @param {unknown[]} rows */
      const addNestedRows = (rows) => {
        const remaining = MAX_CODE_MODE_ROWS - inspectedNestedRows;
        const boundedRows = rows.slice(0, remaining);
        nestedRows.push(...boundedRows);
        inspectedNestedRows += boundedRows.length;
      };
      const metadata = part.state?.metadata;
      if (isRecord(metadata) && Array.isArray(metadata.toolCalls)) {
        addNestedRows(metadata.toolCalls);
      }
      const blocks = Array.isArray(part.state?.content)
        ? part.state.content.slice(0, MAX_RESULT_BLOCKS)
        : [];
      for (const block of blocks) {
        if (inspectedNestedRows >= MAX_CODE_MODE_ROWS) break;
        if (!isRecord(block) || block.type !== "text") continue;
        const decoded = parseText(block.text);
        if (Array.isArray(decoded?.toolCalls)) {
          addNestedRows(decoded.toolCalls);
        }
      }
      for (const nestedRow of nestedRows) {
        if (!isRecord(nestedRow)) continue;
        const name = nestedRow.tool ?? nestedRow.name;
        if (isGuiCodeModeDevLogToolName(name)) {
          const normalized = normalizedDevLogToolName(
            typeof name === "string" && name.startsWith("devlog.")
              ? name.slice("devlog.".length)
              : name,
          );
          if (normalized) toolNames.add(normalized);
        }
      }
      if (toolNames.size === 0) continue;
      const allToolNames = [...toolNames];
      rows.push({
        source: "code-mode",
        toolNames: allToolNames.slice(0, MAX_HISTORY_TOOL_NAMES_PER_ROW),
        ...(allToolNames.length > MAX_HISTORY_TOOL_NAMES_PER_ROW
          ? {
              additionalToolNameCount:
                allToolNames.length - MAX_HISTORY_TOOL_NAMES_PER_ROW,
            }
          : {}),
        hostCallId: boundedNativeCallId(part.id ?? part.callID),
        platformOutcome:
          status === "running"
            ? "RUNNING"
            : status === "completed"
              ? "COMPLETED"
              : status === "error"
                ? "ERROR"
                : "UNKNOWN",
        // The outer execute result cannot establish an individual nested
        // DevLog outcome; name-only rows intentionally stay unknown.
        taskOutcome: undefined,
        ...timing,
      });
    }
  }

  return rows.slice(-MAX_HISTORY_ROWS).reverse();
}

/**
 * @param {Record<string, any>} part
 * @returns {Array<"WORKING" | "ERROR" | "UNKNOWN">}
 */
function codeModeOutcomes(part) {
  const metadata = part.state?.metadata;
  const rows = [];
  if (isRecord(metadata) && Array.isArray(metadata.toolCalls)) {
    rows.push(...metadata.toolCalls.slice(0, MAX_CODE_MODE_ROWS));
  }
  const blocks = Array.isArray(part.state?.content)
    ? part.state.content.slice(0, MAX_RESULT_BLOCKS)
    : [];
  for (const block of blocks) {
    if (!isRecord(block) || block.type !== "text") continue;
    const decoded = parseText(block.text);
    if (Array.isArray(decoded?.toolCalls)) {
      rows.push(...decoded.toolCalls.slice(0, MAX_CODE_MODE_ROWS - rows.length));
    }
  }
  /** @type {Array<"WORKING" | "ERROR" | "UNKNOWN">} */
  const outcomes = [];

  for (const row of rows.slice(0, MAX_CODE_MODE_ROWS)) {
    if (
      !isRecord(row) ||
      !isGuiCodeModeDevLogToolName(row.tool ?? row.name)
    ) continue;
    if (row.status === "running" && part.state?.status === "running") {
      outcomes.push("WORKING");
    } else if (row.status === "error") {
      outcomes.push("ERROR");
    } else {
      // Pinned Code Mode results expose names, not nested status/call IDs/results.
      outcomes.push("UNKNOWN");
    }
  }

  return outcomes;
}

/** @param {unknown} messages */
function recognizedOutcomes(messages) {
  /** @type {Array<"ACTIVE" | "WORKING" | "READY" | "ERROR" | "UNKNOWN">} */
  const outcomes = [];
  if (!Array.isArray(messages)) return outcomes;

  const boundedMessages = messages.slice(-MAX_MESSAGES);
  let latestAssistantIndex = -1;
  boundedMessages.forEach((message, index) => {
    if (isRecord(message) && message.type === "assistant") {
      latestAssistantIndex = index;
    }
  });
  for (const [messageIndex, message] of boundedMessages.entries()) {
    if (!isRecord(message) || message.type !== "assistant") continue;
    const parts = Array.isArray(message.content)
      ? message.content.slice(-MAX_PARTS_PER_MESSAGE)
      : [];

    for (const part of parts) {
      if (!isRecord(part) || part.type !== "tool") continue;
      if (isGuiDevLogToolName(part.name)) {
        const status = part.state?.status;
        if (status === "running") {
          outcomes.push(messageIndex === latestAssistantIndex ? "ACTIVE" : "UNKNOWN");
        } else if (status === "error") {
          outcomes.push("ERROR");
        } else if (status === "completed") {
          outcomes.push(decodeTaskOutcome(part.state.content));
        } else if (status === "streaming") {
          // Streaming only proves an attempt/input phase, not dispatch.
          outcomes.push("UNKNOWN");
        } else {
          outcomes.push("UNKNOWN");
        }
        continue;
      }

      if (part.name === "execute") {
        outcomes.push(
          ...codeModeOutcomes(part).map((outcome) =>
            outcome === "WORKING"
              ? messageIndex === latestAssistantIndex
                ? "ACTIVE"
                : "UNKNOWN"
              : outcome,
          ),
        );
      }
    }
  }

  return outcomes;
}

/**
 * Derive only a bounded presentation state. The transcript and event payloads
 * are read synchronously and are never returned or retained by this module.
 *
 * @param {unknown} messages
 * @param {{interrupted?: boolean}} [options]
 * @returns {"IDLE" | "WORKING" | "READY" | "ERROR" | "UNKNOWN"}
 */
export function deriveGuiPresence(messages, options = {}) {
  if (!Array.isArray(messages)) return "UNKNOWN";
  const outcomes = recognizedOutcomes(messages);
  if (outcomes.length === 0) return "IDLE";

  if (options.interrupted) return "UNKNOWN";
  if (outcomes.includes("ACTIVE")) return "WORKING";
  const latest = outcomes.at(-1);
  return latest === "ACTIVE" ? "UNKNOWN" : latest ?? "UNKNOWN";
}

/**
 * Subscribe to only the session lifecycle events that invalidate this GUI
 * projection. Event payloads are reduced to a session-key comparison and are
 * never returned or retained.
 *
 * @param {any} data
 * @param {string} sessionID
 * @param {{refresh: () => void; started: () => void; interrupted: () => void}} callbacks
 */
export function subscribeGuiSessionEvents(data, sessionID, callbacks) {
  /** @param {() => void} callback */
  const forCurrentSession = (callback) =>
    /**
     * @param {{data?: {sessionID?: unknown}}} event
     */
    (event) => {
      if (event?.data?.sessionID !== sessionID) return;
      callback();
    };

  return [
    data.on("session.tool.called", forCurrentSession(callbacks.refresh)),
    data.on("session.tool.success", forCurrentSession(callbacks.refresh)),
    data.on("session.tool.failed", forCurrentSession(callbacks.refresh)),
    data.on("session.execution.started", forCurrentSession(callbacks.started)),
    data.on(
      "session.execution.interrupted",
      forCurrentSession(callbacks.interrupted),
    ),
  ];
}

export const guiPresenceLimits = Object.freeze({
  maxMessages: MAX_MESSAGES,
  maxPartsPerMessage: MAX_PARTS_PER_MESSAGE,
  maxCodeModeRows: MAX_CODE_MODE_ROWS,
  maxHistoryRows: MAX_HISTORY_ROWS,
  maxHistoryToolNamesPerRow: MAX_HISTORY_TOOL_NAMES_PER_ROW,
  maxNativeCallIdLength: MAX_NATIVE_CALL_ID_LENGTH,
  maxResultText: MAX_RESULT_TEXT,
  maxResultBlocks: MAX_RESULT_BLOCKS,
});
