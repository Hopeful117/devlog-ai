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
 * @returns {"WORKING" | "READY" | "ERROR" | "UNKNOWN"}
 */
function decodeTaskOutcome(content) {
  if (!Array.isArray(content)) return "UNKNOWN";
  let taskStatus = "UNKNOWN";
  let isError = false;
  const blocks = content.slice(0, MAX_RESULT_BLOCKS);

  for (const block of blocks) {
    if (!isRecord(block)) continue;
    if (block.isError === true) isError = true;
    if (block.type !== "text") continue;
    const decoded = parseText(block.text);
    if (!decoded) continue;
    if (decoded.isError === true) isError = true;
    const candidate = decoded.aiTaskStatus ?? decoded.taskStatus;
    if (typeof candidate !== "string" || candidate.length > 40) continue;
    const normalized = candidate.toUpperCase();
    if (TASK_STATUSES.has(normalized)) taskStatus = normalized;
  }

  if (isError || ["FAILED", "CANCELLED", "REJECTED"].includes(taskStatus)) {
    return "ERROR";
  }
  if (["SUBMITTED", "PENDING", "RUNNING"].includes(taskStatus)) {
    return "WORKING";
  }
  if (taskStatus === "COMPLETED" || taskStatus === "SUCCEEDED") return "READY";
  return "UNKNOWN";
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
  maxResultText: MAX_RESULT_TEXT,
  maxResultBlocks: MAX_RESULT_BLOCKS,
});
