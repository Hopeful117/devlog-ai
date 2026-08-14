# Story 0054 — Detect Stale Understanding And Projection Refresh Gaps

## Status

Draft

## Priority

High

## Objective

Implement the first deterministic context-maintenance signals for stale project
understanding and outdated projection refreshes, and surface them as
maintenance findings.

## Motivation

The most obvious and least ambiguous maintenance failures in DevLog today are
freshness-related:

* project understanding can fall behind newer evidence;
* timeline or other projections can become stale or fail to reflect recent
  project activity;
* users and future agents may consume context that still exists technically but
  is no longer operationally trustworthy.

These cases are high-value first targets for ADR-053 because they are:

* concrete;
* deterministic enough to explain;
* immediately useful to both humans and agents;
* much less ambiguous than semantic duplicate cleanup.

This Story delivers the first real maintenance logic on top of the findings
foundation.

## Scope

### In Scope

1. Define deterministic freshness or staleness rules for project understanding.
2. Define deterministic rules for detecting projection refresh gaps for at
   least one projection surface such as timeline.
3. Generate maintenance findings when those rules are triggered.
4. Expose enough explanation for the finding to be actionable.
5. Keep the policy bounded and documented.

### Out Of Scope

* automatic semantic remediation
* duplicate trusted-knowledge detection
* broad scheduler orchestration
* deep AI reasoning about ambiguous maintenance cases

## Constraints

* freshness checks must be deterministic and explainable
* the system must avoid noisy false positives caused by trivial activity
* findings should distinguish stale context from merely unchanged context
* no trusted knowledge may be mutated by this Story

## Acceptance Criteria

* AC-1: DevLog can detect at least one stale project-understanding condition.
* AC-2: DevLog can detect at least one outdated or missing projection-refresh
  condition for a project-facing projection surface.
* AC-3: triggered conditions create explicit maintenance findings rather than
  hidden logs or implicit UI behavior.
* AC-4: findings expose enough detail to explain why the context is considered
  stale or lagging.
* AC-5: tests cover the freshness rules and the no-finding non-regression path.
* AC-6: documentation records the first deterministic staleness policy and its
  limits.

## Dependencies

* ADR-053 — Internal Context Maintenance Capability
* Story 0052 — Define Context Health Signals And Maintenance Findings
* Story 0053 — Expose Context Maintenance Findings In API And Cockpit

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
