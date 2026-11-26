# Story 0246 - Analyse de dépôt

## Statut et base examinée

Analyse approuvée par le propriétaire le 26 novembre 2026.
Base locale détachée : `7ecc1680`. Le code
sous `tools/devlog_mcp/` est identique à celui de `origin/main` au moment de
la vérification (`217ae1df`). Le numéro 0246 suit 0245, dernier numéro trouvé
dans le catalogue Git et sur `main` distant. L'ADR-084 local accepté est
hors périmètre et n'a pas été modifié.

**DEVLOG_CONTEXT_ERROR** : aucun Story Agent ni outil direct DevLog exposé
dans cette session. La découverte a été tentée ; aucune requête n'a pu être
soumise et aucun job n'est en attente. **DEVLOG_LIFECYCLE_ERROR** :
l'enregistrement distant de la nouvelle Story n'a pas pu être effectué ;
aucun UUID DevLog n'est connu. L'analyse s'appuie sur les journaux, les
artefacts canoniques du dépôt et le code courant. Vault non consulté :
l'incident est circonscrit au cycle de vie du lanceur local.

## Éléments établis

Le 25 novembre, heure locale :

| Heure | Trace observée |
|---|---|
| 23:15 | Renouvellement réussi et génération suivante prête. Des appels DevLog répondent ensuite. |
| 23:17:13 | Le Story Agent répond. Le rejet de grounding `GROUNDING_REQUESTED_FILE_SCOPE` concerne le résultat, pas le transport MCP. |
| 23:20:20 | `TOKEN_RENEWAL_STARTED`, puis `MCP_INSTANCE_STOPPED` avec le motif `TOKEN_RENEWAL`, puis `MCP_PROCESS_EXITED` avec le code `143`. Aucun démarrage de remplaçant dans le journal de cette instance ; OpenCode signale la fermeture. |
| Après 23:26 | D'autres instances répondent au Story Agent. Aucune panne générale du backend n'est établie. |

Sources : `~/.local/state/devlog/mcp/bd98dc6831765fc2bdac66fe/events.jsonl` et
`~/.local/share/opencode/log/opencode.log`. Aucun rejet du point d'émission
de jeton n'est enregistré dans la séquence en cause. Les journaux ne
montrent pas directement l'ordonnancement des threads.

## Chemin concerné et hypothèse

Dans [`tools/devlog_mcp/runtime.py`](../../../tools/devlog_mcp/runtime.py) :

- `renew_if_needed` (vers L1151) marque le renouvellement en cours sous
  verrou, applique les protections d'opérations en vol et libère les
  attentes via `renewal_condition` à la fin du traitement.
- `_renew` (vers L1177) acquiert le nouveau bearer, arrête l'ancien enfant,
  démarre le remplaçant et restaure le handshake. `stop_child` (vers L795)
  ne marque volontairement pas un arrêt définitif quand le motif est
  `TOKEN_RENEWAL`.
- `main` (vers L1424) teste le processus enfant et son `poll()` dans la
  condition de supervision, sans consulter l'état de renouvellement.
  Une fois hors boucle, il attend l'enfant courant et enregistre sa sortie
  avant de terminer le lanceur.

**Hypothèse forte, non reproduite** : `main` observe la fin de l'ancien
enfant alors que le thread de renouvellement ne l'a pas encore remplacé.
Il quitte alors la supervision et peut publier son code de sortie comme
celui du lanceur. Le code permet cet entrelacement et les traces sont
cohérentes avec lui ; elles ne suffisent pas à prouver qu'il s'est produit.
Un test de `_renew` seul ne ferait pas intervenir l'observateur qui prend
la décision de sortie.

## Contraintes et points de validation

- Story 0172 : le lanceur conserve la vérification de l'identité du parent,
  l'ownership sur l'enfant et la séparation des diagnostics et du stdout.
- Story 0174 : un renouvellement réussi conserve la session logique ;
  le remplaçant ne devient pas prêt avant son handshake. Un échec ne doit
  pas produire de disponibilité fictive.
- Story 0212 et ADR-081 : ne pas élargir les règles d'éligibilité au retry
  ou au rejeu ; conserver le traitement borné et sûr des issues ambiguës.
- Distinguer l'entre-deux normal, un arrêt définitif, une sortie
  inattendue et un échec de remplacement. Examiner notamment l'échec
  avant et après l'arrêt de l'ancien enfant, ainsi que la disparition du
  parent pendant cette transition.
- Vérifier la propriété côté superviseur avec un ordre imposé par le
  test ; une temporisation qui réduit la fréquence du défaut n'est pas
  une preuve de correction. Le choix du mécanisme de synchronisation
  relève du plan après approbation de cette analyse.

## Baseline et limite de couverture

`PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/devlog_mcp/tests -q`
→ **69 tests réussis**. Aucun service réel sollicité par la validation.
`test_shutdown_during_renewal_does_not_start_replacement_child` vérifie
que `_renew` ne démarre rien si l'arrêt est déjà demandé. Le test
`test_renewal_replays_initialized_notification_after_ready` couvre la
reprise du handshake. Aucun ne force la boucle de `main` à voir l'ancien
enfant terminé au milieu du remplacement. La baseline ne reproduit
donc pas la course et ne démontre pas le futur correctif.

## Décision attendue

Approuver ou corriger cette analyse et le périmètre de la Story. Le plan
proposera ensuite le test de reproduction et la plus petite correction
préservant les invariants. Aucun code, test de reproduction ou
`implementation-plan.md` n'a été créé à cette étape.
