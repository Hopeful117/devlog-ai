# Repository Analysis - Story 0216

## Scope

Cette analyse prepare une validation provider-free de la resilience MCP. Elle
ne constitue pas une autorisation de modifier le runtime ou le serveur.

## Current implementation surfaces

### Python runtime

- `tools/devlog_mcp/runtime.py` porte le renouvellement du bearer, le retry
  borne du token endpoint, la gestion des generations, les reservations
  d'operations, les rejeux conditionnels et les evenements lifecycle.
- `tools/devlog_mcp/status.py` projette les etats runtime et les diagnostics
  operateur.
- `tools/devlog_mcp/tests/test_runtime.py` couvre deja des scenarios de timeout,
  `5xx`, renouvellement, concurrence, arret, generation et idempotence.
- `tools/devlog_mcp/tests/test_status.py` couvre la projection et la redaction.

### Java MCP server

- `CoreCallPolicy` centralise le mapping des erreurs Core et les diagnostics.
- Les tests `CoreCallPolicyTest`, `ResourceSupportTest` et les tests d'outils
  couvrent les erreurs Core, la non-divulgation et les contrats d'appel.
- Les tests de protocole et d'observabilite couvrent stdio, correlation et
  lifecycle MCP.

## Validation gaps

- Les scenarios existent principalement comme tests unitaires distribues, pas
  comme matrice traceable vers ADR-081.
- La combinaison entre erreur, decision de retry, idempotence et resultat
  ambigu doit etre verifiee explicitement.
- Les scenarios de `429`, delegation/provider, generations obsoletes et arret
  pendant retry doivent etre rattaches a des attentes normatives uniques.
- Les findings doivent etre rapportes sans modifier automatiquement le
  comportement de production.

## Constraints

- Les invariants de Story 0172 restent prioritaires pour ownership, generations
  et transitions terminales.
- ADR-081 interdit les retries generiques, les rejeux ambigus et les secrets
  dans les diagnostics.
- Stories 0212 a 0215 sont des implementations precedentes a valider, pas des
  contrats a redefinir silencieusement.
- La validation doit rester deterministe et independante de credentials ou
  services externes.

## Proposed evidence

- Une matrice scenario -> fixture/test -> decision -> preuve observable.
- Un rapport des tests passes et des findings avec severite et provenance.
- Les sorties de suites Python et MCP Java sans payload ou secret.
- Une verification finale de `git diff --check` et de la non-modification du
  comportement de production hors tests ou documentation autorises.

## Unknowns requiring human decision

- Faut-il limiter la Story a des tests unitaires/contractuels, ou autoriser un
  harness d'integration local provider-free ?
- Les scenarios `429` doivent-ils seulement verifier la classification actuelle
  ou aussi une simulation de `Retry-After` ?
- Un finding de production doit-il bloquer l'acceptance de la Story ou etre
  transforme en Story corrective distincte ?
