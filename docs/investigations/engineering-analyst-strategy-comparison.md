# Engineering Analyst Strategy Comparison

## Status

`STRATEGIC COMPARISON - AWAITING EXPERIMENTAL EVIDENCE`

This document compares three treatments. It is not a recommendation to enable
the analyst or to replace the current Story Context path.

## Treatment Definitions

| Treatment | Definition |
|---|---|
| A - DevLog current | Existing Core-composed, bounded, mostly one-shot Story Context projection and structured generation |
| B - Direct investigator | Provider receives the question and controlled read-only repository/Git/document tools, without DevLog-composed context |
| C - DevLog Engineering Analyst | Provider receives the same controlled tools as B plus an authorized DevLog projection, canonical references, trust/revision metadata, and investigation state |

B and C must use the same tool schema, repository revision, provider, answer
contract, safety controls, and final evaluator. C's additional input must be
identified and hashed; it must not contain the benchmark oracle.

## Capability Matrix

| Dimension | A | B | C |
|---|---|---|---|
| Initial context | Strongly bounded deterministic projection | Question only plus tool contract | Bounded projection plus tools |
| Concrete code inspection | Only if selected/projected | Direct read/search | Direct read/search through authorized Core |
| Git history | Selected history/evidence only | Direct pinned Git tools | Direct pinned tools plus DevLog history/provenance |
| Explicit decisions/stories | Only if selected | Must discover them | Initial candidates and targeted expansion |
| Iterative investigation | Current default one-shot; v2 opt-in | Natural bounded loop in evaluation runtime | Story 0184 v2 loop, explicitly initialized |
| Trust/revision metadata | Core-owned | Tool adapter/evaluator-owned | Core-owned for DevLog evidence and pinned tools |
| Contradiction tracking | Structured output, limited active checking | Agent plan and synthesis | Agent plus Core ledger/audit |
| Traceability | Projection/callback digests | Tool trace and evidence hashes | Tool/action trace, Core ledger, grounding digest |
| Main risk | Wrong composition/weak synthesis | Search wandering, hallucination, weak provenance | Anchoring, context interference, more cost and complexity |

## Expected Strengths and Weaknesses

### A

Strengths:

- Lowest orchestration complexity and latency when one-shot succeeds.
- Strongest current Core governance and deterministic reproducibility.
- Existing production and evaluation path.

Weaknesses:

- Cannot ask for an omitted implementation or history slice during the same run.
- Category concentration and bounded projection can remove answer-critical
  evidence.
- Rich structured input does not guarantee that the model reconstructs a useful
  engineering explanation.

### B

Strengths:

- Can inspect the concrete implementation rather than infer from summaries.
- Can adapt search terms, paths, and Git queries to the question.
- Tests whether active investigation, independently of DevLog composition, is
  the missing capability.

Weaknesses:

- Must rediscover project structure, trust, supersession, and relevant history.
- Can waste calls or converge on plausible but irrelevant evidence.
- Provenance and revision safety must be implemented in the tool boundary, not
  assumed from model behavior.

### C

Strengths:

- Combines structured orientation with concrete verification.
- Can use DevLog references to prioritize or explain evidence while checking the
  implementation directly.
- Best candidate for explicit contradiction and uncertainty reporting if the
  additional context is salient and not redundant.

Weaknesses:

- More input can create anchoring and context interference.
- It may duplicate evidence already available through tools and increase cost.
- It risks becoming a second retrieval/composition architecture unless Core
  remains authoritative.

## Hypotheses

| ID | Hypothesis | Falsifier |
|---|---|---|
| H-A | Most quality loss is composition/projection, so A remains competitive when answer-critical evidence is present | B/C do not improve quality on concrete multi-source cases despite successful tool use |
| H-B | Active investigation is the material missing capability | B materially improves evidence completeness and answer usefulness over A |
| H-C | DevLog orientation improves investigation efficiency or correctness | C beats B on quality or evidence efficiency without higher unsupported-claim rate |
| H-D | DevLog context harms independent investigation through anchoring/noise | B beats C on semantically eligible cases with equal tools and higher C context |
| H-E | Provider/runtime constraints dominate observed quality | A/B/C have similar semantic quality conditional on completion, but high technical exclusion rates persist |
| H-F | The final synthesis contract is a bottleneck | Evidence ledgers are adequate, but all treatments produce weak or generic answers |

## Conditional Recommendation

Do not choose a winner before the benchmark. If C wins on quality and uncertainty
honesty while preserving grounding and acceptable technical completion, evolve
Story Context incrementally toward the bounded analyst described by Story 0184.
If B wins, prioritize direct investigative capability and treat DevLog context
as optional orientation. If A is statistically tied with B/C, stop expanding
the loop and focus on composition, projection, or synthesis. If technical
failures dominate, no semantic architecture decision is valid yet.
