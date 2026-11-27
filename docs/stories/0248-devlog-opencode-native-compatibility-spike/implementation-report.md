# Implementation Report — Story 0248

## Repository State

- Branch: `story/0248-opencode-native-compatibility-spike`
- Base HEAD before delivery: `f83fbd6cf260d7cdef1d74ee12b991d595b639d2`
- Pre-delivery worktree: Story 0248 artifacts, ADR-084, and package files were
  untracked; no existing tracked changes were overwritten.

## Governance

- Governing decision: ADR-084, plus its referenced trust, identity, failure,
  context, and observability decisions.
- Authorized Story: Story 0248, implementation plan explicitly approved by the
  human owner; implementation explicitly authorized in this session.
- Runtime experiments: explicitly authorized and partially performed on
  2026-11-26, first in a disposable profile and later in the normal CLI profile.
  The later authorization allowed configured MCP use, including live DevLog.
- DevLog preflight: the latest `devlog_story_agent` call completed for
  `devlog-ai`, task `f540d018-182b-4167-926e-8b675cb91090`, without grounded
  Story 0248 evidence. Project freshness was `PARTIALLY_FRESH`/`STALE`; context
  selection was truncated. No verified DevLog Story UUID was available. The
  repository Story and ADR remain the local governing sources.

## Files Changed

- `opencode-mod/devlog-companion/` — isolated server/GUI package, pinned
  manifest/lockfile, deterministic projection and decoder, typed RPC contract,
  Desktop panel and composer pill, route-scope state helpers, unit tests, build
  configuration, and package notes.
- `docs/stories/0248-devlog-opencode-native-compatibility-spike/story.md` —
  implementation status and links to protocol/report.
- `docs/stories/0248-devlog-opencode-native-compatibility-spike/implementation-plan.md`
  — records that implementation authorization was subsequently granted while
  keeping runtime authorization as a separate gate.
- `docs/stories/0248-devlog-opencode-native-compatibility-spike/runtime-validation-protocol.md`
  — authorized-runtime prerequisites, cases, evidence limits, and cleanup.
- `docs/stories/0248-devlog-opencode-native-compatibility-spike/implementation-report.md`
  — this report.

The untracked ADR-084, Repository Analysis, and approved plan were pre-existing
governing inputs; ADR-084 and the analysis were not modified in this
implementation pass.
No Claude Companion, backend, MCP/REST contract, Preflight Gate, or global
OpenCode configuration files were changed.

## Implementation Summary

The package observes only the allowlisted DevLog tools, stores bounded sanitized
metadata in memory, and keeps OpenCode tool outcome, MCP error outcome, explicit
MCP operation ID, DevLog task ID/status, and connectivity as separate facts.
Unmatched or incomplete observations remain unknown/incomplete; raw arguments,
prompts, outputs, and error messages are not retained. The Desktop panel and
composer pill request the currently routed session snapshot through that
session's server. The RPC resolves the session and checks its project,
location, and workspace against the plugin instance; mismatches fail closed.
There is no cross-session event broadcast. These checks establish location
consistency only, not caller-to-session authorization; host authorization
remains unverified and Story isolation acceptance is blocked on that evidence.
The GUI keeps only one current-route projection, clears it on route changes, and
accepts a response only while that route's request remains active.

The Preflight Gate remains untouched. The GUI/RPC scope and exact host pairing
still require isolated runtime validation; no compatibility or governance claim
is made from the static implementation.

## Tests and Commands

Executed in `opencode-mod/devlog-companion/`:

```text
npm install --package-lock-only --ignore-scripts --no-audit --no-fund
npm ci --ignore-scripts --no-audit --no-fund
npm test
npm run typecheck
npm run build
npm pack --dry-run
node --input-type=module -e "import('./src/server.js')..."
node --check src/model.js && node --check src/server.js && node --check src/rpc.js && node --check src/ui-state.js
```

- Unit tests: 15 passed.
- Typecheck: passed.
- GUI bundle build: passed.
- Package dry-run: passed; 11 files included (server, RPC, GUI bundle, source,
  README); `node_modules` and tests excluded.
- Server entry import: passed; plugin `setup` was not invoked.
- Runtime: isolated authenticated CLI server startup and config-source discovery
  passed. In the first attempt the log records `mcp connected server=devlog
  tools=2`; however, the CLI/API MCP status surfaces were empty.
- Follow-up synthetic MCP diagnostic: the config was parsed in a fresh isolated
  profile, but `mcp list` reported no configured servers and the explicit API
  connect endpoint returned `McpServerNotFoundError`/404, even after creating a
  session. Project-only and global-plus-project configurations behaved the
  same. Discovery/connect remains unresolved.
- The Companion's earlier runtime load attempt failed with
  `TypeError: Object.entries requires that input parameter not be null or
  undefined`. The unchanged Gate was discovered and a load attempt was logged,
  but successful setup or behavior was not confirmed in that run.
- After the user explicitly authorized the normal CLI profile and live DevLog,
  `mcp list` reported nine connected MCP servers, including DevLog. A
  temporary project-scoped config exposed the Companion to the active CLI host;
  `plugin list` discovered it alongside the existing Gate. The host log again
  recorded the Companion load failure above. The temporary config was removed,
  and a subsequent `plugin list` no longer included the Companion. The Gate
  hash remained unchanged. No tool call was executed through the Companion and
  no operations were made against the other connected MCPs.
- Tool behavior, caller authorization, Desktop rendering/pairing, and Gate
  decision behavior: **not evaluated**. Desktop was not launched.
- Follow-up isolated probe (2026-11-26): an instrumented temporary copy under
  `/tmp/opencode/devlog-companion-0248-probe-zbbb6yu5` showed module imports,
  setup, and both tool-hook registrations were reached; failure occurred at
  `ctx.rpc.register`. The RPC schema declaration requires `events`, while
  `opencode-mod/devlog-companion/src/rpc.js` omits it. With only
  `events: {}` added to the temporary copy, setup and RPC registration completed
  under CLI 2.4.1 with both `@opencode/plugin` 2.4.1 and 2.4.0. The run then
  stopped at the intentionally unavailable model, before any provider call.
  This confirms the missing-field defect and rules out SDK version mismatch
  alone as its cause. Temporary files/logs were removed; no implementation
  source was changed.
- Authorized correction (2026-11-26): added the required empty `events` map to
  `DevLogCompanionRpc`, changed its regression assertion, and expanded the
  TypeScript check to include `src/rpc.js`. The negative typecheck with the map
  removed failed with `TS2345` for missing required property `events`.
- Post-correction validation: 15 tests passed; typecheck, build, and package
  dry-run passed. In a fresh isolated profile, the checked-in package was
  discovered by CLI 2.4.1 with SDK 2.4.0; no plugin-load failure was logged,
  and execution reached the intentionally unavailable model. No MCP or provider
  credentials were configured. Temporary profile and logs were removed.
- Isolated RPC smoke test: session creation alone did not activate plugin setup;
  a synthetic prompt against the intentionally unavailable model activated the
  location plugin and stopped before provider dispatch. The plugin list then
  included the Companion. `snapshot` returned the expected empty `UNKNOWN`
  result for an existing session and an unknown session. Missing or non-string
  `sessionID` inputs were rejected as `rpc.invalid_input`. An extra input
  property was unexpectedly accepted with HTTP 200 and the same snapshot,
  despite the declared JSON Schema `additionalProperties: false`.
- Follow-up strict-input correction: an instrumented temporary copy showed the
  host strips undeclared JSON Schema properties before invoking the handler.
  `src/rpc.js` now uses the SDK-supported Standard Schema validator to reject
  any extra key and malformed `sessionID` input before handler execution; the
  validated handler input is rebuilt as `{ sessionID }`.
- Final isolated RPC validation: valid and unknown session inputs returned the
  expected empty `UNKNOWN` snapshot; extra-key, missing-key, and non-string
  inputs were rejected as `rpc.invalid_input`. The plugin appeared in the
  isolated location plugin list. The synthetic prompt used a deliberately
  unavailable model and stopped before provider dispatch; no MCP or provider
  credentials were configured. All disposable state was removed.
- Final package validation: 15 tests passed; typecheck, build, and package
  dry-run passed.

## Known Limitations and Risks

- The V2 Desktop/service CLI pairing was observed, but the SDK actually loaded
  by each host component is not established.
- `connectivity` remains `UNKNOWN`; no additional MCP/DevLog health request is
  made.
- OpenCode plugin call/result hooks cannot prove dispatch when the after hook is
  absent; such calls remain incomplete.
- The package is experimental/private. The required RPC map is now present and
  the checked-in package passed an isolated load check. The earlier
  “no hook event” notes below are historical and are superseded by the
  subsequently confirmed Code Mode hook run.
- Strict rejection of extra and malformed RPC inputs is covered by the
  Standard Schema validator and isolated host probes. Caller-to-session
  authorization is still unproven.
- Hook runtime feasibility follow-up: the allowed profile is constrained to no
  MCP and no provider calls. The V2 client/API exposes no direct tool-execution
  operation; prompt-based tool dispatch requires the model/provider path, while
  shell execution would not exercise a DevLog-named MCP tool. No hook events
  were generated and no boundary was bypassed. The existing deterministic hook
  and model fixtures cover before-only, completed result, tool error, MCP error
  decoding, missing-terminal expiry to `INCOMPLETE`, malformed envelopes,
  immutability, and cleanup. `npm test` passed all 15 tests. These fixture
  results do not establish host-runtime hook delivery or interruption behavior.
- Subsequent isolated attempts used only a loopback deterministic model stub,
  synthetic local tools, and `bwrap --unshare-net`; no real credentials or
  external provider were used. The MCP fixture did not receive tool calls. A
  separately registered local `devlog_story_agent` fixture appeared in the
  plugin inventory, but model requests still contained no tool definitions.
  These attempts therefore did not invoke hooks. The later Code Mode run
  documented below supersedes that limitation for the tested synthetic
  success/error paths. The model stub did receive loopback requests.
  Temporary profiles were removed. The Gate copy matched the unchanged source
  hash `5022ae33ab17f1057587efd1a0bf771e058b20d9542424d59f309524f85c4250`.
- The next isolated dispatch diagnostic did send one prompt to the loopback
  model. Its request contained built-in tool names (including `execute`) but
  omitted `devlog_story_agent`; the fixture's registration evidence file was
  absent and it was never called. Thus plugin setup/tool exposure is not yet
  established, and no hook event was produced. Following the stop condition,
  no further dispatch or MCP scenario was attempted. The temporary profile was
  removed and the Gate hash stayed unchanged. A cleanup defect left the CLI
  process after profile removal; its deleted cwd/log/database descriptors
  identified it as belonging to this probe. It received `SIGTERM` and is now a
  non-executing zombie. A pre-existing partial profile was left untouched.
- In a corrected follow-up, the fixture setup completed and both its transform
  callback and effective registry read confirmed `devlog_story_agent` was
  registered. The earlier fixture `TypeError` came from treating SDK
  `{ data: [...] }` list responses as arrays. Nevertheless, the two model
  requests for the single session prompt contained no `tools` field at all;
  the stub therefore made no `execute` or local-tool call and no hook was
  observed. The Companion, fixture, and Gate appeared in the host plugin
  inventory, which proves discovery, not Gate behavior. The fresh profile and
  process were stopped and removed; the old partial profile was left untouched.
  Gate hash remained unchanged. The cause of tool omission remains unresolved.
- Applied the phase-observation policy in one further disposable run. The
  synthetic fixture recorded successful setup and effective registration of
  `devlog_story_agent`; `build` was primary with catch-all `allow`. Its model
  inventory omitted `fixture/runner`, although both loopback model requests
  named `runner`. Both requests also omitted the `tools` field. The prompt
  returned without a client error, but no tool or hook ran. This localizes the
  unknown to model-request assembly after setup/registration; the inventory
  discrepancy is a lead, not a confirmed cause. The Companion, fixture, and
  Gate appeared in the plugin list, which establishes discovery only. No raw
  prompt or payload was logged; the new profile/process were removed and the
  Gate hash stayed unchanged.
- At that stage, the unchanged Preflight Gate was discovered and its loading
  initiated by the isolated V2 host, but behavior was `NOT_EVALUATED`; this
  historical observation is superseded by the active-host negative-handoff
  result below.
- A local API server could not bring up its loopback interface inside the
  namespace (`RTNETLINK answers: Operation not permitted`); access did work via
  the namespace's local network path. This does not establish Desktop isolation.
- Earlier MCP discovery/status behavior was inconsistent across runs; the
  corrected V2 `mcp.servers` project configuration subsequently connected a
  local stdio fixture. The corrected dispatch and hook results are recorded
  below and supersede the earlier no-dispatch observations for the tested
  2.4.1/2.4.0 tuple. They do not establish behavior for other configurations.
- Partial disposable-profile cleanup removed temporary fixtures/configuration,
  the Gate copy, generated passwords/session records, generated database/cache
  files, and launch scripts. A sanitized summary and runtime logs remain in the
  profile; complete profile restoration/destruction was not performed or
  verified.
- Several npm transitive dependencies emitted deprecation warnings during the
  clean install; no runtime security assessment was performed.
- Follow-up verification (2026-11-26): `npm run build` passed (15 tests,
  TypeScript check, GUI build), and `npm run pack:dry-run` passed. A disposable
  CLI host, with an unreachable loopback-only model endpoint and no real
  credentials, activated the Companion. Authenticated RPC returned empty
  `UNKNOWN` snapshots for the same and unknown session IDs, rejected an
  additional input property as `rpc.invalid_input`, and an unauthenticated RPC
  request was rejected by the host as `UnauthorizedError`. These results
  establish host authentication and fail-closed responses for this probe, not
  caller-to-session authorization: no positive workspace-scoped authorization
  case or cross-principal case was demonstrated.
- The unchanged Gate's exported decision helpers were exercised with synthetic
  inputs in an isolated Node harness: an `explore` delegation without the
  required handoff was blocked with `DEVLOG_PREFLIGHT_REQUIRED`; a preflight
  result classified `COMPLETED` plus the required handoff was allowed. This is
  helper-level behavior only, not proof that the active OpenCode host enforces
  the Gate on a real nested delegation.
- A separate isolated host confirmed the Companion, fixture, audit observer,
  and unchanged Gate all active after bootstrap. The synthetic missing-handoff
  and completed-handoff prompts drained, but neither the synthetic preflight
  fixture nor audit observer recorded tool events and no Gate decision marker
  appeared. Because the tool dispatch itself was not evidenced, actual Gate
  enforcement remains untested; this is neither a pass nor a Gate defect
  finding.
- Follow-up MCP probe (2026-11-26): the earlier `McpServerNotFoundError` came
  from a malformed V2 project config that placed the server name directly
  under `mcp`; the documented `mcp.servers` shape then connected a local stdio
  fixture and completed `tools/list`. With `codemode:true`, a prompt exposed
  the outer `execute` schema, but the attempted
  `tools.devlog.get_engineering_context(...)` produced no fixture `tools/call`
  or Companion hook. Project-config discovery is therefore confirmed for the
  corrected shape. This no-dispatch attempt is superseded by the corrected
  32768-token dispatch result below. No real DevLog MCP was contacted.
- A `codemode:false` direct-tool control exposed the normalized
  `devlog_get_engineering_context` tool to the loopback model stub. Although the
  stub returned a synthetic function-call response, the fixture observed no
  `tools/call` and the Companion observed no hook. No platform tool event or
  terminal tool-message state was captured, so this is an inconclusive dispatch
  attempt, not evidence of an OpenCode or MCP defect. The direct-MCP path also
  remains **UNKNOWN**; no real DevLog MCP was contacted.
- Corrected dispatch follow-up (2026-11-26): metadata-only session events showed
  the previous no-dispatch probes were entering automatic compaction because
  the synthetic model advertised a 4096-token context. With the disposable
  model context raised to 32768, direct MCP (`codemode:false`) emitted host
  tool-called/success events, invoked the local fixture's `tools/call`, and
  produced Companion before/after observations. Code Mode (`codemode:true`)
  likewise invoked the fixture through `tools.devlog.get_engineering_context`;
  nested `codemode:true` and outer `execute` hook outcomes were observed. The
  fixture returned only synthetic `UNKNOWN` task evidence. These checks used
  `bwrap --unshare-net`, a loopback-only model and local MCP fixture; no real
  DevLog, credentials, or external provider were used. The 4096-token finding
  diagnoses the earlier harness; it does not explain every possible no-dispatch
  configuration.
- Gate-host follow-up (2026-11-26): the unchanged installed Gate and a
  metadata-only observer were active. The V2 `subagent` event exposed input
  keys `agent`, `description`, and `prompt`; the Gate predicate recognizes
  `subagent_type` / `subagentType` instead. On the missing-handoff case, no
  `DEVLOG_PREFLIGHT_REQUIRED` marker appeared, the tool completed, and one
  child session was created. The child was configured with all tools denied;
  its only possible model endpoint was the loopback stub. This confirms that
  Gate enforcement did not block this V2 `agent:"explore"` request on the tested
  tuple. It is a governance blocker, not authorization to modify the Gate in
  Story 0248; the positive Gate case was not attempted after the negative
  result. A separate Gate-compatibility change/Story is required.
- The combined interruption/concurrency runtime harness exited non-zero before
  any loopback model request, fixture call, or hook event. It supplies no new
  lifecycle evidence. Deterministic package tests remain the concurrency
  evidence; full host interruption and runtime concurrency remain
  **NOT_EVALUATED**. No workspace/principal or reconnect RPC scenario was run in
  this follow-up.
- Non-Gate follow-up (2026-11-26): DevLog preflight task
  `fa65ce16-b46a-4577-b8ee-2b643b36bb53` ended with
  `STRUCTURAL_CONTRACT_ERROR`; the selected context was stale/partially fresh,
  truncated, and had no verified Story UUID. No historical conclusion was
  based on that response.
- An additional fresh, marker-owned CLI 2.4.1 host probe ran with
  `bwrap --unshare-net`, an unreachable `127.0.0.1:1` model endpoint, and no
  external service. Through the same authenticated RPC instance, a request for
  a known session in a different project directory returned an empty `UNKNOWN`
  snapshot, as did an unknown session ID. The probe observed distinct project
  IDs for the two directories. This confirms fail-closed behavior for that
  cross-project mismatch only; it does not prove caller-to-session
  authorization, same-project workspace isolation, or a positive scoped
  snapshot.
- A separate lifecycle/concurrency harness attempt exited before the loopback
  model received a request and before fixture/Companion events. It provides no
  new interruption or concurrency evidence; the bounded interruption result
  still has no observed terminal session outcome, and runtime concurrency stays
  **NOT_EVALUATED**.
- Cleanup distinction: every newly created marker-owned profile in these
  follow-ups was stopped/reaped and removed, with its owned path checked absent.
  The earlier partial profile
  `/tmp/opencode/devlog-companion-0248-runtime-20261126` pre-existed this
  follow-up and was preserved under the protocol's explicit rule not to alter
  pre-existing partial profiles without separate authorization. Its prior
  partial restoration/destruction remains unresolved; I did not delete it.
- Desktop remained unlaunched under the active constraint, so rendering and
  interaction remain **NOT_EVALUATED**. The Gate was not changed.
- DevLog context preflight for this follow-up completed, but the selected
  projection omitted the requested Story 0248 files and reported project
  freshness `PARTIALLY_FRESH`/`STALE`, truncated selection, and
  `resolutionState: UNKNOWN`. It establishes no Story-specific prior
  constraints; current repository artifacts govern this report. The later
  non-Gate preflight is separately recorded above and ended in
  `STRUCTURAL_CONTRACT_ERROR`.

## Documentation Reconciliation

- Updated `runtime-validation-protocol.md` and this report with the additional
  isolated cross-project RPC result, the failed lifecycle/concurrency harness
  attempt, and the cleanup boundary for newly created versus pre-existing
  partial profiles.
- No package source or test change was justified by this follow-up: it produced
  no new lifecycle dispatch evidence and no proven implementation defect outside
  the separately scoped Gate issue.

## Vault Outcome

- Obsidian vault: not consulted for this runtime-only follow-up; no curated
  transverse note was identified as necessary to interpret the host evidence.
- Outcome: **no vault action**. The findings are tuple-specific runtime
  observations and do not establish a reusable cross-project knowledge change.

## Readiness

**Owner-directed delivery in the current state, with runtime gaps retained; no
compatibility certification is made.** On 2026-11-26 the owner instructed that
the Story be closed as-is rather than launching Desktop or changing the Gate.
This is a delivery disposition, not evidence that unmet runtime criteria passed.
The independent whole-Story review returned `NEEDS CHANGES` because applicable
acceptance criteria remain unverified; a later review of the evidence-document
updates returned `PASS` and did not supersede that whole-Story finding.

The RPC registration defect is corrected and the checked-in package passed an
isolated load check. Strict RPC input rejection is verified for this CLI/SDK
tuple. Isolated Code Mode dispatch confirms nested Companion hook ordering for
successful execution and controlled nested-tool failure, and the corrected MCP
probes confirm direct and Code Mode fixture delivery. A bounded interruption
probe observed `before` without a terminal `after`, but did not capture the
platform outcome. Cross-project RPC requests failed closed in the added probe,
but caller-to-session authorization is still unproven. The unchanged Gate did
not block the tested V2 `agent:"explore"` missing-handoff request; this remains
a governance blocker assigned to separate Gate-compatibility work. Runtime
qualification remains incomplete for Desktop, caller-to-session authorization,
complete interruption lifecycle, runtime concurrency, and cleanup/restoration
of the historical partial profile. Newly created follow-up profiles were
removed and verified. The corrected runtime probes used
`experimental.full_codemode` and a 32768-token model context only in disposable
configuration. Pull-request validation remains required before merge.

At the time this report was first drafted, no commit or push had been created.
Delivery state is tracked separately by the resulting Git history and pull
request; human PR validation remains required before merge.

## Desktop visual milestone follow-up (2026-11-27)

- Product focus was redirected to the first static OpenCode Desktop Companion
  panel. The package was narrowed to a GUI-only export for this milestone:
  its export map exposes only `./gui-experimental`, and its package file allowlist
  excludes the server observer, RPC contract, and projection code. Those source
  files remain in the repository for later authorized integration work. The
  panel now presents the DevLog mark, static `IDLE`, and a closable tab with an
  OpenCode side-panel launcher. It does not call DevLog or register/call RPC.
- Validation: 16 Node tests passed (including a GUI-only export/package
  allowlist regression); TypeScript check, GUI bundle build, and `npm pack
  --dry-run` passed. The dry-run package contained only five files and no server
  or RPC source.
- A Desktop 2.4.2 launch was attempted with a temporary HOME/XDG profile and a
  separate Electron `--user-data-dir`. The window startup failed at the
  Wayland/Vulkan GPU initialization boundary. The Desktop log also reported
  loading its V2 background service; a read-only bundled-CLI plugin-list probe
  then failed because the standard service port `49374` was already occupied by
  the pre-existing normal-profile service. This shows that the proposed
  environment variables and Electron data-directory flag did not establish
  isolation from the existing service. The temporary Desktop did not reach
  plugin/panel validation. No existing service was stopped, reconfigured, or
  signaled.
- No Companion panel load, rendering, opening, closing, or Claude runtime
  regression result is claimed. Desktop visual validation remains
  **NOT_EVALUATED**; the GUI API/version pairing remains experimental and
  unqualified. The temporary profile was removed. The normal Desktop install,
  normal OpenCode profile, Claude Companion, Preflight Gate, and Story 0250
  evidence were not intentionally modified. No OpenCode runtime harness
  qualification or real DevLog operation was performed.

## Owner-confirmed Desktop visual milestone update (2026-11-27)

- After the report above was written, the owner authorized manual installation
  in the existing OpenCode profile. The global
  `~/.config/opencode/opencode.jsonc` was backed up to
  `~/.config/opencode/opencode.jsonc.bak-companion-20261127-024432`, and the
  local Companion directory was added to its existing `plugins` array. The
  bundled OpenCode 2.4.2 CLI reported `Configuration reloaded`; no service
  restart or port change was made.
- The active server's read-only plugin API reported `devlog-companion` as
  `active`, with `gui: true` and no server feature. The other three local
  plugin records remained active.
- The owner then confirmed: “le plugin fonctionne et que Devlog apparaît bien
  dans le panneau latéral.” This is owner-reported visual evidence that the
  plugin works and appears in the sidebar, and supersedes the earlier
  `NOT_EVALUATED` statement for basic discovery/visibility. No screenshot was
  captured by the agent. This confirmation does not independently establish
  all individual interactions, a full version compatibility matrix, live MCP
  integration, or runtime qualification; the GUI API remains experimental.
- The approved global plugin entry and backup remain in place for manual
  testing. Story 0250 and `runtime-tests/` remain preserved and suspended.
