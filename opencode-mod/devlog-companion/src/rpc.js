import { Rpc } from "@opencode/plugin/rpc";

const callSchema = {
  type: "object",
  properties: {
    callID: { type: "string" },
    tool: { type: "string" },
    startedAt: { type: "number" },
    endedAt: { type: "number" },
    platformOutcome: {
      type: "string",
      enum: ["ATTEMPTED", "RESULT_REPORTED", "ERROR", "INCOMPLETE"],
    },
    mcpOutcome: { type: "string", enum: ["UNKNOWN", "ERROR"] },
    mcpOperationId: { type: "string" },
    taskId: { type: "string" },
    taskStatus: {
      type: "string",
      enum: [
        "UNKNOWN",
        "SUBMITTED",
        "PENDING",
        "RUNNING",
        "COMPLETED",
        "SUCCEEDED",
        "FAILED",
        "CANCELLED",
        "REJECTED",
        "POLL_TIMEOUT",
      ],
    },
  },
  required: ["callID", "tool", "startedAt", "platformOutcome", "mcpOutcome", "taskStatus"],
  additionalProperties: false,
};

/**
 * @param {unknown} value
 * @returns {value is Record<string, unknown>}
 */
function isRecord(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

const snapshotInputSchema = {
  "~standard": {
    version: /** @type {const} */ (1),
    vendor: "devlog-companion",
    /** @param {unknown} value */
    validate(value) {
      if (!isRecord(value)) {
        return { issues: [{ message: "Expected an object" }] };
      }
      if (Object.keys(value).some((key) => key !== "sessionID")) {
        return { issues: [{ message: "Unexpected input property" }] };
      }
      const sessionID = value.sessionID;
      if (
        typeof sessionID !== "string" ||
        sessionID.length < 1 ||
        sessionID.length > 128
      ) {
        return {
          issues: [{ message: "sessionID must be a string of 1 to 128 characters" }],
        };
      }
      return { value: { sessionID } };
    },
  },
};

export const DevLogCompanionRpc = Rpc.define({
  id: "devlog-companion",
  events: {},
  methods: {
    snapshot: {
      input: snapshotInputSchema,
      output: {
        type: "object",
        properties: {
          sessionID: { type: "string" },
          connectivity: { type: "string", enum: ["UNKNOWN"] },
          calls: { type: "array", items: callSchema, maxItems: 20 },
        },
        required: ["sessionID", "connectivity", "calls"],
        additionalProperties: false,
      },
    },
  },
});
