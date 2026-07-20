# Artifact Domain

## Overview

The Artifact domain represents the technical elements that compose a project.

An Artifact is a concrete and identifiable technical resource associated with a project.

It allows DevLog AI to understand the structure and components of a software project.

Unlike Knowledge Events, which represent what happened, and Decisions, which represent why choices were made, Artifacts represent what exists.

---

## Business Concept

An Artifact answers:

> "What technical element exists in this project?"

Examples:

- Source code files.
- Configuration files.
- Infrastructure definitions.
- Database schemas.
- API specifications.
- Technical documentation.
- Machine learning models.

---

## Entity Model

A project can contain multiple artifacts.

Each artifact belongs to exactly one project.

---

## Artifact Types

Current supported types:

| Type | Description |
|---|---|
| CODE | Source code or application component |
| DOCUMENTATION | Technical documentation |
| CONFIGURATION | Configuration files |
| DATABASE | Database structures or schemas |
| API | API definitions |
| INFRASTRUCTURE | Deployment and infrastructure resources |
| MODEL | Data or AI models |
| OTHER | Uncategorized artifact |

---


---

## Responsibilities

The Artifact domain is responsible for:

- Creating project artifacts.
- Associating artifacts with projects.
- Retrieving project components.
- Filtering artifacts by type.

The domain is not responsible for:

- Parsing source code.
- Generating documentation.
- AI analysis.
- Version control integration.
- Automatic artifact discovery.

These responsibilities belong to future domains.

---


