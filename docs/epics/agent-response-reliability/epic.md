# Epic - Fiabiliser les reponses DevLog pour les agents

## Statut

DESIGN MATERIALIZED - implementation not authorized.

Cette epic est approuvee pour figer son perimetre et ses artefacts de
planification. Elle n'autorise pas l'implementation, la modification des
budgets, le changement des contrats de production, le commit ou le push.

## Objectif produit

Permettre a DevLog de fournir des reponses suffisamment pertinentes,
traceables et honnetes pour qu'un agent puisse les accepter comme briefing de
travail, tout en conservant la verification ciblee de la codebase courante.

DevLog ne doit pas remplacer l'inspection du depot. Il doit permettre a l'agent
de savoir clairement :

- ce qui est etabli par les preuves autorisees ;
- ce qui est une interpretation ou une recommandation ;
- ce qui a ete perdu par troncature ou budget ;
- ce qui est inconnu ou potentiellement obsolete ;
- quelle verification ciblee reste necessaire.

## Probleme

Les reponses actuelles peuvent etre structurellement valides mais peu fiables
pour un agent : contexte tronque, fraicheur inconnue, confiance faible,
preuves critiques perdues ou reponse generale mal adaptee a la question.

Le systeme expose deja des elements de grounding, de confiance, de provenance,
d'incertitude et de diagnostics de budget, mais ne fournit pas de contrat
global permettant de determiner si une reponse est acceptable, utilisable avec
verification ou insuffisante.

## Objectifs

- Definir un contrat d'acceptabilite agentique.
- Rendre la fraicheur, le scope et les pertes d'information explicites.
- Preserver les preuves critiques a travers selection, enrichissement et
  projection.
- Ameliorer la specificite, l'abstention et la calibration de confiance.
- Mesurer la qualite avec des scenarios reproductibles et une revue humaine.
- Valider l'utilite sur des Engineering Stories reelles.

## Hors perimetre global

- Remplacer l'exploration directe de la codebase.
- Introduire immediatement RAG, embeddings ou vector search.
- Augmenter les budgets sans baseline mesuree.
- Deplacer l'autorite du contexte vers Python.
- Creer une boucle autonome illimitee.
- Promouvoir une sortie IA en connaissance trustee.

## Invariants architecturaux

1. Java/Core reste l'autorite du scope, de la revision, de la selection, du
   grounding et de l'acceptation.
2. Python reste responsable de l'execution probabiliste et de la validation
   defensive.
3. La confiance ne remplace jamais une preuve.
4. Une troncature, une fraicheur inconnue et un echec provider restent des
   conditions distinctes.
5. Les reponses restent non trustees et read-only.
6. Toute modification de budget ou de retrieval doit etre justifiee par une
   evaluation separee.

## Engineering Stories

1. [Story 0210 - Contrat d'acceptabilite agentique et baseline](../../stories/0210-agent-response-acceptability-contract/story.md)
2. [Story 0219 - Freshness, Scope and Provenance Explicitness](../../stories/0219-explicit-freshness-scope-provenance/story.md)
3. [Story 0220 - Revision-Pinned Runtime Loss and Provider Outcome Evaluation](../../stories/0220-revision-pinned-runtime-loss-evaluation/story.md)
4. [Story 0221 - Bounded Live Provider Captures and Attribution](../../stories/0221-bounded-live-provider-captures/story.md)
5. [Story 0222 - Abstention and Confidence Calibration](../../stories/0222-abstention-confidence-calibration/story.md)
6. [Story 0223 - Agentic Quality Evaluation and Real Engineering Story Pilot](../../stories/0223-agentic-quality-real-story-pilot/story.md)
7. [Story 0228 - MCP Live Evaluation Preparation](../../stories/0228-mcp-live-evaluation-preparation/story.md)
8. [Story 0229 - MCP Live Evaluation Authorization](../../stories/0229-mcp-live-evaluation-authorization/story.md)
9. [Story 0230 - Core Citable Evidence Resolution for Investigation](../../stories/0230-core-citable-evidence-resolution-for-investigation/story.md)
10. [Story 0231 - Core Search Evidence Resolution for Investigation](../../stories/0231-core-search-evidence-resolution-for-investigation/story.md)
11. [Story 0232 - Q1 Live Result and Revision Integrity](../../stories/0232-live-q1-result-and-revision-integrity/story.md)
12. [Story 0233 - Detect Empty Result under Constrained Budget](../../stories/0233-detect-empty-result-constrained-budget/story.md)
13. [Story 0234 - Core Knowledge Floor with File Scope](../../stories/0234-core-knowledge-floor-with-file-scope/story.md)
14. [Story 0235 - Bound get_engineering_context Response](../../stories/0235-bound-get-engineering-context-response/story.md)
15. [Story 0236 - MCP Agent Approval for Live Evaluation](../../stories/0236-mcp-agent-approval-live-evaluation/story.md)
16. [Story 0237 - ABC Harness Validity and Multi-Case Campaign](../../stories/0237-abc-harness-validity-multi-case/story.md)
17. [Story 0238 - Core Project Freshness Aggregation](../../stories/0238-core-project-freshness-aggregation/story.md)
18. [Story 0239 - Core Multi-Case Live Evaluation Campaign Contract](../../stories/0239-core-multi-case-live-evaluation-campaign/story.md)

Les Stories 0158, 0173, 0176, 0178 et 0209 sont des dependances ou travaux a
reconcilier. Elles ne sont pas dupliquees par cette epic.

## Ordre recommande

1. Figer le contrat et la baseline.
2. Rendre fraicheur et scope explicites.
3. Mesurer puis corriger les pertes par etape.
4. Ameliorer specificite, abstention et calibration.
5. Executer l'evaluation qualitative et le pilote reel.

## Criteres de cloture (PROPOSES - validation humaine requise)

Niveau de preuve retenu : campagne 8 questions x 3 conditions x 3 repetitions
(72 observations, Story 0223), notee par revue humaine contre les oracles
acceptes.

- **Completude** : au moins 90 % des cellules A et C `COMPLETED` ; chaque
  echec est classe (provider, budget, revision, outil, grounding).
- **Exactitude** : la meilleure condition DevLog couvre au moins 70 % des
  `requiredClaims` et ne fait pas moins de B - 10 points.
- **Honnetete** : aucune `forbiddenClaim` assertee en confiance `HIGH` ;
  abstention correcte sur Q7 pour au moins 2 repetitions sur 3 par condition
  DevLog.
- **Calibration** : aucune reponse jugee hors sujet ou incorrecte en confiance
  `HIGH`.
- **Fraicheur** : fraicheur differente de `UNKNOWN` dans toutes les
  observations DevLog.
- **Gouvernance** : Stories de l'epic acceptees ou explicitement classees
  comme prerequis.

Etat au 2026-11-25 : non atteint. Le run live Q1 28 est techniquement complet
mais invalide comme mesure (voir
[l'evaluation du run 28](../../evaluation/devlog-ai-q1-live-run28-assessment-v1.md)).

## Gouvernance

La materialisation de cette epic ne vaut pas autorisation d'implementation.
Chaque Story conserve les gates de Repository Analysis, Implementation Plan,
implementation et acceptance. Aucun resultat d'evaluation ne constitue une
acceptation humaine de l'architecture ou du comportement produit.

## References

- [ADR-006](../../decisions/ADR-006.md)
- [ADR-063](../../decisions/ADR-063.md)
- [ADR-066](../../decisions/ADR-066.md)
- [ADR-067](../../decisions/ADR-067.md)
- [Story 0158](../../stories/0158-devlog-story-agent-response-specificity/story.md)
- [Story 0178](../../stories/0178-story-context-loss-aware-composition/story.md)
- [Story 0209](../../stories/0209-agent-response-quality-evidence-grounding/story.md)
