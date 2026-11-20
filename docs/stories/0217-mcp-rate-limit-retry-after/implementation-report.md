# Story 0217 - Implementation Report

## Statut

IMPLEMENTATION COMPLETE - awaiting human acceptance.

L'implementation ajoute un retry unique et borne des operations rejouables apres
`429`, avec propagation d'une metadata `retryAfterSeconds` validee et plafonnee
par le serveur MCP Java. Aucun retry n'est execute dans Java et aucun scheduler
durable n'a ete introduit.

## Files Changed

- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/service/CoreCallPolicy.java`:
  valide et borne `Retry-After` en secondes ou date HTTP et expose uniquement
  `retryAfterSeconds`.
- `mcp-server/src/test/java/hopefull117/devlogai_mcp/mcp_server/service/CoreCallPolicyTest.java`:
  couvre metadata valide, bornage et valeurs invalides.
- `tools/devlog_mcp/runtime.py`:
  ajoute le retry `429` interrompable, limite a une tentative et soumis a
  l'allowlist d'idempotence existante.
- `tools/devlog_mcp/tests/test_runtime.py`:
  couvre le retry, l'identite d'operation, le rejet des mutations et les
  bornes de delai.

## Architectural Constraints Preserved

- Le runtime Python reste l'autorite unique du retry.
- Les adaptateurs Java ne rejouent aucune operation.
- Un `429` Core ne declenche pas de renouvellement bearer.
- Les operations non idempotentes ou ambigues restent fail-closed.
- L'`operationId`, le payload original, la generation et la transition
  terminale sont preserves.
- Aucun secret, header HTTP brut, payload ou message backend n'est propage dans
  les diagnostics.
- Aucun scheduler durable, queue, broker, outbox ou circuit breaker n'a ete
  ajoute.

## Tests Executed

```bash
python3 -m py_compile tools/devlog_mcp/runtime.py tools/devlog_mcp/tests/test_runtime.py
python3 -m unittest tools.devlog_mcp.tests.test_runtime -q
python3 -m unittest discover -s tools/devlog_mcp/tests -q
./mcp-server/mvnw -f mcp-server/pom.xml -Dtest=CoreCallPolicyTest test -B
./mcp-server/mvnw -f mcp-server/pom.xml test -B
git diff --check
```

## Independent Review Corrections

- Serialisation explicite de l'attente `429` pour empecher deux retries
  concurrents ou une recreation d'operation.
- Rejeu `429` sans renouvellement bearer transport.
- Requetes sans outil explicite exclues du retry `429`.
- Parsing HTTP-date rendu testable avec une horloge fournie et couvre les dates
  futures, expirees et les valeurs invalides.
- Test reel d'arret pendant l'attente et test de concurrence ajoutes.

## Test Results

- Python runtime cible : **61 tests passed** lors de la premiere correction,
  puis **69 tests passed** apres fermeture des races finales.
- Python runtime complet via `unittest discover` : **69 tests passed**.
- Java `CoreCallPolicyTest` : **10 tests passed**.
- Suite Java MCP complete : **121 tests passed**.
- `git diff --check` : **passed**.
- Les warnings Mockito/Byte Buddy observes dans la suite Java sont preexistants
  et n'ont pas cause d'echec.

## Environment Limitations

- `pytest` n'est pas installe dans l'environnement (`python3 -m pytest` est
  indisponible) ; la suite Python a ete executee avec `unittest`, compatible
  avec les tests concernes.
- Aucun endpoint Core, Keycloak, provider ou credential reel n'a ete utilise.

## Documentation Reconciliation

Documentation update: the Story plan and implementation report record the
contract and validation outcome. No broader runtime documentation was modified
because the user-facing tool contract remains structurally classified as
`RATE_LIMITED` and the new metadata is internal to the MCP/runtime boundary.

## Vault Outcome

Vault not consulted. No vault action is proposed; the implementation is fully
covered by repository-owned Story, ADR and source evidence.

## Blockers / Risks

- La Story reste en attente d'acceptance humaine.

## Independent Review

Final independent review: **PASS**. No findings remain. The reviewer confirmed
the concurrency, terminalization, generation, stop, idempotence, bearer-renewal
and Java authority boundaries.

## Readiness

READY_FOR_HUMAN_ACCEPTANCE

Commit created: NO
Push performed: NO
