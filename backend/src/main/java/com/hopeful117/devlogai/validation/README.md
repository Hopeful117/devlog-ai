# Validation Domain

## Overview

The Validation domain records the human decision made on an AI-generated
`ValidatableProposal`.

It is the approval boundary between a proposal and trusted project knowledge:
AI-generated content remains a proposal until a user explicitly accepts or
rejects it.

---

## Business Concept

A Validation answers:

> "What decision was made for this proposal, by whom, and why?"

Each validation belongs to exactly one proposal, and each proposal can have at
most one validation.

```text
Analysis
   |
   +-- ValidatableProposal (PROPOSED)
             |
             +-- Validation
                    |
                    +-- ACCEPTED
                    |       |
                    |       +-- proposal status: ACCEPTED
                    |
                    +-- REJECTED
                            |
                            +-- proposal status: REJECTED
```

---

## Validation Workflow

Only proposals with the `PROPOSED` status can be validated.

A user submits one of the following decisions:

| Decision | Effect on the proposal |
| --- | --- |
| `ACCEPTED` | Changes its status to `ACCEPTED` |
| `REJECTED` | Changes its status to `REJECTED` |

The validation record and the proposal status update are performed in the same
transaction. A proposal that has already been decided cannot be validated
again.

The database also enforces this rule with a unique constraint on `proposal_id`.

---

## Validation Data

| Field | Description |
| --- | --- |
| `proposalId` | Identifier of the validated proposal |
| `decision` | Human decision: `ACCEPTED` or `REJECTED` |
| `validatedAt` | Timestamp automatically recorded when the validation is created |
| `validatedBy` | Identifier of the user who made the decision |
| `comment` | Optional explanation, limited to 2,000 characters |

---

## API

The domain currently exposes the following endpoints:

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/validations` | Create a validation for a proposed proposal |
| `GET` | `/api/v1/validations/{id}` | Retrieve a validation by its identifier |
| `GET` | `/api/v1/validations/proposal/{proposalId}` | Retrieve the validation associated with a proposal |

Example creation request:

```json
{
  "proposalId": "0f6f6d83-b6e3-4a1c-a2a8-7d59da755e7d",
  "decision": "ACCEPTED",
  "comment": "The proposal matches the project context.",
  "validatedBy": "3f876bc4-0751-4d10-8a01-85db98b6d547"
}
```

---

## Responsibilities

The Validation domain is responsible for:

- Recording a human decision for a proposal.
- Associating the decision with its author and optional comment.
- Updating the proposal status to `ACCEPTED` or `REJECTED`.
- Preventing a proposal from being validated more than once.
- Retrieving validations by their identifier or proposal.

The domain is not responsible for:

- Generating proposals or performing AI analysis.
- Deciding on behalf of a user.
- Promoting accepted proposal payloads into their target knowledge domain.
- Managing user identities or permissions.

Those responsibilities belong to the proposal, AI, knowledge-core, and
identity-related parts of the application.
