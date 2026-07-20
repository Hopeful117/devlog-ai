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

When a repository is connected for the first time, DevLog AI performs an initial understanding phase.

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


