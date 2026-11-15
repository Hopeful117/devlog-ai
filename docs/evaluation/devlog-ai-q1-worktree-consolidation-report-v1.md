# DevLog AI Q1 Worktree Consolidation Report

## A. Executive Summary

- Branch: `story/0184-bounded-story-context-investigation`.
- HEAD: `60fae3e0e1104d0ffbe3832c1f578ed532b1268a`.
- Q1 source snapshot: `60fae3e0e1104d0ffbe3832c1f578ed532b1268a`.
- Deterministic A/B/C integration simulation: `Q1_INTEGRATION_SIMULATION_PASS`.
- External provider calls: `0`; external network calls: `0`.
- No commit, stage, push, live benchmark, or real credential was used.
- Commit readiness: `READY_WITH_RESERVATIONS`.

The Q1 corrections and provider-free harness are coherent enough to be committed
as an ordered revision. The reservation is material: the execution identity is
currently the dirty worktree plus the pinned source snapshot, not a single
verifiable commit. The first live pilot remains blocked by human oracle/provider
approval, a finalized live adapter/artifact contract, and explicit budget and
execution authorization.

The dedicated DevLog Story Agent capability was not available in this session;
this report therefore relies on the repository, Q1 manifest, existing reports,
and deterministic test artifacts, with the repository treated as authoritative
for current physical state.

## B. Git Inventory

### Repository State

| Item | Result |
|---|---|
| Branch | `story/0184-bounded-story-context-investigation` |
| HEAD | `60fae3e0e1104d0ffbe3832c1f578ed532b1268a` |
| Staged changes | None; `git diff --cached --stat` empty |
| Tracked modified files | 7 |
| Tracked deleted files | 0 |
| Tracked renamed files | 0 |
| Non-ignored untracked files | 47 |
| Ignored files | 75,513; generated/dependency output, not commit candidates |
| Patch whitespace check | Passed |

Ignored output includes `frontend/node_modules`, `.venv`, Java/Node targets,
`.opencode`, caches, and an ignored `.env`. The `.env` was not inspected or
included. No Q1 source or fixture contains a real secret pattern; credentials in
the C binding are test inputs supplied at runtime and are not persisted.

### File Classification

| File | Git state | Category | Change / purpose | Proposed commit |
|---|---|---|---|---|
| `backend/src/main/java/com/hopeful117/devlogai/ai/task/entity/AiTask.java` | Modified | A | Allows the authorized credential binding identity to be persisted after task creation; no secret value is stored. | 1 |
| `backend/src/main/java/com/hopeful117/devlogai/authorization/OidcSecurityConfiguration.java` | Modified | A | Exposes the action transport to application-level HMAC/JWT branching; controller still authenticates the request. | 1 |
| `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/investigation/InvestigationCapabilityExecutionService.java` | Modified | A | Drops out-of-scope evidence instead of returning it to Python. | 1 |
| `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/StoryContextInvestigationActionService.java` | Modified | A | Removes internal timing/request-digest fields from the public action response while retaining Core ledger state. | 1 |
| `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/StoryContextSubmissionService.java` | Modified | A | Binds authorized provider, model, and credential identity to `AiTask`. | 1 |
| `backend/src/test/java/com/hopeful117/devlogai/storycontextanalysis/StoryContextCoreIntegrationTest.java` | Untracked | D | Testcontainers/PostgreSQL Core harness for A/C submission, action, callback, validation, and persistence. | 1 |
| `ai-engine/app/clients/core_callback_client.py` | Modified | B | Preserves the Core v2 validation error detail in the Python exception. | 2 |
| `ai-engine/app/prompts/story_context_analysis.py` | Modified | B | Validates only immutable investigation initialization fields while retaining mutable ledger state for the protocol. | 2 |
| `ai-engine/evaluations/devlog_ai_abc_capability_comparison/__init__.py` | Untracked | C | Package marker for evaluation-only capability fixtures. | 3 |
| `ai-engine/evaluations/devlog_ai_abc_capability_comparison/q1_fixture.py` | Untracked | C | Provider-free B/C action and pinned-repository contract fixture. | 3 |
| `ai-engine/evaluations/devlog_ai_abc_normalization/__init__.py` | Untracked | C | Package marker for evaluation-only normalization. | 3 |
| `ai-engine/evaluations/devlog_ai_abc_normalization/normalizer.py` | Untracked | C | Deterministic RAW-to-DERIVED claim/evidence normalization and replay identity. | 3 |
| `ai-engine/evaluations/devlog_ai_abc_runner/__init__.py` | Untracked | C | Q1 runner package marker and mode exports. | 3 |
| `ai-engine/evaluations/devlog_ai_abc_runner/core_bridge.py` | Untracked | C | Fixed Java Core bridge; pins the Q1 revision and locates `uv` portably for C replay. | 3 |
| `ai-engine/evaluations/devlog_ai_abc_runner/fixtures.py` | Untracked | C | Deterministic native-contract fixtures for A/C dry runs. | 3 |
| `ai-engine/evaluations/devlog_ai_abc_runner/integration.py` | Untracked | C | A/B/C provider-free integration adapters. | 3 |
| `ai-engine/evaluations/devlog_ai_abc_runner/live.py` | Untracked | C | Closed-by-default live preflight and authorization validation. | 3 |
| `ai-engine/evaluations/devlog_ai_abc_runner/live_adapters.py` | Untracked | C | Live entrypoint metadata/adapters; not executable live transport. | 3 |
| `ai-engine/evaluations/devlog_ai_abc_runner/provenance.py` | Untracked | C | Source/runtime/harness identity, dirty-worktree detection, and generated-file exclusions. | 3 |
| `ai-engine/evaluations/devlog_ai_abc_runner/q1_b_binding.py` | Untracked | C | B binding to the unchanged V4 runtime and pinned Git repository tools. | 3 |
| `ai-engine/evaluations/devlog_ai_abc_runner/q1_c_binding.py` | Untracked | C | Deterministic Python PLAN/ACTION/FINAL binding with Core HTTP/HMAC callbacks. | 3 |
| `ai-engine/evaluations/devlog_ai_abc_runner/runner.py` | Untracked | C | Isolated runner, RAW/DERIVED/trace/status artifacts, replay, and PASS/PARTIAL classification. | 3 |
| `ai-engine/tests/test_devlog_ai_abc_capability_comparison.py` | Untracked | D | Capability fixture contract tests. | 3 |
| `ai-engine/tests/test_devlog_ai_abc_live_preflight.py` | Untracked | D | Live gate, authorization, and no-provider preflight tests. | 3 |
| `ai-engine/tests/test_devlog_ai_abc_normalization.py` | Untracked | D | Evaluation-only normalization and provenance tests. | 3 |
| `ai-engine/tests/test_devlog_ai_abc_preflight.py` | Untracked | D | Manifest, oracle, budget, identity, and safety validation tests. | 3 |
| `ai-engine/tests/test_devlog_ai_abc_provenance.py` | Untracked | D | Snapshot, runtime, harness, dirty-worktree, and generated-file provenance tests. | 3 |
| `ai-engine/tests/test_devlog_ai_abc_runner.py` | Untracked | D | Dry-run, integration simulation, replay, artifact, and live-gate tests. | 3 |
| `ai-engine/tests/test_devlog_ai_q1_b_binding.py` | Untracked | D | V4 Q1 binding and pinned repository identity tests. | 3 |
| `docs/evaluation/answer-quality-benchmark-specification.md` | Untracked | E | Benchmark scope, answer quality, and evaluation boundary. | 4 |
| `docs/evaluation/comparative-experiment-protocol.md` | Untracked | E | End-to-end A/B/C experiment protocol. | 4 |
| `docs/evaluation/devlog-ai-abc-benchmark-v1.json` | Untracked | E | Benchmark machine-readable definition. | 4 |
| `docs/evaluation/devlog-ai-abc-comparability-matrix-v1.md` | Untracked | E | Treatment capability/comparability matrix. | 4 |
| `docs/evaluation/devlog-ai-abc-first-test-manifest-v1.json` | Untracked | E | Q1 manifest, revision, identities, budgets, and gates. | 4 |
| `docs/evaluation/devlog-ai-abc-normalized-answer-contract-v1.md` | Untracked | E | Evaluation-only normalized answer contract. | 4 |
| `docs/evaluation/devlog-ai-abc-oracle-human-review-sheets-v1.md` | Untracked | E | Human oracle review material. | 4 |
| `docs/evaluation/devlog-ai-abc-oracle-proposals-v1.json` | Untracked | E | Proposed Q1 oracle claims; human acceptance remains pending. | 4 |
| `docs/evaluation/devlog-ai-abc-preflight-report-v1.md` | Untracked | E | Preflight results and authorization boundaries. | 4 |
| `docs/evaluation/devlog-ai-abc-question-review-v1.md` | Untracked | E | Q1 question review and scope. | 4 |
| `docs/evaluation/devlog-ai-q1-answer-evaluability-report-v1.md` | Untracked | E | Answer evaluability assessment. | 4 |
| `docs/evaluation/devlog-ai-q1-asymmetric-pilot-runbook-v1.md` | Untracked | E | Future pilot runbook; not live authorization. | 4 |
| `docs/evaluation/devlog-ai-q1-b-direct-config-v1.json` | Untracked | E | Q1 B runtime binding and historical V4 identity. | 4 |
| `docs/evaluation/devlog-ai-q1-capability-equivalence-report-v1.md` | Untracked | E | Capability comparison and asymmetry record. | 4 |
| `docs/evaluation/devlog-ai-q1-comparative-report-template-v1.md` | Untracked | E | Future comparative result template. | 4 |
| `docs/evaluation/devlog-ai-q1-dry-run-report-v1.md` | Untracked | E | Provider-free dry-run report. | 4 |
| `docs/evaluation/devlog-ai-q1-live-readiness-report-v1.md` | Untracked | E | Live readiness and blockers; some sections describe the pre-C-replay state and should be reconciled before final publication. | 4, review needed |
| `docs/evaluation/devlog-ai-q1-oracle-review-v1.md` | Untracked | E | Human oracle review; status remains pending. | 4 |
| `docs/evaluation/devlog-ai-q1-worktree-consolidation-report-v1.md` | Untracked | E | This final worktree inventory, production audit, validation record, and commit proposal. | 4 |
| `.claude/agents/devlog-readonly-pilot.md` | Untracked | F | Read-only agent configuration; not Q1 runtime/harness. Preserve separately. | Not Q1 |
| `CLAUDE.md` | Untracked | F | Repository instruction pointer to `AGENTS.md`; not Q1 runtime/harness. Preserve separately. | Not Q1 |
| `docs/investigations/current-state-quality-bottleneck-investigation.md` | Untracked | F | Strategic investigation awaiting human validation; not required by Q1 execution. Preserve separately. | Not Q1 |
| `docs/investigations/engineering-analyst-architecture-decision-proposal.md` | Untracked | F | Conditional architecture proposal, not an accepted ADR. Preserve separately. | Not Q1 |
| `docs/investigations/engineering-analyst-implementation-roadmap-proposal.md` | Untracked | F | Conditional roadmap, not implementation authorization. Preserve separately. | Not Q1 |
| `docs/investigations/engineering-analyst-strategy-comparison.md` | Untracked | F | Strategic comparison awaiting experimental evidence. Preserve separately. | Not Q1 |

No file requires hunk-level staging for the proposed grouping based on the
current diff. The four documentation files marked `F` must not be silently
included in Q1 commits. The Q1 live-readiness report should either be updated
by its owner or explicitly accepted as historical pre-C-replay context.

## C. Production Correction Audit

### Java Core

| Correction | Protected invariant | Evidence / regression test | Residual risk |
|---|---|---|---|
| Persist `credentialBindingId`, provider, and model on `AiTask` | Later action authentication matches the authorized task binding. | `StoryContextCoreIntegrationTest`; C artifact persisted `credentialBindingId`, provider, and model identities. | The correction is only reproducible after it is committed with the harness and a new effective revision is declared. |
| Permit the action transport through OIDC filter | Application-level HMAC or authenticated principal remains responsible for action authentication. | Controller checks HMAC first and falls back to JWT principal; Java integration tests pass. | `permitAll` is safe only while the controller/service checks remain intact; future endpoint refactors need security regression coverage. |
| Continue on unauthorized evidence | Out-of-scope or wrong-revision evidence is never exposed to Python. | Investigation action tests and C Core artifact; action result contains only authorized evidence. | Empty results must remain explicit and must not be interpreted as proof of absence. |
| Remove internal elapsed/request digest fields from action response | Internal Core metadata is not exposed as public evidence payload. | C action response and callback integration tests. | Internal ledger still retains accounting and idempotency data; this distinction must remain documented. |

### Python AI Engine

| Correction | Protected invariant | Evidence / regression test | Residual risk |
|---|---|---|---|
| Validate immutable initialization subset | Mutable usage/actions/ledger do not invalidate the immutable initialization contract. | `test_story_context_investigation.py`, Q1 C binding. | The field projection must remain synchronized if the immutable contract changes. |
| Preserve Core v2 error detail | Diagnosis does not collapse a typed Core rejection into an opaque generic error. | `test_core_callback_client.py`. | Error text remains bounded by caller/provider logging policy. |
| PLAN/ACTION/FINAL binding | Python proposes actions and consumes Core results; Core remains authoritative. | C binding records two `PYTHON_EVIDENCE_RECEIVED` events before `PYTHON_FINAL`; Core validates callback and persists. | The live adapter/provider path is not authorized or exercised. |

No Python production service implementation was added for the Q1 loop beyond
the two tracked corrections above; the Q1 C binding invokes the existing
`StoryContextAnalysisGenerationService._process_investigation` path.

### Security Review

- No real secret, token, private key, or provider credential was found in Q1
  source, fixtures, configuration, or documentation.
- No Spring Security bypass was added. The new `permitAll` route is guarded by
  HMAC in `StoryContextAgentProtocolController`, with JWT principal fallback.
- Core owns scope, revision, action identity, limits, usage, evidence filtering,
  callback identity, grounding, and final persistence.
- Python does not access the repository, database, or authorization store.
- The ignored `.env` was not opened and is not a commit candidate.

## D. A/B/C Validation

### Simulation Result

Command executed:

```bash
PYTHONPATH=ai-engine uv run --project ai-engine python \
  -m evaluations.devlog_ai_abc_runner.runner \
  --mode INTEGRATION_SIMULATION \
  --manifest docs/evaluation/devlog-ai-abc-first-test-manifest-v1.json \
  --repository-root . \
  --output-root /tmp/opencode/q1-final-simulation-1
```

Result: `Q1_INTEGRATION_SIMULATION_PASS`.

| Treatment | Result | Verified boundary |
|---|---|---|
| A | `PASS` | Core authorization, preparation, submission, callback validation, and persistence with deterministic boundaries. |
| B | `PASS` | Existing V4 runtime, `PinnedGitRepositoryTools`, Q1 snapshot read, native output, and native trace. |
| C | `PASS` | Python PLAN/ACTION/FINAL, two HMAC Core actions, evidence returned before FINAL, final callback, Core validation, and persistence. |

The C RAW artifact records `PYTHON_EVIDENCE_RECEIVED` before `PYTHON_FINAL`,
three scripted provider turns, `HTTP_LOOPBACK_HMAC`, Core validation `PASS`,
and persisted non-trusted analysis state.

### Provenance

| Identity | Value |
|---|---|
| Q1 source snapshot | `60fae3e0e1104d0ffbe3832c1f578ed532b1268a` |
| Snapshot probe | `f67ee2b41dd1647580088cb3f812f65c3686ec19c849c9a83e4046e9249a4107` |
| V4 historical manifest SHA-256 | `e6d084964b84b5e9e29eee17ef2285c0f041ddd315d3c952096ef2b20831595c` |
| Current Q1 manifest SHA-256 | `34e8b08b61d46184a667831898b5b7c3335660a66e6e5b5b7411efb8d42038fc` |
| Q1 B config SHA-256 | `5ea124c223f7204d748f4c7aeef9f2010271ed898ef48a82914c7829605d6384` |
| Harness identity | Recorded in the simulation; excludes only `__pycache__` and `*.pyc`. |
| Worktree | Dirty; effective runtime/harness differs from HEAD. |

The historical V4 manifest hash matches both the worktree and the pinned Git
object. No source, configuration, or functional file is hidden by the generated
file exclusions.

## E. Tests and Known Failures

| Suite / command | Result | Q1 relevance | Decision |
|---|---|---|---|
| Q1 integration simulation | `PASS` | Direct | Accept as provider-free technical integration evidence. |
| Java `StoryContextCoreIntegrationTest` | `2 passed` | Direct | Accept. |
| Q1 Python runner/preflight/provenance/B binding tests | Passed; latest focused run green | Direct | Accept. |
| Story Context, callback, bounded baseline, and V4 targeted suites | Passed | Direct/supporting | Accept. |
| `uv run --project . --extra dev pytest -q tests` from `ai-engine` | 6 failures, remaining tests collected | Indirect; failing files are outside Q1 paths | Do not fix in this mission; retain as repository validation limitation. |
| Root `uv run ... pytest -q` | Collection errors from `data/workspaces` duplicates and evaluation modules imported without their local path | None for Q1 | Use the scoped `ai-engine/tests` command instead. |

The six broader failures are:

- `tests/test_retry.py::test_no_retry_on_success`
- `tests/test_retry.py::test_retry_on_timeout`
- `tests/test_retry.py::test_no_retry_on_validation_error`
- `tests/test_retry.py::test_retry_on_429`
- `tests/test_retry.py::test_no_retry_on_400`
- `tests/test_typed_architecture_overview.py::test_v3_unauthorized_grounding_gets_structured_retry_and_can_recover`

The five retry failures stop at `effective_output_tokens()` because the test
uses a `MagicMock(spec=Prompt)` without `generation_parameters`. The typed
architecture failure expects `GROUNDING_ERROR`, while the current service emits
the more specific `GROUNDING_AUTHORIZATION`. None of the failing files is in
the Q1 production diff or Q1 harness. They are therefore not classified as Q1
blockers, but they prevent claiming a clean global AI Engine suite.

## F. Commit Plan

### Commit 1 — `fix(core): persist and validate bounded story investigation identity`

Files:

- `backend/src/main/java/com/hopeful117/devlogai/ai/task/entity/AiTask.java`
- `backend/src/main/java/com/hopeful117/devlogai/authorization/OidcSecurityConfiguration.java`
- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/investigation/InvestigationCapabilityExecutionService.java`
- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/StoryContextInvestigationActionService.java`
- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/StoryContextSubmissionService.java`
- `backend/src/test/java/com/hopeful117/devlogai/storycontextanalysis/StoryContextCoreIntegrationTest.java`

Validation:

```bash
./backend/mvnw -pl backend -am \
  -Dtest=StoryContextCoreIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test -B
```

Dependency: none. Risk: action endpoint security depends on the controller HMAC
check remaining paired with the `permitAll` route.

### Commit 2 — `fix(ai-engine): preserve bounded investigation contract details`

Files:

- `ai-engine/app/clients/core_callback_client.py`
- `ai-engine/app/prompts/story_context_analysis.py`

Validation:

```bash
uv run --project ai-engine --extra dev pytest -q \
  ai-engine/tests/test_story_context_investigation.py \
  ai-engine/tests/test_core_callback_client.py
```

Dependency: Commit 1's Core contract. Risk: immutable initialization field
projection must evolve with the shared contract.

### Commit 3 — `test(q1): add deterministic A/B/C bindings and replay harness`

Files:

- `ai-engine/evaluations/devlog_ai_abc_capability_comparison/__init__.py`
- `ai-engine/evaluations/devlog_ai_abc_capability_comparison/q1_fixture.py`
- `ai-engine/evaluations/devlog_ai_abc_normalization/__init__.py`
- `ai-engine/evaluations/devlog_ai_abc_normalization/normalizer.py`
- `ai-engine/evaluations/devlog_ai_abc_runner/__init__.py`
- `ai-engine/evaluations/devlog_ai_abc_runner/core_bridge.py`
- `ai-engine/evaluations/devlog_ai_abc_runner/fixtures.py`
- `ai-engine/evaluations/devlog_ai_abc_runner/integration.py`
- `ai-engine/evaluations/devlog_ai_abc_runner/live.py`
- `ai-engine/evaluations/devlog_ai_abc_runner/live_adapters.py`
- `ai-engine/evaluations/devlog_ai_abc_runner/provenance.py`
- `ai-engine/evaluations/devlog_ai_abc_runner/q1_b_binding.py`
- `ai-engine/evaluations/devlog_ai_abc_runner/q1_c_binding.py`
- `ai-engine/evaluations/devlog_ai_abc_runner/runner.py`
- `ai-engine/tests/test_devlog_ai_abc_capability_comparison.py`
- `ai-engine/tests/test_devlog_ai_abc_live_preflight.py`
- `ai-engine/tests/test_devlog_ai_abc_normalization.py`
- `ai-engine/tests/test_devlog_ai_abc_preflight.py`
- `ai-engine/tests/test_devlog_ai_abc_provenance.py`
- `ai-engine/tests/test_devlog_ai_abc_runner.py`
- `ai-engine/tests/test_devlog_ai_q1_b_binding.py`

Validation:

```bash
uv run --project ai-engine --extra dev pytest -q \
  ai-engine/tests/test_devlog_ai_abc_capability_comparison.py \
  ai-engine/tests/test_devlog_ai_abc_live_preflight.py \
  ai-engine/tests/test_devlog_ai_abc_normalization.py \
  ai-engine/tests/test_devlog_ai_abc_preflight.py \
  ai-engine/tests/test_devlog_ai_abc_provenance.py \
  ai-engine/tests/test_devlog_ai_abc_runner.py \
  ai-engine/tests/test_devlog_ai_q1_b_binding.py
```

Dependency: Commits 1 and 2. Risk: this commit records dirty-worktree status
until the commit exists; the post-commit manifest/runtime revision must be
revalidated.

### Commit 4 — `docs(evaluation): document Q1 protocol and readiness boundaries`

Files:

- All 19 files under `docs/evaluation/` listed in the inventory above, including
  this consolidation report.

Validation:

```bash
python3 -m json.tool docs/evaluation/devlog-ai-abc-first-test-manifest-v1.json >/dev/null
python3 -m json.tool docs/evaluation/devlog-ai-q1-b-direct-config-v1.json >/dev/null
sha256sum ai-engine/evaluations/comparative_baseline_v4/v4-manifest.json
```

Dependency: Commit 3. Risk: `devlog-ai-q1-live-readiness-report-v1.md` contains
historical pre-C-replay wording and should be reconciled before publication;
do not silently treat it as the final current-state report.

### Separate, not Q1 commits

Keep `.claude/agents/devlog-readonly-pilot.md`, `CLAUDE.md`, and the four
`docs/investigations/` files separate from the Q1 commit sequence. They are
human-authored instruction/strategy artifacts and must be preserved, but their
presence does not authorize inclusion in the benchmark revision.

## G. Live Readiness

Status remains:

- `Q1_LIVE_TECHNICALLY_BLOCKED`
- `Q1_LIVE_NOT_AUTHORIZED`
- `Q1_ORACLE_PENDING_HUMAN_REVIEW`

Before a first live pilot, the following must be independently approved and
verified:

1. Commit the coherent production/runtime/harness set and update the effective
   pinned revision and hashes.
2. Re-run the simulation from that committed revision.
3. Approve the oracle semantically and record reviewer/date/digest.
4. Define executable live adapters for A/B/C, including token, duration, cost,
   and failure artifacts.
5. Validate provider/model identity and pricing source.
6. Approve the budget ceiling, operation limits, retention/redaction policy,
   and explicit live execution authorization.

No live launch command is authorized by this report.

## H. Recommendation

**Next action:** human-review and commit the four proposed groups in order,
excluding the explicitly separate F files, then rerun the exact provider-free
simulation from the resulting commit before considering any live authorization.

The current Q1 worktree is `READY_WITH_RESERVATIONS` for commits, not ready for
a live pilot and not accepted as human oracle approval.
