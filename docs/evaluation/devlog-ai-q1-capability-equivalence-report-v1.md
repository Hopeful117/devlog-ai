# Q1 B/C Operational Capability Report

## Status

`Q1_CAPABILITIES_BLOCKED`

The common search/read names do not provide equivalent operational behavior on
the frozen Q1 revision. The difference is documented rather than hidden behind
a test double.

## Frozen Scope

- Repository: `git:github.com/Hopeful117/devlog-ai`
- Revision: `60fae3e0e1104d0ffbe3832c1f578ed532b1268a`
- B: `PinnedGitRepositoryTools` evaluation runtime
- C: Story 0184 `InvestigationCapabilityExecutionService` through Core
- No provider calls or network operations

## Dimensions

| Dimension | B | C | Q1 impact |
|---|---|---|---|
| Repository scope | Pinned Git object; safe relative path validation | Authorized project/story scope plus allowed path prefixes | Common root scope can be configured, but C remains Core-scoped |
| Revision | Every tool argument is checked against the frozen revision | Action revision must equal scope revision; returned evidence revision is rechecked | Equivalent identity checks for valid requests |
| Search implementation | Direct `git grep -n -I -F` over the pinned revision | `RepositoryContextAdapter` builds a context using collectors, term ranking and bounded selection | **Blocking:** C may omit an exact repository match that B returns |
| Search path filter | Optional Git path arguments | Optional `pathPrefix`, then Core authorized-path filtering | Similar restriction, different selection semantics |
| Search results | Match path, line and matching text | Canonical evidence reference, kind, provenance, summary, trust tier; content is not guaranteed for search | Different evidence surface |
| Result limit | V4 schema default 20; direct adapter truncates matches | Core capability maximum 20 and action result limit | Comparable upper bound, not identical ordering/content |
| Read source | Direct file content from `revision:path` | Reads content only from Core-returned `RepositoryEvidence` for the exact path | Equivalent only after evidence is available |
| Read range | Arbitrary valid line range in B adapter | Maximum 500 lines; exact path required | Common Q1 range can fit within C |
| Bytes/tokens | V4 tool itself has no equivalent C 64k/8k projection in its direct operation | Maximum 64,000 bytes and 8,000 estimated tokens, with truncation | First pilot must use C-compatible read budgets |
| Provenance | Tool reference/path and pinned revision | Reference, revision, source type, identifier, trust tier, citable flag | C is richer; normalization preserves both |
| Errors | Runtime contract error for invalid path/revision/read; empty search on no match | Typed rejection codes including revision/path/limit/unsupported-result failures | Both fail closed, but error taxonomy differs |
| Stop conditions | Provider/runtime V4 loop controls tool lifecycle | Core budgets steps, retrieval calls, evidence, bytes, tokens and elapsed time | Must be compared as separate technical metrics |

## Deterministic Evidence

The provider-free tests execute B search/read against the actual frozen Git
object and project the same file content through a C-shaped bounded read result.
They cover:

- Java class search;
- Python contract search;
- ADR reference search;
- bounded line read;
- empty search;
- inaccessible/missing paths;
- range and byte/token limits;
- wrong revisions;
- C unauthorized returned evidence.

The read projection matches for an already authorized file/range. This does
not prove search equivalence because C's production search path is context
retrieval and ranking rather than direct content search.

## Minimal Correction Options

1. **Recommended for this scope:** keep Q1 blocked for a causal A/B/C claim and
   run only an explicitly asymmetric technical comparison. This does not answer
   whether DevLog context caused a quality difference.
2. Add an evaluation-only Core-authorized search/read adapter for C that exposes
   the same pinned content-search semantics. This requires a separate design and
   must not be represented as current Story 0184 behavior.
3. Add Git/search capabilities to production C. Not in scope and not authorized
   by this mission.
