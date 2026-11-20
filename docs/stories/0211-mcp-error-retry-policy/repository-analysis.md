# Story 0211 - Repository Analysis

## Statut

ANALYSIS COMPLETE - implementation not authorized.

Cette analyse documente l'etat present du repository pour preparer la politique
des erreurs MCP. Elle ne modifie aucun comportement de production.

## Sources examinees

- `tools/devlog_mcp/runtime.py`
- `tools/devlog_mcp/status.py`
- `tools/devlog_mcp/tests/test_runtime.py`
- `mcp-server/.../resource/ResourceSupport.java`
- `mcp-server/.../config/DevlogBackendClientConfiguration.java`
- Story 0171, Story 0172 et Story 0174
- ADR-077 et ADR-078

## Architecture actuelle

### Runtime Python

`tools/devlog_mcp/runtime.py` est proprietaire du processus MCP local, de la
generation de processus, du renouvellement du bearer et du suivi des
operations en vol.

Le runtime reconnait une erreur transport uniquement lorsque la reponse JSON-RPC
contient exactement :

```text
code    = -32600
message = Authentication is required
```

Pour cette erreur, il conserve l'operation, autorise au maximum un retry,
force le renouvellement du bearer et retransmet le payload exact. A l'echec du
renouvellement ou apres epuisement du budget, l'operation termine avec
`MCP_REAUTH_REQUIRED`, sauf si la barriere d'arret impose
`MCP_INSTANCE_STOPPING`.

Le renouvellement est refuse lorsqu'il existe plusieurs operations en vol. Ce
comportement est explicitement accepte par Story 0172.

### Serveur Java MCP

`ResourceSupport` est l'adaptateur commun des ressources et traduit plusieurs
exceptions HTTP en erreurs MCP :

- `404` vers `RESOURCE_NOT_FOUND` ;
- `400` et `409` de certains follow-ups vers `INVALID_PARAMS` ;
- `5xx` vers `INTERNAL_ERROR` ;
- `403` vers `RESOURCE_NOT_FOUND` pour les snapshots autorises, ou vers une
  erreur interne dans le chemin generique ;
- `401` vers `INVALID_REQUEST` avec le message exact
  `Authentication is required`.

Le client backend utilise `RestClient` avec un interceptor qui injecte le
bearer et un interceptor de logging. Les echecs backend sont journalises avec
la methode, l'URI, le type d'exception et le message, sans retry local visible
dans cette configuration.

### Diagnostics

`status.py` expose l'etat des instances, les derniers evenements et les
evenements de renouvellement. Il verifie aussi la joignabilite du backend et de
l'endpoint de token en mode `doctor`. Il ne definit pas de politique de retry.

## Findings

### F-01 - Collision de contrat entre authentification transport et Core

**Evidence**

- `runtime.py:is_transport_auth_failure` reconnait exactement
  `-32600 / Authentication is required`.
- `ResourceSupport.unauthenticated` produit `INVALID_REQUEST` avec ce meme
  message lorsqu'un appel backend repond `401`.

**Risque**

Un `401` du backend Core peut etre indistinguable d'un `401` du transport MCP
pour le runtime Python. Le runtime peut alors renouveler le bearer transport,
redemarrer le processus MCP et rejouer une operation alors que le probleme
concerne l'autorisation ou l'authentification cote Core.

**Decision requise par la politique**

Les erreurs doivent porter une provenance explicite, ou le contrat doit
interdire au runtime de classifier comme transport toute erreur provenant d'un
appel metier Core. Un message humain identique ne doit pas etre la seule preuve
de la couche d'origine.

### F-02 - Politique de retry repartie entre deux couches

Le retry transport est borne et implemente dans le runtime Python, tandis que
les appels Core sont adaptes dans Java sans politique commune visible dans les
clients REST. La Story 0211 doit definir la frontiere : qui decide, qui compte
les tentatives, et comment le contexte est conserve.

### F-03 - Mapping HTTP non uniforme selon le chemin Java

`ResourceSupport` ne mappe pas uniformement les memes statuts selon qu'il s'agit
d'un snapshot autorise, d'un follow-up ou d'une lecture generique. Cette
variation peut etre legitime pour la non-divulgation, mais elle doit etre
explicite dans la taxonomie afin de ne pas etre confondue avec une erreur
transitoire.

### F-04 - Absence de retry backend explicite dans la configuration actuelle

`DevlogBackendClientConfiguration` construit les clients REST et journalise les
reponses, mais aucune strategie de retry, de backoff, de `Retry-After` ou de
circuit-breaker n'est configuree dans ce composant. Cela ne prouve pas qu'aucun
autre mecanisme n'existe, mais aucune politique partagee n'est visible ici.

## Invariants a preserver

- Story 0172 reste proprietaire des operations en vol, generations et
  transitions terminales.
- Une seule transition terminale est emise par operation.
- Le payload d'un retry transport reste interne et n'est jamais journalise.
- Le renouvellement du bearer transport reste distinct de la delegation et des
  credentials provider definis par ADR-077/078.
- Les erreurs `400`, `403`, `404` et `409` ne deviennent pas retryables par
  simple uniformisation.
- Une operation Core ambigue ne doit pas etre rejouee sans garantie
  d'idempotence.

## Scope recommande pour l'Implementation Plan

1. Definir un contrat d'erreur qui transporte la couche d'origine et la
   provenance sans exposer de secret.
2. Corriger la classification du runtime pour separer transport et Core.
3. Definir la matrice de retry/backoff par couche et par statut.
4. Definir les garanties d'idempotence necessaires avant tout rejeu Core.
5. Ajouter des tests de non-confusion entre `401` transport et `401` Core.
6. Ajouter des tests de concurrence et d'arret pendant renouvellement, sans
   modifier les invariants acceptes de Story 0172.
7. Definir les diagnostics `status/doctor` correspondant aux nouvelles
   categories.

## Gaps et decisions ouvertes

- Le format exact d'une provenance d'erreur MCP n'est pas encore decide.
- La liste des operations Core idempotentes n'est pas formalisee.
- Le comportement `429` et l'utilisation de `Retry-After` ne sont pas
  implementes dans le chemin examine.
- L'autorite finale pour les retries Core doit etre tranchee entre l'adaptateur
  MCP et le client/backend.
- Aucun nouvel ADR n'a encore ete cree pour cette politique.

## DevLog context

Le preflight DevLog a ete tente avant l'analyse mais la connexion MCP s'est
fermee avec `MCP error -32000: Connection closed`. Les conclusions ci-dessus
reposent donc sur les artefacts du repository et doivent etre reconciliees avec
DevLog lorsque le service sera disponible.

## Conclusion

Le repository confirme que la Story 0211 est necessaire. Le finding F-01 est le
risque prioritaire : le contrat actuel ne preserve pas la distinction entre
authentification transport et `401` Core. L'Implementation Plan doit traiter ce
point avant tout ajout de retry.
