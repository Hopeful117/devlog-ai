import { deriveGuiHistory, deriveGuiPresence } from "./gui-presence.js";

/**
 * Derive a sanitized, bounded view model for the native TUI from its
 * already-loaded native message cache. Raw messages are not returned.
 *
 * @param {unknown} messages
 * @param {{interrupted?: boolean}} [options]
 */
export function deriveTuiProjection(messages, options = {}) {
  if (!Array.isArray(messages)) {
    return {
      window: "unavailable",
      presence: "UNKNOWN",
      activity: "UNKNOWN",
      latest: undefined,
      history: [],
    };
  }

  const history = deriveGuiHistory(messages) ?? [];
  const latest = history[0];
  const empty = messages.length === 0;
  return {
    window: empty ? "empty" : "available",
    // An empty inspected window is not evidence that DevLog is idle.
    presence: empty ? "UNKNOWN" : deriveGuiPresence(messages, options),
    activity: latest?.platformOutcome ?? "UNKNOWN",
    latest,
    history,
  };
}
