# Story 0019 — Agent-Ready Engineering Story Context

## Story ID

0019

## Title

Deliver a compact, reliable Engineering Story Context to engineering agents

## Status

Completed

## Priority

High

## Date

2026-08-09

---

## User Story

As a developer preparing an Engineering Story with an AI engineering companion,
I want DevLog to return a compact and directly actionable repository context,
So that the agent receives the most useful evidence without spending a large part of its context
window on duplicated ranking metadata and rejected candidates.

---

## Context

DevLog now supports the complete short-term preparation path required by the Engineering Story
workflow:

* repository synchronization and Git history import;
* on-demand initialization or refresh of project understanding;
* immutable Project Profiles and deterministic AnalysisContext snapshots;
* layered Repository Context with explainable ranking and diversity-aware selection;
* bounded source/test content and Java declaration enrichment;
* a body-based Engineering Story Context endpoint consumed by the Engineering Story adapter.

Live validation after Story 0018 showed that context selection is useful and revision-correct, but
the transport representation remains disproportionately large. One representative request analyzed
124 candidates, selected 60 evidence items, and reported 3,301 used context tokens, while the JSON
response was approximately 154 KB and occupied roughly 38,000 tokens when delivered to the agent.
The selected evidence itself contained repeated score explanations and ranking reasons, while
selection decisions serialized metadata for both selected and rejected candidates.

The same validation also exposed a cold-request timeout against the Engineering Story adapter's
three-second bound, although subsequent requests completed in approximately 1.6–2.0 seconds.

The internal Repository Context is intentionally rich because it supports diagnostics,
explainability, auditing, and immutable AI-task provenance. The external Engineering Story consumer
does not need every internal field on every request. It needs a deterministic agent-facing
projection that preserves useful evidence and traceability while summarizing diagnostic detail.

This Story optimizes delivery, not discovery. It must not weaken ranking, selection, token safety,
repository security, or the current-repository verification requirement.

---

## Objective

Introduce a versioned Agent-Ready projection for Engineering Story Context that:

* contains only selected evidence and actionable traceability;
* removes or summarizes duplicated and rejected-candidate metadata;
* applies a measurable budget to the complete serialized agent payload;
* preserves an explicit route or mode for full diagnostic context;
* remains compatible with the current Engineering Story adapter contract;
* responds reliably within the bounded local workflow under representative warm and cold
  conditions.

The compact projection must be derived from the authoritative Repository Context after normal
collection, ranking, selection, symbol enrichment, content allocation, and final accounting. It
must not introduce a competing context engine or independently reinterpret evidence.

---

## Acceptance Criteria

### AC-1: The default Engineering Story response is agent-ready

The body-based Engineering Story Context operation returns a versioned compact Repository Context
projection suitable for direct transport to an engineering agent. The existing adapter continues
to locate `repositoryContext.evidence` and validate usable evidence without requiring a new
consumer-side ranking algorithm.

The projection is the default for the product-specific Engineering Story endpoint. Internal
analysis workflows and stored Repository Context snapshots retain their existing rich domain model.

### AC-2: Only selected evidence is transported

The agent projection contains only final selected evidence. Rejected candidates are represented by
bounded aggregate counts grouped by stable rejection reason where useful; individual rejected
candidate paths, scores, and explanations are absent.

The projection reports candidate count, selected count, discarded count, truncation state, and
warnings truthfully.

### AC-3: Evidence remains actionable and traceable

Every transported evidence item retains at least:

* layer and kind;
* stable reference and concise summary;
* provenance sufficient to locate the source repository/file/commit;
* resolved revision when applicable;
* final relevance score and a bounded set of non-duplicated selection reasons;
* bounded content and Java-symbol outcomes when present;
* related references when they materially aid navigation.

The projection must not expose identical explanations through multiple fields. Repository Analysis
must determine the smallest stable contract that preserves navigation and auditability.

### AC-4: Complete serialized size is bounded and measured

The Core calculates the UTF-8 serialized size and a deterministic token estimate for the canonical
semantic projection payload, including evidence metadata, diagnostics summary, content, symbols,
and warnings. The projection digest, byte count, and token-count fields themselves are excluded
from that canonical input to avoid self-referential accounting. The HTTP `Content-Length`, when
available, remains the authority for the complete wire representation.

The response exposes the applied projection policy/version, configured limits, actual byte count,
and actual deterministic token estimate. Budget accounting must describe the payload that is
actually transported, not only selected evidence before serialization.

### AC-5: Budget degradation is deterministic

When the initial projection exceeds its configured limit, reduction follows one documented,
deterministic policy. The policy should first remove or compact optional diagnostic detail before
removing actionable evidence content, and must never silently produce invalid or untraceable items.

Any removed content, symbols, related references, reasons, or complete evidence items produce
bounded aggregate warnings/counters. Repeated requests over identical inputs return the same
projection and digest.

### AC-6: A full diagnostic representation remains available

An explicit diagnostic mode or separate operation retains access to the complete existing
Repository Context, including individual selection decisions and full ranking explanations.

The diagnostic representation is not the default adapter payload. Its contract and intended human
or troubleshooting use are documented. Existing internal consumers do not lose information.

### AC-7: Agent projection identity is explicit

The compact response retains the authoritative Repository Context digest and also exposes a
projection-specific digest calculated from the final serialized semantic payload. It records the
Repository Context version, Agent Projection version, active profile keys, context-plan version,
and resolved repository revision information.

Consumers can distinguish changes in the underlying Repository Context from changes in projection
policy.

### AC-8: Latency and timeout behavior are observable

Representative cold and warm local requests are measured. The endpoint exposes or logs bounded
phase timings sufficient to distinguish repository synchronization/context construction from
projection serialization.

The default Engineering Story adapter timeout and the measured endpoint behavior must be
reconciled. A transient cold start must not routinely force the workflow to discard otherwise
usable DevLog context. Retry behavior, if introduced, must be bounded and must not duplicate
repository analysis work uncontrollably.

### AC-9: Repository Context semantics remain unchanged

Collection, ranking, diversity selection, content allocation, symbol extraction, security,
revision pinning, and the global Repository Context token budget keep their current ownership and
semantics. The projection consumes their final output and cannot promote rejected evidence or
re-rank selected evidence.

### AC-10: Current-repository verification remains mandatory

The projection explicitly communicates that synchronized evidence is navigation context and may
lag an uncommitted working tree. The Engineering Story workflow must continue targeted reads of the
current repository for exact behavior.

No projection claim may imply that DevLog replaces direct repository inspection.

### AC-11: Failure remains non-blocking and explicit

Projection construction or serialization failure produces the established visible
`DEVLOG_CONTEXT_ERROR` consumer outcome and Repository Analysis continues through direct repository
inspection. No malformed or partially serialized context may be presented as usable.

Diagnostic access remains available to investigate the failure.

### AC-12: Backend coverage is mandatory

Focused tests cover at least:

* compact projection from representative selected evidence;
* absence of individual rejected candidates;
* non-duplicated bounded ranking reasons;
* source, commit, decision, documentation, validated-knowledge, content, and symbol evidence;
* exact byte/token accounting for the complete serialized payload;
* deterministic degradation at each projection boundary;
* stable projection digest and sensitivity to semantic changes;
* retention of the authoritative Repository Context digest;
* unchanged full diagnostic representation;
* malformed/oversized projection failure behavior;
* generic Repository Context and AI-task snapshot compatibility.

### AC-13: Adapter-contract coverage is mandatory

Contract tests use the real serialized HTTP shape expected by the Engineering Story adapter. They
verify the default compact response, usable evidence detection, complete Story transport in the
request body, explicit diagnostic mode, malformed response handling, non-success handling, and
bounded timeout behavior.

### AC-14: Representative effectiveness validation is mandatory

Before implementation, capture a disposable baseline for the current `devlog-ai` project including
response bytes, approximate delivered tokens, latency, candidate/selected counts, and major payload
contributors.

After implementation, repeat the same representative Story request and demonstrate a material
reduction in actual bytes and delivered tokens while retaining the evidence needed to identify the
relevant implementation files, tests, ADRs, recent changes, and validated knowledge. The report
must not claim general productivity improvements from one repository measurement.

### AC-15: Documentation and quality are reconciled

Canonical API, architecture, and Engineering Story integration documentation describes the compact
default, diagnostic mode, projection policy and limits, digest semantics, timeout behavior, and
continued direct repository verification.

Run focused and complete backend validation, adapter contract tests, JaCoCo, authenticated
SonarQube with Quality Gate wait, Docker/API validation, and the representative before/after
benchmark. Completion requires a passing Quality Gate and no new unresolved issue attributable to
the Story.

---

## Out of Scope

* New collectors, evidence layers, ranking criteria, or semantic retrieval.
* Vector embeddings, RAG, symbol solving, dependency graphs, or call graphs.
* Working-tree ingestion or direct access to uncommitted local files by DevLog.
* Passive repository monitoring or automatic Project Understanding refresh.
* Changing AI-task prompt construction or stored AnalysisContext/Repository Context snapshots.
* Removing explainability or full diagnostics from the platform.
* Authentication, authorization, private-repository credentials, or multi-tenant isolation.
* Durable AgentJob orchestration, message brokers, or distributed caches.
* Proposal-review workflow improvements; those belong to a subsequent short-term Story.

---

## Architectural Constraints

* The Java Core remains authoritative for Repository Context and projection construction.
* Agent projection occurs after final Repository Context enrichment and accounting.
* Projection is deterministic, versioned, immutable for one response, and derived without AI.
* The existing rich Repository Context remains authoritative for diagnostics and internal
  provenance.
* The AI Engine never reads repositories and does not construct the projection.
* Synchronized Git evidence remains distinct from the current local working tree.
* No configured safety, content, symbol, or repository-confinement limit may be weakened.
* The current Engineering Story adapter's `repositoryContext.evidence` usability contract must be
  preserved unless a coordinated, explicitly validated migration is approved in planning.

---

## Success Measures

* The representative default response is materially smaller than the approximately 154 KB
  baseline.
* Delivered agent tokens are materially closer to the reported payload budget than the current
  approximately 38,000-versus-3,301 discrepancy.
* Relevant source, test, documentation, history, and validated-knowledge navigation evidence
  remains present.
* Rejected-candidate detail is absent from the default response and available in diagnostic mode.
* Warm and cold behavior is compatible with a documented bounded adapter timeout.
* Complete backend, contract, Docker, and SonarQube validation passes.

---

## Expected Workflow Artifacts

* `story.md`
* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
