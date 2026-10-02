# Story 0157 - Automatiser la recuperation des resultats du DevLog Story Agent

## Statut

ACCEPTEE PAR L'HUMAIN - IMPLEMENTATION AUTORISEE

Cette Story formalise un besoin de consommation du protocole existant. Son
acceptation valide le perimetre et les criteres proposes. L'implementation est
autorisee dans le perimetre defini; aucune mutation ou promotion de sortie AI
n'est autorisee par cette Story.

## Objectif produit

Permettre a un consommateur du DevLog Story Agent d'obtenir automatiquement le
resultat d'une execution asynchrone sans devoir relancer manuellement une
requete apres une reponse `SUBMITTED`.

Le consommateur doit orchestrer le cycle existant de soumission et de lecture
du snapshot, dans des limites deterministes et observables, sans transformer
le Story Agent en boucle autonome.

## Contexte

Le protocole Story Context Agent v1 est asynchrone. Une soumission retourne une
identite d'execution et un statut `SUBMITTED`; le resultat est ensuite
accessible par lecture du snapshot. Les Stories 0153 a 0156 etablissent le
contrat versionne, le runtime borne, la lecture autorisee et le follow-up
borne.

Le comportement actuel expose cette asynchronie directement au consommateur,
qui doit soumettre une seconde requete pour connaitre le resultat. Cette Story
ajoute une orchestration de polling au niveau du consommateur, sans modifier
le contrat de contexte, la projection, le snapshot ou les frontieres Core /
Python.

## Dependances et autorite

- Story 0153 - Story Context Agent Protocol v1.
- Story 0154 - DevLog High-Level Agent Runtime borne et read-only.
- Story 0155 - Autoriser la lecture des snapshots Story Context Agent.
- Story 0156 - Follow-up borne du DevLog Story Agent.
- ADR-063 - autorisation avant retrieval et frontiere de contexte partagee.
- ADR-067 - Java/Core proprietaire de la capacite et de l'autorisation.
- ADR-069 - contexte canonique et projections non-elargissantes.

## Perimetre

- Fournir une operation de consommation qui soumet une execution puis attend
  automatiquement son etat terminal.
- Reutiliser exactement l'identite retournee par la soumission pour les
  lectures du snapshot.
- Conserver la meme cle d'idempotence pour les retries d'une meme demande,
  lorsqu'une cle est utilisee par le contrat appelant.
- Utiliser un intervalle de polling, un nombre maximal de tentatives et un
  timeout bornes et configurables selon les conventions du consommateur.
- Retourner le resultat terminal ou un statut explicite de timeout sans
  masquer l'etat connu de l'execution.
- Propager les erreurs d'authentification, d'autorisation, de contrat et de
  transport sans relaxation de securite.
- Exposer des diagnostics bornes permettant de distinguer soumission,
  attente, lecture, succes, timeout et echec.
- Ajouter les tests unitaires, d'integration et de non-regression necessaires
  au consommateur concerne.

## Hors perimetre

- Modification du protocole Story Context Agent v1.
- Modification de `snapshotId`, `aiTaskId`, des digests ou de l'immutabilite.
- Nouvelle collecte, retrieval, selection, RAG, vector store ou projection.
- Boucle autonome de tool-calling, planification ou action sur le repository.
- Polling sans limite, retry infini ou attente bloquante non observable.
- Modification de l'autorite Java/Core ou reconstruction de contexte en Python.
- Nouvelle memoire conversationnelle ou session multi-tour generale.
- Modification de l'UI si elle n'est pas le consommateur explicitement vise.
- Mutation de Story, decision, contexte ou connaissance trustee.

## Invariants

1. Une execution de polling ne cree qu'une seule soumission logique.
2. Toutes les lectures ciblent l'identite retournee par la soumission.
3. Un retry identique reste idempotent; un payload divergent suit le contrat
   d'erreur existant.
4. Le polling s'arrete sur tout etat terminal, timeout ou erreur non retryable.
5. Le timeout ne transforme jamais une execution inconnue en echec confirme.
6. Aucune erreur d'autorisation ou de grounding n'est contournee par polling.
7. Le consommateur ne fabrique ni contexte, ni projection, ni resultat.
8. Le comportement reste borne, observable et compatible avec les limites du
   runtime existant.

## Contrat de consommation propose

Entree:

- les parametres de soumission existants;
- une cle d'idempotence lorsque le contrat appelant la supporte;
- une politique de polling bornee, ou la configuration par defaut du
  consommateur.

Sortie:

- le resultat terminal existant lorsque l'execution aboutit;
- l'etat connu et un statut `TIMED_OUT` lorsque la limite est atteinte;
- les erreurs du contrat existant lorsqu'elles sont non retryables;
- des diagnostics non sensibles et limites.

La forme exacte du DTO, du transport et de la politique de backoff reste a
consolider avant implementation. Cette Story ne redefinit pas les statuts
canoniques du protocole.

## Criteres d'acceptation proposes

1. Une consommation valide soumet une seule execution et retourne son resultat
   terminal sans intervention manuelle du caller.
2. Les etats intermediaires declenchent des lectures bornees du snapshot avec
   la meme identite d'execution.
3. Un etat terminal de succes retourne le snapshot/resultat existant sans
   transformation semantique non autorisee.
4. Un etat terminal d'echec est retourne au caller avec son diagnostic
   contractuel, sans retry supplementaire non prevu.
5. La limite de temps ou de tentatives retourne `TIMED_OUT` avec l'identite et
   le dernier etat connu, sans pretendre que l'execution a echoue.
6. Les retries identiques ne provoquent pas de seconde soumission logique et
   les payloads divergents suivent le contrat d'idempotence existant.
7. Les erreurs d'authentification, d'autorisation, de validation et de
   transport non retryables sont propagees sans contournement.
8. Les bornes de polling sont testees: intervalle, nombre de lectures,
   timeout, arret terminal et absence de boucle infinie.
9. Les tests prouvent qu'aucune nouvelle construction de contexte, selection,
   mutation de snapshot ou promotion de connaissance n'est executee.
10. Les logs et metriques ne contiennent ni prompt, token, secret, contenu
    repository ni payload complet de la demande.
11. Les suites de non-regression des Stories 0153, 0154, 0155 et 0156 restent
    vertes.

## Decisions a confirmer avant implementation

1. Le consommateur cible et le transport initial de cette Story.
2. Les valeurs par defaut et les limites maximales de timeout, tentatives et
   intervalle de polling.
3. La politique de backoff et les statuts retryables.
4. Le mapping public du timeout et du dernier etat connu.
5. La strategie de compatibilite lorsque le consommateur ne supporte pas la
   cle d'idempotence.

## Regle de gouvernance

Cette Story ne change pas le protocole asynchrone etabli par Story 0153.
L'autorisation accordee porte uniquement sur le perimetre de consommation
defini ici. Toute evolution du contrat Core, des statuts, de l'autorisation,
du grounding ou de la persistance doit etre documentee et autorisee
separement.

## References

- [Story 0153](../0153-story-context-agent-protocol-v1/story.md)
- [Story 0154](../0154-devlog-high-level-agent-runtime/story.md)
- [Story 0155](../0155-authorized-story-context-snapshot-read/story.md)
- [Story 0156](../0156-bounded-story-agent-follow-up/story.md)
- [ADR-063](../../decisions/ADR-063.md)
- [ADR-067](../../decisions/ADR-067.md)
- [ADR-069](../../decisions/ADR-069.md)
