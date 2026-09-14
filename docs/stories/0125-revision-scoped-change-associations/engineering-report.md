# Story 0125 - Engineering Report

## Source of Truth

`ProjectCommit.changedFiles` remains authoritative. No Git parser or new
persistence model was introduced.

## Hybrid Boundary

`KnowledgeRelation` continues to represent durable UUID-backed validated
knowledge. `EngineeringRelationship` is a consumer-neutral reconstructed
projection carrying typed endpoints, origin, trust tier, revision, and evidence.

## Admission

Repository `CHANGES` relationships use the existing Story 0124 relational
capacity, deterministic ordering, and strict endpoint closure. They are eligible
for `architecture-overview` only. Existing durable relation handling remains in
place.

## Consumer Equity

The same selected relationship representation feeds structured human-compatible
context and AI prompt projection. No AI-only extraction path exists.
