# DevLog Comparative Baseline V3 Protocol Diagnosis

## Status and Scope

This document is an evidence-based diagnosis of the preserved V3 comparative
baseline. It proposes candidate V4 changes only. It does not change V3 data,
the V3 notebook, the collection runtime, the evaluator, the questions, or the
oracle.

The primary evidence is the immutable attempt
`official-20260916T213831Z-deb74ef1` under
`data/comparative-baseline/story0133-comparative-baseline-1.0.0/attempts/`:

- `collection-metadata.json` records the official/live identity, frozen
  repository revision, execution order, and excluded prior attempt.
- `derived/observations.csv` records the normalized 18-row result set.
- `derived/completeness.json` records the expected and finalized assignment
  shape.
- `raw/*.json` records the provider responses, tool traces, tool errors, byte
  accounting, and derived validation states.
- `collection-summary.json` records 18 finalized results, 36 logical model
  calls, 37 provider calls, and one technical retry.

The immutable baseline is interpreted together with the accepted grounding and
scoring amendment in
`docs/evaluation/comparative-baseline-protocol-v1-grounding-scoring-amendment.md`.

## 1. Intended Measurement

The original research question is:

> Does structured context produced by DevLog add measurable value compared with reading the repository directly, for an agent and for a human?

V3 executed only the two AI conditions. `HUMAN_DIRECT` remained a frozen,
non-executable policy identity and is not part of the 18 observations.

The intended measurement is not a comparison of formatting skill or citation
syntax. It is a comparison of project understanding under two context
conditions:

| Element | Intended meaning |
|---|---|
| Independent variable | Context-acquisition condition: prepared frozen DEVLOG context versus autonomous bounded AGENT_DIRECT repository inspection. |
| Primary outcome | Correct grounded answer, measured only when the answer is structurally valid, evidence-grounded, and semantically correct. |
| Primary diagnostic outcomes | Structural validity, grounding validity, semantic eligibility, semantic correctness, and correct-grounded result, each reported separately. |
| Efficiency outcomes | Context preparation or repository/tool work, input/output tokens, provider/model calls, latency, context/evidence bytes, repository bytes, and operation counts. |
| Grounding role | Establish that an answer's cited evidence is authorized, resolvable against the frozen snapshot, and faithfully identified. It is an evidence-fidelity condition, not a causal-truth judgment. |
| Semantic role | Determine whether the answer's substantive conclusion matches the frozen oracle, including causal correctness or correct abstention where applicable. |
| Repository navigation role | Measure the cost and behavior of acquiring context in AGENT_DIRECT. Search and reads are part of that condition, not an error to erase by giving the condition prepared evidence. |

The different acquisition mechanisms are intentional. DEVLOG supplies a
preconstructed context projection; AGENT_DIRECT must discover relevant context
from the pinned repository. Fairness therefore means a common question,
repository revision, model/provider, final answer contract, evaluator, and
oracle, while capturing the condition-specific acquisition work. It does not
require identical interaction paths.

The condition-specific difference must nevertheless leave both conditions a
reasonable opportunity to produce a final answer that can be evaluated. If a
limit systematically stops one condition before a final answer, the result is
a diagnostic of the limit and not a semantic comparison.

## 2. V3 Outcome Summary

The assignment matrix was complete and balanced: three questions, two AI
conditions, and three repetitions produced 18 finalized observations, with 9
per condition. The frozen execution class was `OFFICIAL_BASELINE`, the mode was
`LIVE`, and all observations used repository revision
`18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149`.

| Condition | Assigned | Structurally valid | Grounding valid | Semantically eligible | Semantic correctness |
|---|---:|---:|---:|---:|---|
| `DEVLOG` | 9 | 7 | 0 | 0 | Not evaluated |
| `AGENT_DIRECT` | 9 | 0 | 0 | 0 | Not evaluated |

V3 is a **partially valid baseline**. It measured execution completeness and
failure progression, but it did not measure semantic accuracy or comparative
effectiveness. `semantic_correct = NOT_EVALUATED` is not a semantic score and
must not be reported as 0% accuracy.

## 3. DEVLOG Diagnosis

### 3.1 Observed failures

Seven DEVLOG responses were received as complete final responses and parsed far
enough to be structurally valid. All seven then failed deterministic grounding.
The persisted diagnostics include:

- `CASE-01-COMPARATIVE` repetitions 2 and 3: line locators exceeded content
  bounds.
- `CASE-03` repetitions 2 and 3: comparative evidence excerpts did not match
  resolved content.
- `CASE-04` repetition 1: line locator exceeded content bounds.
- `CASE-04` repetitions 2 and 3: comparative evidence excerpts did not match
  resolved content.

The raw final answers show the mechanisms behind these classifications: they
contained plausible claims and cited references, but some excerpts were
paraphrased or abbreviated rather than exact captured substrings, and some
line ranges did not resolve within the authoritative content. The offline
investigation additionally identified oversized excerpts and invalid or
out-of-range locators among the failure mechanisms. These are evidence-fidelity
failures, not proof that every substantive claim was wrong.

Two DEVLOG responses failed structural validation before grounding:

- `CASE-01-COMPARATIVE:r1`
- `CASE-03:r1`

Their raw artifacts contain a completed provider response and usage, but no
parsed response. The normalized data therefore correctly records structural
failure and leaves grounding and semantic evaluation unevaluated. This is a
model-output/contract boundary failure, not evidence that the corresponding
answer would have been semantically incorrect.

### 3.2 Cause and measurement assessment

| Mechanism | Likely owner | Necessary for the research question? | What V3 measured in practice | Evidence-supported implication |
|---|---|---|---|---|
| Invalid or out-of-range locators | Model behavior, exposed locator contract, and deterministic validation | Some locator authority is necessary for auditable comparison; exact line-range syntax is not itself the research outcome. | Ability to emit locator syntax that survives the frozen contract. | Preserve deterministic resolution, but make the provider-visible locator domain and boundaries unambiguous and test them offline before collection. |
| Paraphrased, abbreviated, oversized, or non-matching excerpts | Model behavior under an exact-substring grounding contract | Evidence identity/fidelity is necessary; exact reproduction of long text is only an indirect way to establish it. | Citation/excerpt fidelity and contract compliance, with semantic content censored after failure. | Keep exact-match grounding as a separate validity dimension, while allowing a separate semantic diagnostic for parseable answers that fail grounding. |
| Evidence that is semantically appropriate but unauthorized or unresolved | Evaluator design boundary | Authorization and frozen-snapshot resolution are necessary to prevent fabricated or unverifiable support. | Whether the answer cited an item accepted by the evidence authority, not whether its reasoning was useful. | Keep authorized-universe and resolver checks. Do not use expected-correct evidence to repair or credit a citation. |
| Structurally complete-looking final answers rejected before grounding | Model behavior and common response contract | A common schema is necessary for comparable extraction. | Whether the model filled the envelope, rather than semantic understanding. | Retain structural validity, but report structural and semantic dimensions independently where the response is parseable. |

### 3.3 What grounding must establish

The V3 requirements are not interchangeable:

1. **Semantic appropriateness** asks whether the evidence supports the claim.
   This belongs to semantic/oracle evaluation and was not established by the
   grounding layer.
2. **Authorization** asks whether the cited item belongs to the condition's
   captured allowed evidence universe. This prevents post-hoc access to hidden
   evidence.
3. **Resolution** asks whether the reference and typed locator identify content
   in the frozen repository/evidence snapshot.
4. **Exact excerpt occurrence** asks whether the submitted excerpt is faithful
   to the resolved content. This protects against invented or silently altered
   quotations.
5. **Locator correctness** asks whether the requested line/section/hunk is
   valid in that same content domain.
6. **Causal correctness** asks whether the conclusion matches the oracle. It
   must not be inferred from any of the preceding properties.

Exact-substring grounding should therefore not be removed merely because it
blocked V3. Removing it would lose an important guarantee: the evaluator could
no longer distinguish a citation to the captured source from a plausible
paraphrase or fabricated excerpt. The evidence supports a separation of
dimensions, not the removal of evidence fidelity.

The smallest supported direction is to retain exact grounding for the primary
`correct_grounded_answer` outcome, add independent semantic diagnostics for
parseable structurally valid answers, and improve the provider-visible locator
representation so a model can cite the same byte/text domain that the
evaluator resolves. Any relaxed or alternate citation representation would
need a new frozen contract and a demonstrated one-to-one mapping to immutable
source content.

## 4. AGENT_DIRECT Diagnosis

### 4.1 Seven bounded-agent failures

The seven observations classified post hoc as
`LEGITIMATE_BOUNDED_AGENT_FAILURE` are:

| Observations | Trace pattern | What was discovered before stopping |
|---|---|---|
| `CASE-01-COMPARATIVE:r1-r3` | Three successful searches returned 3,450, 67, and 5,092 bytes. The next `read_file` request exceeded the remaining question budget; the following read was not executed. | ADR-042, no match for `Story-0039`, and matches for PAPER provisioning. The agent had not read the authoritative ADR/story content needed to finish. |
| `CASE-03:r1` | One successful search returned 3,768 bytes and one read returned 15,342 bytes. The next ADR-043 read was rejected at the byte limit. | A PAPER settlement search and the Story-0042 repository analysis had been found, but the agent had no final answer. |
| `CASE-03:r3` | Four successful searches returned 3,898, 3,768, 78, and 82 bytes. The next read was rejected at the byte limit; a subsequent read was not executed. | Several PAPER-related search results, but no authoritative file read sufficient for a final answer. |
| `CASE-04:r2` | One search returned 3,618 bytes; the ADR-043 read was rejected at the byte limit. | ADR-043 had been located, but its content was not available to the model before termination. |
| `CASE-04:r3` | Two successful searches returned 3,618 and 2,826 bytes. The ADR-043 read was rejected and the Story-0042 read was not executed. | Both target concepts had been located, but the agent could not inspect their authoritative content. |

The raw traces show successful repository operations followed by
`BudgetExhausted` errors. The configured cumulative repository budgets were
32,401 bytes for CASE-01, 31,683 for CASE-03, and 13,504 for CASE-04, with a
maximum of six tool operations and eight model turns. The rejected reads were
large file results (for example 39,296 or 35,376 bytes), so the agent reached a
limit while attempting ordinary evidence acquisition rather than after
producing a final answer.

These observations do measure a real cost of direct repository exploration:
search selection and file-reading choices can be inefficient. But they do not
measure semantic direct-repository performance because no final answer was
produced and useful evidence was often discovered only as search metadata.
The budget therefore acted as both a safety limit and a censoring mechanism.

### 4.2 Two model-output failures

The two observations classified as `MODEL_OUTPUT_FAILURE` are:

- `CASE-03:r2`: six repeated `search_repository` calls supplied `pattern` but
  omitted the required `query`. Each call failed with `RuntimeContractError`;
  the next operation was rejected because the six-operation budget was
  exhausted.
- `CASE-04:r1`: the same pattern occurred with `term` instead of `query`.
  Six invalid calls consumed the operation budget, followed by a non-executed
  budget-exhausted call.

The runtime returned deterministic validation errors and enforced the frozen
operation limit. There is no evidence of a runtime infrastructure defect.
The model did not adapt its tool arguments after the first error, so these
slots measured schema-recovery behavior plus operation-budget exhaustion, not
repository understanding.

### 4.3 Protocol implication

AGENT_DIRECT must retain autonomous navigation. Giving it the expected evidence
list would destroy the intended condition. However, a tool schema error that is
repeated six times leaves no opportunity to answer and is not informative about
semantic direct inspection. V4 should therefore make tool-call validity
observable as a diagnostic and decide, before collection, whether a bounded
contract error may receive a deterministic non-semantic recovery path. Any
recovery must be frozen, applied identically to all matching tool errors, and
counted as condition-specific interaction cost; it must not inject evaluator
hints or repair the answer.

For byte exhaustion, the evidence does not justify an arbitrary budget increase.
It does justify qualifying whether the selected per-question cap permits at
least one ordinary search/read/final-answer path. Without that qualification,
the direct condition can be declared complete while remaining semantically
unobservable.

## 5. Resource-Budget Fairness

V3 matched AGENT_DIRECT cumulative repository byte budgets to DEVLOG
provider-visible evidence bytes. This is a defensible first safety control, but
not a directly equivalent efficiency measure:

- DEVLOG bytes are already-selected context delivered to the model.
- AGENT_DIRECT bytes include search-result metadata, navigation overhead,
  irrelevant content, file granularity, and failed or rejected attempts.
- The model's final prompt input, repository bytes accessed, and useful evidence
  bytes are different quantities.
- A direct read can request one large file even when only a small section is
  relevant; a prepared projection can package selected sections without the
  discovery cost.

The V3 recorded values illustrate the problem. DEVLOG provider-visible context
was 32,401 bytes for CASE-01, 31,683 for CASE-03, and 13,504 for CASE-04. The
AGENT_DIRECT successful repository-byte totals in the derived observations were
not equivalent semantic payloads: for example, CASE-01 slots recorded 8,609
bytes after searches while the decisive file read was rejected; CASE-03 and
CASE-04 slots similarly stopped after partial search/read work. Comparing those
numbers as equal context consumption would reward the condition that was
allowed to select its content in advance.

Recommended classification of controls:

| Measurement/control | V4 treatment |
|---|---|
| Maximum wall clock, operation count, output limits, and cumulative repository bytes | Hard safety/runtime constraints, frozen before calls and reported as censoring events when hit. |
| Repository bytes requested, returned, rejected, and useful evidence bytes | Observed AGENT_DIRECT dependent variables; preserve each separately. |
| DEVLOG context preparation bytes/time and provider-visible context bytes | Observed DEVLOG dependent variables; do not equate them with direct repository bytes. |
| Input/output tokens, provider calls, tool calls, latency, and cost | Observed efficiency outcomes, with final-answer and navigation calls separated. |
| Budget adequacy | Pre-run qualification criterion, not a post-hoc adjustment to rescue V3. |

Candidate V4 change: retain bounded direct exploration as an experimental
condition, but freeze a budget only after an offline qualification demonstrates
that the budget can contain a representative valid search/read/final-answer
path for each frozen question. The qualification must not use the expected
answer as a hint. The exact budget and any deterministic tool-error policy
remain human decisions; V3 does not prove a particular replacement value.

## 6. Evaluator-Gating Analysis

V3 used the pipeline:

`execution -> structural validation -> grounding validation -> semantic evaluation`

Strict gating is correct for the primary outcome: a result cannot be called a
correct grounded answer if its structure or evidence cannot be trusted. It also
prevents invalid slots from silently becoming semantic zeroes. Those properties
must remain.

The cost is visibility. Seven DEVLOG responses were structurally valid and
contained answer text, claims, and relationship results, but a grounding
failure made their semantic correctness invisible. A semantically correct
answer with an imperfect excerpt therefore has no semantic diagnostic under the
current single path. This conflates eligibility for the grounded primary
outcome with observability of substantive answer correctness.

The supported V4 design direction is a layered evaluator, not a weakened one:

| Layer | Question | Effect of failure |
|---|---|---|
| Structural validity | Is there a parseable, contract-compliant answer? | No semantic evaluation if no answer can be reliably extracted. |
| Grounding quality | Are references authorized, resolvable, and exact against the frozen snapshot? | Exclude from correct-grounded outcome, but preserve diagnostic grounding dimensions. |
| Semantic correctness | Does the answer match the frozen oracle? | Report separately when a structurally valid answer is available. |
| Causal correctness/abstention | Is the causal conclusion or negative-control abstention correct? | Report only for the applicable frozen question and valid semantic assessment. |

The semantic diagnostic must not credit missing evidence, repair locators, use
expected-correct evidence, or alter the primary denominator. It should be
explicitly labeled `semantic_only` or equivalent and reported with its own
eligibility and benchmark-validity fields. The primary result remains
`correct_grounded = structural AND grounding AND semantic`.

## 7. Diagnostic Matrix

The matrix connects each V3 observation class to a candidate response. Candidate
changes are proposals, not implementation decisions.

| V3 observation | Evidence | Root cause | What V3 was measuring in practice | Protocol implication | Candidate V4 change | Risk introduced |
|---|---|---|---|---|---|---|
| DEVLOG `CASE-01:r1`, `CASE-03:r1` | Final response received; `parsedResponse` absent; `structural_valid=NO`; output usage 994 and 898 tokens | Model output did not satisfy the common response contract | Structured-envelope completion and schema compliance | Structural validity remains necessary, but semantic visibility should not depend on an all-or-nothing reporting path when parsing is possible | Preserve strict structural gate; capture parse diagnostics and add no semantic score where no reliable answer exists | A fallback parser could accidentally normalize invalid output or create answer-selection bias |
| DEVLOG `CASE-01:r2-r3`, `CASE-04:r1` | Grounding diagnostic: line locator exceeds content bounds | Model locator behavior and/or unclear locator domain | Citation locator fidelity | Locator authority must remain deterministic and byte-consistent | Publish locator-domain metadata/line bounds in the provider-visible evidence representation and validate with offline fixtures | More locator metadata may increase prompt size or make the task easier in a condition-specific way |
| DEVLOG `CASE-03:r2-r3`, `CASE-04:r2-r3` | Grounding diagnostic: excerpt does not match resolved content; offline review also found paraphrase/size issues | Model excerpt behavior under exact-substring contract | Quotation fidelity rather than semantic appropriateness | Exact grounding remains necessary for trusted primary results but must be separable from semantic correctness | Add semantic-only diagnostic for structurally valid, ungrounded responses; retain exact-match primary gate | Semantic-only scores could be misread as grounded scores unless names, denominators, and reporting are strict |
| All 7 valid DEVLOG answers | `structural_valid=YES`, `grounding_valid=NO`, `semantic_eligible=NO` | Evaluator gating order | Grounding as a censor on semantic observability | Preserve grounded denominator while exposing independent semantic diagnostic | Evaluate semantic correctness independently after structural validity, never repair grounding | More metrics increase interpretation complexity and multiple-comparison risk |
| AGENT_DIRECT CASE-01 all repetitions | Three searches succeeded, then 39,296-byte read rejected against 32,401-byte budget | Budget exhaustion during large authoritative read | Search/navigation overhead plus hard censoring | Current cap did not provide a reasonable path to final answer | Pre-qualify budget adequacy; retain cap as a reported safety constraint | A larger cap can increase cost and may change the measured direct-exploration task |
| AGENT_DIRECT CASE-03:r1, r3; CASE-04:r2, r3 | Searches/partial reads succeeded, decisive reads rejected at 35,376-byte result against remaining budget | Budget exhaustion after partial discovery | Partial context acquisition, not semantic direct performance | Separate useful bytes from requested/rejected bytes and classify censoring | Freeze question-specific qualified caps and report rejection point and useful evidence discovered | Qualification may encode assumptions about a representative navigation path |
| AGENT_DIRECT CASE-03:r2, CASE-04:r1 | Six repeated calls omit required `query`; `RuntimeContractError`; operation budget exhausted | Model tool-schema recovery failure | Tool-call validity and failure recovery | Runtime was correct, but no semantic opportunity remained | Consider a frozen, uniform, non-hinting schema-error recovery policy or retain as explicit failure diagnostic | Recovery changes interaction cost and could mask a meaningful agent weakness |
| All 9 AGENT_DIRECT observations | `structural_valid=NO`, no final answer, no grounding/semantic stage | Combination of bounded exploration and model tool behavior | Whether the agent could navigate within V3 limits | Complete execution is not sufficient evidence of comparable semantic opportunity | Add a predeclared opportunity/evaluability report alongside execution completeness | A new opportunity metric could be used to excuse genuine direct-condition inefficiency |

## 8. Candidate Changes by Necessity

### Required

These are the smallest changes supported by V3 that are likely necessary for a
valid measurement rather than merely a successful run:

1. **Separate semantic observability from grounded-primary eligibility.** Keep
   structural and exact grounding gates for `correct_grounded_answer`, but
   evaluate semantic correctness independently for structurally valid answers
   whose grounding failed. Preserve separate denominators and validity flags.
2. **Qualify direct-condition opportunity before the full run.** For every
   frozen question, demonstrate offline that the frozen tool policy and budget
   permit a representative search/read/final-answer path. A slot stopped by a
   budget must remain a budget-censored diagnostic, not a semantic failure.
3. **Make the locator domain explicit and consistent.** The provider-visible
   evidence, direct tool result, and evaluator must expose and resolve the same
   immutable textual bytes and line model. Exact substring matching remains.
4. **Preserve complete failure attribution.** Record tool argument validation,
   operation index, requested/returned/rejected bytes, final-answer presence,
   and the first stopping reason as separate fields. Do not collapse model tool
   errors or budget exhaustion into infrastructure failure.

### Experimental-design improvements

These do not necessarily block execution, but improve interpretability:

1. Report context preparation cost for DEVLOG separately from online answering
   cost, and direct navigation cost separately from final-answer cost.
2. Report requested, returned, rejected, and useful evidence bytes rather than
   one `repository_bytes` total.
3. Define semantic metrics with explicit `eligible`, `observedDenominator`,
   `expectedDenominator`, `benchmarkValid`, and reason fields.
4. Add a small offline smoke fixture covering valid locators, out-of-range
   locators, paraphrased excerpts, exact repeated excerpts, unauthorized
   references, and tool schema errors before any provider calls.
5. Retain condition-specific navigation and tool operations as outcomes rather
   than trying to normalize them into DEVLOG context bytes.

### Optional

These are not strongly enough justified by V3 to include automatically:

1. Increasing the AGENT_DIRECT budget to a particular numeric value without a
   predeclared adequacy qualification.
2. Automatically retrying malformed tool calls. V3 supports considering a
   uniform recovery policy, but does not establish that retrying is preferable
   to measuring schema discipline.
3. Relaxing exact-substring grounding or replacing it with semantic similarity.
   V3 shows that exact matching censored semantic visibility, not that source
   fidelity is unnecessary.
4. Changing the frozen questions, oracle, expected causal relationships, model,
   or provider because the observed answers were poor.

## 9. Frozen Elements and Protection Against Moving the Target

The following should remain frozen for V4 unless a separate human-approved
protocol decision gives a documented reason:

- the three question identities, wording, and versions;
- the oracle, expected causal relationships, positive/negative classification,
  and semantic mapping;
- the repository identity and pinned revision;
- the DEVLOG and AGENT_DIRECT condition meanings;
- the common final answer intent and evaluator-facing contract semantics;
- provider/model identity and common final-answer generation settings;
- evidence authorization boundaries and the requirement for immutable,
  deterministic grounding;
- repetition count, assignment matrix, and pairing rules.

The condition-specific acquisition mechanisms must also remain distinct:
DEVLOG must not be given a hidden repository-navigation path, and AGENT_DIRECT
must not receive the expected evidence list or DevLog projection. Any changed
output envelope, tool recovery rule, locator representation, or budget is an
execution-configuration change that requires a new identity and fresh
qualification; it must not overwrite or relabel V3.

V3 does not require a question or oracle change. Poor semantic performance was
not observed because semantic evaluation ran and failed; it was not observed at
all. Changing the benchmark to improve pass rates would move the target.

## 10. Proposed V4 Validity Criteria

V4 should be judged on whether it can measure the possible outcomes, not whether
DEVLOG wins.

### Execution completeness

- Every frozen assignment is present exactly once, or the run is explicitly
  incomplete.
- Every assignment has an immutable raw artifact, ordered attempts, and safe
  provider/tool metadata.
- Technical failures, tool failures, budget exhaustion, and missing slots are
  distinct states.
- The run manifest, condition policies, repository revision, model/provider
  configuration, and evaluator identities are hash-consistent.

### Structural evaluability

- Both conditions can produce the same evaluator-facing answer contract.
- The final-answer envelope is proven adequate by offline maximum/legal fixtures
  before collection.
- Structural validity, parse diagnostics, and final-answer presence are
  independently reported.
- No malformed or truncated response is silently repaired into a valid answer.

### Grounding evaluability

- Provider-visible evidence and evaluator evidence use the same immutable content
  and locator domain.
- Authorization, resolution, locator correctness, excerpt exactness, and digest
  results are independently recorded.
- Grounding failures exclude an observation from the grounded primary outcome,
  but do not erase structural or semantic-only diagnostics.
- Expected-correct evidence cannot repair, redirect, or credit a citation.

### Semantic evaluability

- Every structurally valid final answer has a deterministic semantic assessment
  path, including answers that fail grounding, with the result labeled by its
  grounding status.
- Semantic correctness and causal/abstention correctness are scored only
  against the frozen oracle and question-specific rules.
- `NOT_EVALUATED` is used when no reliable answer exists, never as a semantic
  zero.
- The primary `correct_grounded_answer` result retains its strict structural,
  grounding, and semantic conjunction.

### Comparative interpretability

- Both conditions have a declared opportunity to reach a final answer; any
  censoring by budget, wall clock, or tool policy is measured and reported.
- Direct navigation cost and prepared-context cost are reported as distinct
  condition-specific efficiency outcomes.
- Semantic results include eligible and expected denominators, execution
  completeness, and benchmark validity.
- The result can validly support DEVLOG advantage, AGENT_DIRECT advantage, no
  meaningful difference, mixed results, or insufficient evidence.
- No conclusion of superiority is required for protocol validity.

## 11. Unresolved Human Decisions

1. Should malformed repository-tool arguments receive one deterministic,
   contract-defined recovery opportunity, or should each malformed call remain
   a measured agent failure? If recovery is allowed, what is its exact uniform
   cost and stopping rule?
2. What pre-run fixture constitutes a representative but non-answer-leaking
   direct search/read/final-answer path for budget adequacy qualification?
3. Should the direct byte limit remain question-specific and matched to the
   prepared evidence volume, or should V4 freeze a different safety policy while
   treating bytes only as an observed cost?
4. What provider-visible locator metadata is sufficient to make line boundaries
   and excerpt domains unambiguous without supplying expected evidence or
   otherwise changing condition difficulty?
5. What exact semantic-only reporting contract should be used for structurally
   valid but ungrounded responses, and how will its separate denominator be
   presented so it cannot be confused with grounded accuracy?
6. Is the existing common answer envelope adequate for the frozen questions
   after offline maximum-size serialization tests, or is an execution-only
   envelope revision required? Any revision must receive a new configuration
   identity.

## Proposed V4 Direction

Do not repair V3 by changing its questions, oracle, or condition meanings. The
smallest defensible V4 direction is to preserve strict, exact, deterministic
grounding for the grounded primary outcome while adding an independent semantic
diagnostic for structurally valid answers; qualify the AGENT_DIRECT budget and
tool policy as capable of reaching a final answer before the full run; make
locator and byte accounting explicit; and report censoring separately from
semantic performance. This would make both context-acquisition conditions
observable without giving either condition the other's acquisition mechanism.

The direction remains a proposal. It does not authorize runtime implementation,
provider calls, budget changes, or V4 collection.
