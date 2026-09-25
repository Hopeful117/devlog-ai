# Engineering Story — Canonicaliser la capacité EngineeringContext du Story Context Agent

Statut : implémentée et validée contre ADR-069 **Accepted** (2026-09-25). ADR-069 tranche Option B : le Core possède un résultat canonique interne riche ; `EngineeringContext` est une projection publique ; le Story Context Agent reçoit une projection Core versionnée. Analyse repository-only du 24-09-2026. Repository : /home/ludo/workspace/devlog-ai.

## 1. Résumé et objectif

Le dépôt possède une façade EngineeringContext canonique pour REST/MCP, mais le Story Context Analysis reconstruit ensuite un AnalysisContext, appelle KnowledgeSelectionService et envoie SelectedKnowledge. L’ancien endpoint engineering-story-context expose en parallèle deux records distincts. Cette Story doit rendre EngineeringContext réellement unique pour le Story Context Agent : Core construit/scopes une fois, le consommateur ne fait qu’une projection déterministe, sans seconde sélection sémantique.

## 2. État réel et contradiction

- EngineeringContextFacadeImpl appelle ProjectContextProvider, RepositoryContextAdapter et EngineeringContextContractMapper (backend/src/main/java/com/hopeful117/devlogai/engineeringcontext/EngineeringContextFacadeImpl.java:15-53).
- REST GET /api/v1/projects/{projectSlug}/engineering-context est mappé par EngineeringContextController.java:18-34.
- MCP get_engineering_context appelle ce REST via DevlogProjectContextClient.java:23-29 et EngineeringContextTool.java:19-57.
- AnalyzeStoryContextUseCase.java:110 appelle bien la façade, mais :121-146 rebâtit/adapte AnalysisContext et exécute KnowledgeSelectionService.select ; :147-210 projette SelectedKnowledge et le met dans PromptRequest.
- Conclusion vérifiée : la façade est canonique pour MCP et existe dans SCA, mais elle n’est pas encore l’unique source du payload SCA. C’est la contradiction centrale.

## 3. Workflow request → transports → Core → Python → callback

Contexte : MCP client → EngineeringContextTool → DevlogProjectContextClient GET → EngineeringContextController → EngineeringContextFacadeImpl → ProjectContextProvider + RepositoryContextAdapter → RepositoryContextService/RepositoryContextEngine → EngineeringContextContractMapper → contrat EngineeringContext → JSON MCP.

SCA : POST /api/v1/projects/{slug}/stories/{storyId}/analyze-context (StoryContextAnalysisController.java:24-36) → AnalyzeStoryContextUseCase → Analysis → façade EngineeringContext → baseline ProjectProfile/AnalysisContextService et historique → KnowledgeSelectionService → SelectedKnowledgePromptProjectionService → AiTaskService → snapshot freshness/grounding → submit → PromptRequest → AIEngineClient.

Python valide PromptRequest dans ai-engine/app/schemas/ai_task.py:56-70 ; ContractModel extra=forbid est défini :18-20. Le service story_context_analysis_generation_service.py génère puis appelle Core avec AiTaskResultRequest. AiTaskResultServiceImpl.handle valide corrélation/job/contrat et route le SCA (backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiTaskResultServiceImpl.java:54-73).

## 4. Entrypoints, transports et use cases

| Surface | Fichier/lignes | Rôle |
|---|---|---|
| REST EngineeringContext | engineeringcontext/controller/EngineeringContextController.java:18-34 | façade agent-facing |
| MCP contexte | mcp-server/.../EngineeringContextTool.java:19-57 | adaptateur JSON mince |
| REST SCA | storycontextanalysis/controller/StoryContextAnalysisController.java:24-36 | démarre AiTask |
| MCP SCA | mcp-server/.../StoryContextAnalysisTool.java | appelle REST et poll |
| Résultat SCA | StoryContextAnalysisController.java:39-45 | lecture du résultat |
| REST legacy | projectcontext/EngineeringStoryContextController.java:21-47 | full ou agent projection |

La façade n’expose qu’une méthode slug, intent, files, storyId (EngineeringContextFacade.java:8-14) ; elle ne renvoie pas SelectedKnowledge et ne soumet pas d’AI task.

## 5. Composition canonique et legacy

RepositoryContextAdapter.java:79-123 résout story, compose le texte, borne facts/observations, synthétise AnalysisContext, appelle RepositoryContextService puis filtre story. Cette synthèse interne est autorisée comme détail d’implémentation de la façade : elle ne doit pas être reconstruite par AnalyzeStoryContextUseCase, qui ne doit ni resélectionner ni reclasser les éléments. Le mapper est documenté comme unique mapping vers le contrat dans docs/mcp-engineering-context-contract.md:21-38.

EngineeringStoryContext.java:9-19 contient ProjectContextSnapshot, generatedAt, projectId, RepositoryContext et fraîcheur. EngineeringStoryContextServiceImpl.java:24-75 offre full et agent ; la branche agent appelle AgentContextProjectionService. Le contrôleur choisit via detail=FULL, sinon agent. Les recherches de callers montrent que ce chemin est consommé par le contrôleur legacy et ses tests, pas par SCA. Il faut le migrer/documenter, pas le supprimer silencieusement.

## 6. RepositoryContext, AnalysisContext, SelectedKnowledge

- AnalysisContext.java:20-60 agrège facts, observations, événements, analyses, artefacts, décisions, milestones, propositions validées, évolution, stories, human inputs et relations. AnalysisContextServiceImpl.java:31-50 borne à 100 facts et 50 observations.
- RepositoryContext est la sortie déterministe des collectors/engine ; la façade peut synthétiser en interne un AnalysisContext sans Analysis persistée. Ce fait ne donne pas au use case le droit de reconstruire un AnalysisContext ou d’appeler KnowledgeSelectionService une seconde fois.
- KnowledgeSelectionServiceImpl.java:30-44 définit versions v4/v5 et budget 40 facts, 25 observations, 10 insights, 5 architecture, 60 preuves repository ; :85-204 classe, admet relations, reconstruit repository context et digest.
- SelectedKnowledge.java:15-45 porte les sélectionnés, diagnostics, RepositoryContext, SelectionMetadata, selectionDigest et relations. SelectedKnowledgePromptProjectionService.java:44-79 produit la map AI et la variante typée.

Décision ADR-069 appliquée : `SelectedKnowledge` n’est pas le contexte canonique. Le Core compose une source interne riche conservant `RepositoryContext`, scope, grounding, provenance, trust, freshness et accounting ; il produit ensuite une projection SCA versionnée. `SelectedKnowledge` reste une enveloppe de compatibilité temporaire, explicitement non autoritative. Aucune nouvelle recherche, reclassification ou sélection concurrente ne peut suivre la construction canonique.

## 7. Projections, grounding, références, digests et budgets

EngineeringContextContractMapper.java:148-180 construit sections TRUSTED, HUMAN_AUTHORED, TECHNICAL_EVIDENCE et SYSTEM_METADATA, puis evidence[] de compatibilité. Le mapper conserve provenance, extraction, contenu, symboles, raisons et références (à partir de :253) ; contextDigest est calculé en amont selon docs/mcp-engineering-context-contract.md:105-114.

AgentContextProjectionService.java:71-159 a une politique bytes/tokens et réduit successivement références, raisons, déclarations, contenu, résumés, profil, human inputs, listes puis preuves ; il produit accounting, warnings et projectionDigest.

SCA construit groundingContract à partir de SelectedKnowledge.repositoryContext et le stocke dans AiTask.contextSnapshot (AnalyzeStoryContextUseCase.java:156-187). Aujourd’hui `selectedKnowledge.selectionDigest` est affecté à `contextDigest`, alors que `AiTask.contextDigest` reste nul jusqu’au callback ; `PromptRequest.metadata` ne contient que `projectSlug` et `storyId`. Cette ambiguïté est bloquante.

ADR-069 définit et l’implémentation doit faire respecter : `contextDigest` = digest du contexte canonique complet et de son scope/freshness ; `selectionDigest` = digest legacy de `SelectedKnowledge`, uniquement lorsqu’une sélection de compatibilité est effectivement produite ; `projectionDigest` = digest exact de la projection transportée vers le prompt (shape, version, ordre, réduction et accounting). Sans réutilisation implicite, `AiTask`, `contextSnapshot`, `PromptRequest`, le callback et le résultat portent ces identités dans des champs/enveloppes versionnés distincts, avec `selectionDigest` explicitement compatible. Les changements Python respectent `extra=forbid`.

Validations obligatoires : avant soumission, `contextDigest` doit égaler celui du snapshot canonique, `projectionDigest` celui du payload sérialisé, et `selectionDigest` ne peut être déclaré que si une sélection correspondante est effectivement produite ; après callback, corrélation/job, versions, scope et les trois digests doivent égaler l’AiTask/snapshot enregistrés (ou l’absence explicitement autorisée), sinon rejet diagnostiqué et idempotent. Aucun callback ne doit « compléter » un `contextDigest` nul.

## 8. Consommateurs et variantes

1. MCP/REST direct : contrat EngineeringContext.
2. SCA : EngineeringContext construit, mais payload réel SelectedKnowledge projeté.
3. Analysis générique : AnalysisContext → KnowledgeSelectionService → SelectedKnowledge → AiTask/PromptRequest (AnalysisWorkflowServiceImpl.java:46-90), hors migration automatique.
4. Legacy : EngineeringStoryContext full ou AgentEngineeringStoryContext réduit.
5. Python : PromptRequest.selectedKnowledge, groundingContract, metadata ; aucun champ engineeringContext actuel dans le schéma.
6. Callback : AiTaskResultServiceImpl et query SCA ; il ne reconstruit pas mais dépend des snapshots/digests.

Inconnue de contrat : garder selectedKnowledge comme nom de projection ou ajouter engineeringContext. Pydantic extra=forbid implique une version/coordination additive.

## 9. ADR, contrats et tests existants

ADR-063 établit EngineeringContext agent-facing et sépare KnowledgeSelectionService ; ADR-064 sépare composition et analysis ; ADR-067 §§D3 affirme Java/Core sole authority et input immutable. docs/mcp-engineering-context-contract.md est la référence la plus directe, mais affirme facts/observations vides sur MCP alors que l’adapter actuel les borne/injecte : contradiction à tester.

Tests pertinents : backend EngineeringContextControllerWebMvcTest, EngineeringContextContractMapperTest, RepositoryContextAdapter*Test, KnowledgeSelectionService*Test, SelectedKnowledgePromptProjectionServiceTest, AgentContextProjectionServiceTest, EngineeringStoryContextControllerWebMvcTest, AnalyzeStoryContextUseCaseTest ; Python test_story_context_analysis_wire_contract.py, test_story_context_analysis_prompt.py, test_ai_tasks.py, test_core_callback_client.py ; MCP EngineeringContextToolUnitTest et StoryContextAnalysisToolTest.

## 10. Design de Story étroite

1. Implémenter l’Option B acceptée : résultat canonique interne riche, avec `RepositoryContext` comme composant repository borné, sans nouveau moteur de retrieval.
2. Introduire une projection SCA versionnée et déterministe au-dessus de la composition Core, sans recopier `RepositoryContextAdapter`.
3. Faire utiliser cette projection une seule fois par `AnalyzeStoryContextUseCase` pour `AiTask` et `PromptRequest` ; partager exactement le même snapshot/digest. Le use case ne reconstruit ni `AnalysisContext` ni sélection concurrente.
4. Propager séparément les trois digests et les références typées ADR-068, avec validation avant soumission et au callback.
5. Journaliser versions, digests, counts, bytes/tokens, warnings, scope et source path.

Invariant : pour un même projectSlug, intent, files, storyId et snapshot de fraîcheur, Core n’a qu’une construction/scoping ; un consommateur peut seulement projeter/ordonner/réduire.

## 11. Hors périmètre et follow-ups

Hors périmètre : réécrire RepositoryContextEngine/collectors ; fusionner tous les workflows Analysis ; supprimer immédiatement legacy ; changer les budgets métier ; autonomiser Python ; changer trust/proposals ; UI ; migration DB ; réécrire les ADR historiques.

Follow-ups : migration clients legacy ; ADR de dépréciation de /api/projects/.../engineering-story-context ; harmonisation de la doc facts/observations ; décision sur AnalysisContext/SelectedKnowledge internes ; version de contrat Python/MCP ; replay comparatif ; relations non projetées et citabilité.

Compatibility window obligatoire : inventorier et notifier les consommateurs externes de REST/MCP/SCA, maintenir pendant une fenêtre datée la lecture des anciens snapshots et le champ/shape legacy convenu, et publier métriques de bascule, replay et rollback. Aucun renommage/suppression de `selectedKnowledge`, endpoint legacy ou champ Python ne peut être implicite ; une version de contrat et une date de fin doivent précéder la suppression.

## 12. Critères d’acceptation

- [ ] Une construction Core produit le contexte canonique utilisé pour dériver les projections publiques et SCA ; `AnalyzeStoryContextUseCase` ne reconstruit ni `AnalysisContext` ni sélection concurrente.
- [ ] Le résultat canonique interne riche conserve les données nécessaires aux relations, Facts/Observations, diagnostics, grounding, provenance, trust et callback ; `RepositoryContext` reste le composant repository borné autoritatif pendant la migration.
- [ ] La projection SCA Core-owned, versionnée et déterministe est dérivée du contexte canonique, avec références autorisées constituant un sous-ensemble strict du grounding canonique.
- [ ] Contrat Python versionné et accepté par `extra=forbid` : intent, scope/request echo, trust sections, evidence, provenance/références typées, freshness, grounding, budgets et digests.
- [ ] `AiTask`, snapshot et `PromptRequest` portent séparément `contextDigest`, `selectionDigest` et `projectionDigest` ; validations avant soumission et après callback rejettent toute divergence, réassignation ou digest nul non autorisé.
- [ ] Le snapshot d’exécution immuable conserve scope, source revisions/freshness, policies, budgets, accounting, mapping de références, grounding allow-list et projection exacte ou représentation content-addressed.
- [ ] Le test de frontière prouve que la façade peut synthétiser AnalysisContext, mais qu’AnalyzeStoryContextUseCase ne le reconstruit ni ne sélectionne une seconde fois.
- [ ] Projection non-broadening : files/story scope, exclusions trust, références et ordre déterministe conservés.
- [ ] Budget borné, observable et documenté ; réduction ou échec explicite, jamais suppression silencieuse.
- [ ] REST/MCP/SCA restent des adaptateurs sans univers de retrieval indépendant ; legacy n’est pas changé implicitement et conserve une compatibility window.
- [ ] Callback, erreurs, retries, doublons et lecture résultat restent fonctionnels ; les incohérences d’identité/grounding sont refusées de manière diagnostiquée et idempotente.
- [ ] Documentation de contrat et ADR mises à jour, y compris la correction/test de la contradiction Facts/Observations.
- [ ] Compatibility window legacy explicite, consommateurs externes inventoriés, compatibilité et rollback vérifiables ; `SelectedKnowledge` est documenté comme enveloppe temporaire non autoritative.

## 13. Tests requis et vérification

Unit projection : égalité JSON/digest, ordre, scope, trust, grounding, relations/diagnostics retenus, budgets/warnings et distinction des trois digests. Use case : façade une fois, aucun `AnalysisContext` reconstruit, aucune sélection concurrente, snapshots identiques. REST/MCP : fixtures GET/POST, désérialisation Java/Python, empty files/guidance, story autre projet. Python : wire contract, prompt, ai tasks, extra fields et metadata complète. Grounding/callback : références inconnues/non projetées, digest nul ou divergent, correlation/job, duplicate terminal, failed callback. Frontière façade/use case : stub de façade qui synthétise un AnalysisContext et vérification qu’aucun appel de sélection/reconstruction n’est effectué par le use case. Documentation : test/fixture affirmant la politique réelle Facts/Observations et échouant si la prose diverge. Non-régression : tests listés section 9. Replay : même snapshot et PromptRequest comparés avant toute attribution au modèle.

Commandes exécutées : rg --files ; rg -n ; find ... | sort ; nl -ba ... | sed/head ; git status --short --branch. Aucun build/test exécuté afin de ne produire aucun artefact dans le repository cible ; l’absence de run ne prouve pas la compilation.

## 14. Risques, migration, ADR et Reviewer Findings

Risques : contrat cassant (Pydantic extra=forbid) ; perte ou élargissement de connaissance ; confusion des trois digests ; budgets legacy divergents ; point de gel freshness incertain ; contradiction documentaire facts/observations ; snapshots historiques replayables.

Migration : phase 1 builder/projection en shadow mode ; phase 2 activation SCA avec lecture des anciens snapshots ; phase 3 migration MCP/clients et compatibility window ; phase 4 ADR de dépréciation legacy après métriques/replay.

ADR-069 accepté : la Story implémente désormais sa frontière, sa projection,
ses identités et sa migration conformément à cet ADR. Toute question
architecturale nouvelle qui dépasserait ADR-069 doit être isolée dans un
follow-up et ne peut pas être résolue implicitement dans cette Story.

### Reviewer Findings (revue indépendante intégrée)

Statut de revue : **findings intégrés dans ADR-069 accepté**. Les constats
restent des exigences d’implémentation et de test ; ils ne constituent pas une
autorisation d’élargir le périmètre.

1. **Contenu canonique.** ADR-069 choisit Option B : projection interne depuis un résultat canonique riche conservant `RepositoryContext`, scope, grounding, relations, diagnostics, provenance et trust.
2. **Digests et temporalité.** `contextDigest`, `selectionDigest` et `projectionDigest` sont distincts, placés explicitement dans `AiTask`/snapshot/`PromptRequest` et validés avant soumission puis après callback.
3. **Frontière façade/use case.** La façade peut synthétiser un `AnalysisContext` en interne, mais `AnalyzeStoryContextUseCase` ne doit pas en reconstruire un ni refaire une sélection ; un test d’invocation unique est obligatoire.
4. **Documentation Facts/Observations.** La politique réelle doit être corrigée dans le contrat et verrouillée par fixture/test.
5. **Consommateurs externes et legacy.** Une compatibility window datée, une version de contrat, un inventaire, des métriques de bascule/replay et un rollback sont requis avant toute dépréciation ou renommage.

Inconnues résiduelles : consommateurs externes non déductibles des callers
locaux, valeurs de configuration runtime non chargées et résultats de
compilation/tests à obtenir pendant l’implémentation ; aucune décision
architecturale fondamentale ne reste ouverte dans le périmètre d’ADR-069.
