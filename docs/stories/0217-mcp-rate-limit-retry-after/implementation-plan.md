# Story 0217 - Implementation Plan

## Statut

PLAN APPROVED - implementation authorized by the human project owner.

Ce plan est derive du Repository Analysis approuve de Story 0217. Les choix
proposes ont ete approuves par le proprietaire humain. L'acceptation finale
reste une gate distincte.

## Gates prealables

Repository Analysis approuve par le proprietaire humain.

Les decisions suivantes doivent etre confirmees avant implementation :

1. Une seule tentative de retry d'operation apres `429`.
2. Un plafond de delai `Retry-After` de 60 secondes pour le premier increment.
3. Une valeur absente ou invalide utilise le backoff local borne de 1 seconde ;
   une valeur valide mais superieure a 60 secondes est bornee a 60 secondes.
4. Les operations `RETRY_SAFE_TOOLS` existantes et les operations avec une cle
   d'idempotence explicite restent les seules operations rejouables.
5. Le serveur Java valide et propage uniquement `retryAfterSeconds`; il ne
   lance aucun retry.
6. La metadata `retryAfterSeconds` n'est jamais une valeur HTTP brute et ne
   contient ni header, ni message backend, ni payload.

## Design propose

### Contrat Java vers runtime

Pour un `429` Core portant un header `Retry-After` :

1. Lire la valeur du header dans `CoreCallPolicy`.
2. Accepter les secondes entieres positives et les dates HTTP parseables.
3. Convertir vers une duree en secondes entieres.
4. Borner la valeur a 60 secondes.
5. Exposer uniquement `retryAfterSeconds` dans les donnees MCP lorsque la
   valeur est valide et positive.
6. Omettre la metadata pour une valeur absente, invalide ou deja expiree.

Le mapping reste `RATE_LIMITED`. La presence de metadata ne rend pas toutes
les operations retryables et ne declenche rien dans Java.

### Decision de retry Python

1. Detecter un message JSON-RPC `RATE_LIMITED` provenant de Core.
2. Refuser le retry si l'operation n'est pas dans l'allowlist existante ou ne
   porte pas la cle d'idempotence deja exigee.
3. Refuser le retry si le budget d'operation est deja consomme.
4. Calculer le delai a partir de `retryAfterSeconds` ; utiliser le fallback
   local borne si la metadata est absente ou inutilisable.
5. Attendre via `stop_requested.wait(timeout=...)`, jamais via une attente non
   interruptible.
6. Rejouer le payload original avec le meme `operationId`, la meme generation
   logique et la meme cle d'idempotence.
7. Emettre un diagnostic borne de retry ou terminaliser l'operation sans
   exposer la metadata brute.

Le traitement `429` restera distinct de `retry_authentication` et ne
declenchera aucun renouvellement de bearer.

## Phases d'implementation

### Phase 1 - Contrat et bornage Java

1. Ajouter une constante de plafond partagee au point de mapping minimal
   retenu.
2. Extraire et valider `Retry-After` sans conserver le header brut.
3. Ajouter `retryAfterSeconds` uniquement pour les `429` Core valides.
4. Garder `retryable: false` au niveau du mapping Java, car le retry effectif
   est decide par le runtime selon l'operation.

### Phase 2 - Retry `429` dans le runtime

1. Ajouter la lecture defensive de `retryAfterSeconds` dans le message
   JSON-RPC.
2. Ajouter un budget d'une tentative pour le retry `429`, distinct du retry de
   renouvellement bearer et du retry token endpoint.
3. Reutiliser l'allowlist `RETRY_SAFE_TOOLS` et la verification de cle
   d'idempotence existante.
4. Attendre avec la barriere d'arret existante.
5. Rejouer uniquement le payload conserve de l'operation.
6. Preserver les protections de concurrence, generation, transition terminale
   et redaction deja existantes.

### Phase 3 - Diagnostics et documentation

1. Ajouter des diagnostics bornes pour tentative, budget, delai applique et
   action recommandee.
2. Ne jamais recopier le header ou le message backend dans les journaux.
3. Mettre a jour `TOOLS.md` uniquement si le contrat observable pour
   l'operateur change.

### Phase 4 - Tests

#### Java MCP

- `Retry-After` en secondes positives ;
- date HTTP future, date expiree et date invalide ;
- header absent, vide, negatif, non numerique et excessif ;
- plafond applique ;
- absence du header brut dans les donnees MCP ;
- absence de retry Java ;
- non-regression des autres statuts et de la redaction.

#### Python runtime

- `RATE_LIMITED` avec metadata valide ;
- metadata absente, invalide et excessive ;
- fallback borne ;
- operation read-only rejouable une fois ;
- operation `devlog_story_agent` avec cle rejouable une fois ;
- mutation sans cle rejouee zero fois ;
- budget consomme ;
- preservation de l'`operationId` et du payload ;
- `429` Core sans renouvellement transport ;
- concurrence sur une meme operation ;
- arret pendant l'attente ;
- redaction des diagnostics.

## Fichiers probables

- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/service/CoreCallPolicy.java` ;
- `mcp-server/src/test/java/hopefull117/devlogai_mcp/mcp_server/service/CoreCallPolicyTest.java` ;
- `tools/devlog_mcp/runtime.py` ;
- `tools/devlog_mcp/tests/test_runtime.py` ;
- `TOOLS.md` seulement si necessaire apres reconciliation documentaire.

## Fichiers exclus

- Keycloak et configuration OAuth ;
- Core authorization et delegation ;
- AI Engine et credentials provider ;
- tout scheduler durable, queue, broker, outbox ou circuit breaker ;
- tout retry automatique dans les clients Java/MCP hors propagation de
  metadata ;
- modifications des Stories 0215 et 0216, sauf references documentaires
  strictement necessaires.

## Ordre de verification

1. Tests unitaires Java du parsing et du bornage `Retry-After`.
2. Tests unitaires Python du calcul de delai et du budget.
3. Tests Python d'idempotence, concurrence, generation et arret.
4. Tests Java de non-retry et de non-divulgation.
5. Suite runtime Python complete.
6. Suite MCP Java complete.
7. `git diff --check`.
8. Revue du diff pour exclure les changements existants de 0216.

## Risques residuels

1. La metadata JSON-RPC devient une extension de contrat entre Java et Python.
2. Un plafond local de 60 secondes est une decision de produit/runtime et doit
   etre confirme avant implementation.
3. La date HTTP peut dependre de l'horloge locale ; un resultat deja expire ne
   doit pas produire une attente negative.
4. Le retry d'une operation avec cle doit rester borne par la garantie Core de
   deduplication et ne peut pas etre etendu aux mutations sans cette garantie.

## Autorisation

Implementation autorisee par approbation humaine explicite. Commit, push, merge
et acceptance humaine restent des gates distinctes.
