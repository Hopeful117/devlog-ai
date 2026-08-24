# Story 0089 — Repository Analysis

## Where the pieces live (verified on merged main @ 4d16138)

- Contract: `devlog-contracts/…/engineeringcontext/EngineeringEvidence.java`
  (fields end at `symbols`).
- Mapping to contract: backend
  `EngineeringContextContractMapper.toContract(projectContext,
  repositoryContext, intent)` — receives the full `ProjectContextSnapshot`
  whose `project().slug()` is available (no extra lookup needed, §9 satisfied).
- Resource definitions: mcp-server `@McpResource` URI templates (Story 0088):
  decisions/{decisionId}, insights/{insightId}, stories/{storyId},
  engineering-events/{eventId}, commits/{commitSha} — all under
  `devlog://projects/{projectSlug}/…`.
- Engine digest: computed inside `RepositoryContextEngine.digest()` **before**
  contract mapping, over the internal result only → adding a contract field
  cannot change it.

## Real evidence shapes per collector (contract-visible fields)

| kind | layer | contract `identifier` | internal `reference` | produced by |
|---|---|---|---|---|
| DECISION | ADR | `<uuid>` | `decision:{uuid}` | ProjectKnowledgeContextCollector |
| INSIGHT | VALIDATED_INSIGHT | `<uuid>` | `insight:{uuid}` | id. (source: ACTIVE-only query in RepositoryContextAdapter) |
| MILESTONE | ROADMAP | `<uuid>` | `milestone:{uuid}` | id. |
| ENGINEERING_STORY | ROADMAP | `<uuid>` (+ metadata storyNumber/status/baseCommit/targetCommit) | `story:{uuid}` | id. |
| ARTIFACT | PROJECT_DOCUMENTATION | `<uuid>` | `artifact:{uuid}` (+ originatingFile) | id. |
| ANALYSIS | PREVIOUS_ANALYSIS / CURRENT_ANALYSIS | `<uuid>` | `analysis:{uuid}` | id. / CurrentAnalysisContextCollector |
| CHALLENGE | ROADMAP | `<uuid>` (+ metadata status/impact) | `challenge:{uuid}` | id. (Story 0087) |
| ENGINEERING_EVENT | GIT_HISTORY | `<uuid>` (+ metadata category/baseCommit/targetCommit/proposalId; relatedReferences = git refs of base/target commits) | `event:{uuid}` | id. (Story 0087) |
| COMMIT | GIT_HISTORY | **null** (SHA not exposed in identifier) | `git:{sourceId}:{sha}` (+ parent refs in relatedReferences) | GitHistoryContextCollector |
| CHANGED_FILE | COMMIT_DIFF | `commit-diff:{path}` (+ metadata resolvedRevision on structure files; relatedReferences = `diff:{hash}:{path}` list) | `diff:{sha}:{path}` | CommitDiffEvidenceCollector |
| FACT/OBSERVATION | varies | fact/observation ids or paths | `fact:` / `observation:` / plain path | DeterministicKnowledgeContextCollector (**not emitted on the MCP path**: facts/observations empty by construction) |
| MODULE/SOURCE_DIRECTORIES/TEST_DIRECTORIES/CONFIGURATION_FILES/FILE_EXTENSIONS/MODULE_SUMMARY/SOURCE_FILE/TEST_FILE/CONFIG_FILE | RELATED_SOURCE_CODE | path- or name-based (`file:`, `module:`, …) + metadata resolvedRevision | same shape | RepositoryStructureCollector |

## Direct-mapping matrix (decision)

| Evidence kind | Resource (0088) | Direct mapping | Rule |
|---|---|---|---|
| DECISION | decisions/{id} | **yes** | identifier IS the decision UUID |
| INSIGHT | insights/{id} | **yes** | identifier IS the insight UUID; both sides enforce ACTIVE-only (adapter query ↔ resource governance) — no weakening |
| ENGINEERING_STORY | stories/{id} | **yes** | identifier IS the story UUID |
| ENGINEERING_EVENT | engineering-events/{id} | **yes** | identifier IS the event UUID (validated events only) |
| COMMIT | commits/{sha} | **yes** (Case A) | the evidence IS the commit; SHA recovered deterministically from the internal reference `git:{sourceId}:{sha}` (40/64 hex). The resource returns DevLog's context of exactly that SHA |
| CHANGED_FILE | — | **no** (Case B) | aggregated file-change group across several commits ≠ a commit context. Stays null; its `relatedReferences` already identify the involved commits for future indirect navigation |
| MILESTONE / ARTIFACT / ANALYSIS / FACT / OBSERVATION / CHALLENGE | none | no | no corresponding resource exists (§17/§18) |
| Repository structure kinds | none | no | live filesystem views, no resource |

Never fuzzy: unknown kind, unparsable reference or invalid UUID ⇒ `resource =
null` (safe absence), never an error.

## Slug availability

`ProjectContextSnapshot.project().slug()` is already loaded by the facade —
the mapper reads it directly; zero additional lookups regardless of evidence
count.

## Module-boundary decision (§7/§8)

URI construction centralized in `DevlogResourceUriFactory` placed in
**devlog-contracts** (`com.hopeful117.devlogai.contracts.engineeringcontext`):

- single source of truth shared by the backend mapper and mcp-server tests;
- pure static string building (deterministic, no I/O, no business logic);
- contracts gain a string-pattern convention, not an MCP runtime dependency;
- `@McpResource` annotations keep literal templates (Java annotation constants)
  — a mcp-server test asserts factory output ⇄ template equality to prevent
  drift.
