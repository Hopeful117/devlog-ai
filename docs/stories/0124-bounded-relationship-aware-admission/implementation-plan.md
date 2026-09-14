# Implementation Plan

1. Add a configurable, validated relationship capacity with a fail-closed
   default of zero and preserve the existing constructor used by unit tests.
2. Bound active Insight discovery to the standalone budget plus the configured
   relational capacity.
3. Expand the shared Engineering Event candidate bound to the derived maximum
   pool and keep final standalone admission at ten.
4. Build a deterministic one-hop admission pass over explicit relations,
   admitting only Insight and Engineering Event endpoints and excluding
   `RESOLVES` for `architecture-overview`.
5. Preserve existing prompt projection closure, repository context inputs, and
   trust boundaries.
6. Add focused tests for capacity, endpoint eligibility, deterministic order,
   and the architecture-specific `RESOLVES` exclusion.
7. Run focused selection tests and the broader backend test suite; inspect the
   final diff without committing.
