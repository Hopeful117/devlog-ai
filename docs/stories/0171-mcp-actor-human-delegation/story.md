# Story 0171 - MCP Actor and Human Delegation Contract

## Statut

IMPLEMENTATION AUTHORIZED - Repository Analysis and Implementation Plan
approved by the human project owner on 2026-10-31.

## Reconciliation with ADR-078

ADR-078 adds the autonomous OpenCode Agent path. This Story remains applicable
to the explicit human-delegated mode only; human delegation is not required for
the normal DevLog Agent runtime. The autonomous path uses the stable
`AI_AGENT` principal and Core project authorization, with the DevLog Agent's
default `openai_chatgpt` OAuth configuration.

## Objectif produit

Permettre a OpenCode et au MCP d'agir comme un acteur technique `AI_AGENT` pour
un utilisateur DevLog uniquement lorsqu'une delegation humaine explicite,
bornee et verifiable par Core existe.

Le transport MCP ne doit plus transformer implicitement un token
`client_credentials` en principal `HUMAN`. Core doit conserver la distinction
entre l'acteur technique, le principal humain deleguant et la capacite AI
task-scoped.

## Autorite

- [ADR-006](../../decisions/ADR-006.md) - sorties AI non trustees.
- [ADR-067](../../decisions/ADR-067.md) - Java/Core autorite deterministe.
- [ADR-070](../../decisions/ADR-070.md) - principal, acteur et autorisation projet.
- [ADR-072](../../decisions/ADR-072.md) - isolation des credentials ChatGPT.
- [ADR-073](../../decisions/ADR-073.md) - binding Core principal/tache/credential.
- [ADR-077](../../decisions/ADR-077.md) - acteur MCP et delegation humaine.
- [Investigation MCP/OpenCode/ChatGPT](../../investigations/devlog-mcp-opencode-chatgpt-oauth-stability.md).

## Constat actuel

- `FailClosedMcpAuthenticatedPrincipalProvider` mappe tout JWT valide vers
  `PrincipalKind.HUMAN`.
- Les appels MCP transmettent `X-DevLog-Principal-Id`,
  `X-DevLog-Principal-Kind` et `X-DevLog-Authentication-Source` au backend.
- Le backend resout les JWT sans distinction d'acteur technique et de principal
  humain.
- `AiCredentialAssociation` et `AiCredentialBinding` existent deja comme
  frontieres Core/AI separees.
- Le bearer MCP et les tokens ChatGPT ont des lifecycles differents et ne
  doivent pas etre fusionnes.

## Perimetre IN

- Representer explicitement l'acteur MCP technique comme `AI_AGENT`.
- Definir un contexte de delegation Core reliant un acteur technique a un
  principal humain.
- Definir les donnees minimales non secretes d'une delegation : acteur humain,
  acteur technique, audience, scope, expiration, statut et reference d'audit.
- Faire valider la delegation par Core avant toute autorisation projet,
  configuration ou emission de `AiCredentialBinding`.
- Interdire a MCP de choisir un principal humain, un projet ou une association
  via les arguments d'un outil ou du texte du modele.
- Propager separement l'acteur technique et le principal humain dans les
  decisions et les diagnostics non sensibles.
- Conserver `AiCredentialAssociation` sur le principal humain deleguant et
  `AiCredentialBinding` comme capacite temporaire task-scoped.
- Rendre les erreurs d'authentification transport, delegation et credential
  distinctes et fail-closed.
- Ajouter les tests de contrat, d'isolement et de regression sur les chemins
  MCP et Core existants.

## Perimetre OUT

- Renouvellement automatique du bearer Keycloak/MCP.
- Nouveau flux OAuth distant pour MCP.
- Stockage d'un token ChatGPT dans Core, MCP ou OpenCode.
- Modification du secret store AI Engine.
- Synchronisation d'invalidation des credentials AI Engine/Core.
- Durabilite ou queue d'execution des taches AI.
- Framework IAM generique, multi-tenant billing ou impersonation generique.
- Migration automatique des anciennes donnees de principal ambigues.
- Extension de `AiCredentialBinding` en mecanisme de delegation.

## Invariants

1. Un token `client_credentials` authentifie un acteur technique seulement.
2. Un acteur MCP agissant pour un humain est de type `AI_AGENT`, jamais
   implicitement `HUMAN`.
3. L'identite humaine est obtenue par un flux Core explicite et non par un
   champ fourni par MCP, un slug de projet, un credential handle ou le modele.
4. Core est l'autorite unique pour la validation de delegation, le scope,
   l'autorisation projet et l'emission de binding.
5. Une delegation est liee a l'acteur, a l'audience, au scope et a une duree;
   elle est revocable et auditable sans secret.
6. L'autorisation effective est l'intersection de la delegation, de
   l'appartenance projet, de la capacite et de l'autorisation task/provider.
7. Le principal humain est utilise pour les decisions de configuration et de
   credential; l'acteur technique reste disponible pour l'audit et la politique
   d'acteur.
8. `AiCredentialBinding` reste opaque, temporaire, task-scoped et distinct de
   la delegation.
9. Aucun token ChatGPT, refresh token, code ou secret provider ne traverse Core,
   MCP ou OpenCode.
10. Le modele n'est jamais l'etat de session et ne peut pas renouveler,
    reconstruire ou elargir une delegation.

## Contrat cible

Le contrat exact de transport reste a fixer dans le plan, mais les concepts
Core suivants sont obligatoires :

```text
TechnicalActor
  actorId
  kind = AI_AGENT
  authenticationSource

McpDelegationGrant
  grantId
  technicalActorId
  humanPrincipalId
  audience
  scopes
  projectScope
  issuedAt
  expiresAt
  status

DelegatedExecution
  technicalActor
  humanPrincipal
  grantId
  effectiveScopes
```

Les identifiants et metadonnees sont non secrets. Le bearer de transport et la
preuve de delegation ne sont pas transmis a l'AI Engine comme credential
provider. Aucun nom d'endpoint, schema SQL ou claim JWT n'est impose par cette
Story sans decision du plan.

## Cycle de vie

1. Un utilisateur autorise explicitement l'acteur MCP via Core.
2. Core valide l'enregistrement de l'acteur et la capacite de l'utilisateur a
   deleguer le scope demande.
3. Core emet une delegation expiree et auditable.
4. MCP presente son authentification technique et la delegation pour une
   operation bornee.
5. Core valide acteur, humain, audience, scope, expiration, statut et replay.
6. Core applique l'autorisation humaine existante.
7. Core emet un binding AI task-scoped si necessaire.
8. Une revocation bloque les nouvelles operations deleguees sans reecrire
   l'historique.
9. Une delegation expiree produit une reautorisation requise, sans fallback
   vers un autre humain.

## Criteres d'acceptation

### AC-1 - Classification de l'acteur MCP

Un JWT de service valide utilise par MCP produit un acteur `AI_AGENT` et non un
principal `HUMAN`.

### AC-2 - Delegation explicite

Une operation MCP liee a un utilisateur est refusee si aucune delegation Core
valide ne relie l'acteur technique au principal humain.

### AC-3 - Pas de selection d'identite par le caller

Un principal humain, un projet ou une association fournis dans les arguments,
headers metier ou metadata arbitraires ne peuvent pas creer ou elargir une
delegation.

### AC-4 - Scope et audience

Une delegation pour un autre acteur, une autre audience, un autre projet ou une
capacite non incluse est refusee par Core.

### AC-5 - Expiration et revocation

Une delegation expiree ou revoquee est refusee avant toute autorisation projet,
emission de binding ou execution de capacite.

### AC-6 - Separation credential

L'association AI est resolue pour le principal humain deleguant. Le binding
reste limite a la tache et au provider et ne devient pas la preuve de
delegation.

### AC-7 - Audit sans secret

Les decisions peuvent distinguer acteur technique, humain, delegation, tache et
motif de refus sans journaliser de bearer, token provider, code OAuth ou secret.

### AC-8 - Erreurs fail-closed

Les absences ou invalidites d'authentification transport, de delegation et de
credential sont refusees sans fallback implicite et avec des diagnostics
categories non sensibles.

### AC-9 - Compatibilite des adaptateurs

Les chemins REST, MCP et background continuent d'utiliser la meme decision Core;
MCP reste un adaptateur mince et ne reconstruit pas l'autorisation.

### AC-10 - Tests

Les tests couvrent au minimum la classification `AI_AGENT`, l'absence de
delegation, le mismatch acteur/humain/audience/scope, l'expiration, la
revocation, l'absence de secret dans les diagnostics et la non-regression du
binding existant.

## Decisions a fixer dans le plan

1. Le point Core d'autorisation initiale et son mecanisme de session/reference.
2. La representation persistante ou transitoire de `McpDelegationGrant`.
3. Le protocole de preuve entre MCP et Core et sa protection contre le replay.
4. Les scopes V1 exacts et leur relation avec les capacites existantes.
5. La representation de l'acteur technique dans le JWT et son enregistrement.
6. La strategie de migration ou de refus des principals MCP historiques deja
   interpretes comme `HUMAN`.

Ces decisions ne peuvent pas remettre en cause les invariants de l'ADR-077.

## Tests attendus

- tests unitaires du provider MCP et de la classification de principal;
- tests Core de validation de delegation et de scope;
- tests de refus pour delegation absente, expiree, revoquee ou mal liee;
- tests d'isolement inter-acteur, inter-humain, inter-projet et inter-tache;
- tests de compatibilite `AiCredentialAssociation` et `AiCredentialBinding`;
- tests de diagnostics rediges;
- tests d'integration MCP -> Core sur les headers et le bearer;
- verification Maven ciblee puis verification backend pertinente;
- `git diff --check`.

## Documentation concernee

- ADR-077 si le contrat approuve evolue de maniere substantielle;
- documentation operateur du principal MCP et de la delegation;
- documentation de configuration locale, uniquement si le protocole implemente
  modifie le bootstrap existant.

## Gouvernance

Cette Story est autorisee pour implementation par le proprietaire humain le
2026-10-31. L'autorisation ne vaut pas approbation de l'analyse du depot ni du
plan d'implementation. Ces deux gates restent obligatoires avant toute
modification de code.
