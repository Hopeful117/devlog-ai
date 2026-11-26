import { Plugin } from "@opencode/plugin";
import { createProjectionStore } from "./model.js";
import { DevLogCompanionRpc } from "./rpc.js";

export default Plugin.define({
  id: "devlog-companion",
  async setup(ctx) {
    const store = createProjectionStore({
      // Plugin setup is scoped to this OpenCode server location. Session IDs are
      // only used inside that server-local partition.
      location: `${ctx.location.directory}\u0000${ctx.location.workspaceID ?? ""}`,
    });
    const hookRegistrations = [];

    let registration;
    try {
      hookRegistrations.push(
        await ctx.tool.hook("execute.before", (event) => {
          try {
            store.before(event);
          } catch {
            // Observation is best-effort and must never change tool execution.
          }
        }),
      );

      hookRegistrations.push(
        await ctx.tool.hook("execute.after", (event) => {
          try {
            store.after(event);
          } catch {
            // Do not propagate observer errors into the tool result path.
          }
        }),
      );

      registration = await ctx.rpc.register(DevLogCompanionRpc, {
        snapshot: async ({ sessionID }) => {
          try {
            const session = await ctx.session.get({ sessionID });
            const sameWorkspace =
              typeof ctx.location.workspaceID === "string" &&
              ctx.location.workspaceID.length > 0 &&
              session.location.workspaceID === ctx.location.workspaceID;
            if (
              session.projectID !== ctx.location.project.id ||
              session.location.directory !== ctx.location.directory ||
              !sameWorkspace
            ) {
              return {
                sessionID,
                connectivity: "UNKNOWN",
                calls: [],
              };
            }
            return store.snapshot(sessionID);
          } catch {
            // Missing or inaccessible sessions fail closed without exposing state.
            return {
              sessionID,
              connectivity: "UNKNOWN",
              calls: [],
            };
          }
        },
      });
    } catch (error) {
      store.clear();
      await Promise.allSettled(
        hookRegistrations.map((hook) => hook.dispose()),
      );
      throw error;
    }

    return async () => {
      store.clear();
      await Promise.all([
        registration.dispose(),
        ...hookRegistrations.map((hook) => hook.dispose()),
      ]);
    };
  },
});
