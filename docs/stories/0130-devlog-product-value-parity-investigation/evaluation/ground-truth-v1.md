# Product Evaluation Oracle v1

## Status

`CANDIDATE - HUMAN VALIDATION REQUIRED BEFORE USE AS ACCEPTANCE TRUTH`

This oracle was prepared from live DevLog evidence for `trading-os` at imported
repository revision `18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149`. It is not AI
generated truth and must not be accepted by the evaluated system. A human
engineer must resolve every artifact and approve or correct the candidate
classification before a future implementation Story uses it.

The machine-readable case definitions are in `benchmark-suite-v1.json`.

The `C-*`, `CL-*`, `COMP-*` and `TEST-*` values in that file are stable oracle
identifiers for deterministic scoring. Their mapping to concrete artifacts and
the correctness of each mapping are provisional until the human validation
record below is completed.

## Resolution Rules

- An expected artifact is valid only when its path, commit, or DevLog resource
  resolves at the pinned revision or imported evidence snapshot.
- `EXPLICITLY_DOCUMENTED` means the artifact directly states the relationship.
- `STRONGLY_SUPPORTED` means multiple independent artifacts support the link,
  but no single artifact states it as a decision relationship.
- `NOT_ESTABLISHED` means the evidence may show co-occurrence or plausibility,
  but does not establish causality. It is a successful negative result to refuse
  the causal claim.
- An absent expected artifact is an oracle defect, not a model failure; the
  oracle must be corrected and versioned before evaluation.
- Grounding/reference validity does not prove that the referenced artifact
  supports the returned claim. Claim support is reviewed separately.

## Human Validation Record

The human validator must record:

| Field | Required value |
|---|---|
| Validator | Name/identity |
| Date | ISO date |
| Revision checked | Must equal pinned revision or documented snapshot revision |
| Artifact resolutions | PASS/FAIL per expected artifact |
| Causal classifications | Approved/corrected per causal link |
| Negative control | Confirmed `NOT_ESTABLISHED` or corrected |
| Changes | Exact change and reason |
| Approval | Explicit human approval |

Until this record is completed, the suite can be used for protocol rehearsal
only and cannot establish Product Value PASS.
