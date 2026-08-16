# DevLog AI — MCP Architecture Context

> **Status:** Architecture Context
> **Scope:** DevLog AI — MCP Integration
> **Goal:** Define the architectural role of MCP before implementation

## Context

DevLog AI is intended to become the technical memory and engineering context provider for the projects it observes.

Today, engineering work may be performed by different consumers:

* a human developer;
* Kiko;
* OpenCode;
* another IDE agent;
* a future coding agent;
* Developer OS workflows.

The engineering context required to understand and continue a task must therefore **belong to the project and DevLog knowledge**, not to the conversational memory of a specific agent or provider.

A practical example is provider or token exhaustion: if Kiko becomes temporarily unavailable, another agent should be able to continue the work without requiring the developer to manually reconstruct the entire project context.

This is only one use case. The broader goal is to make DevLog context reusable by any authorized human, agent or development tool.

## Problem

Without a standardized integration layer, external tools must reconstruct context independently from:

* repository files;
* Git history;
* commits and diffs;
* ADRs;
* Engineering Stories;
* roadmap information;
* DevLog projections;
* validated project knowledge.

This causes:

```text
Agent / Tool
    ↓
Incomplete context
    ↓
Repeated manual explanations
    ↓
Incorrect assumptions
    ↓
Poor investigations or implementations
    ↓
Additional engineering iterations
```

The objective is to allow consumers to retrieve reliable DevLog context through a stable interface.

## Decision Direction

DevLog will expose selected context and capabilities through an **MCP adapter**.

Conceptually:

```text
Human / IDE Agent / Kiko / OpenCode
                 │
                 ▼
             MCP Client
                 │
                 ▼
           DevLog MCP
                 │
                 ▼
       DevLog Application Layer
                 │
        ┌────────┼─────────┐
        ▼        ▼         ▼
   Projections  History   Knowledge
        │        │         │
        └────────┼─────────┘
                 ▼
         Engineering Context
```

MCP is an **integration protocol**, not a business layer.

## Core Principle

> **Engineering context must belong to the project, not to the agent currently consuming it.**

Consumers should be replaceable without losing access to the project's technical context.

For example:

```text
Kiko unavailable
      ↓
OpenCode / another IDE agent
      ↓
DevLog MCP
      ↓
same engineering context
      ↓
work continues
```

DevLog must therefore remain consumer-agnostic.

Capabilities should never be designed specifically around one agent.

Prefer:

```text
get_engineering_context()
search_project_history()
get_architectural_constraints()
```

instead of:

```text
get_context_for_kiko()
get_context_for_opencode()
```

## Architectural Role of MCP

The intended separation is:

```text
MCP
  =
External protocol adapter

DevLog Application
  =
Use cases and orchestration

DevLog Domain
  =
Business rules and knowledge authority

Projections
  =
Optimized read models

Future Retrieval Layer
  =
Relevant context discovery

Agents / IDE
  =
Consumers of engineering context
```

The MCP layer must not duplicate DevLog business logic.

## Initial Deployment Decision

The first MCP implementation should live inside the DevLog repository as a dedicated module:

```text
devlog/
├── devlog-core/
├── devlog-application/
├── devlog-infrastructure/
├── devlog-api/
├── devlog-mcp/
└── frontend/
```

`devlog-mcp` should behave conceptually like another inbound adapter beside the REST API.

```text
               DevLog Application
                  ▲           ▲
                  │           │
            REST Adapter   MCP Adapter
                  │           │
                  ▼           ▼
              Web UI       IDE / Agents
```

The module should remain sufficiently decoupled to allow extraction later if MCP evolves into a Developer OS-wide gateway.

## Initial Scope

The first version should remain intentionally small and primarily **read-only**.

Its goal is not to expose every DevLog capability.

The first useful capabilities should focus on retrieving engineering context such as:

```text
get_project_context
search_project_context
get_engineering_context
search_project_history
get_architectural_constraints
```

Exact MCP Resources and Tools will be defined in a dedicated ADR.

## IDE Integration Use Case

A primary early use case is enabling an IDE agent to request DevLog context while investigating or implementing a task.

Example:

```text
IDE Agent
    │
    │ "Why does this component behave this way?"
    ▼
DevLog MCP
    │
    ▼
Engineering Context
    ├── relevant code context
    ├── historical commits
    ├── ADRs
    ├── Engineering Stories
    ├── validated knowledge
    └── architectural constraints
```

The IDE agent remains responsible for:

* reading and modifying local files;
* running Git commands;
* building the project;
* executing tests;
* producing code changes.

DevLog MCP is responsible for providing **project memory and engineering context**, not local development execution.

## Relationship With Retrieval / RAG

MCP and the future Retrieval Layer solve different problems.

```text
Retrieval
    =
Which engineering context is relevant?

MCP
    =
How can an external consumer access that capability?
```

Initially:

```text
MCP
 ↓
Application Services
 ↓
Existing Projections
```

Later:

```text
MCP
 ↓
DevLog Application
 ↓
Retrieval Layer
 ↓
Hybrid Retrieval
 ↓
ContextPack
```

The MCP contract should therefore remain independent from the underlying retrieval implementation.

A future migration from structured queries to hybrid/vector retrieval must not require IDE consumers to change how they interact with DevLog.

## Relationship With Agent Orchestration

MCP must not decide which agent performs a task.

For example, provider fallback:

```text
Kiko unavailable
       ↓
choose another agent
```

belongs to a future **Agent Orchestrator / Workflow layer**, not DevLog MCP.

The responsibilities remain:

```text
Agent Orchestrator
    → selects / coordinates execution

MCP
    → provides interoperability

DevLog
    → provides context and project memory

Agent
    → performs engineering work
```

## Knowledge Governance

MCP must never bypass existing DevLog knowledge rules.

In particular:

```text
Agent
  ↓
MCP
  ↓
Trusted Knowledge
```

must **not** be a direct write path.

Future write-capable MCP tools must preserve the DevLog proposal and validation model:

```text
Agent
  ↓
MCP Tool
  ↓
ValidatableProposal
  ↓
Validation
  ↓
Atomic promotion
  ↓
Trusted Knowledge
```

ADR-006 remains authoritative.

## Security Boundary

The MCP module should not directly expose:

* database access;
* persistence repositories;
* unrestricted filesystem access;
* internal Git implementation details;
* unrestricted project mutations;
* direct trusted-knowledge writes.

Expected path:

```text
MCP
  ↓
Application Port / Service
  ↓
Domain / Projection
```

Not:

```text
MCP
  ↓
Database / JDBC / filesystem
```

## Future Evolution

The initial implementation is DevLog-specific.

Later, if multiple Developer OS modules require a shared MCP interface, the architecture may evolve toward:

```text
Developer OS MCP
        │
        ├── DevLog
        ├── Workspace
        ├── Codeglyphe
        ├── Engineering Workflow Studio
        └── future modules
```

At that point, extracting the MCP gateway into a dedicated service/repository may become appropriate.

This should not be done prematurely.

## Success Criterion for the First Prototype

The first prototype should demonstrate one concrete capability:

> **An external IDE agent can retrieve enough structured DevLog engineering context to continue investigating a project task without depending on the conversational context of a specific agent.**

This provides immediate practical value while validating:

* DevLog application boundaries;
* quality of existing projections;
* usefulness of stored engineering knowledge;
* MCP integration with IDE agents;
* future Retrieval Layer requirements.

## Next Architecture Work

Before initializing `devlog-mcp`, define:

1. **ADR — DevLog MCP Module Placement**
2. **ADR — MCP Capability Model**
3. **MCP Security & Governance**
4. Initial MCP resource/tool contracts
5. Minimal V1 acceptance criteria

Implementation should begin only once these boundaries are explicit.
