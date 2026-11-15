# Q1 A/B/C Answer Evaluability Report

## Status

`Q1_ANSWERS_EVALUABLE`

The three outputs can be evaluated for technical correctness, evidence/citation
quality and uncertainty. They are not equivalent for narrative prose quality:
B has a native `answerText`; A/C have structured Story Context sections and may
need the exact-text display projection.

| Criterion | A | B | C | Evaluation status |
|---|---|---|---|---|
| Technical correctness | Structured findings, causal claims and classifications | `answerText`, claims and relationship result | Same Story Context structure plus investigation result | Measurable with Q1 required claims |
| Evidence/citations | Grounding references on structured findings | Claim references plus evidence locators | Core-returned references and grounding | Measurable; exact reference identity preserved |
| Depth of analysis | Structured sections and explicit causal assessment when present | Claim count/text and tool trace | Structured sections plus action trace | Partly measurable; human review required |
| Engineer usefulness | Structured content can be inspected directly | Native answer text and evidence | Structured content can be inspected directly | Human qualitative review; not a single prose metric |
| Uncertainty | `uncertainties`, `missingInformation`, confidence | Explicit abstention/confidence, if emitted | Same Story Context fields plus terminal status | Measurable when explicit; absence remains unknown |
| Technical failure separation | RAW execution status | V4 execution status | Investigation stop/rejection status | Measurable through RAW status normalization |

## A/C Display Projection

`render_evaluation_display` is an evaluation-only rendering helper. When an
explicit `answerText` is absent, it joins the exact extracted claim and
limitation strings with newlines. It adds no labels, transitions, conclusion,
or evidence. The original RAW and normalized structured answer remain the
primary evaluation artifacts.

This makes A/C readable for a blind reviewer, but it does not manufacture a
narrative answer. A/C prose quality therefore remains `NOT_COMPARABLE` to B's
native narrative text; technical, evidence, depth and uncertainty review remain
available.

## Normalization Limitations

- General prose claims are not heuristically segmented.
- A/C fields without an explicit answer field retain `answerText: null`.
- A source reference is not treated as semantically sufficient merely because
  it resolves in the RAW ledger.
- Semantic eligibility and oracle support are not assigned by normalization.
