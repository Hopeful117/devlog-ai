# DevLog AI A/B/C Normalized Answer Contract

## Status

`EVALUATION_ONLY - NORMALIZATION_READY - SEMANTIC_REVIEW_PENDING`

Contract identity: `devlog-abc-claim-evidence-normalization-v1`.

This contract is used only by the provider-free and provider-backed benchmark
artifacts. It does not replace REST, MCP, Java Core, Python provider, or Story
Context contracts.

## Pipeline

```text
immutable RAW observation
  -> deterministic adapter
  -> DERIVED normalized answer
  -> independent oracle evaluation
  -> human review/adjudication
```

The adapter never decides semantic correctness. It copies explicit structured
fields and preserves the original RAW identity. It does not receive oracle
content.

## Normalized Answer

| Field | Meaning |
|---|---|
| `normalizationContract` | This contract identity |
| `observationId`, `caseId`, `treatment` | Execution identity copied from the RAW envelope |
| `repositoryRevision` | Revision bound to the execution envelope; not a reconstructed citation revision |
| `technicalStatus` | `COMPLETED`, `PROVIDER_FAILURE`, `PROVIDER_TIMEOUT`, `PROVIDER_INCOMPLETE_RESPONSE`, `RUNTIME_FAILURE`, or `NOT_EVALUATED` |
| `semanticEligibility` | Existing evaluator status when explicitly present; otherwise `NOT_EVALUATED` |
| `answerText` | Explicit answer field only; `null` means unavailable |
| `claims` | Explicit structured claims only, with deterministic derived IDs and source paths |
| `evidenceReferences` | References cited by the response, including locator, revision source, and resolution status |
| `limitations` | Explicit uncertainty/missing-information descriptions or stated limitations |
| `abstention` | Explicit boolean only; `null` means not expressed |
| `normalizationWarnings` | Missing, partial, unresolved, or contradictory information found during normalization |
| `rawArtifact` | RAW observation identity, hash, and preservation metadata |
| `derivedSha256` | Hash of the complete normalized artifact before this field is added |

## Evidence Semantics

- A claim is text explicitly present in a structured answer field. It is not a
  statement inferred from a cited excerpt.
- A response citation is a reference emitted by the treatment.
- A RAW ledger item is evidence captured by the runtime or context envelope.
- `RESOLVED_IN_RAW_LEDGER` means the exact response reference also occurs in
  that RAW ledger. It does not mean the evidence semantically supports the
  claim.
- `CITED_NOT_RESOLVED` means the response cited a reference that was not found
  in the supplied RAW ledger.
- `REVISION_MISMATCH` means the source record carries a revision different from
  the execution revision.
- Oracle support is evaluated later and is never represented as a normalization
  status.

## Extraction Boundary

The adapters extract:

- B `answerText`, structured `claims`, evidence records, explicit abstention;
- A/C explicit Story Context causal claims, causal assessment explanation,
  structured finding descriptions, grounding references, uncertainties and
  missing information.

They do not attempt general prose claim segmentation. A/C responses without an
explicit answer field retain `answerText: null` and receive
`ANSWER_TEXT_NOT_AVAILABLE`. Missing citations, provenance, locators, or status
are reported as warnings or explicit unavailable statuses rather than repaired.

For human inspection only, `render_evaluation_display` returns explicit A/C
claim and limitation text joined by newlines when `answerText` is unavailable.
It does not add labels, transitions, conclusions, or evidence. Therefore it
supports technical/citation/uncertainty review, but it does not make prose
quality or narrative usefulness equivalent to B's native `answerText`.

## RAW / DERIVED Integrity

RAW payloads are deep-copied and never modified. A supplied `rawSha256` must
match the canonical JSON hash or normalization fails. If the hash is absent, a
computed hash is recorded with `COMPUTED_FROM_RAW_PAYLOAD` and a warning; this
does not falsely claim that the source artifact supplied a hash.
