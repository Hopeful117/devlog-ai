/** @jsxImportSource @opentui/solid */
import { Plugin } from "@opencode/plugin/tui";
import type { Context } from "@opencode/plugin/tui/plugin";
import { For, Show, createEffect, createMemo, createSignal, onCleanup } from "solid-js";
import { deriveGuiHistory, subscribeGuiSessionEvents } from "./gui-presence.js";
import { deriveTuiProjection } from "./tui-projection.js";

const PANEL_NAME = "devlog-companion";
const COMMAND_ID = "devlog-companion.tui.open";

function statusLabel(value: string) {
  switch (value) {
    case "IDLE":
      return "No activity observed";
    case "WORKING":
      return "Working";
    case "READY":
      return "Ready";
    case "ERROR":
      return "Error";
    default:
      return "Unknown";
  }
}

function activityLabel(value: string) {
  switch (value) {
    case "RUNNING":
      return "Running";
    case "COMPLETED":
      return "Host completed";
    case "ERROR":
      return "Host error";
    default:
      return "Unknown";
  }
}

function latestLabel(
  row: NonNullable<ReturnType<typeof deriveGuiHistory>>[number] | undefined,
) {
  if (!row) return "None recognized";
  const names =
    row.source === "code-mode"
      ? row.toolNames?.join(", ") ?? "Code Mode"
      : row.toolName ?? "DevLog tool";
  return `${names}${row.additionalToolNameCount ? ` +${row.additionalToolNameCount}` : ""}`;
}

function useProjection(context: Context, getSessionID: () => string) {
  const [revision, setRevision] = createSignal(0);
  const [interrupted, setInterrupted] = createSignal(false);

  createEffect(() => {
    const currentSessionID = getSessionID();
    if (!currentSessionID) return;

    setInterrupted(false);
    setRevision((value) => value + 1);
    const dispose = subscribeGuiSessionEvents(context.data, currentSessionID, {
      refresh: () => setRevision((value) => value + 1),
      started: () => {
        setInterrupted(false);
        setRevision((value) => value + 1);
      },
      interrupted: () => {
        setInterrupted(true);
        setRevision((value) => value + 1);
      },
    });

    onCleanup(() => {
      for (const unsubscribe of dispose) {
        try {
          unsubscribe();
        } catch {
          // Presentation cleanup must not affect host execution.
        }
      }
    });
  });

  return createMemo(() => {
    revision();
    const sessionID = getSessionID();
    if (!sessionID) {
      return deriveTuiProjection(undefined);
    }
    try {
      return deriveTuiProjection(context.data.session.message.list(sessionID), {
        interrupted: interrupted(),
      });
    } catch {
      // Keep host data errors local and never retain error payloads.
      return deriveTuiProjection(undefined);
    }
  });
}

function SessionStatus(props: {
  context: Context;
  sessionID: string;
}) {
  const projection = useProjection(props.context, () => props.sessionID);
  return (
    <text>
      DevLog: {statusLabel(projection().presence)} | Activity:{" "}
      {activityLabel(projection().activity)} | Latest:{" "}
      {latestLabel(projection().latest)}
    </text>
  );
}

function CompanionPanel(props: {
  context: Context;
  sessionID: string;
}) {
  const projection = useProjection(props.context, () => props.sessionID);
  return (
    <box flexDirection="column" padding={1}>
      <text>DevLog Companion</text>
      <text>Now: {statusLabel(projection().presence)}</text>
      <text>Observed activity: {activityLabel(projection().activity)}</text>
      <text>Latest recognized: {latestLabel(projection().latest)}</text>
      <text>History (loaded session window)</text>
      <Show when={projection().window === "unavailable"}>
        <text>Session data unavailable</text>
      </Show>
      <Show when={projection().window === "empty"}>
        <text>No messages in the loaded session window</text>
      </Show>
      <Show when={projection().window === "available" && projection().history.length === 0}>
        <text>No recognized DevLog operations in the loaded window</text>
      </Show>
      <For each={projection().history}>
        {(row) => (
          <text>
            {latestLabel(row)} — {activityLabel(row.platformOutcome)}
            {row.taskOutcome ? ` — task ${row.taskOutcome}` : ""}
          </text>
        )}
      </For>
    </box>
  );
}

export default Plugin.define({
  id: "devlog-companion-tui",
  setup(context) {
    const disposeStatus = context.ui.slot({
      append: "session.composer.top",
      render: ({ sessionID }) => (
        <SessionStatus context={context} sessionID={sessionID} />
      ),
    });
    const disposePanel = context.ui.slot({
      append: "session.panel",
      render: (input) => (
        <Show when={input.name === PANEL_NAME}>
          <CompanionPanel context={context} sessionID={input.sessionID} />
        </Show>
      ),
    });

    // The pinned TUI SDK owns this layer and removes it on plugin deactivation.
    context.keymap.layer(() => ({
      commands: [
        {
          id: COMMAND_ID,
          title: "Open DevLog Companion",
          description: "Show the current session's DevLog activity",
          group: "DevLog Companion",
          bind: false,
          palette: true,
          slash: { name: "devlog-companion" },
          run: () => {
            if (!context.ui.panel.open(PANEL_NAME)) {
              context.ui.toast.show({
                title: "DevLog Companion",
                message: "Open a session before showing its activity.",
                variant: "info",
              });
            }
          },
        },
      ],
    }));

    return () => {
      for (const dispose of [disposePanel, disposeStatus]) {
        try {
          dispose();
        } catch {
          // Registration cleanup must not affect host operation.
        }
      }
    };
  },
});
