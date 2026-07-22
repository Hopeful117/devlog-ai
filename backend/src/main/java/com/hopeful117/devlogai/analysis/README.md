# Analysis Domain

## Overview

The Analysis domain represents an analysis execution performed on a project.

An analysis processes the knowledge accumulated within a project and provides the context from which insights can be generated.

The Analysis domain acts as a bridge between the project's knowledge and the future AI Engine.

---

## Business Concept

An Analysis answers:

> "What analysis was performed on the project, and what was its result?"

An analysis belongs to exactly one project.

An analysis may later produce multiple Insights.

An analysis can target an optional immutable Git revision (branch, tag or SHA).
When absent, collection uses each active Source's default branch at HEAD.

```text
Project
   |
   +── Analysis
           |
           +── Insight
           +── Insight
           └── Insight

```
## Domain Model
The Analysis entity maintains a unidirectional relationship toward Project.

The Project entity does not directly expose a collection of analyses.

### Analysis Types

The following analysis types are currently supported:

| Type                 | Description                                                        |
| -------------------- | ------------------------------------------------------------------ |
| ARCHITECTURE_REVIEW  | Reviews the project's architectural evolution and structure        |
| PROJECT_EVOLUTION    | Analyzes how the project has evolved over time                     |
| TECHNICAL_DEBT       | Identifies potential technical debt and recurring technical issues |
| SECURITY_REVIEW      | Analyzes potential security concerns                               |
| DOCUMENTATION_REVIEW | Reviews the quality and evolution of project documentation         |



These types may evolve as the AI Engine becomes more mature.

### Analysis Status

PENDING

The analysis has been created but execution has not started.

IN_PROGRESS

The analysis is currently being executed.

COMPLETED

The analysis has completed successfully.

FAILED

The analysis could not be completed successfully.



## Responsibilities

The Analysis domain is responsible for:

Creating analysis records.
Tracking analysis type.
Tracking analysis status.
Associating analyses with projects.
Retrieving analyses by project.
Filtering analyses by type.
Filtering analyses by status.
Maintaining analysis execution timestamps.
Defining the requested source revision for deterministic collection.

The Analysis domain is not responsible for:

Performing AI analysis.
Communicating directly with an LLM provider.
Generating insights.
Building prompts.
Managing AI model configuration.

These responsibilities belong to the future AI Engine.
