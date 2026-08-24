# DevLog AI — Audit technique du serveur MCP et de `get_engineering_context`

> **Mise à jour (2026-08-24)** : les gaps G1/G2 décrits ci-dessous ont été traités par la story
> `docs/stories/0087-expose-repository-context-information-through-mcp/`. Le contrat actuel est
> documenté dans `docs/mcp-engineering-context-contract.md`. Ce rapport est conservé comme
> instantané de l'état avant correction.

- **Date** : 2026-08-24
- **Nature** : audit lecture seule — aucun tool/resource/prompt créé, aucun comportement fonctionnel modifié
- **Question fondatrice** : que sait réellement faire `get_engineering_context`, que faudrait-il exposer comme MCP Resources, et quelles capacités manquent réellement ?
- **Méthode** : lecture complète du module `mcp-server`, du contrat `devlog-contracts`, du pipeline backend (`engineeringcontext` → `projectcontext` → `repositorycontext`), des sous-systèmes temporels (`temporal`, `lineage`, `projectfreshness`, `projectunderstanding`, `timeline`, `history`) et de gouvernance (`proposal`, `validation`, `insight`, `knowledge`, `story`) ; exécution des tests existants pour confirmation.
- **Tests exécutés** : `mcp-server` — 8/8 verts ; `backend` ciblé — `EngineeringContextControllerWebMvcTest`, `RepositoryContextEngineTest`, `DeterministicEvidenceRankerTest`, `BudgetedDiverseEvidenceSelectorTest` — 17/17 verts. Le test contrôleur confirme notamment qu'un appel sans `intent` produit un **500** (`MissingServletRequestParameterException`, observé dans les logs de test).

---

## 1. Executive Summary

1. **`get_engineering_context` n'est pas un moteur de recherche** : c'est l'extrémité MCP d'un véritable **moteur de composition de contexte déterministe** (`RepositoryContextEngine`, ADR-038) qui planifie, collecte, score, sélectionne sous budget et enrichit des preuves multi-couches, avec digest SHA-256 et diagnostics. La couche MCP elle-même est mince (un adapter HTTP vers le backend).

2. **Le pipeline est nettement plus capable que ce que le contrat MCP expose.** Trois pertes majeures ont été identifiées :
   - L'enrichissement **contenu fichier** et **symboles Java** (budgeté, calculé à chaque appel par `SelectedFileContentEnricher` / `SelectedJavaSymbolEnricher`) est **entièrement jeté** par `EngineeringContextContractMapper` : `EngineeringEvidence` n'a pas de champ content/symbols.
   - Les timestamps, références liées, métadonnées d'extraction (dont `storyNumber`, `baseCommit`, `targetCommit`, `resolvedRevision`), scores par critère, warnings et diagnostics sont perdus en route.
   - Cinq collections chargées dans le snapshot projet (**EngineeringEvents validés, Challenges, KnowledgeRelations, ValidatedProposals, KnowledgeEvents**) ne sont émises par **aucun collector** : invisibles via MCP.
   - De plus, `DeterministicKnowledgeContextCollector` est du **code mort dans le chemin MCP** : l'`AnalysisContext` synthétisé passe toujours `facts=[]`, `observations=[]`.

3. **La sémantique d'intent est partiellement exploitée** : le texte d'intent influence la scoring lexical (doublement : via `objective` et via `UserGuidance.focus`) mais **jamais le choix du profil** — tout appel MCP utilise le profil figé `engineering-story-v1`. Il n'y a aucune recherche vectorielle ; la « relevance » est lexicale/règle/temporelle déterministe.

4. **Réponses aux trois questions de la mission** :
   - *Que sait faire `getContext` ?* → construire un contexte borné (~6000 tokens), scoré, justifié (`selectionReason`), multi-sources (commits, diffs agrégés, décisions, insights validés, stories, milestones, artifacts, structure repo), avec budget et digest — mais sans contenu de fichier exposé, sans timestamp, sans relations.
   - *Quoi exposer en Resources ?* → les artefacts adressables déjà modélisés : Decision/ADR, Insight, EngineeringStory, EngineeringEvent, Commit context, Timeline, Freshness, liste des projets. `devlog://projects/{slug}/context` existe déjà ; c'est le bon pattern.
   - *Quels tools manquent ?* → principalement une **recherche historique ciblée** (`search_project_history`) qui nécessite de nouvelles requêtes repository (aucune query sur messages/chemins de commits n'existe). `explainDecision`/`traceHistory` seraient des vues spécialisées de capacités existantes, pas des moteurs nouveaux.

5. **Frontière respectée** : le chemin MCP est lecture seule côté DevLog (aucune écriture DB, aucun appel IA). Seule exception opérationnelle : `WorkspaceManager.synchronize()` exécute de vraies commandes Git (fetch/checkout) sur le workspace interne pendant la collecte — coût/latence à documenter.

---

## 2. Current MCP Architecture

### 2.1 Module `mcp-server`

Application Spring Boot séparée (`mcp-server/pom.xml` : parent Boot 4.1.0, BOM Spring AI 2.0.0, `spring-ai-starter-mcp-server`, dépendance au module partagé `devlog-contracts`, `spring-boot-starter-restclient`). Java 21.

Transport et configuration (`mcp-server/src/main/resources/application.properties`) :

```properties
spring.ai.mcp.server.name=devlog-mcp
spring.ai.mcp.server.version=0.1.0
spring.ai.mcp.server.type=SYNC
spring.ai.mcp.server.stdio=true
devlog.backend.base-url=${DEVLOG_BACKEND_BASE_URL:http://localhost:18080}
```

- Transport **STDIO synchrone** ; logs dirigés vers stderr uniquement (`logback-spring.xml`, appender `System.err`) — stdout réservé au protocole (commit `a000048`, test `StdioProtocolHygieneTest`).
- Client backend : `DevlogBackendClientConfiguration` construit un proxy `@HttpExchange` (`DevlogProjectContextClient`) sur RestClient, base-url configurable.
- Aucune config client MCP committée : `.ai/mcp/mcp.json` est vide (0 octet).
- Enregistrement confirmé par exécution de `McpServerApplicationTests` : `Registered tools: 2`, `resources: 1`, `resource templates: 1`, `prompts: 1`.

### 2.2 Surface MCP actuelle

| Type | Nom | Entrée | Sortie | Service interne appelé | Utilité réelle |
|---|---|---|---|---|---|
| Tool | `get_engineering_context` (`EngineeringContextTool.java:16`) | `projectSlug*`, `intent*` | JSON `EngineeringContext` (string) | HTTP GET `/api/v1/projects/{slug}/engineering-context?intent=` → `EngineeringContextFacadeImpl` | Capacité centrale, réellement utilisée par les workflows DevLog (`docs/stories/0077…0079/*/repository-analysis.md` documentent des appels réels) |
| Tool | `echo_message` (`EchoTool.java:10`) | `message*` | message inchangé | aucun | Diagnostic de connectivité |
| Resource | `devlog://server/info` (`ServerInfoResource.java:8`) | — | JSON statique `{name, version, status}` | aucun | Health/description serveur |
| Resource template | `devlog://projects/{projectSlug}/context` (`ProjectContextResource.java:24`) | `projectSlug` | JSON `ProjectContext` | GET `/api/v1/projects/{slug}/context` → `ProjectContextController` → `ProjectContextProviderImpl.build` + `ProjectContextContractMapper` | Lecture projet (identité + notes humaines ACTIVE, max 10) |
| Prompt | `explain_code` (`ExplainCodePrompt.java:9`) | `language*` | template texte générique | aucun | Démo ; aucune donnée DevLog |

DTO partagés (`devlog-contracts/src/main/java/com/hopeful117/devlogai/contracts/`) :
`ProjectContext{id,name,slug,description,status,notes[]}`, `ProjectNote{id,type,title,contentMarkdown,status,updatedAt}`, `EngineeringContext{project,intent,evidence[],metadata}`, `EngineeringEvidence{kind,layer,summary,sourceType,originatingFile,identifier,relevanceScore,selectionReason}`, `EngineeringContextMetadata{candidateCount,selectedCount,truncated,usedTokens,contextDigest}`.

### 2.3 Vue d'architecture

```text
MCP Client (IDE agent, OpenCode, Kiko…)
    │  stdio JSON-RPC (SYNC)
    ▼
MCP Server (module mcp-server, Spring AI MCP)
    ├── Tool  get_engineering_context ──┐
    ├── Tool  echo_message              │ HTTP (@HttpExchange)
    ├── Resource devlog://server/info   │ base-url = DEVLOG_BACKEND_BASE_URL
    ├── Resource devlog://projects/{slug}/context
    └── Prompt explain_code             │
                                        ▼
                        DevLog Backend (REST adapter)
                             ├── EngineeringContextController      (GET …/engineering-context)
                             ├── ProjectContextController          (GET …/context)
                             ▼
                    Application Services
                             ├── ProjectService.getBySlug
                             ├── ProjectContextProviderImpl.build          (snapshot projet)
                             └── RepositoryContextAdapter                  (synthèse AnalysisContext)
                                        ▼
                          RepositoryContextEngine (ADR-038)
                             plan → collectors(6) → ranker → selector
                                   → symbol enricher → content enricher
                             diagnostics · warnings · SHA-256 digest
                                        ▼
     Domain / Projections / Knowledge / Git history
     (Decisions, Insights ACTIVE, Stories, Milestones, Artifacts,
      ProjectCommit + ChangedFile, workspace Git synchronisé,
      Human Context Inputs)
```

**Frontière MCP** : conforme à `docs/mcp-architecture-context.md` et ADR-056/057 — le serveur MCP ne touche ni la base ni Git ; il ne fait qu'adapter deux endpoints REST. Toute l'intelligence est dans la couche application backend.

---

## 3. getContext End-to-End Flow

Flux complet depuis l'appel MCP (chaque étape vérifiée dans le code) :

```text
MCP request (stdio)
  ↓ EngineeringContextTool.getEngineeringContext()            (mcp-server)
    sérialise la réponse en JSON string ; aucune validation ni gestion d'erreur
  ↓ DevlogProjectContextClient.getEngineeringContext()        HTTP GET
  ↓ EngineeringContextController.getEngineeringContext()      (backend)
  ↓ EngineeringContextFacadeImpl.getEngineeringContext()
      1. projectService.getBySlug(projectSlug)
         → EntityNotFoundException → GlobalExceptionHandler → 404 ENTITY_NOT_FOUND
      2. projectContextProvider.build(projectId)
         → ProjectContextProviderImpl : ~13 requêtes repositories plafonnées
           (constantes MAX_RECENT_EVENTS=20, MAX_VALIDATED_PROPOSALS=20,
            MAX_ARCHITECTURE_ARTIFACTS=20, MAX_ARCHITECTURE_DECISIONS=20,
            MAX_RECENT_MILESTONES=10, MAX_RELATED_ANALYSES=∞ (triées),
            MAX_VALIDATED_ENGINEERING_EVENTS=10, MAX_OPEN_CHALLENGES=20,
            MAX_KNOWLEDGE_RELATIONS=50, MAX_ENGINEERING_STORIES=20,
            MAX_HUMAN_CONTEXT_INPUTS=10)
         → ProjectContextSnapshot
      3. repositoryContextAdapter.buildRepositoryContext(projectId, intent, snapshot)
         a. synthesizeAnalysisContext() :
            - facts=[], observations=[], evolutionContext=null
            - AnalysisSnapshot synthétique : id = UUID.nameUUIDFromBytes(projectId),
              type=ARCHITECTURE_REVIEW, intentId="engineering-story-preparation",
              status=COMPLETED, startedAt/createdAt = Instant.now()
            (bypass documenté de KnowledgeSelectionServiceImpl, javadoc
             RepositoryContextAdapter.java:22-30)
         b. createIntentDefinition(intent) :
            IntentDefinition(id="engineering-story-preparation", objective=intent,
              constraints=["deterministic evidence only"],
              promptTemplate="engineering-story-context-v1",
              contextProfiles=["engineering-story-v1"])   ← profil FIGÉ
         c. createGuidance(intent) :
            UserGuidance(focus=intent, audience="kiko", levelOfDetail="focused",
              writingStyle="analytical", outputContext="engineering-story-preparation",
              priorities=[])
         d. insightRepository.findByProjectIdAndStatusIn([ACTIVE])  ← non borné
         e. repositoryContextService.build(context, intent, guidance, insights)
  ↓ RepositoryContextEngine.build()                            (cœur du moteur)
      plan → collect → rank → select → enrich(symbols) → enrich(content)
      → tri → diagnostics → warnings → digest SHA-256
  ↓ EngineeringContextContractMapper.toContract()
      PROJECTION PERDANTE vers le contrat MCP (voir §9)
  ↓ HTTP 200 EngineeringContext
  ↓ EngineeringContextTool → objectMapper.writeValueAsString() → MCP result
```

**Responsabilités réelles par couche**

| Couche | Responsabilité effective |
|---|---|
| `mcp-server` | pur adaptateur protocole (zéro logique métier) — conforme aux docs d'architecture |
| Controller/Facade backend | résolution slug→projectId, orchestration snapshot+moteur, mapping contrat |
| `ProjectContextProviderImpl` | lecture plafonnée de l'état connaissances du projet |
| `RepositoryContextAdapter` | pont story-spécifique→générique : synthèse d'un contexte d'analyse fictif |
| `RepositoryContextEngine` | planification de profil, collecte, ranking, sélection budgétée, enrichissement, diagnostics |
| Contract mapper | aplatissement (perteux) vers `EngineeringContext` |

---

## 4. getContext Inputs

### 4.1 Paramètres exposés

| Paramètre | Obligatoire | Défaut | Rôle | Impact | Validation |
|---|---|---|---|---|---|
| `projectSlug` | oui | — | identifiant stable du projet | résolu en projectId ; sinon 404 | aucune côté MCP ; 404 backend si inconnu |
| `intent` | oui | — | texte libre décrivant l'objectif d'ingénierie | alimente `objective` + `UserGuidance.focus` → scoring lexical ; devient le summary de la preuve CURRENT_ANALYSIS | aucune ; **absent ⇒ HTTP 500** (testé, `shouldReturnStatus500WhenIntentQueryParameterIsMissing`) |

C'est tout. **Aucun** paramètre optionnel n'existe aujourd'hui.

### 4.2 Concepts internes existants NON exposés (vrais noms du code)

| Concept du code | Où | Exposé via MCP ? |
|---|---|---|
| Context Profiles (`engineering-story-v1`, `architecture-v1`, `history-v1`, `project-state-v1`, `documentation-v1`, `release-v1`, `knowledge-extraction-v1`) | `DeterministicContextIntelligence.profiles()` | Non — MCP force toujours `["engineering-story-v1"]` |
| Layers (`RepositoryContextLayer`: CURRENT_ANALYSIS, RELATED_SOURCE_CODE, GIT_HISTORY, COMMIT_DIFF, ADR, ROADMAP, VALIDATED_INSIGHT, PREVIOUS_ANALYSIS, PROJECT_DOCUMENTATION) | enum homonyme | Partiellement (nom de layer par evidence) |
| Context Budget (`max-evidence-items:60`, `max-summary-characters:500`, `max-history-items:20`, `max-tokens:6000`) | `application.properties` backend | Non (seul `usedTokens` ressort) |
| Evidence Precision Policy (`maximumCommonTermPercentage=50, minimumRelevanceScore=35, maximumKindSharePercentage=25, strongRelevanceScore=75`) | `EvidencePrecisionPolicy`, appliqué au profil story | Non |
| UserGuidance structuré (focus/audience/levelOfDetail/writingStyle/outputContext/priorities) | `UserGuidance` | Non — l'intent remplit uniquement `focus` |
| Filtres par artifact/source type, plage temporelle, mode de retrieval | n'existent pas | — |

---

## 5. Context Sources

Six collectors (`repositorycontext/collector/`, interface `RepositoryContextCollector`, ordonnés par `@Order`) produisent des `RepositoryEvidence` :

| # | Collector | Source de données | Layer / Kind produits | Format de `reference` | Bornes |
|---|---|---|---|---|---|
| 1 | `CurrentAnalysisContextCollector` (@Order 10) | AnalysisContext synthétique | CURRENT_ANALYSIS / ANALYSIS | `analysis:{uuidSynthétique}` | 1 item ; summary = texte de l'intent |
| 2 | `DeterministicKnowledgeContextCollector` (@Order 20) | `analysisContext.facts()` + `.observations()` | FACT/OBSERVATION selon chemin (ADR si `adr-` ou `/decisions/`, ROADMAP si `roadmap`, DOC si `.md`) | `fact:{id}`, chemin fichier, `observation:{id}` | **Code mort dans le chemin MCP** : facts/observations toujours vides (adapter, args 4-5 = `List.of()`) |
| 3 | `GitHistoryContextCollector` (@Order 30) | `ProjectCommitRepository.findByProjectIdOrderByCommittedAtDescCommitHashDesc` | GIT_HISTORY / COMMIT | `git:{sourceId}:{commitHash}` ; parents → `git:{sourceId}:{parentHash}` | top 20 commits récents (`max-history-items`) |
| 4 | `CommitDiffEvidenceCollector` (@Order 35) | `ChangedFile` des commits ≤ 90 jours (fenêtre `window-days` depuis startedAt) | COMMIT_DIFF / CHANGED_FILE | `diff:{hash}:{path}` | max 50 items ; exclusions binaires/vendored/generated ; regroupement par chemin avec cumuls +/- et nombre de commits |
| 5 | `ProjectKnowledgeContextCollector` (@Order 40) | Snapshot projet + Insights ACTIVE (requête dédiée de l'adapter) | ADR/DECISION (`decision:{uuid}`), ROADMAP/MILESTONE (`milestone:{uuid}`), VALIDATED_INSIGHT/INSIGHT (`insight:{uuid}`), PREVIOUS_ANALYSIS/ANALYSIS (`analysis:{uuid}`), PROJECT_DOCUMENTATION/ARTIFACT (`artifact:{uuid}`), ROADMAP/ENGINEERING_STORY (`story:{uuid}`) | voir référence | decisions/milestones/artifacts/stories plafonnés par le snapshot ; **Insights ACTIVE non plafonnés** |
| 6 | `RepositoryStructureCollector` (@Order 40) | **Scan live du workspace Git synchronisé** (`WorkspaceManager.synchronize(source,null)` → `SecureRepositoryScanner.scan`) | RELATED_SOURCE_CODE / MODULE_SUMMARY, SOURCE_DIRECTORIES, TEST_DIRECTORIES, CONFIGURATION_FILES, FILE_EXTENSIONS, MODULE, SOURCE_FILE, TEST_FILE, CONFIG_FILE | `file:{path}`, `config:{path}`, `module:{name}`, … | 40 items file-level max ; tri par nb de termes de l'intent contenus dans le chemin (`storyTermMatches`) ; metadata `resolvedRevision` |

**Sources portées mais jamais émises en evidence** (chargées dans `ProjectContextSnapshot`/`AnalysisContext`, aucun collector ne les consomme — vérifié par grep sur `repositorycontext/`) :

- `validatedEngineeringEvents` (avec `baseCommit`/`targetCommit`/`occurredAt` — la seule passerelle commit↔connaissance validée !)
- `openChallenges`
- `knowledgeRelations` (graphe RESOLVES/CAUSED_BY/RELATES_TO/DERIVED_FROM/ADDRESSES/INFORMED_BY)
- `validatedProposals`
- `recentKnowledgeEvents`

Seules les `humanContextInputs` ressortent, mais hors evidence : via `ProjectContext.notes` dans l'en-tête du contrat (`ProjectContextContractMapper.toContract`).

**Enrichissement post-sélection** (calculés puis…) :
- `SelectedJavaSymbolEnricher` : déclarations Java (nom, type propriétaire, paramètres, annotations) pour ≤6 fichiers sélectionnés, budgets `SYMBOL_MAX_*`.
- `SelectedFileContentEnricher` : contenu fichier réel lu via `SecureRepositoryContentReader` (≤6 fichiers, 4000 caractères/fichier, 12000 total), statuts INCLUDED/TRUNCATED/SKIPPED/UNAVAILABLE avec raisons.
- **Les deux payloads sont absents du contrat MCP** (voir §9).

---

## 6. Retrieval / Ranking / Selection

Pipeline exact (`RepositoryContextEngine.build()`, RepositoryContextEngine.java:67-114) :

```text
ContextIntelligence.plan(context, intent)          → ContextPlan (profil, poids, layers préférés,
                                                     diversité minimale, precision policy, explications)
        ↓
collectors.forEach(collect(request))               → List<RepositoryEvidence> candidates
        ↓
DeterministicEvidenceRanker.rank(candidates, req)  → scored+sorted candidates
        ↓
BudgetedDiverseEvidenceSelector.select(ranked,req) → selected + SelectionDecision(*) + usedTokens
        ↓
SelectedJavaSymbolEnricher.enrich(req, selection)  → symbols + warnings (+ re-budget tokens)
        ↓
SelectedFileContentEnricher.enrich(req, sel')      → contents + warnings (+ re-budget tokens)
        ↓
tri final : relevanceScore DESC → layer.ordinal → reference
        ↓
diagnostics (par layer/kind, disponibilité des layers préférés, doublons)
warnings (REPOSITORY_CONTEXT_BUDGET_APPLIED, EVIDENCE_SUMMARY_TRUNCATED,
          SYMBOL_*/CONTENT_* …)
digest SHA-256(plan, evidence, budget, policy, diagnostics, decisions, warnings)
        ↓
RepositoryContext (record interne riche)
```

### 6.1 Classification du retrieval

**STRUCTURED + RULE_BASED + LEXICAL, 100 % déterministe.** Aucun embedding, aucun appel IA, aucun BM25. Précisément :

- **Lexical** : `TermModel` du ranker — tokenisation `[a-z0-9]+` (termes ≥3 chars) de `intent.id() + " " + intent.objective()` et de la guidance (`focus + outputContext` — donc **l'intent compte deux fois**) ; fréquence des termes sur l'ensemble des candidats ; filtrage des termes trop communs (>50 % des candidats pour le profil story) ; contribution inverse à la fréquence, plafond 100. `SEMANTIC_RELEVANCE` est donc un **score lexical**, malgré son nom.
- **Règles par layer** : `architecturalRelevance` (ADR=100, SOURCE/DIFF=80, INSIGHT=70, GIT=50, autre=20, +20 si keywords ARCHITECTURE/MODULE/API/DEPENDENCY), `historicalRelevance` (GIT/DIFF=100, ROADMAP/PREV_ANALYSIS=85, ADR=65…), `confidence` par `sourceType` (GIT=100, DETERMINISTIC_EXTRACTION=95, CORE_ANALYSIS=90, CORE_KNOWLEDGE=85–100).
- **Temporel** : `recency` en buckets (≤7j=100, ≤30j=80, ≤90j=60, ≤365j=30, sinon 10) mesuré vs `createdAt` de l'analyse synthétique (= maintenant).
- **Boost garanti** : toute evidence CURRENT_ANALYSIS reçoit un plancher de 90 en SEMANTIC_RELEVANCE (ranker ligne 94-96) — l'intent est donc toujours présent dans le contexte.
- Score final = moyenne pondérée des 6 critères (`EvidenceCriterion`) avec les poids du profil.

### 6.2 Profil effectif MCP : `engineering-story-v1`

Poids (`DeterministicContextIntelligence.profiles()`): SEMANTIC=15, ARCHITECTURAL=15, HISTORICAL=25, RECENCY=20, CONFIDENCE=20, GUIDANCE=5. Layers préférés : RELATED_SOURCE_CODE, GIT_HISTORY, COMMIT_DIFF, ADR, PROJECT_DOCUMENTATION, ROADMAP. Diversité ≥ 3 layers. Precision : terme commun >50 % ignoré, score minimum 35, un kind ne peut dépasser 25 % des items sauf score ≥75 (« strong »).

### 6.3 Sélection (`BudgetedDiverseEvidenceSelector`)

Ordre exact :
1. Déduplication par `reference` (raison `DUPLICATE_REFERENCE` pour les rejetés).
2. Passe « diversité » : premier candidat ≥ score minimum de chaque layer préféré jusqu'à 3 layers (`SELECTED_BY_DIVERSITY`).
3. Passe rang : pour chaque candidat restant — `INSUFFICIENT_RELEVANCE` (<35) → `CATEGORY_CONCENTRATION_LIMIT` (kind >25 % et score <75) → `EVIDENCE_ITEM_BUDGET_EXCEEDED` (>60 items) → `TOKEN_BUDGET_EXCEEDED` (>6000 tokens estimés) → sinon `SELECTED_BY_RANK` ou `SELECTED_BY_STRONG_RELEVANCE`.
4. Tokens estimés ≈ (caractères summary+reference+content+symbols)/4.

**Chaque candidat (retenu ou non) obtient une `SelectionDecision{reference, selected, reason, relevanceScore, estimatedTokens}`** — excellente auditabilité… interne (le contrat MCP ne garde que la raison des retenus).

---

## 7. Temporal & Historical Capabilities

### 7.1 Ce que fait déjà le chemin `getContext`

| Capacité | État | Preuve |
|---|---|---|
| Commits récents ordonnés + topologie parent | Oui | `GitHistoryContextCollector` (committedAt desc, parents en `relatedReferences`) |
| Chronologie des changements de fichiers (90 j) | Oui | `CommitDiffEvidenceCollector` (groupes par chemin, compteur de commits, type dominant) |
| Distinguer récent vs ancien | Partiellement | critère RECENCY en buckets vs *maintenant* ; git history tronquée au top-20 ; pas de contrôle de fenêtre |
| Relier une Story à ses commits | Oui (dans le modèle) | `EngineeringStory.baseCommit/targetCommit` copiés en extractionMetadata des evidence ROADMAP/ENGINEERING_STORY… **mais metadata perdues au mapping contrat** |
| Relier un changement à une décision/ADR validée | Heuristique seulement | `CommitDiffContextBuilder.isAdr/isRoadmap` (co-change de chemins) ; `Decision` n'a **aucun champ commit** |
| Connaissance validée liée aux commits | Modélisé, non exposé | `EngineeringEvent.baseCommit/targetCommit` (chaîne gouvernée commit→Analysis→Proposal→Validation→Event) ; **non collecté en evidence** |
| Filtrer le knowledge obsolète | Partiellement | seuls Insights `ACTIVE` entrent (`RepositoryContextAdapter`, requête `StatusIn[ACTIVE]`) ; `SUPERSEDED`/`ARCHIVED` exclus |
| Marquer le stale | Hors chemin MCP | `TemporalAssessmentServiceImpl.assess(Insight)` : verdicts CURRENT/SUSPECTED_STALE/UNKNOWN fail-closed sur lineage résolue + comparaison baseline(`Analysis.targetRevision`) vs révision courante — **aucun caller production** (tests only) |
| Fraîcheur repo vs baseline | Hors chemin MCP | `ProjectFreshnessClassifier` → NO_BASELINE/CURRENT/STALE + guidance ; consommé par `EngineeringStoryContextServiceImpl` (REST stories) mais pas par la facade MCP |
| Timeline consolidée | Hors chemin MCP | `TimelineProjectionServiceImpl.getTimeline` fusionne stories/events/knowledge-events/decisions/milestones (cap 20, sans commits bruts) — REST uniquement |

### 7.2 Ce qui manque structurellement

- **Aucune requête ne cherche dans les messages ou chemins de commits** : `ProjectCommitRepository` n'offre que des tris par date/hash. « Retrouver les commits ayant introduit un concept » est impossible aujourd'hui (le sens inverse existe : `collection/collector/CommitScopedFactCollector.classifyCommit` classifie un commit donné en facts).
- Pas de notion de supersession pour `Decision` (entité sans status, CRUD simple) — seule la supersession d'Insight existe (`InsightServiceImpl.supersedeInsight` + relation RESOLVES).
- Pas d'index inversé concept→commit introduisant.

**Conclusion `traceHistory`** : la matière première est là (graphe de commits complet importé : `ProjectCommit`/`CommitParent`/`ChangedFile` peuplés par `CommandLineGitHistoryProvider` + `ProjectHistoryServiceImpl.importSynchronizedHistory`), mais il manque le moteur de requête. Un futur tool serait **B/C** (capacité nouvelle sur données existantes), pas redondant.

---

## 8. Decision Understanding Capabilities

Chaîne reconstructible dans le modèle de données :

```text
ValidatableProposal (payload, supportingFactIds/ObservationIds, evidenceReferences, aiTask→provider/model/contextDigest)
    ↓ ValidationServiceImpl.validate() [@Transactional, lock pessimiste]  (ADR-006 implémenté ici)
Decision {title, context, choice, rationale, consequences, proposal OneToOne}   ← promotion atomique
Insight {proposal OneToOne, validation OneToOne, confidence, evidenceReferences}
EngineeringEvent {proposal, validation, source, baseCommit, targetCommit, occurredAt}
```

Ce que `getContext` en expose aujourd'hui : les Decisions apparaissent comme evidence ADR/DECISION avec summary `title — choice — rationale` et référence `decision:{uuid}` (`ProjectKnowledgeContextCollector` lignes 30-43). Les Insights validés comme VALIDATED_INSIGHT. Les consequences, le payload complet, la validation et les relations ne passent pas.

**Verdict `explainDecision`** : les informations existent majoritairement (rationale, consequences, chaîne de provenance proposal/validation, commits co-changant les ADR via ChangedFile). Un tel outil serait une **vue spécialisée (B)** jointure Decision+Proposal+relations+commits — pas un nouveau moteur. Aucun nouveau tool nécessaire tant que la demande n'est pas démontrée ; la donnée est en partie accessible via le contexte général.

---

## 9. Output Model

### 9.1 Structure réellement retournée au client MCP

```json
{
  "project": {"id","name","slug","description","status",
              "notes":[{"id","type","title","contentMarkdown","status","updatedAt"}]},
  "intent": "<texte repris tel quel>",
  "evidence": [ { "kind", "layer", "summary",
                  "sourceType",       // GIT | DETERMINISTIC_EXTRACTION | CORE_KNOWLEDGE | CORE_ANALYSIS | REPOSITORY_STRUCTURE
                  "originatingFile",  // chemin ou null
                  "identifier",       // uuid ou hash ou null
                  "relevanceScore",   // 0..100
                  "selectionReason" } // SELECTED_BY_RANK | SELECTED_BY_DIVERSITY | SELECTED_BY_STRONG_RELEVANCE
              ],
  "metadata": { "candidateCount", "selectedCount", "truncated", "usedTokens", "contextDigest" }
}
```

### 9.2 Pertes au mapping (`EngineeringContextContractMapper` vs `RepositoryContext` interne)

| Information interne | Dans le contrat MCP ? |
|---|---|
| `occurredAt` (timestamp de chaque evidence) | **Non** — aucun timestamp par item |
| `relatedReferences` (parents git, `analysis:{id}` d'un insight, faits supportants) | **Non** |
| `extractionMetadata` (collectorId/version, `resolvedRevision`, `storyNumber`, `status`, `baseCommit`, `targetCommit`) | **Non** |
| `EvidenceScore` détaillé (critères, poids, explications `SEMANTIC_TERMS:matched=…`, MatchStrength) et `rankingReasons` | **Non** (seul le score final passe) |
| Enrichissement `content` (texte fichier lu) et `symbols` (déclarations Java) | **Non** — calculé ET budgété puis jeté |
| Warnings (budget, troncatures, enrichissement indisponible) | **Non** |
| Diagnostics (candidats par layer/kind, layers préférés indisponibles, doublons) | Partiellement (candidateCount/truncated seulement) |
| Profil actif, explications du plan, version moteur | Non |
| `SelectionDecision` des items rejetés | Non |
| Budget (60 items / 6000 tokens / 500 chars) | Non (inférable via usedTokens) |

### 9.3 Distinction faisable par un agent externe

| Distinction demandée | Possible ? |
|---|---|
| Fait vérifié vs inférence | Approximativement : `sourceType=GIT`/`DETERMINISTIC_EXTRACTION` = fait ; `CORE_KNOWLEDGE`=validé humain ; rien ne distingue une observation rule-based d'un fait brut (les observations n'arrivent jamais) |
| Info historique vs contexte courant | Non fiable : pas de timestamps ; seul le kind/layer donne un indice (GIT_HISTORY/COMMIT_DIFF vs RELATED_SOURCE_CODE) |
| Source | Partiellement : `originatingFile` + `identifier` (formats §10) mais sans URI résolvable |
| Absence d'information | Non représentée : pas de sections vides explicites, pas de « unknown », pas d'availability des layers (interne seulement) |

### 9.4 `getContext` est-il déjà un ContextPack ?

**Oui, conceptuellement** — la mission posait la question : `RepositoryContext` interne EST un proto-`ContextPack` (borné, sélection justifiée, budgeté, digesté, versionné `repository-context-engine-v1` / plan `context-intelligence-v2` / policy `multi-criteria-v2`), anticipé par ADR-055/057 (`ContextRequest`/`ContextPackage`). Le contrat MCP en est la vue appauvrie. Le chantier pertinent n'est pas « créer un ContextPack » mais **arrêter de perdre les champs déjà calculés**.

---

## 10. Provenance & Auditability

**Formats d'identifiants présents dans `identifier`/`originatingFile`** : `decision:{uuid}`, `insight:{uuid}`, `milestone:{uuid}`, `analysis:{uuid}`, `artifact:{uuid}`, `story:{uuid}`, `git:{sourceId}:{sha}`, `diff:{sha}:{path}`, `module:{name}`, `fact:{id}`, `observation:{id}, chemins de fichiers`. `sourceType` distingue l'origine de collecte. `contextDigest` (SHA-256) rend une réponse reproductible/testable.

**Trous identifiés** :

1. Pas d'horodatage par item (occurredAt calculé mais jeté) → impossible de dater une affirmation côté client.
2. Pas de lien vers une resource récupérable (pas d'URI `devlog://…` dans la réponse — voir §14).
3. Chaîne de provenance IA coupée : le client ne voit jamais proposalId/validationId/provider/model (présents en base via `AiTask` et liens OneToOne).
4. Pas d'état stale/fresh par connaissance (`TemporalAssessmentService` non câblé ; freshness non incluse).
5. `identifier` parfois null (commits : null, l'hash n'est que dans la reference ; files : null).
6. Révision Git du contexte : `resolvedRevision` posé dans les metadata des file-evidence… puis perdu au mapping. Le client ne sait pas à quelle révision le contenu a été vu.

---

## 11. Capability Matrix

| Capacité | Couvert | Partiellement | Non couvert | Preuve dans le code |
|---|---|---|---|---|
| Construire un contexte ciblé | ✔ | | | `RepositoryContextEngine.build` + profil `engineering-story-v1` + budget 6000 tokens |
| Trouver des connaissances liées | | ✔ | | Insights/Decisions scorés lexicalement contre l'intent ; **KnowledgeRelations chargées mais jamais émises** (grep négatif dans `repositorycontext/`) |
| Comprendre l'état courant | | ✔ | | `ProjectContextProviderImpl` (snapshot 13 sources) mais non exposé en evidence ; structure repo live (`RepositoryStructureCollector`) |
| Reconstruire un historique | | ✔ | | commits top-20 + diffs 90 j ; pas de recherche, pas de fenêtre contrôlable, timeline REST non branchée |
| Expliquer une décision | | ✔ | | Decision(title/context/choice/rationale/consequences) en evidence ; consequences/proposal/relations perdus au mapping |
| Retrouver les ADR pertinentes | | ✔ | | Decisions classées ADR-layer, score architectural 100 de base ; pertinence = lexical intent uniquement |
| Retrouver les Engineering Stories pertinentes | | ✔ | | stories en evidence ROADMAP + metadata storyNumber/base/target (perdue au contrat) |
| Retrouver les commits pertinents | | ✔ | | top-20 par date + lexical subject ; aucune recherche par message/chemin (`ProjectCommitRepository`) |
| Analyser les relations entre artifacts | | | ✔ | `KnowledgeRelationRepository` lu dans le snapshot ; aucun collector ; EntityType limité à CHALLENGE/DECISION/ENGINEERING_EVENT/INSIGHT |
| Évaluer la pertinence | ✔ | | | `DeterministicEvidenceRanker` 6 critères pondérés, précision composée, explications internes |
| Gérer la temporalité | | ✔ | | RECENCY buckets, fenêtre diff 90 j, Insights ACTIVE only ; TemporalAssessment non câblé |
| Fournir la provenance | | ✔ | | sourceType/originatingFile/identifier/digest ; sans timestamps ni révision ni chaîne IA (§10) |
| Détecter les informations manquantes | | ✔ | | interne : `PreferredLayerAvailability(NO_CANDIDATE_FOR_PREFERRED_LAYER)`, truncated flag ; contrat : `truncated` + counts seulement |
| Gérer un budget de contexte | ✔ | | | `RepositoryContext.ContextBudget` + `BudgetedDiverseEvidenceSelector` + allocation enrichers |
| Évaluer l'impact d'un changement proposé | | | ✔ | aucun service d'impact ; closest : CommitDiffContextBuilder (analyse d'un commit passé, REST only) |
| Valider une implémentation contre le contexte projet | | | ✔ | aucun endpoint/tool ; la validation humaine existe seulement pour les proposals (`ValidationController`) |

**Capacités supplémentaires découvertes pendant l'audit** :

| Capacité | État | Preuve |
|---|---|---|
| Lecture bornée de contenu fichier réel (post-sélection) | Calculée, perdue au contrat | `SelectedFileContentEnricher` (6 fichiers × 4000 chars, statuts+raisons) |
| Extraction de symboles Java (déclarations typées) | Calculée, perdue au contrat | `SelectedJavaSymbolEnricher` + `JavaDeclarationExtractor` |
| Reproductibilité d'une réponse | ✔ (interne) | `RepositoryContextEngine.digest()` SHA-256 |
| Supersession de connaissance | ✔ Insights uniquement | `InsightServiceImpl.supersedeInsight` + relation RESOLVES |
| Diagnostic de cycle de vie proposition→knowledge | ✔ interne, sans surface | `lineage/KnowledgeLifecycleDiagnosticServiceImpl` (aucun controller) |

---

## 12. Current MCP Gaps

Classés par impact :

1. **G1 — Contrat appauvrissant** : contenu/symbols/timestamps/metadata/warnings jetés (`EngineeringContextContractMapper`). Le moteur fait le travail ; le contrat le défait. Gap n°1, corrigeable sans toucher au moteur.
2. **G2 — Collections fantômes** : EngineeringEvents, Challenges, Relations, Proposals, KnowledgeEvents chargés puis ignorés ; `DeterministicKnowledgeContextCollector` mort dans ce chemin.
3. **G3 — Profil unique figé** : tout intent passe par `engineering-story-v1` (via l'adapter) ; les profils `architecture-v1`, `history-v1`… existent mais sont inaccessibles depuis MCP ; `IntentDefinition.contextProfiles` n'est pas un input.
4. **G4 — Pas de recherche historique** : aucune query sur messages/chemins de commits ; pas de pagination/fenêtre temporelle.
5. **G5 — Erreurs brutes** : `intent` manquant → 500 (au lieu de 400) ; erreurs HTTP backend propagées telles quelles au canal MCP sans mapping vers messages d'erreur propres.
6. **G6 — Insights non bornés** : la requête ACTIVE de l'adapter n'a pas de PageRequest (contraste avec tout le reste, plafonné).
7. **G7 — Effet de bord Git** : `RepositoryStructureCollector`/enrichers déclenchent `fetch --prune` + checkout sur le workspace partagé (verrouillé par source) → latence variable et dépendance réseau sur un prétendu read.
8. **G8 — Pas de liste de projets** : impossible de découvrir les slugs via MCP (le client doit connaître `projectSlug` a priori).

---

## 13. MCP Resource Candidates

Évalués sur : Valeur / Stabilité / Coût / Duplication (vs getContext) / Adressabilité / Provenance. Basés sur les projections réellement existantes (URI illustratifs, non décisionnels — conformément à ADR-057 qui renvoie le format exact à l'implémentation) :

| Candidat | Source backend existante | Valeur | Stabilité | Coût | Adressabilité | Verdict |
|---|---|---|---|---|---|---|
| Liste des projets (`devlog://projects`) | `ProjectService.getAll` | Haute (résout G8) | Haute | Très bas | — | **Fort** |
| Contexte projet (existant) `devlog://projects/{slug}/context` | `ProjectContextController` | Haute | Haute | Bas | slug | Existe — garder |
| Vue « current state » (snapshot projet : décisions, stories, milestones, events, challenges, relations) | `ProjectContextProviderImpl.build` (déjà tout en mémoire) | Haute | Moyenne-haute | Bas | slug | **Fort** — c'est la donnée que getContext charge déjà et jette en partie |
| Timeline (`…/timeline`) | `TimelineProjectionServiceImpl` | Moyenne-haute | Haute (projection figée, cap 20) | Très bas | slug | **Fort** |
| Décision individuelle (`…/decisions/{id}`) | `DecisionService` + payload proposal | Haute (rationale/consequences complets) | Haute | Très bas | decision uuid (déjà dans les identifiers MCP) | **Fort** |
| Insight individuel (`…/insights/{id}`) | `InsightService.getById` (+ futur TemporalAssessment) | Haute | Haute | Très bas | insight uuid | Fort |
| Story individuelle (`…/stories/{number}`) | `EngineeringStoryService` | Moyenne | Haute | Bas | storyNumber (unique par projet) | Fort |
| EngineeringEvent (`…/events/{id}`) | `EngineeringEventQueryService` (inclut evidence/supporting ids) | Moyenne-haute | Haute | Bas | event uuid | Fort (clé commit↔connaissance) |
| Commit context (`…/commits/{sha}/context`) | `ProjectHistoryServiceImpl.getCommitContext` → `CommitDiffAnalysisContext` (classification, candidats ADR/roadmap, warnings de troncature) | Haute | Haute (déterministe, borné) | Bas | sha (déjà dans les references `git:`/`diff:`) | **Fort** |
| Fraîcheur (`…/freshness`) | `ProjectFreshnessService.summary` (déjà utilisé côté stories) | Moyenne | Haute | Bas (dernier check persisté) | slug | Moyen-fort |
| Relations (`…/relations`) | `KnowledgeRelationRepository` | Moyenne | Moyenne | Bas | slug | Moyen |

À éviter : resources reflétant Facts/Observations (périmètre d'analyse interne, forte volumétrie, faible valeur directe) ; resources miroir de tables (contraire à ADR-057 §3).

## 14. getContext ↔ Resource Relationship

Faisabilité : **élevée et naturelle**. Les `reference`/`identifier` produits par les collectors sont déjà des identifiants stables (`decision:{uuid}`, `story:{uuid}`, `git:{sourceId}:{sha}`…) qui se transposent presque directement en URIs. Le changement serait confiné à `EngineeringContextContractMapper` + devlog-contracts (ajout d'un champ optionnel `resource` par evidence), sans toucher au moteur — conforme au principe « MCP = adaptation, pas redéfinition sémantique » (ADR-057 §12 : la provenance ne doit pas être aplatie).

Intérêt : transforme le résultat de getContext en point d'entrée navigable (progressive disclosure), résout partiellement G1 (le détail riche devient accessible à la demande sans gonfler chaque réponse), et prépare le budget : le contexte renvoie les références, les Resources portent le volume (contenus, symboles, payloads complets).

Risques de couplage : (a) double source de vérité si l'URI encode un format trop interne — utiliser un schéma stable côté MCP server et mapper vers les endpoints REST backend ; (b) cohérence transactionnelle : une resource peut avoir bougé depuis le digest du contexte — exposer la révision/timestamp dans la resource pour permettre la comparaison ; (c) ne pas rendre getContext dépendant des Resources : les URIs doivent être additifs (champ optionnel), jamais obligatoires pour le consommateur.

Compatibilité actuelle : aucune rupture. `ProjectContextResource` prouve déjà le pattern template+proxy REST.

## 15. Evaluation of Additional Tool Ideas

| Idée | Classe | Justification (fondée sur le code) |
|---|---|---|
| `explainDecision` | **B** — vue spécialisée | Données présentes : `Decision.rationale/consequences`, proposal/validation liés, relations, commits co-changeant les ADR (`CommitDiffContextBuilder.candidateAdrReferences`). Ne nécessite qu'une jointure applicative + exposition des champs actuellement perdus (G1/G2). Pas avant démonstration d'usage. |
| `traceHistory` | **B/C** — données là, moteur absent | Graphe complet importé (`ProjectCommit`/`CommitParent`/`ChangedFile`) mais aucune query par message/chemin/concept (§7.2). Nécessiterait de nouvelles queries repository + un tool fin. Le plus fort candidat « nouveau tool ». |
| `findRelatedKnowledge` | **A/B** — quasi couvert | La sélection lexicale d'Insights/Decisions contre un intent EST une recherche de connaissances liées ; le vrai manque est l'exposition des `KnowledgeRelation`s (G2) — d'abord resource/vue, pas tool. |
| `assessChangeImpact` | **C/E** — nouveau, hors périmètre immédiat | Aucune base existante (matrice vide §11) ; risquerait de transformer DevLog en analyseur de code (frontière à ne pas franchir, docs/mcp-architecture-context.md). Différer. |
| `validateAgainstProjectContext` | **E** — hors responsabilité | La validation est humaine et gouvernée (ADR-004/006, `ValidationController`). Un agent qui « valide » contre le contexte inverserait le modèle ValidatableProposal. Non. |
| `proposeKnowledge` | **C** (futur, conditionné) | Prévu par ADR-057 §9 : doit passer par `ValidatableProposal` (endpoint `POST /api/v1/proposals` existe déjà). Rien à créer tant que le socle read-only n'est pas exploité ; gouvernance stricte obligatoire. |
| `reportImplementation` | **E/C** — hors périmètre initial | Revient à écrire dans DevLog depuis un agent ; même réponse que proposeKnowledge, priorité plus basse. |

Aucun de ces tools n'est justifié **avant** d'avoir corrigé G1–G3 : la moitié des besoins exprimés par ces idées est déjà produite par le moteur puis perdue au mapping.

## 16. Prompt Opportunities

Analyse courte (ADR-057 §15 reporte les prompts après Resources/Tools) :

- `prepare-engineering-story` : recouvert à ~90 % par `get_engineering_context` lui-même (profil story + `AgentEngineeringStoryContext` existe déjà côté REST). Faible valeur ajoutée.
- `implement-with-project-context` / `review-with-devlog` : value = encodage de workflow ; mais les agents (OpenCode etc.) possèdent déjà leurs propres templates, et le contenu serait une variante de « appelle get_engineering_context puis lis les resources X ». Redondant tant que les resources n'existent pas.
- `investigate-project-history` : deviendrait pertinent **après** un tool de recherche historique (traceHistory/search_project_history) — sinon il n'a rien à orchestrer.
- `explain_code` actuel : coquille vide sans lien DevLog ; candidat à suppression/remplacement lors d'une prochaine évolution (constat, pas recommandation d'action immédiate).

Conclusion prompts : aucun prompt DevLog ne passe le test de valeur aujourd'hui ; re-évaluer après resources + search tool.

## 17. Recommended Next Step

Séquence proposée (aucune implémentation dans cet audit) :

1. **Arrêter la perte d'information (G1, G2)** — évolution du contrat `EngineeringEvidence` (timestamps, relatedReferences, extractionMetadata, warnings ; optionnellement content/symbols opt-in) et émission des EngineeringEvents/Relations/Challenges par un collector. Plus haute valeur, risque minimal, zéro nouveau concept MCP.
2. **Première vague de Resources** sur artefacts adressables : projets (liste), décision, insight, story, commit-context, timeline — toutes adossées à des services REST existants, pattern déjà prouvé par `ProjectContextResource`.
3. **Ajouter des références resource dans getContext** (§14) une fois ces resources stables.
4. **Ensuite seulement**, évaluer `search_project_history` (seul vrai tool nouveau justifié par les workflows), puis re-considérer explainDecision comme vue spécialisée si la demande se confirme.
5. Hygiène associée (petits correctifs justifiables) : 400 au lieu de 500 pour `intent` manquant ; borne (PageRequest) sur la requête Insights de `RepositoryContextAdapter` ; documenter l'effet Git/network du synchronize dans la description du tool.

Chaque étape reste dans les frontières posées par `docs/mcp-architecture-context.md`, ADR-056/057 (read-only, consumer-agnostic, provenance préservée, contexte borné) et ADR-006 (aucune écriture directe de connaissance).

---

## Annexe A — Fichiers inspectés (principaux)

- mcp-server : `EngineeringContextTool`, `EchoTool`, `ServerInfoResource`, `ProjectContextResource`, `ExplainCodePrompt`, `DevlogProjectContextClient`, `DevlogBackendClientConfiguration`, `application.properties`, `logback-spring.xml`, tests (8 verts).
- devlog-contracts : `EngineeringContext`, `EngineeringEvidence`, `EngineeringContextMetadata`, `ProjectContext`, `ProjectNote`.
- backend engineeringcontext : `EngineeringContextController`, `EngineeringContextFacade(Impl)`, `EngineeringContextContractMapper`, test WebMvc (3 verts).
- backend projectcontext : `ProjectContextProvider(Impl)`, `ProjectContextSnapshot`, `RepositoryContextAdapter`, `ProjectContextContractMapper`, `AgentContextProjectionService` (partiel), controllers REST associés.
- backend repositorycontext : engine/service/request, 6 collectors, `EvidenceFactory`, `DeterministicEvidenceRanker`, `BudgetedDiverseEvidenceSelector`, 2 enrichers, `ContextPlan`/`ContextProfileDefinition`/`EvidencePrecisionPolicy`/`EvidenceCriterion`/`EvidenceScore`, `RepositoryContext(Layer)` — tests moteur (14 verts).
- backend temporal/history/lineage/freshness/understanding/timeline/knowledge/proposal/validation/insight/story : exploration structurée (voir §7-8).
- docs : `mcp-architecture-context.md`, `engineering-context-v1.md`, `architecture.md`, ADR-006/035/036/038/056/057/058/059, `knowledge-usability-audit.md` (convention d'audit), `docs/investigations/temporal-knowledge-readiness.md`.
