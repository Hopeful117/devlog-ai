# Knowledge Event Domain

## Overview

The Knowledge Event domain represents the memory layer of DevLog AI.

A Knowledge Event is a significant fact that happened during the lifecycle of a project.

It captures raw development knowledge that can later be analyzed by AI systems to generate insights, documentation, summaries, or architectural recommendations.

The domain does not store AI interpretations. It stores factual events.

---

## Business Concept

A Knowledge Event answers:

> "What important thing happened in this project?"

Examples:

- A new feature was implemented.
- A bug was fixed.
- An architectural decision was introduced.
- A dependency was replaced.
- A security improvement was applied.
- A refactoring was completed.

The event is the source information. AI analysis is a separate responsibility.

---

## Entity Model

A project can contain multiple knowledge events.

Each event belongs to exactly one project.

---

## KnowledgeEvent Types

Current supported types:

| Type | Description |
|---|---|
| FEATURE | New functionality added |
| BUG | Bug correction |
| REFACTORING | Code improvement or restructuring |
| ARCHITECTURE | Architectural change |
| DOCUMENTATION | Documentation improvement |
| DEPENDENCY | Dependency change |
| SECURITY | Security improvement |
| PERFORMANCE | Performance optimization |
| TEST | Test related change |
| DEPLOYMENT | Deployment or infrastructure change |
| OTHER | Uncategorized event |

---

## Responsibilities

The Knowledge Event domain is responsible for:

- Creating knowledge events.
- Associating events with projects.
- Retrieving project history.
- Providing factual development history.

The domain is not responsible for:

- AI analysis.
- Documentation generation.
- Embeddings.
- Knowledge scoring.
- Automatic classification.

Those responsibilities belong to future AI-related domains.

