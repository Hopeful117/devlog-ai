# Repository Analysis - Story 0180

## Scope Reviewed

The analysis covers the approved Story 0180 scope, the current `main` revision
`2a92d65b42e41819b4e3bd915cf486d2f1bc8b83`, the Story Context evaluation runner
and aggregator, the authenticated MCP snapshot path, the Core submission
identity logic, ADR-079, and the Story 0179 runtime artifacts.

The DevLog MCP context service was not available in this environment. The current
repository and the bounded runtime capture were used as the authoritative sources
for this analysis.

## Current Runtime Evidence

The new `C0-current` campaign ran four Story 0158 scenarios with three repetitions
each and produced twelve terminal rows:

- all twelve execution statuses were `COMPLETED`;
- all twelve captures were marked invalid by the runner;
- only four distinct task IDs were returned, each reused three times;
- no row contained an analysis result;
- no row contained a versioned loss ledger or loss reasons;
- observed context budget was `6000` for every row while the profile declared
  `32000`;
- freshness revisions were `0272f37...` for three rows and `0630e42...` for nine
  rows;
- current repository `HEAD` was `2a92d65...`;
- the deterministic aggregator rejected the dataset on duplicate task IDs before
  it could produce a valid aggregate.

This is a runtime-readiness failure, not evidence of a selection, provider, or
model-quality regression.

## Relevant Implementation

### Evaluation Runner

`ai-engine/evaluations/story_context_budget_live.py`:

- loads the fixed Story 0158 scenario manifest;
- sends the same scenario question and files for each repetition;
- generates a distinct `idempotencyKey` containing a UUID nonce;
- retrieves the authenticated snapshot by returned `aiTaskId`;
- records bounded digests, freshness, budget, analysis availability, and loss
  accounting;
- validates the row only after capture, but has no target-revision argument or
  readiness preflight;
- does not stop a campaign when a task is reused, the snapshot is incomplete, or
  the runtime budget differs from the profile label.

### Aggregator

`ai-engine/evaluations/story_context_runtime_report.py`:

- validates bounded rows against the expected Story 0158 scenario identities and
  repetitions;
- rejects duplicate task IDs and duplicate scenario/repetition pairs;
- validates revision, digest, ledger, provider, budget, and bounded diagnostic
  fields;
- excludes invalid rows from aggregate diagnostics;
- currently raises on duplicate task IDs rather than producing a separate
  readiness report.

The current behavior is safe for preventing invalid evidence from being reported,
but it is too late in the workflow: the runner performs all repetitions before
the duplicate identity is surfaced.

### Core Submission Identity

`backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java`
constructs two identities:

- `submissionDigest`, based on project, story, intent, question, files, and
  guidance;
- `idempotencyKeyHash`, based on the supplied idempotency key.

`existingSubmission` first checks the idempotency key, then always falls back to
`findBySubmissionDigest`. Therefore, changing the idempotency key does not create
a new task when the logical request has the same submission digest. This explains
why the runtime runner's distinct keys still returned one task per scenario.

Changing that Core behavior would be a production idempotency decision and is
explicitly outside Story 0180.

## Governing Constraints

- ADR-079 requires distinct selection, projection/prompt, provider-output, model
  capacity, and safety-margin diagnostics, with bounded failure semantics.
- ADR-079 requires task and correlation identity in normalized diagnostics and
  fail-closed behavior when safe budget or identity cannot be established.
- Stories 0178 and 0179 require bounded loss accounting, revision correlation,
  explicit provider visibility semantics, and no inference from invalid captures.
- Java/Core remains the sole authority for context construction, revision scope,
  selection, budget policy, grounding, and provider authorization.
- Python evaluation code may validate and report authorized runtime evidence, but
  must not reconstruct context or alter production policy.

## Root Causes and Risks

### Confirmed

1. The Core submission lookup falls back to the logical `submissionDigest`, so
   identical scenario inputs reuse tasks despite distinct idempotency keys.
2. The runner has no explicit target revision or readiness phase before running
   repetitions.
3. The aggregator treats duplicate identity as a fatal report error but has no
   separate bounded readiness output.

### Supported Interpretation

The mixed historical revisions and empty ledgers indicate that the running MCP/
backend workspace or image did not correspond to the intended current evaluation
revision. The capture proves the mismatch, but the current runner does not collect
enough runtime image/workspace metadata to identify the exact stale component.

### Unknown

- Whether runtime revision alignment should be enforced by the runner, by an
  operator preflight, or by both.
- Whether readiness failure should stop immediately or complete a bounded
  diagnostic sweep.
- Whether a future production Story should change Core submission identity
  semantics or leave them unchanged and use unique evaluation inputs.

## Affected Surfaces

- `ai-engine/evaluations/story_context_budget_live.py`: readiness options,
  preflight, task/revision correlation, and bounded readiness output.
- `ai-engine/evaluations/story_context_runtime_report.py`: separate readiness
  classification from evaluable campaign aggregation.
- `ai-engine/evaluations/`: focused deterministic validation for malformed and
  non-ready captures, if no existing test location is more appropriate.
- Story 0180 local reports: readiness evidence and rerun results.

No backend, MCP protocol, database, Docker, provider, or production policy file
needs to change to satisfy the approved Story scope unless analysis during
implementation identifies a required contract conflict. Any such conflict must
stop the workflow and require a separate decision.

## Recommended Implementation Direction

1. Add an explicit expected revision to the runner contract and validate it
   against the authenticated snapshot before a row can become evaluable.
2. Add a bounded readiness state per scenario/repetition, including task identity,
   freshness, ledger presence, digest presence, analysis presence, and budget
   consistency.
3. Detect task reuse immediately and stop or short-circuit according to the human
   decision recorded in Story 0180.
4. Keep readiness failures separate from terminal execution outcomes and never
   include them in loss-stage totals.
5. Reuse the existing aggregator for valid rows, but emit a bounded readiness
   report when the campaign is not evaluable.
6. Rebuild or synchronize the runtime explicitly before the next campaign and
   record the target revision outside provider prompts.

This direction avoids changing Core idempotency semantics and avoids drawing a
policy conclusion from invalid runtime evidence.

## Validation Mapping

- AC-1: target revision tests and mixed/stale revision rejection.
- AC-2: duplicate task and duplicate scenario/repetition tests.
- AC-3: missing ledger, digest, snapshot, and analysis classification tests.
- AC-4: profile-versus-observed budget mismatch tests.
- AC-5: readiness versus terminal provider outcome aggregation tests.
- AC-6: bounded field and redaction tests using malformed captures.
- AC-7: a post-readiness four-scenario runtime campaign with three repetitions.
- AC-8: decision report must state evaluability before recommending any next
  product improvement.

## Documentation and Vault Outcome

The Story-local Repository Analysis is required. No general operator documentation
change is justified yet because the runtime procedure is not a stable supported
operational contract. The vault was not consulted; no vault action is proposed.

## Recommendation

The Repository Analysis supports Story 0180 implementation, subject to the two
human decisions explicitly requested by the Story: readiness failure behavior and
ownership of runtime image/workspace synchronization.

Implementation Planning must not begin until this Repository Analysis is
explicitly approved.
