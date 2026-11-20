# Repository Analysis - Story 0217

## Scope

La Story doit ajouter un retry borne pour les reponses `429` dans le runtime
Python, en respectant `Retry-After` lorsqu'une valeur valide et sure est
disponible. Elle ne doit pas introduire de scheduler durable, de queue, de
broker, de retry Java independant ou de rejeu d'une operation non idempotente.

## Repository State

- Le runtime Python est dans `tools/devlog_mcp/runtime.py`.
- Les tests runtime sont dans `tools/devlog_mcp/tests/test_runtime.py`.
- Le mapping Java des appels Core est dans
  `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/service/CoreCallPolicy.java`.
- La matrice Java est testee par
  `mcp-server/src/test/java/hopefull117/devlogai_mcp/mcp_server/service/CoreCallPolicyTest.java`.
- Le worktree contient deja les changements non lies de la Story 0216 et la
  materialisation de la Story 0217. Ces changements ne doivent pas etre
  reverts ou melanges silencieusement a l'implementation.

## Current Behavior

### Python runtime

- `RATE_LIMITED` est catalogue comme `retryable: false`.
- `jsonrpc_error_reason` et `jsonrpc_diagnostic` classent correctement la
  categorie `RATE_LIMITED`.
- `retry_authentication` ne traite que l'echec d'authentification transport
  explicite ; un `429` Core ne declenche donc pas de renouvellement bearer.
- Le retry existant du token endpoint est borne a une tentative et utilise
  `RuntimeSession.token_retry_backoff`.
- Les operations portent deja `retry_safe`, `retry_count`, `operation_id` et
  une generation, ce qui fournit les primitives necessaires pour preserver
  l'identite logique et la transition terminale.
- L'arret est deja represente par `stop_requested` et utilise par l'attente du
  retry token.

### Java MCP server

- `CoreCallPolicy.map` classe HTTP `429` comme `RATE_LIMITED`.
- La classification expose actuellement `retryable: false` et aucune metadata
  `Retry-After`.
- `RestClientResponseException` contient les headers HTTP, mais le mapping
  actuel ne les transporte pas dans le `McpError`.
- Le code Java ne doit pas effectuer le retry. Il peut uniquement propager une
  valeur derivee, validee et bornee si le contrat retenu l'exige.

## Integration Finding

Le runtime Python recoit une erreur JSON-RPC provenant du serveur MCP et non
l'exception HTTP Core. Le header `Retry-After` est donc actuellement perdu
entre `CoreCallPolicy` et `runtime.py`.

Une implementation qui ne modifierait que Python ne pourrait pas respecter
`Retry-After` pour un `429` Core. Une implementation qui ajouterait un retry
dans `CoreCallPolicy` violerait l'autorite unique du runtime et les Stories
0212/0213.

La solution a planifier doit donc choisir un contrat minimal de propagation,
par exemple une duree en secondes deja validee et plafonnee dans les donnees
JSON-RPC, sans transmettre le header brut ni de details backend. Cette
decision doit rester compatible avec les contraintes de non-divulgation et
etre validee dans l'Implementation Plan.

## Relevant Constraints

- ADR-081 : retry interdit par defaut ; categorie, budget, contexte et
  idempotence obligatoires.
- Story 0212 : runtime Python autorite unique ; pas de retry Java concurrent ;
  `429` reporte a une Story ulterieure.
- Story 0213 : les appels Java/MCP ne font pas de retry automatique.
- Story 0214 : une cle technique locale ne suffit pas a prouver l'idempotence
  metier ; les operations ambigues restent fail-closed.
- Story 0215 : les retries existants ne doivent pas etre modifies par une
  simple Story de diagnostics.
- Story 0216 : la validation ne modifie ni retry, ni budget, ni politique.

## Affected Files

Probable files, a confirmer par l'Implementation Plan :

- `tools/devlog_mcp/runtime.py`
- `tools/devlog_mcp/tests/test_runtime.py`
- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/service/CoreCallPolicy.java`
- `mcp-server/src/test/java/hopefull117/devlogai_mcp/mcp_server/service/CoreCallPolicyTest.java`
- documentation runtime ou `TOOLS.md` uniquement si le contrat utilisateur
  change.

## Risks

1. Propager une valeur HTTP brute pourrait divulguer un header ou introduire
   un contrat non borne.
2. Marquer globalement `RATE_LIMITED` comme retryable pourrait autoriser le
   rejeu de mutations ambigues.
3. Reutiliser le retry d'authentification pourrait renouveler inutilement un
   bearer et casser la separation Core/transport.
4. Une attente bloquante non interruptible pourrait rendre l'arret du runtime
   non deterministe.
5. Une nouvelle politique locale pourrait diverger du budget de Story 0212.

## Required Decisions for Implementation Plan

- Operations exactes autorisees au rejeu apres `429`.
- Budget maximal et plafond de delai.
- Semantique d'une valeur `Retry-After` absente, invalide ou excessive.
- Forme de metadata JSON-RPC minimale et non-divulgante.
- Visibilite de la tentative et du delai dans les diagnostics.

## Validation Strategy

- Tests Python provider-free du parsing, du budget, de l'idempotence, de
  l'arret et de la preservation de l'identite.
- Tests Java provider-free du mapping `Retry-After` vers une metadata bornee,
  sans retry cote Java.
- Tests de non-renouvellement du bearer pour `429` Core.
- Tests de redaction et absence de header brut, token ou payload.
- `git diff --check`.
- Suites ciblees Python runtime et MCP Java.

## DevLog Context

`DEVLOG_CONTEXT_ERROR`: aucune capacite DevLog MCP exploitable n'est exposee
dans cette session. Cette analyse repose sur les artefacts canoniques du
repository et l'etat courant du code ; le repository reste l'autorite pour
l'etat physique actuel.

## Gate

Repository Analysis complete. L'Implementation Plan ne doit pas etre produit
avant approbation humaine explicite de cette analyse.
