# Story 0212 - Retry et renouvellement du runtime MCP

## Statut

IMPLEMENTED - AWAITING HUMAN REVIEW.

Cette Story materialise la prochaine etape de l'epic MCP Error Resilience
Policy. Le proprietaire humain a confirme les decisions de l'Implementation
Plan et autorise l'implementation. L'acceptation finale reste une gate distincte.

## Objectif

Appliquer au runtime MCP local la politique de renouvellement du bearer et de
retry definie par Story 0211 et ADR-081, en preservant les invariants de cycle
de vie de Story 0172 et le renouvellement technique deja introduit par Story
0174.

Le runtime doit distinguer un bearer de transport expire d'une erreur Core,
delegation ou provider. Il doit renouveler de facon bornee, ne rejouer qu'une
operation explicitement sure et echouer ferme lorsque le resultat precedent est
ambigu ou que l'ownership du processus ne peut pas etre prouve.

## Artefacts gouvernants

- Epic `mcp-error-resilience-policy`.
- ADR-077 - MCP Actor Identity and Human Delegation Boundary.
- ADR-078 - DevLog Internal Agent and External Agent Client Identity.
- ADR-081 - MCP Error Classification, Retry and Idempotency Policy.
- Story 0172 - MCP Runtime Observability and Instance Lifecycle.
- Story 0174 - MCP Transport Bearer Renewal and Session Reconnection.
- Story 0211 - Politique normative des erreurs MCP et des retries.
- `TOOLS.md` et le runtime local `tools/devlog_mcp`.

## Probleme

Le runtime actuel sait deja reacquerir un bearer, remplacer le child MCP et
rejouer une operation read-only dans un cas d'authentification transport
explicite. La politique complete reste cependant incomplete :

- les erreurs transitoires du token endpoint ne disposent pas d'un budget de
  retry explicite et teste ;
- la classification transport doit rester strictement structurelle et ne pas
  dependre d'un message humain generique ;
- le budget de retry d'acquisition du token doit rester distinct du retry de
  l'operation MCP ;
- les chemins concurrents, les generations anciennes et l'arret pendant un
  renouvellement doivent etre verifies contre la matrice 0211 ;
- les decisions de diagnostic et de fail-closed doivent etre coherentes entre
  runtime, status/doctor et documentation operateur.

## Perimetre IN

- Reconciler le runtime actuel avec les invariants acceptes des Stories 0172,
  0174 et 0211.
- Classifier structurellement les erreurs de transport, Core, delegation et
  provider utilisees par la decision de retry.
- Conserver au maximum un renouvellement et un retry d'operation autorise.
- Definir et implementer un budget borne pour les erreurs transitoires du token
  endpoint lorsque cette decision est validee par l'Implementation Plan.
- Preserver l'allowlist des outils rejouables et la regle par defaut de non-rejeu
  pour les operations ambigues ou non idempotentes.
- Preserver l'`operationId`, la generation, la reservation unique et la
  transition terminale unique pendant un renouvellement.
- Preserver le remplacement du seul child possede par le runtime courant.
- Preserver l'integrite STDIO : protocole sur STDOUT, diagnostics sur STDERR ou
  dans les journaux bornes.
- Mettre a jour les diagnostics `status/doctor` et `TOOLS.md` uniquement si la
  matrice retenue le necessite.
- Ajouter les tests provider-free, de concurrence, de generation et de
  redaction necessaires.

## Perimetre OUT

- Modifier l'identite technique ou humaine, la delegation ou les credentials
  provider.
- Ajouter un nouveau flux OAuth ou modifier Keycloak.
- Introduire une queue, un broker, un superviseur generique ou un circuit
  breaker durable.
- Rejouer automatiquement une operation dont le resultat est ambigu sans
  garantie d'idempotence metier ou de deduplication cote serveur.
- Ajouter simultanement une politique de retry independante dans Java Core et
  dans le runtime Python.
- Modifier les contrats metier Core ou les bindings de credentials AI.
- Executer une validation avec des credentials reelles dans le cadre des tests.

## Invariants

1. Le bearer renouvele authentifie uniquement l'acteur technique MCP autorise.
2. Le renouvellement ne cree, ne choisit et n'elargit aucune delegation humaine.
3. Core reste l'autorite pour l'authentification, l'autorisation et la
   delegation.
4. Un `401` Core ne declenche jamais un renouvellement transport automatique.
5. Un retry est interdit par defaut et doit avoir une categorie, un budget et
   une condition d'idempotence explicites.
6. Une seule operation logique et un seul `operationId` sont conserves pendant
   un retry autorise.
7. Les reponses d'une generation ancienne sont ignorees.
8. Une seule transition terminale est emise pour chaque operation.
9. Une erreur de renouvellement ou une ownership ambigue provoque un echec
   ferme.
10. Aucun secret, bearer, token de delegation, payload JSON-RPC ou contenu
    provider n'apparait dans les diagnostics.

## Decisions confirmees pour l'implementation

1. Une erreur sans `data.source` reste compatible uniquement pour le couple
   historique code/message exact.
2. Une seule reacquisition supplementaire du token est autorisee par cycle de
   renouvellement.
3. Timeout, `502`, `503` et `504` sont transitoires. `400`, `401`, `403`, `404`
   et `409` sont terminaux. `429` est reporte a la Story d'idempotence.
4. Le renouvellement reste declenche par une nouvelle requete, sans
   renouvellement proactif d'une session inactive.
5. Le runtime Python reste l'autorite unique du retry ; aucun retry automatique
   n'est ajoute dans les adaptateurs Java MCP.

## Criteres d'acceptation

### AC-1 - Renouvellement borne

Une expiration du bearer transport declenche au plus un renouvellement et au
plus un retry pour une operation autorisee.

### AC-2 - Separation des couches

Les erreurs Core `401`, Core `403`, delegation et provider ne declenchent pas de
renouvellement transport et restent diagnostiquees dans leur couche d'origine.

### AC-3 - Retry du token endpoint

Les erreurs transitoires retenues pour le token endpoint disposent d'un budget
borne, d'un backoff explicite et d'une terminalisation deterministe en cas
d'echec. Aucun retry illimite ou concurrent n'est possible.

### AC-4 - Idempotence et ambiguite

Une operation non idempotente ou dont le resultat precedent est ambigu n'est
jamais rejouee automatiquement sans garantie explicite de deduplication.

### AC-5 - Concurrence et generations

Les operations concurrentes, l'arret pendant renouvellement et les reponses
d'une ancienne generation respectent les invariants de Story 0172, sans
transition terminale dupliquee.

### AC-6 - Ownership

Le renouvellement, le remplacement et l'arret affectent uniquement le child
dont l'ownership est prouvee par le manifest et l'identite de processus.

### AC-7 - Integrite protocolaire et secrets

STDOUT reste exclusivement reserve au protocole MCP et les tests prouvent
l'absence de bearer, secret, delegation, payload et contenu provider dans les
diagnostics.

### AC-8 - Diagnostics operateur

`status/doctor` et la documentation distinguent au minimum expiration du
transport, echec de renouvellement, erreur Core, delegation, provider et erreur
transitoire du token endpoint.

### AC-9 - Validation

Les tests Python du runtime, les tests MCP Java pertinents, les scenarios de
concurrence/redaction et `git diff --check` passent. Toute validation live
dependante de credentials est explicitement declaree comme non executee.

## Tests attendus

- expiration transport puis renouvellement et retry unique ;
- seconde erreur transport sans second retry ;
- `401` Core structurel sans renouvellement ;
- `403` Core, delegation et provider sans renouvellement ;
- timeout et `5xx` du token endpoint avec budget borne ;
- `429` selon la decision retenue ;
- operation non idempotente et resultat ambigu sans rejeu ;
- plusieurs operations pendant un renouvellement ;
- arret pendant renouvellement ;
- reponse d'une ancienne generation ;
- child appartenant a une autre instance ;
- redaction des secrets et integrite STDIO ;
- non-regression des suites Stories 0172 et 0174.

## Workflow et gates

1. Repository Analysis de cette Story.
2. Implementation Plan derive du Repository Analysis.
3. Validation humaine des decisions et autorisation explicite
   d'implementation.
4. Implementation minimale et tests cibles.
5. Revue independante et verification complete.
6. Acceptance humaine de la Story.

L'autorisation d'implementation ne vaut ni autorisation de commit, push, merge
ou validation finale.

## References

- [Epic - Politique de resilience MCP](../../epics/mcp-error-resilience-policy/epic.md)
- [Story 0172](../0172-mcp-runtime-observability-and-instance-lifecycle/story.md)
- [Story 0174](../0174-mcp-bearer-renewal/story.md)
- [Story 0211](../0211-mcp-error-retry-policy/story.md)
- [ADR-077](../../decisions/ADR-077.md)
- [ADR-078](../../decisions/ADR-078.md)
- [ADR-081](../../decisions/ADR-081.md)
