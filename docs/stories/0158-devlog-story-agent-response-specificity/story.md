# Story 0158 - Rendre les reponses du DevLog Story Agent specifiques et actionnables

## Statut

IMPLEMENTATION AUTORISEE - autorisation humaine recue le 2026-10-03.

Cette Story formalise une priorite produit issue de l'utilisation du runtime
haut niveau. Elle ne vaut pas autorisation d'implementation, de modification du
prompt, de changement de contrat ou de changement d'architecture.

## Objectif produit

Le DevLog Story Agent est le point de contact haut niveau des autres agents
pour obtenir une analyse d'ingenierie. La priorite est donc la qualite utile de
sa reponse: pour une question donnee, il doit restituer une analyse specifique
au projet, au scope et a l'intention demandes, plutot qu'une synthese generale
qui pourrait s'appliquer a n'importe quel repository.

Le goal officiel est:

> Fournir aux agents appelants une reponse directement pertinente pour leur
> question, ancree dans les preuves autorisees du contexte demande, avec les
> limites et les informations manquantes explicitement indiquees.

Le point de contact unique signifie ici une surface d'analyse haut niveau pour
les autres agents. Il ne supprime ni les transports REST/MCP, ni les capacites
Core sous-jacentes, ni l'autorite Java/Core definies par ADR-067.

## Probleme

Les executions actuelles peuvent etre structurellement valides et grounded,
mais produire surtout des reponses de niveau general lorsque le contexte est
 large ou tronque. Ce comportement est une regression fonctionnelle comparee
 a l'utilisation directe des capacites DevLog par des agents via MCP: la
 reponse perd la question concrete, les elements pertinents et le prochain pas
 utile.

Une reponse riche en sections mais faible en specificite ne satisfait pas le
besoin du consommateur. La validite du schema, la confiance du modele ou la
presence de references ne suffisent donc pas a etablir la qualite attendue.

## Dependances et autorite

- Story 0154 - runtime haut niveau borne et read-only.
- Story 0156 - follow-up borne du Story Agent.
- Story 0157 - recuperation automatisee des resultats.
- ADR-006 - gouvernance de la connaissance generee par IA.
- ADR-063 - contexte canonique et composition deterministe.
- ADR-066 - evaluation replayable et evaluation qualitative.
- ADR-067 - separation Core/Python et frontiere d'autorite.
- ADR-069 - projections non-elargissantes.

Cette Story doit etre reconcilee avec les investigations existantes sur la
qualite d'analyse, notamment `story-context-agent-to-bounded-analyst-and-
retrieval-strategy.md`. Elle ne reouvre pas les decisions acceptees et ne
presume pas qu'une retrieval supplementaire ou une boucle autonome soit la
solution.

## Perimetre

- Rendre l'intention et la question du caller visibles dans l'analyse retournee.
- Prioriser les preuves et artefacts directement pertinents pour cette question.
- Distinguer explicitement les faits specifiques au projet, les interpretations,
  les recommandations et les generalites de contexte.
- Signaler quand le contexte est large, tronque, insuffisant ou hors sujet au
  lieu de remplir la reponse par du contenu generique.
- Produire des reponses exploitables par un agent appelant pour poursuivre
  l'investigation ou determiner le prochain pas.
- Etendre l'evaluation ADR-066 avec des cas de questions concretes et un
  oracle qualitatif de specificite.
- Mesurer la specificite sans relacher le grounding, le scope, les budgets ou
  la classification de confiance.

## Hors perimetre

- Retour aux appels directs des outils DevLog comme architecture principale.
- Contournement du DevLog Story Agent ou creation d'un second analyste parallele.
- Reconstruction du contexte, retrieval non autorisee, acces direct Python a la
  base ou au repository.
- Boucle autonome non bornee, delegation multi-agent ou memoire
  conversationnelle persistante.
- Promotion d'une sortie AI en connaissance trustee ou creation automatique de
  `ValidatableProposal`.
- Changement de transport REST/MCP uniquement pour masquer un defaut de qualite.
- Ajout de RAG, embeddings ou vector store sans mesure demontrant un deficit de
  rappel qui ne peut pas etre resolu par les capacites deterministes.

## Invariants

1. Java/Core reste l'autorite du scope, de la selection, du grounding, de la
   revision et de l'acceptation du resultat.
2. Python n'accede pas directement a la base ou au repository et ne construit
   pas de contexte parallele.
3. Une reponse plus specifique ne peut pas introduire de preuve, relation ou
   fait absent du contexte autorise.
4. Une question sans preuve suffisante produit `NOT_ESTABLISHED`, une incertitude
   explicite ou une question de clarification, jamais une generalite presentee
   comme reponse.
5. Les sorties restent transitoires, non trustees et read-only.
6. Les contraintes de budget, de digests, de revision, de scope et de retry
   restent inchangees sauf decision explicite ulterieure.
7. La surface haut niveau reste unique pour les agents appelants, tandis que
   REST et MCP restent des adaptateurs equivalents de la meme capacite Core.

## Criteres d'acceptation proposes

1. Pour chaque scenario d'evaluation, la reponse reprend explicitement la
   question ou l'objectif demande et ne se limite pas a une description
   generale du projet.
2. Les conclusions principales citent des preuves autorisees directement
   pertinentes pour la question; les preuves seulement generales sont
   distinguees des preuves decisives.
3. Une reponse contenant uniquement des faits generiques alors qu'une question
   ciblee est posee echoue l'evaluation qualitative de specificite.
4. Lorsqu'aucune preuve specifique n'est disponible, la reponse le declare,
   retourne les informations manquantes et n'invente pas de conclusion.
5. Pour une question actionnable, la reponse fournit un prochain pas ou une
   question de clarification relie a la preuve; elle n'affirme pas qu'une
   action est autorisee.
6. Les tests verifient que l'amelioration de specificite ne permet aucune
   reference inconnue, relation fabriquee, extension de scope ou violation de
   trust boundary.
7. L'evaluation compare le runtime haut niveau a une baseline d'utilisation
   directe des capacites DevLog sur les memes questions et le meme contexte.
8. Les scenarios couvrent au minimum une question large, une question ciblee
   par fichier/composant, une question sans preuve suffisante et un contexte
   tronque.
9. Les criteres de reussite mesurent au minimum pertinence, specificite,
   couverture des preuves, taux d'affirmations generiques non utiles,
   abstention correcte et utilite du prochain pas.
10. Les tests de non-regression des Stories 0154 a 0157 restent verts.

## Decisions a prendre avant implementation

1. Le format de la baseline MCP et le jeu de questions de reference.
2. La methode de notation qualitative et l'oracle attendu pour la specificite.
3. La repartition entre selection deterministe Core, contrat de sortie et
   instructions de generation.
4. Le seuil minimal d'amelioration accepte avant de declarer le goal atteint.
5. La necessite eventuelle d'une retrieval ciblee, uniquement apres mesure du
   deficit de pertinence du contexte initial.

## Regle de gouvernance

Cette Story officialise un objectif de qualite et ne declare ni implementation
ni acceptation. Toute modification du contrat, de l'architecture de retrieval,
des budgets ou des frontieres de confiance doit etre explicitement decidee et
autorisee dans le cadre approprie.

## References

- [Story 0154](../0154-devlog-high-level-agent-runtime/story.md)
- [Story 0156](../0156-bounded-story-agent-follow-up/story.md)
- [Story 0157](../0157-automatiser-recuperation-resultats-devlog-story-agent/story.md)
- [ADR-006](../../decisions/ADR-006.md)
- [ADR-063](../../decisions/ADR-063.md)
- [ADR-066](../../decisions/ADR-066.md)
- [ADR-067](../../decisions/ADR-067.md)
- [ADR-069](../../decisions/ADR-069.md)
- [Investigation bounded analyst](../../investigations/story-context-agent-to-bounded-analyst-and-retrieval-strategy.md)
