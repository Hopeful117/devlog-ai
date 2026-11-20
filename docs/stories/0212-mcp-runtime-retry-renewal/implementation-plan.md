# Story 0212 - Implementation Plan

## Statut

PLAN APPROVED - implementation authorized by the human project owner.

Ce plan est derive du Repository Analysis de Story 0212. Les decisions ouvertes
ont ete confirmees par le proprietaire humain. L'acceptation finale reste une
gate distincte.

## Gates prealables

Les decisions confirmees avant implementation sont :

1. Une erreur sans `data.source` reste compatible uniquement pour le couple
   historique code/message exact.
2. Une seule reacquisition supplementaire du token est autorisee par cycle de
   renouvellement.
3. Timeout, `502`, `503` et `504` sont transitoires. `400`, `401`, `403`, `404`
   et `409` sont terminaux. `429` est reporte a la Story d'idempotence.
4. Le renouvellement reste declenche par une nouvelle requete, sans
   renouvellement proactif.
5. Le runtime Python reste l'autorite unique du retry ; aucun retry automatique
   n'est ajoute dans les adaptateurs Java MCP.

## Decisions proposees

Ces propositions sont soumises a validation et ne sont pas encore des
decisions de projet :

- conserver la compatibilite sans `data.source` uniquement pour le code/message
  historique, avec une classification degradee et sans elargir l'allowlist ;
- autoriser au plus une reacquisition supplementaire du token par cycle de
  renouvellement ;
- traiter timeout, `502`, `503` et `504` comme transitoires ; traiter `400`,
  `401`, `403`, `404` et `409` comme terminaux ; reporter `429` a la decision
  explicite de la Story d'idempotence si `Retry-After` n'est pas deja contractualise ;
- conserver le renouvellement declenche par une nouvelle requete ;
- ne pas ajouter de retry automatique dans les adaptateurs Java MCP.

## Phase 1 - Formaliser la politique locale

1. Introduire une classification interne minimale pour les resultats du token
   endpoint sans exposer de secret.
2. Separer le budget de reacquisition du token du budget de retry d'operation.
3. Definir le backoff borne et le cooldown sous forme de configuration validee.
4. Conserver l'autorite de decision dans `RuntimeSession`.

## Phase 2 - Durcir le renouvellement

1. Adapter `acquire_token` ou son appelant pour permettre uniquement les retries
   transitoires autorises.
2. Ne jamais rejouer le payload MCP pendant un retry d'acquisition echoue.
3. Conserver le remplacement du seul child de l'instance courante.
4. Preserver le replay limite du handshake `initialize` et de la notification
   `notifications/initialized`.
5. Conserver l'isolation par generation et l'operation id stable.
6. Conserver la barriere conservative lorsque plusieurs operations sont en
   vol.

## Phase 3 - Classification et diagnostics

1. Rendre explicite la decision lorsqu'une erreur comporte `source=CORE`,
   `source=TRANSPORT` ou une autre provenance.
2. Produire un diagnostic borne pour : expiration transport, renouvellement
   refuse, token endpoint indisponible, Core authentication, Core authorization,
   delegation et provider.
3. Etendre `status.py` uniquement aux metadonnees necessaires au diagnostic
   read-only.
4. Mettre a jour `TOOLS.md` avec les budgets, les limites et la procedure de
   recuperation.

## Phase 4 - Tests

### Python

- classification avec provenance transport, Core, delegation, provider et
  provenance absente ;
- timeout et `5xx` du token endpoint ;
- erreur client terminale ;
- budget de reacquisition et backoff ;
- distinction entre reacquisition et rejeu d'operation ;
- un seul retry d'operation autorisee ;
- non-rejeu d'une operation non idempotente ou ambigue ;
- operations concurrentes, stop et ancienne generation ;
- ownership du child et redaction.

### Java MCP

- non-regression du mapping structurel des `401` Core ;
- absence de retry implicite dans l'adaptateur ;
- non-divulgation des erreurs `403`, `404`, `409` et payloads.

### Integration controlee

- doubles locaux pour le token endpoint et le child MCP ;
- expiration puis renouvellement reussi ;
- renouvellement refuse et terminalisation ;
- `429` selon la decision retenue ;
- deux operations simultanees pendant un renouvellement ;
- arret pendant renouvellement ;
- reponse d'une generation ancienne.

Aucun credential reel ou test live Keycloak n'est requis pour le gate local.

## Fichiers probables

- `tools/devlog_mcp/runtime.py` ;
- `tools/devlog_mcp/status.py` ;
- `tools/devlog_mcp/tests/test_runtime.py` ;
- tests MCP Java de `ResourceSupport` et des outils ;
- `TOOLS.md` ;
- documentation de Story 0212.

## Fichiers exclus par defaut

- configuration et deploiement Keycloak ;
- Core authorization et delegation ;
- AI Engine credentials et providers ;
- contrats de taches et bindings ;
- tout nouveau mecanisme de retry Java non decide.

## Ordre de verification

1. Tests unitaires des classifieurs et de l'acquisition du token.
2. Tests du renouvellement et du budget de retry.
3. Tests de concurrence et de generations.
4. Tests de redaction et d'integrite STDIO.
5. Tests MCP Java cibles.
6. Suite Python runtime complete.
7. Suite MCP complete.
8. `git diff --check` et revue du diff complet.

## Risques

1. Un retry d'acquisition mal separe pourrait masquer une expiration ou
   augmenter le nombre de requetes Keycloak sans borne.
2. Une compatibilite trop large avec le contrat historique pourrait recreer la
   collision Core/transport corrigee par Story 0211.
3. Le remplacement du child pendant des operations en vol pourrait produire un
   resultat ambigu si les generations ne sont pas verifiees.
4. Un ajout de retry dans Java et Python pourrait doubler le budget global.
5. Une extension de `status.py` pourrait exposer involontairement des details
   sensibles si elle reutilise un message backend non redige.

## Criteres de sortie du plan

- les cinq decisions ouvertes sont validees ou explicitement reportees ;
- les budgets de retry sont distincts et numeriquement definis ;
- l'autorite unique du retry est identifiee ;
- chaque chemin terminal et chaque scenario concurrent possede un test ;
- aucune modification de production n'est faite avant autorisation humaine.

## Autorisation

Implementation non autorisee. Commit, push, merge et acceptance humaine restent
des gates distinctes.
