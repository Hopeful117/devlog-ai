# Claude Companion — protocole de validation runtime v1

## Statut

**Préparé, non exécuté (`NOT_EVALUATED`).** Ce document ne constitue pas une
autorisation d’installer le plugin, de démarrer Claude, de lire un profil
personnel ou d’effectuer un appel DevLog réel.

## Préconditions et sécurité

Avant toute exécution ultérieure, obtenir une autorisation humaine explicite
distincte couvrant l’essai runtime et préciser :

1. Produit/hôte exact (Claude Desktop ou Claude Code), version et système
   d’exploitation.
2. Version Claude Mods/SDK réellement chargée, méthode d’installation et
   source du package.
3. Profil de test jetable isolé, sans installation ou configuration
   personnelle. Le répertoire cible codé en dur par `install.sh` est sous
   `$HOME/.claude/...`; ne pas exécuter ce script tant qu’il ne peut pas être
   redirigé de manière vérifiable vers le profil isolé.
4. Sauvegarde et état initial du profil de test, procédure de restauration et
   retrait vérifié.
5. Mode sans secrets : ne pas utiliser de compte ou credential réel ; ne pas
   déclencher de requête de trace nécessitant authentification. Toute
   validation de trace autorisée doit suivre une autorisation et un protocole
   de sécurité propres.
6. Révision exacte du plugin et empreinte des cinq fichiers. Ne pas installer
   une copie différente de la baseline documentée.

Si l’un des prérequis manque, arrêter et enregistrer l’essai
`NOT_EVALUATED` avec la raison. Ne pas substituer le profil utilisateur.

## Règles de collecte

- Consigner uniquement la date, versions, révision/empreintes, étape, résultat
  (`PASS`, `FAIL`, `BLOCKED`, `NOT_EVALUATED`) et observation minimale.
- Ne pas enregistrer secrets, arguments de processus, prompts, entrées/sorties
  d’outils, traces complètes, données de projet ou captures révélant des
  identifiants.
- Un résultat visible dans l’interface ne prouve pas la livraison complète du
  protocole/hook sous-jacent.
- Un appel de test doit être simulé ou utiliser un environnement explicitement
  autorisé et non sensible. Ne jamais lancer une mutation DevLog.
- Restaurer le profil jetable et vérifier que l’installation a été retirée ;
  si le retrait ne peut pas être prouvé, déclarer l’essai `BLOCKED`.

## Cas de validation

Pour chaque cas, joindre les versions exactes et le résultat observé. Les
résultats attendus décrivent le code présent, pas une promesse de support.

| ID | Étapes | Résultat attendu / échec |
|---|---|---|
| R1 — Découverte | Installer seulement dans le profil jetable selon une procédure préalablement vérifiée ; redémarrer l’hôte cible. | Attendu : le plugin est découvert et le chargement est visible sans erreur. Échec : plugin absent, erreur de chargement ou profil réel modifié. |
| R2 — Session | Ouvrir une nouvelle session sans déclencher d’opération DevLog. | Attendu selon le code : tentative d’ouverture de la pane `devlog-companion`, vue initialisée à `now`. Noter séparément hook reçu, commande enregistrée et panneau réellement visible. |
| R3 — Présence vide | Observer AbovePrompt et Pane avant tout appel. | Attendu selon le code : libellé disponible/IDLE et message d’attente. Échec : surface absente ou erreur de rendu. |
| R4 — Tentative outil | Dans un environnement de test autorisé, déclencher un outil DevLog non mutateur. | Noter séparément tentative, dispatch établi (si preuve host disponible), état running et terminaison. Ne jamais déduire le dispatch du seul début du hook ni le succès de l’absence d’événement. |
| R5 — Succès / échéance | Observer un résultat explicitement réussi puis l’état à moins de 10 minutes et au-delà. | Attendu selon le reducer : READY avant fenêtre puis IDLE après, sous réserve que l’horodatage soit celui attendu. Toute divergence est consignée ; aucun appel n’est répété automatiquement. |
| R6 — Erreur | Utiliser uniquement un résultat de test non sensible explicitement en erreur ; vérifier exception et réponse `isError` séparément. | Attendu selon le code : statut d’opération failed / ERROR pour la dernière opération. Une erreur de lecture de trace ne doit pas être confondue avec l’échec de l’opération initiale. |
| R7 — Context Inspector | Ne tester la lecture de trace que si une autorisation dédiée, read-only et isolée existe. | Noter explicitement l’autorisation et l’état de trace. Sinon `NOT_EVALUATED`; ne pas configurer ou réutiliser un credential personnel. |
| R8 — Navigation et commande | Basculer Now/Context/History, fermer/rouvrir via `/devlog-companion`. | Attendu selon le code : onglets et toggle fonctionnent ; noter toute perte d’état ou erreur. |
| R9 — Surface et accessibilité | Sur les surfaces desktop/terminal disponibles, inspecter bandeau/pane ; activer réduction de mouvement sur desktop si possible. | Noter chaque surface séparément. Ne pas inférer qu’un fallback non testé est supporté. |
| R10 — Isolation | Avec des sessions de test indépendantes et données synthétiques, comparer état et opérations ; tester appels simultanés si le harness les permet. | Aucune donnée ne doit apparaître hors de son scope. Toute fuite ou ambiguïté est FAIL et bloque la qualification. Un unique essai ne prouve pas une isolation générale. |
| R11 — Mise à jour / retrait | Sur profil jetable seulement, remplacer la version puis retirer selon une procédure approuvée ; vérifier fichiers/configuration restants. | Attendu : mise à jour et retrait reproductibles sans altérer les autres plugins/données. À défaut de procédure sûre, `BLOCKED`; ne pas supprimer manuellement un profil réel. |

## Rapport d’exécution à compléter

```text
Date / environnement :
Hôte et version :
Claude Mods / SDK :
Révision plugin / empreintes :
Profil jetable et état initial vérifié : oui/non
Autorisation runtime : référence/portée
R1–R11 : résultat et observation assainie par cas
Incidents ou écarts :
Restauration/retrait vérifiés :
Évaluateur :
```

En l’absence d’exécution, conserver tous les cas comme `NOT_EVALUATED` ; ne
pas compléter le rapport par inférence depuis les sources statiques.
