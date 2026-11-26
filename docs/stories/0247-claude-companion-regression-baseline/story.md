# Story 0247 — Claude Companion : baseline de compatibilité et de non-régression

## Statut

**Implémentation documentaire terminée — revue indépendante en attente.**

Cette Story couvre le premier incrément recommandé par l’ADR-084 : établir
une baseline vérifiable du Companion Claude avant extraction de logique
partagée ou évolution susceptible d’affecter Claude. Elle ne constitue pas
une autorisation de modifier le plugin de production, d’installer des
dépendances ni d’effectuer des essais réels. L’implémentation autorisée ici
produit les artefacts d’évidence et le protocole, sans changement de code du
Companion.

## Origine et autorité

- L’ADR-084, acceptée par le propriétaire le 25 novembre 2026, recommande
  explicitement une baseline de régression Claude avant toute extraction
  partagée.
- Le propriétaire a choisi ce périmètre comme première Story le 26 novembre
  2026.
- **Écart de provenance :** la copie disponible de l’ADR-084 est locale et
  non suivie par Git ; elle est absente de `origin/main` à la révision
  `7c577230d9a6651b411d77fa93e8c7a4bc994e12`. La Story la cite comme source
  fournie par le propriétaire, sans prétendre qu’elle est publiée dans le
  dépôt.

## Problème

Le dépôt contient un Companion natif Claude ainsi que des Stories antérieures
qui décrivent ses comportements. La Story 0193 porte toutefois une version
ancienne du plugin, et aucun dispositif de baseline reproductible ni contrat
de compatibilité maintenu n’a été établi pour protéger ces comportements
avant les travaux multi-plateformes.

Sans baseline révisionnée, les changements partagés ou propres à une
plateforme risquent de régresser Claude sans que les validations OpenCode ou
les tests de code déterministes puissent le révéler.

## Objectif

Établir et documenter une baseline de compatibilité Claude, fondée sur le
plugin réellement présent dans le dépôt et sur des versions d’hôte et de
runtime explicitement vérifiées. Distinguer les comportements testables
automatiquement des comportements exigeant une observation manuelle, et
marquer `UNKNOWN` toute capacité ou combinaison qui n’a pas été vérifiée.

## Périmètre initial

- Inventorier l’implémentation actuelle du Companion Claude, son modèle
  d’état, ses hooks, ses surfaces de présentation et son mécanisme
  d’installation.
- Déterminer, à partir d’éléments vérifiables, les versions d’hôte et de
  runtime effectivement prises en charge ; ne pas transformer une version
  supposée en promesse de compatibilité.
- Définir une matrice de comportements et d’évidence couvrant au minimum
  découverte/installation, cycle de session, observation des outils,
  transitions d’état, présentation, Context Inspector, erreurs,
  isolation et retrait/mise à jour du plugin.
- Ajouter uniquement les fixtures et tests déterministes nécessaires à
  la baseline, si l’analyse confirme qu’ils sont réalisables sans dépendre
  d’un runtime propriétaire non disponible.
- Définir un protocole manuel versionné, borné et reproductible pour les
  comportements qui ne peuvent pas être établis par les tests locaux.

## Hors périmètre

- Extraction ou refactorisation de logique partagée.
- Implémentation de l’adaptateur OpenCode, du Companion OpenCode Desktop/TUI
  ou du spike de compatibilité OpenCode.
- Modification des contrats backend, de l’autorisation, de l’authentification,
  du MCP, du Preflight Gate ou des workflows Claude.
- Mise à niveau de dépendances ou migration de packaging sans analyse et
  approbation distinctes.
- Déclaration de support d’une version, installation dans un profil réel,
  essai réel Claude, publication marketplace, commit, push ou merge sans
  autorisation applicable.

## Contraintes

1. L’ADR-084 et les décisions fondatrices qu’elle cite restent des contraintes,
   sans que la baseline ne rouvre leurs choix.
2. Une tentative de hook ne prouve pas un dispatch confirmé ; absence
   d’événement ou de terminal ne vaut pas succès.
3. Les états de tâche DevLog, d’exécution d’outil, de trace et de connectivité
   restent distincts. Aucune présence ou aucun résultat ne doit être fabriqué.
4. Les données et états observés restent isolés par plateforme, session et
   opération selon les contrats effectivement disponibles.
5. Les identifiants sensibles, entrées/sorties brutes et secrets ne sont pas
   conservés dans les fixtures ni les rapports.
6. Les validations réelles restent distinctes des tests déterministes et
   exigent une autorisation humaine séparée.

## Critères d’acceptation proposés

1. La baseline décrit les fichiers et comportements réellement présents à
   une révision identifiée ; les divergences avec les Stories historiques,
   notamment la version du plugin, sont signalées.
2. Une matrice versionnée distingue les capacités `SUPPORTED`, `PARTIAL`,
   `UNSUPPORTED` et `UNKNOWN`, avec la preuve et la date/révision associées.
3. Les transitions de domaine et les interprétations déterministes qui peuvent
   être testées localement disposent de fixtures reproductibles, incluant
   résultats inconnus/incomplets, erreurs, concurrence et isolation pertinents.
4. Chaque comportement runtime ou visuel non reproductible automatiquement
   dispose d’un protocole manuel avec prérequis, version cible, étapes,
   résultats attendus, critères d’échec et collecte d’évidence assainie.
5. L’installation, la mise à jour, la découverte et le retrait sont évalués
   indépendamment de l’intégration OpenCode ; tout risque de modification de
   configuration ou de données utilisateur est explicité.
6. Aucun résultat non exécuté, bloqué ou non vérifié n’est présenté comme
   réussi ou comme preuve de support.
7. La Story ne modifie pas le comportement de production du Companion, du
   backend ou du Gate ; tout correctif nécessaire est proposé comme travail
   séparé soumis à approbation.
8. Le rapport de baseline recommande le prochain incrément borné, ou conclut
   qu’aucun changement de production n’est justifié.

## Questions initiales pour Repository Analysis

- Quelle est la révision source exacte à baseliner, étant donné que la copie
  ouverte est détachée et que `origin/main` a avancé ?
- Quels comportements du code actuel sont effectivement couverts par des tests,
  fixtures ou workflows d’installation ?
- Quel runtime Claude Mods/Claude est déclaré ou vérifiable, et quelles
  surfaces restent expérimentales ou inconnues ?
- Les transitions pures peuvent-elles être testées sans importer les API du
  SDK propriétaire ?
- Quels comportements d’installation, d’upgrade et de retrait peuvent être
  reproduits sans toucher au profil utilisateur réel ?
- Quelles preuves manuelles sont nécessaires avant de qualifier un comportement
  de supporté ?
- L’ADR-084 doit-elle être publiée dans le dépôt avant l’approbation de cette
  Story, afin que ses références soient canoniques ?

## Workflow et gates

1. Story et Repository Analysis approuvées par le propriétaire.
2. Implementation Plan approuvé explicitement par le propriétaire le
   26 novembre 2026.
3. L’implémentation présente `companion-baseline-v1.md` et
   `runtime-validation-protocol.md`. Aucun changement du plugin ou essai réel
   n’est implicite.
4. Les résultats de validation sont consignés dans
   `implementation-report.md`; les points non exécutés restent
   `NOT_EVALUATED` ou `UNKNOWN`.
5. La revue indépendante et l’acceptation finale restent des décisions
   distinctes et humaines.
