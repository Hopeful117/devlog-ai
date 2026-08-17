# Engineering Context V1 — Required Information

> **Status:** Draft
> **Scope:** DevLog AI / MCP
> **Purpose:** Define the minimum engineering context required by an external agent to understand and investigate a concrete engineering task.

## Context

DevLog can now expose trusted project information to external tools and agents through MCP.

The first implemented vertical slice exposes project-level context through:

```text
devlog://projects/{projectSlug}/context
```

The resource currently provides:

- project identity;
- project description;
- project status;
- project notes / Human Context Inputs.

This proves the integration path:

```text
External Agent / IDE
        ↓
       MCP
        ↓
   MCP Server
        ↓
      HTTP
        ↓
 DevLog Backend
        ↓
 Trusted Project Context
```

The next objective is not to expose every piece of information available in DevLog.

The objective is to answer a more specific question:

> What is the minimum reliable context an engineering agent needs to understand and investigate a concrete task?

This document defines the initial requirements for that capability.

---

# Core Principle

Engineering context must be built around an **engineering intent**, not around the structure of the database.

The agent should not need to know where DevLog stores:

- ADRs;
- Engineering Stories;
- project notes;
- commits;
- analyses;
- validated knowledge;
- historical events.

Instead, the agent should be able to express:

```text
I am working on this project
for this reason
and this is what I currently know.
```

DevLog is responsible for reconstructing the relevant engineering context.

Conceptually:

```text
Engineering Intent
        ↓
DevLog Context Composition
        ↓
Relevant Trusted Information
        ↓
Engineering Context
        ↓
External Agent
```

---

# Initial Use Case

The first use case is an **engineering investigation**.

Example:

```text
Project:
devlog-ai

Intent:
Investigate why Project Notes Markdown is displayed incorrectly.

Current information:
The Markdown content is stored correctly but appears incorrectly
when displayed through the application.
```

An external coding agent receiving only this description may lack important project-specific information.

For example:

- architectural boundaries;
- existing decisions;
- relevant project notes;
- current Engineering Story;
- previous changes related to the feature;
- known constraints;
- relevant validated knowledge.

Today, this context often has to be provided manually.

Engineering Context V1 should reduce that manual context reconstruction.

---

# Target Interaction

The intended interaction is conceptually:

```text
Agent
  ↓
get_engineering_context
  ↓
DevLog
  ↓
EngineeringContext
  ↓
Agent investigation
```

Example request:

```text
projectSlug = "devlog-ai"

intent =
"Investigate why Project Notes Markdown is displayed incorrectly."
```

Future versions may provide additional hints:

```text
files
symbols
engineeringStory
commit
branch
error message
```

These are optional hints, not authoritative context.

---

# Engineering Context Request V1

The minimum request should contain:

```text
projectSlug
intent
```

Conceptually:

```java
EngineeringContextRequest {
    String projectSlug;
    String intent;
}
```

## projectSlug

Identifies the project through its stable human-readable integration identifier.

Example:

```text
devlog-ai
```

The internal UUID remains a DevLog implementation detail.

## intent

Natural-language description of the engineering objective.

Examples:

```text
Investigate why Project Notes Markdown is displayed incorrectly.
```

```text
Implement the Project Context MCP resource.
```

```text
Understand how project history is reconstructed from commits.
```

The intent is not trusted project knowledge.

It is input used to determine what context may be relevant.

---

# Engineering Context Response V1

The response should be structured rather than returned as one large generated text block.

Conceptually:

```text
EngineeringContext
├── project
├── intent
├── projectNotes
├── decisions
├── engineeringStories
├── knowledge
├── history
└── metadata
```

Not every section must contain data.

An empty section is preferable to fabricated or weakly inferred information.

---

# Project Context

The response must identify the project.

Minimum information:

```text
id
name
slug
description
status
```

This information already exists in the current shared `ProjectContext` contract.

---

# Project Notes

Relevant Human Context Inputs may be included.

Potential information:

```text
type
title
contentMarkdown
status
updatedAt
```

Project Notes are particularly useful for understanding:

- goals;
- constraints;
- assumptions;
- known gaps;
- domain context.

Archived notes may remain useful for historical reconstruction but should not automatically have the same relevance as active notes.

V1 may initially include existing notes without sophisticated ranking.

---

# Architectural Decisions

Engineering context should eventually include ADRs relevant to the requested intent.

An agent investigating a feature should be able to discover decisions that constrain its implementation.

Examples:

```text
ADR defining project-history reconstruction
ADR defining AI proposal governance
ADR defining MCP integration boundaries
```

The context should preserve:

```text
ADR identity
title
status
decision
relevant content/reference
```

DevLog must distinguish validated architectural decisions from generated suggestions.

---

# Engineering Stories

If the intent relates to an existing Engineering Story, relevant story information should be available.

Potential information:

```text
story identity
title
status
objective
requirements
acceptance criteria
related architectural decisions
```

Engineering Stories are especially important for implementation agents because they describe the expected engineering outcome.

---

# Validated Knowledge

Validated project knowledge may be included when relevant.

Only trusted/promoted knowledge should be exposed as authoritative project knowledge.

AI-generated proposals must not silently appear as trusted knowledge.

ADR-006 remains applicable:

```text
AI Output
    ↓
ValidatableProposal
    ↓
Validation
    ↓
Trusted Knowledge
```

Engineering Context must preserve this authority boundary.

---

# Project History

Historical information can help explain why the current implementation exists.

Potential sources include:

```text
commits
diff analyses
historical events
previous analyses
relationships with ADRs
relationships with Engineering Stories
```

V1 does not require complete repository-history reconstruction.

History should only be included when DevLog already has sufficiently reliable information.

---

# Source Authority

Engineering Context must preserve the authority of its sources.

The following must never be treated as equivalent:

```text
Validated ADR
Validated Knowledge
Human Project Note
Engineering Story
Repository Fact
AI Analysis
ValidatableProposal
Agent Request
```

The consumer should be able to understand where important information came from.

Future contracts should therefore preserve source metadata where appropriate.

---

# Deterministic First

Engineering Context V1 should initially use existing deterministic DevLog capabilities.

The first implementation should not require:

- vector search;
- embeddings;
- LLM ranking;
- semantic RAG;
- autonomous agents.

Initial context composition may rely on:

```text
structured project relationships
existing projections
statuses
explicit associations
chronology
known project scope
```

This allows the MCP capability to become useful before the Retrieval Layer is complete.

---

# Future Retrieval Layer

The public Engineering Context capability should not depend on how context is retrieved internally.

Today:

```text
get_engineering_context
        ↓
Deterministic Context Composition
        ↓
EngineeringContext
```

Future:

```text
get_engineering_context
        ↓
Retrieval Layer
        ↓
Structured Search
+ Lexical Search
+ Vector Search
+ Temporal Context
+ Relationship Traversal
        ↓
ContextPack
        ↓
EngineeringContext
```

The MCP contract should remain stable while retrieval capabilities evolve.

---

# MCP Responsibility

MCP remains an integration adapter.

It does not determine engineering relevance itself.

```text
MCP Tool
    ↓
Application Service
    ↓
Context Composition / Retrieval
    ↓
Trusted DevLog Sources
```

The MCP server must not:

- query the DevLog database directly;
- inspect backend repositories directly;
- implement ranking rules;
- reconstruct project history itself;
- decide which knowledge is trusted;
- bypass DevLog application services.

---

# Initial MCP Capability

The first capability should conceptually be:

```text
get_engineering_context
```

Input:

```text
projectSlug
intent
```

Output:

```text
EngineeringContext
```

This is better represented as an MCP Tool than as a static Resource because the result depends on a request and an engineering intent.

The existing project Resource remains useful:

```text
devlog://projects/{projectSlug}/context
```

The two capabilities have different responsibilities:

```text
Project Resource
=
What does DevLog know about this project?

Engineering Context Tool
=
What does DevLog know that may help with this engineering task?
```

---

# Context Size

Engineering Context must eventually operate under an explicit context budget.

Returning all:

```text
ADRs
notes
stories
commits
knowledge
history
```

for every request will not scale.

V1 may initially tolerate larger responses while the project validates usefulness.

Future versions should support:

```text
relevance ranking
section limits
token budgets
summaries
progressive retrieval
references to additional resources
```

Context reduction must never silently alter source authority.

---

# Failure Behavior

DevLog should prefer explicit incomplete context over fabricated context.

Examples:

```text
project not found
→ explicit error

no related Engineering Story
→ empty engineeringStories

no reliable history available
→ empty history

retrieval unavailable
→ return available deterministic context where appropriate

unknown relationship
→ do not infer one as fact
```

An external agent must be able to distinguish:

```text
No information exists

from

Information could not be retrieved
```

---

# V1 Success Criteria

Engineering Context V1 is successful if an external coding agent can receive:

```text
projectSlug
+
engineering intent
```

and obtain enough structured DevLog context to begin a useful investigation with significantly less manual project explanation.

The first validation should use a real engineering problem.

Recommended reference scenario:

```text
Investigate why Project Notes Markdown is displayed incorrectly.
```

The resulting context should then be evaluated manually:

- What useful information was returned?
- What irrelevant information was returned?
- What important information was missing?
- What context still had to be manually supplied?
- Did the agent understand architectural constraints better?
- Did DevLog prevent incorrect assumptions?

The answers should drive V2.

---

# Explicit Non-Goals

Engineering Context V1 is not intended to:

- generate implementation code;
- automatically modify the repository;
- replace Kiko;
- replace coding agents;
- implement full RAG;
- provide unrestricted repository access;
- expose the database;
- expose every DevLog object;
- automatically trust AI-generated analysis;
- autonomously decide engineering actions.

Its responsibility is:

> Provide reliable, structured, project-aware engineering context to a consumer that needs to perform an engineering task.

---

# Evolution

Expected evolution:

```text
Phase 1
+ project identity
+ project notes
+ deterministic context composition
+ get_engineering_context MCP Tool

Phase 2
+ Engineering Story context
+ ADR context
+ validated knowledgeInvestigate whether the EXISTING DevLog context pipeline can serve as the core
of the future `get_engineering_context` capability.

This is a READ-ONLY architecture investigation.

Do NOT modify code.
Do NOT implement anything.
Do NOT redesign DevLog from scratch.
Do NOT repeat the previous general capability analysis.

We already know that DevLog contains:

- ProjectContextProvider
- EngineeringStoryContextService
- RepositoryContextAdapter
- RepositoryContextEngine / RepositoryContextService
- KnowledgeSelectionService
- AgentContextProjectionService
- AnalysisContext

The question is now much narrower:

Can these existing components be reused to implement a general-purpose
Engineering Context capability for external engineering agents, or are they too
specialized around Engineering Story generation?

The intended future flow is conceptually:

External Agent
      ↓
MCP get_engineering_context
      ↓
Application-level facade
      ↓
Existing DevLog context/retrieval capabilities
      ↓
Agent-ready structured engineering context

MCP must remain only an adapter.

---

# 1. EngineeringStoryContextService Deep Dive

Inspect:

- EngineeringStoryContextService
- EngineeringStoryContextServiceImpl
- EngineeringStoryContext
- AgentEngineeringStoryContext
- every direct dependency used by the implementation

Report the exact public methods and signatures.

For each method determine:

- input;
- output;
- data sources;
- transformations;
- assumptions;
- whether an EngineeringStory must already exist;
- whether a free-text description is enough;
- whether it is coupled to story lifecycle;
- whether it persists anything;
- whether it can be called safely for a generic investigation.

Pay particular attention to methods similar to:

build(...)
buildWithRepositoryContext(...)
buildAgentWithRepositoryContext(...)

if they exist.

Determine whether:

"Investigate why Project Notes Markdown is displayed incorrectly."

could already be passed through this service WITHOUT creating or persisting an
Engineering Story.

Classify the service as:

GENERAL_REUSABLE
REUSABLE_WITH_ADAPTER
STORY_SPECIFIC

Explain precisely why.

---

# 2. RepositoryContextAdapter Deep Dive

Inspect RepositoryContextAdapter and all directly invoked context services.

Report:

- exact method signatures;
- required inputs;
- output;
- whether it requires Analysis;
- whether it requires EngineeringStory;
- whether it can operate from projectId + free-text description/intent;
- whether it creates synthetic AnalysisContext;
- what information from ProjectContextSnapshot it transfers;
- what information it drops;
- whether it persists anything.

Trace one complete call through the adapter.

Example:

projectId
+ ProjectContextSnapshot
+ description
+ intent
+ guidance
    ↓
?
    ↓
RepositoryContext

Do not summarize conceptually only.

Follow the actual code path and identify the concrete classes/methods invoked.

Classify:

GENERAL_REUSABLE
REUSABLE_WITH_ADAPTER
STORY_SPECIFIC

---

# 3. RepositoryContext Engine Deep Dive

Inspect the actual implementation behind RepositoryContext creation.

Identify:

- RepositoryContextService
- RepositoryContextEngine
- collectors
- rankers
- selectors
- policies
- context profiles
- intent handling

Produce the actual pipeline.

For example, only if supported by code:

Request
  ↓
Collectors
  ↓
Candidate evidence
  ↓
Rankers
  ↓
Selectors
  ↓
RepositoryContext

For every stage identify:

- class;
- input;
- output;
- deterministic or AI-assisted;
- whether free-text intent affects behavior.

We specifically need to understand whether this is already a deterministic
retrieval engine.

---

# 4. Intent Semantics

Trace exactly how `intent` is represented.

Determine whether intent is:

- arbitrary free text;
- enum/profile;
- structured object;
- persisted entity;
- combination of several values.

Trace an example string:

"Investigate why Project Notes Markdown is displayed incorrectly."

through the pipeline.

Does the literal text influence retrieval?

Or is it transformed/ignored while a predefined intent/profile controls
selection?

Identify every location where intent affects:

- collectors;
- scoring;
- ranking;
- filtering;
- mandatory evidence;
- context profiles;
- final projection.

This section must distinguish:

FREE_TEXT_RELEVANCE

from

PREDEFINED_INTENT_PROFILE

These are not equivalent.

---

# 5. KnowledgeSelectionService Deep Dive

Inspect KnowledgeSelectionService and its implementation.

Report:

- exact input;
- exact output;
- scoring rules;
- observationScore();
- factScore();
- contextProfiles();
- requireMandatoryKnowledge();
- any other relevant scoring/filtering methods.

Determine what "relevance" means in the current implementation.

For example:

Does it calculate semantic relevance between:

intent text
↔
knowledge content

or does it apply deterministic rules based on:

types
profiles
categories
metadata
relationships?

Give concrete examples from the implementation.

Classify the current retrieval as one or more of:

STRUCTURED_RETRIEVAL
RULE_BASED_RETRIEVAL
LEXICAL_RETRIEVAL
SEMANTIC_RETRIEVAL
VECTOR_RETRIEVAL

Do not call something semantic retrieval unless the implementation actually
compares meaning/content.

---

# 6. AgentContextProjectionService Deep Dive

This is especially important.

Inspect:

- AgentContextProjectionService
- implementation
- configuration/properties
- budget/policy classes
- resulting agent context types

Determine:

- what input it receives;
- what output it produces;
- whether output is structured;
- byte limits;
- token limits if any;
- collection limits;
- truncation rules;
- pruning order;
- preservation priorities;
- whether source authority is preserved;
- whether important information can be dropped silently;
- whether diagnostics explain truncation.

Produce the actual compaction strategy in execution order.

Example only:

full RepositoryContext
       ↓
remove X
       ↓
limit Y
       ↓
truncate Z
       ↓
AgentContext

Use the real implementation.

Then answer:

Could this service serve as the initial Context Budget mechanism for
`get_engineering_context`?

Classify:

DIRECTLY_REUSABLE
REUSABLE_WITH_CHANGES
NOT_SUITABLE

---

# 7. RepositoryContext Output

Inspect the RepositoryContext model itself.

Document its complete structure.

For each major section identify its provenance:

RepositoryContext
├── ?
├── ?
└── ?

Determine whether it preserves enough information for an external agent to
understand:

- what is a repository fact;
- what is project knowledge;
- what came from a human note;
- what came from an ADR;
- what came from an Engineering Story;
- what came from analysis;
- why an item was selected.

Identify any provenance/authority information lost during composition.

---

# 8. AgentEngineeringStoryContext Output

Inspect AgentEngineeringStoryContext.

Document its complete structure.

Then answer:

If we simply renamed this type to EngineeringContext and exposed it through MCP,
what would be wrong?

Identify:

- story-specific fields;
- story-specific semantics;
- missing generic-investigation fields;
- useful reusable sections;
- accidental coupling.

We do NOT intend to rename it blindly.

This comparison is intended to identify reusable concepts.

---

# 9. Side Effects

For every component in this pipeline, determine whether invoking it can:

- write to the database;
- create Analysis entities;
- create Engineering Stories;
- create proposals;
- invoke AI/LLM;
- execute Git commands;
- access filesystem/repository;
- update projections;
- mutate project state.

We need `get_engineering_context` to behave primarily as a READ operation.

Produce:

| Component | DB Write | AI Call | Git/FS Access | Other Side Effect |

This is critical.

---

# 10. Generic Investigation Test

Use this exact request:

projectSlug:
devlog-ai

intent:
"Investigate why Project Notes Markdown is displayed incorrectly."

Assume:

- no Engineering Story is created for this investigation;
- no Analysis should be persisted;
- no AI/LLM should be invoked;
- MCP should perform a read-like operation.

Trace how far the EXISTING pipeline can process this request today.

Show:

Request
   ↓
class.method(...)
   ↓
class.method(...)
   ↓
...
   ↓
Result

At the exact point where the existing pipeline no longer fits, stop and explain
why.

Then list what context would actually be returned.

---

# 11. Architecture Decision

Based on the actual code, choose ONE:

A. Reuse EngineeringStoryContextService directly

B. Add a thin generic facade over EngineeringStoryContextService

C. Reuse lower-level components
   (ProjectContextProvider + RepositoryContextAdapter/Engine +
   AgentContextProjectionService)
   behind a new generic facade

D. Existing pipeline is too story-specific; create a separate context pipeline

Choose only one as the recommendation.

Evaluate using:

- duplication;
- coupling;
- side effects;
- semantic correctness;
- testability;
- future RAG integration;
- MCP independence;
- future Kiko/OpenCode consumers.

Do not prefer reuse merely because code already exists.

Semantic correctness matters more than minimizing new classes.

---

# 12. Future Retrieval Layer Compatibility

Without designing RAG itself, identify the natural extension point where future:

- lexical retrieval;
- BM25;
- vector retrieval;
- hybrid retrieval;
- reranking;
- temporal ranking

could be introduced.

Determine whether that extension point is currently:

RepositoryContextAdapter
RepositoryContextService
RepositoryContextEngine
KnowledgeSelectionService
another abstraction

Explain why.

The goal is that:

get_engineering_context

does NOT need to change its external contract when retrieval evolves.

---

# 13. Recommended Minimal Vertical Slice

If we decide to implement `get_engineering_context`, specify the smallest
production change required.

List:

- new classes actually required;
- existing classes reused unchanged;
- contracts required;
- whether a backend REST endpoint is required;
- whether a new devlog-contracts contract is required;
- MCP Tool required;
- tests required.

Do NOT implement them.

Avoid introducing abstractions that are unnecessary for V1.

---

# Expected Output

Return exactly:

## Executive Conclusion

## EngineeringStoryContextService

## RepositoryContextAdapter

## RepositoryContext Retrieval Pipeline

## Intent Semantics

## Knowledge Selection

## Agent Context Projection / Budgeting

## RepositoryContext Structure and Provenance

## AgentEngineeringStoryContext vs Generic EngineeringContext

## Side Effects Matrix

## Generic Investigation Trace

## Architecture Recommendation

## Future Retrieval Extension Point

## Minimal Vertical Slice

## Files Inspected

Do not modify any files.
Do not implement anything.
Do not repeat the previous general capability matrix.
+ explicit source metadata

Phase 3
+ project-history context
+ commit/diff relationships
+ temporal context

Phase 4
+ Retrieval Layer
+ lexical retrieval
+ structured retrieval
+ vector retrieval
+ relevance ranking
+ context budgets

Phase 5
+ ContextPack
+ Kiko integration
+ coding-agent integration
+ Developer OS orchestration
```

These phases describe an expected direction, not a fixed implementation schedule.

---

# Architectural Constraint

The Engineering Context capability must remain reusable independently of MCP.

The desired architecture is:

```text
                  ┌──────────── MCP
                  │
External Consumer ├──────────── REST
                  │
                  └──────────── Future Agent Runtime
                               ↓
                    Engineering Context Service
                               ↓
                     Retrieval / Composition
                               ↓
                       DevLog Knowledge
```

MCP is one adapter.

Engineering Context belongs to DevLog.

---

# Next Step

Do not implement the complete ContextPack or Retrieval Layer yet.

The next implementation step should define the smallest application-level capability capable of producing an `EngineeringContext` from:

```text
projectSlug
intent
```

using existing deterministic DevLog information.

The first version should then be exposed through:

```text
get_engineering_context
```

and evaluated against a real engineering investigation before adding additional retrieval complexity.