# Story 0087 — Expose repository context information through MCP

## Status

Implemented (see `implementation-report.md`)

## Objective

Close gaps **G1/G2** identified in `docs/mcp-server-audit.md`:

> Réduire la perte d'information entre `RepositoryContextEngine` et la réponse
> MCP de `get_engineering_context`.

The engine already computes bounded file content, Java symbols, per-item
timestamps, provenance references, extraction metadata and warnings. The MCP
contract dropped all of them. Additionally, validated Engineering Events and
open Challenges were loaded into the pipeline but never emitted as evidence.

## Scope

- Additive evolution of the shared contract (`devlog-contracts`).
- Faithful mapping of already-produced information (`EngineeringContextContractMapper`).
- Emission of validated Engineering Events and open Challenges as evidence
  (`ProjectKnowledgeContextCollector` v2).
- No retrieval/ranking algorithm change, no new profile, no new tool,
  no Resources, no prompts.

## Explicit non-goals

- Intent→profile selection (documented as separate architectural topic).
- KnowledgeRelations / ValidatableProposals / KnowledgeEvents exposure
  (classified C/D — see implementation report).
- Any of: search_project_history, explainDecision, proposeKnowledge, …
