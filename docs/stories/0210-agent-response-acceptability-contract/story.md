# Story 0210 - Contrat d'acceptabilite agentique et baseline

## Statut

IMPLEMENTATION COMPLETE - AWAITING HUMAN ACCEPTANCE.

Cette Story fige un contrat et une baseline d'evaluation. L'implementation
evaluation-only est completee sous reserve de l'acceptation humaine. Elle ne
modifie pas les contrats de production, les budgets, le commit ou le push.

## Objectif

Definir les conditions qui permettent a un agent de determiner si une reponse
DevLog peut etre acceptee comme briefing, si elle est utilisable avec une
verification ciblee, ou si elle est insuffisante.

Cette Story ne cherche pas a supprimer l'exploration de la codebase. Elle doit
rendre explicite la part de confiance que l'agent peut accorder a la reponse et
le prochain controle necessaire.

## Probleme

Le contrat Story Context expose deja `confidence`, le grounding, la provenance,
les incertitudes et les informations manquantes. Il ne fournit pas de statut
global d'acceptabilite.

Une confiance `LOW` ne permet pas de distinguer :

- une abstention correcte faute de preuves ;
- une reponse affectee par une troncature ;
- une fraicheur inconnue ;
- une reponse techniquement valide mais semantiquement incomplete ;
- un echec technique non evaluable.

## Artefacts gouvernants

- ADR-006 - AI Proposal and Knowledge Promotion Workflow
- ADR-063 - Engineering Context Retrieval and Composition Architecture
- ADR-066 - Persist a Replayable AI Intent Evaluation Harness
- ADR-067 - DevLog Engineering Story Context Agent
- Story 0158 - DevLog Story Agent Response Specificity
- Story 0173 - Story Context Budget Policy and Provider Failure Diagnostics
- Story 0178 - Story Context Loss-Aware Composition and Projection
- Story 0209 - DevLog Agent Response Quality and Evidence Grounding
- `docs/evaluation/answer-quality-benchmark-specification.md`
- `docs/investigations/story-context-truncation-and-provider-incomplete-response.md`

## Contrat propose

La Story doit definir et faire approuver les etats semantiques suivants :

```text
ACCEPTABLE
BOUNDED
INSUFFICIENT
STALE_OR_UNKNOWN
FAILED
```

### Semantique

- `ACCEPTABLE` : preuves suffisantes, question couverte et limites non
  bloquantes pour un briefing agentique.
- `BOUNDED` : reponse exploitable, mais une limitation connue impose une
  verification ciblee.
- `INSUFFICIENT` : les preuves autorisees ne permettent pas d'etablir la
  reponse.
- `STALE_OR_UNKNOWN` : la revision ou la fraicheur ne permet pas de considerer
  la reponse comme actuelle.
- `FAILED` : aucune reponse semantiquement evaluable n'est disponible.

Les noms et la position definitive dans les contrats restent soumis a la
decision de Repository Analysis et a l'Implementation Plan. Cette Story ne
doit pas imposer une modification additive de production sans approbation
explicite.

## Regles a definir

Le contrat doit expliciter la combinaison de :

- statut technique d'execution ;
- grounding et references autorisees ;
- fraicheur et revision ;
- troncature et pertes d'information ;
- couverture des preuves attendues ;
- abstention ;
- contradictions ;
- prochain pas ;
- confiance declaree.

Regle minimale : une reponse tronquee dont la conclusion principale n'est pas
couverte ne peut pas etre `ACCEPTABLE`, meme si son schema et son grounding
partiel sont valides.

La confiance du modele ne devient jamais une preuve et ne peut jamais elever
une reponse au-dessus des limites imposees par le grounding, la revision ou la
troncature.

## Baseline

Figer une baseline reproductible a partir des scenarios existants :

- question ciblee ;
- question large ;
- question multi-source ;
- preuve insuffisante ;
- contexte tronque ;
- contradiction ou question historique ;
- reponse structurellement valide mais semantiquement incomplete.

Chaque observation doit, lorsque l'information est disponible, contenir :

- statut technique ;
- statut de grounding ;
- statut d'acceptabilite ;
- niveau de confiance ;
- references produites ;
- preuves attendues et couvertes ;
- abstention ;
- prochain pas ;
- revision et fraicheur ;
- troncature ;
- taille du contexte, latence et tokens.

Une observation absente, provider-incomplete ou techniquement invalide est
`NOT_EVALUATED` pour la qualite semantique et ne doit pas etre comptee comme
une mauvaise reponse.

## Perimetre

- Definir le vocabulaire et les invariants d'acceptabilite.
- Reconciler les modeles d'evaluation existants.
- Definir la matrice de decision entre qualite technique et qualite semantique.
- Versionner les scenarios, oracles et seuils proposes.
- Produire une baseline provider-free lorsque possible.
- Identifier les seuils qui necessitent une revue humaine.
- Documenter les limites et les cas non evaluables.

## Hors perimetre

- Modification du prompt de production.
- Modification des budgets ou de la politique provider.
- Ajout de RAG, embeddings ou vector search.
- Nouvelle retrieval ou boucle autonome.
- Modification de la frontiere Java/Core - Python.
- Promotion d'une analyse en connaissance trustee.
- Declaration d'une reponse de production comme `ACCEPTABLE` avant validation
  du contrat et des seuils.

## Criteres d'acceptation

1. Les etats d'acceptabilite et leurs definitions sont documentes sans
   ambiguite.
2. Le contrat distingue validite technique, grounding, qualite semantique et
   acceptabilite agentique.
3. Les regles couvrent les cas complet, tronque, fraicheur inconnue, preuves
   insuffisantes, provider-incomplete, grounding invalide, abstention correcte
   et reponse generique structurellement valide.
4. Une reponse techniquement valide mais insuffisamment etayee ne peut pas
   etre classee `ACCEPTABLE`.
5. La troncature et la fraicheur inconnue sont visibles dans les resultats.
6. Les scenarios existants des Stories 0158, 0178 et 0209 ainsi que le
   benchmark ABC sont reutilises ou explicitement ecartes avec justification.
7. La baseline est reproductible et marque les executions absentes ou
   invalides `NOT_EVALUATED`.
8. Les seuils de qualite humaine, de specificite, de couverture des preuves et
   d'utilite du prochain pas sont identifies.
9. Aucun comportement de production n'est modifie par cette Story.
10. Les decisions encore ouvertes sont listees pour le Repository Analysis ou
    l'Implementation Plan suivant.

## Validation attendue

- Validation des schemas et modeles d'evaluation existants.
- Replay des fixtures provider-free disponibles.
- Verification de la separation technique/semantique.
- Verification des cas `NOT_EVALUATED`.
- Verification de la stabilite des digests et des revisions de baseline.
- Revue humaine independante des oracles et seuils.
- `git diff --check`.

Les resultats live ne doivent pas etre presentes comme baseline officielle sans
configuration, revision, oracle et identite d'execution figes.

## Decisions humaines requises

1. Confirmer les noms definitifs des etats d'acceptabilite.
2. Decider si le statut est d'abord un artefact d'evaluation ou un champ
   additif de l'enveloppe de production.
3. Decider le traitement d'une fraicheur `UNKNOWN`.
4. Approuver les seuils minimums d'acceptabilite.
5. Approuver les oracles du benchmark ABC avant toute comparaison de qualite.

## Gates

Cette Story requiert explicitement :

1. approbation de la Story ;
2. Repository Analysis ;
3. approbation du Repository Analysis ;
4. Implementation Plan ;
5. approbation de l'Implementation Plan avant toute implementation.

La materialisation de cet artefact ne satisfait aucun de ces gates.

## References

- [Epic - Fiabiliser les reponses DevLog](../../epics/agent-response-reliability/epic.md)
- [Story 0158](../0158-devlog-story-agent-response-specificity/story.md)
- [Story 0173](../0173-story-context-budget-observability/story.md)
- [Story 0178](../0178-story-context-loss-aware-composition/story.md)
- [Story 0209](../0209-agent-response-quality-evidence-grounding/story.md)
- [Answer Quality Benchmark](../../evaluation/answer-quality-benchmark-specification.md)
