# Story 0217 - Retry borne des erreurs 429 et respect de Retry-After

## Statut

IMPLEMENTATION COMPLETE - awaiting human acceptance.

Cette Story traite le gap identifie entre ADR-081 et les Stories 0212 a 0216.
Le Repository Analysis et l'Implementation Plan ont ete approuves avant
implementation. La revue independante est passee ; l'acceptance humaine reste
une gate distincte. Aucun commit ni push n'a ete effectue.

## Objectif

Implementer, dans l'autorite de retry deja retenue, un traitement borne des
reponses `429` qui respecte `Retry-After` lorsqu'il est valide, sans introduire
de scheduler durable, de queue, de broker ou de rejeu dangereux.

Le runtime Python reste l'unique autorite de decision et d'execution du retry.
Les adaptateurs Java/MCP ne doivent pas ajouter un retry concurrent ou
independant.

## Gap couvert

- ADR-081 autorise un retry `429` conditionnel, borne et compatible avec
  `Retry-After`.
- Story 0212 reporte `429` a une Story ulterieure et reserve l'autorite du
  retry au runtime Python.
- Story 0213 classe `429` sans retry dans les appels Core Java/MCP.
- Story 0214 n'a pas implemente la politique `429` et laisse explicitement la
  decision ouverte.
- Stories 0215 et 0216 excluent toute modification de budget ou de politique.

## Artefacts gouvernants

- Epic `mcp-error-resilience-policy`.
- ADR-081 - MCP Error Classification, Retry and Idempotency Policy.
- Story 0211 - Politique normative des erreurs MCP et des retries.
- Story 0212 - Retry et renouvellement du runtime MCP.
- Story 0213 - Politique des appels MCP vers Core.
- Story 0214 - Idempotence et securite du rejeu.
- Story 0215 - Diagnostics et experience agent/operator MCP.
- Story 0216 - Validation de resilience MCP.

## Perimetre IN

- Definir le contrat de traitement d'une reponse `429`.
- Parser `Retry-After` uniquement dans les formes supportees et sures.
- Appliquer un delai borne par le budget de retry existant et par un plafond
  local explicite.
- Traiter l'absence, l'invalidite ou l'exces de `Retry-After` de facon
  deterministe et fail-closed.
- N'autoriser le rejeu que pour les operations deja declarees rejouables par
  la politique d'idempotence.
- Preserver l'`operationId`, la generation, la reservation et la transition
  terminale unique pendant un retry autorise.
- Respecter l'arret du runtime et eviter les retries concurrents pour une meme
  operation.
- Exposer un diagnostic borne indiquant la categorie, la tentative, le budget,
  le delai applique et l'action recommandee, sans secret ni payload.
- Ajouter les tests provider-free et mettre a jour la documentation runtime
  necessaire.

## Perimetre OUT

- Creer un scheduler durable, une queue, un broker, un outbox ou un circuit
  breaker.
- Reprogrammer une operation apres redemarrage du processus.
- Ajouter un retry automatique dans Java/MCP ou dans les adaptateurs Core.
- Rejouer une mutation sans garantie d'idempotence metier ou de deduplication
  autoritaire.
- Modifier les contrats d'identite, de delegation ou de credentials provider.
- Modifier les budgets des retries bearer et token endpoint sans decision
  explicite et coherence avec Story 0212.
- Executer des tests avec un provider, Keycloak, Core ou des credentials reels.

## Invariants

1. Un retry est interdit par defaut et exige une categorie, un budget et une
   condition d'idempotence explicites.
2. Le runtime Python est l'autorite unique du retry.
3. Une reponse `429` Core ne declenche jamais un renouvellement du bearer
   transport.
4. Une operation ambigue ou non idempotente n'est jamais rejouee
   automatiquement.
5. `Retry-After` ne peut jamais depasser le plafond ou le budget autorise.
6. Une seule operation logique, une seule generation et une seule transition
   terminale sont conservees.
7. L'arret prime sur l'attente ou le rejeu.
8. Les diagnostics ne contiennent ni header d'authentification, ni token, ni
   payload, ni reponse backend brute.

## Decisions humaines requises

1. Confirmer les operations MCP officiellement rejouables apres `429`.
2. Confirmer le budget maximal et le plafond de delai pour `429`.
3. Decider si une valeur `Retry-After` invalide ou excessive provoque un
   echec immediat ou un fallback borne au backoff local.
4. Confirmer que le premier increment ne couvre que le runtime Python et non
   les clients Java/MCP.
5. Confirmer le niveau de visibilite du delai applique dans les diagnostics
   exposes a l'agent.

## Criteres d'acceptation

### AC-1 - Contrat 429

Les reponses `429` sont distinguees des erreurs d'authentification, de
delegation, fonctionnelles et protocolaires, avec un diagnostic structurel
`RATE_LIMITED`.

### AC-2 - Retry-After borne

Une valeur `Retry-After` valide est respectee uniquement dans les limites du
budget et du plafond autorises. Aucune attente illimitee n'est possible.

### AC-3 - En-tete absent ou invalide

Les cas absent, invalide, negatif, depassement de plafond et date impossible
produisent un comportement deterministe conforme a la decision humaine, sans
rejeu non borne.

### AC-4 - Idempotence

Une operation rejouable conserve son identite logique et son `operationId`.
Une operation non idempotente ou au resultat ambigu echoue sans rejeu
automatique.

### AC-5 - Autorite unique

Les tests prouvent qu'aucun retry automatique n'est ajoute dans les adaptateurs
Java/MCP et qu'un `429` Core ne declenche pas de renouvellement transport.

### AC-6 - Concurrence et arret

Une meme operation ne lance pas plusieurs retries concurrents, et un arret
interrompt l'attente ou empeche le rejeu.

### AC-7 - Secret safety

Les logs, evenements, diagnostics et reponses n'exposent ni bearer, credential,
token de delegation, payload, header sensible ou reponse backend brute.

### AC-8 - Validation provider-free

Les tests runtime, les tests MCP Java affectes, les tests de redaction et
`git diff --check` passent. Toute validation live est declaree non executee.

## Tests attendus

- `429` avec `Retry-After` en secondes valide ;
- `429` avec date HTTP valide ;
- `Retry-After` absent, invalide, negatif ou excessif ;
- budget deja consomme ;
- operation idempotente puis succes ;
- operation non idempotente ou resultat ambigu ;
- `429` Core sans renouvellement transport ;
- deux `429` concurrents sur la meme operation ;
- arret pendant l'attente ;
- preservation de l'`operationId` et de la generation ;
- redaction des headers, tokens et payloads.

## Workflow et gates

1. Repository Analysis de cette Story.
2. Approbation humaine du Repository Analysis.
3. Implementation Plan.
4. Approbation humaine de l'Implementation Plan.
5. Implementation minimale et tests cibles.
6. Documentation reconciliation.
7. Revue independante et verification complete.
8. Acceptance humaine de la Story.

## References

- [Epic - Politique de resilience MCP](../../epics/mcp-error-resilience-policy/epic.md)
- [ADR-081](../../decisions/ADR-081.md)
- [Story 0211](../0211-mcp-error-retry-policy/story.md)
- [Story 0212](../0212-mcp-runtime-retry-renewal/story.md)
- [Story 0213](../0213-mcp-core-call-policy/story.md)
- [Story 0214](../0214-mcp-idempotency-replay-safety/story.md)
- [Story 0215](../0215-mcp-diagnostics-agent-operator-experience/story.md)
- [Story 0216](../0216-mcp-resilience-validation/story.md)
