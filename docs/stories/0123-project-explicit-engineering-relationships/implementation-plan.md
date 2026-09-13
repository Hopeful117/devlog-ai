# Implementation Plan

1. Inspect the existing relation entity, creation lifecycle, context snapshot,
   selection service, projection service, and tests.
2. Confirm that `KnowledgeRelation.description` has no independent trust or
   proposal provenance and must remain internal for this Story.
3. Extend the current bounded relationship projection to recognize selected
   Decision and Challenge repository evidence as endpoints.
4. Enforce endpoint closure at projection: emit only relations whose two
   endpoints are already projected.
5. Add deterministic diagnostics for missing endpoints and relationship-budget
   omission.
6. Add projection and negative closure tests, including final map projection.
7. Run focused tests, broader backend selection/context tests, and inspect the
   final diff without committing.
