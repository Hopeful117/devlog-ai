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
5. Story a definir - Specificite, abstention et confiance calibree
6. Story a definir - Evaluation agentique et pilote sur Stories reelles
7. [Story 0228 - MCP Live Evaluation Preparation](../../stories/0228-mcp-live-evaluation-preparation/story.md)

Les Stories 0158, 0173, 0176, 0178 et 0209 sont des dependances ou travaux a
reconcilier. Elles ne sont pas dupliquees par cette epic.

## Ordre recommande

1. Figer le contrat et la baseline.
2. Rendre fraicheur et scope explicites.
3. Mesurer puis corriger les pertes par etape.
4. Ameliorer specificite, abstention et calibration.
5. Executer l'evaluation qualitative et le pilote reel.

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
