# DevLog AI A/B/C Comparability Matrix

## Status

`NOT_READY_FOR_DIRECT A/B/C COLLECTION`

The matrix documents the real capability difference. It does not claim that B
and C are currently equivalent.

| Dimension | A_DEVLOG_CURRENT | B_DIRECT_INVESTIGATOR | C_DEVLOG_ENGINEERING_ANALYST |
|---|---|---|---|
| Runtime | Core Story Context one-shot | V4 evaluation runtime | Story 0184 v2 opt-in Core/Python path |
| Initial input | Core-prepared projection | Question and pinned-repository tool schema | Core projection plus investigation metadata |
| Provider output contract | `StoryContextAnalysisResult` / Story Context v1 | V4 common answer contract | Story Context result after v2 investigation |
| Repository read/search | Indirect, only selected/projected evidence | `read_file`, `search_repository` | `SEARCH_EVIDENCE`, `READ_EVIDENCE_RANGE` |
| Git history/diff | Selected Core evidence | `git_log`, `git_show`, `git_diff`, `inspect_commit` | Not exposed as Story 0184 actions |
| Plan/action loop | One-shot by default | Multi-turn provider/tool loop | Python PLAN/ACTION/FINAL loop |
| Revision pinning | Core context/projection | V4 tool arguments include pinned revision | Core action scope and result revision checks |
| Grounding | Core allow-list and callback validation | V4 deterministic evidence/evaluator | Core grounding contract extension and final validation |
| Ledger | Core loss accounting | V4 tool/evidence/resource traces | Core bounded action ledger and usage snapshot |
| Default status | Production/default | Evaluation-only | Explicitly initialized/opt-in |
| Technical comparability | Baseline | Candidate direct arm | **Blocked: narrower capabilities than B** |
| Answer comparability | **Ready through evaluation-only normalization** | Common V4 answer contract | **Ready through the same evaluation-only normalization** |

## Fair Comparison Options

### Option 1: Common capability subset

Compare B and C using only repository search and bounded file reads. Exclude Git
history questions or treat them as `NOT_APPLICABLE` for the first common-subset
campaign. This is the smallest change and preserves production behavior.

### Option 2: Explicit asymmetric treatments

Keep B's six tools and C's two actions, label the treatments as different
capability envelopes, and report capability-adjusted results. This answers a
product question about the currently available systems but cannot attribute a
quality difference solely to DevLog context.

### Option 3: Evaluation-only adapter parity

Add an evaluation-only adapter that maps the common V4 read/search/history
operations to a Core-authorized test double for C. This must be versioned,
provider-free tested, and must not add Git access to production Story 0184.

## Recommendation

Use Option 1 for the first real test and reserve Q3/Q8 history-heavy cases for
a later parity campaign. The evaluation-only normalization adapter now extracts
claims, evidence references, uncertainty and answer status from both the Story
Context result and the V4 common answer without altering either production
contract. Q1 remains a suitable first case once capability parity and oracle
approval are available.

## Readiness

The three treatments are not yet directly comparable as full Q1-Q8 systems.
The common-subset manifest and output normalization are prepared without
provider calls, but collection remains blocked by capability parity and human
approval.

The Q1-specific operational comparison is recorded in
`docs/evaluation/devlog-ai-q1-capability-equivalence-report-v1.md`.
