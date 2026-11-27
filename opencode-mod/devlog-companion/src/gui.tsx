import {
  MenuItem,
  Panel,
  Plugin,
  type MenuItem as MenuItemDefinition,
  type MountedSession,
  type PanelTab,
} from "@opencode/plugin/gui";
import { For, Show, createEffect, createMemo, createSignal, onCleanup } from "solid-js";
import logo from "./devlog-mark.svg?raw";
import {
  PRESENTATION_STATES,
  READY_PREVIEW_MS,
  livePresentationDescriptor,
  normalizePresentationState,
  transitionPresentationState,
} from "./presentation.js";
import {
  deriveGuiPresence,
  deriveGuiHistory,
  subscribeGuiSessionEvents,
} from "./gui-presence.js";

const PREVIEW_BUILD = import.meta.env.MODE === "preview";
const HISTORY_VIEWS = ["now", "history"] as const;
type CompanionView = (typeof HISTORY_VIEWS)[number];

function formatDuration(durationMs: number) {
  if (durationMs < 1000) return `${Math.round(durationMs)} ms`;
  return `${(durationMs / 1000).toFixed(1)} s`;
}

function formatObservedTime(timestamp: number) {
  return new Date(timestamp).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

function useReducedMotion() {
  const [reducedMotion, setReducedMotion] = createSignal(false);
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
    return reducedMotion;
  }

  const query = window.matchMedia("(prefers-reduced-motion: reduce)");
  setReducedMotion(query.matches);
  const onChange = (event: MediaQueryListEvent) =>
    setReducedMotion(event.matches);
  query.addEventListener("change", onChange);
  onCleanup(() => query.removeEventListener("change", onChange));
  return reducedMotion;
}

function PresenceAvatar(props: {
  state: string;
  reducedMotion: boolean;
}) {
  const visual = () => livePresentationDescriptor(props.state, props.reducedMotion);
  const satellitePositions = [
    { cx: 10, cy: 3.5 },
    { cx: 16.5, cy: 13 },
    { cx: 3.5, cy: 13 },
  ];

  return (
    <svg
      class="devlog-companion__avatar"
      viewBox="0 0 20 20"
      width="24"
      height="24"
      aria-hidden="true"
    >
      <style>
        {"@media (prefers-reduced-motion: reduce) { animate, animateTransform { display: none; } }"}
      </style>
      <circle cx="10" cy="10" r="3" fill={visual().color} opacity="0.9" />
      <circle
        cx="10"
        cy="10"
        r="6.5"
        fill="none"
        stroke={visual().color}
        stroke-width="1"
        stroke-dasharray={visual().disconnected ? "2 3" : undefined}
        opacity={visual().ringOpacity}
      >
        <Show when={visual().motion === "breathe"}>
          <animate
            attributeName="opacity"
            values={`${visual().ringOpacity};0.75;${visual().ringOpacity}`}
            dur="2.4s"
            repeatCount="indefinite"
          />
        </Show>
        <Show when={visual().motion === "pulse"}>
          <animate
            attributeName="opacity"
            values={`${visual().ringOpacity};0.9;${visual().ringOpacity};0.9;${visual().ringOpacity}`}
            dur="1.6s"
            repeatCount={1}
          />
        </Show>
      </circle>
      <Show when={visual().connectors}>
        <g
          stroke={visual().color}
          stroke-width="0.8"
          opacity="0.3"
          fill="none"
        >
          <line x1="10" y1="6.5" x2="10" y2="4.9" />
          <line x1="13.4" y1="8.3" x2="14.8" y2="11.6" />
          <line x1="6.6" y1="8.3" x2="5.2" y2="11.6" />
        </g>
      </Show>
      <g>
        <For each={satellitePositions}>
          {(position) => (
            <circle
              cx={position.cx}
              cy={position.cy}
              r="1.4"
              fill={visual().color}
              opacity={visual().satelliteOpacity}
            />
          )}
        </For>
        <Show when={visual().motion === "rotate"}>
          <animateTransform
            attributeName="transform"
            type="rotate"
            from="0 10 10"
            to="360 10 10"
            dur="1.6s"
            repeatCount="indefinite"
          />
        </Show>
      </g>
    </svg>
  );
}

const activityTab = {
  id: "activity",
  title: "DevLog",
  closable: "compact",
  fallback: true,
} satisfies PanelTab;

export default Plugin.define({
  icons: { devlog: logo },
  i18n: {
    en: {
      "panel.title": "DevLog Companion",
      "panel.open": "Open DevLog Companion",
    },
  },
  setup(ctx) {
    const panelKey = `${ctx.id}:${activityTab.id}`;

    function CompanionPanel(props: { session: MountedSession }) {
      const [previewState, setPreviewState] = createSignal("IDLE");
      const [activeView, setActiveView] = createSignal<CompanionView>("now");
      const [eventVersion, setEventVersion] = createSignal(0);
      const [interrupted, setInterrupted] = createSignal(false);
      const reducedMotion = useReducedMotion();
      let readyTimer: ReturnType<typeof setTimeout> | undefined;

      const clearReadyTimer = () => {
        if (readyTimer !== undefined) clearTimeout(readyTimer);
        readyTimer = undefined;
      };

      const selectState = (selected: unknown) => {
        clearReadyTimer();
        const next = transitionPresentationState(previewState(), {
          type: "SELECT",
          state: normalizePresentationState(selected),
        });
        setPreviewState(next);
        if (PREVIEW_BUILD && next === "READY") {
          readyTimer = setTimeout(() => {
            setPreviewState((current) =>
              transitionPresentationState(current, { type: "READY_TIMEOUT" }),
            );
            readyTimer = undefined;
          }, READY_PREVIEW_MS);
        }
      };

      createEffect(() => {
        if (PREVIEW_BUILD) return;
        const session = props.session;
        const sessionID = session.id;
        const data = session.server.data;
        // Read route identity here so a new visit/location replaces subscriptions.
        session.key;
        session.visit;
        session.directory;
        setInterrupted(false);
        setEventVersion((current) => current + 1);

        const unsubscribe = subscribeGuiSessionEvents(data, sessionID, {
          refresh: () => setEventVersion((current) => current + 1),
          started: () => {
            setInterrupted(false);
            setEventVersion((current) => current + 1);
          },
          interrupted: () => {
            setInterrupted(true);
            setEventVersion((current) => current + 1);
          },
        });

        onCleanup(() => {
          for (const dispose of unsubscribe) {
            try {
              dispose();
            } catch {
              // A presentation cleanup failure must not affect host execution.
            }
          }
        });
      });

      const liveState = createMemo(() => {
        eventVersion();
        const session = props.session;
        const data = session.server.data;
        if (session.pending || data.session.message.loading(session.id)) return "UNKNOWN";
        const messages = data.session.message.list(session.id);
        if (messages.length === 0) return "UNKNOWN";
        return deriveGuiPresence(messages, { interrupted: interrupted() });
      });
      const historyView = createMemo(() => {
        eventVersion();
        const session = props.session;
        const data = session.server.data;
        if (session.pending || data.session.message.loading(session.id)) {
          return { kind: "unavailable" as const, rows: [] };
        }
        const messages = data.session.message.list(session.id);
        const rows = deriveGuiHistory(messages);
        if (rows === null) return { kind: "unavailable" as const, rows: [] };
        return {
          kind: rows.length === 0 ? "empty" as const : "available" as const,
          rows,
        };
      });
      const state = () => PREVIEW_BUILD ? previewState() : liveState();

      onCleanup(clearReadyTimer);

      return (
        <section
          class="devlog-companion"
          aria-label={ctx.t("panel.title")}
          data-preview={PREVIEW_BUILD ? "simulated" : undefined}
          style={{
            display: "flex",
            "flex-direction": "column",
            gap: "12px",
            padding: "12px",
            color: "inherit",
          }}
        >
          <header
            class="devlog-companion__identity"
            style={{ display: "flex", "align-items": "center", gap: "8px" }}
          >
            <ctx.ui.Icon name={ctx.icons.devlog} size="normal" />
            <h2 style={{ margin: "0", "font-size": "1rem" }}>
              {ctx.t("panel.title")}
            </h2>
          </header>
          <nav
            aria-label="Companion views"
            role="group"
            style={{ display: "flex", gap: "8px", "border-bottom": "1px solid var(--border-base, currentColor)" }}
          >
            <For each={HISTORY_VIEWS}>
              {(view) => (
                <button
                  type="button"
                  id={`devlog-companion-tab-${view}`}
                  aria-pressed={activeView() === view}
                  onClick={() => setActiveView(view)}
                  style={{
                    color: "inherit",
                    background: "transparent",
                    border: "0",
                    "border-bottom": activeView() === view
                      ? "2px solid var(--text-accent, currentColor)"
                      : "2px solid transparent",
                    padding: "6px 8px",
                    cursor: "pointer",
                    opacity: activeView() === view ? "1" : "0.7",
                  }}
                >
                  {view === "now" ? "Now" : "History"}
                </button>
              )}
            </For>
          </nav>
          <Show when={activeView() === "now"}>
            <div
              id="devlog-companion-panel-now"
              class="devlog-companion__now"
              style={{ display: "flex", "flex-direction": "column", gap: "12px" }}
            >
              <div
                class="devlog-companion__presence"
                style={{ display: "flex", "align-items": "center", gap: "8px" }}
              >
                <PresenceAvatar state={state()} reducedMotion={reducedMotion()} />
                <p
                  role="status"
                  aria-live="polite"
                  style={{ margin: "0", "font-weight": "600" }}
                >
                  {PREVIEW_BUILD ? `Simulated preview · ${state()}` : state()}
                </p>
              </div>
              <p style={{ margin: "0", "line-height": "1.4" }}>
                {PREVIEW_BUILD
                    ? "Simulated only. No live DevLog activity or connectivity is observed."
                  : state() === "IDLE"
                    ? "No recognized DevLog activity is currently observed in this session."
                    : state() === "WORKING"
                      ? "DevLog activity or a task is in progress."
                      : state() === "READY"
                        ? "A successful DevLog task outcome was observed."
                        : state() === "ERROR"
                          ? "A DevLog tool or task failure was observed."
                          : "Current-session evidence is unavailable or inconclusive."}
              </p>
            </div>
          </Show>
          <Show when={activeView() === "history"}>
            <div
              id="devlog-companion-panel-history"
              aria-live="polite"
              class="devlog-companion__history"
              style={{ display: "flex", "flex-direction": "column", gap: "8px" }}
            >
              <h3 style={{ margin: "4px 0", "font-size": "0.95rem" }}>
                Recent DevLog activity
              </h3>
              <Show
                when={!PREVIEW_BUILD}
                fallback={<p style={{ margin: "0", opacity: "0.7" }}>History is unavailable in this preview build.</p>}
              >
                <Show
                  when={historyView().kind !== "unavailable"}
                  fallback={<p style={{ margin: "0", opacity: "0.7" }}>Current session history is loading or unavailable.</p>}
                >
                  <Show
                    when={historyView().kind !== "empty"}
                    fallback={<p style={{ margin: "0", opacity: "0.7" }}>No recognized DevLog activity in the inspected transcript window.</p>}
                  >
                    <ol
                      aria-label="Recent DevLog activity"
                      style={{
                        margin: "0",
                        padding: "0",
                        "list-style": "none",
                        display: "flex",
                        "flex-direction": "column",
                        gap: "8px",
                      }}
                    >
                      <For each={historyView().rows}>
                        {(row) => {
                          const toolLabel = row.source === "direct-tool"
                            ? row.toolName ?? "DevLog tool"
                            : `Code Mode · ${(row.toolNames ?? []).join(", ")}${
                                row.additionalToolNameCount
                                  ? ` · +${row.additionalToolNameCount} more tool names`
                                  : ""
                              }`;
                          const hostLabel = row.platformOutcome === "RUNNING"
                            ? "Host execution running"
                            : row.platformOutcome === "COMPLETED"
                              ? "Host execution completed"
                              : row.platformOutcome === "ERROR"
                                ? "Host execution error"
                                : "Host execution state unknown";
                          return (
                            <li
                              style={{
                                display: "flex",
                                "flex-direction": "column",
                                gap: "3px",
                                padding: "8px",
                                border: "1px solid var(--border-base, currentColor)",
                                "border-radius": "6px",
                              }}
                            >
                              <span style={{ "font-weight": "600", "overflow-wrap": "anywhere" }}>
                                {toolLabel}
                              </span>
                              <span style={{ "font-size": "0.85em", opacity: "0.78" }}>
                                {hostLabel}
                                {row.taskOutcome
                                  ? ` · ${
                                      ["SUBMITTED", "PENDING", "RUNNING", "POLL_TIMEOUT"]
                                        .includes(row.taskOutcome)
                                        ? "Last reported DevLog task status"
                                        : "Reported DevLog task status"
                                    }: ${row.taskOutcome}`
                                  : row.source === "direct-tool" && row.platformOutcome === "COMPLETED"
                                    ? " · DevLog task outcome unavailable"
                                    : row.source === "code-mode"
                                      ? " · Nested outcome unknown"
                                      : ""}
                              </span>
                              <Show when={row.observedAt !== undefined || row.durationMs !== undefined}>
                                <span style={{ "font-size": "0.8em", opacity: "0.65" }}>
                                  {row.observedAt !== undefined
                                    ? `Observed ${formatObservedTime(row.observedAt)}`
                                    : ""}
                                  {row.observedAt !== undefined && row.durationMs !== undefined ? " · " : ""}
                                  {row.durationMs !== undefined
                                    ? `Duration ${formatDuration(row.durationMs)}`
                                    : ""}
                                </span>
                              </Show>
                            </li>
                          );
                        }}
                      </For>
                    </ol>
                  </Show>
                </Show>
              </Show>
            </div>
          </Show>
          {PREVIEW_BUILD ? (
            <Show when={activeView() === "now"}>
              <fieldset class="devlog-companion__preview">
                <legend>Simulated preview — no live DevLog data</legend>
                <div
                  role="group"
                  aria-label="Select simulated presence state"
                  style={{ display: "flex", "flex-wrap": "wrap", gap: "6px" }}
                >
                  <For each={PRESENTATION_STATES}>
                    {(candidate) => (
                      <button
                        type="button"
                        aria-pressed={state() === candidate}
                        onClick={() => selectState(candidate)}
                        style={{
                          color: "inherit",
                          background: "transparent",
                          border: "1px solid currentColor",
                          "border-radius": "4px",
                          padding: "4px 7px",
                          cursor: "pointer",
                        }}
                      >
                        {candidate}
                      </button>
                    )}
                  </For>
                </div>
              </fieldset>
            </Show>
          ) : null}
        </section>
      );
    }

    ctx.add(Panel, {
      id: "activity",
      region: "side",
      transient: true,
      list: (input) => input.open.includes(activityTab.id) ? [activityTab] : [],
      render: (props) => <CompanionPanel session={props.session} />,
    });

    ctx.add(MenuItem, (): MenuItemDefinition => {
      const session = ctx.sessions.current();
      return {
        menu: "session.panel",
        id: "open",
        title: ctx.t("panel.open"),
        opened: !!session && ctx.layout.state(panelKey, session) !== "closed",
        run: () => {
          if (session) ctx.layout.open(panelKey, session);
        },
      };
    });
  },
});
