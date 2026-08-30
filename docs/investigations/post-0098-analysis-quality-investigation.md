# Post-Story 0098 Analysis Quality Investigation

## Status

Investigation only. No production changes.

## Baseline

- Baseline SHA: `a4aa44d`
- Story 0098 commit: `a1d5d92`
- Story 0098 merge: `a4aa44d`
- Worktree status during investigation: only `?? data/`

## Objective

Determine the next primary bottleneck affecting the usefulness of canonical Analysis results after Story 0098 resolved COMMIT_DIFF category dominance.

## Analyses Executed

Executed on the merged Story 0098 product path:

- `b30b2961-95bf-474c-aa7d-7f7889f3e750` — Understand this project — `describe-project-v1`
- `ba1fd8c0-057d-43ca-850f-02d720cff2f0` — Review the architecture — `architecture-overview-v1`
- `be5a62e0-c455-4619-b33e-d5f79f45a7ad` — Analyze engineering decisions — `analyze-engineering-decision-v1`

All analyses were preserved as diagnostic fixtures.

## Story 0098 Verification

Story 0098 remains effective on the real Analysis path.

- `COMMIT_DIFF` selected repository evidence is now `12 / 60 = 20%`
- The previous `73–75%` category dominance did not recur

Repository evidence distributions:

### Understand this project

- `COMMIT_DIFF=12`
- `GIT_HISTORY=12`
- `PREVIOUS_ANALYSIS=11`
- `VALIDATED_INSIGHT=12`
- `ROADMAP=11`
- `CURRENT_ANALYSIS=1`
- `ADR=1`

### Review the architecture

- `COMMIT_DIFF=12`
- `GIT_HISTORY=12`
- `VALIDATED_INSIGHT=12`
- `PREVIOUS_ANALYSIS=12`
- `ROADMAP=10`
- `ADR=1`
- `RELATED_SOURCE_CODE=1`

### Analyze engineering decisions

- `COMMIT_DIFF=12`
- `GIT_HISTORY=12`
- `VALIDATED_INSIGHT=12`
- `ROADMAP=14`
- `PREVIOUS_ANALYSIS=8`
- `CURRENT_ANALYSIS=1`
- `ADR=1`

## Qualitative Findings

### Understand this project

Useful for basic orientation only.

- Correctly identified project purpose, Spring Boot, Maven, Docker, tests, ADR usage
- Did not explain important modules/services in depth
- Did not connect history, roadmap, previous analyses, or human context into the answer
- Output mostly enumerates facts instead of synthesizing relationships

### Review the architecture

User-facing result is weak.

- Selected evidence was rich: facts, observations, architecture knowledge, ADR, previous analyses, source-related evidence
- Canonical `/result` returned `0` proposals
- This means the useful context existed but did not become a useful architectural result

### Analyze engineering decisions

Weak.

- Returned plausible decision prose
- Decisions were generic continuations like “keep using X”
- No meaningful historical or causal chain was reconstructed
- Grounding arrays were empty in the proposals

## Retrieval vs Interpretation Classification

### Evidence selected but ignored

Classification: `D. EVIDENCE_SELECTED_BUT_IGNORED`

- `GIT_HISTORY` selected but rarely used in outputs
- `PREVIOUS_ANALYSIS` selected but ignored
- `ROADMAP` selected but ignored
- `HUMAN_CONTEXT` selected but ignored
- `architectureKnowledge` selected for architecture review but still yielded zero proposals

### Poor cross-evidence synthesis

Classification: `E. POOR_CROSS_EVIDENCE_SYNTHESIS`

- Outputs rarely connect:
  - decision -> implementation -> later evolution -> current architecture
  - roadmap objective -> commits/diffs -> resulting capability -> remaining work

### Evidence selected but weak

Classification: `C. EVIDENCE_SELECTED_BUT_WEAK`

- `COMMIT_DIFF` summaries are still terse
- `PREVIOUS_ANALYSIS` repository evidence is often only status-level
- roadmap items are often too thin to drive synthesis by themselves

### Analysis output structure issues

Classification: `F. ANALYSIS_OUTPUT_STRUCTURE`

- architecture review produced zero proposals despite rich selected context
- engineering decision outputs carried empty grounding arrays

### Result projection issues

Classification: `G. CANONICAL_RESULT_PROJECTION`

- canonical `/result` shows only a 5-item repository preview
- preview remains COMMIT_DIFF-heavy even when the full selected set is balanced

## Evidence Quality Assessment

### COMMIT_DIFF enrichment

- Better distributed after Story 0098
- Still semantically weak in many cases
- Often dominated by file-change summaries of stories, tests, or UI work
- `content` and `symbols` were usually `null`

### GIT_HISTORY

- More useful than raw diff summaries
- Still shallow: mostly commit subject, counts, and parent refs
- Limited causal explanation

### Validated insights

- Highest semantic value category in practice
- But also somewhat duplicative with generated insight outputs

### Previous Analysis

- Present in quantity after Story 0098
- Often too weakly summarized to help the model infer relationships

### Roadmap evidence

- Present in quantity after Story 0098
- Often terse and disconnected from implementation evidence in the final answer

### ADR evidence

- Strong signal when selected
- Usually collapsed to “ADRs exist” instead of actual decision chains

## Temporal and History Assessment

Temporal metadata survives, but chronology is not operationally used.

- timestamps are present
- ordering is mostly by relevance, not chronology
- outputs do not reconstruct evolution or causality
- selected history evidence does not translate into historical understanding

This is a real limitation, but the strongest observed loss happens one layer earlier in context composition.

## Context Composition Assessment

This is the strongest current bottleneck.

Observed path:

- backend selection produces a rich, structured `selectedKnowledge`
- prompt projection preserves some categories and local links
- AI prompt builder flattens that into one large JSON block inside the user prompt

What survives:

- type/category metadata mostly survives
- timestamps survive
- local links like `supportingFactIds` and `relatedReferences` survive

What degrades or disappears:

- broader `knowledgeRelations` do not survive into the projected selected knowledge
- repository evidence provenance/ranking context is stripped
- chronology is not foregrounded
- salience is not grouped by theme or relationship
- important evidence competes in one undifferentiated blob

Practical effect:

- easy high-level facts dominate attention
- richer history/roadmap/previous-analysis evidence is selected but underused
- architecture and decision objectives underperform despite improved retrieval diversity

## Canonical Result Projection Assessment

Separate from generation quality.

- `/selected-evidence` exposes the full selected snapshot
- `/result` exposes only a limited repository preview
- this hides some Story 0098 benefit from the human-facing page

This is a real projection weakness, but it does not fully explain zero architecture proposals or generic decision synthesis.

## Primary Bottleneck

`CONTEXT_COMPOSITION`

Reason:

- Story 0098 fixed category selection
- useful evidence now reaches selection in balanced proportions
- the next major loss happens when rich selected knowledge is flattened into prompt context with weak relationship projection and weak attention guidance

## Secondary Bottlenecks

- `RESULT_PROJECTION`
- `EVIDENCE_ENRICHMENT`
- `ANALYSIS_OUTPUT_STRUCTURE`

## Comparison with Story 0098

Story 0098 materially improved more than raw distribution, but only partially improved human usefulness.

Concrete effect:

- Before: changed-file evidence dominated repository context
- After: previous analyses, roadmap, ADR, validated insight, and history evidence survive selection in meaningful volume

Remaining reality:

- project-understanding output is still mostly enumerative
- architecture review can still return no useful proposals
- engineering-decision output remains generic and weakly grounded

Conclusion:

Story 0098 improved the evidence foundation and modestly improved usefulness, but it did not solve the next product bottleneck.

## Recommended Next Action

`B — DESIGN_REQUIRED`

Reason:

- the bottleneck is evidenced and real
- but it spans selected-knowledge projection, prompt structure, relationship survival, and objective-specific behavior
- this should be designed before materializing a new Story
