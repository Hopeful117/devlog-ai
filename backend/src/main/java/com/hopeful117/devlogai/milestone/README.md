# Milestone Domain

## Overview

The Milestone domain represents significant stages in the evolution of a project.

A milestone provides a higher-level view of project progress by grouping a period of development around a meaningful objective or achievement.

Examples:

- MVP
- Beta release
- Production release
- Major migration
- Architecture refactoring

---

## Business Concept

A Milestone answers:

> "What significant stage of the project are we working toward or have we completed?"

A project can contain multiple milestones.



---

## Domain Model

Each milestone belongs to exactly one project.

A project can contain multiple milestones.

The Milestone domain does not maintain a bidirectional JPA relationship from `Project` to `Milestone`.

---

## Status

A milestone can have one of the following statuses:

| Status | Description |
|---|---|
| PLANNED | The milestone has been defined but work has not started |
| IN_PROGRESS | Work on the milestone is currently active |
| COMPLETED | The milestone has been completed |
| CANCELLED | The milestone has been cancelled |

New milestones are automatically created with the `PLANNED` status.

The status is not provided by the client during creation.

---

## Historical Context

Milestones provide a higher-level structure for understanding project evolution.

The Knowledge Core contains:
KnowledgeEvent
|
└── What happened?

Decision
|
└── Why was it chosen?

Artifact
|
└── What exists?

Documentation
|
└── What do we understand?

Milestone
|
└── What significant stage are we working toward?


Milestones are intended to provide context for the project's evolution.

---

## Responsibilities

The Milestone domain is responsible for:

- Creating milestones.
- Tracking milestone status.
- Retrieving milestones by project.
- Filtering milestones by status.
- Tracking milestone start and completion dates.

The domain is not responsible for:

- Generating milestones automatically.
- Analyzing project progress.
- Generating AI insights.
- Managing relationships between all knowledge entities.

Those responsibilities belong to other parts of the application.

---

