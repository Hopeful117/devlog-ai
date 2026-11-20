# Story 0213 - Implementation Plan

## Statut

PLAN COMPLETE - implementation completed; awaiting human acceptance.

Ce plan est derive du Repository Analysis de Story 0213 et des decisions
approuvees pour sa planification. L'implementation a ete autorisee
explicitement pendant la session.

## Gates prealables

Le proprietaire humain doit confirmer avant implementation :

1. L'abstraction minimale de mapping partage retenue par la Phase 1.
2. La liste exacte des endpoints classes comme observabilite best-effort ou
   resultat principal.
3. Le contrat final des categories `CORE_AUTHORIZATION_FAILED` et
   `RATE_LIMITED` dans les reponses MCP.
4. L'autorisation explicite d'implementation de Story 0213, obtenue dans cette
   session.

La planification et l'implementation restent distinctes de l'acceptance humaine.

## Phase 1 - Inventaire et table de politique

1. Lister toutes les interfaces HTTP declaratives et leurs appelants MCP.
2. Classer chaque operation comme read-only, mutation, observabilite ou
   Companion.
3. Definir une table de mapping par categorie et statut HTTP/transport.
4. Identifier les chemins qui doivent rester non-divulgants pour ne pas
   confirmer l'existence d'une ressource ou d'un projet.
5. Definir le point unique de classification sans transformer
   `ResourceSupport` en client HTTP generique.

Table de politique initiale :

| Erreur | Read-only | Mutation | Observabilite | Diagnostic |
|---|---|---|---|---|
| `400` | parametres invalides | operation rejetee | absorbee | `FUNCTIONAL` |
| `401` | auth Core | auth Core | absorbee | `CORE_AUTHENTICATION_FAILED` / data `CORE_AUTHENTICATION` |
| `403` | non-divulgant ou autorisation Core | autorisation Core | absorbee | `CORE_AUTHORIZATION_FAILED` / data `CORE_AUTHORIZATION` |
| `404` | ressource absente/non-divulgante | ressource absente | absorbee | `FUNCTIONAL` |
| `409` | conflit fonctionnel | conflit fonctionnel | absorbee | `FUNCTIONAL` |
| `429` | echec borne | echec borne | absorbee | `RATE_LIMITED` |
| timeout/`5xx` | indisponibilite Core | echec ferme | absorbee | `TRANSIENT_INFRASTRUCTURE` |

Aucune ligne de cette table n'autorise un retry Java dans cette Story.

## Phase 2 - Authentification et delegation

1. Verifier que le principal request-scoped est prioritaire lorsqu'il est
   disponible dans le contexte MCP.
2. Verifier que le fallback SecurityContext/configuration ne reutilise pas un
   principal d'une autre requete.
3. Normaliser la propagation de `Authorization` et
   `X-DevLog-Delegation` pour les interfaces qui en ont besoin.
4. Preserver le comportement fail-closed lorsque le bearer ou la validation du
   principal est absent.
5. Tester explicitement les appels avec delegation vide, presente et invalide.

## Phase 3 - Mapping et adaptateurs

1. Introduire le minimum de code partage necessaire pour transformer les
   exceptions HTTP et transport en categories MCP structurelles.
2. Migrer les mappings locaux de `ResourceSupport` et
   `SyncProjectHistoryTool` vers cette politique, en conservant les messages
   non-divulgants specifiques aux ressources.
3. Classer les erreurs Core avec `source=CORE` et une categorie stable.
4. Mapper `429` en `RATE_LIMITED` sans `Retry-After` exploitable par un retry.
5. Mapper les timeouts et `5xx` en indisponibilite Core sans exposer le corps
   de reponse.
6. Laisser les mutations en echec ferme ; ne pas reutiliser leur payload.

## Phase 4 - Observabilite et Companion

1. Conserver l'ingestion des evenements en best-effort.
2. Absorber explicitement `401`, `403`, `429`, timeout et `5xx` pour ce chemin.
3. Ne jamais remplacer le resultat ou l'erreur du tool principal par une
   erreur d'ingestion.
4. Appliquer la meme taxonomie aux appels Companion, avec la distinction entre
   resultat principal et appel secondaire.
5. Verifier que les logs restent bornes et ne contiennent pas de headers ou de
   payloads.

## Phase 5 - Tests

### Tests de politique

- matrice des statuts `400`, `401`, `403`, `404`, `409`, `429`, `5xx` ;
- timeout et exceptions de transport ;
- distinction Core/transport et categories structurelles ;
- absence de retry et absence de double appel.

### Tests d'identite

- principal request-scoped en contexte MCP ;
- principal SecurityContext en fallback ;
- bearer absent ou invalide ;
- delegation presente, absente et non validee ;
- aucun header sensible dans les logs.

### Tests par categorie d'appel

- ressources read-only ;
- outils de mutation ;
- ingestion best-effort ;
- Companion.

### Tests de non-regression

- `ResourceSupportTest` ;
- tests de `SyncProjectHistoryTool` et `RegisterProjectTool` ;
- tests `McpToolObservabilityService` ;
- suite complete du module `mcp-server` ;
- `git diff --check`.

## Fichiers probables

- `mcp-server/src/main/java/.../config/DevlogBackendClientConfiguration.java` ;
- `mcp-server/src/main/java/.../config/BearerTokenAuthInterceptor.java` ;
- `mcp-server/src/main/java/.../resource/ResourceSupport.java` ;
- `mcp-server/src/main/java/.../tool/SyncProjectHistoryTool.java` ;
- `mcp-server/src/main/java/.../tool/RegisterProjectTool.java` ;
- `mcp-server/src/main/java/.../service/McpToolObservabilityService.java` ;
- interfaces `client/Devlog*Client.java` si necessaire ;
- tests Java MCP ;
- `TOOLS.md`.

## Fichiers exclus par defaut

- `tools/devlog_mcp/runtime.py` ;
- Keycloak et configuration OAuth ;
- Core authorization et delegation ;
- AI Engine provider credentials ;
- contrats de taches et bindings ;
- tout mecanisme de retry Java.

## Risques

1. Une centralisation trop large pourrait effacer les protections de
   non-divulgation des ressources.
2. Un fallback de principal mal borne pourrait transmettre un bearer d'un autre
   contexte de requete.
3. Un mapping uniforme pourrait exposer un `403` ou un `404` que le contrat
   d'une ressource exige de masquer.
4. Une erreur d'observabilite pourrait involontairement devenir bloquante si
   elle traverse l'adaptateur commun.
5. Une exception de timeout non mappee pourrait contourner la taxonomie HTTP.

## Criteres de sortie du plan

- chaque appel Core est classe et rattache a une politique ;
- les statuts et exceptions de transport ont un mapping explicite ;
- l'autorite de propagation du principal est unique et testee ;
- aucun retry Java n'est introduit ;
- le best-effort d'observabilite est preserve ;
- les decisions de divulgation `403`/`404` sont explicites ;
- l'implementation reste en attente d'une autorisation humaine distincte.

## Autorisation

Implementation autorisee et realisee. Commit, push, merge et acceptance humaine
restent des gates distinctes.
