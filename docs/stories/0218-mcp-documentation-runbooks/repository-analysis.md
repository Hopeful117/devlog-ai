# Repository Analysis - Story 0218

## Scope

Cette Story finalise l'epic de resilience MCP par une reconciliation
documentaire. La modification autorisee est documentaire et cible `TOOLS.md`,
qui est deja la source operateur canonique du repository. Aucun code runtime,
retry, budget, idempotence, identite ou delegation ne doit etre modifie.

## Repository State

- Documentation operateur canonique : `TOOLS.md`.
- Runtime Python : `tools/devlog_mcp/runtime.py`.
- Diagnostic read-only : `tools/devlog_mcp/status.py`.
- Epic : `docs/epics/mcp-error-resilience-policy/epic.md`.
- Stories gouvernantes : 0211 a 0217, avec dependances 0172 et 0174.
- Le worktree contient deja les implementations et artefacts non commis des
  Stories 0216 et 0217. Cette Story ne doit pas reformatter ni modifier leurs
  fichiers de code ou de tests.

## Current Documentation

`TOOLS.md` documente deja :

- le mapping de projet et les frontieres d'identite ;
- la delegation humaine et sa revocation ;
- les commandes `status --json` et `doctor --json` ;
- les diagnostics bornes et la redaction ;
- le renouvellement bearer et le retry token endpoint ;
- le rejeu limite par idempotence.

Une contradiction reste presente : la section runtime indique que `429` est
terminal et reserve a la Story d'idempotence, alors que Story 0217 implemente
desormais un retry unique borne pour les operations rejouables, avec
`Retry-After` plafonne a 60 secondes.

## Documentation Gaps

La documentation ne fournit pas encore une procedure operateur complete pour :

1. distinguer `RATE_LIMITED` d'une erreur d'authentification ou de delegation ;
2. comprendre la tentative automatique unique et le plafond de 60 secondes ;
3. savoir quoi faire lorsque `Retry-After` est absent, invalide, expire ou
   depasse le plafond ;
4. recuperer apres `MCP_REAUTH_REQUIRED`, `MCP_PROCESS_FAILED`, une erreur Core
   ou une delegation revoquee ;
5. eviter le rejeu manuel d'une mutation ou d'un resultat ambigu ;
6. collecter les identifiants de correlation sans collecter de secrets ;
7. distinguer un arret attendu `MCP_INSTANCE_STOPPING` d'un incident.

## Source Contracts to Reconcile

- `TOOLS.md` lignes runtime et diagnostics.
- `tools/devlog_mcp/status.py` pour les champs effectivement projetes par
  `status --json` et `doctor --json`.
- `tools/devlog_mcp/runtime.py` pour les codes et actions allowlistes.
- `ADR-081` pour la taxonomie, le fail-closed et l'idempotence.
- Story 0212 pour le renouvellement bearer et le retry token endpoint.
- Story 0213 pour la separation Core/transport et l'absence de retry Java.
- Story 0214 pour les operations rejouables et les resultats ambigus.
- Story 0215 pour le contrat de diagnostics.
- Story 0216 pour les scenarios de validation provider-free.
- Story 0217 pour `RATE_LIMITED`, `Retry-After`, le budget et le plafond.

## Proposed Documentation Structure

Dans `TOOLS.md`, ajouter ou reorganiser une section operateur couvrant :

1. diagnostic initial read-only ;
2. matrice signal, interpretation, action sure, action interdite et escalade ;
3. recovery bearer et runtime ;
4. recovery Core et delegation ;
5. rate limiting et `Retry-After` ;
6. idempotence et rejeu manuel ;
7. checklist d'incident et collecte non sensible.

La structure doit reutiliser les contrats existants, pas introduire une seconde
taxonomie ni un nouveau format de telemetry.

## Security Constraints

La documentation ne doit contenir aucun credential reel ni exemple ressemblant
a un token utilisable. Elle doit interdire explicitement la collecte de :

- bearer transport ;
- secret client ;
- token de delegation ;
- payload JSON-RPC ;
- prompt ou contenu repository sensible ;
- reponse provider ou corps backend brut.

Les procedures doivent rester read-only par defaut. Toute action manuelle doit
etre identifiee comme telle et ne doit pas conseiller le rejeu d'une mutation
ambigue.

## Affected Files

Probable files :

- `TOOLS.md` ;
- `docs/stories/0218-mcp-documentation-runbooks/story.md` ;
- le futur `implementation-report.md` de la Story.

Fichiers exclus :

- `tools/devlog_mcp/runtime.py` ;
- `tools/devlog_mcp/status.py` ;
- serveur Java MCP ;
- backend Core, Keycloak et providers ;
- Stories 0216 et 0217 hors references strictement necessaires.

## Validation Strategy

- Verifier chaque commande et chemin contre le repository actuel.
- Rechercher les formulations obsoletes ou contradictoires, notamment `429`
  terminal/reserve a une Story future.
- Verifier les liens Markdown relatifs vers ADR et Stories.
- Rechercher les secrets, tokens et valeurs sensibles dans le diff documentaire.
- Executer `git diff --check`.
- Confirmer qu'aucun fichier de code ou test runtime n'est modifie.

## DevLog Context

`DEVLOG_CONTEXT_ERROR`: aucune capacite DevLog MCP exploitable n'est exposee
dans cette session. Cette analyse repose sur les artefacts canoniques du
repository et l'etat courant du code ; le repository reste l'autorite pour
l'etat physique actuel.

## Gate

Repository Analysis complete. L'Implementation Plan ne doit pas etre produit
avant approbation humaine explicite de cette analyse.
