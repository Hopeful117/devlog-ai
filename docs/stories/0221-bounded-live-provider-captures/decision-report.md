# Story 0221 - Decision Report

## Approved Decisions

- Captures remain evaluation-only exports; no new production persistence entity is added.
- Export retention reuses the existing diagnostic retention ceiling.
- Usage is limited to token measurements; provider cost metadata is deferred.
- `AI_INTERACTION_TRACE` is the approved attribution vocabulary for measured visibility.
- A real provider capture is optional for acceptance; unavailable credentials or runtime access
  remain `NOT_EVALUATED`.

## Boundary

The implementation projects allowlisted metadata from an existing interaction trace and Core
snapshot. It does not retrieve context, reconstruct Core identity, change provider policy, persist
raw provider material, or promote evaluation output to trusted knowledge.
