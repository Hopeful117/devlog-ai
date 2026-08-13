# Repository Analysis — Story 0051

## Story

* **ID:** 0051
* **Title:** Reduce Context Noise Before Enrichment

## Objective Restatement

Before DevLog adds richer context sources or stronger semantic enrichment, it
must first reduce low-value noise in the context it already exposes.

The Story targets two concrete problems:

1. project-facing `project-state` outputs currently surface semantically
   redundant proposed proposals with little prioritization;
2. agent-facing Engineering Story Context projection can still fail with
   `AgentContextProjectionException` when the selected context cannot fit the
   configured transport budget.

The goal is not to invent a new intelligence layer. The goal is to improve
signal quality and robustness at the existing projection and summary
boundaries.

## Why This Story Exists Now

Story 0050 proved that internal human context inputs are useful.

The inserted project note successfully exposed a medium-term product goal that
was not recoverable from repository evidence alone. That is a positive
validation of DevLog as a context supplier.

However, the evaluation also showed that the existing outputs remain too noisy
to turn that new signal into a clean next-step recommendation:

* the note appears in `project-state`, but competes with many repetitive
  `PROPOSED` proposals;
* `project-state.activeWork.proposedProposals` and
  `project-state.pendingActions.proposedProposals` currently expose raw
  repository proposal stock without deterministic reduction;
* the Engineering Story Context endpoint can fail entirely instead of
  returning a degraded but usable payload.

This means the current system can expose more context than before, but not yet
distill it reliably.

That makes noise reduction the correct next step before enrichment.

## Relevant Existing Architecture

### 1. Agent-ready projection already exists

Story 0019 and ADR-046 already introduced the distinction between:

* rich authoritative `RepositoryContext`;
* compact agent-facing projection returned by the Engineering Story endpoint.

Relevant sources:

* [docs/stories/0019-agent-ready-engineering-story-context/story.md](/home/ludo/Bureau/workspace/devlog-ai/docs/stories/0019-agent-ready-engineering-story-context/story.md)
* [docs/decisions/ADR-046.md](/home/ludo/Bureau/workspace/devlog-ai/docs/decisions/ADR-046.md)

The current implementation is in
[AgentContextProjectionService.java](/home/ludo/Bureau/workspace/devlog-ai/backend/src/main/java/com/hopeful117/devlogai/projectcontext/projection/AgentContextProjectionService.java).

The projection already supports deterministic degradation in this order:

1. remove related references
2. compact reasons
3. remove declarations
4. remove content text
5. remove tail evidence

This is structurally sound and aligned with ADR-046.

### 2. Project State currently exposes raw proposal lists

Project overview data is assembled in
[ProjectStateProjectionServiceImpl.java](/home/ludo/Bureau/workspace/devlog-ai/backend/src/main/java/com/hopeful117/devlogai/projectstate/service/ProjectStateProjectionServiceImpl.java)
and mapped in
[ProjectStateMapper.java](/home/ludo/Bureau/workspace/devlog-ai/backend/src/main/java/com/hopeful117/devlogai/projectstate/mapper/ProjectStateMapper.java).

Important observation:

* `buildActiveWork(...)` loads all `PROPOSED` proposals for the project and
  passes them directly to the mapper.
* `buildPendingActions(...)` does the same.
* `ProjectStateMapper.toProposalSummary(...)` only extracts payload fields and
  does not apply grouping, deduplication, ranking, or suppression.

On the frontend, [project-state-page.ts](/home/ludo/Bureau/workspace/devlog-ai/frontend/src/app/features/project-state/project-state-page.ts)
and [project-state-page.html](/home/ludo/Bureau/workspace/devlog-ai/frontend/src/app/features/project-state/project-state-page.html)
currently filter only proposals with no meaningful label.

So the current product behavior is:

* hide empty labels;
* keep every labeled proposal, including semantically repetitive ones.

That explains the observed noise.

### 3. Human context is now part of the same visible surface

Story 0050 added `humanContextInputs` into the objective section of project
state and into the analysis/selected-knowledge path.

Relevant sources:

* [docs/stories/0050-internal-human-context-inputs/story.md](/home/ludo/Bureau/workspace/devlog-ai/docs/stories/0050-internal-human-context-inputs/story.md)
* [docs/decisions/ADR-052.md](/home/ludo/Bureau/workspace/devlog-ai/docs/decisions/ADR-052.md)

This means that improving signal-to-noise now has direct user value:

* less repetitive proposal clutter;
* more room for distinct human-authored context;
* better chances that project intent remains visible.

## Concrete Observations From Live Evaluation

### 1. Project State contains heavy semantic repetition

Live `project-state` responses for `devlog-ai` currently include many
variations of the same claims:

* multiple project overview statements;
* multiple Spring Boot / REST architecture statements;
* multiple testing-structure statements;
* multiple Docker / containerization statements;
* multiple documentation / ADR statements.

These are not empty outputs.

They are low-value duplicates or near-duplicates presented as if they were
independent first-class signals.

This creates two problems:

* the user sees clutter rather than a prioritized snapshot;
* genuinely distinct signals compete with repetitive generic descriptions.

### 2. Engineering Story Context can still fail hard

Live calls to:

* `POST /api/projects/{projectId}/engineering-story-context`

produced backend `500` responses.

Backend logs show:

* `AgentContextProjectionException: Agent context cannot fit configured projection limits`

The exception comes from
`AgentContextProjectionService.removeTailEvidence(...)`.

The current code throws once only one evidence item remains and the payload
still does not fit.

This means the existing degradation chain is not sufficient in all cases.

That violates the intent of Story 0051:

* DevLog should prefer a degraded usable payload over a hard failure whenever
  possible.

### 3. The current state projection also appears partially stale

The live `project-state` response still reports story `47` as the active story
and shows old in-progress stories.

This is not the primary scope of Story 0051, but it reinforces the central
finding:

* project-facing context currently lacks a strong reduction and curation layer;
* raw stock leaks too directly into the overview.

The Story should avoid expanding into a full project-state correctness audit,
but the design must acknowledge that surfacing everything raw is already
hurting usefulness.

## Current Test Coverage

### Agent projection tests

[AgentContextProjectionServiceTest.java](/home/ludo/Bureau/workspace/devlog-ai/backend/src/test/java/com/hopeful117/devlogai/projectcontext/projection/AgentContextProjectionServiceTest.java)
already covers:

* deterministic projection;
* degradation of references, reasons, declarations, and content;
* tail-evidence removal;
* failure when one evidence item cannot fit;
* projection digest sensitivity.

This is a strong base for extending the projection policy safely.

### Project state tests

[ProjectStateProjectionServiceTest.java](/home/ludo/Bureau/workspace/devlog-ai/backend/src/test/java/com/hopeful117/devlogai/projectstate/service/ProjectStateProjectionServiceTest.java)
currently covers section orchestration and empty-state behavior, but does not
assert any proposal-noise reduction policy.

Frontend state-page tests exist, but the current page logic only removes
display-invalid proposals. It does not validate deduplication or grouping.

So this Story will need new focused tests, not just minor fixture updates.

## Design Tension

The key architectural tension is:

* we want less noise;
* we do not want a second AI ranking engine;
* we must keep deterministic, explainable, repository-owned behavior.

That excludes broad AI-based semantic clustering in this Story.

The right short-term direction is deterministic reduction based on stable
proposal summary keys and bounded grouping rules.

## Candidate Solution Directions

### Direction A — Frontend-only suppression

Approach:

* deduplicate proposal rows in Angular before rendering.

Advantages:

* small implementation surface;
* easy visual improvement.

Drawbacks:

* duplicates still remain in API responses;
* backend and frontend semantics drift;
* does not help other consumers;
* does not address agent projection failure at all.

Verdict:

* insufficient as the primary solution.

### Direction B — Backend project-state reduction policy

Approach:

* reduce proposal noise in backend `ProjectStateProjectionServiceImpl` /
  `ProjectStateMapper` before shipping `ProjectStateResponse`.

Potential deterministic key material:

* proposal `type`
* `insightType`
* normalized title
* normalized summary

Potential policy shape:

* exact-key deduplication first;
* bounded grouping of near-identical project-presentation and
  technology-description summaries;
* preserve higher-confidence representative;
* optionally expose grouped-count metadata if needed.

Advantages:

* one authoritative rule for all project-state consumers;
* aligns with Story scope;
* keeps proposal history untouched while cleaning the projection.

Drawbacks:

* requires careful policy design to avoid hiding distinct proposals.

Verdict:

* recommended for the project-facing part of the Story.

### Direction C — Harden agent projection fallback

Approach:

* keep current degradation chain, but make final failure rarer and more
  graceful.

Possible options:

1. allow stronger degradation of the last evidence item:
   * strip summary verbosity
   * strip optional provenance fields
   * collapse content/symbol/status sections further
2. introduce a last-resort compact sentinel shape for one remaining evidence
   item rather than throwing immediately
3. distinguish “no usable evidence can fit” from “current representation cannot
   fit”

Advantages:

* directly fixes the observed `500`;
* respects ADR-046 intent better than a hard failure.

Drawbacks:

* must preserve minimum usable agent contract;
* must remain deterministic and explicit.

Verdict:

* recommended for the agent-facing part of the Story.

## Recommended Repository Strategy

Implement Story 0051 as two coordinated projection-boundary improvements.

### 1. Add deterministic proposal-noise reduction to Project State

At the backend project-state boundary:

* reduce exact and obvious near-duplicate proposal summaries;
* preserve distinct items;
* prefer representative items with clearer title / summary / confidence;
* keep the raw proposal repository untouched.

This improves the user-facing overview without redefining proposal semantics.

### 2. Extend agent projection to degrade further before failing

At the Engineering Story Context projection boundary:

* preserve the current staged degradation order;
* add a documented last-resort compacting step for the final remaining
  evidence item before throwing;
* throw only when even the minimum viable compact contract cannot fit.

This aligns better with “context distillation” and should eliminate the
observed avoidable `500` cases.

## Risks

### 1. Over-deduplication can hide distinct meaning

If grouping is too aggressive, proposals that are related but materially
different may collapse into one row.

Mitigation:

* use deterministic narrow rules first;
* prefer exact normalized duplicates before broader heuristics;
* keep the first slice conservative.

### 2. Last-resort projection could become too weak to be useful

If the final fallback strips too much, the endpoint may technically succeed
but stop being practically useful.

Mitigation:

* define a minimum viable projected evidence contract explicitly;
* keep strong test coverage around “usable evidence” semantics.

### 3. Noise reduction without visibility can look arbitrary

If the system silently drops items, users may not trust the reduced view.

Mitigation:

* expose bounded counters and warnings when grouping or compaction occurs.

## Affected Areas

Likely backend areas:

* `backend/src/main/java/com/hopeful117/devlogai/projectstate/service/`
* `backend/src/main/java/com/hopeful117/devlogai/projectstate/mapper/`
* `backend/src/main/java/com/hopeful117/devlogai/projectstate/dto/`
* `backend/src/main/java/com/hopeful117/devlogai/projectcontext/projection/`

Likely frontend areas:

* `frontend/src/app/features/project-state/`

Likely documentation areas:

* `docs/architecture.md`
* possibly `docs/knowledge-model.md` if projection semantics need clarification

Likely tests:

* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/projection/`
* `backend/src/test/java/com/hopeful117/devlogai/projectstate/`
* `frontend/src/app/features/project-state/`

## Recommendation

Proceed with Story 0051.

The repository already contains the right architectural seams for this work:

* a dedicated project-state projection boundary;
* a dedicated agent-context projection boundary;
* existing degradation and test foundations;
* a newly introduced human-context signal worth protecting from noise.

This Story is a natural follow-up to 0050 and a necessary prerequisite before
broader context enrichment.
