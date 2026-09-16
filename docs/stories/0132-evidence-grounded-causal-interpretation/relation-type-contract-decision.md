# Story 0132 - Relation Type Contract Decision

## Decision

`GroundingMetadata.relationType` is structurally nullable, but it is required
for relationship-bearing findings. `ArchitectureFinding`, `DecisionFinding`,
`HistoricalContextItem`, and `ImpactedComponentFinding` are relationship-bearing.
`EvidenceFinding` and `ConstraintFinding` may omit the field.

The allowed values remain `EXPLICIT`, `TEMPORAL_PROXIMITY`,
`POSSIBLE_RELEVANCE`, and `INFERRED_HYPOTHESIS`. Missing values are not replaced
with a default relation.

## Authority

Story 0116 explicitly defines `relationType` as required for relationship-bearing
findings, and its acceptance criteria name the four affected finding categories.
The Story0132 prompt preserves the same matrix. ADR-067 D8 defines the relation
vocabulary and keeps relation semantics distinct from confidence and causality.

Commit `86ff5b9` introduced the enum and relationship-bearing validation, but its
Java `GroundingMetadata` record also made the field globally non-null. That was an
implementation divergence from the approved finding-specific rule, not a later
domain decision.

## Enforcement

Java now permits null at the generic record boundary and enforces presence in
Core callback validation for the four relationship-bearing finding lists.
Evidence and constraint findings retain valid absence semantics. Invalid enum
values remain rejected. Python already represented the generic field as nullable
and applied the same finding-specific validation matrix.

No historical output was enriched and no relation value was inferred.
