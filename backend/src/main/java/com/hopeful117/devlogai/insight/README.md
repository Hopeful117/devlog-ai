# Insight Domain

## Overview

The Insight domain represents a significant observation, finding, or recommendation produced by an analysis of a project.

Insights are the concrete results generated from the project's accumulated knowledge and analysis history.

The Insight domain is therefore one of the main outputs of the future AI Engine.

---

## Business Concept

An Insight answers:

> "What did the analysis identify about the project?"

An Insight belongs to:

- One Project.
- One Analysis.

An Analysis may generate multiple Insights.

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

An Insight maintains direct references to both its Project and its source Analysis.

This allows the application to retrieve insights directly at project or analysis level.

## Insight Types

| Type           | Description                                      |
| -------------- | ------------------------------------------------ |
| ARCHITECTURAL  | Identifies architectural observations or issues  |
| EVOLUTION      | Identifies relevant project evolution patterns   |
| TECHNICAL_DEBT | Identifies potential or confirmed technical debt |
| SECURITY       | Identifies potential security concerns           |
| RISK           | Identifies project risks                         |
| RECOMMENDATION | Provides a recommended action or improvement     |

## Insight Severity
| Severity | Description                                  |
| -------- | -------------------------------------------- |
| INFO     | Informational observation                    |
| WARNING  | Observation requiring attention              |
| CRITICAL | Important issue requiring priority attention |

## Responsabilities

The Insight domain is responsible for:

Storing analysis findings.
Associating findings with projects.
Associating findings with their source analyses.
Categorizing insights by type.
Assigning insight severity.
Retrieving insights by project.
Retrieving insights by analysis.
Filtering insights by type.
Filtering insights by severity.

The Insight domain is not responsible for:

Performing AI analysis.
Communicating with LLM providers.
Building prompts.
Selecting AI models.
Generating analysis context.

These responsibilities belong to the future AI Engine.