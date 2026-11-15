# DevLog AI Q1 Comparative Report Template

## Status

`DRAFT - DO NOT TREAT AS RESULT`

## Run Identity

| Field | Value |
|---|---|
| Manifest | `devlog-ai-abc-first-test` |
| Case | `Q1` |
| Revision | `<frozen commit>` |
| Run ID | `<run id>` |
| Oracle digest | `<digest>` |
| Provider/model | `<authorized values>` |
| Runner version | `<version>` |

## Technical Results

| Observation | Condition | Native status | Normalization | Semantic eligibility | Stop reason |
|---|---|---|---|---|---|
| `Q1-A-DEVLOG-CURRENT-r1` | A | `<status>` | `<status>` | `<eligible/not eligible>` | `<reason>` |
| `Q1-B-DIRECT-INVESTIGATOR-r1` | B | `<status>` | `<status>` | `<eligible/not eligible>` | `<reason>` |
| `Q1-C-DEVLOG-ENGINEERING-ANALYST-r1` | C | `<status>` | `<status>` | `<eligible/not eligible>` | `<reason>` |

Technical completion and semantic eligibility must be reported separately.

## Capability Envelope

Describe the actual capabilities used by each condition, including search,
read, history, context projection, investigation actions, and rejected
operations. Do not call differences defects unless parity was required.

## Weighted Scoring

Score only semantically eligible normalized artifacts:

| Dimension | Weight | A | B | C |
|---|---:|---:|---:|---:|
| Technical accuracy | 30 | `<0-5>` | `<0-5>` | `<0-5>` |
| Evidence and traceability | 25 | `<0-5>` | `<0-5>` | `<0-5>` |
| Depth | 20 | `<0-5>` | `<0-5>` | `<0-5>` |
| Engineer usefulness | 20 | `<0-5>` | `<0-5>` | `<0-5>` |
| Uncertainty handling | 5 | `<0-5>` | `<0-5>` | `<0-5>` |
| **Weighted total** | **100** | `<0-5>` | `<0-5>` | `<0-5>` |

Use `sum(score / 5 * weight)`. A technically invalid observation receives no
semantic score.

## Claim-Level Review

For every required claim, record presence, technical accuracy, evidence support,
and uncertainty handling. Record unsupported claims separately from omissions.

## Decision Evidence

- Technical completion: `<summary>`
- Semantic eligibility: `<summary>`
- Required-claim recall: `<summary>`
- Unsupported-claim rate: `<summary>`
- Evidence support rate: `<summary>`
- Cost/latency/tool consumption: `<summary>`
- Capability asymmetry impact: `<summary>`
- Findings requiring a larger corpus: `<summary>`

## Human Review

- Oracle decision: `<PENDING/ACCEPTED/CORRECTED/REJECTED>`
- Artifact review: `<PENDING/ACCEPTED/REJECTED>`
- Adjudication notes: `<notes>`
- Architecture/product conclusion: `<none or explicitly authorized conclusion>`

This report is evaluation evidence only. It is not trusted project knowledge
and cannot by itself authorize an architecture decision.
