# Epic - Simplifier la configuration des providers et modeles IA de DevLog AI

## Statut

DESIGN MATERIALIZED - implementation not authorized.

## Objectif produit

Permettre a un utilisateur de choisir une solution IA, de la connecter ou de
la configurer, d'accepter ou choisir un modele, de tester la configuration,
d'atteindre un etat `READY`, puis d'utiliser cette configuration pour une
tache DevLog.

Le parcours cible est:

```text
Choisir une solution IA
  -> connecter/configurer
  -> accepter ou choisir un modele
  -> tester
  -> READY
  -> utiliser une tache DevLog
```

Le premier vertical slice doit valider les deux familles de connexion
destinees aux utilisateurs:

- OAuth avec `openai_chatgpt`;
- API key avec `anthropic`.

Le provider `mock` reste disponible pour les tests et le developpement, mais
ne constitue pas une validation suffisante de l'experience utilisateur cible.

## Contexte

L'AI Engine supporte actuellement `mock`, `openai`, `openai_chatgpt`,
`gemini`, `anthropic` et `openrouter` derriere une abstraction commune
`LlmProvider`. La configuration est principalement process-scoped via
`LLM_PROVIDER` et `LLM_MODEL`.

DevLog dispose deja d'un stockage chiffre de credentials dans l'AI Engine,
d'associations de credentials dans Core et de `AiCredentialBinding` limites a
une tache. Le frontend ne fournit pas encore un parcours produit complet de
configuration.

## Probleme utilisateur

La configuration actuelle suppose de comprendre les variables d'environnement,
les identifiants internes des providers, les differences OAuth/API key, les
modeles et les mecanismes de binding. Cette complexite ne doit pas etre
imposee a un utilisateur qui souhaite simplement activer l'IA.

## Valeur utilisateur

- configuration initiale sans connaissance des details internes;
- parcours identique au niveau produit pour OAuth et API key;
- modele recommande immediatement utilisable;
- test de configuration borne et comprehensible;
- reconnexion, remplacement et revocation explicites;
- mode Advanced disponible sans imposer sa complexite;
- aucun secret reaffiche apres son enregistrement.

## Scope IN

- catalogue provider/modele controle par DevLog;
- distinction provider, methode d'authentification, credential,
  configuration, association, binding et runtime state;
- preferences utilisateur non secretes;
- configuration OAuth ChatGPT;
- intake et lifecycle d'une API key utilisateur pour Anthropic;
- test de configuration sans contexte repository;
- parcours frontend Simple et Advanced;
- integration aux taches et bindings Core existants;
- redaction, audit, isolation, expiration et revocation;
- documentation et reconciliation ADR-072/ADR-073.

## Scope OUT

- reconstruction de `LlmProvider`;
- stockage de secrets dans Core;
- nouveau systeme d'autorisation dans l'AI Engine;
- fallback automatique entre providers, credentials ou modeles;
- decouverte dynamique complete des modeles en V1;
- scoring ou benchmark de modeles;
- multi-credentials complet en V1;
- facturation, quotas et multi-tenant billing;
- acces aux conversations ChatGPT;
- modification du grounding, du contexte ou de la promotion trustee;
- remediation du fichier `.env`.

## Invariants architecturaux

1. Java/Core reste l'autorite du principal, projet, tache, binding et contexte.
2. Python/AI Engine reste responsable de l'execution des providers.
3. L'AI Engine reste proprietaire du stockage des secrets provider.
4. Core ne recoit ni ne persiste une API key ou un token OAuth.
5. Le frontend ne relit jamais une credential apres son enregistrement.
6. Un handle opaque n'est jamais une autorisation d'utilisation.
7. L'utilisation reelle necessite un `AiCredentialBinding` gouverne par Core.
8. REST, MCP et background utilisent la meme decision Core.
9. Simple et Advanced convergent vers le meme modele de configuration.
10. Un modele explicitement choisi ne peut jamais etre remplace automatiquement.

## Modele conceptuel

```text
Provider
  -> Authentication Method
  -> Credential
  -> Provider Configuration
  -> Credential Association
  -> Credential Binding
  -> Runtime State
```

- **Provider**: systeme externe ou mode d'execution, par exemple Anthropic,
  ChatGPT ou Mock.
- **Authentication Method**: OAuth, API key ou aucune authentification.
- **Credential**: secret ou autorisation detenue par l'AI Engine.
- **Provider Configuration**: choix non secret de l'utilisateur.
- **Credential Association**: relation Core entre un principal, un provider
  et un handle AI Engine.
- **Credential Binding**: autorisation Core temporaire et limitee a une tache.
- **Runtime State**: etat courant de disponibilite et de validite.

Le catalogue contient les metadonnees controlees par DevLog: providers,
methodes, modeles connus, recommandations et capacites. Il ne constitue pas
un systeme d'autorisation.

## Regles de modele

DevLog distingue:

- modele recommande;
- modele initial/default;
- modele explicitement choisi;
- modele actuellement disponible.

Il n'existe aucun fallback automatique de modele. Si le modele configure
devient indisponible:

1. la configuration quitte `READY`;
2. l'etat `MODEL_UNAVAILABLE` ou equivalent est expose;
3. DevLog peut proposer le modele recommande actuel;
4. l'utilisateur doit accepter explicitement le remplacement.

Une mise a jour du catalogue ne modifie jamais silencieusement une
configuration existante.

## Vertical slice

### OAuth

```text
openai_chatgpt
  -> OAuth
  -> association
  -> modele recommande
  -> test sans contexte repository
  -> READY
  -> binding Core
  -> tache DevLog reelle
```

### API key

```text
anthropic
  -> intent d'enregistrement Core
  -> intake securise
  -> stockage chiffre AI Engine
  -> handle opaque
  -> association Core
  -> modele recommande
  -> test sans contexte repository
  -> READY
  -> binding Core
  -> tache DevLog reelle
```

L'intent API key est court, signe par Core, lie au principal, provider et
methode, a usage unique, protege contre le replay et inutilisable comme task
binding.

## Criteres de succes

- OAuth ChatGPT fonctionne de bout en bout depuis le parcours Simple.
- Anthropic API key fonctionne de bout en bout depuis le parcours Simple.
- Les deux parcours convergent vers le meme modele de configuration.
- Aucun secret n'apparait dans Core, frontend, logs, traces, snapshots,
  prompts ou callbacks.
- Une tache reelle obtient toujours une autorisation Core valide.
- Une revocation ou expiration bloque les utilisations futures.
- Un modele indisponible bloque `READY` sans remplacement silencieux.
- Le catalogue peut accueillir un nouveau provider sans reconstruire toute l'UX.

## Dependances architecturales

- [ADR-006](../../decisions/ADR-006.md)
- [ADR-067](../../decisions/ADR-067.md)
- [ADR-068](../../decisions/ADR-068.md)
- [ADR-069](../../decisions/ADR-069.md)
- [ADR-070](../../decisions/ADR-070.md)
- [ADR-072](../../decisions/ADR-072.md)
- [ADR-073](../../decisions/ADR-073.md)
- [ADR-074](../../decisions/ADR-074.md)
- [ADR-075](../../decisions/ADR-075.md)
- [ADR-076](../../decisions/ADR-076.md)

## Engineering Stories

1. [Story 0162 - Decisions and documentation boundary](../../stories/0162-ai-provider-configuration-decisions/story.md)
2. [Story 0163 - Provider and Model Catalog](../../stories/0163-provider-model-catalog/story.md)
3. [Story 0164 - User AI Configuration and State](../../stories/0164-user-ai-configuration-state/story.md)
4. [Story 0165 - OAuth Provider Configuration Integration](../../stories/0165-oauth-provider-configuration/story.md)
5. [Story 0166 - User API Key Credential Lifecycle](../../stories/0166-user-api-key-credential-lifecycle/story.md)
6. [Story 0167 - AI Configuration Test](../../stories/0167-ai-configuration-test/story.md)
7. [Story 0168 - Simple AI Configuration UX](../../stories/0168-simple-ai-configuration-ux/story.md)
8. [Story 0169 - DevLog Task Configuration Integration](../../stories/0169-devlog-task-configuration-integration/story.md)
9. [Story 0170 - Advanced UX, Security and Documentation](../../stories/0170-advanced-ux-security-documentation/story.md)

## Recommended order

ADR-074, ADR-075 and ADR-076 must be approved before their implementation
Stories. Story 0162 records the decision boundary and ADR reconciliation.
The implementation sequence is 0163, 0164, 0165, 0166, 0167, 0168, 0169,
then 0170.

## Governance

This Epic materializes the design only. It does not authorize implementation,
production configuration, live provider validation or a change to the status
of ADR-072 or ADR-073.
