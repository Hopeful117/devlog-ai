# Documentation Domain

## Overview

The Documentation domain manages generated and stored project knowledge.

A documentation represents a structured synthesis of a project's technical knowledge.

Unlike artifacts, which describe what exists, or decisions, which describe why choices were made, documentation provides a readable representation of the current understanding of a project.

---

## Business Concept

A Documentation answers:

> "What do we currently know about this project?"

Examples:

- Architecture documentation.
- API documentation.
- Database documentation.
- Security documentation.
- Deployment documentation.

---

## Domain Model

A project can contain multiple documentation entries.

Each documentation entry belongs to exactly one project.

---

## Historical Model

Documentation is considered historical knowledge.

Each generated documentation creates a new entry.

Existing documentation is never overwritten.



Each version represents the state of project knowledge at a specific point in time.

---

## Storage Format

Documentation content is stored as Markdown.

Benefits:

- Human readable.
- Compatible with LLM processing.
- Easy conversion to HTML or PDF.
- Version friendly.
- Suitable for generated technical documentation.

Example:

```markdown
# Architecture Overview

## Services

The project contains:

- API Gateway
- Trading Core
- Broker Service
```
## Decisions
The services were separated to improve isolation.

## Documentation Types

| Type         | Description                                 |
| ------------ | ------------------------------------------- |
| ARCHITECTURE | System architecture documentation           |
| API          | API documentation                           |
| DATABASE     | Database documentation                      |
| SECURITY     | Security documentation                      |
| DEPLOYMENT   | Deployment and infrastructure documentation |
| GENERAL      | General project documentation               |

## Responsibilities

The Documentation domain is responsible for:

Creating documentation entries.
Storing generated knowledge.
Managing documentation history.
Retrieving documentation by project.
Filtering documentation by type.

The domain is not responsible for:

Generating content.
Calling AI models.
Analyzing source code.
Discovering artifacts.

Those responsibilities belong to future domains.