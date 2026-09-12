# Story 0121 — DevLog Analysis Communication Prototype

**Status:** IN_PROGRESS  
**Created:** 2026-09-13  
**Author:** opencode (AI coding agent)  
**Scope:** Backend only (Java)

---

## 1. Context

Story 0120 established the `AgentCommunication` boundary (record, port, adapter, intent enum). However, **zero production code invokes it**. Story 0121 is the first real consumer — the analysis domain's first explicit semantic communication.

**Governing ADRs:** ADR-006 (trust governance), ADR-067 (hybrid agent architecture)

---

## 2. Goal

Produce a deterministic, groundable, bounded semantic communication that:
- Surfaces analysis result content (synthesis or insight) to the user
- Communicates **only when meaningful content exists** (silence is valid)
- Never creates or modifies analysis domain state
- Never depends on the `agentpresence` package

---

## 3. Design

### Semantic Selection Logic

```
1. Analysis not completed → SILENCE
2. Synthesis present → extract first meaningful item → COMMUNICATED
3. No synthesis → highest-severity insight → COMMUNICATED  
4. Neither synthesis nor insights → SILENCE
```

### OLED Bounding

- Title: max 50 chars (truncated at composition layer)
- Body: max 200 chars (truncated at composition layer)

### Data Flow

```
AnalysisService.getById() → status check
AnalysisResultQueryService.getResult() → synthesis/insights
AnalysisCommunicationUseCase.compose() → semantic selection + OLED bounding
AgentCommunicationPort.communicate() → Adapter → AgentPresenceMessage
```

### Trust Boundaries

- `AgentCommunication` is a **transport mechanism**, not a trust declaration
- Synthesis (from `AiTask.synthesisSnapshot`) = non-trusted AI output
- Insights (from `Insight` entity) = already promoted trusted knowledge
- Proposals are never communicated

---

## 4. Acceptance Criteria

| AC | Criterion | Status |
|----|-----------|--------|
| AC-1 | First consumer of `AgentCommunicationPort` exists | ✅ |
| AC-2 | Semantic selection: synthesis → insight → silence | ✅ |
| AC-3 | Agent identity = `DEVLOG` (not hardcoded in adapter) | ✅ |
| AC-4 | Intent = `INFORM` only | ✅ |
| AC-5 | OLED bounding: title ≤ 50, body ≤ 200 | ✅ |
| AC-6 | Silence when no meaningful content | ✅ |
| AC-7 | No analysis state mutation | ✅ |
| AC-8 | No `agentpresence` dependency | ✅ |
| AC-9 | 1278/1278 tests pass | ✅ |
| AC-10 | Controller endpoint exposed | ✅ |
| AC-11 | ADR-006 trust boundaries preserved | ✅ |

---

## 5. Implementation Summary

### New Files
- `AnalysisCommunicationUseCase.java` — Semantic composition use case
- `AnalysisCommunicationResponse.java` — COMMUNICATED/SILENCE response DTO
- `AnalysisCommunicationUseCaseTest.java` — 11 focused tests

### Modified Files
- `AnalysisController.java` — Added `POST /{id}/communicate` endpoint
- `AnalysisControllerWebMvcTest.java` — Updated constructor calls (6 occurrences)

### Key Design Decisions

1. **Use case owns composition, not adapter** — AgentCommunicationPort stays generic
2. **No severity → AgentCommunicationIntent mapping** — Intent remains fixed (INFORM)
3. **First synthesis item, not all** — Prevents message flooding
4. **High-severity insight as fallback** — Critical/Warning insights surface first
5. **No domain state mutation** — Pure read + communicate

---

## 6. Test Coverage

- `AnalysisCommunicationUseCaseTest`: 11 tests covering synthesis, insight fallback, silence, truncation, trust boundaries
- `AnalysisControllerWebMvcTest`: Updated to include new dependency (no new tests needed — use case logic is unit tested)
- All 1278 backend tests pass

---

## 7. Story Artifacts

- [ ] repository-analysis.md
- [ ] implementation-plan.md
- [ ] implementation-report.md
- [ ] engineering-report.md
- [ ] code-review.md
- [ ] final-report.md
