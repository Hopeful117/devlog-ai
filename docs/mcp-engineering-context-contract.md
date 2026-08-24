# Engineering Context MCP Contract — `get_engineering_context`

> **Status:** Current (post-audit, story 0087)
> **Scope:** DevLog AI / MCP — contract exposed by tool `get_engineering_context`
> **Reference audit:** `docs/mcp-server-audit.md` (gaps G1/G2)

## Purpose

Document the **actual** contract returned by the MCP tool
`get_engineering_context`, where each piece of information is produced inside
DevLog, and which information is intentionally **not** exposed.

Principle: *the engine is already intelligent — the MCP boundary must stop
losing information DevLog already produces.*

## Flow

```text
MCP request (projectSlug, intent)
      ↓
EngineeringContextTool                     (mcp-server, protocol adapter only)
      ↓ HTTP GET /api/v1/projects/{slug}/engineering-context?intent=…
EngineeringContextController → EngineeringContextFacadeImpl
      ↓
ProjectContextProviderImpl.build()         (project snapshot, capped queries)
RepositoryContextAdapter                   (synthesizes AnalysisContext,
                                            fixed profile engineering-story-v1)
      ↓
RepositoryContextEngine.build()            (ADR-038)
   plan → collectors(6) → rank → budgeted selection
        → symbol enrichment → content enrichment
   diagnostics · warnings · SHA-256 contextDigest
      ↓
internal RepositoryContext                 (deterministic Context result)
      ↓
EngineeringContextContractMapper           ← the ONLY mapping step
      ↓
EngineeringContext                         (devlog-contracts)
      ↓
get_engineering_context response (JSON)
```

## Exposed contract

### EngineeringContext

| Champ | Origine interne | Notes |
|---|---|---|
| `project` | `ProjectContextProviderImpl` + `ProjectContextContractMapper` | identité projet + notes humaines ACTIVE |
| `intent` | requête MCP | repris tel quel |
| `evidence[]` | `RepositoryContext.evidence()` sélectionnée | voir ci-dessous |
| `metadata` | `RepositoryContext` | voir ci-dessous |

### EngineeringEvidence (par item)

| Champ | Origine interne | Notes |
|---|---|---|
| `kind`, `layer`, `summary`, `sourceType`, `originatingFile`, `identifier` | `RepositoryEvidence` + `EvidenceProvenance` | inchangé (V1) |
| `relevanceScore` | `EvidenceScore.finalScore` (0–100) | inchangé |
| `selectionReason` | `SelectionDecision.reason` (`SELECTED_BY_RANK`, `SELECTED_BY_DIVERSITY`, `SELECTED_BY_STRONG_RELEVANCE`) | inchangé |
| `occurredAt` | `RepositoryEvidence.occurredAt()` | timestamp métier de l'item (commit, décision, story…) ; **absent** si la source n'en définit pas — jamais inventé |
| `relatedReferences` | `RepositoryEvidence.relatedReferences()` | liens de provenance : parents git `git:{sourceId}:{sha}`, analyse source d'un insight `analysis:{id}`, commits d'un event |
| `extractionMetadata` | `RepositoryEvidence.extractionMetadata()` | `collectorId`/`collectorVersion` + données d'extraction : `resolvedRevision`, `baseCommit`, `targetCommit`, `storyNumber`, `status`, `category`, `proposalId`, `impact`… |
| `content` | `SelectedFileContentEnricher` → `RepositoryEvidenceContent` | contenu fichier lu, borné ; `null` si non enrichi |
| `symbols` | `SelectedJavaSymbolEnricher` → `RepositoryEvidenceSymbols` | déclarations Java (CLASS/INTERFACE/RECORD/ENUM/ANNOTATION_DECLARATION/CONSTRUCTOR/METHOD) avec localisation ; `null` si non enrichi |
| `resource` | `DevlogResourceUriFactory` (devlog-contracts, source unique partagée avec le mcp-server) | URI MCP de l'artefact **lorsque la correspondance est exacte** (`devlog://projects/{slug}/decisions/{id}`, `…/insights/{id}`, `…/stories/{id}`, `…/engineering-events/{id}`, `…/commits/{sha}`) ; `null` sinon — absence normale, jamais une erreur |

### Navigation resource

```text
get_engineering_context
        ↓
EngineeringEvidence.resource      (ex: devlog://projects/devlog-ai/decisions/{uuid})
        ↓
resources/read                    (URI utilisable telle quelle)
        ↓
full trusted artifact
```

Règles du mapping (déterministe, sans heuristique) :

- **Adressables** : DECISION, INSIGHT (ACTIVE-only des deux côtés), ENGINEERING_STORY, ENGINEERING_EVENT, COMMIT (SHA extrait de la référence interne `git:{sourceId}:{sha}`).
- **Volontairement non adressables** (`resource = null`) : CHANGED_FILE (groupe de diffs agrégé ≠ un commit ; ses `relatedReferences` identifient déjà les commits), CHALLENGE, MILESTONE, ARTIFACT, ANALYSIS, FACT/OBSERVATION, evidences de structure repository — aucune Resource correspondante n'existe.
- Différence avec `relatedReferences` : `resource` désigne **cette evidence** comme artefact lisible ; `relatedReferences` sont des liens de provenance bruts (parents git, analysis source…) non transformés en URIs.

### EngineeringEvidenceContent

| Champ | Signification |
|---|---|
| `status` | `COMPLETE`, `TRUNCATED`, `SKIPPED`, `UNAVAILABLE` (noms internes conservés) |
| `text` | contenu du fichier (borné par la policy de contenu) |
| `reason` | raison machine de l'état (`CONTENT_BUDGET_EXHAUSTED`, `ENRICHED_FILE_LIMIT`, `WORKSPACE_UNAVAILABLE`, …) |
| `revision` | révision Git exacte à laquelle le contenu a été lu |

### EngineeringEvidenceSymbols

| Champ | Signification |
|---|---|
| `status` | `EXTRACTED`, `NO_SUPPORTED_SYMBOLS`, `SKIPPED`, `UNSUPPORTED`, `UNAVAILABLE`, `FAILED` |
| `truncated`, `returnedSymbolCount`, `availableSymbolCount` | complétude de l'extraction |
| `extractorId`, `extractorVersion` | provenance de l'extracteur (`java-declarations`) |
| `revision` | révision Git du fichier analysé |
| `declarations[]` | `kind`, `name`, `owningType`, `modifiers`, `returnType`, `parameters[]`, `annotations[]`, `location{beginLine,endLine,…}` |

### EngineeringContextMetadata

| Champ | Origine | Notes |
|---|---|---|
| `candidateCount`, `selectedCount`, `truncated`, `usedTokens`, `contextDigest` | `RepositoryContext` | inchangés |
| `warnings` | `RepositoryContext.warnings()` | catégories produites par le moteur : `REPOSITORY_CONTEXT_BUDGET_APPLIED`, `EVIDENCE_SUMMARY_TRUNCATED`, `SYMBOL_*`, `CONTENT_ENRICHMENT_*` — aucune catégorie nouvelle n'est créée côté MCP |

## Digest et reproductibilité

Le `contextDigest` est calculé dans `RepositoryContextEngine.digest()` **avant**
le mapping MCP, sur le résultat interne (evidence sélectionnées y compris
contenu/symboles/métadonnées, plan, budget, diagnostics, décisions de
sélection, warnings). Conséquences :

* une évolution du mapping MCP **ne change pas** la sémantique du digest ;
* un changement du résultat interne (nouvelles preuves collectées, autre
  sélection) **change** le digest — c'est voulu : il représente le résultat
  déterministe interne, pas sa représentation JSON.

## Information utilisée mais non exposée (décision documentée)

| Information | Rôle interne | Pourquoi non exposée |
|---|---|---|
| `KnowledgeRelation`s (RESOLVES, CAUSED_BY, …) | graphe entre DECISION/INSIGHT/ENGINEERING_EVENT/CHALLENGE | structure relationnelle, pas une « preuve » ; candidate naturelle pour une future MCP Resource dédiée |
| `ValidatableProposal`s ACCEPTED | intermédiaires de lignée (ADR-006) ; leurs artefacts promus (Decision/Insight/Event) sont déjà exposés | héritage du partage d'`AnalysisContext` avec le workflow IA ; provenance complète future via Resources |
| `KnowledgeEvent`s | journal de curation manuelle | candidat Resource (état projet / timeline), pas une evidence rankable aujourd'hui |
| Facts/Observations | collectés uniquement pendant une Analysis persistée ; vides dans le chemin MCP par construction (`RepositoryContextAdapter`) — utilisés par le workflow IA via `KnowledgeSelectionServiceImpl` | rien à exposer dans le chemin MCP actuel |
| Scores par critère, explications de ranking, diagnostics détaillés, décisions de sélection des items rejetés | auditabilité interne du moteur | volume élevé, valeur de debugging ; réévaluer si un besoin consommateur est démontré |

## Évolution du contrat

L'évolution est **additive** : tous les champs V1 sont inchangés en nom,
position et type ; les nouveaux champs s'ajoutent en fin de record. Les items
non enrichis sérialisent `content: null` / `symbols: null` (absence propre).
Aucun consommateur existant n'est cassé au niveau JSON.
