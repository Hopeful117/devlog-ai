# Comparative Baseline V4 Schema/Runtime Parity Remediation

## Scope and Method

This offline remediation audits the typed V4 provider schema against
`_contract_error` and the pinned repository adapter. It changes only argument
constraints that are deterministic and already required by repository-tool
semantics. It does not broaden the six-tool capability, alter Policy A, repair
model requests, change repository-state validation, execute a provider, make a
network call, create an observation, or modify a historical artifact.

## Git Intent

V4 is pinned to one immutable repository revision. The live adapter validates
the pinned revision with `rev-parse --verify <revision>^{commit}`, and every
Git selector (`commit` and `parent`) is accepted only when it matches the
lowercase 40-hex `_HEX_REVISION` expression. The in-memory fixture adapter
also requires the injected repository revision to equal its frozen revision.
The evidence therefore supports the explicit contract category:

```text
FULL_OBJECT_ID_ONLY
```

This is a representation constraint, not an existence guarantee. Object
existence, commit type, repository availability, revision equality, and Git
command output remain runtime/state-dependent checks.

## Complete Field Matrix

Provider-visible optional fields remain nullable because the current OpenAI
strict-schema construction requires every property to be present in the
`required` list. Omitted and null optional values retain existing defaults.

| Tool | Field | Required by envelope | Type | Provider-visible constraint | Runtime parity check | State-dependent check |
|---|---|---:|---|---|---|---|
| `read_file` | `path` | yes | string or null | non-empty; relative; no NUL; no `..` | same path format | path exists at frozen revision |
| `read_file` | `startLine` | no | integer or null | minimum `1` | minimum `1` | none |
| `read_file` | `endLine` | no | integer or null | minimum `1` | minimum `1` | `endLine >= startLine` and content lookup |
| `search_repository` | `query` | yes | string or null | `minLength: 1` | non-empty | Git grep/output validity |
| `search_repository` | `paths` | no | array or null | each item is a non-empty safe relative path | same item checks | selected paths and Git output |
| `search_repository` | `maxMatches` | no | integer or null | minimum `1` | minimum `1` | none |
| `git_log` | `commit` | no | string or null | lowercase 40-hex SHA | `FULL_OBJECT_ID_ONLY` | object exists and is usable by Git |
| `git_show` | `commit` | no | string or null | lowercase 40-hex SHA | `FULL_OBJECT_ID_ONLY` | object/path exists |
| `git_show` | `path` | no | string or null | non-empty; relative; no NUL; no `..` | same path format | path exists in commit |
| `git_diff` | `commit` | no | string or null | lowercase 40-hex SHA | `FULL_OBJECT_ID_ONLY` | object exists |
| `git_diff` | `parent` | no | string or null | lowercase 40-hex SHA | `FULL_OBJECT_ID_ONLY` | parent/object relationship and Git diff |
| `inspect_commit` | `commit` | no | string or null | lowercase 40-hex SHA | `FULL_OBJECT_ID_ONLY` | object exists and metadata command succeeds |

The hidden `repositoryRevision` is injected by runtime and is intentionally
not a provider argument. Its equality with the frozen revision remains a
runtime-only repository-state guard.

## Defect Categories

| Category | Before | Remediation |
|---|---|---|
| Provider/runtime mismatch | `startLine=0` and `endLine=0` could be provider-valid but fail the live adapter | add `minimum: 1` and matching validator checks |
| Provider/runtime mismatch | empty query, unsafe paths, and zero match limits were not represented in the typed schema | add `minLength`, path pattern, and `minimum: 1` constraints |
| Git revision mismatch | abbreviated, uppercase, or non-SHA Git selectors were provider-valid but rejected by runtime | add the full lowercase 40-hex pattern to every Git revision field |
| Dynamic/state semantics | line ordering, object existence, repository identity, and Git command output cannot be decided statically | preserve runtime-only validation |

## Runtime Failure Semantics Audit

`_contract_error` runs after provider normalization and before tool execution.
Invalid arguments produce `NOT_EXECUTED_INVALID_REQUEST`, increment invalid
request/tool accounting, consume Policy A operation ceiling accounting, and
append only the deterministic contract error to the conversation. No silent
repair or fallback acceptance is introduced. Valid static inputs may still
produce `EXECUTED_RUNTIME_FAILURE`, `EXECUTED_TOOL_FAILURE`, or tool timeout
when repository state or command execution fails. Provider parsing failures
remain provider failures and are not converted into Policy A failures.

The existing emergency runaway guard, natural model recovery, propagation,
accounting, historical replay, and state-dependent failure behavior are
unchanged.

## Options Considered

1. Keep the schema broad and rely on runtime checks. Rejected because a
   provider-valid request could fail before execution, which is the demonstrated
   parity defect.
2. Silently clamp or repair values. Rejected by Policy A and the no-repair
   requirement.
3. Add dynamic cross-field or repository-existence constraints to the schema.
   Rejected because they are not statically expressible and would duplicate or
   weaken runtime authority.
4. Accept abbreviated Git references. Rejected because the frozen adapter and
   immutable-revision intent require full object IDs.

## Limitations

- JSON Schema cannot express `endLine >= startLine` with the current simple
  field schema; the runtime retains that check.
- JSON Schema cannot establish that a SHA exists in the frozen repository or
  that it names a commit usable by a specific Git command.
- The schema uses nullable properties for compatibility with the existing
  OpenAI strict-schema dialect; required envelope fields still reject null in
  `_contract_error`.
- No live execution or provider response was used to validate this change.

## Identity Changes

Only identities bound to schema/runtime behavior were regenerated.

| Identity | Old | New |
|---|---|---|
| Typed repository schema component | `comparative-v4-typed-repository-tools-1.0.0` | `comparative-v4-typed-repository-tools-2.0.0` |
| Runtime contract version | `comparative-v4-live-runtime-contract-3.0.0` | `comparative-v4-live-runtime-contract-4.0.0` |
| Runtime contract digest | `9d80f6677c37fadbc743b1bc523d781c771872b655b38affde5ac540c228dbb0` | `3d4985ae3d5db051a6924eef0f44eac90ce66db05440bc110e69dce4cedcb7e1` |
| Execution configuration hash | `f40a1dfd969f148ee8654f48189a2b78882ff89f9f8d517dccf0f9385e9add73` | `479031b0814373e2d5fc693bf8bfd81fc7c42af96af6e80d471a6ee646145bd4` |
| Experiment identity | `3c9b60e071cab1342b78ab8bf3d448d6e6d3b673a9c3b317c3b96070fdf4a1fe` | `8a18746c1d387cb9375d186a2b8ab8da38c190eb854a918dddbe20dcf38d5910` |
| Pilot plan identity | `2e2313c50a06fbc07ca3695abe89d960298e5a26bd814c28b8d21a838203dfe8` | `e8580a3bc5ec5d38c840d7fee414d1ebc17789358b97b3b59e6e9b0f34100528` |
| Official plan identity | `e1282e398bb17c111aa517a3d15ac4cf91a85de5bb248b4b9d1d2aa1ad1b7809` | `1ca5deba40f5ca40997dcc1d9e8308a8a9d980e471bb5134a3099706389b9490` |

The V4 manifest remains `comparative-baseline-v4-2.0.0`; benchmark, question,
oracle, repository, condition, capability, and safety identities are unchanged.

## Mandatory Conclusions

- `read_file` line bounds now have provider-visible and runtime parity at
  minimum `1`.
- Actual path, query, and positive `maxMatches` semantics are represented in
  the provider schema and `_contract_error` without broadening capability.
- Git revision fields are `FULL_OBJECT_ID_ONLY` because the frozen adapter
  demonstrably requires lowercase 40-character object IDs.
- Repository-state validation remains runtime-only.
- Policy A, no silent repair, natural recovery, accounting, propagation,
  runaway guards, historical fixtures, and experimental capability are
  preserved.
- No provider/network call, live execution, observation creation, or historical
  artifact modification occurred.

## Required Classifications

- A. `read_file startLine >= 1` is statically knowable: `YES`.
- A. Represented in the corrected provider schema: `YES`, via `minimum: 1`;
  `_contract_error` enforces the same constraint.
- B. Authoritative Git revision contract: `FULL_OBJECT_ID_ONLY`.
- C. Git object existence provider-side: `NO — RUNTIME_STATE`.
- D. Runtime-resolution failures: `PARTIALLY`. Repository resolution and tool
  execution remain runtime behavior; the previous static SHA/range mismatch was
  corrected. Changing runtime failure termination semantics is not authorized by
  this remediation.
- E. Schema/runtime parity: `PASS` for statically expressible constraints;
  repository state remains runtime-only.
- F. Historical parity fixtures: `YES`; all known malformed/static cases are
  rejected consistently without execution or repair.
- G. Frozen dimensions preserved: `YES`.
- H. Historical artifacts preserved: `YES`.

## Verification and Readiness

- Focused V4/live adapter tests: `PASS`.
- Full relevant comparative test selection (`tests/test_comparative*.py`):
  `PASS`.
- Provider calls: `0`.
- Network calls: `0`.
- New observations: `0`.
- No validation-plan artifact was regenerated; the affected official and pilot
  identities were regenerated because they bind the changed runtime/tool
  contract. The prior validation artifacts and their identities remain
  historical and untouched.

`READY_FOR_HUMAN_AUTHORIZATION_TARGETED_DIRECT_VALIDATION`
