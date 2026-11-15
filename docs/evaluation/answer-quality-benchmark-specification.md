# Answer Quality Benchmark Specification

## Status

`SPECIFICATION - ORACLES REQUIRE HUMAN REVIEW`

Benchmark version: `devlog-ai-abc-v1.0.0`

This revision fixes the first-campaign corpus to DevLog AI only. Trading OS is
deferred to a separate benchmark after this method and its oracles are reviewed.

The benchmark is proposed, not executed. It must not be used as a production
quality claim until each oracle is independently reviewed and frozen.

## Corpus Rules

- Eight cases, all concerning DevLog AI for the first campaign.
- Each case pins a repository commit, source identity, question version, oracle
  version, and treatment-independent evidence manifest.
- Reference answers are written from repository evidence first, then reviewed by
  at least two engineers who did not run the treatment.
- The oracle records required claims, acceptable qualified variants, required
  evidence, forbidden claims, and correct abstentions.
- A missing provider result is `NOT_EVALUATED`, never an incorrect answer.

## Proposed Cases

| ID | Category | Exact question | Expected evidence |
|---|---|---|---|
| Q1 | Architecture | How does a Story Context request move from Core authorization to the final persisted analysis, and which side owns each invariant? | ADR-067, Story 0184 contracts, Java submission/callback services, Python generation service |
| Q2 | Code behavior | When an investigation action is repeated, what happens to repository reads and budget usage? | `StoryContextInvestigationActionService`, idempotency tests, task snapshot behavior |
| Q3 | Historical decision | Why does the current design keep Core authoritative and Python repository-blind? | ADR-006, ADR-063, ADR-067, relevant commits and Story 0184 rationale |
| Q4 | Impact analysis | If `READ_EVIDENCE_RANGE` limits or evidence projection change, which validation, grounding, and tests are affected? | Capability contracts, validator, execution service, callback/grounding tests |
| Q5 | Code/document inconsistency | Which claims in older Story 0184 repository artifacts no longer describe the current branch, and why? | Current commit/branch, story-local reports, changed files, current implementation |
| Q6 | Sequential investigation | What evidence is needed to decide whether a weak answer came from candidate loss, projection loss, or synthesis failure? | Loss ledger, projection code, provider traces, V4 reports, targeted reads |
| Q7 | Insufficient evidence | Does the current repository prove that the bounded analyst improves real engineering answer quality? | Existing tests/reports only; correct answer must distinguish technical readiness from quality evidence and abstain on superiority |
| Q8 | Contradiction/history | Do the 0177 fixture result and V4 live result justify production hybrid retrieval or direct-agent adoption? | 0177 report, V4 reports, ADR-063, current branch state; answer must separate fixture evidence, runtime failure, and architecture conclusion |

Q1-Q8 remain the exact question texts from the strategic investigation. They are
versioned as `1.0.0` in `docs/evaluation/devlog-ai-abc-benchmark-v1.json` and
are benchmark candidates, not frozen oracles. Trading OS use requires a
separate benchmark, authorization, and repository revision. DevLog AI alone is
sufficient for the first dry-run and oracle review.

## Oracle Schema

Each case should freeze:

```text
caseId, version, repositoryId, repositoryRevision, question
requiredClaims[], acceptableQualifiedClaims[], requiredEvidenceReferences[]
forbiddenClaims[], expectedUncertainty[], expectedAnswerClass
```

Claims are scored individually. A claim is not accepted merely because a
similar phrase appears in the answer; its scope, direction, qualification, and
revision must match the reference.

## Scoring Rubric

| Criterion | Weight | Scoring guidance |
|---|---:|---|
| Technical accuracy | 30% | Required claims correct; no material wrong behavior, ownership, direction, or revision |
| Evidence and traceability | 25% | Claims cite canonical locators that actually support them; citations are not decorative |
| Analysis depth | 20% | Reconstructs interactions, history, alternatives, contradictions, and impact where required |
| Engineer usefulness | 20% | Gives actionable understanding, affected surfaces, and next checks without unnecessary prose |
| Uncertainty handling | 5% | Explicitly distinguishes established, inferred, missing, and unknown information |

Hard safety gates apply before the weighted score:

- unsupported material fact;
- wrong revision or fabricated evidence reference;
- trust-boundary violation;
- contradiction presented as settled fact;
- failure to abstain on an oracle-required insufficient-evidence case.

## Bias Reduction

- Blind treatment labels during primary scoring.
- Randomized answer order per case.
- Two independent human raters; adjudication only after independent scores.
- Claim-level rubric with examples written before seeing outputs.
- Do not reward verbosity, tool-call count, or citation count by itself.
- Separate technical eligibility from semantic quality.
- Record inter-rater agreement and unresolved disagreements.
- Use the same oracle and revision for A, B, and C.

## Technical Incompleteness vs Incorrectness

| Outcome | Classification |
|---|---|
| Provider timeout, incomplete response, transport failure, invalid runtime capture | Technical failure; excluded from semantic accuracy denominator and reported separately |
| Valid structured answer with wrong claim | Semantic incorrectness; included in semantic denominator |
| Valid answer that explicitly says evidence is insufficient where oracle expects abstention | Correct semantic behavior |
| Valid answer with a minor non-material citation imprecision | Human-reviewed partial evidence score; not automatically incorrect |
| Missing answer after a valid completed run | Incorrect outcome if execution was semantically evaluable |

## Required Outputs

Per observation: answer contract status, technical status, claims, evidence
references, tool/action trace, token/byte/latency usage, and replay identity.

Per case/treatment: completion rate, semantic eligibility, weighted score,
claim precision/recall, evidence support rate, unsupported-claim rate,
abstention correctness, contradiction detection, p50/p95 latency, actual token
usage, tool calls, and cost where available.
