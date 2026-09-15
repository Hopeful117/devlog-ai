# Preliminary Product Value Baseline Report v1

`PRELIMINARY - FORMAL ACCEPTANCE BLOCKED`

## Conditions

| Case | Direct repository | DevLog |
|---|---|---|
| CASE-01 | `NOT_EXECUTED` | `EXECUTED_CONTEXT_ONLY`; evidence=8; selected=42; tokens=8080; follow-ups=0 |
| CASE-02 | `NOT_EXECUTED` | `EXECUTED_CONTEXT_ONLY`; evidence=4; selected=41; tokens=8085; follow-ups=0 |
| CASE-03 | `NOT_EXECUTED` | `EXECUTED_CONTEXT_ONLY`; evidence=4; selected=43; tokens=8100; follow-ups=0 |
| CASE-04 | `NOT_EXECUTED` | `EXECUTED_CONTEXT_ONLY`; evidence=3; selected=43; tokens=8088; follow-ups=0 |

## Evaluation

- Evaluator status: `ORACLE_NOT_APPROVED`
- Oracle approval: `NO`
- Human Utility: `NOT_MEASURED`
- Unsupported claim rate: `NOT_MEASURED` (context-only path has no generated claims)
- Evidence stability: `NOT_MEASURED` (single live execution)

## Interpretation

The live path returned bounded agent context, not a benchmark answer. It selected evidence but returned no canonical constraints, causal links or impact identifiers, and reported truncation/budget warnings. This is a preliminary observed limitation, not a formal AI utility score. The direct repository condition was not executed because no isolated direct-agent experiment has been run; the pinned checkout is available for the separate condition.
