# Story 0050 — Internal Human Context Inputs — Engineering Report

## Status

Completed

## Story Recap

Story 0050 operationalizes ADR-052.

Before this Story, DevLog could understand repository-grounded evidence and
trusted knowledge, but it had no native mechanism for persisting project-owner
context that is:

* important for future analyses;
* not recoverable from the repository;
* not meant to bypass trusted-knowledge validation.

The Story goal was therefore not to scan more repository files.

It was to add an internal DevLog capability for human-authored project context.

## Problem

DevLog’s analysis quality is constrained when important project context exists
only in the project owner’s head.

Examples include:

* medium-term objectives
* known gaps
* non-obvious assumptions
* domain clarifications
* architectural constraints

Without a persistent internal place for that information:

* future analyses remain under-contextualized;
* humans cannot reuse that context reliably inside DevLog;
* the system is tempted either to ignore the context or to smuggle it into the
  wrong abstraction.

The key architectural risk was clear:

human context must enrich analysis without collapsing into trusted knowledge.

## Implemented Outcome

Story 0050 delivered a small but complete vertical slice.

### Backend capability

New dedicated domain:

* `ProjectHumanContextInput`
* `ProjectHumanContextInputType`
* `ProjectHumanContextInputStatus`

Database support:

* `V38__create_project_human_context_inputs.sql`

The entity is:

* project-owned
* persisted inside DevLog
* cascade-deleted with the project
* explicitly typed
* explicitly statused

### API capability

New project-scoped endpoints:

* list notes
* create note
* archive note

This is enough to establish real use without over-expanding the lifecycle.

### Context propagation

The most important architectural outcome is not the CRUD itself.

It is the propagation path:

* `ProjectContextSnapshot`
* `AnalysisContext`
* `SelectedKnowledge`
* prompt projection

Active human context now has an explicit bounded path into AI-facing analysis
context.

That is what turns the feature into analysis infrastructure rather than an
incidental note pad.

### User-facing workflow

The first UI lives in project `Settings` as `Project Notes`.

The user can now:

* read existing notes;
* create a typed note;
* archive a note.

This is intentionally modest, but it is immediately useful.

## Why The Chosen Design Is Correct

Several tempting but weaker designs were avoided.

### Not repository-ingested Markdown

The Story does not add another repository file to scan.

That would make the capability depend on Git workflow and blur the distinction
between repository artifacts and DevLog-owned context.

### Not trusted knowledge

The Story does not store these notes as `Insight`, `Decision`, or validated
proposal payload.

That would weaken the trust model and confuse human declarations with accepted
knowledge.

### Not a generic wiki

The first slice is intentionally typed, status-aware, and project-scoped.

It captures analysis-relevant project context, not arbitrary documentation.

## Live Outcome

The local DevLog stack was rebuilt live after implementation.

The new API was then exercised against the existing `devlog-ai` project:

* project id:
  `f3d56247-aada-4a76-982b-e6802c0b309c`

Live seed note created:

* title: `Medium-term objective`
* type: `GOAL`
* status: `ACTIVE`

This proves that the first real use case motivating ADR-052 is now supported by
the implemented system.

## Documentation Reconciliation

Repository documentation update: **Required and completed**

Updated:

* [knowledge-model.md](/home/ludo/Bureau/workspace/devlog-ai/docs/knowledge-model.md:1)

Reason:

The repository now supports a concrete internal DevLog mechanism for persisting
human project context as analysis input, and the knowledge model should reflect
that evolution.

No broader architectural document update was required in this first slice.

## Tests And Verification

Passed:

* targeted backend tests for new service/controller plus context/prompt
  propagation
* targeted frontend tests for the new service and section
* lint
* formatting check
* diff formatting check
* live docker rebuild
* live API verification on the running stack

## What The Story Intentionally Does Not Do

Story 0050 does **not** yet implement:

* edit workflow
* note version history
* semantic ranking of many notes
* dedicated workspace navigation
* automatic trusted-knowledge promotion from human context

These are appropriate future evolutions, not defects in this slice.

## Engineering Learning

This Story reinforced a useful modeling rule:

when context is important but not repository-observable, create an explicit
human-context channel instead of forcing that information into repository
evidence or trusted knowledge.

That rule now exists in three places:

* repository ADR-052
* the new DevLog capability itself
* the updated Obsidian vault guidance

## Final Outcome

Completed.

Story 0050 gives DevLog a new internal source of project context that is:

* durable
* explicit
* analysis-usable
* bounded
* and still architecturally separate from trusted knowledge

That is the right first step toward improving the quality of information
provided by DevLog to both human users and AI agents.
