# Epic - Politique de resilience et de gestion des erreurs MCP

## Statut

DESIGN MATERIALIZED - implementation not authorized.

Cette epic est prioritaire. Sa validation autorise la planification et la
materialisation de ses Stories, mais pas l'implementation, le commit, le push
ou le changement de comportement de production.

## Objectif

Definir puis implementer une politique unique pour les erreurs MCP, notamment
les erreurs `401`, les renouvellements de bearer, les retries, les timeouts,
l'idempotence et les echecs fermes.

L'objectif n'est pas de rendre tous les appels automatiquement retryables. Un
appel doit echouer de facon previsible, explicable et recuperable lorsque cela
est sur.

## Probleme

Les Stories 0172 et 0174 ont deja mis en place l'observabilite du runtime, le
renouvellement du bearer et un retry limite pour une erreur MCP precise. Il
manque cependant une politique transversale qui distingue explicitement :

- authentification transport ;
- authentification ou autorisation Core ;
- delegation ;
- erreurs fonctionnelles ;
- erreurs transitoires d'infrastructure ;
- protocole MCP ;
- credentials ou erreurs provider.

Les conditions de retry, de rejeu et de fail-closed ne sont pas definies dans
une matrice commune.

## Principes

1. Aucun retry illimite ou concurrent pour une meme operation.
2. Un rejeu exige une garantie d'idempotence explicite.
3. Une operation dont l'execution est ambigue n'est pas rejouee par defaut.
4. Le renouvellement du bearer reste distinct de la delegation humaine et des
   credentials provider.
5. Une erreur `401` ne doit pas etre automatiquement interpretee comme une
   expiration du bearer transport.
6. Les diagnostics sont bornes et ne contiennent aucun secret ou payload.
7. Story 0172 reste l'autorite pour l'ownership, les generations et les
   transitions terminales du runtime.

## Stories

1. [Story 0211 - Politique normative des erreurs MCP et des retries](../../stories/0211-mcp-error-retry-policy/story.md)
2. Story a definir - Retry et renouvellement du runtime MCP
3. Story a definir - Politique des appels MCP vers Core
4. Story a definir - Idempotence et securite du rejeu
5. Story a definir - Diagnostics et experience agent/operateur
6. Story a definir - Validation de resilience
7. Story a definir - Documentation et runbooks

Les Stories 0172 et 0174 sont des dependances a reconcilier, pas des travaux a
dupliquer.

## Ordre recommande

1. Figer la taxonomie et la matrice de politique.
2. Reconciler les comportements de 0172 et 0174.
3. Implementer les retries transitoires et le renouvellement borne.
4. Uniformiser les appels MCP vers Core.
5. Formaliser l'idempotence et le rejeu.
6. Ajouter diagnostics, alertes et runbooks.
7. Executer la matrice de resilience.

## Hors perimetre

- Modifier l'identite technique ou humaine.
- Renouveler automatiquement une delegation humaine.
- Changer la propriete des credentials ChatGPT.
- Introduire une queue durable ou un broker.
- Rejouer automatiquement une tache dont l'execution est ambigue.
- Masquer une erreur d'autorisation par un retry agressif.

## Gouvernance

Un nouvel ADR est probablement necessaire car cette epic definit des semantiques
transversales de retry, d'idempotence et de classification d'erreur. Son numero
et son statut seront decides pendant la premiere Story.

Chaque Story conserve les gates de Repository Analysis, Implementation Plan,
implementation et acceptance. La materialisation de cette epic n'autorise pas
l'implementation.

## References

- [Story 0172](../../stories/0172-mcp-runtime-observability-and-instance-lifecycle/story.md)
- [Story 0174](../../stories/0174-mcp-bearer-renewal/story.md)
- [ADR-077](../../decisions/ADR-077.md)
- [ADR-078](../../decisions/ADR-078.md)
- [ADR-081](../../decisions/ADR-081.md)
- [Investigation MCP/OpenCode/ChatGPT](../../investigations/devlog-mcp-opencode-chatgpt-oauth-stability.md)
