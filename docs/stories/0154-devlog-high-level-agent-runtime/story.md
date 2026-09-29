# Story 0154 - DevLog High-Level Agent Runtime borne et read-only

## Statut

IMPLÉMENTATION AUTORISÉE — pair coding avec validation humaine continue.

Les decisions operationnelles de Story 0153 sont maintenant retenues : HMAC
Core, TTL snapshots de 30 jours, `409 Conflict`, budget fixe v1,
`selectedKnowledge` maintenu et polling MCP. Elles devront etre implementees
et testees dans le perimetre de cette Story.

## Objectif produit

Fournir un point d'entree haut niveau pour le premier agent DevLog. Cet agent
orchestrera une analyse preparee d'Engineering Story en reutilisant le
protocole Story Context Agent v1, sans reconstruire le contexte, sans seconde
selection et sans modifier les donnees du projet.

Le runtime doit rendre explicite la sequence :

```text
request -> get_projection -> submit_task -> get_snapshot -> structured result
```

Le resultat reste transitoire et non truste. Java/Core conserve l'autorite sur
le contexte, le grounding, le scope, le cycle de vie et l'acceptation du
callback. Python conserve l'execution probabiliste et la validation defensive.

## Dependances et autorite

- Story 0153 - Story Context Agent Protocol v1
- ADR-006 - gouvernance de la connaissance generee par IA
- ADR-067 - premiere capacite agent et separation Core/Python
- ADR-068 - references AI typees et namespaces de grounding
- ADR-069 - frontiere canonique du contexte Engineering
- Stories 0120 et 0122 - communication agent et premier reveil autonome borne

Si Story 0153 n'est pas acceptee humainement, Story 0154 reste en refinement.

## Perimetre

- Definir une capacite applicative haut niveau nommee DevLog Story Agent.
- Reutiliser le protocole v1 et ses quatre operations existantes.
- Orchestrer une seule analyse de Story par execution.
- Exposer un resultat structure compose de faits, interpretations,
  recommandations, incertitudes et questions ouvertes.
- Conserver les identites `contextDigest`, `projectionDigest` et les
  references de grounding dans toute la trace d'execution.
- Rendre les echecs, timeouts, retries et resultats `NOT_ESTABLISHED`
  observables sans les transformer en connaissance de confiance.
- Fournir une interface MCP et une interface REST equivalentes par des
  adaptateurs minces au-dessus de la meme capacite Core.
- Ajouter une preuve end-to-end deterministe avec provider mock.

## Hors perimetre

- Boucle autonome de tool-calling ou de planification.
- Retrieval, RAG, embeddings, vector store ou seconde selection.
- Acces Python direct a la base ou au repository.
- Modification de code, de Story, de decision ou de connaissance trustee.
- Creation automatique de `ValidatableProposal`.
- Memoire persistante d'agent ou orchestration multi-agent.
- Scheduler, broker, queue, outbox ou execution en arriere-plan non bornee.
- UI nouvelle ou changement de provider AI.
- Suppression de `selectedKnowledge` legacy.

## Invariants

1. Java/Core construit et autorise le contexte une seule fois.
2. Le runtime ne peut ajouter scope, evidence, relation, trust ou grounding.
3. `snapshotId == aiTaskId` est conserve.
4. Une execution est read-only vis-a-vis du contexte canonique et de la
   connaissance trustee.
5. Une sortie AI est transitoire et explicitement non trustee.
6. Un retry identique est idempotent ; un payload divergent est rejete.
7. Toute affirmation factuelle est liee a une reference autorisee.
8. L'absence de preuve produit `NOT_ESTABLISHED`, jamais une inference promue.
9. MCP et REST appellent la meme capacite et exposent le meme contrat.

## Decisions retenues et controles avant implementation

Les decisions suivantes sont retenues :

- secret HMAC detenu par Core pour authentifier le callback ;
- TTL de 30 jours et politique d'acces controlee pour les snapshots ;
- `409 Conflict` pour les payloads divergents, sans mutation ;
- budget fixe et deterministe par intent ;
- maintien de `selectedKnowledge` sans retrait dans cette version ;
- polling MCP via `submit_task` puis `get_snapshot`.

L'implementation doit verifier ces choix et ne doit pas les elargir sans une
nouvelle decision explicite.

## Contrat de capacite propose

Entree :

- `projectSlug` ;
- `storyId` nullable ;
- `intent` ;
- `files` ;
- guidance optionnelle ;
- cle d'idempotence optionnelle.

Sortie :

- statut terminal ou explicitement en cours ;
- `aiTaskId` et `snapshotId` ;
- `contextDigest` et `projectionDigest` ;
- scope, freshness et accounting ;
- analyse structuree non trustee ;
- evidence references et incertitudes ;
- diagnostics d'execution sans prompt, secret ou contenu sensible dans les
  logs.

## Criteres d'acceptation proposes

1. Une requete valide execute la sequence projection, soumission et lecture
   snapshot sans seconde construction de contexte.
2. REST et MCP produisent le meme resultat pour une meme requete et un meme
   snapshot.
3. Une execution ne peut pas ecrire dans le contexte canonique, les facts,
   observations, insights, decisions, Stories ou relations trustees.
4. Les digests et le scope du snapshot, du prompt et du resultat sont egaux
   ou rejetes fail-closed.
5. Les retries identiques sont idempotents et les payloads divergents
   retournent une erreur sans mutation.
6. Les references de sortie sont un sous-ensemble des references autorisees
   par Core ; une reference inconnue est rejetee.
7. Les erreurs provider, timeout et callback sont exposees comme diagnostics
   bornes et ne deviennent pas une connaissance trustee.
8. Un scenario sans preuve produit une analyse vide ou `NOT_ESTABLISHED` avec
   avertissement explicite.
9. Les tests unitaires, wire, integration MCP/REST et replay deterministe
   passent avec le provider mock.
10. Aucun tool loop, scheduler, broker, memoire persistante ou mutation de
    projet n'est introduit.

## Plan d'implementation futur

1. Faire accepter humainement Story 0153.
2. Ecrire le contrat de capacite Core et sa machine d'etats minimale.
3. Implementer l'orchestrateur read-only au-dessus du protocole v1.
4. Brancher les adaptateurs REST et MCP communs.
5. Ajouter le scenario replayable et les tests de non-mutation.
6. Executer les suites Python, backend, MCP et le test end-to-end mock.
7. Faire une validation humaine sur une Engineering Story reelle.

## Regle de gouvernance

Cette Story ne donne aucune autorisation de promouvoir une sortie AI en
connaissance trustee. Toute extension vers des actions, une memoire ou une
boucle autonome necessite une nouvelle decision architecturale et une Story
distincte.
