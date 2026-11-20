# Story 0216 - Validation de resilience MCP

## Statut

IMPLEMENTATION COMPLETE - awaiting independent review.

Cette Story materialise l'etape de validation de l'epic. La validation a ete
executee avec des tests provider-free ; la revue independante et l'acceptance
finale restent des gates separees.

## Objectif

Executer une matrice provider-free de scenarios de resilience MCP afin de
verifier que les decisions d'ADR-081 et les invariants des Stories 0172, 0212,
0213, 0214 et 0215 sont effectivement observables dans le runtime Python et le
serveur MCP Java.

La Story doit produire des preuves deterministes des decisions attendues. Elle
ne doit pas ajouter un retry, modifier un budget existant ou transformer un
finding de validation en correction implicite.

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
- Story 0214 - Idempotence et securite du rejeu MCP.
- Story 0215 - Diagnostics et experience agent/operator MCP.

## Repository evidence

- Le runtime Python dispose deja de tests de renouvellement, retry borne,
  generation, concurrence, arret et rejeu conditionnel.
- Le serveur Java dispose de tests de mapping Core, d'outils MCP,
  d'observabilite et d'hygiene du protocole stdio.
- Les tests existants ne constituent pas encore une matrice nommee couvrant
  explicitement tous les scenarios de l'ADR-081.
- Les validations deja executees sont provider-free et ne necessitent pas de
  credentials reelles.
- La Story 0215 a reporte les runbooks operateur a une Story ulterieure.

## Perimetre IN

- Definir une matrice executable de scenarios et de resultats attendus.
- Valider l'expiration explicite du bearer transport.
- Valider le renouvellement refuse et l'echec fail-closed.
- Valider les erreurs Core `401`, `403`, `404` et `409`.
- Valider une delegation expiree ou refusee sans renouvellement transport.
- Valider timeout et `5xx` du endpoint token.
- Valider timeout et `5xx` Core selon l'idempotence de l'operation.
- Valider `429` avec et sans `Retry-After` selon la politique existante.
- Valider les erreurs protocole et configuration.
- Valider un resultat metier ambigu sans rejeu automatique.
- Valider les operations concurrentes et les transitions terminales uniques.
- Valider l'arret pendant renouvellement ou retry.
- Valider la preservation de `operationId`, generation et correlation quand
  elles existent.
- Verifier l'absence de secrets, payloads, tokens et messages backend bruts.
- Produire un rapport de findings qui separe regression confirmee, risque et
  decision a prendre.

## Perimetre OUT

- Ajouter ou modifier un retry, un budget, un backoff ou une politique `429`.
- Rejouer une mutation sans garantie d'idempotence Core.
- Ajouter une queue, un broker, un circuit breaker ou une telemetry durable.
- Executer des tests avec des credentials, un provider ou un backend reels.
- Creer les runbooks operateur detailles de la Story suivante.
- Modifier l'identite technique, humaine ou la delegation.
- Corriger automatiquement un finding sans Story ou autorisation explicite.

## Invariants

1. Une erreur Core ne declenche jamais un renouvellement transport.
2. Un retry reste borne et soumis a la matrice de politique.
3. Un resultat ambigu n'est pas rejoue sans garantie d'idempotence.
4. Une operation possede au plus une transition terminale observable.
5. Une generation ancienne ne peut pas terminer l'operation courante.
6. L'arret prime sur le demarrage d'un remplacement ou d'un rejeu.
7. Les diagnostics restent bornes, allowlistes et non-divulgants.
8. Les tests ne dependent pas d'un service externe ou d'un secret local.

## Criteres d'acceptation

### AC-1 - Matrice complete

Chaque scenario defini dans cette Story possede une fixture ou un test
deterministe, une decision attendue, un code diagnostique et un resultat
terminal attendu.

### AC-2 - Decisions de retry

Les tests prouvent les decisions de retry pour bearer expire, token endpoint,
Core `5xx`, `429`, erreurs fonctionnelles et erreurs protocole sans modifier les
budgets existants.

### AC-3 - Resultats ambigus et idempotence

Les tests prouvent qu'une mutation au resultat ambigu n'est pas rejouee sans
cle ou garantie d'idempotence autoritaire, et qu'une operation explicitement
rejouable conserve son identite logique.

### AC-4 - Concurrence et fail-closed

Les tests couvrent operations concurrentes, arret pendant renouvellement,
generations obsoletes et transition terminale concurrente. Toute situation non
prouvable echoue ferme.

### AC-5 - Secret safety

La matrice verifie que les journaux, evenements, diagnostics et projections ne
contiennent ni bearer, credential, token de delegation, payload JSON-RPC,
reponse provider ou details backend bruts.

### AC-6 - Rapport de validation

Le rapport distingue les tests passes, les regressions confirmees, les risques
residuels et les decisions humaines necessaires. Il ne declare pas la Story
acceptee au nom du proprietaire humain.

### AC-7 - Validation technique

Les suites Python runtime/status et MCP Java pertinentes, les tests affectes,
`git diff --check` et les checks repository applicables passent.

## Workflow

1. Repository Analysis - a realiser.
2. Implementation Plan - a realiser.
3. Approbation humaine du perimetre et du plan.
4. Autorisation humaine d'implementation des tests uniquement.
5. Execution de la matrice et rapport de findings.
6. Revue independante.
7. Acceptance humaine.

## References

- [Epic - Politique de resilience MCP](../../epics/mcp-error-resilience-policy/epic.md)
- [ADR-081](../../decisions/ADR-081.md)
- [Story 0172](../0172-mcp-runtime-observability-and-instance-lifecycle/story.md)
- [Story 0174](../0174-mcp-bearer-renewal/story.md)
- [Story 0211](../0211-mcp-error-retry-policy/story.md)
- [Story 0212](../0212-mcp-runtime-retry-renewal/story.md)
- [Story 0213](../0213-mcp-core-call-policy/story.md)
- [Story 0214](../0214-mcp-idempotency-replay-safety/story.md)
- [Story 0215](../0215-mcp-diagnostics-agent-operator-experience/story.md)
