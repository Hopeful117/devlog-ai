# Engineering Report — Story 0019

## Story

Story 0019 — Agent-Ready Engineering Story Context separates DevLog's rich internal Repository
Context from the compact representation delivered directly to engineering agents.

## Objective

Reduce duplicated transport metadata and rejected-candidate detail without weakening repository
selection, traceability, explainability, revision safety, or the mandatory direct inspection of the
current working repository. Preserve an explicit rich diagnostic mode and keep the existing
Engineering Story adapter contract operational within its bounded timeout.

## Repository Analysis Summary

Repository Analysis established that the Repository Context Engine's internal model was correctly
optimized for diagnostics, auditing, immutable AI-task provenance, and explainability, but was the
wrong transport shape for an agent. The exact Story 0019 baseline returned 164,445 JSON bytes for
60 selected evidence items even though internal accounting reported only 3,186 evidence tokens.

The approved architectural direction kept the rich model authoritative and introduced a distinct
projection at the `projectcontext` integration boundary. Projection had to occur after normal
collection, ranking, selection, symbol enrichment, content allocation, and final accounting, with
no second relevance engine.

## Implementation Plan Summary

The human-approved plan introduced:

* compact `agent` mode by default and unchanged `detail=full` diagnostics;
* immutable versioned projection DTOs;
* a 32-KiB / 8,192 estimated-token canonical semantic budget;
* deterministic mechanical degradation before final-tail evidence removal;
* separate authoritative Repository Context and projection digests;
* one reused Project Context snapshot per request;
* bounded phase observability, adapter compatibility, complete tests, live benchmarking,
  documentation reconciliation, Docker validation, and authenticated SonarQube.

## Implementation Summary

GET and POST Engineering Story Context operations now default to
`engineering-story-agent-projection-v1`. The response retains Project Context, selected evidence,
essential provenance and revisions, final score, bounded reasons and related references, content
and Java-symbol outcomes, aggregate diagnostics, policy identity, dual digests, and complete
projection accounting. Individual rejected candidates, criterion score maps, repeated
explanations, and allocation-reason duplication are omitted.

Canonical semantic JSON excludes generated time, its own digest, and accounting fields. Its UTF-8
bytes enforce both the hard byte limit and deterministic `ceil(bytes / 4)` token estimate. When
necessary, the projector removes related references, extra reasons, declaration payloads, content
text, and then final-tail selected evidence in fixed order. Every loss is represented by bounded
warnings and exact counters. No evidence is re-ranked and no rejected evidence can be promoted.

Full mode continues to serialize the existing rich `EngineeringStoryContext`. Internal generic
Repository Context, AI-task snapshots, persistence, ranking, selection, revision pinning,
filesystem security, symbol extraction, and content allocation remain unchanged.

During review, transported layer/kind aggregates were corrected to derive from the final projected
evidence list after degradation. This guarantees that evidence count, selected count, and aggregate
totals describe the same payload.

## Quality-Gate Reconciliation

The first authenticated SonarQube analysis included three inherited issues from Story 0018. After
explicit human authorization, both public history-import paths retained compatible Spring
transactional boundaries while sharing a private implementation, eliminating the self-invocation
proxy defect. A Project Understanding exception assertion now constructs its request outside the
lambda. Focused and complete tests verify the corrections.

## Architecture Impact

ADR-046 records the rich-domain/agent-transport separation. Java Core owns both representations,
but their identities and budgets are independent. The Repository Context digest identifies the
authoritative Core decision; the projection digest identifies the final canonical agent payload.

This is an integration contract change, not a new context engine. It adds no persistence,
collector, ranking criterion, semantic retrieval, tokenizer dependency, scheduler, broker, or
working-tree ingestion.

## Documentation Reconciliation

Documentation update: Completed.

* `README.md` documents compact default behavior, `detail=full`, configuration, dual digests,
  degradation, adapter fallback, and current-repository verification.
* `docs/architecture.md` documents the rich internal context and compact transport boundary.
* `docs/decisions/ADR-046.md` records the decision, consequences, and rejected alternatives.

## Validation

Final validation evidence:

* focused projection, controller, service, history, and Project Understanding tests passed;
* backend `clean verify`: 445 tests, 0 failures, 0 errors, 0 skipped;
* JaCoCo: 86.49% instruction, 65.14% branch, and 83.15% line coverage;
* Engineering Story adapter: 9 tests passed;
* authenticated SonarQube Quality Gate `OK`;
* new-code coverage 84.2%, new duplicated lines 0.0%, new violations 0;
* unresolved SonarQube issues 0;
* backend Docker image rebuilt and served the new contract;
* `git diff --check` passed.

The exact representative Story request fell from 164,445 rich wire bytes to 33,064 compact wire
bytes, a 79.9% reduction. Canonical accounting reported 32,684 bytes and 8,171 estimated tokens.
The final payload retained 34 selected evidence items with internally consistent counts; it removed
98 related references, 120 extra reasons, and 26 final-tail evidence items without removing content
or declaration payloads. `detail=full` retained the 164,445-byte diagnostic representation.

The real adapter completed the measured warm request in 0.88 seconds, within its three-second
bound. This benchmark demonstrates payload reduction and compatibility for the configured
`devlog-ai` repository; it does not claim general productivity improvement.

## Code Review Outcome

Review initially returned the inconsistent post-degradation aggregates and the failing repository
Quality Gate to implementation. Both were corrected with regression coverage and the complete
validation cycle was repeated.

The final Code Review found no remaining Blocker, Major, Minor, or Observation finding and
recommended approval.

Human Code Review approval: granted on 2026-08-09.

## Workflow Approvals

* Repository Analysis: Human approved
* Implementation Plan: Human approved
* Code Review: Human approved

## Residual Risks

* Tight configured limits may remove low-ranked final-tail evidence; counters, warnings, and full
  diagnostics make this explicit.
* Byte/4 token estimation is deliberately provider-neutral and approximate.
* Synchronized committed evidence may differ from an uncommitted working tree; direct current
  repository inspection remains mandatory.
* Cold infrastructure startup can exceed normal timings even when the endpoint itself remains
  within the local adapter bound.

These are bounded documented constraints, not unfinished Story scope.

## Remaining Work

None for Story 0019.

Evidence-discovery precision, semantic retrieval, working-tree ingestion, durable agent jobs, and
proposal-review workflow improvements remain separate future Stories.

## Lessons Learned

* Internal explainability models and external agent transport contracts need distinct budgets and
  identities.
* Complete serialized payload measurement exposes costs hidden by evidence-only token accounting.
* Deterministic degradation must keep every aggregate synchronized with the final transported list.
* Live benchmark requests reveal representation defects that isolated projection fixtures may not.
* Quality validation periods can surface inherited defects; scope expansion still requires explicit
  human authorization.

## Final Status

Completed

No commit, push, or merge was performed automatically.
