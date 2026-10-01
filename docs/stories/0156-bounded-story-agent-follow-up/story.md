# Story 0156 - Follow-up borne du DevLog Story Agent

## Statut

IMPLEMENTATION AUTORISEE - validation humaine de la Story 0156 accordee.

## Mode de collaboration prevu

- Python: pair coding.
- Java/Core: delegate, sauf indication contraire explicite.

## Objectif produit

Permettre a un utilisateur de poursuivre une execution du DevLog Story Agent
avec une question de suivi bornee, sans reconstruire le contexte, sans nouvelle
retrieval et sans transformer l'agent en boucle autonome. La reponse doit rester
transitoire, grounded et utile pour determiner le prochain pas d'implementation
ou de clarification.

## Contexte

Les Stories 0152 a 0155 ont etabli la projection versionnee, le protocole
asynchrone, le runtime haut niveau read-only et la lecture autorisee des
snapshots. Le runtime actuel produit une seule analyse structuree par execution
et expose deja recommandations, incertitudes et questions ouvertes.

Le gap suivant est l'absence de suivi sur cette analyse: l'utilisateur doit
relancer une execution complete pour demander une clarification ou approfondir
un point. Cette Story ajoute un suivi explicite au-dessus du snapshot immutable
existant, sans modifier les frontieres Core/Python ni introduire de memoire
conversationnelle generale.

## Dependances et autorite

- Story 0153 - Story Context Agent Protocol v1.
- Story 0154 - DevLog High-Level Agent Runtime borne et read-only.
- Story 0155 - lecture autorisee des snapshots Story Context Agent.
- ADR-006 - gouvernance de la connaissance generee par IA.
- ADR-063 - autorisation avant retrieval et frontiere de contexte partagee.
- ADR-067 - Java/Core proprietaire de la capacite et de l'autorisation.
- ADR-068 - references AI typees et grounding.
- ADR-069 - contexte canonique et projections non-elargissantes.

## Perimetre

- Ajouter une capacite de follow-up liee a un `snapshotId` existant.
- Authentifier et autoriser la lecture du snapshot avant toute execution.
- Reutiliser exactement le snapshot Core autorise comme contexte d'entree.
- Accepter une question de suivi et une guidance optionnelle bornee.
- Produire une reponse structuree contenant au minimum:
  - la reponse ou le statut `NOT_ESTABLISHED`;
  - les references de grounding autorisees;
  - les incertitudes et informations manquantes;
  - le prochain pas recommande;
  - les identites du snapshot et des digests de contexte/projection.
- Exposer la capacite par REST et MCP comme adaptateurs du meme service Core.
- Rendre les limites d'execution, les timeouts et les echecs observables sans
  exposer prompt, secret, token ou contenu repository dans les logs.

## Hors perimetre

- Boucle autonome de tool-calling ou de planification.
- Plus d'un follow-up par execution.
- Nouvelle collecte, retrieval, RAG, vector store ou seconde selection.
- Reconstruction Python du contexte ou acces Python a la base/repository.
- Memoire conversationnelle persistante ou session multi-tour generale.
- Modification de code, Story, decision, contexte ou connaissance trustee.
- Creation automatique de `ValidatableProposal`.
- Re-analyse automatique, scheduler, broker, outbox ou notification.
- UI dediee.

## Invariants

1. Le `snapshotId` est une identite et ne constitue jamais une permission.
2. L'autorisation est verifiee avant la materialisation du snapshot et avant
   l'appel au provider AI.
3. Le follow-up utilise le snapshot autorise; il ne reconstruit pas le contexte
   et ne peut pas elargir ses references, son scope ou son grounding.
4. Le projet, la story, la revision, `contextDigest` et `projectionDigest`
   restent ceux du snapshot Core.
5. Une reference de sortie appartient a l'allow-list du snapshot, sinon la
   reponse est rejetee fail-closed.
6. L'absence de preuve produit `NOT_ESTABLISHED`, jamais une affirmation
   factuelle non grounded.
7. Une execution de follow-up est read-only et ne modifie aucun objet trustee.
8. Le nombre de follow-ups, le budget et la taille de la question sont bornes.
9. REST et MCP produisent le meme resultat de domaine pour une meme requete.
10. Un retry identique est idempotent; un payload divergent est rejete sans
    mutation.

## Contrat de capacite propose

Entree:

- `snapshotId`;
- `question` obligatoire et bornee;
- guidance optionnelle et bornee;
- cle d'idempotence optionnelle.

Sortie:

- `followUpId`;
- `parentSnapshotId`;
- `snapshotId`;
- `status` (`COMPLETED`, `NOT_ESTABLISHED`, `FAILED` ou `TIMED_OUT`);
- `answer` structuree;
- `nextStep` structure;
- `evidenceReferences` typees;
- `uncertainties` et `missingInformation`;
- `contextDigest` et `projectionDigest` identiques au snapshot;
- diagnostics bornes et non sensibles.

`nextStep` est toujours present et porte un statut ferme parmi `RECOMMENDED`,
`NEEDS_CLARIFICATION` et `NO_SAFE_NEXT_STEP`. Il peut contenir des references
de grounding lorsqu'elles existent, mais ne declenche jamais automatiquement
une action.

La forme exacte des DTO et le transport REST/MCP restent a consolider avant
implementation. Le domaine ne recoit pas un `projectSlug` ou un nouveau scope
du caller: le projet est resolu depuis le snapshot Core.

## Criteres d'acceptation proposes

1. Un follow-up autorise sur un snapshot non expire retourne une reponse
   structuree liee au meme `snapshotId`, `contextDigest` et `projectionDigest`.
2. Une requete non authentifiee, non autorisee, inter-projet, inconnue ou expiree
   est rejetee selon la politique de divulgation de Story 0155, sans appel AI.
3. Les tests prouvent que le snapshot et l'allow-list sont charges avant le
   provider et qu'aucune nouvelle construction de contexte ou selection n'est
   executee.
4. Une question depassant la limite de taille ou le budget est rejetee
   deterministiquement sans mutation.
5. Une sortie contenant une reference inconnue, un scope divergent ou un digest
   divergent est rejetee fail-closed sans mutation.
6. Une question sans preuve suffisante retourne `NOT_ESTABLISHED`, les
   incertitudes et un prochain pas prudent, sans inference promue.
7. Le nombre maximal de follow-ups et le lien au snapshot sont verifies et
   testes; aucun follow-up implicite ou illimite n'est possible.
8. REST et MCP utilisent la meme capacite Core et exposent des resultats de
   domaine equivalents, y compris les erreurs.
9. Les retries identiques sont idempotents et les payloads divergents retournent
   une erreur sans seconde execution ni mutation.
10. Les logs et metriques ne contiennent ni question complete, prompt, snapshot,
    token, secret ni contenu repository.
11. Les suites de non-regression des Stories 0153, 0154 et 0155 restent vertes.

## Decisions a confirmer avant implementation

1. Un seul follow-up est autorise par snapshot dans cette version.
2. `nextStep` est obligatoire dans toute reponse, avec un statut explicite
   lorsqu'aucun prochain pas sûr n'est établi.
3. Le follow-up est asynchrone et reutilise le cycle de soumission, traitement,
   callback et lecture du runtime 0154.
4. La reponse est conservee dans un artefact d'execution immutable lie au
   snapshot, soumis au TTL et explicitement non trustee.
5. La question est limitee a 2 000 caracteres et la guidance a 1 000 caracteres
   apres serialisation JSON canonique. Ces limites sont appliquees avant l'appel
   provider. Le budget AI reutilise le budget fixe existant; tout depassement est
   rejete sans troncature silencieuse.
6. `nextStep` est toujours present et utilise uniquement les statuts
   `RECOMMENDED`, `NEEDS_CLARIFICATION` ou `NO_SAFE_NEXT_STEP`; il ne declenche
   aucune action automatiquement.
7. Le follow-up possede un `followUpId` distinct et un `parentSnapshotId`
   obligatoire; il ne cree pas une nouvelle identite de contexte.
8. Le follow-up est non chainable: seul le snapshot initial peut etre parent et
   aucune reponse de follow-up ne peut devenir une nouvelle source de contexte.
9. Le resultat initial immutable du snapshot parent peut etre reutilise pour
   repondre a une demande de clarification, sans devenir une nouvelle autorite
   ni une nouvelle connaissance.

## Regle de gouvernance

Cette Story ne donne aucune autorisation d'implementation, de mutation ou de
promotion de sortie AI. Une boucle autonome, une memoire persistante, une
nouvelle retrieval ou une action sur le repository necessitent une decision
architecturale et une Story distincte.

## References

- [Story 0153](../0153-story-context-agent-protocol-v1/story.md)
- [Story 0154](../0154-devlog-high-level-agent-runtime/story.md)
- [Story 0155](../0155-authorized-story-context-snapshot-read/story.md)
- [Investigation Next Agent Maturity Objective](../../investigations/next-agent-maturity-objective.md)
- [ADR-067](../../decisions/ADR-067.md)
- [ADR-068](../../decisions/ADR-068.md)
- [ADR-069](../../decisions/ADR-069.md)
- [ADR-070](../../decisions/ADR-070.md)
- [ADR-071](../../decisions/ADR-071.md)
