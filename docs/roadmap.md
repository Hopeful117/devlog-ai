# Roadmap

## V1 Development Roadmap

The first version of DevLog AI focuses on proving the value of maintaining a living technical memory of software projects.

The objective is not to build every possible feature, but to validate the complete knowledge lifecycle.

## Phase 0 — Technical Foundation

Objective:

Establish the technical foundation of the platform.

Scope:

- initialize project repositories,
- implement service architecture,
- configure Docker environment,
- establish CI/CD foundations,
- prepare communication between services.

## Phase 1 — Repository Memory

Objective:

Allow DevLog AI to understand and preserve repository evolution.

Scope:

- connect Git repositories,
- collect repository activity (first commit-history import slice implemented),
- store Raw Activity (commit metadata, parents, changed files and diff statistics implemented),
- build bounded deterministic commit-diff contexts (implemented),
- assemble layered, traceable and budgeted Repository Context during knowledge selection
  (ADR-037 implemented),
- execute context construction through modular collectors, context profiles, deterministic
  ranking, diversity-aware selection and a token budget (ADR-038 implemented),
- compose versioned Context Profiles and calculate explainable multi-criteria Evidence scores
  through deterministic Context Intelligence (ADR-039 implemented),
- enrich selected source/test evidence with bounded revision-traceable content while preserving
  token budgets and configuration exclusion (ADR-044 implemented),
- expose bounded deterministic Java declaration symbols for selected source/test evidence before
  content allocation, without semantic or dependency inference (ADR-045 implemented),
- initialize or refresh repository understanding explicitly on demand (implemented through the
  single-source `describe-project-v1` workflow).
- check default-revision freshness explicitly and provide deterministic manual refresh guidance
  without passive monitoring or automatic analysis (implemented).

Current boundary:

- AI commit interpretation, validated historical events, multi-parent comparison, non-Java symbol
  extraction, symbol resolution and dependency relationships remain deferred.

Output:

DevLog AI can understand the history and current state of a project.

## Phase 2 — Intelligent Analysis

Objective:

Transform technical activity into knowledge proposals.

Scope:

- deterministic analysis,
- signal detection,
- AI analysis service,
- Engineering Event proposals,
- human validation workflow.

Output:

DevLog AI can identify meaningful project evolutions.

## Phase 3 — Knowledge Model

Objective:

Build the project's technical memory.

Scope:

- Engineering Events,
- Challenges,
- Engineering Decisions,
- Project Snapshot,
- knowledge relationships.

Output:

DevLog AI maintains an evolving understanding of the project.

## Phase 4 — Documentation Outputs

Objective:

Transform knowledge into useful documentation.

Scope:

- Markdown generation,
- technical article generation,
- ADR generation,
- architecture documentation.

Output:

DevLog AI can generate documentation from validated project knowledge.

## Future Evolution

Potential future capabilities include:

- project assistant,
- technical debt analysis,
- advanced RAG capabilities,
- collaboration tools integration,
- autonomous documentation workflows.

The V1 priority remains building a trustworthy project memory system.
