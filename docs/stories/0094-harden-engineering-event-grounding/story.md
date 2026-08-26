# Story 0094 — Harden Engineering Event AI Grounding Contract and Failure Diagnostics

## Status

**READY_FOR_COMMIT_APPROVAL**

## Objective

Make Engineering Event proposal generation reliably obey the exact grounding
contract and produce honest, diagnostic failures when it does not:

```text
allowed grounding IDs → prompt → LLM → exact-copy IDs → strict validation
    ↘ invalid → corrective retry naming offending IDs + restated allow-list
        ↘ still invalid → INVALID_LLM_OUTPUT (precise diagnostics)
        ↘ provider/network failure → LLM_PROVIDER_ERROR (truthful)
```

## Problem

The Cockpit "Analyze evolution" flow fails chronically with:

```text
INVALID_LLM_OUTPUT — Unknown supportingFactIds
```

Historical `ai_tasks` for `analyze-engineering-event-prompt-v1`: 4 FAILED / 4
COMPLETED (~50%). Live reproduction on `main @ 491d0cf` (Story 0094
investigation phase): runs 2 and 3 of 3 failed identically.

Source: investigation *Human vs Agent Context Capability in DevLog*
(`docs/investigations/human-vs-agent-context-capability.md`, findings §Workflow B).

Root cause chain (all code-verified):

1. `engineering_event.py` embeds a UUID-dense SelectedKnowledge blob whose
   unrelated identifiers (`evolutionContext.projectId/sourceId/analysisId`,
   commit hashes) are indistinguishable from valid citation targets;
   its "GROUNDING CONTRACT" section is a bare JSON dump with **none** of the
   copy-exact discipline that `insight.py` carries.
2. `_generate` raises `"Unknown supportingFactIds"` without naming offenders.
3. `corrective_retry` appends only that opaque string — no offending IDs, no
   restated allow-list.
4. The service's second-attempt handler catches bare `Exception` and files
   provider timeouts/connection errors as `INVALID_LLM_OUTPUT`
   (`engineering_event_generation_service.py:34`), corrupting failure
   statistics and diagnostics.

## Resolution

AI Engine only (backend untouched, schema untouched, validator strictness
unchanged):

1. **Grounding contract in prompt**: explicit copy-exact rules + clearly
   separated allow-lists (`ALLOWED_SUPPORTING_FACT_IDS: [...]` etc.), stating
   that unrelated IDs appearing elsewhere remain invalid, and the precise
   domain rule for empty lists (a proposal must keep at least one grounding
   element across fact/observation/evidence fields).
2. **Diagnostic validation**: collect every violation per proposal with the
   offending values listed (`supportingFactIds contains references absent from
   the allowed list: [...]`).
3. **Diagnostic corrective retry**: retry message names offending IDs,
   restates the allow-lists inline, and repeats the copy-exact instruction.
4. **Honest error mapping**: second-attempt exceptions split into output-invalid
   vs provider failure, mirroring the proven insight-generation pattern.
5. **Prompt version**: template identifier remains
   `analyze-engineering-event-prompt-v1` because it is backend-owned intent
   identity (`IntentCatalog`) asserted by the builder; content reproducibility
   is carried by the per-call `prompt_content_digest` (same convention as the
   insight prompt's earlier in-place hardening). Decision documented in
   implementation-plan §Versioning.

## Scope

### IN SCOPE

- `ai-engine/app/prompts/engineering_event.py`
- `ai-engine/app/services/engineering_event_generation_service.py`
- Deterministic tests: prompt contract, allowed-ID matrix, retry diagnostics,
  persistent invalidity, provider-failure classification

### OUT OF SCOPE

Backend Java/schema · MCP tools/resources · context ranking/budget ·
RepositoryContextAdapter · freshness/synchronization · documentation
generation · Cockpit UI · insight.py semantics (unchanged; small duplication
preferred over abstraction) · LLM provider choice · generic New Analysis UI.

## Non-Negotiable Invariants

1. Backend grounding validation stays strict; unknown IDs are always rejected.
2. A proposal requires ≥1 grounding element overall (existing schema rule).
3. Corrective retry is bounded at one (existing architecture).
4. `INVALID_LLM_OUTPUT` never masks provider failures.
5. Diagnostics contain only model-repair-relevant data (no secrets/internal dumps).
6. Insight generation behavior and tests remain green.

## References

- Investigation: docs/investigations/human-vs-agent-context-capability.md
- Story 0042 — analysis context grounding closure (prior art)
- Known-good discipline: `ai-engine/app/prompts/insight.py`
