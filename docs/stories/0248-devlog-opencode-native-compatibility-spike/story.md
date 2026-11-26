# Story 0248 — DevLog Companion — OpenCode Native Compatibility Spike

## Status

**Clôture en l’état demandée par le propriétaire le 26 novembre 2026, avec les gaps runtime ci-dessous explicitement conservés.** Cette disposition clôt le spike documentaire et son prototype sans certifier la compatibilité Desktop ni un workflow DevLog gouverné.

La Repository Analysis et le présent Implementation Plan ont été explicitement
approuvés par le propriétaire, puis l’implémentation du package a été autorisée
explicitement. Les expériences runtime ont été autorisées dans un environnement
isolé et partiellement effectuées. Le propriétaire demande maintenant la
livraison du spike en l’état, sans déclarer satisfaits les critères runtime
restants. La validation de la pull request demeure une étape humaine distincte
avant toute fusion.

## Origine et autorité

- [ADR-084](../../decisions/ADR-084.md), acceptée par le propriétaire le
  25 novembre 2026, a été copiée à l’identique depuis le fichier local non suivi
  et est préparée pour une publication documentaire indépendante. Tant qu’elle
  n’est pas publiée, elle n’appartient pas au catalogue distant canonique.
  Elle gouverne le périmètre proposé selon l’approbation du propriétaire.
- Story 0247 fournit une baseline documentaire Claude ; elle ne certifie pas le
  runtime Claude.
- L’audit de faisabilité OpenCode antérieur conclut à un **CONDITIONAL GO**, pas
  à une certification runtime. Ses observations sur `@opencode/plugin@2.4.0`,
  le CLI V2, le CLI legacy et Desktop ne prouvent pas la combinaison réellement
  installée ni le comportement visuel ou RPC.
- La présente Story propose un spike isolé. Elle ne présume pas que les API
  expérimentales, le packaging candidat ou les versions auditées sont compatibles.

## Problème

DevLog est intégré à OpenCode par MCP et par un workflow de préflight, mais le
dépôt ne possède pas de Companion OpenCode natif. Les surfaces serveur, RPC et
Desktop décrites par les versions auditées pourraient permettre une présence
native, mais leurs versions effectives, leur couverture d’observation, leur
isolation et leur comportement interactif ne sont pas établis.

Le CLI legacy et le CLI V2 ne doivent pas être confondus. De même, l’achèvement
d’un outil OpenCode n’établit pas l’achèvement d’une tâche AI DevLog. Un spike
qui assimilerait des événements manquants à des succès, ou contournerait le
Preflight Gate, donnerait une fausse preuve de compatibilité et de gouvernance.

## Objectif

Évaluer la faisabilité d’un Companion OpenCode natif et indépendamment
installable, avec le serveur OpenCode comme observateur et OpenCode Desktop
comme surface principale. Produire des preuves versionnées et reproductibles
sur les contrats et capacités réellement disponibles, tout en conservant les
issues inconnues, incomplètes et non évaluées.

Claude et OpenCode restent deux plateformes de premier rang. Ce spike ne modifie
pas le Companion Claude et n’en déduit aucune compatibilité par analogie.

## Périmètre proposé

Après approbation des gates correspondants :

1. Vérifier les versions réellement présentes du CLI, de Desktop, du sidecar ou
   runtime, du plugin SDK et des SDK pertinents. Distinguer explicitement CLI
   legacy et CLI V2 et établir le pairing Desktop/sidecar effectivement testé.
2. Comparer les APIs installées aux surfaces de la baseline auditée
   `@opencode/plugin@2.4.0`, en identifiant les différences pertinentes de hooks,
   événements, RPC et contributions Desktop.
3. Construire un plugin serveur minimal, isolé et retirable, observant
   passivement les outils MCP DevLog et enregistrant ses hooks sans modifier les
   entrées/sorties, l’autorisation ni le résultat des outils.
4. Examiner la corrélation before/after, les événements de session et les cas
   d’absence d’événement terminal. Distinguer tentative, dispatch établi,
   exécution, résultat signalé, interruption et résultat incomplet/inconnu.
5. Décoder les réponses DevLog structurées et leurs erreurs. Extraire un
   identifiant de tâche uniquement lorsqu’il est explicitement présent et
   conserver séparément le cycle de vie de l’outil OpenCode et celui de la tâche
   AI DevLog.
6. Suivre opérations concurrentes, réponses hors ordre, sessions et sous-
   sessions sans mélange d’état; traiter explicitement les résultats inconnus
   et incomplets.
7. Enregistrer une contribution native Desktop, afficher un panneau interactif
   et un indicateur de présence limité à la session (par exemple ComposerPill
   ou une capacité équivalente effectivement disponible).
8. Évaluer le RPC serveur-vers-interface, ses erreurs, reconnexions et réponses
   tardives, avec isolation des sessions, workspaces et fenêtres applicables.
9. Vérifier la coexistence avec les plugins de gouvernance existants, notamment
   le Preflight Gate, sans désactivation, contournement ni correction implicite.
10. Vérifier l’installation et le retrait indépendants du spike; analyser la
    faisabilité de packaging de code ou contrats communs Claude/OpenCode sans
    extraire de Shared Companion Core.
11. Documenter la réutilisabilité future de la projection serveur pour une
    présentation TUI et les workflows distants, sans déclarer leur support
    runtime sur la seule base d’une analyse.

## Hors périmètre

- Toute modification du Companion Claude ou toute extraction de Shared
  Companion Core.
- Toute modification des contrats MCP ou REST, du backend DevLog, du Core ou
  de son comportement.
- Tout framework générique de plugins ou d’intégration multi-hôtes.
- L’implémentation du Context Inspector de production ou une lecture de trace
  non couverte par une autorisation existante.
- Toute nouvelle authentification, tout proxy de credential ou toute extension
  de portée d’autorisation.
- Toute modification, désactivation ou contournement du Preflight Gate. Si le
  contrat OpenCode V2 révèle une défaillance, la correction relève d’une Story
  distincte et bloque la validation d’un workflow DevLog réellement gouverné.
- Simulation d’événements lifecycle absents, assimilation d’un succès outil à
  une réussite de tâche DevLog, ou déclaration de compatibilité sans preuve
  runtime correspondante.
- Publication, installation dans un profil personnel, test contre un service
  réel, commit, push ou merge sans autorisation humaine applicable.

## Contraintes

1. L’ADR-084, les ADR référencées par celle-ci et les contrats DevLog existants
   restent contraignants. Java/Core demeure l’autorité sur identité, scope,
   contexte, autorisation et connaissance de confiance.
2. Le Companion est observateur par défaut. Il ne modifie ni appels d’outils,
   ni décisions du Gate, ni autorisation, ni workflow de tâche; ses erreurs
   n’interrompent pas l’opération observée.
3. Un hook before prouve au plus une tentative observée. L’absence d’un hook
   after ne prouve ni succès, ni échec, ni annulation.
4. Une réussite d’exécution OpenCode n’implique pas la réussite de la tâche AI
   DevLog. Les identifiants d’appel, d’opération MCP et de tâche restent
   distincts.
5. Les états `UNKNOWN`, incomplets, bloqués et `NOT_EVALUATED` sont préservés.
   Aucune transition, attribution ou couverture lifecycle manquante n’est
   inventée.
6. Les observations sont bornées, assainies et isolées par instance serveur,
   projet, workspace, session (y compris enfant) et appel selon les preuves et
   contrats réellement disponibles.
7. L’interface n’acquiert pas de credential DevLog. Un identifiant de tâche ou
   une connexion RPC ne constitue pas une autorisation de lire une trace.
8. Le spike doit être indépendant et retirable sans modifier Claude, les autres
   plugins, le Gate ni la configuration utilisateur hors du périmètre isolé.
9. Desktop est la cible de présentation principale. TUI/SSH/remote ne sont
   qualifiés que si leurs propres versions, installations et comportements
   sont effectivement validés.
10. Toute expérience runtime requiert une autorisation explicite distincte,
    une matrice de versions convenue, un environnement isolé et une procédure
    de restauration/retrait vérifiable.

## Critères d’acceptation proposés

1. Une matrice de compatibilité consigne les versions effectives et leur
   provenance pour CLI legacy/V2, Desktop, sidecar/runtime, plugin API et SDK;
   les combinaisons non vérifiées restent `UNKNOWN`.
2. Les écarts entre APIs présentes et baseline `@opencode/plugin@2.4.0` sont
   documentés avec la source, la version, la surface affectée et les limites
   de preuve.
3. Un prototype serveur minimal peut être chargé et retiré de façon
   reproductible dans l’environnement isolé autorisé; son observation ne
   change pas le résultat des outils et une erreur du plugin ne les bloque pas.
4. Les essais before/after établissent séparément tentative, dispatch lorsque
   prouvé, résultat rapporté, interruption et absence/incomplétude. Aucun
   événement manquant n’est synthétisé.
5. Le décodage couvre les réponses structurées DevLog, enveloppes d’erreur,
   réponses incomplètes et identifiant de tâche présent/absent/invalide.
   L’issue plateforme et l’issue de tâche DevLog sont rapportées séparément.
6. Des fixtures déterministes vérifient les identités, transitions, résultats
   inconnus/incomplets, concurrence, ordre inversé, isolation et assainissement
   réalisables sans runtime propriétaire.
7. Le panneau et l’indicateur Desktop sont réellement enregistrés, rendus et
   interactifs sur une combinaison épinglée; un enregistrement de contribution
   sans rendu ne satisfait pas ce critère.
8. Les messages RPC et leurs erreurs sont bornés et scellés au scope prévu.
   Les changements de session, réponses tardives et reconnexions ne révèlent
   ni ne réaffichent l’état d’un autre scope.
9. La coexistence avec les plugins de gouvernance est vérifiée. Le Gate reste
   actif et inchangé; si son comportement V2 est inefficace, l’échec est un
   blocker explicite et une Story séparée est proposée.
10. Installation, mise à jour si applicable, désinstallation et vérification
    post-retrait sont documentées pour le spike isolé; aucune configuration
    personnelle ou du Companion Claude n’est modifiée.
11. Une analyse de packaging décrit les options et preuves nécessaires à une
    éventuelle consommation commune par Claude et OpenCode; aucun code partagé
    n’est extrait ni imposé.
12. La faisabilité TUI/remote est qualifiée séparément de Desktop et reste
    `UNKNOWN`/`NOT_EVALUATED` si elle n’a pas été exécutée sur une topologie
    précisément déclarée.
13. Le rapport distingue résultats statiques, fixtures déterministes, essais
    runtime et acceptation humaine; il inclut révision, versions, protocole,
    preuves assainies, échecs, limites et combinaisons non testées.
14. Aucune revendication de support n’est formulée pour une capacité ou une
    combinaison qui n’a pas passé ses validations applicables. Les blockers et
    changements hors périmètre sont proposés comme travaux séparés.

## Questions initiales pour Repository Analysis

- Quels binaires et paquets OpenCode sont réellement présents et comment
  distinguer de manière fiable CLI legacy, CLI V2, Desktop et sidecar ?
- Quelle version du plugin SDK est consommée par le runtime effectif, et quels
  écarts existe-t-il depuis la baseline auditée 2.4.0 ?
- Quels hooks couvrent les appels MCP DevLog, les outils imbriqués, Code Mode,
  les erreurs, annulations, sous-sessions et tâches longues ?
- Quels IDs stables et événements RPC sont exposés, à quels scopes et avec
  quelles garanties d’ordre, de livraison et de reconnexion ?
- Quelles contributions Desktop sont disponibles dans le build installé, et
  lesquelles sont expérimentales ou nécessitent un flag ?
- Le Preflight Gate existant fonctionne-t-il avec le contrat V2 réel, en
  particulier la sélection d’agent et la classification d’achèvement ?
- Quelle méthode d’installation isolée et réversible est supportée pour le
  spike, sans effet sur les plugins ou profils personnels ?
- Les contrats MCP actuels exposent-ils suffisamment l’identifiant de tâche et
  son résultat sans inférer un lifecycle absent ?
- Peut-on partager ultérieurement des contrats ou fixtures sans imposer à
  Claude un chargement runtime ou une migration de packaging ?
- Quelles parties peuvent être testées avec fixtures et quelles affirmations
  nécessitent un runtime Desktop réel ?

## Workflow et gates

1. Story proposée et périmètre approuvé par le propriétaire. L’accord de
   création de cette Story ne vaut pas approbation de ses artefacts suivants.
2. Repository Analysis en lecture seule; aucune installation ou expérience
   runtime n’est implicite.
3. Approbation humaine explicite de l’analyse.
4. Implementation Plan détaillant isolation, versions, sécurité, étapes,
   critères d’arrêt, retrait et preuves; approbation humaine distincte.
5. Autorisation humaine distincte avant toute installation ou expérience
   runtime. Un environnement ou credential réel exige son autorisation propre.
6. Implémentation minimale du spike uniquement après approbation des gates
   applicables; aucun changement Claude, backend, contrats ou Gate.
7. Rapport séparant preuves et statuts, puis revue indépendante et acceptation
   humaine. L’acceptation de la Story ne découle ni du merge ni d’un test vert.

## Livrables envisagés

- `repository-analysis.md` : inventaire des versions, contrats, capacités,
  Gate, installation et blockers à la révision examinée.
- `implementation-plan.md` : seulement après l’analyse et son approbation;
  protocole runtime isolé et plan de retrait.
- Prototype serveur et contribution Desktop isolés, plus fixtures ciblées :
  seulement après approbation du plan et autorisation d’implémentation.
- `runtime-validation-protocol.md` : prérequis, combinaison épinglée, étapes,
  résultat attendu, critères d’arrêt et collecte assainie; aucune exécution
  n’est autorisée par sa rédaction.
- `implementation-report.md` : résultats et limites effectifs, sans transformer
  `UNKNOWN`, `BLOCKED` ou `NOT_EVALUATED` en succès.

Artefacts réalisés :

- [Implementation Plan](implementation-plan.md) — approuvé.
- [Repository Analysis](repository-analysis.md) — approuvée.
- [Runtime Validation Protocol](runtime-validation-protocol.md) — préparé puis
  partiellement exécuté sous autorisation distincte; la qualification reste
  bloquée.
- [Implementation Report](implementation-report.md) — état de la revue et
  limites runtime.

## Notice de non-autorisation

Cette Story ne modifie ni code de production ni configuration OpenCode ou
Claude. Elle n’autorise pas l’installation d’un plugin, l’ajout de dépendances,
les essais runtime, les appels à un service réel, l’accès à des credentials,
la correction du Preflight Gate, l’extraction d’un Shared Companion Core, ni
les opérations Git d’intégration. Chacune de ces étapes demeure soumise à un
gate et à une autorisation humaine explicites.
