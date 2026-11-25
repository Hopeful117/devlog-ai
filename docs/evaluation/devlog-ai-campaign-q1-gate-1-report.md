# Campaign Q1 Technical Gate 1 - Report

## Identity

| Field | Value |
|-------|-------|
| Artifacts | `data/devlog-ai-campaign-q1-gate-1/` |
| Manifest | `devlog-ai-abc-campaign` 1.0.0 (digest `8bff8a65...6dfe`), case batch Q1 |
| Runtime revision | `2994ec0a` (pushed, Core-verified) |
| Frozen source revision | `0673d1d3` |
| Core plan / authorization | `461dbe0c...` READY / `9f3cfc76...` APPROVED then CONSUMED |
| Status | `CAMPAIGN_LIVE_EXECUTION_PARTIAL` |
| Matrix | 9 assigned in batch: 3 EXECUTED (A), 6 INVALID (B x3, C x3) |

The whole flow was driven through MCP: preparation, authorization request and
approval, freshness gate, case Story resolution, consumption and execution.
The baseline refresh preceding it also went through MCP (Stories 0240, 0241).

## Results per condition

### A - DevLog current (3/3 technically COMPLETED)
- The real frozen question and the accepted Q1 scope were received.
- All three repetitions abstained honestly: summary `NOT_ESTABLISHED`,
  confidence `LOW`, with 1 to 4 grounded claims each.
- A cited the right files, which are also the oracle evidence files:
  `AnalyzeStoryContextUseCase`, `DevLogStoryAgentService`,
  `story_context_analysis_generation_service.py` and the prompts/schemas. But
  it reports that "the retained evidence omits the method-level content"
  needed to answer.
- Accounting: 60 of about 1100 candidates selected, `truncated=true`, and only
  4392 of the 12000-token budget used. The limit is therefore not the token
  budget but missing file content (Core logs: ADR-063/066/067 skipped as
  "exceeds per-document limit", `CONTENT_ENRICHMENT_TRUNCATED`).
- Freshness: `FRESH` / `CURRENT` (Story 0238 + 0241).
- **Revision defect:** A ran on `7ecc1680` (source current revision), not on the
  frozen `0673d1d3`. A has no revision check, so the mismatch is silent.

### B - Direct investigator (3/3 PROVIDER_FAILURE)
- The remote provider returned HTTP 409 `CREDENTIAL_BINDING_RESOLUTION_FAILED`
  for task `5fa2a3e7...`.
- Cause: `/home/ludo/workspace/devlog-ai/.env` still sets a stale
  `DEVLOG_LIVE_B_TASK_ID` from an earlier session. The harness then reuses it
  instead of reserving a fresh binding per authorization, and that binding no
  longer exists in the AI Engine. This is an operational configuration issue,
  and also a harness weakness: the override should not exist for LIVE.

### C - DevLog engineering analyst (3/3 RUNTIME_FAILURE)
- `Q1_SOURCE_REVISION_MISMATCH`: Core resolved the source to its current
  revision `7ecc1680`, while the campaign is frozen at `0673d1d3`.
- Run 28 passed only because `main` was still at `0673d1d3` then.

## Findings

1. **Main reliability finding (epic objective 3).** With the right question and
   scope, DevLog selects the right evidence but does not deliver its content,
   so the best answer it can give is an honest abstention. Honesty is good
   (3/3 abstained, LOW confidence, no invented flow). Usefulness for this
   architecture question is nil.
2. **Revision pinning is missing for Story Context executions.** A and C always
   run on the source's current revision. A campaign frozen on a revision
   cannot stay valid once `main` moves. C fails closed; A drifts silently.
3. **Per-case single consumption consumed the Q1 slot with a technically failed
   batch.** Under the `devlog-ai-abc-campaign` contract, Core will refuse a
   second Q1 consumption. A technical gate should not use the official
   campaign contract.
4. **Stale B binding override** in `.env` (operational).

## Not evaluated

- Semantic scoring against the oracle: A abstained, B and C produced no answer.
