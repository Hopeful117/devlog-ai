# Comparative Baseline V4 Repository Tool Contract Remediation

## Scope

This offline-only remediation replaces the provider-visible generic repository
tool schema with six separate typed function tools. Benchmark questions, oracle,
prompts, model/provider configuration, evaluators, experiment dimensions,
RAW/DERIVED artifacts, and observations were not changed.

## Authoritative Matrix

The matrix is derived from `PinnedGitRepositoryTools.execute` in
`ai-engine/evaluations/comparative_baseline/live_adapters.py`:

| Tool | Required | Optional |
|---|---|---|
| `read_file` | `path: string` | `startLine: integer`, `endLine: integer` |
| `search_repository` | `query: string` | `paths: string[]`, `maxMatches: integer` |
| `git_log` | none | `commit: string` |
| `git_show` | none | `commit: string`, `path: string` |
| `git_diff` | none | `commit: string`, `parent: string` |
| `inspect_commit` | none | `commit: string` |

Separate tools are the minimum sufficient design. A generic tool would require
an open arguments object and could not truthfully express operation-specific
fields to the provider. Strict schemas represent optional fields as nullable;
runtime validation remains authoritative for exact envelope, field, and type
checks.

## Contract Behavior

Provider function names are normalized to the canonical V4 envelope:
`{ "operation": "...", "arguments": { ... } }`. No malformed top-level
`query`/`path`, missing operation, non-string operation, unknown operation,
missing field, wrong type, or unexpected field is repaired or executed.

Policy A exposes only the deterministic generic contract diagnostic in the tool
conversation and permits natural model recovery. Invalid requests are traced
and counted as invalid/ceiling-counted operations; valid requests retain the
existing execution, result-byte, conversation, and provider accounting rules.

## Identities

Only bound identities changed:

| Identity | Value |
|---|---|
| Runtime contract | `comparative-v4-live-runtime-contract-3.0.0`, `9d80f6677c37fadbc743b1bc523d781c771872b655b38affde5ac540c228dbb0` |
| Execution configuration | `f40a1dfd969f148ee8654f48189a2b78882ff89f9f8d517dccf0f9385e9add73` |
| Experiment | `3c9b60e071cab1342b78ab8bf3d448d6e6d3b673a9c3b317c3b96070fdf4a1fe` |
| Official plan | `e1282e398bb17c111aa517a3d15ac4cf91a85de5bb248b4b9d1d2aa1ad1b7809` |
| Pilot plan | `2e2313c50a06fbc07ca3695abe89d960298e5a26bd814c28b8d21a838203dfe8` |

The previous corrected runtime identity was not reused. Assignment count,
conditions, repetitions, and all experiment dimensions remain unchanged.

## Verification

Focused offline tests cover every operation’s valid canonical shape, required
fields, applicable wrong types, unexpected fields, missing/non-string/unknown
envelopes, historical malformed top-level fixtures, no execution, accounting,
normalization, conversation propagation, Policy A recovery, and safety limits.
No provider call, network call, live observation, RAW artifact, or DERIVED
artifact was created.
