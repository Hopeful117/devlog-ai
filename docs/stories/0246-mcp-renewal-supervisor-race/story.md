# Story 0246 - Préserver le lanceur MCP pendant le remplacement de son processus enfant

## Statut

IMPLEMENTED - AWAITING HUMAN REVIEW. Story, analyse et plan approuvés
par le propriétaire le 26 novembre 2026. Aucun essai sur un service réel
n'a été autorisé ni exécuté.

## Contexte et objectif

Le 25 novembre 2026, OpenCode a perdu sa connexion au serveur MCP `devlog`
pendant un renouvellement du bearer de transport. Le journal local montre
l'arrêt volontaire du processus Java, puis la sortie du lanceur, sans
démarrage d'un remplaçant. Le renouvellement précédent avait réussi.

Corriger le cycle de vie du lanceur local pour qu'un remplacement légitime
ne soit pas confondu avec un arrêt définitif. Reproduire d'abord
l'entrelacement suspecté ; ne pas assimiler le rejet antérieur d'un résultat
du Story Agent à une panne du transport.

## Artefacts gouvernants

- [ADR-081 — classification des erreurs, retry et idempotence](../../decisions/ADR-081.md).
- [Story 0172 — cycle de vie et ownership](../0172-mcp-runtime-observability-and-instance-lifecycle/story.md).
- [Story 0174 — renouvellement du bearer](../0174-mcp-bearer-renewal/story.md).
- [Story 0212 — renouvellement et rejeux bornés](../0212-mcp-runtime-retry-renewal/story.md).

## Périmètre

- Supervision et renouvellement de l'enfant Java du lanceur MCP stdio,
  dans `tools/devlog_mcp/runtime.py`, et tests ciblés du runtime local.
- Préserver les règles existantes d'identité du parent, de renouvellement,
  de readiness et de rejeu, ainsi que l'intégrité du flux JSON-RPC.
- Hors périmètre : modification de Keycloak, des contrats Core/MCP, des
  politiques de retry, des plugins OpenCode ou de leur configuration, des
  intégrations Claude, et des travaux Companion. Aucun redémarrage de service
  ni test réel sans approbation distincte.

## Critères d'acceptation

1. Un test déterministe impose l'observation de l'ancien enfant arrêté entre
   son arrêt de renouvellement et le démarrage du remplaçant. Il échoue sur
   le comportement actuel et réussit après correction, sans Keycloak, Java,
   OpenCode ni attente fondée sur le hasard de l'ordonnancement.
2. Après un renouvellement réussi, le même lanceur supervise le remplaçant,
   reprend le relais stdio après le handshake et n'annonce pas la sortie de
   l'instance à cause de l'ancien enfant.
3. Un arrêt demandé ou la disparition du parent reste prioritaire, y
   compris pendant le renouvellement. Le lanceur ne laisse aucun remplaçant
   actif après l'arrêt définitif et ne termine aucun processus étranger.
4. Si l'acquisition du jeton, le démarrage ou le handshake échoue, le runtime
   ne se déclare pas prêt et ne reste pas indéfiniment en attente du
   renouvellement. Une sortie d'enfant hors renouvellement reste détectée.
5. Aucun appel ambigu ou non éligible n'est rejoué. Les chemins de rejeu
   déjà autorisés restent bornés. Aucun doublon terminal, secret ou
   diagnostic non protocolaire n'est ajouté au flux JSON-RPC.
6. Les nouveaux tests et la suite du lanceur MCP passent. Le rapport
   distingue la reproduction contrôlée et les éventuelles validations
   réelles, autorisées séparément.

## Gouvernance

L'[analyse de dépôt](repository-analysis.md) a été approuvée. Le
[plan d'implémentation](implementation-plan.md) a fait l'objet d'une
approbation distincte. Le [rapport](implementation-report.md) est soumis
à revue humaine. Les essais réels restent exclus.
