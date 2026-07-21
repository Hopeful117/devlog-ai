# Validatable Proposal Domain

## Overview

The Validatable Proposal domain represents AI-generated project knowledge proposals that require human validation before becoming trusted project knowledge.

AI-generated outputs are never directly persisted as authoritative project knowledge.

The proposal workflow is:

AI Interpretation
|
v
ValidatableProposal
|
v
Human Validation
|
+----+----+
|         |
v         v
ACCEPTED  REJECTED
|
v
Knowledge Promotion
|
v
Trusted Project Knowledge

## Responsibilities

The Validatable Proposal domain is responsible for:

- storing AI-generated proposals;
- associating proposals with a project and an analysis;
- tracking proposal type;
- tracking proposal lifecycle status;
- storing structured AI output;
- preserving proposal history.

## Proposal Lifecycle

A proposal starts with the `PROPOSED` status.

It can then transition to one of two terminal states:

- `ACCEPTED`
- `REJECTED`

The lifecycle is:

PROPOSED
|
+----> ACCEPTED
|
+----> REJECTED

Once a proposal has been accepted or rejected, it is immutable.

If a new AI analysis produces a different interpretation, a new proposal must be created.

## Proposal Types

Supported proposal types are:

- `INSIGHT`
- `ENGINEERING_DECISION`
- `ENGINEERING_EVENT`
- `CHALLENGE`
- `DOCUMENTATION`

The proposal type determines the expected structure of the JSON payload.

## Persistence

The proposal payload is stored as PostgreSQL `JSONB`.

This allows the proposal model to remain generic while supporting structured payloads for different proposal types.

Conceptually:

ProposalType
|
+---- INSIGHT
|       |
|       +---- Insight Proposal Payload
|
+---- ENGINEERING_DECISION
|       |
|       +---- Decision Proposal Payload
|
+---- ENGINEERING_EVENT
|
+---- Event Proposal Payload

The Core is responsible for validating the payload before promoting it to trusted domain knowledge.

## Relationships

A proposal belongs to:

- one `Project`;
- one `Analysis`.

The relationships are represented using JPA associations and database foreign keys.

The relationship is:

Project
|
+---- Analysis
|
+---- ValidatableProposal

