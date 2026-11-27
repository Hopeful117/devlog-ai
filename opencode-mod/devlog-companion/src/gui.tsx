import {
  MenuItem,
  Panel,
  Plugin,
  type MenuItem as MenuItemDefinition,
  type PanelTab,
} from "@opencode/plugin/gui";
import { For, Show, createSignal, onCleanup } from "solid-js";
import logo from "./devlog-mark.svg?raw";
import {
  PRESENTATION_STATES,
  READY_PREVIEW_MS,
  normalizePresentationState,
  presentationDescriptor,
  transitionPresentationState,
} from "./presentation.js";

const PREVIEW_BUILD = import.meta.env.MODE === "preview";

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
  const visual = () =>
    presentationDescriptor(props.state, props.reducedMotion);
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

    function CompanionPanel() {
      const [state, setState] = createSignal("IDLE");
      const reducedMotion = useReducedMotion();
      let readyTimer: ReturnType<typeof setTimeout> | undefined;

      const clearReadyTimer = () => {
        if (readyTimer !== undefined) clearTimeout(readyTimer);
        readyTimer = undefined;
      };

      const selectState = (selected: unknown) => {
        clearReadyTimer();
        const next = transitionPresentationState(state(), {
          type: "SELECT",
          state: normalizePresentationState(selected),
        });
        setState(next);
        if (PREVIEW_BUILD && next === "READY") {
          readyTimer = setTimeout(() => {
            setState((current) =>
              transitionPresentationState(current, { type: "READY_TIMEOUT" }),
            );
            readyTimer = undefined;
          }, READY_PREVIEW_MS);
        }
      };

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
              : "Presentation only. Live DevLog activity is not connected."}
          </p>
          {PREVIEW_BUILD ? (
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
          ) : null}
        </section>
      );
    }

    ctx.add(Panel, {
      id: "activity",
      region: "side",
      transient: true,
      list: (input) => input.open.includes(activityTab.id) ? [activityTab] : [],
      render: () => <CompanionPanel />,
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
