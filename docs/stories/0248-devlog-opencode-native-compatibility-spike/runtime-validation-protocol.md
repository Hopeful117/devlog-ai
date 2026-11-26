# Runtime Validation Protocol — Story 0248

## Status

**Partially evaluated; isolated Code Mode hook delivery and success/error
outcomes now observed.** The corrected package loaded under CLI 2.4.1 with its
declared SDK 2.4.0. Earlier isolated RPC calls and strict schema rejection were
also confirmed. A synthetic local tool exercised nested `execute.before` and
`execute.after` hooks for success and controlled tool failure; an interruption
probe recorded `before` events without a terminal `after` during its bounded
observation window. A local stdio MCP fixture later connected through the
correct V2 `mcp.servers` configuration and completed `tools/list`, but no
earlier probes observed `tools/call`. A later corrected local-model probe
confirmed both direct MCP and Code Mode dispatch and Companion hooks. A host
Gate probe then showed that the installed, unchanged Gate did not block a V2
`subagent` call using the observed `agent` input field; this is a governance
blocker for the tested tuple, not permission to modify the Gate in this Story.
Complete interruption outcome, runtime concurrency, positive caller-to-session
authorization, and Desktop rendering remain unevaluated. A later isolated
cross-project RPC request failed closed; this is not caller authorization proof.
No Desktop compatibility claim is made.

## Candidate under test

| Component | Candidate | Evidence / limitation |
|---|---|---|
| OpenCode legacy CLI | 1.18.11 | Read-only local inventory |
| OpenCode Desktop | 2.4.1 | Read-only local inventory |
| Desktop service CLI | 2.4.1 | Bundled service binary; observed running process |
| `@opencode/plugin` | 2.4.0 | Local package declaration and installed package; not proof of the SDK loaded by every host component |
| Node.js | 26.10.0 | Local development runtime; host plugin runtime remains to be evidenced |
| OS | Linux x86_64, Pop!_OS 24.04 | Read-only local inventory |
| OpenCode PTY helper | 0.6.0 | Auxiliary component, not treated as the plugin sidecar |

The pairing and GUI/RPC behavior are not certified by this inventory.

## Runtime observation policy

Each disposable runtime probe uses a fresh, marker-owned profile and records
phase-level JSONL metadata beneath that profile. Instrumentation is confined to
temporary fixture/plugin copies; it must not modify the repository Companion,
the installed Preflight Gate, global configuration, or user profile.

Record only the minimum needed to identify the failing boundary:

| Phase | Allowed observation |
|---|---|
| Host/config | CLI and SDK versions, isolated config source, startup/exit category |
| Plugin lifecycle | plugin ID, setup entered/completed, registration phase, sanitized error class/code |
| Tool registry | effective tool IDs, selected agent ID/mode, permission action/effect summary, selected model ID and `tools` capability |
| Model dispatch | request sequence, model ID, whether `tools` exists, tool names and schema property names; never messages or prompt content |
| Tool hooks | stage, tool name, Code Mode flag, terminal status and ephemeral call correlation ID |
| MCP | server/status and protocol method/category; never raw input, output, headers, or credentials |
| Cleanup | owned root marker, child PID/group, termination result, profile removal and Gate hash |

Do not retain prompts, repository payloads, raw tool arguments/results, headers,
passwords, credentials, full exception messages, or unredacted server logs.
Unexpected exceptions are reported with phase, error class, safe numeric/code
field when present, and a bounded/redacted top stack frame only if phase
markers cannot localize the fault. Observer code is best-effort and must not
change tool behavior.

Probes are gated: do not proceed from registration to model dispatch unless
setup completion is evidenced; do not proceed from dispatch to hook/MCP
scenarios unless the outgoing request exposes the intended tool path. Stop at
the first failed gate, preserve only a sanitized summary in the canonical
report, and destroy only the newly created profile after verifying its marker
and stopping/reaping its process group. Pre-existing partial profiles remain
untouched unless separately authorized.

## Runtime attempt (2026-11-26)

- Profile: `/tmp/opencode/devlog-companion-0248-runtime-20261126`.
- The CLI was run in a `bwrap` user/network namespace with HOME, all XDG
  directories, and TMPDIR beneath that profile. The API reported version
  `2.4.1` and its temporary path beneath the disposable profile. The host
  loopback interface could not be brought up in the namespace; the local API
  nevertheless became reachable and authenticated from within that namespace.
- `debug config` listed both the disposable project config and disposable XDG
  config. Server logs record plugin loading being initiated for the unchanged
  Gate copy and the Companion. This is discovery/load-attempt evidence only;
  successful setup, hook registration, and runtime behavior were not confirmed.
- Gate source and disposable copy both hash to
  `5022ae33ab17f1057587efd1a0bf771e058b20d9542424d59f309524f85c4250`.
- Historical logs from the first runtime attempt record
  `mcp connected server=devlog tools=2` for the synthetic fixture. In that same
  attempt the Companion failed to load with
  `TypeError: Object.entries requires that input parameter not be null or
  undefined`. The Gate had a loading-attempt log only; setup/behavior was not
  confirmed.
- In the follow-up diagnostic, a fresh isolated profile parsed a minimal
  `mcp.servers` configuration, but `mcp list` still reported
  `No MCP servers configured`. After creating a session, `/api/mcp` returned
  an empty list and `POST /api/experimental/mcp/{name}/connect` returned
  `McpServerNotFoundError`/404. Project-only and global-plus-project
  configurations behaved the same. Thus the historical handshake and current
  discovery/connect surfaces disagree; neither result is silently preferred.
- The minimal fixture did not pass its follow-up visibility/connect gate.
  Tool-call scenarios, Companion/Gate behavioral tests, RPC authorization, and
  Desktop pairing/rendering were not resumed. No live DevLog or real
  credentials were used; the network namespace prevented external connectivity.
- **Scope amendment (2026-11-26):** the user subsequently authorized evaluation
  in the normal OpenCode CLI profile, use of all configured MCP servers as
  necessary, and live DevLog calls. This superseded the earlier fixture-only
  restriction for the following bounded check; it did not authorize changes to
  the user's global configuration or Gate.
- In the normal CLI profile, `mcp list` reported nine servers connected,
  including `devlog`. A project-scoped temporary config added the local
  Companion source; `plugin list` discovered it alongside the existing Gate.
  The active server log then recorded `failed to load plugin` for the Companion
  with `TypeError: Object.entries requires that input parameter not be null or
  undefined`. No DevLog operation was run through the Companion.
- The temporary project config was removed immediately after the failed load;
  `plugin list` no longer listed the Companion. The Gate source hash remained
  unchanged. No tool operation was made against the other connected MCPs.
- A bounded DevLog Story Agent preflight completed under the new authorization
  (`aiTaskId=f540d018-182b-4167-926e-8b675cb91090`), but returned no grounded
  Story 0248 evidence; project freshness was `PARTIALLY_FRESH`/`STALE` with
  truncated context selection. Repository files remain authoritative for
  current runtime state.
- The separate GUI/Desktop, pairing, tool-hook scenarios, RPC authorization,
  and Gate decisions remain **NOT_EVALUATED**. Desktop was not launched.
- Partial cleanup removed the disposable project/XDG configuration, fixture,
  copied Gate, generated server passwords, session records, databases/cache
  files, and temporary launch scripts from this profile. A sanitized diagnostic
  summary and bounded runtime logs remain under its `evidence` directory. A
  complete profile restoration or destruction was not performed or verified.

## Follow-up isolated load probe (2026-11-26)

- A new disposable home, XDG config/data/cache/state, project, and copied
  Companion package were created under
  `/tmp/opencode/devlog-companion-0248-probe-zbbb6yu5`. No MCP servers,
  provider credentials, or Gate copy were configured. The temporary profile,
  generated session/database state, and probe logs were removed after the
  experiment; the normal profile and repository package were not modified.
- The probe ran the installed OpenCode CLI **2.4.1** with an intentionally
  unavailable model identifier so startup reached plugin setup but no model
  provider could be invoked. The command's eventual model-unavailable exit is
  not a plugin-load result.
- A temporary copy of the original RPC definition failed reproducibly at
  `ctx.rpc.register`, after both tool-hook registration calls had been reached.
  The captured stack entered the host RPC `register` path and threw
  `TypeError: Object.entries requires that input parameter not be null or
  undefined`.
- Repository SDK declarations (`@opencode/schema@2.4.0`,
  `dist/rpc.d.ts`) require `Rpc.PortableDefinition.events`; its `Rpc.define`
  implementation returns the supplied definition and does not synthesize that
  field. `opencode-mod/devlog-companion/src/rpc.js` defines `id` and `methods`
  but no `events`.
- Differential probe, all in temporary copies:

  | CLI | `@opencode/plugin` | RPC definition | Setup result |
  |---|---:|---|---|
  | 2.4.1 | 2.4.1 | original, no `events` | fails at RPC registration with the reported `Object.entries` TypeError |
  | 2.4.1 | 2.4.1 | temporary `events: {}` | reaches `setup-complete`; RPC registration resolves |
  | 2.4.1 | 2.4.0 | temporary `events: {}` | reaches `setup-complete`; RPC registration resolves |

- This isolates a **confirmed missing-field defect** in the Companion's RPC
  definition, not an SDK-version mismatch as the cause of this exception. It
  does not establish runtime compatibility beyond setup: no actual hook event,
  RPC invocation, authorization, Gate behavior, or Desktop surface was tested.
- The original package typecheck did not cover `src/rpc.js`; its tsconfig
  included only `src/gui.tsx`.

## Correction validation (2026-11-26)

- The authorized source correction adds `events: {}` to
  `DevLogCompanionRpc`, changes the contract test to assert an empty event map,
  and extends `tsconfig.json` to check `src/rpc.js`.
- A negative typecheck against a temporary copy with `events` removed failed
  with TypeScript `TS2345`, explicitly stating that required property `events`
  was missing. The temporary negative-test file was removed.
- `npm test`: 15 passed. `npm run typecheck`, `npm run build`, and
  `npm pack --dry-run` passed.
- A new isolated profile loaded the checked-in corrected package with CLI
  2.4.1 / SDK 2.4.0. The host logged plugin discovery and no plugin-load
  failure, then stopped on the intentionally unavailable model
  `probe-invalid/not-installed`. No MCP server or provider credentials were
  configured or copied. The profile and its logs were removed.
- A subsequent isolated API-client probe established that session creation
  alone imports the plugin module but does not run `setup`; a synthetic local
  prompt was needed to activate the location plugin. The session used the same
  intentionally unavailable model, and the host stopped at model resolution
  before provider dispatch. After activation, the plugin list included
  `devlog-companion`, with no plugin-load error.
- The real RPC endpoint `/api/rpc/devlog-companion/snapshot` returned HTTP 200
  and the expected empty `UNKNOWN` snapshot for both an existing synthetic
  session and an unknown session ID. Missing `sessionID` and a non-string
  `sessionID` were rejected as `rpc.invalid_input`. **Unexpected result:**
  input containing an extra `unexpected` property also returned HTTP 200 and
  the same snapshot, despite `additionalProperties: false` in the declared
  schema. Runtime rejection of extra properties is therefore not demonstrated;
  this is a separate compatibility/validation gap, not a passing check.
- No MCP server or provider credentials were configured or copied. Temporary
  profiles, sessions, databases, and logs were removed after each probe. The
  initial extra-property acceptance was resolved by the strict validator
  described below.
- This confirms the checked-in fix removes the observed load blocker and that
  the RPC can be invoked for valid and unknown session inputs. Hook delivery,
  authorization, Gate coexistence, or Desktop behavior remain
  **NOT_EVALUATED**. Do not interpret this correction as Story acceptance.

## Strict RPC input validation follow-up (2026-11-26)

- An instrumented temporary copy showed that the host's JSON Schema path removes
  an undeclared property before the handler receives input (`Object.keys(input)`
  contained only `sessionID`). A handler-level key check therefore could not
  enforce the Story's rejection requirement.
- The RPC input now uses the SDK-supported Standard Schema validation contract.
  It accepts only a plain object with exactly one `sessionID` string of 1–128
  characters and returns validation issues for extra keys, missing/invalid
  values, arrays, and non-objects. The handler receives a newly constructed
  `{ sessionID }`, not caller-owned fields.
- Unit coverage exercises valid, extra-key, missing-key, wrong-type, and
  non-object inputs. `npm test` (15 tests), `npm run typecheck`,
  `npm run build`, and `npm pack --dry-run` passed.
- A fresh isolated profile with the checked-in package on CLI 2.4.1 / SDK 2.4.0
  activated the location plugin, confirmed it in the plugin list, and exercised
  the real RPC endpoint. Valid and unknown session calls returned the expected
  empty `UNKNOWN` snapshot; extra-key, missing-key, and wrong-type inputs were
  rejected as `rpc.invalid_input`. The synthetic prompt used a deliberately
  unavailable model; execution stopped before provider dispatch. No MCP or
  provider credentials were configured. Temporary profile and logs were
  removed.
- Strict rejection of additional RPC properties is now demonstrated for this
  runtime tuple. Tool hook delivery, cross-session authorization, Gate
  coexistence, and Desktop remain **NOT_EVALUATED**.

## Hook runtime feasibility follow-up (2026-11-26)

- The bounded hook-runtime attempt remains constrained to a disposable profile
  with no MCP server and no provider calls. The Story's proposed hook procedure
  requires a synthetic/local MCP fixture, but executing that fixture as an
  OpenCode tool requires a session model/tool-dispatch turn.
- The V2 API and installed client contract expose session prompt/command/shell
  operations, but no direct tool-execution operation. Prompt-based dispatch
  would cross the provider boundary; shell execution would not exercise a
  DevLog-named MCP tool. No route was used to bypass these boundaries.
- Consequently no OpenCode-hosted `execute.before`/`execute.after` event was
  produced in this follow-up. The existing deterministic tests do cover
  attempted-only, completed result, platform/tool error, MCP error decoding,
  missing terminal event expiry to `INCOMPLETE`, malformed envelopes, input
  immutability, and cleanup. These are fixture/unit results, not host-runtime
  hook-delivery evidence.
- `npm test`: 15 passed. No disposable server/profile was created, no MCP or
  provider was contacted, and no user profile, Desktop data, or Gate file was
  changed. Hook delivery, actual interruption behavior, and Gate coexistence
  remain **NOT_EVALUATED** pending separate authorization for an execution path
  that can dispatch a local synthetic tool without violating the no-provider
  constraint.

## Isolated synthetic-dispatch attempts (2026-11-26)

- Follow-up attempts used fresh disposable profiles under `/tmp/opencode`, with
  OpenCode CLI 2.4.1 / client SDK 2.4.0 inside `bwrap --unshare-net`. The model
  endpoint was a deterministic loopback-only stub; no real credentials or
  external network access were available.
- The unchanged Preflight Gate was copied byte-for-byte into the disposable
  profile; its SHA-256 remained
  `5022ae33ab17f1057587efd1a0bf771e058b20d9542424d59f309524f85c4250`.
  The Companion was copied to a temporary fixture and its retention shortened
  to 5 seconds solely to observe expiry. Neither temporary modification was
  applied to the repository package or Gate source.
- The MCP fixture did not produce usable dispatch: although one diagnostic
  reported `connected`, the model stub received no tool definitions and the
  fixture recorded no `tools/call`. A second attempt registered a local
  synthetic `devlog_story_agent` tool in OpenCode; the plugin list showed that
  fixture loaded, but the model endpoint again received requests with no tools.
  Consequently it made no tool call. The model endpoint was contacted, but
  only over loopback; this was not a real-provider call.
- No `execute.before` or `execute.after` hook event was observed. The apparent
  successful process exit only indicates that the probe harness completed; it
  is not a hook-runtime pass. Gate behavior was not exercised. MCP discovery
  and model tool-dispatch behavior remain unresolved blockers for host-hook
  validation.
- Every disposable profile was removed after its attempt, including the final
  instrumented profile. No user profile, Desktop data, source package, or Gate
  file was changed. Runtime hook delivery, terminal/missing-terminal behavior,
  and Gate coexistence remain **NOT_EVALUATED**.

## Dispatch metadata diagnostic (2026-11-26)

- One further prompt turn ran in a fresh `bwrap --unshare-net` profile using
  CLI 2.4.1 / SDK 2.4.0, an explicitly selected local model, the Companion,
  the unchanged Gate, and a local synthetic tool fixture. The profile had no
  MCP servers, real credentials, or external network access.
- The loopback stub captured one request's metadata only. Its `tools` array
  contained 14 built-in names (including `execute`) but not
  `devlog_story_agent`; the synthetic tool recorded no invocation. The fixture's
  post-registration registry-facts file was absent, so this run does not prove
  the fixture's `setup` or tool transform completed. No Companion load-error
  entry was found, which alone is not proof of successful setup.
- This locates the immediate blocker before tool execution: the candidate
  DevLog-named tool was not present in the model request. The exact cause
  (plugin setup/registration, agent/tool exposure, or Code Mode dispatch) is
  unresolved. Per the stop condition, no second prompt, MCP call, or hook
  scenario was attempted. No `execute.before`/`execute.after` event was
  observed, and Gate behavior remains `NOT_EVALUATED`.
- The client prompt completed and only loopback model traffic occurred. A
  runner cleanup defect left its isolated CLI process alive after the profile
  directory was removed. The process was identified by its deleted temporary
  working directory and deleted profile log/database descriptors, then sent
  `SIGTERM`; it is now a zombie (`Z`) and is not executing. The owned temporary
  profile itself is absent. The older partial profile
  `/tmp/opencode/devlog-companion-0248-runtime-20261126` was inspected read-only
  and intentionally left untouched because it is pre-existing and has no
  cleanup authorization in this action.
- The installed Gate source hash remained
  `5022ae33ab17f1057587efd1a0bf771e058b20d9542424d59f309524f85c4250`.
  Hook delivery, lifecycle outcomes, MCP fixture delivery, and behavioral Gate
  coexistence remain **NOT_EVALUATED**.

## Code Mode dispatch follow-up (2026-11-26)

- A corrected one-prompt probe ran under `bwrap --unshare-net` with a fresh
  disposable profile, CLI 2.4.1 / SDK 2.4.0, the unchanged Gate, an instrumented
  temporary Companion copy, and a synthetic local plugin tool. The model
  endpoint was loopback-only; no MCP server, real credential, or external
  provider was configured.
- The fixture setup completed. Its transform callback and subsequent tool
  registry read both confirmed the effective `devlog_story_agent` tool ID.
  Agent and model registry reads also completed, so the earlier
  `setup-error: TypeError` was a defect in the first diagnostic fixture's
  handling of the SDK's `{ data: [...] }` API responses, not evidence of a
  Companion or host failure.
- The local model endpoint received two requests for the single prompted
  session. Neither request contained a `tools` field, so the stub did not issue
  a direct tool call or a Code Mode `execute` call. The synthetic tool's
  executor was never reached; no Companion hook record was produced. This
  confirms the current blocker is after plugin registration but before the
  model-dispatch tool snapshot. Its cause remains unknown; no further turn was
  attempted.
- The host plugin inventory included the Companion, fixture, and Gate. This
  establishes discovery only; Gate policy behavior was not exercised.
  The disposable profile and host process were stopped and removed; the
  pre-existing partial profile was left untouched. The Gate source hash
  remained
  `5022ae33ab17f1057587efd1a0bf771e058b20d9542424d59f309524f85c4250`.
- Hook behavior, MCP delivery, terminal/error cases, and behavioral Gate
  coexistence remain **NOT_EVALUATED**.

## Phase-observed dispatch probe (2026-11-26)

- The runtime observation policy above was applied in a new disposable
  `bwrap --unshare-net` profile. The temporary Companion copy, local fixture,
  and byte-identical Gate copy were loaded with CLI 2.4.1 / SDK 2.4.0. The
  deterministic model endpoint was loopback-only; no MCP server, credentials,
  or external provider were configured.
- The fixture recorded `setup-enter`, a successful transform callback, and an
  effective tool-registry read containing `devlog_story_agent` (29 tools total).
  Agent `build` was present in `primary` mode with a catch-all `allow`
  permission. The fixture's model inventory contained 11 models but did not
  contain `fixture/runner`, despite the session and outbound requests naming
  model `runner`. This inventory/request discrepancy is evidence to investigate,
  not yet a proven cause of missing tools.
- One user prompt produced two loopback model requests. Both named model
  `runner`, neither contained the `tools` field, and neither contained a tool
  result. The prompt returned without a client error. The local tool executor
  was not called; the Companion hook trace was empty. No further prompt or MCP
  attempt was made after the dispatch gate failed.
- The host plugin inventory contained the Companion, local fixture, and Gate;
  the host log had no plugin-load-failure entry. This proves discovery only,
  not Gate policy behavior. No prompt, message body, tool argument, result,
  credential, or raw exception message was retained by the observation output.
- The new disposable profile and host process were removed/stopped. No
  `devlog-companion-0248-observe-*` directory or loopback OpenCode host remained.
  The pre-existing partial profile was left untouched. The installed Gate hash
  remained
  `5022ae33ab17f1057587efd1a0bf771e058b20d9542424d59f309524f85c4250`.
- The observation policy is operationally useful: it localizes the unresolved
  boundary to model-request assembly after plugin setup and tool registration.
  The cause remains **UNKNOWN**. Hook lifecycle, MCP delivery, and behavioral
  Gate coexistence remain **NOT_EVALUATED**.

## Confirmed Code Mode hook dispatch (2026-11-26)

- A fresh marker-owned profile ran under `bwrap --unshare-net` with a local
  deterministic OpenAI-compatible model endpoint, synthetic key, local
  `devlog_story_agent` fixture, temporary instrumented Companion copy, and
  unchanged Gate copy. No real DevLog MCP, provider, credential, Desktop, or
  external network was used.
- The earlier empty tool snapshots were explained by the release build's
  default-off `experimental.full_codemode` flag. The probe enabled this flag
  only in the disposable project config. The local provider/model then appeared
  in the active registry and outgoing requests exposed `execute`. A location
  bootstrap turn was required before plugins became active. The fixture's
  transform callback and registry evidence were checked independently before
  the stub was permitted to dispatch any nested tool.
- The synthetic tool was reached only through the normal model request →
  OpenCode `execute` Code Mode path. Its tool name did not appear as a direct
  model function: the request contained the `execute` schema, and the Code Mode
  program invoked the separately confirmed local `tools.devlog_story_agent`.
- **Success:** the observed hook sequence was `execute.before`
  (`codemode=false`), `devlog_story_agent.before` (`codemode=true`),
  `devlog_story_agent.after` (`status=completed`, `codemode=true`), then
  `execute.after` (`status=completed`, `codemode=false`).
- **Controlled nested-tool error:** the same ordering was observed, with the
  nested `devlog_story_agent.after` reporting `status=error`; the enclosing
  Code Mode `execute.after` independently reported `status=completed`. This
  confirms that wrapper completion does not imply nested-tool success.
- **Interrupted/incomplete attempt:** the fixture remained pending; the
  nested `before` hook was observed, `session.interrupt` returned, and no
  nested or outer `after` hook appeared during the following 2.5-second
  observation window. The probe then stopped the host. No terminal session
  event was captured, so the platform interruption outcome remains
  **UNKNOWN**; the missing `after` was not translated into success or failure.
- The Companion, fixture, and Gate all reported active in the isolated plugin
  inventory during the success/error run. This establishes loading/coexistence
  for that run only; Gate policy decisions were not exercised. The installed
  Gate source and every disposable copy retained SHA-256
  `5022ae33ab17f1057587efd1a0bf771e058b20d9542424d59f309524f85c4250`.
- Each newly created profile and host process was stopped and removed. No
  `devlog-companion-0248-*` probe directory or loopback OpenCode host remained.
  The pre-existing partial profile `/tmp/opencode/devlog-companion-0248-runtime-20261126`
  was left untouched.
- This validates the server hooks for these isolated success/error paths and
  one incomplete observation window. MCP tool delivery, Gate policy effectiveness,
  caller-to-session authorization, concurrency, Desktop UI, and complete
  interruption lifecycle remain **NOT_EVALUATED**.

## Additional isolated RPC and Gate checks (2026-11-26)

- The package validation was rerun: `npm run build` passed (15 tests,
  TypeScript check, and GUI build), followed by a successful
  `npm run pack:dry-run`.
- An isolated CLI 2.4.1 host loaded the Companion with a disposable home and an
  unreachable `127.0.0.1:1` model endpoint. No model request reached a provider
  and no real credential or external network was available. The authenticated
  client activated the plugin and called the registered RPC:
  - a same-session request returned the expected empty `UNKNOWN` snapshot;
  - an unknown session ID also returned an empty `UNKNOWN` snapshot;
  - an extra input property was rejected as `rpc.invalid_input`;
  - the same RPC request without host authentication was rejected as
    `UnauthorizedError`.
- These host results confirm OpenCode's host-level authentication rejection
  and fail-closed responses for this setup. They do **not** establish
  caller-to-session authorization: the probe did not demonstrate a
  positive workspace-scoped response or a cross-principal access denial.
  Location/project/workspace consistency checks are not a substitute for that
  authorization proof.
- The unchanged Gate's exported decision helpers were exercised separately in
  an isolated Node harness. A synthetic `explore` call without a qualifying
  `DEVLOG_PREFLIGHT` handoff was rejected with `DEVLOG_PREFLIGHT_REQUIRED`; a
  synthetic preflight completion plus qualifying handoff was allowed. No
  actual nested `explore` delegation was launched, so this is helper-level
  evidence, not host-hook/Gate coexistence evidence.
- The Gate file remained byte-for-byte unchanged at SHA-256
  `5022ae33ab17f1057587efd1a0bf771e058b20d9542424d59f309524f85c4250`.
  Disposable profiles were removed and the pre-existing partial profile was
  preserved.
- A local stdio MCP fixture configured under the V2 `mcp.servers` key connected
  from project configuration; OpenCode reported `connected`, and the fixture
  observed initialization plus `tools/list`. This corrects the earlier probe,
  which had placed the server name directly under `mcp` and therefore received
  `McpServerNotFoundError`; that error was a malformed probe configuration, not
  evidence of an OpenCode discovery defect.
- With `codemode:true`, the model request exposed the outer `execute` schema.
  A synthetic Code Mode attempt using the documented namespace form
  `tools.devlog.get_engineering_context(...)` drained without an observable MCP
  `tools/call`; the fixture log contains only initialization and discovery, and
  the instrumented Companion has no hook record. The MCP Code Mode invocation
  path therefore remains **UNKNOWN**. Do not infer that the tool call happened
  from the prompt completing or the model request containing `execute`.
- A separate Gate-host probe first confirmed all four plugins active after a
  bootstrap turn, then submitted missing-handoff and synthetic-handoff
  scenarios. The prompts drained, but neither the local preflight fixture nor
  the audit hook observed a tool event, and no Gate decision marker appeared.
  This probe did not exercise Gate policy and is not a pass or a Gate defect
  finding; host enforcement was **NOT_EVALUATED** by that probe (superseded by
  the later host result below).
- A direct-MCP control (`codemode:false`) also exposed the normalized
  `devlog_get_engineering_context` tool in the loopback model request. The
  deterministic stub returned a synthetic function-call response, but the
  fixture still recorded no `tools/call` and the Companion recorded no hook.
  Since this probe did not capture a platform tool event or a terminal
  tool-message state, it does not prove OpenCode dispatched or rejected the
  function call. It narrows neither the MCP transport nor Companion behavior;
  both remain **UNKNOWN** for this attempted call. No real DevLog MCP was
  contacted.
- Positive and negative caller-to-session authorization across
  workspaces/principals, actual Gate enforcement through the active host, RPC
  reconnect/concurrency, and Desktop rendering remain **NOT_EVALUATED**.
  Desktop was not launched, in keeping with the no-Desktop constraint.

## Corrected dispatch and Gate compatibility follow-up (2026-11-26)

- A metadata-only event probe revealed that the failed dispatch attempts were
  entering automatic compaction: the disposable model advertised only 4096
  context tokens, and OpenCode emitted `compaction.failed` before any tool
  event. Raising only the disposable deterministic model's declared context to
  32768 moved the prompt onto the normal model/tool path. This diagnoses the
  earlier harness limitation; it is not a change to the Companion or host.
- With a local stdio MCP fixture, loopback-only model, `bwrap --unshare-net`,
  `experimental.full_codemode:true`, and the 32768-token synthetic context:
  - `codemode:false` exposed `devlog_get_engineering_context`; the host emitted
    `session.tool.called` and `session.tool.success`, the fixture observed
    `tools/call` with session metadata, and the Companion recorded matching
    `execute.before`/`execute.after` observations for the direct tool.
  - `codemode:true` exposed the outer `execute` tool; its Code Mode program
    reached `tools.devlog.get_engineering_context`. The fixture observed
    `tools/call`; the Companion observed the nested DevLog-named tool with
    `codemode:true`, followed by the completed outer `execute` event.
  - The fixture returned only synthetic `UNKNOWN` task evidence. No real DevLog
    MCP, credential, provider, or external network was used.
- The unchanged Gate and a metadata-only audit plugin were active in a separate
  isolated host. On the missing-handoff case, OpenCode's `subagent` tool event
  carried input keys `agent`, `description`, and `prompt`; the Gate's current
  predicate checks `subagent_type` / `subagentType` for `"explore"`. No
  `DEVLOG_PREFLIGHT_REQUIRED` marker was observed, the `subagent` tool completed,
  and the parent had one child session. The child agent was configured with all
  tools denied and the model endpoint was loopback-only. This demonstrates that
  the unchanged Gate did not block this V2 `agent:"explore"` shape on the
  tested tuple. It is a **confirmed governance blocker** for a real governed
  workflow, not authorization to edit or disable the Gate; a separate
  Gate-compatibility change/Story is required. The positive Gate case was not
  exercised after this negative failure.
- A subsequent combined interruption/concurrency runtime harness exited
  non-zero before it produced any model request, fixture invocation, or hook
  event. It provides no lifecycle evidence. Existing unit tests remain the
  deterministic concurrency evidence; complete host interruption and runtime
  concurrency remain **NOT_EVALUATED**.
- The Gate hash was checked before the Gate probe and matched the expected
  installed SHA-256. The probe was marker-owned and its cleanup trap removed the
  temporary profile; the pre-existing partial profile was not targeted.
- RPC authentication, strict input validation, same/unknown-session fail-closed
  behavior, and the absence of caller-to-session proof remain as recorded in
  the earlier RPC section. No new workspace/principal or reconnect RPC
  scenario was run in this follow-up. Desktop remained unlaunched.

## Non-Gate runtime-gap follow-up (2026-11-26)

- DevLog preflight was attempted for these gaps with idempotency key
  `story-0248-runtime-gaps-nongate-20261126-v1`, task
  `fa65ce16-b46a-4577-b8ee-2b643b36bb53`. It ended in
  `STRUCTURAL_CONTRACT_ERROR`; the selected project context was stale/partially
  fresh, truncated, and did not resolve a Story UUID. No project-history
  conclusion was taken from it. Current Story, ADR, protocol, and source were
  used as repository evidence.
- An additional CLI 2.4.1 host probe used a fresh marker-owned profile under
  `/tmp/opencode`, a no-network `bwrap --unshare-net` namespace, an unreachable
  model endpoint at `127.0.0.1:1`, and only synthetic sessions. The same
  authenticated RPC instance returned an empty `UNKNOWN` snapshot for its own
  session, for an unknown ID, and for a known session created under a different
  project directory. The host reported distinct project IDs for the two
  directories. The cross-project result confirms this handler fails closed for
  that mismatch; it does **not** establish caller-to-session authorization,
  same-project/different-workspace isolation, or a positive scoped snapshot.
- A new model-dispatch lifecycle/concurrency harness attempt exited before its
  local model endpoint received any request and before the fixture or Companion
  recorded an event. It adds no interruption or runtime concurrency evidence.
  The earlier bounded interrupt probe remains `UNKNOWN` at the platform
  terminal-outcome boundary; runtime concurrency remains `NOT_EVALUATED`.
- Each newly created probe profile was removed after the host process was
  stopped/reaped and the marker-owned path was checked absent. The historical
  partial profile
  `/tmp/opencode/devlog-companion-0248-runtime-20261126` was not modified or
  deleted: it existed before this follow-up and its destruction was not
  authorized. Its prior partial cleanup/restoration state therefore remains
  unresolved under the protocol's cleanup criterion.
- Desktop remained unlaunched as required. Desktop render/interaction cannot be
  resolved under the current no-Desktop constraint and remains
  `NOT_EVALUATED`. The Gate remains unchanged and its V2 enforcement blocker
  remains assigned to separate work.

## Preconditions

Do not start until all are true:

1. Human gives explicit runtime authorization for the specific disposable
   environment and test actions.
2. A dedicated temporary home/config is provided or created under that
   authorization. Do not point the test process at the user's normal
   `~/.config/opencode` or Desktop data directory.
3. Record a restorable initial snapshot and verify the cleanup procedure before
   installation. The temporary profile must contain no real DevLog, provider,
   GitHub, or other credentials.
4. Record exact binaries, package versions, SDK provenance, OS, Node runtime,
   profile path, and SHA-256 of the package and unchanged Gate source.
5. Provide a synthetic/local MCP fixture only. Do not contact live DevLog or
   transmit prompts, repository content, credentials, or secrets.
6. Keep the Preflight Gate enabled and byte-for-byte unchanged. Do not work
   around a Gate rejection. A confirmed incompatibility blocks a claim that the
   workflow was governed and must be handled by a separate Story.

## Procedure

### A. Baseline and isolation

- Confirm the test process resolves configuration and app data only beneath the
  approved temporary home.
- Record the clean profile's installed plugin list and relevant files.
- Copy the unchanged Gate implementation into the temporary profile and record
  its hash; do not edit it.
- Install the locally built package only into the temporary project/profile
  after authorization. Do not publish it.

### B. Load and hook behavior

- Load the server part and record host/plugin versions and load diagnostics.
- Use the synthetic fixture to produce a DevLog tool attempt, a reported result,
  an MCP error result, a thrown tool error, and a call with no terminal hook.
- Verify a before hook records only an attempt; it must not be shown as confirmed
  dispatch. An absent after hook must age to `INCOMPLETE`, never success.
- Compare tool input and output before/after observation to confirm they are
  unchanged. Inject an observer exception and verify it does not affect the tool
  result.
- Verify unknown or malformed envelopes do not produce a task ID, success, or
  inferred task status.

### C. Identity, bounded state, and RPC

- Run concurrent calls, duplicate call IDs in separate sessions, reversed
  completions, child sessions, session switching, server reconnect, and delayed
  responses.
- Confirm identity dimensions remain separate: OpenCode call ID, explicit
  `mcpOperationId`, and explicit DevLog task ID. Missing identifiers remain
  absent; there is no timestamp-based correlation.
- Confirm only the routed session snapshot is displayed, stale responses do not
  cross a session switch, RPC failures degrade to unknown, and the GUI sends no
  credentials or raw tool data.
- Request a known session ID through a mismatched project/workspace and request
  an unknown session ID; both must return an empty unknown snapshot. Verify the
  host's caller authentication and caller-to-session authorization separately.
  Location equality alone is not evidence of that authorization.
- Repeat with the plugin instance missing an explicit workspace ID; it must fail
  closed rather than infer that workspace scoping is unnecessary.
- Verify memory bounds, retention, redaction, and snapshot schema rejection of
  extra fields. Connectivity remains `UNKNOWN` unless independent evidence is
  available.

### D. Desktop UI and Gate coexistence

- Confirm the GUI contribution is discovered in the temporary Desktop profile.
- Render and interact with the DevLog panel and session-scoped composer pill.
- Switch between sessions and workspaces while requests are pending; no
  previous session's projection may be rendered for the newly routed session.
- Run a synthetic `explore` delegation with and without the required
  `DEVLOG_PREFLIGHT` handoff, under the unchanged Gate.
- Record whether the Gate authorizes or blocks the V2 path. Do not change the
  Gate or reinterpret a failure as a successful governed workflow.

### E. Removal and cleanup

- Disable/remove the package only within the temporary environment.
- Restart only the temporary host if required by the authorized procedure.
- Verify package files, configuration entries, caches, and generated state are
  absent; compare the profile against its initial snapshot.
- Restore or destroy only the disposable profile according to the approved
  procedure. Do not modify the user's normal profile.

## Evidence and stop conditions

Capture only sanitized metadata: timestamps, versions, hashes, event categories,
bounded statuses, and test outcomes. Do not capture prompts, raw tool inputs or
outputs, credentials, or repository payloads.

Stop and report `BLOCKED` if isolation, restoration, Gate integrity, version
pairing, or authorization cannot be demonstrated. Report unrun checks as
`NOT_EVALUATED`. Documentation, typecheck, unit tests, and package inspection
are not runtime evidence and do not qualify the OpenCode/Gate combination.
