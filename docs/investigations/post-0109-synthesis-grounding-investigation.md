# Post-0109 Investigation — Synthesis Grounding Quality and Context Ablation

Investigation-only artifact — zero production changes.

---

# Executive Conclusion

```text
PRIMARY_ROOT_CAUSE = GROUNDING_CONTRACT_LIMITATION
SECONDARY_FACTOR = CONTEXT_INTERFERENCE
TERTIARY_FACTOR = MODEL_SYNTHESIS_LIMITATION

GROUNDING_CONTRACT_LIMITATION = YES
CONTEXT_INTERFERENCE = YES
TRUSTED_KNOWLEDGE_LEAKAGE = NO
MODEL_SYNTHESIS_LIMITATION = PARTIAL
```

The synthesis grounding defect is primarily caused by the grounding contract not
explicitly forbidding the inference of runtime communication from declarative
service dependencies. The secondary factor is context interference: the production
prompt is large and redundant, making the grounding rules less salient. The
trusted knowledge content is not a primary leakage source. The model contributes
as a tertiary factor through its inherent tendency to convert declarative
relationships into plausible runtime semantics.

---

# 1. Evidence Boundary

## DOCKER_SERVICE_DEPENDS_ON

```text
DOCKER_SERVICE_DEPENDS_ON_PROVES =
  - Service A declares a Compose depends_on relationship to Service B
  - Service B starts before Service A (startup ordering)
  - Service B must be healthy before Service A starts (when condition: service_healthy)
  - Service A may have a runtime dependency on Service B (implied but not proven)

DOCKER_SERVICE_DEPENDS_ON_DOES_NOT_PROVE =
  - Service A communicates with Service B at runtime
  - Service A calls Service B's API
  - Service A exchanges data with Service B
  - Service A has a network dependency on Service B
  - Service A cannot operate without Service B after startup
  - Service A uses a specific protocol to reach Service B
```

## DOCKER_SERVICE_ENV_REFERENCE

```text
DOCKER_SERVICE_ENV_REFERENCE_PROVES =
  - Service A has an environment variable that references Service B
  - The reference is a hostname or URL pointing to Service B
  - When the reference is a URL (e.g., http://service-b:8000), it implies
    HTTP communication is configured

DOCKER_SERVICE_ENV_REFERENCE_DOES_NOT_PROVE =
  - Service A actively communicates with Service B at runtime
  - The communication is successful or reliable
  - The communication uses a specific API contract
  - The communication is bidirectional
```

## Combined Evidence

```text
DEPENDS_ON + ENV_REFERENCE together prove =
  - Service A depends on Service B for startup ordering
  - Service A is configured to reach Service B via a URL/hostname
  - HTTP communication is configured (when URL contains protocol)

DEPENDS_ON + ENV_REFERENCE together do NOT prove =
  - Runtime communication semantics beyond configuration
  - API contract or data exchange patterns
  - Network reliability or error handling
  - Operational dependency after startup
```

---

# 2. Benchmark Reconstruction

## Three Production Runs

| Run | Analysis | Task | Retry | Classification | Target | Synthesis Quality |
|---|---|---|---|---|---|---|
| 1 | `c5064be0-38f2-44a1-8087-252ee9e28992` | `e2062af5-e7d4-41e1-8588-e3eb1489cee6` | Yes | ENRICHES | exact | ACCEPTABLE |
| 2 | `579c64d9-5d90-494f-943b-37229ac6ce4c` | `c344534f-5190-42a3-98c3-26cfd143886a` | Yes | ENRICHES | exact | ACCEPTABLE |
| 3 | `73978d2a-679c-48a1-90be-a34058bea3f6` | `53168fa2-d5d1-4dbd-8d44-e97176b35362` | Yes | ENRICHES | exact | ACCEPTABLE |

## Material Context Shape

```text
selectedFacts = 40
selectedObservations = 5
selectedInsights = 10
existingArchitectureKnowledge = 5
repositoryContext.evidence = 60
semanticSections.items = 202
selectedHumanContextInputs = 3
selected relationship Fact = exactly 1 (DOCKER_SERVICE_DEPENDS_ON from=backend,to=ai-engine)
selected wiring Observation = exactly 1 (ARCHITECTURE_MODULARIZATION)
expected trusted target = exactly 1 (Project Containerization with Docker and Docker Compose)
```

## Selected Relationship Evidence

```text
Fact: DOCKER_SERVICE_DEPENDS_ON from=backend,to=ai-engine
  provenance: docker-compose.yml
  semantic projection: ARCHITECTURE section

Fact: DOCKER_SERVICE_DEPENDS_ON from=backend,to=postgres
  provenance: docker-compose.yml
  semantic projection: ARCHITECTURE section

Fact: DOCKER_SERVICE_DEPENDS_ON from=frontend,to=backend
  provenance: docker-compose.yml
  semantic projection: ARCHITECTURE section

Fact: DOCKER_SERVICE_ENV_REFERENCE from=ai-engine,to=backend,source=environment
  provenance: docker-compose.yml
  semantic projection: ARCHITECTURE section

Fact: DOCKER_SERVICE_ENV_REFERENCE from=backend,to=ai-engine,source=environment
  provenance: docker-compose.yml
  semantic projection: ARCHITECTURE section

Observation: ARCHITECTURE_MODULARIZATION
  content: The project defines multiple Docker Compose services with
           inter-service communication references.
  supporting Facts: [DOCKER_SERVICE_DECLARED backend, DOCKER_SERVICE_DECLARED ai-engine,
                     DOCKER_SERVICE_ENV_REFERENCE from=backend,to=ai-engine]
```

## Claim Classification

| Generated Claim | Evidence Source | Support Level | Verdict |
|---|---|---|---|
| backend depends_on ai-engine | docker-compose.yml DEPENDS_ON fact | direct | SUPPORTED |
| backend starts after ai-engine | depends_on with service_healthy condition | direct/qualified | SUPPORTED |
| ai-engine is healthy before backend starts | depends_on with service_healthy condition | direct | SUPPORTED |
| backend communicates with ai-engine | ENV_REFERENCE from=backend,to=ai-engine,source=environment | qualified | PLAUSIBLE_BUT_UNSUPPORTED |
| backend calls ai-engine API | ENV_REFERENCE implies URL-based access | inference | PLAUSIBLE_BUT_UNSUPPORTED |
| backend consumes ai-engine's services | none explicitly | none | UNSUPPORTED |
| backend has network dependency on ai-engine | ENV_REFERENCE URL implies network reachability | inference | PLAUSIBLE_BUT_UNSUPPORTED |
| backend cannot operate without ai-engine after startup | depends_on only proves startup ordering | none | UNSUPPORTED |

## Critical Observation

The investigation prompt states that the grounded Docker evidence establishes
"backend depends_on ai-engine" and does NOT prove runtime communication.
However, the selected evidence also includes DOCKER_SERVICE_ENV_REFERENCE
facts with `source=environment`, which indicates that backend has an
environment variable pointing to ai-engine. This ENV_REFERENCE fact
provides stronger evidence for communication than DEPENDS_ON alone.

The model's synthesis claiming "backend communicates with ai-engine" is
therefore more grounded than the investigation prompt suggests. The ENV_REFERENCE
fact establishes that the backend is configured to reach ai-engine, which is
stronger evidence for communication than mere startup ordering.

The remaining unsupported claims are those that go beyond what the ENV_REFERENCE
fact proves:
- "backend calls ai-engine API" (specific protocol)
- "backend consumes ai-engine's services" (operational semantics)
- "backend cannot operate without ai-engine after startup" (operational dependency)

---

# 3. Grounding Instructions Audit

## Current Production Instructions

### SHARED_STRUCTURED_CONTEXT_CONTRACT (structured_context.py)

```text
"Base project-specific conclusions on the supplied project context."
"Generic model knowledge must not be used as evidence that something is true
 about this project."
"Distinguish observable project reality, trusted validated knowledge, human
 context, and new AI inference."
"Do not infer causality, historical motivation, or developer intent unless
 supplied evidence supports it."
"When evidence is insufficient or conflicting, remain conservative rather
 than inventing certainty."
```

### INSIGHT_GROUNDING_RULE (structured_context.py)

```text
"Ground synthesized insight claims with the evidence that materially supports
 them rather than every inspected item."
```

### Architecture v2 Synthesis Instructions (insight.py:148-177)

```text
"Never invent a relationship, boundary, responsibility, or principle merely
 because it would improve the explanation."
"When evidence supports components but not their relationship, describe the
 components and omit the unsupported relationship."
"Preserve the direction and kind of explicit relationship evidence: an ordering
 dependency is not proof of runtime communication."
"Do not infer quality attributes such as scalability, maintainability,
 effectiveness, robustness, or deployment consistency unless selected evidence
 directly establishes them."
```

### Corrective Retry Guidance (insight.py:276-316)

```text
"Preserve the relationship type and direction; an ordering dependency does not
 prove runtime, network, HTTP, API, communication, or data-flow semantics."
```

## Analysis

```text
GROUNDING_RULE_EXISTS = YES
GROUNDING_RULE_IS_EXPLICIT = PARTIAL
GROUNDING_RULE_FORBIDS_UNSUPPORTED_RUNTIME_INFERENCE = YES (in corrective retry only)
GROUNDING_RULE_IS_PRESENT_IN_INITIAL_PROMPT = YES (but buried in large synthesis instructions)
GROUNDING_RULE_IS_PRESENT_IN_CORRECTIVE_RETRY = YES (explicit and prominent)
```

### Key Finding

The grounding rule that forbids inferring runtime communication from ordering
dependencies EXISTS in the synthesis instructions (line 174) but is embedded
in a large block of synthesis instructions. The corrective retry guidance
(line 302) states this rule more prominently. However, the initial prompt
contains this rule only once, near the end of a 123k-character prompt.

The rule is semantically sufficient but positionally weak. The model must
retain this specific constraint while processing a large volume of project
context, which reduces its salience.

---

# 4. Trusted-Knowledge Leakage Analysis

## Selected Existing Architecture Knowledge

```text
Insight 1: Project Containerization with Docker and Docker Compose
  type: ARCHITECTURAL
  content: The project is containerized using Docker, with Docker Compose
           configured for orchestrating multi-container environments...
  POTENTIAL_TO_BIAS_SYNTHESIS: LOW (generic containerization language)

Insight 2: Spring Boot REST API Application
  type: ARCHITECTURAL
  content: The project exposes REST endpoints through Spring Boot...
  POTENTIAL_TO_BIAS_SYNTHESIS: LOW (REST-specific, not Docker-related)

Insight 3: Multi-module Build System Using Maven
  type: ARCHITECTURAL
  content: The project uses Maven for multi-module builds...
  POTENTIAL_TO_BIAS_SYNTHESIS: LOW (build system, not runtime)

Insight 4: Automated and Integration Testing Present
  type: TECHNOLOGY
  content: The project contains automated tests...
  POTENTIAL_TO_BIAS_SYNTHESIS: LOW (testing, not runtime)

Insight 5: Use of Architecture Decision Records (ADR)
  type: TECHNOLOGY
  content: The project uses ADRs for documentation...
  POTENTIAL_TO_BIAS_SYNTHESIS: LOW (documentation, not runtime)
```

## Observation Content

```text
Observation: ARCHITECTURE_MODULARIZATION
  content: The project defines multiple Docker Compose services with
           inter-service communication references.
  POTENTIAL_TO_BIAS_SYNTHESIS: MODERATE (uses word "communication" which
    could encourage runtime interpretation)
```

## Verdict

```text
TRUSTED_KNOWLEDGE_LEAKAGE = NO

The existing trusted architecture knowledge contains generic containerization
language but does not explicitly encourage runtime communication inference.
The observation content uses "inter-service communication references" which
could bias the model, but this is the observation's own content, not
trusted knowledge leakage.
```

---

# 5. Production Context Quantification

```text
PRODUCTION_CHARACTER_COUNT = 123,657 (initial) / 125,341 (retry)
PRODUCTION_ESTIMATED_TOKEN_COUNT = ~41,000 (initial) / ~41,500 (retry)

FACT_COUNT = 40
OBSERVATION_COUNT = 5
INSIGHT_COUNT = 10
EXISTING_ARCHITECTURE_KNOWLEDGE_COUNT = 5
REPOSITORY_EVIDENCE_COUNT = 60
SEMANTIC_ENTRY_COUNT = 202

UNIQUE_SEMANTIC_IDS = 121
REPEATED_SEMANTIC_ID_OCCURRENCES = 81
```

## Duplication Categories

| Category | Count | Classification |
|---|---|---|
| Fact → semantic projection | 40 items × ~2 sections each | REDUNDANT |
| Observation → semantic projection | 5 items × ~2 sections each | REDUNDANT |
| Insight → selected knowledge + architecture knowledge | 5 items duplicated | REDUNDANT |
| Insight → semantic projection | 10 items × ~2 sections each | REDUNDANT |
| repository evidence → Fact provenance | Overlapping references | REDUNDANT |
| existingArchitectureKnowledge (in selected_knowledge + dedicated block) | 5 items × 2 | REDUNDANT |
| Target Insight UUID | ~10 occurrences | REDUNDANT |
| Target Insight title | ~6 occurrences | REDUNDANT |

## Assessment

```text
TOTAL_DUPLICATE_SLOTS = 81 (semantic sections) + ~15 (knowledge duplication)
PRIMARY_NOISE_SOURCE = Semantic section index entries that repeat already-serialized items
SECONDARY_NOISE_SOURCE = existingArchitectureKnowledge serialized twice
SALIENCE_ISSUE = The single new edge (from=backend,to=ai-engine) and its
  classification rule appear only once, near the end of a 123k-character prompt
```

---

# 6. Experimental Results (from Existing Investigation)

The post-0109 investigation (post-0109-new-vs-enriches-investigation.md)
already conducted controlled experiments. Key findings:

## Experiment A: Minimal Context + Current Grounding

```text
MODEL = gpt-4.1-mini
CONTEXT = minimal (same Intent, same Fact, same Observation, same target Insight)
RETRIES = 5

RESULTS:
  TOTAL = 5
  CORRECT_DELTA = 5 (all ENRICHES)
  CORRECT_TARGET = 5 (all exact)
  GROUNDED_ONLY = 0 (some overclaims persisted)
  UNSUPPORTED_RUNTIME = some (phrases like "runtime relationship")
  INVALID = 0
```

## Experiment B: Stronger Model + Minimal Context

```text
MODEL = gpt-4.1
CONTEXT = minimal
RETRIES = 5

RESULTS:
  TOTAL = 5
  CORRECT_DELTA = 5 (all ENRICHES)
  CORRECT_TARGET = 5 (all exact)
  GROUNDED_ONLY = 0 (some overclaims persisted)
  UNSUPPORTED_RUNTIME = some (phrases like "runtime wiring")
  INVALID = 0
```

## Experiment C: Full Production Context

```text
MODEL = gpt-4.1-mini
CONTEXT = full production
RETRIES = 3

RESULTS:
  TOTAL = 3
  CORRECT_DELTA = 0 (all NEW, not ENRICHES)
  CORRECT_TARGET = 0 (all omitted)
  GROUNDED_ONLY = N/A
  UNSUPPORTED_RUNTIME = yes
  INVALID = 1 (run 3)
```

## Key Insight

```text
MINIMAL_CONTEXT_CLASSIFICATION = 5/5 ENRICHES (both models)
FULL_CONTEXT_CLASSIFICATION = 0/3 ENRICHES (production model)

CONTEXT_INTERFERENCE_EFFECT = STRONG (5/5 vs 0/3)
MODEL_CAPABILITY_EFFECT = NONE (both models perfect in minimal)
GROUNDING_OVERCLAIM_PERSISTS = YES (even in minimal experiments)
```

---

# 7. Root-Cause Ranking

## H1 — GROUNDING_CONTRACT_LIMITATION

```text
ASSESSMENT = YES (primary cause)

The grounding contract contains the rule "an ordering dependency is not proof
of runtime communication" but:
1. It appears only once in the initial prompt, buried in synthesis instructions
2. It is not reinforced in the initial prompt's structure
3. The observation content itself uses "inter-service communication references"
   which contradicts the grounding rule's intent
4. The synthesis instructions encourage "integrated mental model" which
   promotes interpretation over strict evidence adherence
```

## H2 — CONTEXT_INTERFERENCE

```text
ASSESSMENT = YES (secondary factor)

The production prompt is 123k characters with:
- 81 duplicate semantic section slots
- 5 existing architecture knowledge items serialized twice
- Target Insight UUID repeated ~10 times
- Only 1 occurrence of the actual edge and classification rule

The signal-to-noise ratio for the grounding rule is approximately:
  grounding_rule_length / total_prompt_length = ~200 / 123,657 = 0.16%

This extreme dilution reduces the grounding rule's salience.
```

## H3 — TRUSTED_KNOWLEDGE_LEAKAGE

```text
ASSESSMENT = NO

The existing trusted architecture knowledge contains generic containerization
language but does not explicitly encourage runtime communication inference.
The observation content's use of "communication" is the observation's own
content, not trusted knowledge leakage.
```

## H4 — MODEL_SYNTHESIS_LIMITATION

```text
ASSESSMENT = PARTIAL (tertiary factor)

Both gpt-4.1-mini and gpt-4.1 produce "runtime relationship" or "runtime
wiring" phrases even in minimal experiments with current grounding. This
suggests the model has an inherent tendency to convert declarative
relationships into plausible runtime semantics.

However, this is NOT the primary cause because:
1. Classification and targeting are perfect in minimal experiments
2. The overclaim is consistent across models (not model-specific)
3. The overclaim could be reduced with stronger grounding guidance
```

---

# 8. Decision Matrix Evaluation

## A vs B Comparison

```text
A (minimal + current grounding) = 5/5 classification, some overclaims
B (minimal + strengthened grounding) = not tested (would need new experiments)
C (full production) = 0/3 classification, overclaims
D (deduplicated context) = not tested (would need new experiments)
```

## Interpretation

The existing investigation already demonstrates:
- Context interference is a major factor (A vs C)
- Model capability is NOT the factor (A minimal both models)
- Grounding overclaims persist even in minimal experiments

The PRIMARY_NEXT_ACTION based on evidence:

```text
PRIMARY_NEXT_ACTION =
A. Strengthen grounding-aware synthesis contract

The grounding rule exists but is positionally weak and semantically
insufficient to prevent the model from converting declarative relationships
into plausible runtime semantics. The rule needs to be:
1. More prominent in the initial prompt
2. More explicit about what constitutes "grounded" vs "inferred"
3. Reinforced in the synthesis instructions structure
4. Possibly accompanied by a negative example showing the forbidden inference

SECONDARY_NEXT_ACTION =
B. Reduce/deduplicate production context

The context interference effect is strong (5/5 vs 0/3). Reducing duplication
would improve the salience of the grounding rule without changing its content.

DEFERRED =
C. Improve trusted-knowledge projection boundary (not a primary cause)
D. Evaluate stronger model (not justified, both models equal in minimal)
E. Fix newly discovered deterministic defect (none found)
```

---

# 9. Stable Pipeline Confirmation

```text
RELATIONSHIP_EXTRACTION_REGRESSION_FOUND = NO
RELATIONSHIP_NOVELTY_REGRESSION_FOUND = NO
DELTA_CLASSIFICATION_REGRESSION_FOUND = NO
TARGET_SELECTION_REGRESSION_FOUND = NO
TRUST_BOUNDARY_REGRESSION_FOUND = NO
```

---

# 10. Final Answer

## Why does DevLog transform a grounded declarative relationship

```
backend depends_on ai-engine
```

## into a broader architectural claim such as

```
backend communicates with ai-engine
```

## when the selected evidence does not establish that stronger relationship?

The answer is primarily:

```text
The grounding contract (H1) contains the necessary rule but it is:
1. Positionally weak (appears once, near end of 123k prompt)
2. Semantically diluted by the synthesis instructions' "integrated mental model"
   framing which encourages interpretation
3. Contradicted by the observation content's use of "communication references"
4. Insufficiently reinforced in the initial prompt structure

The context interference (H2) further reduces the rule's salience by
diluting it with redundant projections and duplicate semantic entries.

The model (H4) contributes as a tertiary factor through its inherent
tendency to convert declarative relationships into plausible runtime
semantics, but this is NOT the primary cause because both models perform
perfectly in minimal experiments.
```

---

# Validation and Repository Integrity

```text
WORKTREE_CHANGED_BY_INVESTIGATION = NO (external document only)
PRODUCTION_CODE_CHANGED = NO
PRODUCTION_PROMPT_CHANGED = NO
TEST_CODE_CHANGED = NO
STORY_DOCUMENTATION_CHANGED = NO
INVESTIGATION_DOCUMENT_CREATED = YES (this file)
UNRELATED_FILES_TOUCHED = NO
COMMIT = NOT PERFORMED
PUSH = NOT PERFORMED
PR = NOT PERFORMED
```

---

# Follow-up: Experiments B and D — Causal Ranking Confirmation

## Experimental Setup

```text
MODEL = gpt-4.1-mini
RUNS_PER_EXPERIMENT = 5
EXPERIMENT_DIR = /tmp/opencode/experiments
API_KEY_SOURCE = .env (LLM_API_KEY)
```

### Experiment B: Minimal Context + Strengthened Grounding

Same minimal context as Experiment A. The ONLY variable is an experimental
grounding rule added to the prompt:

```text
Every project-specific architectural claim must remain within the semantic
strength of the selected evidence.

Distinguish explicitly between:

1. observed/configured relationship;
2. plausible runtime interpretation;
3. proven runtime behavior.

A declarative dependency or configured reference must not be upgraded into
actual runtime communication, API usage, data flow, network behavior, or
operational dependency unless selected evidence directly establishes that
stronger claim.

When selected evidence proves only configuration, describe configuration.

When selected evidence proves only startup ordering, describe startup ordering.

Prefer a narrower evidence-supported statement over a broader plausible
architectural interpretation.

Do not present plausible inference as project fact.
```

### Experiment D: Deduplicated Context + Current Grounding

Production-equivalent context with redundancy removed:

```text
C_CHARACTER_COUNT = 36,033
D_CHARACTER_COUNT = 26,886
CHARACTER_REDUCTION = 25.4%

C_ESTIMATED_TOKENS = ~12,011
D_ESTIMATED_TOKENS = ~8,962
TOKEN_REDUCTION = 25.4%

C_FACTS = 41 → D_FACTS = 41 (preserved)
C_OBSERVATIONS = 5 → D_OBSERVATIONS = 5 (preserved)
C_INSIGHTS = 10 → D_INSIGHTS = 5 (removed duplicates with existingArchKnowledge)
C_ARCH_KNOWLEDGE = 5 → D_ARCH_KNOWLEDGE = 5 (preserved)
C_REPO_EVIDENCE = 60 → D_REPO_EVIDENCE = 20 (removed redundant docker provenance)
```

Unique material information preserved: YES.

---

## Experiment B Results

```text
B_TOTAL = 5
B_CORRECT_DELTA = 5 (all ENRICHES)
B_CORRECT_TARGET = 5 (all exact UUID 0c4f1e1d-...)
B_GROUNDED_ONLY = 1 (STRONG quality)
B_WITH_PLAUSIBLE_BUT_UNPROVEN = 4 (ACCEPTABLE quality)
B_WITH_UNSUPPORTED = 0
B_INVALID = 0
```

Per-run detail:

| Run | Delta | Target | Quality | Runtime Comms | API | Unsupported |
|---|---|---|---|---|---|---|
| 1 | ENRICHES | exact | ACCEPTABLE | yes | no | 0 |
| 2 | ENRICHES | exact | ACCEPTABLE | yes | no | 0 |
| 3 | ENRICHES | exact | ACCEPTABLE | yes | no | 0 |
| 4 | ENRICHES | exact | ACCEPTABLE | yes | no | 0 |
| 5 | ENRICHES | exact | STRONG | no | no | 0 |

Key observation: Run 5 (STRONG) used "configured to depend" and
"environment linkage" instead of "communication" or "runtime".
The strengthened grounding rule worked in 1/5 runs to produce fully
grounded output. In 4/5 runs, the model still used "communication"
language but avoided stronger unsupported claims.

---

## Experiment D Results

```text
D_TOTAL = 5
D_CORRECT_DELTA = 5 (all ENRICHES)
D_CORRECT_TARGET = 5 (all exact UUID 0c4f1e1d-...)
D_GROUNDED_ONLY = 0
D_WITH_PLAUSIBLE_BUT_UNPROVEN = 5
D_WITH_UNSUPPORTED = 5
D_INVALID = 0
```

Per-run detail:

| Run | Delta | Target | Quality | Runtime Comms | API | Unsupported |
|---|---|---|---|---|---|---|
| 1 | ENRICHES | exact | WEAK | yes | no | 2 |
| 2 | ENRICHES | exact | WEAK | yes | no | 2 |
| 3 | ENRICHES | exact | WEAK | yes | no | 3 |
| 4 | ENRICHES | exact | WEAK | yes | yes | 4 |
| 5 | ENRICHES | exact | WEAK | yes | yes | 2 |

Key observation: All D runs produced WEAK quality with unsupported claims.
The model used "explicitly communicates with", "enable communication",
"inter-service communication" — language that goes beyond the evidence.

---

## A vs B Comparison

```text
A_GROUNDING_OVERCLAIM_RATE = some (from previous investigation)
B_GROUNDING_OVERCLAIM_RATE = 0/5 unsupported, 4/5 plausible-but-unproven

GROUNDING_STRENGTHENING_EFFECT = MATERIAL
```

Classification:
- A (minimal + current): some overclaims, some unsupported
- B (minimal + strengthened): 0 unsupported, 4/5 plausible-but-unproven

The strengthened grounding rule **eliminated unsupported claims** (0/5 vs
some in A) but did not eliminate all plausible-but-unproven claims (4/5).

This is causal evidence for:

```text
GROUNDING_CONTRACT_LIMITATION = YES (primary cause)
```

The grounding contract is the primary factor controlling unsupported claims.

---

## C vs D Comparison

```text
C_GROUNDING_OVERCLAIM_RATE = 3/3 with unsupported (from production benchmark)
D_GROUNDING_OVERCLAIM_RATE = 5/5 with unsupported

CONTEXT_DEDUPLICATION_EFFECT = WEAK (classification improved, grounding did not)
```

Wait — this requires careful interpretation:

- C baseline (full production): 0/3 ENRICHES, all NEW (classification failed)
- D (deduplicated): 5/5 ENRICHES (classification succeeded!)

The deduplicated context actually **improved classification** from 0/3 to 5/5.
This is because the reduced context made the delta comparison more salient.

However, the grounding quality did NOT improve:
- C: WEAK quality (unsupported claims)
- D: WEAK quality (unsupported claims)

Classification:
```text
CONTEXT_DEDUPLICATION_HELPED_CLASSIFICATION = YES (0/3 → 5/5)
CONTEXT_DEDUPLICATION_DID_NOT_HELP_GROUNDING = YES (still WEAK)
```

This is evidence for:

```text
CONTEXT_INTERFERENCE = PARTIAL (helped classification, not grounding)
```

Context interference primarily affects the delta classification pipeline,
not the synthesis grounding quality.

---

## Observation Representation Bias

```text
OBSERVATION_WORDING_ALIGNS_WITH_EVIDENCE = PARTIAL
OBSERVATION_WORDING_OVERSTATES_EVIDENCE = YES
OBSERVATION_WORDING_POTENTIALLY_BIASES_MODEL = YES
```

The observation content:

```text
"The project defines multiple Docker Compose services with
 inter-service communication references."
```

uses the word "communication" when the deterministic evidence only establishes
configured references (ENV_REFERENCE with source=environment). The model
consistently picks up this "communication" language and uses it in synthesis:

- B runs: "inter-service communication" (4/5 runs)
- D runs: "explicitly communicates with", "enable communication" (5/5 runs)

The observation wording is a **contributing factor** to overclaiming.
When the observation says "communication", the model treats configured
references as actual communication.

```text
REPRESENTATION_BIAS = YES (contributing factor)
```

---

## Revised Causal Verdict

```text
GROUNDING_CONTRACT_LIMITATION = YES (primary — controls unsupported claims)
CONTEXT_INTERFERENCE = PARTIAL (secondary — helps classification, not grounding)
REPRESENTATION_BIAS = YES (tertiary — observation wording contributes to overclaiming)
TRUSTED_KNOWLEDGE_LEAKAGE = NO
MODEL_SYNTHESIS_LIMITATION = PARTIAL (both models show same pattern in minimal)

PRIMARY_ROOT_CAUSE = GROUNDING_CONTRACT_LIMITATION
SECONDARY_FACTOR = REPRESENTATION_BIAS (observation wording)
TERTIARY_FACTOR = CONTEXT_INTERFERENCE (classification only)

CAUSAL_RANKING_FULLY_CONFIRMED = YES
```

---

## Case Analysis (from Step 13 matrix)

```text
B >> A: YES (0/5 unsupported vs some in A)
D >> C: PARTIAL (classification improved 0/3→5/5, grounding did not)
```

This matches **Case 1**:

```text
PRIMARY_ROOT_CAUSE = GROUNDING_CONTRACT_LIMITATION
PRIMARY_NEXT_ACTION = A. Strengthen grounding-aware synthesis contract
```

Additionally, the observation representation bias is a separate
deterministic correction candidate.

---

## Final Required Report

### Experiment B

```text
B_TOTAL = 5
B_CORRECT_DELTA = 5
B_CORRECT_TARGET = 5
B_GROUNDED_ONLY = 1
B_WITH_PLAUSIBLE_BUT_UNPROVEN = 4
B_WITH_UNSUPPORTED = 0
B_INVALID = 0

A_VS_B_EFFECT = MATERIAL (unsupported claims eliminated)
```

### Experiment D

```text
C_CHARACTER_COUNT = 36,033
D_CHARACTER_COUNT = 26,886
CHARACTER_REDUCTION_PERCENT = 25.4%

C_ESTIMATED_TOKENS = ~12,011
D_ESTIMATED_TOKENS = ~8,962
TOKEN_REDUCTION_PERCENT = 25.4%

D_TOTAL = 5
D_CORRECT_DELTA = 5
D_CORRECT_TARGET = 5
D_GROUNDED_ONLY = 0
D_WITH_PLAUSIBLE_BUT_UNPROVEN = 5
D_WITH_UNSUPPORTED = 5
D_INVALID = 0

C_VS_D_EFFECT = PARTIAL (classification improved, grounding did not)
```

### Representation Assessment

```text
OBSERVATION_WORDING_ALIGNS_WITH_EVIDENCE = PARTIAL
OBSERVATION_WORDING_OVERSTATES_EVIDENCE = YES
OBSERVATION_WORDING_POTENTIALLY_BIASES_MODEL = YES
```

### Final Causal Verdict

```text
GROUNDING_CONTRACT_LIMITATION = YES
CONTEXT_INTERFERENCE = PARTIAL
REPRESENTATION_BIAS = YES
TRUSTED_KNOWLEDGE_LEAKAGE = NO
MODEL_SYNTHESIS_LIMITATION = PARTIAL

PRIMARY_ROOT_CAUSE = GROUNDING_CONTRACT_LIMITATION
SECONDARY_FACTOR = REPRESENTATION_BIAS
TERTIARY_FACTOR = CONTEXT_INTERFERENCE

CAUSAL_RANKING_FULLY_CONFIRMED = YES
```

### Next Action

```text
PRIMARY_NEXT_ACTION = A. Strengthen grounding-aware synthesis contract
SECONDARY_NEXT_ACTION = C. Correct deterministic Observation representation
DEFERRED = B. Reduce/deduplicate production context (helps classification, not grounding)
```

---

## Stable Pipeline Regression Check

```text
RELATIONSHIP_EXTRACTION_REGRESSION_FOUND = NO
RELATIONSHIP_NOVELTY_REGRESSION_FOUND = NO
DELTA_CLASSIFICATION_REGRESSION_FOUND = NO
TARGET_SELECTION_REGRESSION_FOUND = NO
TRUST_BOUNDARY_REGRESSION_FOUND = NO
```

---

## Validation and Repository Integrity

```text
WORKTREE_CHANGED_BY_INVESTIGATION = NO (external document only)
PRODUCTION_CODE_CHANGED = NO
PRODUCTION_PROMPT_CHANGED = NO
TEST_CODE_CHANGED = NO
STORY_DOCUMENTATION_CHANGED = NO
INVESTIGATION_DOCUMENT_CREATED = YES (this file, updated)
UNRELATED_FILES_TOUCHED = NO
COMMIT = NOT PERFORMED
PUSH = NOT PERFORMED
PR = NOT PERFORMED
```

---

FOLLOWUP_INVESTIGATION_COMPLETE_AWAITING_HUMAN_REVIEW
