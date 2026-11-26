# Story 0246 - Plan d'implémentation

## Statut

PLAN APPROVED - implementation authorized by the human project owner on
2026-11-26. La [Story](story.md) et son [analyse de dépôt](repository-analysis.md)
ont été approuvées séparément. L'acceptation du correctif reste distincte.

## Périmètre et préconditions

- `tools/devlog_mcp/runtime.py` et `tools/devlog_mcp/tests/test_runtime.py`.
  Pas de changement au contrat MCP, aux règles de retry, à Keycloak,
  aux plugins ou à la documentation d'installation.
- Revérifier la révision, le diff local et les chemins concernés avant
  d'écrire du code. Préserver l'ADR-084 et les travaux Companion.
- Le Story Agent et l'enregistrement distant restent indisponibles dans
  cette session (`DEVLOG_CONTEXT_ERROR`, `DEVLOG_LIFECYCLE_ERROR`). Aucun
  job en attente ni UUID DevLog connu. La base reste l'analyse approuvée.

## 1. Reproduire la course

Introduire un test au niveau de la supervision, avec un ancien enfant et
un remplaçant simulés. Utiliser des barrières explicites pour imposer cet
ordre : le renouvellement est en cours, l'ancien enfant a terminé, le
superviseur lit cet état, puis le remplaçant est publié. Le test doit
échouer sur la décision de sortie actuelle, pas sur un délai ni sur un
chemin de mock absent. Si une extraction mécanique de la boucle est
nécessaire pour isoler le test, garder son comportement inchangé pour
enregistrer le résultat rouge. Utiliser uniquement des timeouts de garde
pour éviter qu'un échec de test ne bloque la suite.

Si le comportement actuel ne peut pas être mis en défaut, arrêter et
réexaminer le diagnostic avant de proposer une correction spéculative.

## 2. Corriger la décision de supervision

Réutiliser le verrou et l'état `renewal_in_progress` de `RuntimeSession`.
Au point de décision, lire de manière cohérente l'enfant courant, son
état, le renouvellement et la demande d'arrêt. Un enfant terminé pendant
un remplacement en cours n'est pas une sortie définitive du lanceur.
Reprendre l'observation de l'enfant courant à l'itération suivante.
Conserver la cadence existante, sans temporisation supplémentaire destinée
à masquer la course et sans attente indéfinie sur le renouvellement.

La disparition du parent et l'arrêt définitif restent prioritaires,
même s'il n'y a aucun enfant vivant à cet instant. Sérialiser la décision
de sortie avec le début d'un renouvellement pour qu'aucun démarrage tardif
ne puisse publier un enfant après cette décision. Utiliser le chemin
d'arrêt existant pour terminer uniquement l'enfant appartenant à
l'instance. Ne pas ajouter d'attente réseau, de `child.wait()` ou de
nouvelle section bloquante sous verrou dans le superviseur.

Conserver la restauration du handshake, les générations, les limites déjà
appliquées à l'acquisition et au démarrage, et le traitement des erreurs de
renouvellement. Après échec, ne pas présenter un remplaçant comme prêt et
ne pas attendre une génération qui ne viendra pas. Avant remplacement,
préserver le traitement existant du défaut d'acquisition et de l'enfant
encore vivant selon le chemin appelant. Ne pas inventer de nouveau code
d'erreur ni convertir le `143` d'une vraie sortie en succès. Aucun nouveau
rejeu de requête.

## 3. Vérifier et rendre compte

| Cas | Résultat attendu |
|---|---|
| Ancien enfant arrêté, remplacement légitime en cours | Le test rouge passe ; le même lanceur supervise le remplaçant sans publication prématurée de sortie. |
| Arrêt demandé ou parent disparu pendant le remplacement | Arrêt définitif prioritaire, aucun enfant survivant ou démarré après la décision de sortie. |
| Échec d'acquisition, de démarrage ou de handshake | Pas de nouvelle readiness fictive ni d'attente indéfinie. L'état final respecte le chemin d'échec existant. |
| Sortie inattendue hors renouvellement | Supervisée et rapportée comme avant. |
| Handshake, retries, isolation des générations, JSON-RPC et diagnostics | Tests existants verts ; pas de rejeu non autorisé, de doublon terminal ni de secret exposé. |

Exécuter d'abord les tests de reproduction et de supervision ciblés, puis
la suite `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/devlog_mcp/tests -v`
(baseline : 69 tests), et `git diff --check`. Comparer le diff et les
résultats aux critères d'acceptation. Consigner le rouge/vert, les cas non
testés et la décision de réconciliation documentaire dans le rapport.
Aucun test réel, changement de configuration, redémarrage, commit ou push
n'est inclus.

## Gate humaine

Approuver ce plan avant l'implémentation. La validation du correctif et
la reprise de la Story Companion restent distinctes.
