# Implementation Plan — Story 0122

**Branch:** `story/0122-first-autonomous-devlog-communication`
**Base:** `main` at `8017288`

---

## 1. Files to Create

| File | Purpose |
|------|---------|
| `backend/.../analysis/communication/CommunicationDecision.java` | SPEAK/SILENCE enum |
| `backend/.../analysis/communication/CommunicationDecisionService.java` | Evaluates communicable content |
| `backend/.../analysis/communication/CommunicationDecisionServiceTest.java` | 6 tests |

## 2. Files to Modify

| File | Change |
|------|--------|
| `backend/.../ai/engine/service/AiTaskResultServiceImpl.java` | Add imports, inject new dependencies, add `evaluateAndCommunicate()` |
| `backend/.../ai/engine/service/AiTaskResultServiceTest.java` | Add mocks for new dependencies |

---

## 3. Implementation Steps

1. Create `CommunicationDecision` enum (SPEAK, SILENCE)
2. Create `CommunicationDecisionService` with `evaluate(analysisId)` method
3. Inject `CommunicationDecisionService` and `AnalysisCommunicationUseCase` into `AiTaskResultServiceImpl`
4. Add `evaluateAndCommunicate()` private method with try-catch isolation
5. Call `evaluateAndCommunicate()` after `finishAnalysis()` on COMPLETED path
6. Add mocks to `AiTaskResultServiceTest`
7. Create `CommunicationDecisionServiceTest` (6 tests)
8. Run focused tests → full backend verification

---

## 4. Verification

```bash
# Focused
./backend/mvnw -pl backend test -Dtest=CommunicationDecisionServiceTest
./backend/mvnw -pl backend test -Dtest=AiTaskResultServiceTest

# Full suite
./backend/mvnw -pl backend -am clean verify -B
```
