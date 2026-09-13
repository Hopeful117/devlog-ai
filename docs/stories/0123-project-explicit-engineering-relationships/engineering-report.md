# Engineering Report

## Result

The first bounded relationship-aware slice preserves explicit relations that
already have projected endpoints. It does not infer new relationships or
change the global ranking model.

## Exact Projection Model

`relationshipHighlights[]` contains directed entries with:

- `relationType`;
- `source.entityType` and `source.entityId`;
- `target.entityType` and `target.entityId`.

`relationshipDiagnostics[]` contains deterministic omissions with relation ID,
type, endpoints, and reason.

## Endpoint Closure

Insight and EngineeringEvent endpoints may be represented by selected domain
snapshots or their selected repository evidence. Decision and Challenge
endpoints require selected repository evidence with the canonical
`decision:<id>` or `challenge:<id>` reference. A relation is never projected
with only one endpoint.

## Trust

No AI output is persisted or promoted as a relationship. Relation descriptions
are excluded because their creation path does not establish independent trust
or provenance.
