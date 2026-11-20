# Story 0213 - Repository Analysis

## Statut

ANALYSIS COMPLETE - implementation completed under human authorization.

Ce document decrit l'etat du module MCP avant l'implementation et les ecarts
pertinents pour la politique des appels Core.

## Contexte et sources

Sources consultees :

- Epic `mcp-error-resilience-policy` ;
- ADR-077, ADR-078 et ADR-081 ;
- Stories 0211 et 0212 ;
- `mcp-server/src/main/java/.../config/DevlogBackendClientConfiguration.java` ;
- `BearerTokenAuthInterceptor.java` ;
- `FailClosedMcpAuthenticatedPrincipalProvider.java` ;
- les interfaces `Devlog*Client` ;
- `ResourceSupport.java` ;
- les outils Register Project et Sync Project History ;
- `McpToolObservabilityService.java` ;
- les tests MCP existants.

Le contexte DevLog n'a pas pu etre interroge dans cet environnement. Les
artefacts du depot sont donc la source d'autorite pour l'intention et l'etat
physique courant.

## Architecture actuelle

### Clients HTTP

`DevlogBackendClientConfiguration` construit tous les clients declaratifs avec
un `RestClient` commun par interface :

- `DevlogProjectContextClient` ;
- `DevlogResourceClient` ;
- `DevlogAdminClient` ;
- `DevlogCompanionClient` ;
- `McpToolEventIngestClient`.

Chaque client partage :

- le `baseUrl` Core ;
- `BearerTokenAuthInterceptor` ;
- un interceptor de logging ;
- `HttpServiceProxyFactory`.

Il existe donc un point d'integration commun pour la propagation et la
telemetrie, mais aucun adaptateur commun de classification d'erreur.

### Propagation d'identite

`BearerTokenAuthInterceptor` ajoute un header `Authorization` si l'appel n'en
possede pas deja. Il lit le principal courant via
`McpAuthenticatedPrincipalProvider` et ajoute `X-DevLog-Delegation` lorsque la
delegation n'est pas vide.

Les appels qui recoivent un principal request-scoped peuvent aussi transmettre
explicitement le bearer et la delegation dans leurs methodes declaratives.
`ResourceSupport.requireProjectId` utilise le contexte MCP lorsqu'il est
disponible, puis retombe sur le contexte de securite.

Cette coexistence est necessaire pour les transports stdio et HTTP, mais elle
doit etre testee afin d'eviter des comportements differents entre ressources et
tools.

### Mapping d'erreur

`ResourceSupport` mappe actuellement plusieurs familles :

- `404` vers `RESOURCE_NOT_FOUND` ;
- `401` vers une erreur structurelle `source=CORE`,
  `category=CORE_AUTHENTICATION`, correspondant a la classification publique
  `CORE_AUTHENTICATION_FAILED` ;
- `403` parfois vers une ressource non trouvable, parfois vers une erreur
  structurelle `CORE_AUTHORIZATION`, correspondant a la classification publique
  `CORE_AUTHORIZATION_FAILED` ;
- `400` et `409` vers `INVALID_PARAMS` dans le chemin Story Context ;
- `5xx` vers `INTERNAL_ERROR` ;
- les autres statuts vers un message interne generique.

`SyncProjectHistoryTool` remappe directement `404`, `401` et `403`. Les outils
et ressources ne partagent donc pas encore une table de decision unique.

### Observabilite

`McpToolObservabilityService` envoie des evenements au backend de facon
asynchrone et best-effort. Les erreurs d'ingestion sont absorbees et tracees en
debug, ce qui protege le resultat principal du tool.

Le client d'ingestion requiert cependant explicitement un bearer et une
delegation. Ce chemin doit rester distinct du resultat metier et ne doit pas
introduire un retry Java.

### Logging

L'interceptor commun logue la methode, l'URI, le statut et la duree. Les
headers ne sont pas logues, mais le contenu du message d'exception doit rester
borne et non-divulgant lorsque Spring expose une reponse backend.

## Findings

### F-01 - Mapping d'erreur disperse

Les decisions d'erreur sont reparties entre `ResourceSupport`,
`SyncProjectHistoryTool` et les appels d'outils. Une meme categorie Core peut
donc produire des codes MCP ou des messages differents selon le point d'entree.

Impact : le runtime Python et l'agent ne disposent pas d'un diagnostic stable,
et une erreur Core peut etre difficile a distinguer d'une erreur fonctionnelle.

### F-02 - Propagation duale du principal

Certains appels utilisent le principal request-scoped, d'autres reposent sur
l'interceptor qui lit le contexte global, et plusieurs interfaces exposent des
methodes authentifiees et non authentifiees en parallele.

Impact : un meme appel peut recevoir un bearer ou une delegation differente
selon le transport et le chemin d'appel. La Story doit uniformiser la regle
sans supprimer le support stdio necessaire.

### F-03 - `429` non formalise

Les mappings actuels traitent surtout les erreurs client usuelles et les
erreurs `5xx`. Aucun contrat commun n'expose explicitement `RATE_LIMITED` pour
les appels Core.

Impact : un agent peut recevoir une erreur interne generique et ne pas savoir
qu'aucun retry n'est autorise dans cette Story.

### F-04 - Timeouts et erreurs reseau non uniformes

Les clients declaratifs peuvent remonter des exceptions de transport qui ne
sont pas toutes attrapees par les mappings `RestClientResponseException`.

Impact : timeout et indisponibilite peuvent traverser un chemin different des
erreurs HTTP `5xx`, avec des diagnostics incoherents.

### F-05 - Observabilite best-effort a preserver explicitement

L'ingestion des evenements absorbe actuellement les exceptions et ne change pas
le resultat principal. Cette propriete est correcte pour la Story, mais elle
n'est pas formalisee dans une politique commune des clients.

Impact : une future abstraction de client ou de mapping ne doit pas transformer
un echec d'observabilite en echec du tool MCP.

### F-06 - Absence de retry Java deja favorable

Aucun retry automatique n'est configure dans `DevlogBackendClientConfiguration`
ou les clients declaratifs. Cette absence est compatible avec ADR-081 et doit
etre preservee ; Story 0213 ne doit pas ajouter de couche de retry implicite.

## Classification initiale des appels

### Read-only Core

- contexte projet et engineering context ;
- ressources decisions, insights, events, stories, commits et freshness ;
- recherche d'historique ;
- resolution d'evidence ;
- projections et snapshots de Story Context.

### Mutations Core

- creation de projet ;
- creation de source ;
- import d'historique ;
- soumission de taches et follow-ups qui declenchent un traitement Core.

Ces appels ne doivent pas etre rejoues automatiquement, meme lorsqu'un timeout
rend le resultat ambigu.

### Observabilite best-effort

- ingestion des `MCP_TOOL_*` events ;
- ingestion des evenements de session ;
- metriques ou traces Companion non essentielles au resultat.

### Companion

Les appels Companion suivent la taxonomie Core, mais leur comportement
best-effort doit etre preserve lorsqu'ils sont secondaires au tool principal.

## Fichiers concernes probables

- `DevlogBackendClientConfiguration.java` pour l'interception commune ;
- `BearerTokenAuthInterceptor.java` et le provider de principal ;
- `ResourceSupport.java` pour la politique partagee de mapping ;
- les tools qui ont un mapping local (`SyncProjectHistoryTool`,
  `RegisterProjectTool`, Story Context) ;
- `McpToolObservabilityService.java` ;
- les interfaces declaratives `Devlog*Client` si un contrat de headers doit
  etre normalise ;
- tests MCP et documentation `TOOLS.md`.

## Decisions de conception retenues pour le plan

1. Le mapping structurel sera centralise au niveau de l'adaptateur MCP/erreur
   partage, avec une abstraction minimale determinee pendant l'implementation
   plan ; `ResourceSupport` ne deviendra pas un client HTTP generique.
2. `403` sera expose comme `CORE_AUTHORIZATION_FAILED` lorsque le contrat le
   permet ; les ressources qui exigent une non-divulgation continueront de
   produire une absence equivalente.
3. `429` recevra un diagnostic `RATE_LIMITED` sans retry.
4. Companion suivra la classification Core et conservera le best-effort pour
   les appels secondaires.
5. L'ingestion d'observabilite absorbera `401`, `403`, `429`, timeout et `5xx`
   sans affecter le tool principal.

## Conclusion

Le depot dispose d'une base HTTP commune et d'une separation Core/transport
deja introduite par Story 0211, mais la politique des appels Core reste
dispersee entre clients, interceptor, support de ressources et tools.

Story 0213 peut etre implemente sans modifier le runtime Python 0212 et sans
ajouter de retry. Son risque principal est de centraliser le mapping sans
perdre les comportements non-divulgants existants ni le support du principal
request-scoped en stdio.
