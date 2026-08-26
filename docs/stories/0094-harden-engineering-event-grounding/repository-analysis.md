# Repository Analysis — Story 0094

## 1. DevLog MCP findings

- `get_engineering_context(devlog-ai, intent="repair engineering-event AI
  grounding reliability and error diagnostics")`: freshness CURRENT (all three
  checkpoints `491d0cf`), warnings REPOSITORY_CONTEXT_BUDGET_APPLIED +
  CONTENT_ENRICHMENT_TRUNCATED; evidence layers git-only (GIT_HISTORY 14,
  COMMIT_DIFF 45, RELATED_SOURCE_CODE 1); zero evidence mentioning grounding —
  not useful for this defect.
- `search_project_history`: "analysis context grounding closure" → Story 0042
  commits (`e6a50232`, `61e1c79 fix(context): close analysis grounding facts`)
  — genuinely useful prior-art pointer. Queries with event-prompt vocabulary
  ("supportingFactIds", "corrective retry") return 0.
- Source inspection still required for prompt/service internals (expected).

## 2. Failure reproduction (before any change)

Stack already running `main @ 491d0cf` (ai-engine image current — no diff vs
main). Three live `POST /engineering-event-executions` runs:

| Run | Analysis | Outcome | failure_code / message |
|---|---|---|---|
| 1 (`c994bde6…`) | `8370002d…` | COMPLETED | — |
| 2 (`bbf87e70…`) | `3ff4f50e…` | **FAILED** | INVALID_LLM_OUTPUT / Unknown supportingFactIds |
| 3 (`3e12f91e…`) | `f0495e45…` | **FAILED** | INVALID_LLM_OUTPUT / Unknown supportingFactIds |

Both failures: `attempt_count=1`, `prompt_version=analyze-engineering-event-
prompt-v1`, callback proposalCount=0. Matches historical 4F/4C. Reproduction ✓.

## 3. Current prompt anatomy (`prompts/engineering_event.py`)

Sections: BUSINESS INTENT → UNTRUSTED SELECTED KNOWLEDGE (full blob) →
GROUNDING CONTRACT (**bare JSON** of three allow-lists) → USER GUIDANCE →
OUTPUT CONTRACT. System message says "Never invent identifiers or references".

The knowledge blob itself carries competing identifiers:
`evolutionContext.projectId`, `.sourceId`, `.analysisId`, commit hashes,
diff paths — none marked as non-citable.

## 4. Grounding discipline comparison

| Grounding rule | insight.py | engineering_event.py |
|---|---:|---:|
| Copy IDs exactly | ✅ explicit prose | ❌ header only |
| Explicit allow-list | ✅ JSON contract | ⚠️ JSON present, unlabeled semantics |
| IDs elsewhere are invalid | ✅ explicit prose | ❌ |
| Empty list allowed | ✅ stated (+schema nuance) | ❌ |
| Never derive/shorten/extend | ✅ | ❌ |
| Corrective retry restates rules + names offenders | ⚠️ restates via error text; insight service lists offending IDs in error | ❌ opaque "Unknown supportingFactIds" |

## 5. Validation & retry mechanics

- `_generate` (service) checks subset per proposal per field and raises on the
  FIRST violation, message without values.
- Schema rule verified (`schemas/engineering_event.py`):
  `require_grounding` rejects a proposal whose fact+observation+evidence
  fields are ALL empty ⇒ "empty list allowed" must be stated as: empty
  supportingFactIds valid only when observations or evidenceReferences ground it.
- Service second-attempt handler catches bare `Exception` → always
  `INVALID_LLM_OUTPUT` (provider timeouts misclassified).
- Insight service is the known-good pattern: retry except split into
  output-invalid vs provider error; `_require_subset` names offending values.

## 6. Versioning conventions

`prompt_template` string originates in backend `IntentCatalog`
(`analyze-engineering-event-prompt-v1`) and is asserted by the builder for
identity consistency; changing it requires a backend change. Precedent:
insight.py hardened its grounding prose in place under the same template id;
reproducibility is carried by persisted `prompt_content_digest`. Conclusion:
keep template id; rely on digest (documented decision).

## 7. Backend compatibility verification

No backend Java/DB change needed: the failure contract
(`AiTaskResultError.code/message`) already supports both codes; backend
validator untouched; intent snapshot untouched.
