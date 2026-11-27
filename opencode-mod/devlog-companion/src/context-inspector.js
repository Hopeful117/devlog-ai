const CONTEXT_TOOL_NAMES = new Map([
  ["get_engineering_context", "get_engineering_context"],
  ["devlog_get_engineering_context", "get_engineering_context"],
]);
const KNOWN_DEVLOG_TOOL_NAMES = new Set([
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

const TRUST_TIERS = [
  "TRUSTED",
  "HUMAN_AUTHORED",
  "TECHNICAL_EVIDENCE",
  "SYSTEM_METADATA",
];
const FRESHNESS_STATES = new Set([
  "CURRENT",
  "STALE",
  "NO_BASELINE",
  "UNKNOWN",
  "PARTIALLY_FRESH",
]);
const WARNING_CODES = new Set([
  "RESPONSE_SIZE_TRUNCATED",
  "PROJECT_CONTEXT_STALE",
  "PROJECT_CONTEXT_PARTIALLY_FRESH",
  "REPOSITORY_CONTEXT_BUDGET_APPLIED",
  "EVIDENCE_SUMMARY_TRUNCATED",
  "CONTENT_ENRICHMENT_TRUNCATED",
  "CONTENT_ENRICHMENT_UNAVAILABLE",
  "CONTENT_ENRICHMENT_SKIPPED",
  "CONTENT_ENRICHMENT_LIMIT_APPLIED",
  "CONTENT_BUDGET_EXHAUSTED",
]);
const MAX_MESSAGES = 40;
const MAX_PARTS_PER_MESSAGE = 80;
const MAX_RESULT_CHARS = 25_000;
const MAX_HOST_CALL_ID_CHARS = 128;
const MAX_EVIDENCE_COUNTED = 1_000;
const MAX_METADATA_COUNT = 1_000_000;
const MAX_WARNINGS_INSPECTED = 32;
const MAX_DIAGNOSTICS = 8;
const REVISION = /^[0-9a-f]{40}(?:[0-9a-f]{24})?$/i;
const RESULT_TRUNCATION_WARNING = "RESPONSE_SIZE_TRUNCATED";

/**
 * @typedef {object} LocalContextInspection
 * @property {"AVAILABLE" | "PARTIAL" | "UNAVAILABLE" | "UNVERIFIED"} state
 * @property {string | undefined} [reason]
 * @property {"OBSERVED_TOOL_RESULT"} provenance
 * @property {string | undefined} [toolName]
 * @property {string | undefined} [hostCallId]
 * @property {string | undefined} [scope]
 * @property {string | undefined} [freshness]
 * @property {string | undefined} [repositoryRevision]
 * @property {string | undefined} [contextRevision]
 * @property {boolean | undefined} [truncated]
 * @property {boolean | undefined} [truncationUnknown]
 * @property {Record<string, number> | undefined} [evidenceCountsByTrustTier]
 * @property {Record<string, number> | undefined} [sectionCountsByTrustTier]
 * @property {{candidate: number, selected: number, rejected: number} | undefined} [reportedCounts]
 * @property {"CONSISTENT" | "MISMATCH" | "UNKNOWN" | undefined} [countConsistency]
 * @property {string[] | undefined} [diagnostics]
 * @property {boolean | undefined} [empty]
 */
/**
 * @param {unknown} value
 * @returns {value is Record<string, any>}
 */
function isRecord(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

/** @param {any} part */
function nativeCallId(part) {
  const value = part?.id ?? part?.callID;
  return typeof value === "string" &&
    value.length > 0 &&
    value.length <= MAX_HOST_CALL_ID_CHARS
    ? value
    : undefined;
}

/** @param {any} part */
function normalizedDevLogToolName(part) {
  if (part?.type !== "tool") return;
  let rawName = part.name ?? part.tool;
  if (typeof rawName !== "string") return;
  if (rawName.startsWith("mcp__devlog__")) {
    rawName = rawName.slice("mcp__devlog__".length);
  }
  if (rawName.startsWith("devlog.")) {
    rawName = rawName.slice("devlog.".length);
  }
  return KNOWN_DEVLOG_TOOL_NAMES.has(rawName) ? rawName : undefined;
}

/** @param {any} part */
function directContextToolName(part) {
  const name = normalizedDevLogToolName(part);
  return name ? CONTEXT_TOOL_NAMES.get(name) : undefined;
}

function emptyTrustCounts() {
  return Object.fromEntries([...TRUST_TIERS, "UNKNOWN"].map((tier) => [tier, 0]));
}

/**
 * @param {LocalContextInspection["state"]} state
 * @param {string} reason
 * @param {Partial<LocalContextInspection>} [details]
 * @returns {LocalContextInspection}
 */
function fixedResult(state, reason, details = {}) {
  return {
    state,
    reason,
    provenance: "OBSERVED_TOOL_RESULT",
    ...details,
  };
}

/**
 * @param {any[]} rows
 * @param {() => void} markPartial
 */
function countTrustTiers(rows, markPartial) {
  const counts = emptyTrustCounts();
  const inspectCount = Math.min(rows.length, MAX_EVIDENCE_COUNTED);
  if (rows.length > MAX_EVIDENCE_COUNTED) markPartial();

  for (let index = 0; index < inspectCount; index += 1) {
    const row = rows[index];
    if (!isRecord(row)) {
      counts.UNKNOWN += 1;
      markPartial();
      continue;
    }
    if (TRUST_TIERS.includes(row.trustTier)) counts[row.trustTier] += 1;
    else {
      counts.UNKNOWN += 1;
      markPartial();
    }
  }
  return { counts, inspected: inspectCount, complete: rows.length <= MAX_EVIDENCE_COUNTED };
}

/**
 * @param {unknown} value
 * @param {() => void} markPartial
 */
function safeRevision(value, markPartial) {
  if (value == null) return undefined;
  if (typeof value === "string" && REVISION.test(value)) return value;
  markPartial();
  return undefined;
}

/**
 * @param {any} part
 * @param {string | undefined} scope
 * @returns {LocalContextInspection}
 */
function projectEngineeringContext(part, scope) {
  const toolName = directContextToolName(part);
  const safeToolName = normalizedDevLogToolName(part);
  const callId = nativeCallId(part);
  const details = {
    toolName: toolName ?? safeToolName ?? "Unsupported tool",
    hostCallId: callId,
    scope,
  };

  if (part?.type !== "tool") {
    return fixedResult("UNVERIFIED", "NOT_A_NATIVE_TOOL_RESULT", details);
  }
  if (part.name === "execute" || part.tool === "execute") {
    return fixedResult("UNVERIFIED", "CODE_MODE_UNSUPPORTED", {
      ...details,
      toolName: "Code Mode",
    });
  }
  if (!toolName) {
    return fixedResult("UNVERIFIED", "UNSUPPORTED_DIRECT_TOOL", details);
  }
  if (!callId) {
    return fixedResult("UNVERIFIED", "NATIVE_CALL_ID_UNAVAILABLE", details);
  }
  if (part.state?.status !== "completed") {
    return fixedResult("UNAVAILABLE", "TOOL_RESULT_NOT_COMPLETED", details);
  }

  const content = part.state?.content;
  if (!Array.isArray(content) || content.length !== 1) {
    return fixedResult("UNVERIFIED", "AMBIGUOUS_RESULT_BLOCKS", details);
  }
  const [block] = content;
  if (!isRecord(block) || block.type !== "text" || block.isError === true) {
    return fixedResult("UNVERIFIED", "UNSUPPORTED_RESULT_BLOCK", details);
  }
  if (typeof block.text !== "string" || block.text.length > MAX_RESULT_CHARS) {
    return fixedResult("UNVERIFIED", "RESULT_SIZE_UNSUPPORTED", details);
  }

  let dto;
  try {
    dto = JSON.parse(block.text);
  } catch {
    return fixedResult("UNVERIFIED", "RESULT_JSON_MALFORMED", details);
  }
  if (
    !isRecord(dto) ||
    !isRecord(dto.project) ||
    typeof dto.intent !== "string" ||
    !Array.isArray(dto.evidence) ||
    !isRecord(dto.metadata) ||
    !Array.isArray(dto.sections) ||
    !isRecord(dto.requestEcho)
  ) {
    return fixedResult("UNVERIFIED", "ENGINEERING_CONTEXT_SHAPE_UNSUPPORTED", details);
  }

  const metadata = dto.metadata;
  const countFields = ["candidateCount", "selectedCount", "rejectedCount"];
  if (
    countFields.some((field) =>
      !Number.isSafeInteger(metadata[field]) ||
      metadata[field] < 0 ||
      metadata[field] > MAX_METADATA_COUNT
    ) ||
    typeof metadata.truncated !== "boolean" ||
    !isRecord(metadata.freshness)
  ) {
    return fixedResult("UNVERIFIED", "ENGINEERING_CONTEXT_METADATA_UNSUPPORTED", details);
  }

  let partial = false;
  const markPartial = () => {
    partial = true;
  };
  const evidence = countTrustTiers(dto.evidence, markPartial);
  const sectionCounts = emptyTrustCounts();
  const sectionLimit = Math.min(dto.sections.length, TRUST_TIERS.length);
  if (dto.sections.length > TRUST_TIERS.length) markPartial();
  let sectionEvidenceComplete = true;
  let sectionTierConflict = false;
  const sectionPresence = Object.fromEntries(TRUST_TIERS.map((tier) => [tier, 0]));
  for (let index = 0; index < sectionLimit; index += 1) {
    const section = dto.sections[index];
    if (!isRecord(section) || !Array.isArray(section.evidence)) {
      return fixedResult("UNVERIFIED", "ENGINEERING_CONTEXT_SECTION_UNSUPPORTED", details);
    }
    if (!TRUST_TIERS.includes(section.trustTier)) {
      markPartial();
      sectionEvidenceComplete = false;
      continue;
    }
    sectionPresence[section.trustTier] += 1;
    const count = Math.min(section.evidence.length, MAX_EVIDENCE_COUNTED);
    if (section.evidence.length > MAX_EVIDENCE_COUNTED) {
      markPartial();
      sectionEvidenceComplete = false;
    }
    for (let evidenceIndex = 0; evidenceIndex < count; evidenceIndex += 1) {
      const row = section.evidence[evidenceIndex];
      if (!isRecord(row) || !TRUST_TIERS.includes(row.trustTier)) {
        markPartial();
        sectionEvidenceComplete = false;
      } else {
        sectionCounts[section.trustTier] += 1;
        if (row.trustTier !== section.trustTier) {
          sectionTierConflict = true;
          markPartial();
        }
      }
    }
  }
  const sectionSetComplete =
    dto.sections.length === TRUST_TIERS.length &&
    TRUST_TIERS.every((tier) => sectionPresence[tier] === 1);
  if (!sectionSetComplete) markPartial();

  const freshness = metadata.freshness;
  const freshnessState = FRESHNESS_STATES.has(freshness.status)
    ? freshness.status
    : "UNKNOWN";
  if (freshnessState === "UNKNOWN") markPartial();
  const repositoryRevision = safeRevision(freshness.repositoryRevision, markPartial);
  const contextRevision = safeRevision(freshness.contextRevision, markPartial);

  const warnings = metadata.warnings;
  const diagnostics = new Set();
  let hasTruncationWarning = false;
  let warningListComplete = true;
  if (!Array.isArray(warnings)) {
    markPartial();
    warningListComplete = false;
  } else if (Array.isArray(warnings)) {
    if (warnings.length > MAX_WARNINGS_INSPECTED) {
      markPartial();
      warningListComplete = false;
    }
    for (const warning of warnings.slice(0, MAX_WARNINGS_INSPECTED)) {
      if (typeof warning !== "string") {
        markPartial();
        diagnostics.add("OTHER_WARNING_PRESENT");
        continue;
      }
      if (warning === RESULT_TRUNCATION_WARNING) hasTruncationWarning = true;
      if (WARNING_CODES.has(warning)) diagnostics.add(warning);
      else if (warning.length > 0) diagnostics.add("OTHER_WARNING_PRESENT");
    }
  }
  if (diagnostics.size > MAX_DIAGNOSTICS) {
    markPartial();
  }

  const truncated = metadata.truncated || hasTruncationWarning;
  const truncationUnknown =
    !metadata.truncated && !hasTruncationWarning && !warningListComplete;
  const evidenceCountsByTrustTier = evidence.counts;
  const sectionEvidenceCounts = emptyTrustCounts();
  for (let index = 0; index < sectionLimit; index += 1) {
    const section = dto.sections[index];
    if (!isRecord(section) || !Array.isArray(section?.evidence)) continue;
    const tier = section.trustTier;
    if (!TRUST_TIERS.includes(tier)) continue;
    for (const row of section.evidence.slice(0, MAX_EVIDENCE_COUNTED)) {
      if (isRecord(row) && TRUST_TIERS.includes(row.trustTier)) {
        sectionEvidenceCounts[row.trustTier] += 1;
      } else {
        sectionEvidenceCounts.UNKNOWN += 1;
      }
    }
  }
  const sectionCountsComplete =
    sectionSetComplete && sectionEvidenceComplete;
  const arraysComplete = evidence.complete && sectionCountsComplete;
  /** @type {LocalContextInspection["countConsistency"]} */
  let countConsistency = "UNKNOWN";
  if (!truncated && arraysComplete) {
    const evidenceTotal = Object.values(evidenceCountsByTrustTier)
      .reduce((total, count) => total + count, 0);
    const sectionEvidenceTotal = Object.values(sectionEvidenceCounts)
      .reduce((total, count) => total + count, 0);
    const byTierMatch = [...TRUST_TIERS, "UNKNOWN"].every(
      (tier) => evidenceCountsByTrustTier[tier] === sectionEvidenceCounts[tier],
    );
    countConsistency =
      metadata.selectedCount === evidenceTotal &&
      evidenceTotal === sectionEvidenceTotal &&
      byTierMatch &&
      !sectionTierConflict &&
      TRUST_TIERS.every((tier) => sectionCounts[tier] === evidenceCountsByTrustTier[tier])
        ? "CONSISTENT"
        : "MISMATCH";
    if (countConsistency === "MISMATCH") markPartial();
  }
  if (truncated || !arraysComplete) markPartial();

  const empty =
    !truncated &&
    arraysComplete &&
    dto.evidence.length === 0 &&
    sectionEvidenceTotalIsZero(sectionCounts) &&
    countConsistency === "CONSISTENT";
  const projection = {
    ...details,
    freshness: freshnessState,
    repositoryRevision,
    contextRevision,
    truncated,
    truncationUnknown,
    evidenceCountsByTrustTier,
    sectionCountsByTrustTier: Object.fromEntries(
      TRUST_TIERS.map((tier) => [tier, sectionCounts[tier]]),
    ),
    reportedCounts: {
      candidate: metadata.candidateCount,
      selected: metadata.selectedCount,
      rejected: metadata.rejectedCount,
    },
    countConsistency,
    diagnostics: [...diagnostics].slice(0, MAX_DIAGNOSTICS),
    empty,
  };
  return {
    state: partial ? "PARTIAL" : "AVAILABLE",
    reason: undefined,
    provenance: "OBSERVED_TOOL_RESULT",
    ...projection,
  };
}

/** @param {Record<string, number>} counts */
function sectionEvidenceTotalIsZero(counts) {
  return Object.values(counts).every((count) => count === 0);
}

/**
 * Resolve an inspection only from the exact native tool part selected in the
 * currently routed transcript. No task IDs, timestamps, or tool-name joins.
 */
/**
 * @param {unknown} messages
 * @param {string | undefined} hostCallId
 * @param {string | undefined} scope
 * @returns {LocalContextInspection}
 */
export function deriveLocalContextInspection(messages, hostCallId, scope) {
  if (!Array.isArray(messages)) {
    return fixedResult("UNAVAILABLE", "TRANSCRIPT_UNAVAILABLE", { scope });
  }
  if (!hostCallId) {
    return fixedResult("UNAVAILABLE", "NO_OPERATION_SELECTED", { scope });
  }

  const matches = [];
  for (const message of /** @type {any[]} */ (messages).slice(-MAX_MESSAGES)) {
    if (!isRecord(message) || message.type !== "assistant") continue;
    const parts = Array.isArray(message.content)
      ? message.content.slice(-MAX_PARTS_PER_MESSAGE)
      : [];
    for (const part of parts) {
      if (nativeCallId(part) === hostCallId) matches.push(part);
    }
  }
  if (matches.length === 0) {
    return fixedResult("UNAVAILABLE", "SOURCE_NO_LONGER_AVAILABLE", { scope });
  }
  if (matches.length !== 1) {
    return fixedResult("UNVERIFIED", "DUPLICATE_NATIVE_CALL_ID", { scope });
  }
  return projectEngineeringContext(matches[0], scope);
}

/**
 * A selection is meaningful only in the exact scope that created it.
 *
 * @param {{scope: string, hostCallId: string, projectionKey?: string} | undefined} selection
 * @param {string | undefined} currentScope
 * @returns {{scope: string, hostCallId: string, projectionKey?: string} | undefined}
 */
export function contextSelectionForScope(selection, currentScope) {
  if (!selection || !currentScope || selection.scope !== currentScope) return;
  return {
    scope: selection.scope,
    hostCallId: selection.hostCallId,
    ...(selection.projectionKey ? { projectionKey: selection.projectionKey } : {}),
  };
}

/**
 * Only a direct History row may select an individual Context result. Code Mode
 * rows intentionally represent the outer execution, not nested call identity.
 *
 * @param {unknown} row
 * @param {unknown} messages
 * @returns {boolean}
 */
export function isContextHistoryRowSelectable(row, messages) {
  if (
    !isRecord(row) ||
    row.source !== "direct-tool" ||
    row.toolName !== "get_engineering_context" ||
    typeof row.hostCallId !== "string" ||
    row.hostCallId.length === 0 ||
    row.hostCallId.length > MAX_HOST_CALL_ID_CHARS
  ) {
    return false;
  }
  const inspection = deriveLocalContextInspection(messages, row.hostCallId, undefined);
  return (
    (inspection.state === "AVAILABLE" || inspection.state === "PARTIAL") &&
    inspection.toolName === "get_engineering_context" &&
    inspection.hostCallId === row.hostCallId
  );
}

/**
 * Project eligible History rows into a minimal in-panel operation picker.
 *
 * @param {unknown} rows
 * @param {unknown} messages
 * @returns {Array<{hostCallId: string, toolName: "get_engineering_context", observedAt?: number}>}
 */
export function deriveSelectableContextOperations(rows, messages) {
  if (!Array.isArray(rows)) return [];
  const operations = [];
  for (const row of rows) {
    if (!isContextHistoryRowSelectable(row, messages)) continue;
    const selectable = /** @type {{hostCallId: string, observedAt?: number}} */ (row);
    operations.push({
      hostCallId: selectable.hostCallId,
      toolName: /** @type {"get_engineering_context"} */ ("get_engineering_context"),
      ...(typeof selectable.observedAt === "number" && Number.isFinite(selectable.observedAt)
        ? { observedAt: selectable.observedAt }
        : {}),
    });
  }
  return operations;
}

/**
 * Stable comparison key over the sanitized view model only.
 * @param {LocalContextInspection} inspection
 * @returns {string}
 */
export function localContextProjectionKey(inspection) {
  return JSON.stringify(inspection);
}

export const contextInspectorLimits = Object.freeze({
  maxMessages: MAX_MESSAGES,
  maxPartsPerMessage: MAX_PARTS_PER_MESSAGE,
  maxResultChars: MAX_RESULT_CHARS,
  maxHostCallIdChars: MAX_HOST_CALL_ID_CHARS,
  maxEvidenceCounted: MAX_EVIDENCE_COUNTED,
  maxMetadataCount: MAX_METADATA_COUNT,
  maxWarningsInspected: MAX_WARNINGS_INSPECTED,
  maxDiagnostics: MAX_DIAGNOSTICS,
});
