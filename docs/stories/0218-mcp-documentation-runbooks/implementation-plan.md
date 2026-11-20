# Story 0218 - Implementation Plan

## Statut

PLAN APPROVED - implementation authorized by the human project owner.

Ce plan est derive du Repository Analysis approuve de Story 0218. Il couvre
uniquement la documentation operateur canonique. L'implementation documentaire
a ete autorisee par approbation humaine explicite ; l'acceptance finale reste
une gate distincte.

## Gates prealables

Repository Analysis approuve par le proprietaire humain.

Les contraintes suivantes sont confirmees pour le plan :

1. `TOOLS.md` reste l'unique documentation operateur canonique de cette Story.
2. Aucun nouveau repertoire `docs/runbooks` n'est cree.
3. Aucun fichier Python, Java, Core, Keycloak ou provider n'est modifie.
4. La documentation de `429` doit refleter Story 0217 : une seule tentative
   pour une operation rejouable, plafond `Retry-After` de 60 secondes et
   fallback borne d'une seconde.
5. Toute recuperation manuelle d'une mutation ambigue reste interdite.

## Phase 1 - Reconciliation du contrat existant

1. Relire les sections runtime, diagnostics, renouvellement et delegation de
   `TOOLS.md`.
2. Remplacer la formulation obsolette indiquant que `429` est terminal et
   reserve a une Story future.
3. Conserver les commandes et chemins deja valides.
4. Eviter de dupliquer les contrats detailles deja documentes ailleurs ;
   utiliser des references vers ADR-081 et les Stories pertinentes lorsque
   cela suffit.

## Phase 2 - Ajouter le runbook de diagnostic initial

Documenter une sequence read-only reproductible :

1. Executer `python3 tools/devlog_mcp/status.py status --json`.
2. Executer `python3 tools/devlog_mcp/status.py doctor --json`.
3. Relever uniquement `instanceId`, `operationId`, `generation`, `status`,
   `reasonCode`, `code`, `category`, `layer`, `retryable`, `action`,
   `attempt` et `maxAttempts` lorsque presents.
4. Verifier les journaux rediges de l'instance sans copier de secret ou
   payload.
5. Escalader avec la correlation et l'heure UTC si la recuperation sure ne
   resout pas l'incident.

## Phase 3 - Ajouter la matrice operateur

Ajouter dans `TOOLS.md` une matrice concise couvrant au minimum :

| Signal | Interpretation | Action sure | Action interdite |
|---|---|---|---|
| `MCP_REAUTH_REQUIRED` | renouvellement ou auth transport impossible | verifier `doctor`, redemarrer le runtime | rejouer une mutation ambigue |
| `CORE_AUTHENTICATION_FAILED` | Core a refuse l'authentification | verifier le contexte d'acces Core | renouveler le bearer par reflexe |
| `CORE_AUTHORIZATION_FAILED` | Core a refuse l'autorisation | verifier projet/delegation | contourner la delegation |
| `DELEGATION_FAILED` | delegation absente, expiree ou revoquee | obtenir une nouvelle delegation humaine | reutiliser un token expire |
| `RATE_LIMITED` | limite de debit Core | laisser le retry borne puis attendre avant action manuelle | lancer des retries concurrents |
| `TRANSIENT_INFRASTRUCTURE` | timeout ou indisponibilite transitoire | verifier `doctor` et l'etat du service | rejouer une mutation ambigue |
| `MCP_PROCESS_FAILED` | child MCP termine ou inaccessible | collecter status/doctor puis redemarrer | tuer un processus non possede |
| `MCP_INSTANCE_STOPPING` | interruption attendue pendant arret | attendre ou relancer une session | declarer un incident sans autre signal |

La matrice finale doit rester coherente avec les codes effectivement exposes
par `runtime.py` et `status.py`.

## Phase 4 - Documenter `429` et l'idempotence

1. Expliquer que le runtime Python est l'autorite unique du retry.
2. Documenter la tentative unique pour les operations explicitement
   rejouables.
3. Documenter `Retry-After` valide, absent, invalide, expire et superieur au
   plafond.
4. Preciser que l'attente est plafonnee a 60 secondes et interruptible.
5. Preciser que les operations non idempotentes, mutations ambigues et
   operations sans garantie Core de deduplication ne sont pas rejouees.
6. Distinguer l'echec terminal d'une recuperation manuelle autorisee pour une
   nouvelle operation explicitement validee.

## Phase 5 - Ajouter la checklist d'incident

La checklist doit demander uniquement :

- date et heure UTC ;
- instance et session si disponibles ;
- operation et generation si disponibles ;
- code, categorie, couche, statut et action recommandee ;
- nombre de tentatives observe ;
- resultat de `status` et `doctor` sans secrets ;
- action deja tentee et prochain escalade.

Elle doit interdire explicitement la collecte de bearer, secret client, token
de delegation, payload JSON-RPC, prompt, repository content, provider response
et corps backend.

## Phase 6 - Validation documentaire

1. Rechercher les contradictions restantes sur `429`, retry, idempotence,
   renouvellement et delegation.
2. Verifier tous les chemins et commandes contre le repository courant.
3. Verifier les liens vers ADR-081 et Stories 0211 a 0218.
4. Scanner le diff documentaire pour les secrets et tokens.
5. Confirmer que seuls `TOOLS.md` et les artefacts de Story 0218 sont modifies
   par cette Story.
6. Executer `git diff --check`.

## Fichiers autorises

- `TOOLS.md` ;
- `docs/stories/0218-mcp-documentation-runbooks/story.md` ;
- `docs/stories/0218-mcp-documentation-runbooks/repository-analysis.md` ;
- `docs/stories/0218-mcp-documentation-runbooks/implementation-plan.md` ;
- futur `implementation-report.md` de la Story.

## Fichiers exclus

- `tools/devlog_mcp/runtime.py` ;
- `tools/devlog_mcp/status.py` ;
- serveur MCP Java et tests ;
- backend Core, Keycloak, providers et credentials ;
- fichiers de Story 0216 et 0217, hors reconciliation de references si
  strictement necessaire.

## Risques

1. Une documentation obsolete pourrait conseiller un retry interdit ou masquer
   la distinction Core/transport.
2. Une procedure trop concrete pourrait encourager la collecte de secrets ou
   le rejeu d'une mutation ambigue.
3. Une matrice documentaire qui invente des codes non exposes pourrait induire
   l'operateur en erreur.
4. Une modification accidentelle de `TOOLS.md` hors perimetre pourrait
   melanger cette Story avec les changements 0216/0217 non commis.

## Criteres de sortie du plan

- Chaque code documente existe dans les catalogues ou est explicitement marque
  comme limite connue.
- La contradiction `429` de `TOOLS.md` est resolue.
- Les actions sures et interdites sont explicites.
- Les secrets et payloads sont exclus de la checklist.
- Aucun fichier de code n'est modifie.
- `git diff --check` passe.

## Autorisation

Implementation documentaire autorisee. Commit, push, merge et acceptance
humaine restent des gates distinctes.
