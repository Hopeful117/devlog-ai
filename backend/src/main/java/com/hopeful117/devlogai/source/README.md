# Source Domain

## Overview

The Source domain represents an origin from which DevLog AI can collect
technical evidence about a Project. A Project can own multiple Sources; a
Source belongs to exactly one Project.

The V1 supports only `GIT_REPOSITORY`. Git is therefore the first supported
Source, but it does not define the Project itself.

## Model

A Git repository Source contains:

- general metadata: name, type, activation state and synchronization dates;
- explicit Git configuration: repository URL, default branch and provider;
- its owning Project and audit timestamps.

Supported providers are `GITHUB`, `GITLAB`, `BITBUCKET`, `AZURE_DEVOPS` and
`GENERIC_GIT`. The provider and default branch are optional.

## Lifecycle

New Sources are active by default. Activation can be changed without deleting
the Source, preserving its history. Inactive Sources remain retrievable but
must not be used by future collection or synchronization operations.

## API

- `POST /api/v1/sources`
- `GET /api/v1/sources/{id}`
- `GET /api/v1/sources/project/{projectId}`
- `PATCH /api/v1/sources/{id}/activation`

Project retrieval is ordered deterministically by `createdAt DESC, id DESC`.

Active Git Sources are consumed by the deterministic collection pipeline before
an Analysis context is built. Their persistent workspaces remain an
infrastructure concern and are not exposed by this domain.

## Out of Scope

V1 does not authenticate with Git providers, ingest non-Git documents, or scope
an Analysis to only a subset of a Project's active Sources.
