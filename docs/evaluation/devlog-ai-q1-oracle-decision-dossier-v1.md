# DevLog AI Q1 Oracle Decision Dossier

## Purpose

This dossier supports the human decision required before the Q1 benchmark can
proceed. It presents factual evidence for each of the four oracle claims
verified against the pinned repository snapshot, evaluates benchmark
suitability, and documents open questions.

**This dossier does not:**

- Modify the oracle claims or manifest.
- Approve the oracle or record an `approverId`.
- Record a fictitious reviewer identity or date.
- Authorize any live provider execution.

The human reviewer must supply the decision, identity, and date separately in
`docs/evaluation/devlog-ai-q1-oracle-review-v1.md` and
`docs/evaluation/devlog-ai-abc-oracle-proposals-v1.json`.

---

## Review Context

| Field | Value |
|---|---|
| Oracle case | `Q1`, version `1.1.0` |
| Oracle status | `CORRECTION_PROPOSED` |
| Pinned repository revision | `60fae3e0e1104d0ffbe3832c1f578ed532b1268a` |
| Repository | `git:github.com/Hopeful117/devlog-ai` |
| Oracle proposed digest | `464fcd1b50b785e78ddd76e0b4eea3e65ac6a85fb124b31cea9c355951fcc457` |
| Human semantic validation | `PENDING` |
| Human decision | **not recorded** |
| Blocking status | `Q1_ORACLE_PENDING_HUMAN_REVIEW` |
| Provider calls during this review | `0` |
| External network calls | `0` |

---

## Claim-by-Claim Verification

Each claim was verified by reading the referenced file at revision `60fae3e0`
using `git show 60fae3e0:<path>`. Conclusions are factual observations about
the code at that revision; they are not oracle acceptance.

---

### Q1-C1 — Core Authorization and Preparation

**Claim:** Java Core resolves the project, invokes authorization for
`MCP_STORY_AGENT_EXECUTE`, verifies the Story/project relationship and required
intent, then prepares the context before calling the submission path.

**Verification status: CODE MATCHES CLAIM**

**Evidence at `60fae3e0`:**

`AnalyzeStoryContextUseCase.java#L119-L141` (within the `execute(...)` method body):

```java
Project project = projectRepository.findBySlug(projectSlug)       // project resolution
        .orElseThrow(() -> new EntityNotFoundException(...));
authorizePrincipal(principal, project, "MCP_STORY_AGENT_EXECUTE"); // authorization scope
EngineeringStory story = storyId == null ? null :
        storyRepository.findById(storyId).orElseThrow(...)          // story lookup
if (story != null && !story.getProject().getId()
        .equals(project.getId())) {
    throw new IllegalArgumentException("Story does not belong to project"); // ownership check
}
if (requestedIntent == null || requestedIntent.isBlank()) {
    throw new IllegalArgumentException("intent is required");       // intent present
}
intentCatalog.resolve(requestedIntent, INTENT_VERSION);             // intent resolved
...
return submitPrepared(
        prepare(projectSlug, storyId, requestedIntent, files),      // prepare precedes submit
        guidance, identity, principal, provider);
```

`#L168-L173` shows `authorizePrincipal` delegates to
`authorizationService.isAuthorized(principal, project, requiredScope)`, confirming
that the authorization scope check is performed but that the full policy is
owned by the injected service, not the use case.

**Required invariants — verification:**

| Invariant | Present at `60fae3e0` |
|---|---|
| Project resolution is Core-owned | Yes — `projectRepository.findBySlug(...)` |
| Required authorization scope checked before submission | Yes — `authorizePrincipal(...)` before `submitPrepared` |
| Supplied Story must belong to resolved project | Yes — `story.getProject().getId().equals(project.getId())` |
| Intent is present and resolved | Yes — null check + `intentCatalog.resolve(...)` |
| Preparation precedes `submitPrepared` | Yes — `prepare(...)` called inline as argument |

**Forbidden claims — none violated** at this code location.

**Observation:** `authorizePrincipal` delegates to an injected
`authorizationService`; the use case does not contain the complete policy, as
the oracle's `expectedQualifications` state. This distinction is correctly
represented in the claim.

---

### Q1-C2 — Submission and Snapshots

**Claim:** `StoryContextSubmissionService` creates the Analysis and AiTask
execution records, binds the context/projection/grounding identities, records
budget and loss accounting, and submits the accepted task to the AI Engine;
submission is not proof that provider processing succeeded.

**Verification status: CODE MATCHES CLAIM**

**Evidence at `60fae3e0`:**

`StoryContextSubmissionService.java#L147-L169` (within `submitPrepared`):

```java
if (contextDigest == null || contextDigest.isBlank()) {
    throw new IllegalStateException(
        "Canonical context digest is required before submission"); // digest required
}
Analysis executionAnalysis = analysisRepository.save(Analysis.builder()...build()); // Analysis persisted
AiTask task = aiTaskService.createForStoryContextAnalysisEntity(...);               // AiTask created
task.setContextDigest(contextDigest);       // context identity
task.setProjectionDigest(projectionDigest); // projection identity
task.setSubmissionDigest(submissionDigest); // submission identity
// grounding identity set via groundingContract in buildExecutionSnapshot
task.setContextSnapshot(snapshot);
aiTaskRepository.save(task);
```

`StoryContextSubmissionService.java#L221-L239`:

```java
ContextLossLedger lossLedger = ContextLossLedger.capture(...);  // loss accounting
snapshot.put("lossAccounting", lossAccounting);                 // recorded in snapshot
task.setContextSnapshot(snapshot);
aiTaskRepository.save(task);
if (!budget.accepted()) {
    aiTaskService.failSubmission(task.getId(), ...);
    throw new IllegalStateException(budget.message());          // budget rejection before submission
}
aiTaskService.submit(task.getId(), new SubmitAiTaskRequest(null));
aiEngineClient.submit(prompt);                                   // delegates to Core's client
return task.getId();                                             // returns ID, not provider result
```

**Required invariants — verification:**

| Invariant | Present at `60fae3e0` |
|---|---|
| Canonical context digest required | Yes — `throw new IllegalStateException("Canonical context digest is required")` |
| Analysis/AiTask records and execution snapshot persisted | Yes — `analysisRepository.save(...)`, `aiTaskRepository.save(task)`, `snapshot` |
| Grounding, projection, submission, context identities retained | Yes — `setContextDigest`, `setProjectionDigest`, `setSubmissionDigest`, `groundingContract` in snapshot |
| Budget rejection fails before submission | Yes — `if (!budget.accepted()) { failSubmission...; throw }` before `aiEngineClient.submit` |
| Successful submission delegates to Core's AI Engine client | Yes — `aiEngineClient.submit(prompt)` |

**Observation:** The return value is `task.getId()` (a UUID), not provider
confirmation. The boundary between task submission and provider completion is
real and correctly described by the claim.

---

### Q1-C3 — Python Generation and Investigation Boundary

**Claim:** Python performs provider-backed structured generation and validates
the generated result. Only the investigation-enabled path enters the
PLAN/ACTION/FINAL loop: Python proposes bounded actions and validates typed
Core results, while Core remains authoritative for action scope, revision,
authorization and limits.

**Verification status: CODE MATCHES CLAIM**

**Evidence at `60fae3e0`:**

`story_context_analysis_generation_service.py#L128-L169` — the `process` method:

```python
async def process(self, submission, external_job_id):
    if self._investigation_enabled(submission):   # only investigation-enabled enters the loop
        await self._process_investigation(submission, external_job_id)
        return
    # ... ordinary one-shot generation path continues
```

Ordinary generation proceeds without entering the investigation path, confirming
the first required invariant.

`story_context_analysis_generation_service.py#L224-L285` — `_process_investigation`:

```python
decision = await self._investigation_decision(prompt, traces)
if not isinstance(decision, InvestigationPlanDecision):
    raise ValueError("Investigation must begin with a PLAN decision")  # PLAN-first invariant
# ... action loop
response = await self._callback_client.execute_investigation_action(   # Core invoked via callback
    submission.ai_task_id, submission.correlation_id,
    self._action_payload(action, submission, traces)
)
typed_result = InvestigationCapabilityResult.model_validate(action_result) # typed validation
self._validate_investigation_identity(submission, typed_result)             # identity check
```

The final decision (`InvestigationFinalDecision`) closes the loop:

```python
if isinstance(decision, InvestigationFinalDecision):
    await self._complete_investigation(...)  # FINAL step
    return
```

`story_context_investigation.py#L122-L156` confirms the PLAN proposal schema
carries `protocolVersion: "story-context-agent-protocol/v2"`, `planId`,
`executionId`, `parentTaskId`, `scope`, `budget`, and `actions`. Actions
contain `revision` that must match the scope revision — Core controls revision.

**Required invariants — verification:**

| Invariant | Present at `60fae3e0` |
|---|---|
| Ordinary Story Context generation can complete without investigation loop | Yes — explicit branch returns before loop |
| Investigation begins with a validated PLAN | Yes — `if not isinstance(decision, InvestigationPlanDecision): raise ValueError(...)` |
| Actions sent to Core through the callback client | Yes — `self._callback_client.execute_investigation_action(...)` |
| Typed action results and identities validated before reuse | Yes — `InvestigationCapabilityResult.model_validate(...)` + `_validate_investigation_identity(...)` |
| Python does not receive repository or database authority | Yes — Python receives callback results from Core, not direct access |

**Forbidden claims — none violated** in the evidence.

**Observation:** The claim and oracle correctly distinguish the
investigation-enabled path from the standard one-shot path. ADR-067 D1
(confirmed at `docs/decisions/ADR-067.md#L33-L62` at this snapshot) provides the
architectural rationale: "Java owns capability, authority and governance.
Python owns agent execution and probabilistic intelligence."

---

### Q1-C4 — Core Validation and Non-Trusted Persistence

**Claim:** Java Core validates the callback result against task identity,
digest, grounding, references, classifications and related invariants, then
persists the result as an immutable non-trusted `StoryContextAnalysis` snapshot;
persistence does not promote the analysis to trusted knowledge.

**Verification status: CODE MATCHES CLAIM**

**Evidence at `60fae3e0`:**

`StoryContextCallbackService.java#L71-L100`:

```java
StoryContextAnalysisResult analysisResult = request.analysisResult();
if (analysisResult == null) {
    throw new IllegalStateException(
        "Story Context Analysis callback must include analysisResult");   // non-null required
}
analysisResult = StoryContextResultValidator.validate(analysisResult, task); // Core validates

StoryContextAnalysis analysis = StoryContextAnalysis.builder()
        .story(...)
        .aiTask(task)
        .analysisSnapshot(objectMapper.convertValue(analysisResult, Map.class)) // snapshot saved
        .contextDigest(task.getContextDigest())
        ...
        .build();
storyContextAnalysisRepository.save(analysis);                               // persisted
```

`StoryContextResultValidator.java#L15-L61`:

```java
Set<String> allowedRefSet = AnalyzeStoryContextUseCase
        .allowedGroundingReferences(groundingContract, task);  // authorized grounding set
String expectedDigest = task.getContextDigest();
String actualDigest = result.provenance().contextDigest();
if (!expectedDigest.equals(actualDigest)) {
    throw new IllegalStateException("Context digest mismatch...");  // digest validated
}
AnalyzeStoryContextUseCase.validateGroundedFindings(...);    // references validated per category
// ... classifications and confidence validated
```

`StoryContextAnalysis.java#L38-L51`:

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "analysis_snapshot", nullable = false,
        updatable = false, columnDefinition = "jsonb")   // updatable=false -> immutable
private Map<String, Object> analysisSnapshot;
```

The entity has no `trustTier` field. ADR-067 D5 (`docs/decisions/ADR-067.md#L124-L146`
at snapshot) states explicitly: "Persistence does NOT mean promotion."

**Required invariants — verification:**

| Invariant | Present at `60fae3e0` |
|---|---|
| A callback must contain an analysis result | Yes — null check throws `IllegalStateException` |
| Core validates authorized grounding set and context digest | Yes — `allowedGroundingReferences` + digest equality check |
| Invalid grounding, references or classifications rejected | Yes — `validateGroundedFindings(...)` for each category; classification set check |
| Persisted snapshot is immutable and associated with the task | Yes — `updatable = false`; `@ManyToOne` on `aiTask` |
| Persisted analysis remains non-trusted | Yes — no trust tier field; ADR-067 D5 explicit |

**Observation:** The optional detail `story_context_analyses.analysis_snapshot`
(the exact column name) is confirmed at this snapshot. The oracle correctly
designates it as an optional detail; a correct answer does not require its
mention.

---

## Benchmark Suitability Evaluation

The oracle proposals document scoring metadata:
`"expectedAnswerClass": "GROUNDED_ARCHITECTURAL_EXPLANATION"`.

| Dimension | Assessment |
|---|---|
| **Accuracy** | All 4 required claims reflect current code at `60fae3e0` without material inaccuracy. |
| **Grounding** | All evidence references resolve to real lines at `60fae3e0`. Evidence depth is proportionate: each claim cites 2–6 locators. |
| **Precision** | Claims correctly distinguish: Core authorization ≠ complete policy; submission ≠ provider completion; investigation path ≠ all requests; persistence ≠ trust promotion. No unnecessary conflation. |
| **Relevance** | Q1 targets the Core/Python boundary on the primary execution path — central to the purpose of the A/B/C comparison. |
| **Discriminative power** | Treatment A (Core+Python one-shot) and C (Core+Python investigation) operate on this exact boundary. Treatment B (direct investigator) bypasses Core authorization. Q1 is architecturally discriminating between all three treatments. |
| **Neutrality** | Scoring is format-neutral (confirmed in proposals and review sheet). No reward for citation count, prose length, or native output format. |
| **Contamination risk** | Oracle claims are factual architectural descriptions, not answer patterns. Scoring rules forbid format-specific rewards. The manifest validator rejects oracle injection into treatment prompts. Risk assessed: **LOW**. |

**Suitability conclusion:** Q1 is factually accurate, well-grounded, and
discriminating for the asymmetric comparison. It is suitable for the benchmark
subject to the human acceptance decision.

---

## Identified Discrepancies and Open Questions

### Minor: Line range discrepancy between oracle documents

`devlog-ai-abc-oracle-proposals-v1.json` cites
`AnalyzeStoryContextUseCase.java#L119-L141` (Q1-C1 primary evidence).

`devlog-ai-abc-oracle-human-review-sheets-v1.md` cites
`AnalyzeStoryContextUseCase.java#L105-L141` (supporting evidence section — slightly broader range).

Both locators resolve to the same code region at `60fae3e0`. The discrepancy
is cosmetic; the broader range captures additional method context. No
correction is required, but the reviewer may choose to align them.

**Decision question for reviewer:** Align the review sheet to `#L119-L141`,
or keep the broader range as complementary context?

---

### Open: `story_context_analysis_generation_service.py#L306-L329`

Q1-C3 cites this reference in addition to `#L128-L169` and `#L224-L285`.
The core PLAN/ACTION/FINAL logic is fully confirmed by the first two ranges.
The `#L306-L329` reference likely covers a helper method (`_complete_investigation`
or `_investigation_decision`) that closes the loop.

The oracle marks Q1-C3 as `VERIFIED`. The primary invariants do not depend on
this additional reference being read; it is supplementary traceability.

**Decision question for reviewer:** Accept the supplementary reference as-is,
or verify its content independently before acceptance?

---

## Human Decisions Required

Reproduced from `devlog-ai-q1-oracle-review-v1.md` §"Human Decisions Required":

1. **Accept, correct, or reject the four corrected claim formulations.**
   This dossier finds all four claims factually supported at `60fae3e0`.
   No code-level correction is required; the reviewer may apply semantic
   judgment or wording corrections independently.

2. **Confirm that the exact field name (`story_context_analyses.analysis_snapshot`)
   is optional scoring detail, not mandatory wording.**
   Factually confirmed present at `60fae3e0`; optional status is correctly
   characterized in the oracle.

3. **Confirm that Q1 remains suitable for the asymmetric comparison.**
   Benchmark suitability assessment above: **recommended yes**, subject to
   reviewer confirmation.

4. **Confirm the weighted, format-neutral scoring rules.**
   Scoring weights (30/25/20/20/5) and format-neutral rules are present in
   the oracle proposals. Reviewer must accept or modify.

---

## Decision Form

The reviewer records the decision in `devlog-ai-q1-oracle-review-v1.md`:

| Field | Value |
|---|---|
| Decision | `ACCEPTED` / `CORRECTED` / `REJECTED` |
| Reviewer | `<to be recorded>` |
| Date | `<to be recorded>` |
| Repository revision | `60fae3e0e1104d0ffbe3832c1f578ed532b1268a` |
| Final oracle digest | `464fcd1b50b785e78ddd76e0b4eea3e65ac6a85fb124b31cea9c355951fcc457` |
| Reservations | `<optional>` |

No provider authorization follows automatically from an oracle decision.

---

## Blocking Statuses (Unchanged)

This dossier records no acceptance and does not change any status:

```
Q1_LIVE_TECHNICALLY_BLOCKED
Q1_LIVE_NOT_AUTHORIZED
Q1_ORACLE_PENDING_HUMAN_REVIEW
```


---

## Final Technical Verification

*Added after user review preferences were communicated. See §Provenance for
the distinction between user review preference, technical verification, and
formal oracle acceptance.*

### Three-Category Distinction

| Category | Definition |
|---|---|
| **User review preference** | The position expressed by the user in conversation — not an authoritative project record. |
| **Technical verification** | The factual result of reading the referenced source at `60fae3e0`. |
| **Formal oracle acceptance** | An official approval recorded in `devlog-ai-abc-oracle-proposals-v1.json` and `devlog-ai-q1-oracle-review-v1.md` with reviewer identity, date, and digest. |

Only the first two categories are populated here. Formal oracle acceptance
requires a separate explicit human authorization. No `approverId`, date, or
digest is recorded in this dossier.

---

### Q1-C3 Supplementary Reference — Full Verification

**Reference:** `ai-engine/app/services/story_context_analysis_generation_service.py#L306-L329`

**Method identified:** `_complete_investigation` — starts at line 306.

**Exact content at `60fae3e0` (lines 306–329):**

```python
async def _complete_investigation(
        self, submission: AiTaskSubmissionRequest, external_job_id: UUID,
        prompt: Prompt | None, traces: InteractionTraceCollector,
        stop_reason,
        provider_result: ProviderStoryContextAnalysisResult,
    ) -> None:
        if prompt is None:
            raise ValueError("Investigation final prompt is missing")
        output = provider_result_to_internal(provider_result)
        self._validate_output(output, submission.selected_knowledge,
                              submission.grounding_contract or {})
        output = self._apply_execution_provenance(output, prompt)
        await self._callback_client.send_result(
            submission.ai_task_id, submission.correlation_id,
            AiTaskResultRequest(
                ..., status=AiTaskResultStatus.COMPLETED,
                analysis_result=output,
                investigation_stop_reason=stop_reason,
                ...
            ),
        )
```

**Role in the PLAN/ACTION/FINAL cycle:**

`_complete_investigation` is the FINAL step. It is called from
`_process_investigation` (line 237) when the loop reaches an
`InvestigationFinalDecision`:

```python
if isinstance(decision, InvestigationFinalDecision):
    await self._complete_investigation(
        submission, external_job_id, prompt, traces,
        decision.stop_reason, decision.result
    )
    return
```

The method:
1. Validates the final provider output against `selected_knowledge` and
   `grounding_contract` (Python-side defensive validation).
2. Applies execution provenance metadata.
3. Sends the result to Core via `self._callback_client.send_result(...)` with
   `AiTaskResultStatus.COMPLETED` and the full investigation metadata
   (`stop_reason`, generation count, elapsed millis, output tokens, traces).
4. Does **not** persist anything — sends a callback to Core.

**Consistency with primary references:**

| Reference | Coverage |
|---|---|
| `#L128-L169` (`process`) | Branch to investigation path; ordinary generation path; corrective retry |
| `#L224-L285` (`_process_investigation`) | PLAN validation; ACTION loop; callback to Core for each action |
| `#L306-L329` (`_complete_investigation`) | FINAL step: Python output validation + callback result to Core |

The three ranges are non-overlapping and together cover the full
PLAN/ACTION/FINAL cycle. `#L306-L329` closes the loop by confirming
that the investigation terminates with a Core callback, not Python-side
persistence.

**Q1-C3 invariants confirmed by `#L306-L329`:**

| Invariant | Confirmed |
|---|---|
| Python does not receive repository or database authority | Yes — FINAL step sends callback to Core; Python does not persist |
| Typed action results and identities validated before reuse | Yes — `_validate_output` called before `send_result` |
| Actions sent to Core through the callback client | Yes — `self._callback_client.send_result(...)` |

**Result: VERIFIED**

`#L306-L329` provides substantive support for Q1-C3. It documents the FINAL
step of the investigation loop, confirming that Python closes the cycle by
sending a validated result to Core rather than persisting it. No contradiction
with the claim formulation.

**Recommendation:** Q1-C3 may be advanced from `VERIFY` to `ACCEPT` based on
this verification. This recommendation does not constitute formal oracle
acceptance.

---

### Confirmation of Q1-C1, Q1-C2, Q1-C4

No new code changes have been committed to `story/0184` that would affect
C1/C2/C4 since the original dossier verification. The oracle-relevant source
files remain at the state read at `60fae3e0`. No contradictions identified.

**Retained qualifications (required):**

- **Q1-C1**: Authorization is invoked by Java Core (`authorizePrincipal`),
  but the complete policy belongs to the injected `authorizationService`.
  The use case proves invocation and boundary, not every internal policy rule.
- **Q1-C2**: Submitting a task to the AI Engine (`aiEngineClient.submit`) is
  not proof that provider processing succeeded. The return value is `task.getId()`,
  not a provider result.
- **Q1-C4**: Persisting a `StoryContextAnalysis` does not promote it to trusted
  knowledge. The column `analysis_snapshot` with `updatable = false` is a confirmed
  optional detail; a correct answer does not require its mention.

---

### Scoring Rules Verification

Scoring rules extracted from `devlog-ai-abc-oracle-proposals-v1.json` Q1 `scoring` object:

**Weights (sum = 100):**

| Criterion | Weight |
|---|---:|
| `technicalAccuracy` | 30 % |
| `evidenceAndTraceability` | 25 % |
| `depth` | 20 % |
| `engineerUsefulness` | 20 % |
| `uncertaintyHandling` | 5 % |

**Rules (verbatim from proposals):**

- **correct**: "States the Core/Python boundary and the execution flow with no
  material contradiction, with relevant evidence or clearly qualified
  repository-grounded reasoning."
- **partiallyCorrect**: "Gets the main boundary or flow right but omits one
  non-central invariant, collapses two stages, or uses incomplete evidence
  without reversing the conclusion."
- **omission**: "Does not address a required claim or leaves a required
  ownership boundary unresolved."
- **contradiction**: "Assigns authorization, repository execution, grounding
  acceptance, or trust promotion to the wrong side, or claims provider
  submission is completed analysis."
- **unsupported**: "Adds a material implementation or architectural claim not
  established by the cited repository evidence and does not mark it as uncertain."

**Format neutrality (verbatim from proposals):**

1. "Do not reward length, narrative prose, structured output shape, citation
   count, or any treatment-specific field by itself."
2. "Score explicit claims and evidence after normalization; treat absence of a
   native `answerText` as a format difference, not an automatic penalty."
3. "A technical failure is not a semantic failure and is excluded from the
   semantic denominator."

**Assessment:**

| Dimension | Assessment |
|---|---|
| Neutrality A/B/C | Yes — format-neutral rules apply identically to all three treatments |
| No reward for length | Yes — explicitly stated |
| No reward for native format | Yes — structured output shape not rewarded by itself |
| Evidence rewarded | Yes — 25 % weight; `correct` requires evidence or grounded reasoning |
| Factual accuracy vs grounding | Distinct — `technicalAccuracy` (30 %) and `evidenceAndTraceability` (25 %) are separate weights |
| Partial answers | Covered — `partiallyCorrect` rule defined |
| Unsupported claims | Penalized — `unsupported` rule defined |
| Justified abstentions | Covered via `uncertaintyHandling` (5 %); required claims cannot be omitted without `omission` label |

**Minor observation:** The scoring rules do not specify whether a single
`contradiction` nullifies otherwise-correct claims or is applied per-claim.
This is a scoring-application decision for the human reviewer, not a gap in
the rules themselves. It is not a blocking ambiguity.

**Status:** Scoring rules verified. No modification recommended.

---

### Decision Matrix

| Claim | User review preference | Technical verification | Formal status |
|---|---|---|---|
| Q1-C1 | ACCEPT | CONFIRMED (CODE MATCHES CLAIM) | PENDING |
| Q1-C2 | ACCEPT | CONFIRMED (CODE MATCHES CLAIM) | PENDING |
| Q1-C3 | VERIFY → recommend ACCEPT | VERIFIED (all invariants confirmed incl. `#L306-L329`) | PENDING |
| Q1-C4 | ACCEPT | CONFIRMED (CODE MATCHES CLAIM) | PENDING |

---

### Verdict Technique

**`ORACLE_READY_FOR_FORMAL_APPROVAL`**

All four Q1 required claims are factually supported at `60fae3e0`. The
supplementary reference `#L306-L329` has been verified and strengthens Q1-C3.
Scoring rules are format-neutral, complete, and consistent. No contradictions
or gaps were found.

This verdict means only that the evidence is sufficient to request formal human
approval. It does not mean that formal approval has been granted.

---

### Formal Decisions Still Required

1. **Oracle acceptance decision** — the reviewer must record `ACCEPTED`,
   `CORRECTED`, or `REJECTED` in `devlog-ai-q1-oracle-review-v1.md` with
   reviewer identity, date, and digest.
2. **Scoring rule confirmation** — explicit reviewer confirmation of the
   30/25/20/20/5 weights and the five scoring rules.
3. **Benchmark suitability confirmation** — explicit confirmation that Q1
   remains appropriate for the asymmetric A/B/C comparison.
4. **Field name optional detail confirmation** — explicit confirmation that
   `story_context_analyses.analysis_snapshot` need not appear in a correct answer.
5. All previous blocking decisions from §"Human Decisions Remaining" remain
   unchanged: live authorization, pricing, C transport path, artifact contract,
   provider/model/budget approvals.

---

### Provenance of This Section

- Verification method: `git show 60fae3e0:<path>` on `story/0184-bounded-story-context-investigation`
- Current HEAD at time of update: `5a26cd5`
- Files read during this update: `story_context_analysis_generation_service.py`, `devlog-ai-abc-oracle-proposals-v1.json`
- Files modified: `devlog-ai-q1-oracle-decision-dossier-v1.md` (this file, append only)
- Files not modified: `devlog-ai-abc-oracle-proposals-v1.json`, `devlog-ai-q1-oracle-review-v1.md`, `devlog-ai-abc-first-test-manifest-v1.json`
- Provider calls: `0`
- External network calls: `0`


---

## Formal Human Decision Record

*Recorded from explicit user approval communicated in this session. See
§"Three-Category Distinction" in the previous section for the distinction
between user review preference, technical verification, and formal acceptance.*

### Decision

| Field | Value |
|---|---|
| Decision | `ACCEPTED` |
| Reviewer | `Hopeful117` |
| Date | `2026-11-15T14:51:33+01:00` |
| Oracle version | `1.1.0` |
| Repository revision | `60fae3e0e1104d0ffbe3832c1f578ed532b1268a` |
| Digest (pre-acceptance) | `464fcd1b50b785e78ddd76e0b4eea3e65ac6a85fb124b31cea9c355951fcc457` |
| Digest (post-acceptance) | `2127d4ce224b1f39f5d8f113bb0b24b3dab7e9443fcb101600a774341c8d6d4e` |

### Claims Accepted

| Claim | Decision |
|---|---|
| Q1-C1 — Core Authorization and Preparation | ACCEPT |
| Q1-C2 — Submission and Snapshots | ACCEPT |
| Q1-C3 — Python Generation and Investigation Boundary | ACCEPT |
| Q1-C4 — Core Validation and Non-Trusted Persistence | ACCEPT |

### Scoring Accepted

| Element | Decision |
|---|---|
| Weights 30/25/20/20/5 (technicalAccuracy / evidenceAndTraceability / depth / engineerUsefulness / uncertaintyHandling) | ACCEPT |
| Scoring rules (correct / partiallyCorrect / omission / contradiction / unsupported) | ACCEPT |
| Format-neutrality rules | ACCEPT |
| Q1 suitability for asymmetric A/B/C comparison | ACCEPT |

### Reservation — Contradiction Handling

The contradiction handling rule is preserved as written in the oracle proposals.
No new per-claim nullification rule was introduced. The user decision was
`REVIEW`, meaning: retain existing behavior for this pilot without further
specification. This reservation is recorded; it does not block oracle acceptance.

### Artifacts Updated

| File | Change |
|---|---|
| `devlog-ai-abc-oracle-proposals-v1.json` | Q1 `status` → `ACCEPTED`; `canonicalDigest` → `2127d4ce224b1f39f5d8f113bb0b24b3dab7e9443fcb101600a774341c8d6d4e` |
| `devlog-ai-q1-oracle-review-v1.md` | Decision record: `ACCEPTED`, reviewer, date, reservations, `Q1_ORACLE_ACCEPTED` |
| `devlog-ai-abc-oracle-human-review-sheets-v1.md` | Q1 human semantic review → `ACCEPTED` with reviewer and date |
| `devlog-ai-q1-oracle-decision-dossier-v1.md` | This section appended |

### Files Not Modified

- `devlog-ai-abc-first-test-manifest-v1.json` — no change
- Scoring weights and rules — no change
- Claim formulations — no change
- Evidence references — no change
- Live authorization status — no change

### Oracle Acceptance Status

**`Q1_ORACLE_FORMALLY_APPROVED`**

The four Q1 claims, the scoring rules and weights, and the benchmark suitability
have been formally accepted. The `Q1_ORACLE_PENDING_HUMAN_REVIEW` status is lifted.

### Remaining Live Blockers (Unchanged)

The following statuses are unchanged by this oracle acceptance:

```
Q1_LIVE_TECHNICALLY_BLOCKED
Q1_LIVE_NOT_AUTHORIZED
```

Before any live execution:

1. Live C Python-to-Core callback path must be verified end-to-end.
2. Live artifact duration/token/cost contract must be completed.
3. Approved model pricing and an explicit Q1 cost ceiling must be supplied.
4. Provider/model/budgets and artifact destinations must be approved.
5. A campaign-scoped authorization with expiry must be created.

Oracle acceptance is not campaign authorization.

### Provenance of This Section

- Reviewer identity: `Hopeful117` (provided by user in this session)
- Date recorded: `2026-11-15T14:51:33+01:00`
- Approval communicated: via conversation message in this session
- Digest recomputed and verified: `2127d4ce224b1f39f5d8f113bb0b24b3dab7e9443fcb101600a774341c8d6d4e`
- Provider calls: `0`
- External network calls: `0`
