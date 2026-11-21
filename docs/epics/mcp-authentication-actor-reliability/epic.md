# Epic - Fiabiliser l'authentification MCP et la frontiere des acteurs

## Statut

DESIGN MATERIALIZED - implementation deferred.

Cette epic est documentee pour execution apres la cloture humaine de
`agent-response-reliability`. Elle n'autorise pas l'implementation, la
modification de production, la modification des secrets, le commit ou le push.

Les Stories devront conserver leurs gates distincts : Repository Analysis,
Implementation Plan, implementation, revue et acceptance humaine.

## Declencheur

Le travail commencera uniquement lorsque l'epic
[`agent-response-reliability`](../agent-response-reliability/epic.md) sera
cloturee et acceptee par le proprietaire humain.

Cette dependance est intentionnelle : les changements d'authentification MCP,
d'identite d'acteur et de delegation ne doivent pas brouiller la validation en
cours de la qualite des reponses agentiques.

## Objectif

Rendre le chemin MCP local fiable et explicable sur deux frontieres distinctes :

1. le cycle de vie du bearer technique MCP ;
2. l'identite de l'acteur technique et, lorsque necessaire, sa delegation
   explicite a un humain.

Le systeme doit distinguer sans ambiguite :

```text
transport MCP
  -> acteur technique AI_AGENT
  -> autorisation Core du projet/capabilite
  -> delegation humaine optionnelle
  -> configuration et credential task-scoped
```

Un token expire ne doit plus rendre la session inutilisable sans tentative de
renouvellement borne. Une authentification technique valide ne doit jamais etre
interpretee implicitement comme une identite humaine.

## Probleme Et Evidence

### Probleme 1 - Bearer MCP expire

Le launcher obtient un token Keycloak `client_credentials` et le transmet au
processus MCP. Le backend a observe des appels valides suivis de :

```text
diagnostic=TOKEN_EXPIRED
oauthErrorCode=invalid_token
exceptionType=InvalidBearerTokenException
```

Le diagnostic concerne la couche Core et ne doit pas etre confondu avec une
expiration de credential ChatGPT.

Le runtime `tools/devlog_mcp/runtime.py` contient deja une implementation de
lease et de renouvellement. Cette epic doit d'abord reconcilier cette
implementation avec Story 0174 et verifier pourquoi une instance active a
neanmoins transmis un bearer expire. Elle ne doit pas recreer aveuglement le
renouvellement.

### Probleme 2 - Acteur technique confondu avec humain

Le chemin MCP utilise une authentification technique `client_credentials`, mais
la frontiere MCP/Core doit conserver la distinction entre :

- OpenCode, client externe ;
- acteur MCP technique `AI_AGENT` ;
- utilisateur humain eventuellement deleguant ;
- DevLog Agent interne ;
- credential provider task-scoped.

Le slug projet, le texte du modele, un header arbitraire ou un credential handle
ne peuvent pas selectionner un humain ou elargir une autorisation.

## Decisions Architecturales Applicables

- **ADR-070** : Core est l'autorite de l'identite applicative, de l'autorisation
  projet et des diagnostics non secrets.
- **ADR-077** : l'acteur technique et la delegation humaine sont distincts ;
  le renouvellement transport ne renouvelle pas une delegation.
- **ADR-078** : le mode autonome `AI_AGENT` est le mode MCP par defaut ; la
  delegation humaine est optionnelle.
- **ADR-081** : les erreurs sont classees par provenance et les retries sont
  bornes par l'idempotence.
- **ADR-072 / ADR-073** : les credentials ChatGPT restent dans AI Engine et le
  binding Core reste task-scoped.

## Mode Produit Cible

### Mode autonome par defaut

```text
OpenCode
  -> bearer technique renouvelable
  -> AI_AGENT stable
  -> policy Core projet/capabilite
  -> DevLog Agent
  -> AiCredentialBinding task-scoped
```

Aucune delegation humaine n'est requise pour ce mode. Le principal agent doit
etre explicitement configure et autorise par Core.

### Mode delegue optionnel

```text
AI_AGENT + McpDelegationGrant valide
  -> contexte humain delegue borne
  -> autorisation Core
  -> association provider du principal humain
```

Une delegation doit etre liee a l'acteur, l'audience, au projet, aux scopes, a
l'expiration et au statut de revocation. Son absence ou son expiration produit
un echec de delegation, jamais un fallback implicite vers un autre humain.

## Perimetre

### Inclus

- Reconciliation et validation operationnelle du renouvellement bearer de Story
  0174.
- Classification structurelle des erreurs transport, Core, delegation et
  provider selon ADR-081.
- Distinction d'un `401` Core de l'authentification transport MCP.
- Renouvellement/reacquisition borne du bearer technique.
- Retry unique uniquement lorsqu'il est autorise par la couche et l'idempotence.
- Classification des tokens `client_credentials` comme `AI_AGENT`.
- Autorisation Core du principal agent autonome.
- Validation et audit de la delegation humaine optionnelle.
- Bootstrap Keycloak/MCP reproductible sans secret dans le repository.
- Diagnostics, status/doctor et runbooks operateur sans donnees sensibles.
- Tests unitaires, integration, resilience, concurrence et non-divulgation.

### Exclus

- Tout changement avant la cloture de `agent-response-reliability`.
- Deplacement de credentials ChatGPT vers Core, MCP ou OpenCode.
- Creation d'une identite humaine a partir d'un slug, d'un prompt ou d'un
  argument MCP.
- Renouvellement implicite d'une delegation humaine pendant un renouvellement
  transport.
- Retry generique de tout `401`, `403`, `400`, `404` ou `409`.
- Rejeu d'une operation au resultat ambigu sans garantie d'idempotence.
- Nouvelle architecture RAG, queue durable, broker ou circuit-breaker global.
- Migration automatique des anciens principals MCP interpretes comme `HUMAN`.
- Decision automatique de superiority produit ou d'autorisation projet.

## Dependances Et Reconciliation

- `agent-response-reliability` doit etre acceptee et close avant demarrage.
- Story 0174 doit etre acceptee ou explicitement reconciliee avec le runtime
  actuel, qui contient deja une implementation de renouvellement.
- Story 0211 doit fournir la taxonomie et la matrice erreur/retry de reference.
- Story 0171 doit etre reconciliee avec ADR-078 : delegation optionnelle, mode
  autonome par defaut.
- Les ADR-077, ADR-078 et ADR-081 sont les contraintes architecturales de cette
  epic ; aucune decision contraire ne doit etre prise implicitement.

## Stories A Definir

1. **Reconciliation et validation du bearer MCP**
   - verifier l'instance active, le lease gate, le renouvellement et les
     processus orphelins ;
   - reproduire l'expiration avec credentials disposable ;
   - corriger uniquement les gaps verifies.
2. **Contrat d'erreurs MCP/Core et retries**
   - integrer la provenance ADR-081 entre runtime Python, MCP Java et Core ;
   - garantir qu'un `401` Core ne declenche pas un renouvellement transport.
3. **Mode autonome AI_AGENT**
   - aligner MCP et Core sur l'acteur technique stable ;
   - autoriser les projets par policy Core explicite ;
   - refuser les configurations incompletes ou ambigues.
4. **Delegation humaine optionnelle**
   - verifier acteur, humain, audience, projet, scopes, expiration, revocation et
     replay ;
   - preserver la separation avec `AiCredentialBinding`.
5. **Bootstrap et runbook operateur**
   - rendre l'enregistrement Keycloak/MCP reproductible sans secret ;
   - documenter renouvellement, reauthentification, delegation et diagnostic.
6. **Validation de resilience et securite**
   - couvrir expiration, renouvellement refuse, Core `401`, Core `403`,
     delegation invalide, concurrence, arret et resultat ambigu.

## Ordre Recommande

1. Confirmer la cloture de `agent-response-reliability`.
2. Reconciler Story 0174 avec le runtime et les diagnostics actifs.
3. Finaliser le contrat ADR-081 et les budgets de retry.
4. Valider le mode autonome `AI_AGENT` de bout en bout.
5. Valider la delegation humaine optionnelle.
6. Ajouter le bootstrap reproductible et le runbook.
7. Executer les tests de resilience et une validation disposable.
8. Effectuer une revue de securite independante.
9. Soumettre chaque Story a acceptance humaine.

## Criteres De Sortie De L'Epic

- Un bearer MCP expire est renouvele ou produit un diagnostic transport
  actionable, sans exposer de secret.
- Un `401` Core est structurellement distinct d'une expiration transport.
- Aucun retry n'est effectue sans categorie autorisee et garantie d'idempotence.
- Un token technique produit `AI_AGENT`, jamais implicitement `HUMAN`.
- Le mode autonome est autorise uniquement par Core et par une policy projet
  explicite.
- La delegation humaine est optionnelle, explicite, bornee, expiree, revocable et
  auditable.
- Les credentials ChatGPT restent dans AI Engine.
- Les diagnostics n'exposent ni bearer, ni secret, ni delegation token, ni prompt,
  ni payload provider ou repository.
- Les tests couvrent les chemins transport, Core, delegation et provider sans
  confondre leurs erreurs.
- Les decisions et Stories de cette epic sont acceptees humainement.

## References

- [Epic - Fiabiliser les reponses DevLog](../agent-response-reliability/epic.md)
- [Epic - Politique de resilience MCP](../mcp-error-resilience-policy/epic.md)
- [ADR-070](../../decisions/ADR-070.md)
- [ADR-077](../../decisions/ADR-077.md)
- [ADR-078](../../decisions/ADR-078.md)
- [ADR-081](../../decisions/ADR-081.md)
- [Story 0171](../../stories/0171-mcp-actor-human-delegation/story.md)
- [Story 0174](../../stories/0174-mcp-bearer-renewal/story.md)
- [Story 0211](../../stories/0211-mcp-error-retry-policy/story.md)
- [Investigation MCP/OpenCode/ChatGPT](../../investigations/devlog-mcp-opencode-chatgpt-oauth-stability.md)

## Gouvernance

Cette epic est documentee pour execution differee. Sa materialisation ne vaut
ni autorisation d'implementation ni acceptance des Stories dependantes.
