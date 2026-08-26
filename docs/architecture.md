# Architecture


## Core Principles

### Knowledge First

DevLog AI is built around the idea that project knowledge is the primary asset.

Documentation is considered a representation of this knowledge, not the knowledge itself. The system focuses on capturing, structuring, and preserving the information required to understand how and why a project evolves.

### AI as a Capability

Artificial intelligence is considered a system capability, not the foundation of the business logic.

The core domain remains independent of AI providers and models. LLMs, embeddings, and other AI technologies are used to enrich and process project knowledge while keeping the architecture flexible.

### Human in the Loop

Automated analysis is used to discover meaningful project evolutions, but human input remains essential to capture context, intentions, and architectural reasoning that cannot be reliably inferred from source code alone.

### Evolution Over State

DevLog AI focuses on understanding how a project changes over time rather than only describing its current state.

The system preserves meaningful transitions, decisions, and milestones that explain the journey from one project state to another.

## Knowledge Model

DevLog AI considers project knowledge as the combination of the technical state of a project and the historical context explaining how and why it evolved.

A software project is not only defined by its current implementation, but also by the challenges encountered, the decisions made, and the changes introduced throughout its lifecycle.

The knowledge model is organized around the following concepts:

### Project Identity

Information describing the purpose, scope, and objectives of a project.

This includes the project's goals, domain, and overall context.

### Technical Context

Information describing the current technical ecosystem of a project.

This includes technologies, architecture, dependencies, infrastructure, and relationships between components.

### Engineering Events

The first implemented vertical slice uses a dedicated immutable `EngineeringEvent` aggregate.
Legacy `KnowledgeEvent` records remain raw/manual occurrences and are neither migrated nor treated
as validated memory. Core owns the exact Source and first-parent revision boundary, output contract,
validation transaction, and promotion; the AI Engine only interprets the selected snapshot.

Meaningful evolutions of a project representing changes, milestones, or important transitions.

Engineering Events are not limited to individual commits. They represent knowledge extracted from project evolution and can be supported by multiple sources such as repository activity, documentation, or human input.

### Engineering Challenges

Problems, constraints, or difficulties encountered during development that influenced the evolution of the project.

Challenges provide the context explaining why changes and decisions happened.

### Engineering Decisions

The reasoning behind important technical choices, including alternatives considered and expected consequences.

Decisions preserve the intent behind the implementation and help future developers understand why a specific approach was selected.

### Documentation Outputs

Different representations generated from project knowledge.

These outputs include technical articles, README improvements, Architecture Decision Records (ADR), release notes, and other project documentation.

Documentation is considered a consumer of project knowledge rather than the primary source of knowledge.

The objective of DevLog AI is to transform development activity into structured knowledge that remains understandable and valuable over time.



## Project Snapshot

DevLog AI maintains a current representation of a project's state in addition to preserving its historical evolution.

The Project Snapshot provides a concise understanding of where a project currently stands and helps developers quickly recover context when returning to a project after a period of inactivity.

### Snapshot Content

A Project Snapshot may include:

- project purpose and objectives,
- current architecture,
- technologies and dependencies,
- active capabilities,
- recent important evolutions,
- known challenges,
- important decisions,
- current development context.

### Snapshot Generation

The Project Snapshot is generated from validated project knowledge.

It does not replace historical information. Instead, it provides a current view built from the accumulated understanding of the project.

### Snapshot Principle

The history explains how a project evolved.

The snapshot explains where the project currently is.

Together, they provide both temporal understanding and operational context.



## Agent Operating Model

DevLog AI follows a hybrid passive/active operating model.

The system continuously observes project activity while selectively activating deeper analysis when meaningful signals are detected.

### Passive Observation

The passive layer continuously collects project activity without immediately generating interpretations.

Its responsibilities include:

- monitoring supported knowledge sources,
- collecting raw project information,
- tracking repository evolution,
- maintaining historical data.

Passive observation ensures that no important information is lost while avoiding unnecessary processing.

### Active Analysis

The active layer performs deeper analysis when project activity indicates potential meaningful evolution.

Examples of analysis triggers:

- architectural changes,
- significant dependency modifications,
- new capabilities,
- repeated modifications around the same area,
- potential engineering decisions.

### Analysis Principle

DevLog AI should be always aware of project evolution without being continuously intrusive.

The system observes silently and acts when additional understanding provides value.

## Project Bootstrap Analysis

Repository connection does not trigger autonomous analysis. From the Project Cockpit, the user may
explicitly start an initial understanding phase and request the same capability again after
meaningful changes.

Each execution targets one selected active Git Source and an optional branch, tag, or commit. Core
resolves `describe-project-v1`, imports history, snapshots Source/revision provenance, and reuses
equivalent work already pending or running. Completed and failed executions never disable later
refresh. Passive monitoring remains a separate future capability.

### Explicit Project Freshness

Project freshness is an operational, Source-scoped projection rather than Trusted Knowledge. An
explicit command resolves the Source's current default Git object ID through the confined workspace
manager and compares it with the immutable revision in the latest comparable completed Project
Understanding. Equality is the only path to `CURRENT`; missing or invalid provenance remains
`UNKNOWN`, and absence of a baseline remains `NO_BASELINE`.

Core retains one bounded latest successful check per Source with its `checkedAt` time. The cockpit
and Engineering Story Context may display that as-of result without contacting Git. Git failures do
not overwrite it or invalidate Analyses, proposals, Insights, or Deliverables. Checks never launch
AI, refresh understanding, or authorize proposal decisions. Scheduled checks, webhooks,
significance classification, and autonomous refresh remain part of the future passive-monitoring
boundary defined by ADR-041 and ADR-043.

The objective is not to reproduce the complete Git history, but to reconstruct the major evolution milestones that explain the current state of the project.

### Bootstrap Objectives

The initial analysis should identify:

- project purpose,
- technology stack,
- current architecture,
- important historical changes,
- major migrations,
- significant engineering decisions,
- recent evolution.

### Progressive Historical Analysis

DevLog AI should prioritize meaningful project milestones over exhaustive commit analysis.

Historical analysis should focus on changes that had a significant impact on:

- architecture,
- technology choices,
- project capabilities,
- development practices.

### Bootstrap Principle

The objective of repository analysis is to understand the story of a project, not to replicate its commit history.

## V1 Technical Architecture

DevLog AI follows a microservice-oriented architecture based on clear responsibility boundaries.

The objective is not to maximize the number of services, but to isolate domains with different responsibilities and evolution cycles.

### Automatic Repository Observation (ADR-062)

A scheduled, read-only detector (`ScheduledRepositoryChangeDetector`) observes the current
immutable HEAD revision of every active Git source via `git ls-remote` and records the
observation through the freshness checkpoints (`project_source_freshness`). This makes
freshness become STALE autonomously when a repository changes — without manual freshness
checks.

Automatic observation DOES NOT mean automatic synchronization: DevLog knowledge intentionally
remains at its baseline revision (`contextRevision = X`, `repositoryRevision = Y`,
`freshness = STALE`) until a synchronization/understanding action explicitly advances it.
STALE is an observation, never a command. Detection can be disabled with
`devlog.repository-observation.enabled=false`; interval is configurable through
`devlog.repository-observation.interval`. The probe performs no clone, fetch, checkout or
reset and shares no state with workspace operations.

### Engineering Story Agent Projection

The Repository Context Engine retains a rich deterministic domain representation for AI-task
provenance, diagnostics and audit. External Engineering Story preparation consumes a distinct,
versioned Agent-Ready projection built by the Java Core after collection, ranking, selection,
symbol enrichment, content allocation and final accounting.

The projection keeps selected navigation evidence, resolved revisions, essential provenance,
bounded content and symbols, aggregate selection diagnostics and the authoritative Repository
Context digest. It removes duplicated score detail and individual rejected-candidate rows under a
separate serialized-payload budget. Mechanical degradation never re-ranks evidence or changes the
internal context. The full rich representation remains explicitly available for diagnostics.

Repository Context and projection digests have different authority: the first identifies the Core
context decision, while the second identifies the final semantic agent payload. Synchronized Git
evidence remains navigation context; the current working repository is still authoritative for
implementation decisions. This boundary is recorded by ADR-046.

### Core Service (Java Spring)

The Core Service contains the business logic and acts as the source of truth for project knowledge.

Responsibilities:

- project management,
- repository management,
- knowledge storage,
- Engineering Events management,
- Challenges and Decisions management,
- human validation workflow,
- documentation generation orchestration.

### AI Service (Python)

The AI Service provides intelligent analysis capabilities while remaining independent from business ownership.

Responsibilities:

- semantic analysis,
- knowledge interpretation,
- event and decision proposals,
- content generation,
- AI-assisted reasoning.

The AI Service consumes structured context from the Core Service and does not directly own project knowledge.

### Frontend Application (Angular)

The frontend provides interaction with the DevLog AI platform.

Responsibilities:

- project visualization,
- knowledge exploration,
- proposal validation,
- snapshot consultation,
- documentation generation interface.

### Initial Communication Model

The initial version uses synchronous communication between services.

The Core Service orchestrates workflows and communicates with the AI Service through APIs.

Future versions may introduce event-driven communication for asynchronous analysis workflows.

## V1 Domain Model

DevLog AI focuses on a limited set of domain entities representing the lifecycle of project knowledge.

The model intentionally avoids unnecessary collaboration and SaaS concepts in the initial version.

### Core Entities

### User

Represents the developer owning and validating project knowledge.

### Project

Represents a software project monitored by DevLog AI.

A project is the main container for repositories, knowledge, and generated outputs.

### Repository

Represents a technical source connected to a project.

A project may contain multiple repositories.

### Raw Activity

Represents unprocessed technical information collected from project sources.

Examples:

- commits,
- file changes,
- dependency modifications.

### Analysis Run

Represents an execution of the analysis pipeline.

It records when analysis occurred, what sources were analyzed, and what knowledge proposals were generated.

### Engineering Event

Represents a meaningful evolution of the project.

### Engineering Challenge

Represents a problem, constraint, or situation influencing project evolution.

### Engineering Decision

Represents the reasoning behind important technical choices.

### Project Snapshot

Represents the current state of a project reconstructed from validated knowledge.

### Documentation Output

Represents generated documents derived from project knowledge.

Examples:

- technical articles,
- ADRs,
- architecture documentation.
