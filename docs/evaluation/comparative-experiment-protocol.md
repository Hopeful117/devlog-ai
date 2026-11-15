# Comparative Experiment Protocol

## Status

`PROTOCOL PROPOSED - HUMAN AUTHORIZATION REQUIRED`

No provider campaign is authorized by this document.

## 1. Experimental Unit

The unit is one `(case, treatment, repetition, frozen repository revision)`.
Treatments are A, B, and C. Every unit receives the identical question and
answer contract. A unit has one immutable execution identity and one bounded
artifact directory.

## 2. Treatment Isolation

### A

Use the current Core one-shot Story Context path with its effective production
configuration recorded, without changing budgets, prompts, selection, or
grounding policies.

### B

Use the existing V4 direct runtime/tool adapter, or a separately versioned
successor if it is required for completion. Provide only the question,
revision, answer schema, and typed read-only tools. Do not provide DevLog
context, oracle material, expected evidence, evaluator hints, prior answers, or
diagnostics.

### C

Use the same tools, model, answer schema, and safety contract as B. Add only a
Core-authorized DevLog projection and investigation state. Record exactly which
references were supplied and their digest. C must not receive material that B
cannot obtain through the authorized repository boundary unless the comparison
explicitly defines that as the treatment difference.

## 3. Fairness Rules

- Same model/provider and SDK family where possible; record exact versions.
- Same repository commit and source identity.
- Same question wording and answer schema.
- No oracle, expected claim, reference answer, or hidden hints in prompts.
- No shared mutable conversation or tool cache across observations.
- Fresh process or isolated execution state per observation where feasible.
- Do not equalize tool calls or tokens artificially; measure actual consumption.
- Compare quality conditional on evaluability, and report technical completion
  separately.
- Never turn a timeout or provider-incomplete result into a wrong answer.
- Never count an unevaluated answer as a semantic failure.

### Q1 Asymmetric Pilot Exception

The first DevLog AI Q1 pilot is explicitly an
`END_TO_END_SYSTEM_COMPARISON`, not a controlled context-value comparison.
Conditions A, B, and C retain their actual current entry points and capability
envelopes. The pilot records the asymmetry rather than hiding it behind a
common tool subset. The normalized claim/evidence contract is used only after
execution for evaluation and does not constrain production inputs.

The controlled comparison that holds model, tools, prompts, and answer schema
constant while varying only authorized DevLog context is deferred until the
asymmetric pilot establishes that the three paths are technically observable.
No result from the asymmetric pilot may be reported as an isolated causal
effect of DevLog context.

## 4. Bounded Investigation Policy

For B and C, the initial protocol should use explicit ceilings:

- maximum model turns;
- maximum provider calls;
- maximum tool operations;
- maximum read bytes per operation and cumulative bytes;
- maximum elapsed time;
- maximum action/evidence items;
- maximum output tokens per provider response.

The values must be approved and frozen before collection. A guard is a
technical stop, not a quality score. The stop record must identify the phase:
planning, retrieval, verification, or synthesis.

The agent loop is:

```text
question -> objective -> plan -> search/read/history
  -> evidence ledger -> hypothesis/contradiction check
  -> optional bounded follow-up -> synthesis -> deterministic validation
```

Stop when sufficient evidence is declared and valid, the budget is exhausted,
no authorized action can reduce uncertainty, a capability is rejected, the
revision is invalid, or the provider/runtime fails.

## 5. Instrumentation

Capture bounded metadata only:

- treatment, case, revision, prompt/schema/runtime/provider identities;
- initial and follow-up evidence reference manifests;
- selected, discarded, retrieved, duplicate, rejected, and grounded counts;
- action purpose and result identity, not hidden reasoning;
- provider attempts, statuses, incomplete reasons, retries, and latency;
- input/output/total tokens when supplied by the provider;
- tool operations, bytes, and failures;
- final answer status and evaluator status;
- hashes linking RAW and derived artifacts.

Do not persist credentials, full prompts unless the approved artifact policy
requires a redacted form, hidden chain-of-thought, or unbounded repository data.

## 6. Collection Sequence

1. Freeze the Q1 oracle and treatment manifest, including its three explicit
   observation identities.
2. Run provider-free preflight and deterministic replay checks.
3. Execute one technical validation per treatment on one low-risk case.
4. Stop for human review if any treatment cannot reach a usable response.
5. If technically valid, complete the three Q1 observations in isolated
   processes. Repetitions and randomized order require a separate approved
   campaign manifest.
6. Run deterministic replay and integrity validation before human scoring.
7. Blind and score all semantically eligible answers.
8. Adjudicate disagreements and publish aggregate results with exclusions.

No second attempt, tuning, retry, provider change, or official collection is
automatic after a technical failure. The current repository history shows why:
V4 has had provider-incomplete and parse-failure outcomes that prevented a
quality conclusion.

## 7. Analysis Plan

Primary endpoint: mean and median weighted answer-quality score among
semantically eligible observations, with paired case-level A/B/C comparisons.

Secondary endpoints:

- technical completion and semantic eligibility rates;
- exact required-claim recall and unsupported-claim rate;
- evidence support and canonical-reference resolution;
- correct abstention and contradiction handling;
- useful-answer rate by case category;
- actual tokens, tool calls, bytes, latency, and cost;
- stop reasons and investigation depth.

Report confidence intervals or bootstrap intervals for paired differences when
sample size permits. Do not claim statistical superiority from eight cases alone;
use the benchmark as decision evidence and identify which findings require a
larger corpus.

## 8. Decision Rules

- C is promising only if its quality advantage survives human adjudication,
  grounding gates, and technical eligibility accounting.
- B is preferred over C if it achieves equal or better quality with fewer
  unsupported claims and C's added context provides no reliable benefit.
- A remains preferred if it is tied on quality and materially simpler/cheaper,
  or if active investigation increases unsupported claims or failures.
- No architecture decision is valid if provider/runtime failures make a
  treatment's semantic eligibility materially incomparable.
- RAG is considered only after candidate-level evidence shows a persistent
  recall gap not solved by existing deterministic targeted search/read.

## 9. Reproducibility and Governance

Every report must name repository revision, branch/commit, treatment/runtime
identity, model/provider, schema/prompt versions, tool schema digest, oracle
digest, environment class, and artifact hashes. Historical artifacts remain
immutable. Results remain evaluation evidence, not trusted project knowledge.
