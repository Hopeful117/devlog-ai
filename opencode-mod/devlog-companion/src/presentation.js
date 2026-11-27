/**
 * @typedef {"IDLE" | "WORKING" | "READY" | "ERROR" | "DISCONNECTED"} PresentationState
 * @typedef {"none" | "breathe" | "rotate" | "pulse"} MotionStyle
 */

/** @type {readonly PresentationState[]} */
export const PRESENTATION_STATES = Object.freeze([
  "IDLE",
  "WORKING",
  "READY",
  "ERROR",
  "DISCONNECTED",
]);

export const READY_PREVIEW_MS = 3000;

const VISUALS = Object.freeze({
  IDLE: Object.freeze({
    label: "IDLE",
    color: "#3fb950",
    ringOpacity: 0.4,
    satelliteOpacity: 0.7,
    disconnected: false,
    connectors: true,
    motion: "breathe",
  }),
  WORKING: Object.freeze({
    label: "WORKING",
    color: "#fbbf24",
    ringOpacity: 0.4,
    satelliteOpacity: 0.7,
    disconnected: false,
    connectors: false,
    motion: "rotate",
  }),
  READY: Object.freeze({
    label: "READY",
    color: "#58a6ff",
    ringOpacity: 0.4,
    satelliteOpacity: 0.7,
    disconnected: false,
    connectors: true,
    motion: "pulse",
  }),
  ERROR: Object.freeze({
    label: "ERROR",
    color: "#f85149",
    ringOpacity: 0.55,
    satelliteOpacity: 0.35,
    disconnected: false,
    connectors: true,
    motion: "none",
  }),
  DISCONNECTED: Object.freeze({
    label: "DISCONNECTED",
    color: "#8b949e",
    ringOpacity: 0.2,
    satelliteOpacity: 0.25,
    disconnected: true,
    connectors: false,
    motion: "none",
  }),
});

/**
 * Normalize a presentation input without treating unknown values as live state.
 *
 * @param {unknown} value
 * @returns {PresentationState}
 */
export function normalizePresentationState(value) {
  return PRESENTATION_STATES.includes(/** @type {PresentationState} */ (value))
    ? /** @type {PresentationState} */ (value)
    : "IDLE";
}

/**
 * Return the visual description for a simulated presentation input.
 *
 * @param {unknown} value
 * @param {boolean} [reducedMotion]
 */
export function presentationDescriptor(value, reducedMotion = false) {
  const state = normalizePresentationState(value);
  const visual = VISUALS[state];
  return {
    ...visual,
    state,
    motion: reducedMotion ? "none" : visual.motion,
  };
}

/**
 * Describe live evidence without collapsing UNKNOWN into the simulated IDLE
 * fallback. Preview inputs continue to use presentationDescriptor.
 *
 * @param {unknown} value
 * @param {boolean} [reducedMotion]
 */
export function livePresentationDescriptor(value, reducedMotion = false) {
  if (value !== "UNKNOWN") return presentationDescriptor(value, reducedMotion);
  return {
    label: "UNKNOWN",
    color: "#8b949e",
    ringOpacity: 0.2,
    satelliteOpacity: 0.25,
    disconnected: false,
    connectors: false,
    state: "UNKNOWN",
    motion: "none",
  };
}

/**
 * Apply a preview-only state selection or reset. A stale READY timer cannot
 * reset a state that has since changed.
 *
 * @param {unknown} current
 * @param {{type: "SELECT"; state: unknown} | {type: "RESET" | "READY_TIMEOUT"} | unknown} action
 * @returns {PresentationState}
 */
export function transitionPresentationState(current, action) {
  const state = normalizePresentationState(current);
  if (!action || typeof action !== "object" || !("type" in action)) return state;

  const event = /** @type {{type?: unknown; state?: unknown}} */ (action);
  if (event.type === "SELECT") {
    return normalizePresentationState(event.state);
  }
  if (event.type === "RESET") return "IDLE";
  if (event.type === "READY_TIMEOUT" && state === "READY") return "IDLE";
  return state;
}
