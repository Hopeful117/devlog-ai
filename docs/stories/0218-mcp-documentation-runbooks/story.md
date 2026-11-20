# Story 0218 - Documentation et runbooks operateur MCP

## Statut

IMPLEMENTATION COMPLETE - awaiting independent review.

Cette Story finalise l'epic de resilience MCP par la reconciliation de la
documentation operateur avec les comportements stabilises par les Stories
0211 a 0217. Le Repository Analysis et l'Implementation Plan ont ete approuves.
La revue independante et l'acceptance humaine restent des gates distinctes.

## Objectif

Fournir une documentation operateur unique, exacte et actionnable pour
diagnostiquer les incidents MCP, appliquer les recuperations sures et eviter
les rejeux dangereux ou l'exposition de secrets.

La documentation operateur canonique de cette Story est `TOOLS.md`. Aucun
nouveau repertoire de runbooks n'est necessaire.

## Artefacts gouvernants

- Epic `mcp-error-resilience-policy`.
- ADR-077 - MCP Actor Identity and Human Delegation Boundary.
- ADR-078 - DevLog Internal Agent and External Agent Client Identity.
- ADR-081 - MCP Error Classification, Retry and Idempotency Policy.
- Story 0172 - MCP Runtime Observability and Instance Lifecycle.
- Story 0174 - MCP Transport Bearer Renewal and Session Reconnection.
- Story 0211 - Politique normative des erreurs MCP et des retries.
- Story 0212 - Retry et renouvellement du runtime MCP.
- Story 0213 - Politique des appels MCP vers Core.
- Story 0214 - Idempotence et securite du rejeu.
- Story 0215 - Diagnostics et experience agent/operator MCP.
- Story 0216 - Validation de resilience MCP.
- Story 0217 - Retry borne des erreurs 429 et respect de Retry-After.

## Perimetre IN

- Reconciler `TOOLS.md` avec le contrat actuel des diagnostics MCP.
- Documenter les commandes read-only `status --json` et `doctor --json`.
- Documenter les categories, codes, couches, actions recommandees et limites
  des diagnostics.
- Documenter les procedures de diagnostic et de recuperation pour :
  - configuration invalide ;
  - expiration, refus ou echec de renouvellement du bearer transport ;
  - erreurs Core `401`, `403`, `404`, `409`, timeout et `5xx` ;
  - delegation absente, expiree ou revoquee ;
  - erreur provider ;
  - erreur protocole ou processus MCP ;
  - `RATE_LIMITED` avec ou sans `Retry-After` ;
  - arret attendu `MCP_INSTANCE_STOPPING`.
- Documenter la difference entre retry automatique borne, recuperation
  manuelle et rejeu interdit.
- Documenter les operations ambigues ou non idempotentes qui ne doivent pas
  etre rejouees automatiquement.
- Ajouter une checklist d'incident et les informations non sensibles a
  collecter pour l'escalade.
- Verifier que toutes les commandes et chemins documentes correspondent au
  repository actuel.
- Ajouter des liens depuis les sections operateur existantes lorsque cela
  ameliore la navigation, sans dupliquer les contrats.

## Perimetre OUT

- Modifier le runtime Python, le serveur Java, Core, Keycloak ou un provider.
- Ajouter ou modifier un retry, un budget, un backoff, une politique
  d'idempotence ou un mecanisme de delegation.
- Introduire une queue, un broker, un scheduler, un outbox ou une telemetry
  durable.
- Executer une recuperation reelle avec credentials, bearer ou delegation.
- Ajouter des secrets, tokens, payloads, reponses provider ou corps backend
  dans la documentation.
- Creer un nouveau repertoire documentaire sans besoin etabli ; `TOOLS.md`
  reste la source operateur canonique.
- Declarer une Story de l'epic acceptee au nom du proprietaire humain.

## Invariants

1. La documentation ne doit jamais conseiller de rejouer une operation dont le
   resultat est ambigu sans garantie d'idempotence.
2. Un `401` Core, un `403` ou une erreur de delegation ne doivent pas etre
   presentes comme une expiration automatique du bearer transport.
3. Un `429` peut etre retente automatiquement une seule fois uniquement selon
   la politique de Story 0217 ; la documentation ne doit pas promettre un
   retry illimite.
4. Les commandes documentees sont read-only, sauf indication explicite qu'une
   action est manuelle et soumise a autorisation operateur.
5. Les secrets et donnees sensibles ne sont jamais demandes dans une issue,
   un log, un prompt ou un artefact de diagnostic.
6. Les actions recommandees restent informatives et n'accordent aucune
   autorisation d'execution.

## Criteres d'acceptation

### AC-1 - Documentation canonique

`TOOLS.md` contient les procedures operateur de l'epic et ne contredit plus
ADR-081 ni les Stories 0212 a 0217.

### AC-2 - Diagnostic initial

Un operateur dispose d'une sequence read-only explicite utilisant
`status --json`, `doctor --json` et les informations de correlation disponibles,
sans devoir lire le code source.

### AC-3 - Matrice de recuperation

Chaque categorie d'erreur pertinente indique :

- le signal ou code observable ;
- l'interpretation correcte ;
- l'action read-only recommandee ;
- l'action manuelle eventuelle ;
- l'action interdite ;
- la condition d'escalade.

### AC-4 - Retry et idempotence

La documentation distingue clairement :

- retry automatique borne du runtime ;
- recuperation manuelle apres echec terminal ;
- operation idempotente rejouable ;
- mutation ou resultat ambigu non rejouable.

### AC-5 - `429` et `Retry-After`

La documentation decrit le plafond et la tentative unique de Story 0217,
ainsi que le comportement lorsque `Retry-After` est absent, invalide, expire
ou depasse le plafond.

### AC-6 - Secret safety

La documentation ne contient aucun credential reel et interdit explicitement
la collecte ou l'exposition de bearer, token de delegation, secret client,
payload JSON-RPC, prompt, reponse provider ou corps backend.

### AC-7 - Checklist d'incident

Une checklist permet de collecter au minimum l'instance, l'operation, la
generation, le code diagnostique, le statut, l'heure et l'action deja tentee,
sans collecter de donnees sensibles.

### AC-8 - Validation documentaire

Les liens, commandes, chemins et exemples de la documentation sont verifies ;
`git diff --check` passe et aucun changement de code runtime n'est introduit.

## Validation attendue

- Recherche des anciens contrats contradictoires, notamment `429` terminal ou
  reserve a une Story future.
- Verification des chemins `tools/devlog_mcp/status.py` et de la commande
  `unittest` documentee.
- Verification des references vers ADR-081 et Stories 0211 a 0217.
- Verification de l'absence de secrets et de valeurs ressemblant a des tokens.
- Verification des liens Markdown relatifs du repository lorsque l'outil est
  disponible.
- `git diff --check`.

## Workflow et gates

1. Repository Analysis de cette Story.
2. Approbation humaine du Repository Analysis.
3. Implementation Plan.
4. Approbation humaine de l'Implementation Plan.
5. Implementation documentaire dans `TOOLS.md`.
6. Documentation reconciliation.
7. Revue independante.
8. Acceptance humaine de la Story.

## References

- [Epic - Politique de resilience MCP](../../epics/mcp-error-resilience-policy/epic.md)
- [TOOLS.md](../../../TOOLS.md)
- [ADR-081](../../decisions/ADR-081.md)
- [Story 0211](../0211-mcp-error-retry-policy/story.md)
- [Story 0212](../0212-mcp-runtime-retry-renewal/story.md)
- [Story 0213](../0213-mcp-core-call-policy/story.md)
- [Story 0214](../0214-mcp-idempotency-replay-safety/story.md)
- [Story 0215](../0215-mcp-diagnostics-agent-operator-experience/story.md)
- [Story 0216](../0216-mcp-resilience-validation/story.md)
- [Story 0217](../0217-mcp-rate-limit-retry-after/story.md)
