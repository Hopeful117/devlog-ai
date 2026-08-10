# Code Review Report — Story 0027

## Review Summary

Story 0027 ajoute les opérations update et delete à l'API Engineering Decision.

**Recommandation : Approved**

## Inputs Reviewed

- Story 0027 story.md
- Repository Analysis approuvée
- Implementation Plan approuvé
- Implementation Report
- Code source : `DecisionService.java`, `DecisionServiceImpl.java`, `DecisionController.java`
- Tests : `DecisionServiceTest.java`

## Acceptance Criteria Verification

| AC | Description | Status |
|----|-------------|--------|
| AC-1 | Given a Decision, when I update its title, then the change is persisted and `updatedAt` is refreshed | ✅ `update()` uses `@LastModifiedDate` |
| AC-2 | Given a Decision, when I delete it, then it is removed from the database | ✅ `delete()` calls `decisionRepository.delete()` |
| AC-3 | Given a non-existent Decision ID, when I attempt update, then `EntityNotFoundException` is thrown | ✅ Test: `shouldThrowExceptionWhenUpdatingNonExistentDecision` |
| AC-4 | Given a non-existent Decision ID, when I attempt delete, then `EntityNotFoundException` is thrown | ✅ Test: `shouldThrowExceptionWhenDeletingNonExistentDecision` |
| AC-5 | All existing tests continue to pass | ✅ 513 tests, 0 failures |

## Code Quality

- **Pattern consistency**: Follows Challenge entity pattern (Story 0024)
- **Immutability**: Project ID not updatable (correct)
- **Error handling**: Consistent with existing `EntityNotFoundException` pattern
- **Test coverage**: 100% of new methods covered

## Findings

No Blocker, Major, or Minor finding.

## Recommendation

**Approved.** Standard CRUD completion following established patterns. No schema changes, no behavioral changes to existing operations.
