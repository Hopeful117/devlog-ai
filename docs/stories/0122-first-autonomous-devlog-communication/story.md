# Story 0122 — First Autonomous DevLog Communication

**Status:** IN_PROGRESS
**Created:** 2026-09-13
**Author:** opencode (AI coding agent)
**Scope:** Backend only (Java)

---

## 1. Context

Stories 0120 and 0121 established the `AgentCommunication` boundary and the first explicit semantic communication (`AnalysisCommunicationUseCase`). However, **zero production code invokes it autonomously**. Story 0121's endpoint `POST /api/v1/analyses/{id}/communicate` is manually triggered — no component decides "should DevLog communicate with the human?" Story 0122 is the smallest possible autonomous slice: after a completed Analysis, the system evaluates whether to speak.

**Governing ADRs:** ADR-006 (trust governance), ADR-067 (hybrid agent architecture)

---

## 2. Goal

Introduce the first autonomous communication decision:
- After an Analysis completes, evaluate whether it exposes meaningful communicable content
- If communicable content exists → SPEAK (delegate to existing `AnalysisCommunicationUseCase`)
- If no communicable content → SILENCE (do nothing)
- Communication failure MUST NOT affect analysis completion
- No LLM call — deterministic evaluation only

---

## 3. Design

### Communication Decision

```
Analysis COMPLETED → evaluateCommunicableContent → SPEAK | SILENCE
```

### Decision Logic

```
1. Synthesis present with meaningful items → SPEAK
2. No synthesis → highest-severity insight with title+content → SPEAK
3. Neither synthesis nor insights → SILENCE
```

### Wake-Up Seam

After `finishAnalysis()` in `AiTaskResultServiceImpl.handle()` — the exact moment analysis becomes COMPLETED.

### Transaction Isolation

```java
private void evaluateAndCommunicate(UUID analysisId) {
    try {
        CommunicationDecision decision = communicationDecisionService.evaluate(analysisId);
        if (decision == CommunicationDecision.SPEAK) {
            analysisCommunicationUseCase.execute(analysisId);
        }
    } catch (Exception e) {
        log.warn("Autonomous communication failed for analysis {}; analysis completion unaffected: {}",
                analysisId, e.getMessage());
    }
}
```

Spring only rolls back on uncaught exceptions, so this try-catch ensures transaction isolation.

---

## 4. Acceptance Criteria

| # | Criterion | Verified |
|---|-----------|----------|
| AC1 | `CommunicationDecision` enum exists with `SPEAK` and `SILENCE` values | YES |
| AC2 | `CommunicationDecisionService` exists and has `evaluate(analysisId)` method | YES |
| AC3 | After `finishAnalysis()` with `AnalysisStatus.COMPLETED`, wake-up occurs | YES |
| AC4 | Wake-up evaluates communicable content | YES |
| AC5 | `AgentCommunicationPort.communicate()` never called directly from completion | YES |
| AC6 | Manual `/communicate` endpoint preserved | YES |
| AC7 | `CommunicationDecisionServiceTest` exists with SPEAK and SILENCE tests | YES |
| AC8 | Communication failure does not fail analysis completion | YES |
| AC9 | No proposals communicated | YES |
| AC10 | No agent loop, no scheduler, no orchestrator, no broker, no polling | YES |
| AC11 | Silence is a valid outcome | YES |
| AC12 | Agent identity "DEVLOG", intent INFORM only | YES |
| AC13 | No frontend changes, no Docker changes | YES |
| AC14 | Full backend test suite passes (1284 tests) | YES |

---

## 5. Files Changed

| File | Change |
|------|--------|
| `backend/.../analysis/communication/CommunicationDecision.java` | NEW — SPEAK/SILENCE enum |
| `backend/.../analysis/communication/CommunicationDecisionService.java` | NEW — evaluates communicable content |
| `backend/.../ai/engine/service/AiTaskResultServiceImpl.java` | MODIFIED — injected decision service, added wake-up after finishAnalysis() |
| `backend/.../analysis/communication/CommunicationDecisionServiceTest.java` | NEW — 6 tests |
| `backend/.../ai/engine/service/AiTaskResultServiceTest.java` | MODIFIED — added mocks for new dependencies |

---

## 6. Test Results

```
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
-- CommunicationDecisionServiceTest
Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
-- AiTaskResultServiceTest
Tests run: 1284, Failures: 0, Errors: 0, Skipped: 0
-- Full backend suite
BUILD SUCCESS
```

---

## 7. Decision Log

| Decision | Rationale |
|----------|-----------|
| Deterministic evaluation | V1: no LLM call, simplest possible decision |
| Try-catch isolation | Transaction isolation: communication failure must not rollback analysis completion |
| New service, not extending existing | Keeps communication decision logic separate from analysis lifecycle |
| Reuse existing AnalysisCommunicationUseCase | Avoids duplicating synthesis→insight→silence composition logic |
| No generic event architecture | V1: simplest possible wake-up, no abstraction overkill |
