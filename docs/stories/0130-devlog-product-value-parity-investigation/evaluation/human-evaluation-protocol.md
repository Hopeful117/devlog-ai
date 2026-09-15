# Human Evaluation Protocol v1

## Purpose

Measure whether DevLog reduces the effort required by a human engineer without
showing the oracle in advance or replacing human judgment with an LLM judge.

## Controls

- Use the pinned Trading OS revision and the same four frozen questions.
- Use equivalent starting knowledge in both conditions.
- Randomize condition order where practical to reduce learning effects.
- Do not show `ground-truth-v1.md` before both conditions are complete.
- A facilitator records actions; the participant does not edit the oracle.
- CASE-04 is a negative control and is not counted as a positive usefulness
  case for the subjective threshold.

## Conditions

### DIRECT_REPOSITORY

The participant may use Git, repository search, file reading, ADRs, Stories and
tests. Record every search, opened file, command, navigation step and elapsed
time from question disclosure to submitted answer.

### DEVLOG

The participant may use the human-facing DevLog UI and documented REST
capabilities available to that UI. Do not grant hidden MCP-only access. Record
every query, opened result, expansion, follow-up search and elapsed time.

## Per-Case Record

```text
caseId =
condition = DIRECT_REPOSITORY | DEVLOG
timeToAnswerSeconds = PENDING_HUMAN_MEASUREMENT
searchCount = PENDING_HUMAN_MEASUREMENT
filesOpened = PENDING_HUMAN_MEASUREMENT
interactions = PENDING_HUMAN_MEASUREMENT
evidenceLocated = []
constraintsLocated = []
causalLinksLocated = []
incorrectClaims = []
answerSubmitted = YES | NO
answerUseful = YES | NO
nonObviousValue = YES | NO
wouldUseDevlogForThisTask = YES | NO | NOT_APPLICABLE
notes =
```

The facilitator maps the submitted answer to canonical oracle identifiers after
the session. Time and effort fields must not be estimated retrospectively.

## Human PASS

The positive set is CASE-01..03. DevLog passes only if:

- its answer correctness is no lower than the direct condition;
- the median DevLog time is at least 30% lower than the direct median;
- at least 2 of 3 positive cases receive `WOULD_USE_DEVLOG_FOR_THIS_TASK=YES`;
- the participant can identify evidence supporting the answer;
- unsupported causal confidence does not count as usefulness.

If the participant reports that DevLog is correct but not useful, the case
fails subjective utility. Qualitative feedback is retained as evidence, not
converted into a numeric score by an AI system.
