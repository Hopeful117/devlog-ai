# Story 0050 — Internal Human Context Inputs

## Status

Draft

## Priority

High

## Objective

Introduce a first vertical slice of internal human-authored project context in
DevLog so users can persist project notes inside DevLog and selected analyses
can consume that context without treating it as trusted knowledge.

## Motivation

DevLog is currently strongest when interpreting repository-grounded evidence.

However, important project context is often known only by the project owner and
is not recoverable from:

* source code;
* Git history;
* repository ADRs;
* deterministic project analysis.

Examples include:

* medium-term project objectives;
* important constraints;
* domain clarifications;
* known gaps in current system behavior.

Without a native mechanism for persisting this human context inside DevLog,
future analyses remain under-contextualized even when repository grounding is
strong.

ADR-052 defines the architectural direction:

* internal human context must be persistent;
* it must remain distinct from repository evidence and trusted knowledge;
* it must enrich analysis context without bypassing validation.

This Story implements the first useful slice of that capability.

## Scope

### In Scope

1. Add a project-owned backend entity for internal human context inputs stored
   inside DevLog.
2. Support a minimal typed CRUD surface for these inputs.
3. Distinguish active human context from archived or inactive context.
4. Expose project human context through a user-facing UI entry point inside the
   project workspace.
5. Make active human context available to bounded analysis context / selected
   knowledge for at least the relevant project-understanding analysis path.
6. Preserve the architectural distinction between:
   * observed evidence,
   * declarative human context,
   * trusted knowledge.
7. Seed the feature with the medium-term project objective already identified
   during this workflow.

### Out Of Scope

* full historical versioning of edited inputs
* semantic ranking of large note corpora
* automatic promotion of human inputs into trusted knowledge
* repository import of Markdown files
* generic collaborative wiki behavior
* broad redesign of knowledge views or overview projections unrelated to this
  first slice

## Constraints

* human context inputs must remain internal to DevLog, not repository-backed
  files
* the feature must not weaken ADR-006 validation boundaries
* notes must be typed and status-aware rather than an unstructured text dump
* context injection must remain bounded and explicit
* the first slice should prefer a small usable vertical path over an
  over-general framework

## Acceptance Criteria

* AC-1: a user can create and persist a typed human context input attached to a
  project inside DevLog.
* AC-2: the project workspace exposes a human-facing UI for listing and adding
  these inputs.
* AC-3: active human context inputs are retrievable as distinct contextual data
  and are not modeled as trusted knowledge.
* AC-4: at least one relevant analysis path consumes active human context in
  its bounded AI-facing context.
* AC-5: tests cover persistence, API behavior, bounded context inclusion, and
  the first user-facing workflow.
* AC-6: the seed project note capturing the medium-term objective can be stored
  through the new capability.

## Dependencies

* ADR-052 — Internal Human Context Inputs for Analysis

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
