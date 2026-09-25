# Story 0147 — Reaping des processus Git

## Statut

Implémentée et validée localement.

## Problème

`ProcessGitCommandExecutor` lançait un processus Git et, sur timeout ou erreur
de lecture de la sortie, pouvait le tuer sans attendre sa terminaison. Un
`destroyForcibly()` asynchrone ne suffit pas à récolter le processus Unix :
les exécutions répétées pouvaient donc laisser des processus zombies.

## Correction

- centraliser le nettoyage dans un `finally` couvrant tous les chemins d’échec;
- demander un arrêt gracieux, forcer l’arrêt après le délai, puis toujours
  appeler `waitFor` pour effectuer le reap;
- fermer explicitement les trois flux du processus dans le `finally` avant de réap le processus et d’arrêter l’exécuteur;
- annuler la tâche de lecture puis attendre sa terminaison effective sans délai de retour
  laissant un reader vivant (la fermeture du pipe débloque `readAllBytes()`);
- rendre le timeout injectable au package pour tester rapidement le chemin.

## Critères d’acceptation

- [x] Un timeout de commande termine et récolte le processus enfant.
- [x] Les chemins normaux et d’échec ferment les flux et attendent la terminaison
  effective de l’exécuteur de lecture.
- [x] Un test de régression couvre une commande bloquée et un test vérifie la fermeture
  explicite des trois flux avant l’arrêt de l’exécuteur.
- [x] Une interruption pendant l'attente force le reap puis restaure le statut interrompu.
- [x] Les changements restent limités à l’exécuteur Git et à son test.

## Clôture du NO-GO final

Le test de régression appelle désormais `execute()` avec un faux exécutable Git réel (script POSIX), au lieu d’invoquer une méthode privée de nettoyage. Le script bloque sur stdin puis écrit un marqueur à la réception de l’EOF : le marqueur et le délai borné prouvent que la fermeture des flux libère le processus avant le reap, puis que le reader est effectivement terminé avant le retour de `execute()`.

L’attente de terminaison de l’exécuteur de lecture reste volontairement non bornée. Elle ne s’exécute qu’après fermeture des trois flux et reap du processus ; elle garantit donc qu’aucun reader ne reste vivant. Une borne ici pourrait rendre le contrat de nettoyage non déterministe. Le reap lui-même reste borné à cinq secondes avant `destroyForcibly()`, et le test vérifie que la fermeture stdin évite ce délai dans le cas nominal.
