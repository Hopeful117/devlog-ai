# Knowledge model

## Raw occurrences and validated Engineering Events

`KnowledgeEvent` remains the legacy raw/manual occurrence layer. A validated `EngineeringEvent` is
different: it is immutable human-approved interpretation tied to one Project, Analysis, proposal,
Validation, Source, first-parent base commit, target commit, and selected evidence. Unvalidated
proposals are never trusted project memory. Engineering Events do not assert a Decision, Challenge,
developer intent, or causality.

## Knowledge Sources

DevLog AI builds project knowledge from multiple sources of information.

The architecture is designed to be source-agnostic, allowing new sources of knowledge to be integrated without changing the core knowledge model.

A knowledge source represents any origin of information that can contribute to understanding the evolution, context, and decisions of a software project.

### Supported Knowledge Sources

#### Git Repository

The primary source of knowledge for the initial version of DevLog AI.

Git repositories provide technical evidence through:

- commits,
- code changes,
- file history,
- branches,
- tags,
- dependency modifications,
- project structure evolution.

Git activity represents what changed in a project but does not always contain the complete context behind these changes.

#### Human Input

Developers can manually provide additional context when automated analysis cannot reliably determine the intent behind a change.

Human input allows developers to document:

- architectural reasoning,
- technical constraints,
- project goals,
- decisions,
- challenges encountered.

For the first internal DevLog slice, this human context may be persisted inside
DevLog itself as project-owned context inputs rather than only being inferred
from repository artifacts.

These inputs enrich future analysis context but remain distinct from validated
trusted knowledge until they contribute to proposals accepted through the normal
validation lifecycle.

#### Context Maintenance Findings

DevLog may also persist internal context-maintenance findings as project-scoped,
reviewable records about context health.

These findings describe issues such as stale understanding or projection refresh
gaps.

The first deterministic slice is deliberately narrow:

- stale understanding is derived from persisted project freshness checks;
- missing projection refresh currently means the project freshness projection is
  absent for one or more active Sources;
- internal human context maintenance is now able to flag a bounded class of
  stale active human-authored context inputs when they appear older than newer
  active context of the same note type;
- trusted-knowledge duplicate debt is derived from the bounded duplicate audit
  already produced in the insight domain;
- duplicate maintenance findings can represent exact duplicate debt, semantic
  duplicate candidates, or review-oriented overlap such as richer successors;
- these findings remain operational maintenance records rather than trusted
  project truth.

Maintenance findings now also support explicit human-reviewed remediation
actions for:

- the first bounded family of duplicate-debt findings;
- the first bounded family of stale internal human-context findings.

Those actions are:

- acknowledge,
- dismiss with rationale,
- resolve with rationale.

They create audit history for the finding workflow itself.

They do not, by themselves, merge, delete, or rewrite trusted knowledge.

For internal human context inputs, they also do not automatically archive or
rewrite the note itself. Note lifecycle state remains in the dedicated
human-context-input domain.

Maintenance findings also now support one narrow automatic workflow action:

- `AUTO_RESOLVE`

This action is reserved for deterministic findings whose underlying condition
can be safely recomputed and shown to have disappeared. In the current slice it
is limited to:

- `STALE_PROJECT_UNDERSTANDING`
- `MISSING_PROJECTION_REFRESH`
- `STALE_HUMAN_CONTEXT_INPUT`

Every automatic resolution writes explicit action history with a reserved
system actor identity and explanatory comment.

Duplicate-debt findings remain excluded from automatic closure because they
still require semantic or human judgment about whether remediation actually
occurred.

They are:

- operational maintenance records,
- bounded by explicit classification,
- reviewable by humans and future workflows,
- distinct from trusted knowledge,
- distinct from proposal history,
- distinct from internal human context inputs themselves.

Maintenance findings help DevLog track context hygiene without asserting new
project truth.

#### Maintenance Assessments

DevLog may also persist AI-assisted maintenance assessments as advisory
interpretation artifacts attached to maintenance findings.

Assessments represent the Context Maintenance Agent's interpretation of
ambiguous maintenance situations that deterministic rules cannot resolve
cleanly.

Each assessment includes:

- confidence level (HIGH, MEDIUM, LOW, VERY_LOW);
- semantic classification (LIKELY_DUPLICATE, LIKELY_ENRICHMENT, UNCERTAIN,
  CORRELATED_STALENESS, ISOLATED_SIGNAL, NOT_APPLICABLE);
- recommended action (RESOLVE, DISMISS, ESCALATE, MONITOR, NO_ACTION);
- rationale explaining the interpretation;
- optional supporting signals as JSON-serialized data.

Assessments are:

- advisory artifacts, not lifecycle transitions;
- attached to findings but do not modify finding status;
- retrievable through project-scoped and finding-scoped API endpoints;
- reviewable by humans for informed remediation decisions;
- distinct from trusted knowledge, proposal history, and finding lifecycle.

Assessments do not:

- directly modify trusted knowledge;
- automatically resolve or dismiss findings;
- override human decisions on previously reviewed findings;
- expand AUTO_RESOLVE eligibility.

The deterministic layer remains the primary evaluation path. Assessments
activate only when deterministic evaluation surfaces findings that benefit
from AI-assisted interpretation.

### Future Knowledge Sources

The architecture should support additional sources of knowledge in future versions, including:

- Pull Requests,
- Issues and project management tickets,
- Existing documentation,
- Architecture Decision Records (ADR),
- External collaboration tools.

### Knowledge Source Principle

DevLog AI does not depend on a specific development workflow.

The system should provide value even when projects have lightweight documentation practices, while becoming more powerful as additional sources of knowledge become available.

## Knowledge Storage Model

DevLog AI preserves both raw project information and structured knowledge generated from this information.

The objective is not only to produce a summary of project evolution, but to maintain a reliable and traceable technical memory.

### Raw Knowledge

Raw Knowledge represents the original information collected from project sources.

Examples include:

- Git commits,
- code changes,
- file modifications,
- dependency changes,
- repository metadata,
- developer inputs.

Raw Knowledge acts as the historical evidence supporting future analysis.

### Structured Knowledge

Structured Knowledge represents the interpretation and understanding built from raw information.

Examples include:

- Engineering Events,
- Engineering Challenges,
- Engineering Decisions,
- project evolution history,
- generated documentation context.

Structured Knowledge provides a human-readable representation of the project's evolution.

### Knowledge Traceability

Every piece of structured knowledge should remain connected to its original sources.

This allows DevLog AI to:

- explain why a knowledge item exists,
- re-analyze previous project history,
- improve future analysis capabilities,
- maintain confidence in generated documentation.

### Storage Principle

Raw information and interpreted knowledge serve different purposes and should coexist.

Raw Knowledge preserves evidence.

Structured Knowledge preserves understanding.

Together, they form the technical memory of a project.
