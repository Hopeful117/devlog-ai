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
2. [Story 0212 - Retry et renouvellement du runtime MCP](../../stories/0212-mcp-runtime-retry-renewal/story.md)
3. [Story 0213 - Politique des appels MCP vers Core](../../stories/0213-mcp-core-call-policy/story.md)
4. [Story 0214 - Idempotence et securite du rejeu](../../stories/0214-mcp-idempotency-replay-safety/story.md)
5. [Story 0215 - Diagnostics et experience agent/operator MCP](../../stories/0215-mcp-diagnostics-agent-operator-experience/story.md)
6. [Story 0216 - Validation de resilience MCP](../../stories/0216-mcp-resilience-validation/story.md)
7. [Story 0217 - Retry borne des erreurs 429 et respect de Retry-After](../../stories/0217-mcp-rate-limit-retry-after/story.md)
8. [Story 0218 - Documentation et runbooks operateur MCP](../../stories/0218-mcp-documentation-runbooks/story.md)

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
8. Implementer le retry borne des erreurs `429` et la prise en compte de
   `Retry-After`.
9. Finaliser la documentation et les runbooks.

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
