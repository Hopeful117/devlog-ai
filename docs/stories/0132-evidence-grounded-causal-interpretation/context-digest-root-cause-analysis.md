# Story 0132 - Context Digest Root-Cause Analysis

**Phase:** `V2_CONTEXT_DIGEST_ROOT_CAUSE_ANALYSIS`
**Date:** 2026-09-16
**Status:** Diagnostic only; no digest correction authorized or implemented

## Findings

### Established

1. The SCA callback digest validated by Java is the task's
   `AiTask.contextDigest`. In the production SCA path this is populated from
   `SelectedKnowledge.selectionDigest()` in
   `AnalyzeStoryContextUseCase.execute()`.
2. `SelectedKnowledge.selectionDigest()` is distinct from the nested
   `RepositoryContext.contextDigest()`.
3. The six parseable frozen callbacks contain a
   `provenance.contextDigest` that differs from the recorded
   `selectedKnowledgeFingerprint`/task digest. The values are stable per case,
   not per repetition.
4. The Core bridge does not reconstruct the original persisted Java task. It
   rebuilds a Python evaluation context and supplies its calculated digest as
   the task expectation. Therefore the bridge is not an exact historical
   context replay.
5. The two focused Python failures validate scenario `schemaDigest` values
   against the current Pydantic output schema. They are schema-fixture
   failures, not the SCA callback context-digest failure.

### Not established

- Whether the historical provider response was ever submitted to Java Core at
  capture time. The frozen capture contains the task fingerprint and provider
  response, but not an original Core acceptance result.
- Whether each captured value exactly equals the historical nested repository
  digest. The available capture preserves the response and task fingerprint,
  but not the complete historical prompt context required to recompute that
  candidate.

## Digest Lifecycle

```text
Java AnalysisContext + intent + guidance
        -> KnowledgeSelectionService.select()
        -> SelectedKnowledge
        -> selectionDigest()
        -> AiTask.contextDigest / PromptRequest traceability.contextDigest
        -> provider response provenance.contextDigest
        -> Java callback result.provenance.contextDigest
        -> AnalyzeStoryContextUseCase.validateStoryContextAnalysisResult()
        -> exact equality with AiTask.contextDigest
```

The production `selectionDigest` is generated in
`KnowledgeSelectionServiceImpl.digest()`:

- Input: a Jackson `DigestInput` containing project, analysis, profile,
  selected observations, selected facts, diagnostics, selected insights,
  existing architecture knowledge, engineering events, knowledge relations,
  engineering relationships, admission diagnostics, repository context,
  evolution context, and selection metadata.
- Serialization: Java `ObjectMapper.writeValueAsString()` using the active
  Java/Jackson object representation and record field order.
- Hash: SHA-256 over UTF-8 serialized bytes.
- Output: lowercase hexadecimal via `HexFormat`.
- No prompt content, provider output, model name, mapping hash, scenario
  schema digest, or capture/artifact hash is included directly.

The nested repository-context digest is generated separately in
`RepositoryContextEngine.digest()` over repository context version, profiles,
context plan version, selected evidence, layer counts, budget, precision
policy, diagnostics, token usage, selection decisions, and warnings. It also
uses SHA-256 over Jackson UTF-8 bytes and lowercase hexadecimal output.

The evaluation runner's `_selected_knowledge()` is not the production digest
implementation. It constructs a reduced repository context and computes:

```text
SHA-256(json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8"))
```

That is a diagnostic surrogate and cannot reproduce Java's full
`SelectedKnowledge` digest contract from the evidence-only context alone.

## Six Replay Failures

The expected value below is the recorded task/interaction-trace
`selectedKnowledgeFingerprint` used by the Core bridge. The captured value is
the callback payload's `provenance.contextDigest`.

| Case | Question | Repetition | Expected | Captured |
|---|---|---:|---|---|
| CASE-01 | `CASE-01::CL-01` | 2 | `35787e2db92094a6c7157cd1bd9bc5353b2a59399d4f163041c159aa24c88aad` | `d12b036040a8a0680896286f602cdb86a16e2df38b9d388dfc59540fb7f637da` |
| CASE-03 | `CASE-03::CL-09` | 1 | `5ef6162c20867ee9f1a06a8fad866067df6943f5fd3a40f423346971ca0627ed` | `bba214ea46382284e930bd6bba1fc6eff78eb7ab6567695b050b8e586ccea177` |
| CASE-03 | `CASE-03::CL-10` | 1 | `5ef6162c20867ee9f1a06a8fad866067df6943f5fd3a40f423346971ca0627ed` | `bba214ea46382284e930bd6bba1fc6eff78eb7ab6567695b050b8e586ccea177` |
| CASE-03 | `CASE-03::CL-09` | 2 | `5ef6162c20867ee9f1a06a8fad866067df6943f5fd3a40f423346971ca0627ed` | `bba214ea46382284e930bd6bba1fc6eff78eb7ab6567695b050b8e586ccea177` |
| CASE-03 | `CASE-03::CL-09` | 3 | `5ef6162c20867ee9f1a06a8fad866067df6943f5fd3a40f423346971ca0627ed` | `bba214ea46382284e930bd6bba1fc6eff78eb7ab6567695b050b8e586ccea177` |
| CASE-04 | `CASE-04::CASE-04` | 3 | `f8ee84ca2eff8d64e4c98a7e40b749f192c1e8ef4d0f198ef0b786d97910197a` | `b57c08b78f7497434d8509bb152367f3123ea46c67c4335f3151cf8121e07c25` |

All six failures have the same immediate cause: callback provenance digest is
not equal to the task digest. The evidence supports a contract-authority
conflict between the selected-knowledge digest and another context digest,
with replay reconstruction drift as a secondary cause. No digest value was
corrected.

## Authority and Temporal Findings

- Context digest provenance was introduced with Story 0112's agent execution
  contract. Story 0112 describes it as provenance and requires context
  integrity validation.
- Story 0116 rewired SCA execution to actual selected knowledge and set the
  task digest to `SelectedKnowledge.selectionDigest()`.
- ADR-033 defines the selection digest as a deterministic digest over ordered
  selected knowledge for reproducibility, diagnostics, replay validation, and
  prompt traceability.
- ADR-046 explicitly distinguishes Repository Context digest from projection
  digest. Story 0127 explicitly keeps the AI reference mapping digest outside
  `contextDigest`.
- The mapping hash is unchanged: `67474f09e41c07899c8c7117754b21e794f285380ffd50675e701a0a3c2c40c0`.
- Historical Story 0132 artifacts and the raw-capture hash recorded in them
  were not modified.

The two Python failures are caused by schema evolution in the uncommitted
Story 0132 contract work: causal assessment/claim fields, the wrapped
`outputClassification`, and the confidence serializer changed the generated
Pydantic schema. The current calculated schema digests are:

- Story Context Analysis: `7186cf1d381c7dca857f0481c283609d100e372afdf098811a3666ffff1c9f90`
- Insight Generation: `a37c56f2417d08a15ae9d59041460ada88410098ac217e32e96a11a13fbec163`

The checked-in scenario values are older. This is related only in the broad
sense that both are digest contract drift; the inputs and authorities differ.

## Root Cause Classification

```text
PRIMARY_ROOT_CAUSE = CONTRACT_AUTHORITY_CONFLICT
SECONDARY_CAUSES =
  - REPLAY_CONTEXT_DRIFT
  - STALE_SCENARIO_SCHEMA_DIGEST (Python tests only)
```

The authoritative production side is Java Core's persisted task digest. The
diverging side in the six callbacks is the historical provider response's
`provenance.contextDigest`, while the replay harness additionally fails to
preserve the exact historical Java context snapshot.

Digest validation is an integrity/replay gate, not a security signature. It
protects the identity of the authorized selected context attached to an AI
task and prevents a result produced for a different context from being
persisted as that task's result.

## Verification and Boundaries

- New provider calls: `0`.
- Diagnostic recomputation: in-memory only; no values persisted.
- Provider structurally invalid slots: `27` preserved.
- Semantic scoring: not established; baseline invalid.
- `git diff --check`: pass.
- Commit/push/merge: none.
- Story 0133: not authorized.

## Authorized Replay Correction

The frozen interaction traces contain one unambiguous
`selectedKnowledgeFingerprint` per parseable slot. The replay bridge now uses
that value as the historical task identity instead of the reconstructed
surrogate selection digest. Java validation remains unchanged and strict.

The callback's model-returned `provenance.contextDigest` is normalized only in
the derived replay input to that historical task identity. Each normalization
is recorded with the original callback value; the raw callback remains inside
the immutable source capture unchanged. This is a deterministic
provenance-boundary correction, not a semantic output correction.

Result: all six slots pass context-digest validation. The next Core blocker is
authoritative evidence assertion resolution against the reconstructed snapshot:
three line locators exceed content bounds and three section headings are absent.
This is recorded in the new artifact
`ground-truth-v2-live-replay-historical-context-correction-2026-09-16.json`.

## Evidence Replay Root-Cause Investigation

The historical-context replay artifact was generated with the DevLog AI
repository supplied as the evidence repository. The frozen benchmark repository
is instead `/home/ludo/Bureau/workspace/trading-os`, resolved at commit
`18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149`. With the DevLog AI repository,
all six assertion sources reconstructed as empty content. This explains the
observed three line-bound and three heading-not-found failures.

An in-memory diagnostic using the pinned Trading OS tree shows the source
identity correction is necessary but not sufficient:

- CASE-01 section locators resolve, but Java retains the terminal newline while
  the Python capture binder removes it, producing a resolved-content digest
  mismatch.
- CASE-03 repetition 1 has two valid source identities, but its generated
  excerpts do not equal the resolved content and its synthetic section names
  are absent from raw Java source/commit text.
- CASE-03 repetition 2 has an out-of-bounds line range (`10..106` against a
  100-line file) and an absent synthetic section heading.
- CASE-03 repetition 3 has two absent synthetic section headings.
- CASE-04 has two resolvable Markdown sections and one absent synthetic
  `ExecutionConfiguration.java code` heading.

The frozen capture contains no provider-visible selected-evidence snapshot,
prompt body, or source-content version. Interaction traces preserve counts,
fingerprints, and metadata only. Historical provider-visible content is
therefore unavailable; historical locator validity cannot be proven. No
locator, evidence content, historical artifact, or provider output was
modified during this investigation.

## Pinned Evidence Replay Correction

The replay runner now fails closed unless its repository argument is exactly
`/home/ludo/Bureau/workspace/trading-os`, and preflight resolves the frozen
benchmark revision `18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149`.

The Python locator splitter now retains Java's trailing empty line semantics.
This aligns section resolution and resolved-content SHA-256 calculation with
`TaskSnapshotEvidenceResolver`; Java production behavior was not changed.

The six immutable outputs were replayed with zero provider calls. All six
historical context digests passed. Core rejected the six only for captured
evidence assertion failures: one resolved-content digest mismatch, two excerpt
mismatches, one out-of-bounds line range, and two absent exact headings. These
failures were not repaired.

New artifact:
`ground-truth-v2-live-replay-pinned-evidence-correction-2026-09-16.json`
SHA-256:
`f64d48c50cf77c59e90a312d4205fc96bd0e09337538ca0b1b356eab98f01981`
