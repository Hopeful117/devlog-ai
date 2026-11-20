# Story 0211 - Implementation Plan

## Statut

PLAN COMPLETE - implementation not authorized.

Ce document decrit une implementation possible de la politique normative des
erreurs MCP. Il ne constitue pas une autorisation de modifier le code, les
contrats ou la configuration de production.

## Gouvernance prealable

Avant l'implementation, le proprietaire humain doit confirmer :

1. L'acceptation de l'ADR-081 comme decision architecturale de reference, ou
   une revision explicite de ses decisions.
2. Le contrat de provenance d'erreur retenu ci-dessous.
3. La liste initiale des operations Core qui peuvent etre rejouees.
4. Le budget maximal des retries et la politique `429`.
5. L'autorisation d'implementation de Story 0211.

## Decision de conception appliquee par ADR-081

Le serveur MCP doit conserver une distinction structurelle entre :

- `TRANSPORT_AUTHENTICATION` : erreur produite par la couche MCP/transport et
  eligible au renouvellement du bearer local ;
- `CORE_AUTHENTICATION` : `401` produit par un appel au backend Core ;
- `CORE_AUTHORIZATION` : `403` produit par le backend Core ;
- `DELEGATION_AUTHENTICATION` : delegation absente, expiree ou refusee ;
- `PROVIDER_AUTHENTICATION` : credential ou provider externe ;
- autres erreurs fonctionnelles, protocolaires ou d'infrastructure.

La provenance ne doit pas etre deduite du seul message humain. La forme exacte
reste a valider, mais elle doit etre exploitable sans exposer le bearer, les
credentials, les tokens de delegation ou le payload de la requete.

## Phases d'implementation

### Phase 1 - Contrat et classification

1. Definir les codes d'erreur internes et leur couche d'origine.
2. Documenter la relation entre statut HTTP backend, erreur MCP et diagnostic
   runtime.
3. Modifier le mapping Java pour que le `401` Core ne reutilise pas le contrat
   reserve a l'authentification transport.
4. Preserver la non-divulgation existante pour `403`, `404` et les erreurs de
   ressources protegees.
5. Definir le mapping des `400`, `409`, `429` et `5xx` sans les rendre
   implicitement retryables.

### Phase 2 - Runtime transport

1. Remplacer la detection fondee uniquement sur
   `-32600 / Authentication is required` par une classification structurelle.
2. Conserver un seul renouvellement et un seul retry pour une erreur
   `TRANSPORT_AUTHENTICATION` eligible.
3. Interdire le renouvellement automatique pour `CORE_AUTHENTICATION`,
   `CORE_AUTHORIZATION`, `DELEGATION_AUTHENTICATION` et
   `PROVIDER_AUTHENTICATION`.
4. Conserver les invariants de Story 0172 : reservation unique, generation,
   operationId stable, une seule transition terminale et barriere d'arret.
5. Ne jamais journaliser le payload exact retransmis.

### Phase 3 - Retries backend et idempotence

1. Identifier les clients backend concernes et l'autorite de decision du retry.
2. Ajouter une matrice partagee pour timeout, `5xx` et `429`.
3. Utiliser `Retry-After` uniquement lorsqu'il est valide et dans le budget
   temporel autorise.
4. Ne rejouer une operation Core que si son idempotence est explicitement
   garantie par le contrat metier ou une cle deduplication server-side.
5. Ne jamais rejouer une operation dont l'execution precedente est ambigue sans
   preuve d'idempotence.
6. Ne pas ajouter de retry automatique aux `400`, `403`, `404` ou `409`.

### Phase 4 - Diagnostics

1. Ajouter les codes de classification au journal runtime et aux diagnostics
   `status/doctor`.
2. Exposer seulement les metadonnees bornees : couche, code, tentative,
   instanceId, operationId, generation et action recommandee.
3. Rediger les messages operateur pour distinguer : renouvellement requis,
   credentials Core invalides, delegation invalide, provider indisponible et
   backend transitoirement indisponible.
4. Verifier que les URI, messages d'exception et reponses backend ne contiennent
   pas de secret ou de payload sensible.

## Fichiers probablement concernes

### Runtime Python

- `tools/devlog_mcp/runtime.py`
  - classification des erreurs transport ;
  - renouvellement et retry ;
  - diagnostics de terminalisation.
- `tools/devlog_mcp/status.py`
  - presentation des nouvelles categories si necessaire.
- `tools/devlog_mcp/tests/test_runtime.py`
  - tests de classification, non-confusion et concurrence.

### Serveur MCP Java

- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/resource/ResourceSupport.java`
  - mapping des erreurs backend ;
  - separation du `401` Core et du contrat transport.
- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/config/DevlogBackendClientConfiguration.java`
  - point d'integration pour toute politique client commune, si elle est
    retenue.
- clients backend et tests MCP associes, a confirmer pendant l'implementation.

### Documentation

- Story 0211 et son Implementation Report.
- ADR dedie, uniquement apres decision humaine.
- `TOOLS.md` pour le contrat visible par les agents.

## Strategie de tests

### Tests unitaires Python

Ajouter ou adapter des cas qui verifient :

1. erreur transport eligible : renouvellement puis un retry ;
2. erreur transport repetee : terminalisation unique en
   `MCP_REAUTH_REQUIRED` ;
3. `401` Core : aucun renouvellement transport et aucun rejeu implicite ;
4. `403` Core ou delegation : echec ferme ;
5. provider authentication : echec distinct ;
6. timeout et `5xx` : decision conforme a la matrice ;
7. `429` avec `Retry-After` valide, absent, invalide et excessif ;
8. resultat ambigue sans idempotence : aucun rejeu ;
9. operation idempotente avec cle deduplication : retry borne ;
10. concurrence entre renouvellement, reponse, stop et ancienne generation ;
11. absence de secret dans les evenements et diagnostics.

### Tests Java MCP

Ajouter ou adapter des tests qui verifient :

1. mapping distinct d'un `401` backend Core ;
2. mapping non-divulgant d'un `403` ;
3. mapping de `400`, `404`, `409`, `429` et `5xx` ;
4. absence de retry implicite dans l'adaptateur si le retry est confie au
   runtime ;
5. preservation du message et du code attendus pour les consommateurs MCP ;
6. absence de bearer, delegation et payload dans les diagnostics.

### Tests d'integration et de resilience

Les scenarios doivent utiliser des doubles locaux et aucun credential reel :

- transport MCP avec bearer expire ;
- backend Core repondant `401` ;
- backend Core repondant `403` ;
- Keycloak timeout ou `5xx` ;
- backend Core timeout ou `5xx` ;
- `429` avec differents headers ;
- deux operations simultanees pendant renouvellement ;
- arret pendant retry ;
- reponse d'une generation ancienne ;
- resultat ambigu apres timeout d'une operation non idempotente.

## Ordre de verification

1. Tests unitaires Java du mapping d'erreurs.
2. Tests unitaires Python du classifieur et du runtime.
3. Tests de non-regression Story 0172 et Story 0174.
4. Tests d'integration MCP avec doubles backend et token endpoint.
5. Tests de resilience et de concurrence.
6. Verification documentaire de `TOOLS.md` et des diagnostics.
7. Revue finale du diff et de l'absence de secrets.

## Criteres de sortie

- Un `401` Core ne peut plus declencher un renouvellement transport par
  collision de message/code.
- Les categories transport, Core, delegation et provider sont observables et
  distinctes.
- Chaque retry est borne et associe a une decision d'idempotence.
- Les operations ambigues ne sont pas rejouees par defaut.
- Les invariants acceptes de Story 0172 restent valides.
- Story 0174 conserve son renouvellement borne et ses transitions terminales.
- Les diagnostics sont utiles sans divulguer de donnees sensibles.
- Les tests couvrent les scenarios de la Story 0211.
- Aucun changement n'est inclus hors perimetre sans approbation humaine.

## Risques d'implementation

1. Modifier le code d'erreur MCP sans mettre a jour les clients peut transformer
   une erreur explicable en erreur generique.
2. Reutiliser le champ `message` comme discriminant recreerait le finding F-01.
3. Ajouter un retry dans Java et Python pourrait doubler le budget global.
4. Une cle d'idempotence seulement locale ne protege pas contre un rejeu apres
   redemarrage.
5. Les mappings de non-divulgation peuvent masquer la difference entre
   autorisation refusee et ressource absente ; la taxonomie interne doit la
   conserver meme si le message expose reste generique.

## Decision gate

L'ADR-081 est accepte. La suite doit etre l'une des options suivantes :

1. autoriser l'implementation de Story 0211 ;
2. demander une revision du contrat de provenance, de la matrice ou du budget
   de retry ;
3. maintenir la Story en attente jusqu'a resolution des decisions ouvertes.
