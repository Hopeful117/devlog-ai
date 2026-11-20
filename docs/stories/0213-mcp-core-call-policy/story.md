# Story 0213 - Politique des appels MCP vers Core

## Statut

IMPLEMENTED - awaiting human acceptance.

Cette Story materialise l'etape suivante de l'epic MCP Error Resilience Policy.
L'implementation a ete realisee apres autorisation humaine explicite. Elle reste
en attente de revue et d'acceptance humaine finale.

## Objectif

Uniformiser la politique des appels du serveur MCP Java vers DevLog Core, en
appliquant la taxonomie d'ADR-081 sans ajouter de retry automatique.

Les appels Core doivent propager de facon coherente l'authentification et la
delegation autorisees, distinguer les erreurs Core des erreurs transport et
fournir des diagnostics bornes et non-divulgants selon la nature de l'appel.

## Artefacts gouvernants

- Epic `mcp-error-resilience-policy`.
- ADR-077 - MCP Actor Identity and Human Delegation Boundary.
- ADR-078 - DevLog Internal Agent and External Agent Client Identity.
- ADR-081 - MCP Error Classification, Retry and Idempotency Policy.
- Story 0172 - MCP Runtime Observability and Instance Lifecycle.
- Story 0211 - Politique normative des erreurs MCP et des retries.
- Story 0212 - Retry et renouvellement du runtime MCP.
- `TOOLS.md` et les clients Java du module `mcp-server`.

## Probleme

Les appels Core sont repartis entre plusieurs interfaces HTTP et plusieurs
outils MCP. Les erreurs et la propagation des headers sont donc traitees a
plusieurs endroits :

- `ResourceSupport` mappe une partie des erreurs de ressources ;
- `SyncProjectHistoryTool` mappe ses propres erreurs ;
- `RegisterProjectTool` choisit entre methodes authentifiees et non
  authentifiees ;
- `BearerTokenAuthInterceptor` injecte le bearer depuis le principal courant ;
- l'observabilite et le Companion ont des chemins distincts.

Cette dispersion peut faire perdre la provenance Core, exposer des messages
incoherents, propager differemment la delegation ou permettre qu'un appel
best-effort affecte le resultat principal d'un tool.

## Categories d'appels

Chaque client ou operation doit etre classe comme :

1. **Read-only Core** : lecture de contexte, ressources, historique et
   freshness.
2. **Mutation Core** : creation de projet, creation de source et import
   d'historique.
3. **Observabilite best-effort** : ingestion des evenements d'outils et de
   session.
4. **Companion** : fonctions de presence et de trace du Companion.

La classification sert au mapping et au comportement d'echec. Elle ne cree pas
une autorisation supplementaire et ne definit pas l'idempotence metier.

## Perimetre IN

- Inventorier et uniformiser les appels HTTP MCP vers Core.
- Definir un mapping commun des erreurs `400`, `401`, `403`, `404`, `409`,
  `429`, timeout et `5xx`.
- Conserver `401` comme `CORE_AUTHENTICATION_FAILED` (expose en donnees comme
  `CORE_AUTHENTICATION` pour compatibilite ADR-081), jamais comme expiration du
  bearer transport.
- Distinguer `CORE_AUTHORIZATION_FAILED` (expose en donnees comme
  `CORE_AUTHORIZATION`), delegation, erreur fonctionnelle et indisponibilite
  Core.
- Garantir la propagation du bearer et de la delegation depuis le principal
  autorise uniquement.
- Garantir que les mutations ne sont jamais rejouees automatiquement.
- Maintenir les appels d'observabilite best-effort sans masquer le resultat du
  tool principal.
- Uniformiser les diagnostics MCP et les logs sans secrets ni payloads.
- Tester les chemins read-only, mutation, observabilite et Companion.
- Documenter la frontiere avec les retries du runtime Python 0212 et la future
  Story d'idempotence.

## Perimetre OUT

- Ajouter un retry automatique dans les clients Java.
- Definir ou implementer l'idempotence metier.
- Modifier le runtime Python ou le renouvellement du bearer.
- Modifier l'identite technique ou humaine.
- Modifier la delegation humaine ou les credentials provider.
- Modifier les contrats metier Core sans Story dediee.
- Introduire une queue, un broker, un circuit breaker ou un superviseur.
- Transformer un `403` ou un `404` en preuve de ressource existante.

## Invariants

1. Java Core reste l'autorite de l'authentification derivee, de l'autorisation,
   de la delegation et du scope projet.
2. Une erreur Core ne peut pas etre encodee comme une expiration du transport
   MCP.
3. Aucun client Java n'ajoute de retry automatique dans cette Story.
4. Une mutation Core n'est jamais rejouee implicitement.
5. Un bearer ou une delegation n'est propage qu'a partir du principal autorise
   de la requete courante.
6. Un appel d'observabilite best-effort ne change pas le resultat du tool
   principal.
7. Les diagnostics ne contiennent ni bearer, delegation, credential, payload,
   prompt, reponse provider ou contenu repository sensible.
8. Les messages non-divulgants restent compatibles avec les contrats MCP
   existants lorsque cela est necessaire.
9. Les erreurs `429` sont diagnostiquees sans retry et restent reservees a la
   future politique d'idempotence.
10. Le retry de renouvellement du transport reste exclusivement gouverne par
    Story 0212 et le runtime Python.

## Politique approuvee pour la planification

- Le mapping commun sera place au niveau de l'adaptateur MCP/erreur partage,
  apres confirmation par le Repository Analysis de l'abstraction minimale
  necessaire ; `ResourceSupport` ne doit pas devenir un second client HTTP.
- Un `403` Core sera expose comme `CORE_AUTHORIZATION_FAILED` lorsque le
  contexte peut le divulguer sans risque ; les ressources protegees continueront
  d'utiliser un resultat non-divulgant lorsque le contrat l'exige.
- Les `429` exposeront un diagnostic structurel `RATE_LIMITED`, sans retry.
- Les appels Companion suivront la meme classification Core, avec conservation
  de leur comportement best-effort lorsqu'ils ne constituent pas le resultat
  principal.
- L'ingestion d'observabilite restera best-effort pour les erreurs `401`, `403`,
  `429`, timeout et `5xx` ; elle ne doit pas interrompre le tool appelant.

## Criteres d'acceptation

### AC-1 - Classification des appels

Chaque appel Core du module MCP est classe comme read-only, mutation,
observabilite best-effort ou Companion, et son comportement d'erreur est
documente.

### AC-2 - Mapping Core uniforme

Les erreurs `400`, `401`, `403`, `404`, `409`, `429`, timeout et `5xx` sont
mappees de facon deterministe selon la categorie de l'appel, sans utiliser un
message backend sensible comme discriminant.

### AC-3 - Separation transport/Core

Un `401` Core est structurellement distinct d'une expiration du bearer
transport et ne declenche aucun renouvellement du runtime Python.

### AC-4 - Propagation d'identite

Les headers bearer et delegation proviennent uniquement du principal autorise
de la requete et sont transmis de facon coherente aux appels qui les exigent.

### AC-5 - Mutations fail-closed

Les mutations Core ne sont jamais rejouees automatiquement et leurs erreurs
fonctionnelles ou d'autorisation sont exposees sans ambiguite dangereuse.

### AC-6 - Observabilite best-effort

Une erreur d'ingestion d'observabilite ne change jamais le resultat du tool
principal et ne provoque pas de retry implicite.

### AC-7 - Diagnostics non-divulgants

Les logs et les erreurs MCP excluent secrets, headers d'authentification,
delegation, payloads et reponses backend sensibles.

### AC-8 - Tests de contrat

Les tests couvrent les quatre categories d'appels, le mapping des statuts, la
propagation des headers, l'absence de retry Java, le best-effort et la
non-divulgation.

## Tests attendus

- mapping Core `400`, `401`, `403`, `404`, `409`, `429` et `5xx` ;
- timeout et indisponibilite Core ;
- bearer present, absent, invalide et delegation presente ou absente ;
- contexte MCP request-scoped contre contexte SecurityContext ;
- read-only avec acces refuse ou ressource absente ;
- mutation avec erreur fonctionnelle et erreur d'autorisation ;
- observabilite best-effort avec `401`, `403`, `429`, timeout et `5xx` ;
- Companion avec les memes categories structurelles ;
- absence de retry et absence de double appel ;
- redaction des logs et diagnostics.

## Workflow et gates

1. Repository Analysis.
2. Implementation Plan.
3. Validation humaine du plan et autorisation explicite d'implementation.
4. Implementation minimale et tests cibles.
5. Revue independante et verification complete.
6. Acceptance humaine de la Story.

L'implementation realisee n'emporte ni acceptance finale, ni commit, ni push,
ni merge.

## References

- [Epic - Politique de resilience MCP](../../epics/mcp-error-resilience-policy/epic.md)
- [Story 0211](../0211-mcp-error-retry-policy/story.md)
- [Story 0212](../0212-mcp-runtime-retry-renewal/story.md)
- [ADR-077](../../decisions/ADR-077.md)
- [ADR-078](../../decisions/ADR-078.md)
- [ADR-081](../../decisions/ADR-081.md)
