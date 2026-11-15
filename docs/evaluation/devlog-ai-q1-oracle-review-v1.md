# Q1 Corrected Oracle Review Sheet

## Review Status

- Case: `Q1`
- Question: **How does a Story Context request move from Core authorization to the final persisted analysis, and which side owns each invariant?**
- Frozen repository: `git:github.com/Hopeful117/devlog-ai`
- Frozen revision: `60fae3e0e1104d0ffbe3832c1f578ed532b1268a`
- Oracle version: `1.1.0`
- Oracle status: `CORRECTION_PROPOSED`
- Automated reference verification: `PASS`
- Human semantic validation: `PENDING`
- Human acceptance: **not granted**

The canonical machine-readable oracle is the Q1 entry in
`docs/evaluation/devlog-ai-abc-oracle-proposals-v1.json`. Its digest excludes
only that entry's `canonicalDigest` field; see the decision record below.

## Corrected Claims

### Q1-C1 — Core Authorization and Preparation

**Correct formulation:** Java Core resolves the project, invokes authorization
for `MCP_STORY_AGENT_EXECUTE`, verifies the Story/project relationship and
required intent, then prepares the context before calling the submission path.

**Status:** `VERIFIED`

**Required invariants:**

- Project resolution is Core-owned.
- The required authorization scope is checked before submission.
- A supplied Story must belong to the resolved project.
- Intent is present and resolved.
- Preparation precedes `submitPrepared`.

**Optional details:** the authorization service's complete policy rules and the
exact idempotency implementation.

**Expected qualification:** the use case proves authorization invocation and
the boundary, not every internal policy rule implemented by the injected
authorization service.

**Forbidden:** claiming that the use case contains the complete authorization
policy, or that Python authorizes the repository operation.

**References:**

- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java#L119-L141`
- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java#L168-L173`

### Q1-C2 — Submission and Snapshots

**Correct formulation:** `StoryContextSubmissionService` creates the Analysis
and AiTask execution records, binds context/projection/grounding identities,
records budget and loss accounting, and submits the accepted task to the AI
Engine. Submission is not proof that provider processing succeeded.

**Status:** `VERIFIED`

**Required invariants:** canonical context digest is required; Analysis/AiTask
records and the execution snapshot are persisted; grounding, projection,
submission and context identities are retained; budget rejection fails before
submission; successful submission delegates to Core's AI Engine client.

**Optional details:** individual snapshot keys, provider binding fields, and the
exact loss-ledger representation.

**Expected qualification:** distinguish task submission/transport from provider
completion and final callback acceptance.

**Forbidden:** claiming that `aiEngineClient.submit` proves a completed analysis
or that every internal snapshot field is required for a correct answer.

**References:**

- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/StoryContextSubmissionService.java#L147-L169`
- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/StoryContextSubmissionService.java#L221-L239`

### Q1-C3 — Python Generation and Investigation Boundary

**Correct formulation:** Python performs provider-backed structured generation
and validates the generated result. Only the investigation-enabled path enters
the PLAN/ACTION/FINAL loop: Python proposes bounded actions and validates typed
Core results, while Core remains authoritative for action scope, revision,
authorization and limits.

**Status:** `VERIFIED`

**Required invariants:** ordinary Story Context generation can complete without
the investigation loop; investigation begins with a validated PLAN; actions are
sent to Core through the callback client; typed action results and identities
are validated before reuse; Python does not receive repository or database
authority.

**Optional details:** exact corrective retry count, interaction-trace accounting
fields, and concrete callback transport.

**Expected qualification:** PLAN/ACTION/FINAL describes the
investigation-enabled path, not every Story Context request; provider
generation and Core action execution are separate responsibilities.

**Forbidden:** claiming that all requests pass through PLAN/ACTION/FINAL, that
Python directly reads the repository/database, or that a provider-generated
action is self-authorizing.

**References:**

- `ai-engine/app/services/story_context_analysis_generation_service.py#L128-L169`
- `ai-engine/app/services/story_context_analysis_generation_service.py#L224-L285`
- `ai-engine/app/services/story_context_analysis_generation_service.py#L306-L329`
- `ai-engine/app/schemas/story_context_investigation.py#L122-L156`
- `ai-engine/app/schemas/story_context_investigation.py#L204-L220`
- `docs/decisions/ADR-067.md#L33-L62`

### Q1-C4 — Core Validation and Non-Trusted Persistence

**Correct formulation:** Java Core validates the callback result against task
identity, digest, grounding, references, classifications and related
invariants, then persists it as an immutable non-trusted `StoryContextAnalysis`
snapshot. Persistence does not promote the analysis to trusted knowledge.

**Status:** `VERIFIED`

**Required invariants:** a callback contains an analysis result; Core validates
the authorized grounding set and context digest; invalid grounding, references
or classifications are rejected; the persisted snapshot is immutable and
associated with the task; the persisted analysis remains non-trusted.

**Optional details:** the precise durable field name
`story_context_analyses.analysis_snapshot`, prompt execution metadata, context
freshness, and individual validator checks.

**Expected qualification:** the field name is a precise optional detail, not a
required phrase for a good answer; persisted is not equivalent to promoted
trusted knowledge.

**Forbidden:** claiming that successful persistence makes the result trusted,
that the model can bypass Core callback validation, or that ADR-067 mandates
this exact database column name.

**References:**

- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/StoryContextCallbackService.java#L71-L100`
- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/StoryContextResultValidator.java#L15-L61`
- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/entity/StoryContextAnalysis.java#L38-L51`
- `docs/decisions/ADR-067.md#L124-L146`
- `docs/decisions/ADR-067.md#L183-L193`

## Global Evaluation Boundaries

### Acceptable Qualified Claims

- The provider may propose actions, but Core executes only validated actions
  within the initialized scope, revision and budgets.
- The current implementation persists a `StoryContextAnalysis` snapshot, while
  ADR-067 defines snapshot semantics without mandating a new table or exact
  field name.
- The repository establishes ownership and flow, not comparative semantic
  quality.

### Forbidden Claims

- Python directly reads the repository or database.
- A successful AI result is automatically trusted knowledge.
- PLAN/ACTION/FINAL is mandatory for every request.
- The current implementation proves improved answer quality.

### Expected Uncertainty

- The cited code establishes ownership and flow, but not comparative semantic
  quality.
- The authorization service's complete policy and provider semantic success are
  outside the cited boundaries.

## Format-Neutral Scoring

The oracle is applied after deterministic normalization and does not reward a
particular response format, treatment, prose length, or citation count.

| Criterion | Weight |
|---|---:|
| Technical accuracy | 30 % |
| Evidence and traceability | 25 % |
| Depth of analysis | 20 % |
| Engineer usefulness | 20 % |
| Uncertainty handling | 5 % |

For each claim, the reviewer records:

- `CORRECT`: ownership and flow are accurate, with relevant evidence or
  explicitly qualified repository-grounded reasoning.
- `PARTIALLY_CORRECT`: main boundary is right but one non-central invariant is
  omitted, stages are collapsed, or evidence is incomplete without reversing
  the conclusion.
- `OMISSION`: a required claim or ownership boundary is not addressed.
- `CONTRADICTION`: authorization, repository execution, grounding acceptance,
  or trust promotion is assigned to the wrong side, or provider submission is
  called completed analysis.
- `UNSUPPORTED`: a material implementation/architecture claim is added without
  repository evidence and without uncertainty qualification.

Technical failures are not semantic failures and are excluded from the semantic
denominator. The absence of a native `answerText` is a format difference, not
an automatic penalty. Evidence quantity alone is not evidence quality.

## Human Decisions Required

1. Accept, correct, or reject the four corrected claim formulations.
2. Confirm that the exact field name is optional scoring detail, not mandatory
   wording.
3. Confirm that Q1 remains suitable for the asymmetric comparison.
4. Confirm the weighted, format-neutral scoring rules.

## Decision Record

The reviewer must make one explicit decision and record the date, reviewer
identity, repository revision, and final oracle digest:

- `ACCEPTED`: claims and evidence are frozen for scoring;
- `CORRECTED`: reviewer edits the oracle and records a new digest before any
  execution;
- `REJECTED`: Q1 is not used and the manifest remains stopped.

The digest is SHA-256 of canonical JSON for the Q1 oracle object with only
`canonicalDigest` omitted, encoded as UTF-8, with sorted keys and compact
separators. This avoids an authorial hash cycle. The digest field itself is not
included in the bytes it authenticates.

- Decision: `PENDING`
- Proposed decision: `CORRECTION_PROPOSED`
- Reviewer: `<not recorded>`
- Date: `<not recorded>`
- Repository revision: `60fae3e0e1104d0ffbe3832c1f578ed532b1268a`
- Final oracle digest: `464fcd1b50b785e78ddd76e0b4eea3e65ac6a85fb124b31cea9c355951fcc457`
- Reservations: `<not recorded>`
- Authorization status: `Q1_ORACLE_READY_FOR_HUMAN_APPROVAL; Q1_LIVE_NOT_AUTHORIZED`

No provider call may occur while the decision is `PENDING`.
