# Story 0155 - Autoriser la lecture des snapshots Story Context Agent

## Statut

IMPLEMENTATION AUTORISEE - ADR-070 et ADR-071 acceptees; implementation
autorisee par le proprietaire humain le 2026-10-01.

## Objectif produit

Permettre a un principal authentifie et autorise de lire un snapshot immutable
de Story Context Agent, tout en empechant l'acces inter-projet, la divulgation
d'existence non autorisee et tout contournement par REST, MCP ou identifiant de
snapshot.

## Dependances et autorite

- Story 0154 - snapshots Story Context Agent et TTL de 30 jours
- ADR-063 - autorisation avant retrieval et frontiere de contexte partagee
- ADR-067 - Java/Core est proprietaire de l'autorisation
- ADR-069 - contexte canonique et projections non-elargissantes
- ADR-070 - modele principal/projet, acceptee
- ADR-071 - capacite de lecture autorisee, acceptee

Les decisions d'architecture et l'autorisation d'implementation sont acquises.

## Perimetre

- Definir le principal authentifie attendu par la capacite de lecture.
- Ajouter une autorisation projet/snapshot au niveau Core.
- Faire passer REST et MCP par la meme capacite read-only.
- Appliquer l'autorisation avant la materialisation detaillee du snapshot.
- Preserver le TTL de 30 jours et le comportement immuable apres expiration.
- Definir et tester la politique de divulgation pour snapshot inconnu,
  inaccessible et expire.
- Ajouter une observabilite minimale ne contenant ni snapshot, prompt, token,
  secret ni contenu repository.

## Hors perimetre

- Choix ou implementation complete d'un fournisseur d'identite si non decide
  par ADR-070.
- UI, gestion d'organisation, facturation ou multi-tenant generique.
- Mutation de snapshot, contexte, grounding ou connaissance trustee.
- Modification du callback HMAC Core-to-Core.
- Autorisation des autres endpoints DevLog.
- Nouvelle retrieval, RAG, vector store ou composition de contexte.

## Invariants

1. `snapshotId == aiTaskId` reste une identite, jamais une permission.
2. Le projet autorise est celui du `AiTask` en Core, pas celui fourni pour
   construire la reponse.
3. Une requete non authentifiee echoue fail-closed.
4. Un principal autorise sur un projet ne peut pas lire le snapshot d'un autre
   projet.
5. REST et MCP appliquent la meme politique et la meme capacite Core.
6. Un snapshot expire n'est ni relu comme actif, ni renouvele, ni modifie.
7. Une lecture ne peut pas promouvoir, modifier ou enrichir une connaissance.
8. Les logs d'autorisation ne contiennent pas de donnees sensibles.

## Decisions retenues avant implementation

- `principalKind` vaut `HUMAN`, `AI_AGENT` ou `SYSTEM`; aucun `type` redondant;
- les roles de lecture sont `PROJECT_READER` et `PROJECT_OWNER`;
- REST et MCP transmettent le principal authentifie a la capacite Core commune;
- les snapshots inconnus, inaccessibles, inter-projets ou expires retournent
  `404` publiquement;
- les environnements locaux et de test n'ont aucun bypass implicite;
- les diagnostics d'autorisation restent limites aux identifiants et raisons
  categorisees, sans contenu sensible.

## Contrat d'erreurs

La capacite Core retourne des resultats de domaine independants du transport.
REST et MCP utilisent ensuite le meme resultat sans modifier la decision
d'autorisation.

| Situation | Resultat REST | Resultat MCP |
|-----------|---------------|--------------|
| Principal absent ou invalide | `401 Unauthorized` | erreur d'authentification |
| Snapshot inconnu | `404 Not Found` | erreur ressource indisponible |
| Snapshot inaccessible ou autre projet | `404 Not Found` | erreur ressource indisponible |
| Snapshot expire | `404 Not Found` | erreur ressource indisponible |
| Snapshot autorise et non expire | `200 OK` | resultat du snapshot |

Les cas inconnus, inaccessibles, inter-projets et expires partagent le meme
resultat public afin de ne pas divulguer l'existence d'un snapshot. Les
diagnostics internes peuvent distinguer la cause, mais ne doivent contenir ni
snapshot, prompt, token, secret ni contenu repository.

Une erreur de format de requete independante de l'autorisation peut conserver
le mapping standard du transport, mais elle ne doit jamais declencher une
materialisation detaillee du snapshot.

## Criteres d'acceptation proposes

1. Un principal authentifie avec le droit de lecture du projet peut lire un
   snapshot non expire.
2. Une requete non authentifiee est rejetee selon le contrat approuve, sans
   lecture detaillee du snapshot.
3. Un principal sans droit sur le projet est rejete sans divulguer le contenu
   ni, sauf decision contraire, l'existence du snapshot.
4. Un snapshot d'un autre projet ne peut pas etre lu en modifiant le slug,
   l'identifiant ou les parametres de transport.
5. Un snapshot expire est rejete sans mutation et sans resurrection.
6. REST et MCP produisent le meme resultat de domaine pour la meme identite et
   le meme snapshot.
7. Les tests prouvent que l'autorisation est executee avant la projection
   detaillee et avant tout chargement de contenu sensible.
8. Les logs et metriques d'autorisation n'exposent ni prompt, snapshot,
   token, secret ni contenu repository.
9. Les tests de regression de Story 0154 restent verts.

## Plan de prochaine session

1. Faire accepter ou corriger ADR-070.
2. Faire accepter ou corriger ADR-071 en consequence.
3. Completer le contrat d'erreurs et le mapping REST/MCP.
4. Inspecter les points d'entree et tests existants a la lumiere des ADRs
   acceptes.
5. Obtenir l'autorisation explicite d'implementation de Story 0155.

## References

- [Story 0154](../0154-devlog-high-level-agent-runtime/story.md)
- [Story 0153](../0153-story-context-agent-protocol-v1/story.md)
- [ADR-063](../../decisions/ADR-063.md)
- [ADR-067](../../decisions/ADR-067.md)
- [ADR-069](../../decisions/ADR-069.md)
- [ADR-070](../../decisions/ADR-070.md)
- [ADR-071](../../decisions/ADR-071.md)
