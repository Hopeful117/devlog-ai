import { ComposerPill, Panel, Plugin } from "@opencode/plugin/gui";
import { createEffect, createSignal, For, onCleanup, Show } from "solid-js";
import { DevLogCompanionRpc } from "./rpc.js";
import { derivePresence, type PresenceState, type ProjectionCall, type ProjectionSnapshot } from "./model.js";
import {
  createScopedRequest,
  scopedSnapshot,
  sessionScope,
  updateScopedSnapshot,
} from "./ui-state.js";

const LABELS: Record<PresenceState, string> = {
  IDLE: "DevLog · inactif",
  WORKING: "DevLog · tentative observée",
  READY: "DevLog · résultat de tâche rapporté",
  ERROR: "DevLog · erreur rapportée",
  UNKNOWN: "DevLog · état inconnu",
};

function statusText(call: ProjectionCall) {
  if (call.platformOutcome === "ATTEMPTED") {
    return "Appel observé avant exécution; le dispatch n’est pas établi.";
  }
  if (call.platformOutcome === "INCOMPLETE") {
    return "Aucun résultat terminal observé; l’issue reste inconnue.";
  }
  if (call.platformOutcome === "ERROR") {
    return "OpenCode a rapporté une erreur. L’état de la tâche DevLog reste indépendant.";
  }
  if (call.mcpOutcome === "ERROR") {
    return "Le résultat MCP signale une erreur.";
  }
  return `Résultat outil rapporté; tâche DevLog : ${call.taskStatus}.`;
}

export default Plugin.define({
  setup(ctx) {
    const [projection, setProjection] = createSignal<{
      scope: string;
      value?: ProjectionSnapshot;
      error: boolean;
    }>();

    createEffect(() => {
      const session = ctx.sessions.current();
      const scope = sessionScope(session);
      setProjection(undefined);
      if (!session || session.pending || !session.location || !scope) return;

      const { id, directory } = session;
      const serverID = session.server.id;
      const server = ctx.servers.get(serverID);
      if (!server?.compatible) return;
      const request = createScopedRequest(scope);
      let timer: ReturnType<typeof setTimeout> | undefined;

      const refresh = async () => {
        try {
          const currentServer = ctx.servers.get(serverID);
          if (!currentServer?.compatible) throw new Error("Server unavailable");
          const result = await currentServer.client
            .rpc(DevLogCompanionRpc)
            .snapshot(
              { sessionID: id },
              {
                location: { directory },
                signal: request.signal,
              },
            ) as ProjectionSnapshot;
          if (
            request.accepts(sessionScope(ctx.sessions.current())) &&
            result.sessionID === id
          ) {
            setProjection((current) =>
              updateScopedSnapshot(
                current,
                scope,
                sessionScope(ctx.sessions.current()),
                { value: result, error: false },
              ),
            );
          }
        } catch {
          if (request.accepts(sessionScope(ctx.sessions.current()))) {
            setProjection((current) =>
              updateScopedSnapshot(
                current,
                scope,
                sessionScope(ctx.sessions.current()),
                { value: undefined, error: true },
              ),
            );
          }
        } finally {
          if (request.accepts(sessionScope(ctx.sessions.current()))) {
            timer = setTimeout(refresh, 2000);
          }
        }
      };

      void refresh();
      onCleanup(() => {
        request.dispose();
        clearTimeout(timer);
      });
    });

    const tab = {
      id: "activity",
      title: "DevLog",
      pinned: true,
      fallback: true,
    };

    ctx.add(Panel, {
      id: "activity",
      region: "side",
      transient: true,
      list: () => [tab],
      render: (props) => {
        const current = () =>
          scopedSnapshot(projection(), sessionScope(props.session));
        return (
          <section class="devlog-companion">
            <h2>DevLog Companion</h2>
            <Show when={current()?.value} fallback={<p>Projection indisponible ou en attente.</p>}>
              {(value) => (
                <>
                  <p>{LABELS[derivePresence(value().calls)]}</p>
                  <p>Connectivité MCP : inconnue (aucun contrôle indépendant).</p>
                  <Show when={value().calls.length === 0}>
                    <p>Aucune activité DevLog observée dans cette session.</p>
                  </Show>
                  <ol>
                    <For each={value().calls}>
                      {(call) => (
                        <li>
                          <strong>{call.tool}</strong>
                          <p>{statusText(call)}</p>
                          <Show when={call.taskId}>
                            <small>Tâche DevLog : {call.taskId}</small>
                          </Show>
                        </li>
                      )}
                    </For>
                  </ol>
                </>
              )}
            </Show>
            <Show when={current()?.error}>
              <p role="status">Serveur DevLog Companion indisponible.</p>
            </Show>
          </section>
        );
      },
    });

    ctx.add(ComposerPill, {
      id: "presence",
      order: 20,
      show: (input) => {
        const value = scopedSnapshot(
          projection(),
          sessionScope(input.session),
        )?.value;
        if (!value) {
          return { label: LABELS.UNKNOWN, tone: "warning" };
        }
        const state = derivePresence(value.calls);
        return {
          label: LABELS[state],
          title: "État DevLog observé pour cette session",
          tone: state === "ERROR" ? "danger" : state === "WORKING" ? "warning" : "success",
        };
      },
    });
  },
});
