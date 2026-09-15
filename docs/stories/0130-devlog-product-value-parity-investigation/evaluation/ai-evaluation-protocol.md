# AI Product Evaluation Protocol v1

## Purpose

Measure whether an AI/coding agent gets materially better engineering context
from DevLog than from direct repository reconstruction. This protocol is
separate from the existing intent replay harness and does not treat a mock
provider or prompt-builder test as product evidence.

## Fixed Inputs

- Use `benchmark-suite-v1.json` without changing questions, scope, expected
  identifiers or thresholds.
- Use the pinned Trading OS revision and record the actual imported/context
  revision if they differ.
- Record provider, model, generation settings, task/intent version, context
  digest, selected count, token/serialized size, warnings and truncation.
- Do not provide the oracle or expected answer to the agent or provider.

## Direct Agent Baseline

For each case, run a coding-agent reconstruction using only Git, repository
search, file reading, ADRs, Stories and tests. The agent may use ordinary
repository tools but not DevLog. Record:

```text
caseId =
toolCalls = PENDING_AI_BASELINE_MEASUREMENT
repositoryQueries = PENDING_AI_BASELINE_MEASUREMENT
contextSize = PENDING_AI_BASELINE_MEASUREMENT
followUpSearches = PENDING_AI_BASELINE_MEASUREMENT
returnedEvidence = []
returnedConstraints = []
returnedCausalLinks = []
returnedComponents = []
returnedTests = []
negativeControlStatus = PENDING_AI_BASELINE_MEASUREMENT
```

The baseline is a real experiment. Counts must be captured from the agent run,
not inferred from the number of files in the repository.

## DevLog Agent Evaluation

Run the same case and input through the production AI-facing path. The preferred
path is the actual Core/imported-project context selection followed by the
production provider, validation, persistence and agent projection. If a live
provider is unavailable, run the deterministic suite against a captured,
human-reviewed result and label it `DETERMINISTIC_ONLY`; it cannot establish
live provider quality.

The result adapter must normalize only canonical references and stable oracle
IDs. It must not use string similarity, a keyword scanner or an LLM judge to
turn an unsupported artifact into a match.

## Deterministic Scoring

The evaluator calculates evidence, constraint, causal-link, component and test
set metrics; typed-reference resolution; grounding validity; negative-control
status; context size; warnings; truncation; follow-up count; and pairwise
evidence stability. It emits one machine-readable result per case and a suite
summary. Missing oracle resolution is `INVALID_ORACLE`, not a passing score.

## Live Product Scoring

The live suite repeats each fixed case three times with fixed repository,
provider and input settings. It retains provider wording as non-authoritative
diagnostic data, but scores canonical evidence/constraint/link identities
deterministically. Provider failure, timeout or invalid callback is an
evaluation failure and is not silently replaced by a mock result.

## AI PASS

AI Utility passes only when every mandatory threshold in `metrics.md` passes for
all four cases, including 100% negative-control accuracy and evidence stability.
Engineering correctness is reported separately. A technically valid but
generic or causally unsupported result fails AI Utility.

## Integrity and Gaming Guards

- Production code, prompts and intent definitions must not branch on `CASE-01`
  through `CASE-04` or contain their questions/answers.
- Oracle files are evaluation-only inputs and are never included in production
  context, provider prompts or MCP responses.
- The evaluator rejects a result that claims a benchmark artifact without a
  resolvable canonical reference.
- A benchmark run records the source revision and context digest; a run against
  an unpinned or changed revision is `INVALID_RUN`, not a pass.
- Expected identifiers are kept outside evaluated production payloads and are
  injected only into the evaluator after the result is produced.
- Adding a case, correcting ground truth or changing a threshold requires a
  suite version increment and an explicit human review record.
