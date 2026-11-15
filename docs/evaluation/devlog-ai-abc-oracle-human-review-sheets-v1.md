# DevLog AI A/B/C Oracle Human Review Sheets

## Review Protocol

- Repository revision: `60fae3e0e1104d0ffbe3832c1f578ed532b1268a`
- Automated verification means only that the referenced path and locator
  resolve. It does not validate semantic support.
- Human semantic review status for every case: `PENDING`.
- No oracle is accepted by this document.

## Q1

- **Question:** How does a Story Context request move from Core authorization to the final persisted analysis, and which side owns each invariant?
- **Required claims:** Q1-C1 `VERIFIED`; Q1-C2 `VERIFIED`; Q1-C3 `VERIFIED`; Q1-C4 `VERIFIED`.
- **Supporting evidence:** `AnalyzeStoryContextUseCase.java#L105-L141`; `StoryContextSubmissionService.java#L122-L239`; `story_context_analysis_generation_service.py#L128-L169`; `story_context_analysis_generation_service.py#L224-L285`; `story_context_investigation.py#L122-L156`; `ADR-067.md#L124-L146`; `ADR-067.md#L183-L212`.
- **Contradictory evidence:** None identified automatically; human must check ownership and persistence wording against the current code.
- **Acceptable alternatives:** Core executes only validated actions; current persistence is an existing task snapshot rather than a new table.
- **Forbidden claims:** Python directly reads repository/database; successful AI output is automatically trusted; current implementation proves quality improvement.
- **Expected uncertainty:** Flow and ownership are established; comparative semantic quality is not.
- **Automated verification:** `PASS` for paths/locators. **Human semantic review:** `PENDING`.
- **Open questions:** Does “final persisted analysis” need to name the exact snapshot entity in the reviewed answer?

## Q2

- **Question:** When an investigation action is repeated, what happens to repository reads and budget usage?
- **Required claims:** Q2-C1 `VERIFIED`; Q2-C2 `VERIFIED`; Q2-C3 `VERIFIED`; Q2-C4 `INFERRED`.
- **Supporting evidence:** `StoryContextInvestigationActionService.java#L103-L193`; `StoryContextInvestigationActionServiceTest.java`; duplicate handling at `#L131-L141`.
- **Contradictory evidence:** None identified automatically; human must distinguish action idempotency from parent-task idempotency.
- **Acceptable alternatives:** Exact low-level repository call count is not observable; identical duplicates must not execute the capability again.
- **Forbidden claims:** All duplicates are accepted; duplicate replay consumes fresh retrieval budget by design; model owns duplicate handling.
- **Expected uncertainty:** Action-level and parent-task idempotency are separate.
- **Automated verification:** `PASS`. **Human semantic review:** `PENDING`.
- **Open questions:** Is the inferred accounting claim sufficiently supported by the current ledger implementation?

## Q3

- **Question:** Why does the current design keep Core authoritative and Python repository-blind?
- **Required claims:** Q3-C1 `VERIFIED`; Q3-C2 `VERIFIED`; Q3-C3 `VERIFIED`; Q3-C4 `VERIFIED`.
- **Supporting evidence:** `ADR-006.md#L11-L37`; `ADR-006.md#L39-L73`; `ADR-067.md#L33-L96`; `ADR-067.md#L160-L179`.
- **Contradictory evidence:** None identified automatically; human must confirm that the rationale is architectural rather than only cost-related.
- **Acceptable alternatives:** Future retrieval expansion may use authorized Core capabilities without giving Python an implicit repository handle.
- **Forbidden claims:** Boundary exists only for token cost; Python assigns evidence truth; all future retrieval expansion is forbidden.
- **Expected uncertainty:** ADR rationale is established; quality consequences remain experimental.
- **Automated verification:** `PASS`. **Human semantic review:** `PENDING`.
- **Open questions:** Are any current evaluation-only direct tools being incorrectly described as production Python access?

## Q4

- **Question:** If `READ_EVIDENCE_RANGE` limits or evidence projection change, which validation, grounding, and tests are affected?
- **Required claims:** Q4-C1 `VERIFIED`; Q4-C2 `VERIFIED`; Q4-C3 `VERIFIED`; Q4-C4 `VERIFIED`.
- **Supporting evidence:** `InvestigationCapabilityActionValidator.java#L46-L79`; `story_context_investigation.py#L98-L113`; `InvestigationCapabilityExecutionService.java#L73-L131`; `StoryContextInvestigationActionService.java#L174-L193`; `StoryContextResultValidator.java#L15-L43`; listed capability/execution/action/Python/behavioral tests.
- **Contradictory evidence:** None identified automatically; human must confirm the complete affected test surface.
- **Acceptable alternatives:** A limit-only compatible change may avoid a schema version change, but behavior changes still require identity/tests updates.
- **Forbidden claims:** Only Python schema is affected; grounding cannot change; tests/evaluation identities need no update.
- **Expected uncertainty:** Exact tests depend on whether evidence semantics change.
- **Automated verification:** `PASS`. **Human semantic review:** `PENDING`.
- **Open questions:** Which current test names are mandatory for a limit-only versus semantic change?

## Q5

- **Question:** Which claims in older Story 0184 repository artifacts no longer describe the current branch, and why?
- **Required claims:** Q5-C1 `VERIFIED`; Q5-C2 `VERIFIED`; Q5-C3 `VERIFIED`; Q5-C4 `VERIFIED`.
- **Supporting evidence:** Story 0184 implementation report `#L3-L19`, `#L21-L33`, `#L101-L128`; current-state investigation `#L11-L27`, `#L74-L80`; `InvestigationCapabilityRegistry.java#L8-L14`; benchmark manifest.
- **Contradictory evidence:** The historical report is stale for branch/worktree identity but may remain valid provenance; human must check each implementation claim.
- **Acceptable alternatives:** Historical documents can remain valid provenance while stale as current-state records.
- **Forbidden claims:** Entire implementation report is false; current branch is `main` at `d83600a`; passing tests proves quality improvement.
- **Expected uncertainty:** Historical execution results cannot be reproduced from the report alone.
- **Automated verification:** `PASS`. **Human semantic review:** `PENDING`.
- **Open questions:** Which report statements require current-code confirmation before being retained as oracle claims?

## Q6

- **Question:** What evidence is needed to decide whether a weak answer came from candidate loss, projection loss, or synthesis failure?
- **Required claims:** Q6-C1 `VERIFIED`; Q6-C2 `VERIFIED`; Q6-C3 `INFERRED`; Q6-C4 `VERIFIED`.
- **Supporting evidence:** current-state investigation `#L109-L124`; Story 0179 evaluation report `#L42-L65`; Story 0184 repository analysis `#L64-L109`; `StoryContextSubmissionService.java#L136-L169`; ADR-066 `#L389-L480`; comparative protocol `#L40-L52`, `#L82-L97`; Story 0180 report `#L49-L69`.
- **Contradictory evidence:** None identified automatically; human must confirm denominator and visibility wording.
- **Acceptable alternatives:** Provider-visible evidence is `UNKNOWN` where the runtime lacks per-reference attribution.
- **Forbidden claims:** Candidate count proves model visibility; grounding proves all evidence was used; timeout is a semantic error.
- **Expected uncertainty:** Visibility is unknown without per-reference attribution.
- **Automated verification:** `PASS`. **Human semantic review:** `PENDING`.
- **Open questions:** Does the proposed ledger expose enough data for each Q6 distinction?

## Q7

- **Question:** Does the current repository prove that the bounded analyst improves real engineering answer quality?
- **Required claims:** Q7-C1 `VERIFIED`; Q7-C2 `VERIFIED`; Q7-C3 `VERIFIED`.
- **Supporting evidence:** Story 0184 behavioral baseline `#L1-L6`, `#L182-L236`; implementation report `#L101-L128`; current-state investigation `#L74-L80`, `#L104-L107`; V4 official report `#L40-L68`; ADR-066 `#L417-L451`.
- **Contradictory evidence:** None identified automatically; human must ensure technical readiness is not presented as quality evidence.
- **Acceptable alternatives:** Technically prepared for controlled evaluation, subject to the B/C capability blocker.
- **Forbidden claims:** Analyst proven superior or useless; V4 technical validity is answer-quality evidence.
- **Expected uncertainty:** Real semantic quality, cost and usefulness remain unmeasured.
- **Automated verification:** `PASS`. **Human semantic review:** `PENDING`.
- **Open questions:** Should the oracle require explicit mention of the current normalization/capability blockers?

## Q8

- **Question:** Do the 0177 fixture result and V4 live result justify production hybrid retrieval or direct-agent adoption?
- **Required claims:** Q8-C1 `VERIFIED`; Q8-C2 `VERIFIED`; Q8-C3 `VERIFIED`; Q8-C4 `INFERRED`.
- **Supporting evidence:** Story 0177 evaluation report `#L3-L17`, `#L34-L56`; V4 official report `#L40-L68`, `#L203-L215`; ADR-063 `#L267-L297`, `#L416-L434`; current-state investigation `#L126-L163`; comparative protocol `#L136-L147`.
- **Contradictory evidence:** None identified automatically; human must reconcile historical runtime failures with any architectural conclusion.
- **Acceptable alternatives:** Direct investigation is worth testing; technical validity does not establish superiority.
- **Forbidden claims:** 0177 proves production RAG; V4 proves B/C superiority; fixture recall equals answer-quality improvement.
- **Expected uncertainty:** Relative value of active investigation and DevLog context remains the benchmark question.
- **Automated verification:** `PASS`. **Human semantic review:** `PENDING`.
- **Open questions:** Is Q8 too broad for the first test, despite being suitable for the full campaign?
