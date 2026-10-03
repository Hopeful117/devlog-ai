# Story 0159 - Garantir la fidelite du contexte EngineeringContext a travers MCP

## Statut

Draft

Cette Story formalise une investigation et une correction eventuelle. Elle ne
vaut pas autorisation d'implementation, de modification du contrat ou de
changement d'architecture.

## Objectif produit

Garantir qu'un agent appelant `get_engineering_context` recoive le contexte
EngineeringContext autorise par Java Core sans perte silencieuse d'informations
utiles a l'analyse.

La Story doit etablir, par des preuves reproductibles, si les champs `content`,
les symboles, les timestamps et les warnings sont preserves entre la
construction Core et la reponse MCP finale. Si une perte est confirmee, elle
doit etre corrigee au premier point de perte avec le changement minimal
compatible avec les contrats existants.

## Probleme

Une analyse DevLog a signale une perte possible d'informations produites par le
repository context engine lors du passage par MCP. Cette analyse etait fondee
sur un contexte tronque et ne prouve pas le point de perte.

Le risque est qu'un agent recoive un contexte structurellement valide mais
incomplet, puis produise une analyse moins specifique ou s'abstienne pour une
mauvaise raison. A l'inverse, modifier MCP sans comparer les payloads Core et
MCP pourrait corriger un probleme inexistant ou elargir silencieusement le
scope autorise.

## Dependances et autorite

- Story 0158 - Rendre les reponses du DevLog Story Agent specifiques et
  actionnables.
- ADR-063 - Contexte canonique et composition deterministe.
- ADR-067 - Separation Core/Python et frontiere d'autorite.
- ADR-069 - Projections non-elargissantes.

Java Core reste l'autorite de la construction, du scope, de la selection et du
grounding de l'EngineeringContext. MCP reste un adaptateur de transport et ne
doit ni reconstruire le contexte ni elargir les preuves autorisees.

## Perimetre

### Dans le perimetre

1. Comparer un meme appel au payload EngineeringContext produit par Core, au
   modele deserialise par `DevlogProjectContextClient` et a la sortie de
   `EngineeringContextTool`.
2. Verifier explicitement la presence, la valeur et la representation des
   contenus, symboles, timestamps et warnings lorsqu'ils sont presents dans le
   contexte autorise.
3. Identifier le premier point de perte ou etablir que la perte n'est pas
   reproductible dans le perimetre teste.
4. Ajouter des tests de contrat et de serialisation couvrant la conservation
   des champs verifies.
5. Corriger uniquement le point de perte confirme, sans modifier la selection,
   le scope, les budgets ou les niveaux de confiance.
6. Rejouer le cas de contexte tronque de Story 0158 lorsque la correction
   affecte les donnees exposees a l'agent.
7. Documenter les champs couverts, les limites de la preuve et le resultat de
   l'investigation.

### Hors perimetre

- Reconstruction du contexte dans Python ou dans MCP.
- Retrieval supplementaire, RAG, embeddings ou vector store.
- Elargissement du scope ou de la selection EngineeringContext.
- Modification des frontieres de confiance ou promotion d'une sortie AI.
- Refonte generale du contrat EngineeringContext.
- Modification des scores existants de l'evaluation Story 0158.
- Correction fondee uniquement sur une recommandation AI non reproduite.

## Invariants

1. Java Core demeure l'unique autorite de construction et d'autorisation du
   contexte.
2. MCP transporte et expose uniquement le contexte autorise recu de Core.
3. Une absence de champ dans la sortie MCP n'est un defaut que si le meme champ
   est present dans le payload Core de reference.
4. La comparaison doit utiliser des entrees identiques : projet, intention,
   fichiers, story et options de scope.
5. Les tests ne doivent pas introduire de donnees ou de relations absentes du
   contexte autorise.
6. L'absence de perte reproductible doit produire une conclusion documentee,
   pas une correction speculative.

## Criteres d'acceptation proposes

1. Un protocole de comparaison deterministe est documente pour un appel Core et
   l'appel MCP equivalent.
2. Le protocole couvre explicitement `content`, symboles, timestamps et
   warnings, avec un resultat presence/absence/valeur pour chaque couche.
3. Le premier point de perte est identifie et ancre dans des payloads ou tests
   reproductibles, ou bien l'absence de perte est etablie dans le perimetre
   couvert.
4. Des tests echouent si un champ couvert present dans le payload Core est
   supprime par le client MCP ou la serialisation de l'outil.
5. Si une perte est confirmee, la correction conserve le scope, le grounding,
   les digests et la revision du contexte autorise.
6. Si aucune perte n'est confirmee, aucun changement de production n'est
   introduit uniquement pour satisfaire l'hypothese initiale.
7. Le cas de contexte tronque de Story 0158 est rejoue lorsque le chemin
   modifie est implique; les scores existants restent inchanges.
8. Les tests backend et MCP pertinents passent, et les limites de validation
   sont documentees.
9. La documentation distingue les faits observes, les hypotheses abandonnees
   et les risques residuels.

## Points d'inspection initiaux

- `backend/src/main/java/com/hopeful117/devlogai/engineeringcontext/EngineeringContextFacadeImpl.java`
- `backend/src/main/java/com/hopeful117/devlogai/engineeringcontext/mapper/EngineeringContextContractMapper.java`
- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/client/DevlogProjectContextClient.java`
- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/tool/EngineeringContextTool.java`
- Les contrats EngineeringContext partages dans `devlog-contracts`.

## Decisions a prendre avant implementation

1. Le jeu exact d'entrees de reference et les fixtures de comparaison.
2. La representation canonique utilisee pour comparer les payloads sans
   confondre omission legitime et perte de donnees.
3. Le niveau de test requis entre contrat unitaire, integration REST et appel
   MCP stdio.
4. La decision d'implementation eventuelle apres identification du premier
   point de perte.

## Regle de gouvernance

Cette Story est une proposition de cadrage. Elle ne declare ni implementation,
ni acceptation, ni autorisation de modifier le contrat ou l'architecture. Toute
correction doit rester minimale et etre validee par comparaison avec la sortie
Core autorisee.

## Artifacts

- `implementation-report.md`

## References

- [Story 0158](../0158-devlog-story-agent-response-specificity/story.md)
- [ADR-063](../../decisions/ADR-063.md)
- [ADR-067](../../decisions/ADR-067.md)
- [ADR-069](../../decisions/ADR-069.md)
