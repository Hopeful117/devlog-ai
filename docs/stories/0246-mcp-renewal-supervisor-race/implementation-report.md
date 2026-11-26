# Story 0246 - Rapport d'implémentation

## État du dépôt et gouvernance

- HEAD détaché : `7ecc16806e3dea3c412f98139952ed0cfabb6f54`.
- [Story](story.md), [analyse](repository-analysis.md) et
  [plan](implementation-plan.md) approuvés. ADR-081 et Stories 0172, 0174
  et 0212 maintenus. ADR-084 local préexistant inchangé.
- **Préflight DevLog** : indisponible à l'implémentation initiale. Pour la
  correction de revue, le Story Agent a répondu `COMPLETED` (tâche
  `86c2dd5f-3ef3-4edc-8d9d-c480563fd96b`, révision `217ae1df`).
  Contexte partiellement frais (`STALE`) et projection tronquée : il
  confirme le périmètre du lanceur et renvoie à l'ADR-081 pour le retry,
  mais n'établit pas les transitions d'arrêt. Le code local et les ADR
  restent l'autorité. Aucun enregistrement de Story ou démarrage de cycle
  distant ; le préflight ne constitue pas une acceptation.

## Changements

- `tools/devlog_mcp/runtime.py` : extraction de la supervision appelée
  par `main`. Un verrou de cycle de vie distinct du verrou de session
  protège les décisions rapides : observation de l'enfant courant,
  réservation de renouvellement, demande d'arrêt et publication de
  l'enfant. Aucune E/S bloquante ni attente de processus sous ce verrou.
  L'arrêt est enregistré avant le nettoyage. Un enfant dont le démarrage
  était déjà en cours mais dont la publication est refusée est arrêté
  via son propre `Popen`, sans relayer de handshake. `main()` attend la
  résolution de la réservation de création avant d'enregistrer la sortie.
  Si le nettoyage par le thread de création échoue, le `Popen` reste
  référencé pour la finalisation. La terminaison de l'enfant possède son
  propre verrou et envoie le signal avant toute attente ; elle ne ferme
  pas un stdin tamponné avant le signal. Les écritures du handshake et
  du relais stdout n'occupent plus le verrou de session. Le relais
  sérialise les trames et ne démarre aucune écriture d'une génération
  obsolète à la vérification d'admission. Aucun renouvellement n'est
  accepté après la décision terminale.
- `tools/devlog_mcp/tests/test_runtime.py` : vingt-cinq tests supplémentaires
  sur la supervision et le cycle de vie, dont des scénarios à deux
  threads avec barrières et faux processus.
- Documentation : statut et rapport de la Story. Pas d'autre
  réconciliation requise : la correction restaure le comportement de
  renouvellement déjà documenté, sans modifier le contrat, les codes de
  diagnostic, l'installation ni les règles de rejeu. Vault non consulté,
  aucune action proposée.

## Validation et revue

1. Avec la boucle d'origine extraite sans changement de logique, le test
   ciblé retourne l'ancien enfant (code simulé 143) avant que le test puisse
   publier le remplaçant. **Rouge attendu**, sans Java ni service réel.
2. Après la première correction : **6 tests de supervision réussis**.
   Cas couverts :
   remplacement, arrêt demandé, changement d'identité du parent, sortie
   inattendue, fin de renouvellement sans remplaçant et échec sur le chemin
   stdio (acquisition, démarrage ou handshake simulé).
3. La revue indépendante de la première version a demandé des changements :
   dépendance du superviseur à stdout, tests sans concurrence effective et
   renouvellement accepté après décision terminale. Les régressions ajoutées
   vérifient la sortie avec stdout bloqué, la perte du parent face au vrai
   chemin de renouvellement, et la reprise du handshake dans ce même chemin.
   Elles vérifient aussi le refus de publier un enfant lancé concurremment
   et l'absence d'acquisition après la décision terminale.
4. Après correction de revue : **11 tests ciblés réussis** (filtres
   `supervisor`, `parent_loss_prevents`, `terminal_decision`,
   `spawned_concurrently`). `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest
   discover -s tools/devlog_mcp/tests -q` : **80 réussis**, contre 69 sur
   la baseline. `git diff --check` : aucun problème d'espacement.
5. La seconde revue a identifié deux lacunes : finalisation avant le
   nettoyage du remplaçant non publié et arrêt bloqué avec un enfant
   vivant et stdout saturé. Après correction, trois régressions réussissent
   (filtres `main_waits`, `parent_loss_stops`, `stdout_descriptor`) :
   appel réel de `main()` avec faux `Popen`, EOF concurrent et preuve que
   le remplaçant est terminé et attendu avant `MCP_PROCESS_EXITED` ; arrêt et
   attente de l'enfant vivant alors que l'écriture stdout est encore
   bloquée ; intégrité des octets avec écritures partielles sur un pipe
   local. Suite totale : **83 réussis**. `git diff --check` : OK.
6. Revue du diff local : pas de changement du contrat MCP, des charges
   utiles, de l'autorisation ou des critères de rejeu. Une trame stdout
   déjà admise peut finir si le client recommence à lire ; aucune trame
   suivante ne la dépasse. La finalisation attend un `Popen` déjà engagé :
   la bibliothèque standard ne fournit pas d'annulation bornée de la
   création du processus elle-même. Une fois l'enfant obtenu, les
   attentes de terminaison restent bornées (`terminate`, puis `kill`
   si nécessaire). Aucun succès de nettoyage n'est journalisé si cette
   attente échoue.
7. Troisième revue : SIGINT pendant la finalisation, échéance incomplète
   du handshake, dernière réponse perdue et ordre des événements de
   démarrage/arrêt. La supervision garde la responsabilité du nettoyage
   lors d'un SIGINT. Le rejeu du handshake a une seule échéance de
   dix secondes pour les deux écritures et la réponse ; `ready` ne
   suit que la restauration terminée. Sur sortie naturelle, le relais
   dispose d'une seconde pour vider les dernières réponses ; le succès
   de la réponse ordinaire est journalisé après écriture. La finalisation
   enregistre les interruptions restantes avant la sortie. L'arrêt
   explicite attend la fin de la publication et de ses métadonnées,
   pas les E/S du handshake. **5 nouvelles régressions ciblées réussies**,
   dont les deux phases de handshake sur pipe saturé et un SIGINT livré
   au processus de test. Suite totale : **88 réussis** ; `git diff --check` :
   OK. Nouvelle revue indépendante requise.
8. Quatrième revue : une réponse livrée pouvait rester momentanément en
   vol, un échec de handshake pouvait réveiller un appelant vers un
   remplaçant inutilisable, un chemin d'échec stdin pouvait être dépassé
   par la sortie terminale, et `MCP_INSTANCE_READY` pouvait suivre
   `MCP_INSTANCE_STOPPED`. La requête est retirée des opérations en vol
   avant l'écriture stdout, mais l'opération reste interruptible jusqu'à
   la journalisation de la livraison. L'échec de remplacement est publié
   avant le réveil des appelants et tout appelant réveillé échoue
   fermé. Les événements d'opération (démarrage, succès, échec,
   interruption) sont mis en file sous le verrou de session puis émis
   sous un verrou de terminalisation que la finalisation vide, garantissant
   l'ordre retrait-émission-sortie. La publication de disponibilité et
   l'arrêt de l'enfant partagent un verrou de cycle de vie qui ordonne
   `MCP_INSTANCE_READY` avant `MCP_INSTANCE_STOPPED`. **4 nouvelles
   régressions ciblées réussies** ; suite totale : **92 réussis** en
   **12 exécutions consécutives**, `git diff --check` : OK. Un test
   antérieur ré-entrait dans le verrou de terminalisation et bloquait la
   suite ; son point d'injection a été déplacé hors du verrou.
 9. Cinquième revue : les quatre constats sont approuvés. Deux constats
   MINOR restent : `publish_ready` acquérait le verrou d'arrêt de l'enfant
   en tenant le verrou de session, et l'écriture du handshake ne partageait
   pas le verrou d'écriture stdin du relais. La disponibilité est décidée
   sous le verrou de session puis publiée après relâchement, comme les
   écritures de handshake. Le handshake prend désormais le même verrou
   d'écriture stdin que le relais, ce qui exclut toute trame entrelacée.
   **2 nouvelles régressions ciblées réussies** (verrou de session libre
   pendant l'attente d'arrêt, écriture handshake sérialisée) ; suite
    totale : **94 réussis** en **10 exécutions consécutives**,
    `git diff --check` : OK.
10. Sixième revue indépendante : **APPROVED**, aucun nouveau finding.
    Elle confirme que `publish_ready` relâche le verrou de session avant
    `child_stop_lock`, avec revalidation de l'arrêt et de la génération,
    et que le handshake et le relais ordinaire partagent
    `stdin_write_lock`. Aucun deadlock ni régression manifeste identifié.
    Cette revue n'a pas réexécuté les tests ; son verdict s'appuie sur le
    code, les tests lus et les résultats locaux rapportés ci-dessus.

Les résultats établissent la correction de l'entrelacement reproduit en
test, pas le rétablissement de la connexion de la session OpenCode actuelle.
Pas de test réel Keycloak/Java/OpenCode, de validation des autres modules,
de déploiement, de redémarrage, de commit ni de push.

**READINESS : READY_FOR_HUMAN_REVIEW.** La sixième revue indépendante
approuve la version sans finding restant. Acceptation humaine, reprise de
la Story Companion, redémarrage, commit et push non effectués.
