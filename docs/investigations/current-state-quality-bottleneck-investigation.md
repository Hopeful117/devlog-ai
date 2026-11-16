# Current State & Quality Bottleneck Investigation

## Status

`INVESTIGATION COMPLETE - AWAITING HUMAN VALIDATION`

This is a read-only strategic investigation. It does not authorize production
changes, provider calls, benchmark collection, ADR acceptance, or Story
implementation.

## 1. Frozen Repository State

The investigated state is the local checkout and its matching remote branch:

| Item | Observed value | Classification |
|---|---|---|
| Branch | `story/0184-bounded-story-context-investigation` | OBSERVED |
| HEAD | `60fae3e0e1104d0ffbe3832c1f578ed532b1268a` | OBSERVED |
| Remote branch | `origin/story/0184-bounded-story-context-investigation` | OBSERVED |
| `origin/main` | `d83600aa4e65e61263a05f4de49d2df14d679e85` | OBSERVED |
| Branch/remote equality | HEAD equals the remote branch tip | OBSERVED |
| Local tracked modifications | none reported by `git status --short --branch` | OBSERVED |
| Pre-existing untracked paths | `.claude/`, `CLAUDE.md` | OBSERVED; not modified |

The story-local implementation report describes an older `main` baseline at
`d83600a`. That report is historical relative to the current branch and must
not be used as the current physical repository state.

## 2. Current Architecture

### 2.1 A: current one-shot DevLog path

The current production path is:

```text
question and authorized scope
  -> Java/Core candidate collection, ranking, selection and enrichment
  -> bounded Story Context projection and grounding allow-list
  -> one Python provider generation
  -> defensive Python validation/retry
  -> authenticated Java callback and authoritative validation
  -> immutable non-trusted analysis snapshot
```

The Core projection carries scope, revision, freshness, selected evidence,
accounting, truncation information, grounding candidates, and digests. The
provider receives a bounded consumer projection, not a repository handle.

### 2.2 Current 0184 capability

The current branch adds an opt-in v2 investigation path above the v1 path:

```text
initial projection
  -> Python PLAN/ACTION/FINAL decision
  -> authenticated Core action endpoint
  -> Core validation and deterministic search/read
  -> canonical evidence and grounding update
  -> next provider decision
  -> final Core callback validation
```

Observed implementation facts:

- Core registry exposes `SEARCH_EVIDENCE` and `READ_EVIDENCE_RANGE`.
- Core validates project, revision, paths, action IDs, result limits, bytes,
  tokens, and usage before execution.
- Core reuses `ProjectContextProvider` and `RepositoryContextAdapter`; Python
  has no repository, database, filesystem, or retrieval-index access.
- Core serializes action execution with a task lock and stores bounded action
  metadata in the existing task snapshot.
- Python uses strict v2 Pydantic contracts and can terminate on rejected,
  invalid, incomplete, or exhausted execution.
- The v1 one-shot path remains the default; initialization of investigation is
  explicit.
- Credential-free Story 0184 tests exercise contracts and behavior, not live
  answer quality.

These are OBSERVED in the current code. The claim that the path improves real
answer quality is UNKNOWN.

## 3. Existing Direct-Investigation Capability

The evaluation-only Comparative Baseline V4 direct arm is separate from the
Story 0184 production capability. It exposes six typed, read-only operations:

`read_file`, `search_repository`, `git_log`, `git_show`, `git_diff`, and
`inspect_commit`.

It pins a repository revision and records tool traces, evidence, resource
usage, replay identities, and deterministic evaluation. This is a usable
experimental substrate for B, but it is not evidence that the production Core
analyst path and the evaluation direct path are behaviorally identical.

## 4. Evidence About the Quality Bottleneck

| Finding | Evidence | Classification | Confidence |
|---|---|---|---|
| One-shot context loses most candidates before projection | Story 0179/0180 runtime ledgers: thousands of candidates reduced to 60 selected rows; category concentration dominates losses | OBSERVED | High |
| Lexical retrieval has a measured fixture recall gap | Story 0177 offline fixture: lexical final recall `0.875`, structural/semantic/hybrid `1.0`; semantic noise is higher | OBSERVED, fixture-scoped | High within fixture; low for production |
| The fixture retrieval result does not establish production RAG value | Story 0177 explicitly uses a test double, no live repository collection or provider path | OBSERVED | High |
| Rich context can still yield generic or shallow synthesis | Post-0105 and post-0109 investigations report rich inputs with limited cross-section/history synthesis and context interference | DOCUMENTED; current branch applicability requires revalidation | Medium |
| Grounding constraints can be technically valid but semantically under-salient | Post-0109 reports large prompts and overclaim persistence even in minimal context | DOCUMENTED; not re-run here | Medium |
| Provider/runtime failures can prevent any quality measurement | V4 official collection: 13/18 not semantically evaluated; later direct validations ended before a usable tool or answer | OBSERVED in historical artifacts | High |
| Story 0184 behavioral tests prove safety and bounded flow, not usefulness | `test_story0184_bounded_behavioral_baseline.py` uses deterministic fakes and checks status/action behavior | OBSERVED | High |
| A direct agent can inspect implementation and history when the provider completes | V4 pilot traces show search/read trajectories and Git operations before some failures | OBSERVED, historical pilot | High |
| C is not yet experimentally measured as a distinct treatment | Current V4 conditions are `DEVLOG` and `AGENT_DIRECT_OPEN`; Story 0184 is not the same comparative treatment | OBSERVED | High |

## 5. Cause Map

The following separates failure stages rather than treating every weak answer as
a retrieval problem.

| Candidate cause | What it means | Current evidence | Verification required |
|---|---|---|---|
| `ABSENT_FROM_CANDIDATES` | Relevant source never enters Core/direct candidate generation | Supported for some lexical fixture cases; not measured on reviewed live questions | Frozen evidence oracle with candidate-level recall |
| `DISCARDED_BY_COMPOSITION` | Relevant candidate exists but is rejected by category, relevance, or item budget | Strongly supported by 0179/0180 ledgers | Per-question selected/discarded evidence manifest |
| `NOT_PROJECTED` | Selected information is not in the model-facing contract | Possible in legacy pipelines; current Story Context projection must be checked per case | Projection-level reference visibility audit |
| `AVAILABLE_NOT_USED` | Evidence is visible but the model does not use it | Supported by prior qualitative investigations; not isolated in current branch | Claim-to-visible-evidence annotation and ablation |
| `REASONING_OR_INVESTIGATION_GAP` | Model needs a follow-up read/search/history operation | Plausible; Story 0184 exists specifically for this gap | A/B/C traces with required evidence and action necessity labels |
| `SYNTHESIS_GAP` | Evidence is correctly collected but conclusion is generic, wrong, or overclaimed | Supported by prior post-0105/post-0109 qualitative work | Blind human scoring and claim-level oracle |
| `CONTRACT_GAP` | Schema prevents useful answer, uncertainty, or evidence expression | Grounding gaps documented for some older intents; Story Context contract is richer | Contract adequacy review per benchmark case |
| `PROVIDER_OR_RUNTIME_FAILURE` | No usable output or incomplete output | Strongly observed in V4 | Exclude from semantic denominator; report separately |
| `EVALUATION_GAP` | Oracle, rubric, or identity makes a result non-comparable | Observed in historical V4 corrections and eligibility rules | Pre-register oracle and treatment identities |

## 6. Strategic Finding

The current evidence does **not** justify the statement that the main problem is
only retrieval. It supports a layered diagnosis:

1. Core composition can discard answer-critical material.
2. A one-shot model cannot request a missing concrete implementation or history
   slice.
3. Even with rich evidence, synthesis and grounding salience can remain weak.
4. Provider/runtime instability has repeatedly reduced the set of semantically
   evaluable answers.

The central question therefore cannot be answered by context-size metrics or
retrieval recall alone. The decisive experiment must compare complete answer
quality while preserving stage-level diagnostics.

## 7. Reusable Capabilities

- Core repository collection, revision pinning, evidence identity, trust tiers,
  grounding allow-lists, and context-loss accounting.
- Story 0184 Core search/read action contracts and authenticated execution.
- Existing history search and Git evidence collectors.
- Existing V4 pinned repository tools for read/search/history/diff inspection.
- ADR-066 replayable evaluator boundaries and deterministic replay.
- Existing V4 isolation, hash, ledger, and provider failure attribution.

## 8. Unknowns Requiring the Benchmark

- Whether B beats A because it investigates, not merely because it sees more
  raw bytes.
- Whether C beats B because DevLog structure reduces search cost or improves
  interpretation.
- Which evidence categories benefit from active investigation.
- Whether C's initial context causes anchoring or interference that harms B-like
  independent reasoning.
- Whether Story 0184's current two actions are sufficient for historical and
  cross-document questions.
- Whether provider behavior is stable enough for paid comparison.

## References

- `docs/investigations/story-context-agent-to-bounded-analyst-and-retrieval-strategy.md`
- `docs/stories/0177-evaluate-hybrid-retrieval-for-story-context/`
- `docs/stories/0179-story-context-runtime-loss-evaluation/`
- `docs/stories/0180-runtime-evaluation-readiness-and-isolation/`
- `docs/stories/0183-authoritative-candidate-trust-fixture/`
- `docs/stories/0184-bounded-story-context-investigation/`
- `docs/decisions/ADR-006.md`, `ADR-063.md`, `ADR-066.md`, `ADR-067.md`
- `docs/evaluation/comparative-baseline-v4-official-collection-report.md`
