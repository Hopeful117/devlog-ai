# Implementation Roadmap Proposal: Engineering Analyst Experiment

## Status

`ROADMAP PROPOSAL - BLOCKED ON HUMAN VALIDATION`

This is a conditional, reversible roadmap. It is not implementation
authorization and does not authorize paid experiments.

## Phase 0 - Freeze the Investigation Record

Deliverables:

- approve the current-state findings and uncertainty labels;
- freeze the eight benchmark questions and human-reviewed oracles;
- verify which Trading OS repository/revision is authorized, if used;
- define retention and redaction policy for artifacts.

Exit: human-approved benchmark specification and no unresolved oracle conflict.

## Phase 1 - Provider-Free Readiness

Reuse existing artifacts and code:

- Story 0184 v2 schemas, Core action endpoint, and bounded ledger;
- V4 typed repository tools and deterministic replay;
- ADR-066 evaluator boundaries and Story 0177/0179/0180 captures.

Add only evaluation adapters or fixtures if required to express A, B, and C
identities. Do not change production prompts, budgets, selection policy, or
historical data.

Exit: all three treatments pass preflight, isolation, schema, revision, replay,
and artifact-integrity checks without provider calls.

## Phase 2 - One Technical Validation Per Treatment

Use one approved case and one repetition per treatment. The purpose is only to
establish that each arm can reach a usable answer and that technical failures
are classified correctly.

Stop immediately on provider-incomplete, tool-contract, timeout, or identity
failure. Do not tune or retry without a new human decision.

Exit: treatment-specific technical validity is documented, or the experiment is
blocked with no semantic conclusion.

## Phase 3 - Small Quality Campaign

Run three repetitions of each of eight cases for A, B, and C, in randomized
isolated order, with the same provider/model where possible. Score only
semantically eligible observations. Blind the human raters and preserve all
technical exclusions.

Exit: paired quality/evidence/uncertainty report and adjudicated decision input.

## Phase 4 - Human Architecture Decision

Choose one bounded outcome:

1. retain A and improve composition/projection/synthesis;
2. prioritize B-like direct investigation;
3. evolve C incrementally using Story 0184;
4. block semantic conclusions and investigate provider/runtime reliability first.

Only this phase can authorize a new Story, ADR amendment, production enablement,
or a paid larger campaign.

## Phase 5 - Conditional Production Evolution

If C wins, the smallest next implementation should be a default-off feature
boundary around the already implemented Story 0184 capability, followed by a
measured rollout. If B wins, preserve the same Core authority and expose the
minimum direct capabilities through Core rather than adding Python access. If
RAG is proposed, require a separate recall experiment and architecture review.

## Non-Goals

- no production code changes in this mission;
- no changes to selection, budgets, prompts, or provider policy;
- no vector/RAG infrastructure decision;
- no generic agent framework;
- no trusted-knowledge promotion;
- no Story acceptance or ADR acceptance by the agent;
- no commit, push, or merge.

## Human Decisions Required

- accept or reject the six investigation documents;
- approve the benchmark cases and oracles;
- authorize any provider-free readiness work;
- separately authorize any live provider validation;
- decide whether Trading OS may be included and under which revision/scope;
- review the eventual A/B/C report before implementation authorization.
