# Implementation Plan

## Overview

Ce plan transforme la Story 0247 en une baseline d’évidence du Companion
Claude, sans refonte ni changement fonctionnel du plugin. Il documente la
révision source, le comportement déterministe que le dépôt permet de tester,
les compatibilités runtime encore inconnues et un protocole manuel isolé.

L’état initial est `origin/main` à
`7c577230d9a6651b411d77fa93e8c7a4bc994e12`. La copie locale acceptée de
l’ADR-084 reste non suivie et doit être citée comme source locale, pas comme
document publié. Aucun essai Claude, installation, dépendance ou accès à un
profil utilisateur ne fait partie de ce plan.

Le contexte DevLog ciblé a été complété, mais reste `PARTIALLY_FRESH / STALE`,
tronqué et de confiance faible. Son résumé reprend des contraintes des
Stories 0186 et 0192 : l’identité de session Claude Code documentée est
distincte de la situation OpenCode ; le Companion doit rester observateur et
l’interprétation contextuelle non-trustée et déclenchée explicitement. Ces
points ont été vérifiés dans les Stories locales. Les fichiers courants du
dépôt et la Story 0247 restent les sources primaires.

Provenance : `devlog_story_agent`, tâche/snapshot
`1613c140-b37f-4ee8-ac98-6acf42857353`, révision résolue
`7c577230d9a6651b411d77fa93e8c7a4bc994e12`, 60 éléments sélectionnés sur
1132 avec enrichissement de contenu tronqué/ignoré. Le résultat signale une
confiance `LOW` et ne fournit pas le contenu de 0193, 0225 ou de plusieurs
fichiers de configuration du plugin. Ces éléments ont donc été vérifiés dans
le dépôt local ; aucune affirmation provenant du résumé IA n’est traitée comme
une décision nouvelle.

## Planned Changes

1. **Figer le périmètre de la baseline**
   - Confirmer la révision du plugin et inventorier le manifest, les hooks,
     les types, l’installation et les surfaces UI.
   - Associer chaque affirmation à une preuve de dépôt, à une observation
     runtime ou à `UNKNOWN`; ne pas convertir Story 0193 en preuve d’exécution.
   - Respecter l’ADR-084 et les limites de la Story 0247.

2. **Établir la matrice de compatibilité**
   - Décrire les comportements de découverte/installation, cycle de session,
     hook d’outil, états, présentation, Context Inspector, gestion d’erreur,
     isolation, mise à jour et retrait.
   - Pour chaque comportement, consigner l’état `SUPPORTED`, `PARTIAL`,
     `UNSUPPORTED` ou `UNKNOWN`, les versions réellement établies, la
     révision, la date et la nature de l’évidence.
   - Une version Claude/Mods non confirmée reste `UNKNOWN`; pas de promesse
     de compatibilité.

3. **Vérifier la faisabilité des tests déterministes**
   - Examiner les outils disponibles dans le dépôt et les fonctions pures
     réellement testables, en particulier la dérivation des états.
   - Réutiliser l’outillage existant s’il existe. Ne pas ajouter de
     dépendance.
   - N’extraire un seam de test qu’en dernier recours, si sa compatibilité avec
     le chargement du plugin est démontrée et si l’extraction ne change pas le
     comportement. Sinon documenter le gap et demander une clarification,
     plutôt que dupliquer la logique en fixture.

4. **Définir le protocole runtime manuel**
   - Écrire des prérequis, versions, étapes, résultats attendus, critères
     d’échec, procédure de nettoyage et format de collecte assainie.
   - Couvrir les surfaces réellement accessibles, l’isolation entre sessions,
     les erreurs, les cas incomplets et la découverte/installation/retrait.
   - N’exécuter aucune étape impliquant Claude, une installation ou un profil
     utilisateur sans autorisation séparée. Résultat non exécuté :
     `NOT_EVALUATED`.

5. **Produire le dossier de baseline**
   - Publier la matrice, le protocole et le rapport dans le répertoire Story.
   - Distinguer faits statiques, tests exécutés, observations runtime,
     inconnues et limitations.
   - Recommander au plus un prochain incrément borné ou conclure qu’aucun
     changement de production n’est justifié.

## Files to Modify

- `docs/stories/0247-claude-companion-regression-baseline/story.md` — ne
  changer les critères ou le périmètre que si l’analyse de faisabilité révèle
  une incompatibilité; toute modification matérielle retourne à approbation.
- Composant `claude-mod/devlog-companion/` — aucun changement de comportement
  prévu. Un éventuel seam de test reste conditionnel à la preuve de
  compatibilité décrite à l’étape 3 et à l’approbation applicable.

## Files to Create

- `docs/stories/0247-claude-companion-regression-baseline/companion-baseline-v1.md`
  — matrice de comportements, versions, preuves, statut et inconnues.
- `docs/stories/0247-claude-companion-regression-baseline/runtime-validation-protocol.md`
  — protocole manuel versionné, isolé et assaini ; aucune exécution réelle
  n’est implicite.
- Fixtures/tests sous `claude-mod/devlog-companion/` uniquement si l’étape 3
  confirme une voie reproductible sans nouvelle dépendance et sans changer
  le comportement. Le chemin exact reste indéterminé tant que l’outillage
  n’est pas établi.
- `docs/stories/0247-claude-companion-regression-baseline/implementation-report.md`
  — résultats réellement obtenus, documentation réconciliée et limitations.

## Dependencies

- Story 0247 et Repository Analysis approuvées.
- ADR-084 fournie localement par le propriétaire ; sa publication dans `main`
  n’est pas incluse et la référence doit rester explicitement signalée comme
  locale tant que ce n’est pas fait.
- Implémentation actuelle du plugin à la révision épinglée.
- Accès à un runtime Claude isolé requis seulement pour les validations
  manuelles ; sans cet accès les points correspondants restent `UNKNOWN` ou
  `NOT_EVALUATED`. Le propriétaire a confirmé le 26 novembre 2026 que cette
  incertitude ne bloque pas la baseline et qu’aucune revendication de support
  ne sera faite à partir de points non évalués.
- Aucun nouveau service backend, endpoint, stockage, schéma ou dépendance
  externe.

## Test Plan

- Examiner la matrice contre la source à la révision épinglée et vérifier que
  chaque affirmation a une preuve explicite.
- Si une voie locale est confirmée, créer des tests comportementaux pour les
  dérivations d’état et les cas limites démontrables sans SDK/runtime
  propriétaire ; résultat attendu : tests déterministes et reproductibles,
  sans dépendance ajoutée.
- Si aucune voie locale fiable n’est disponible, ne pas écrire de faux tests
  de parité; documenter le point `UNKNOWN` et la décision demandée.
- Vérifier les références, bornes, caviardage et cohérence du protocole
  manuel ; ne pas lancer l’installateur.
- Commandes candidates après inspection de l’outillage, pas présumées
  disponibles :
  - commande de test existante si découverte dans le dépôt;
  - `git diff --check`;
  - contrôles manuels seulement après une autorisation distincte.
- Conditions de succès : fichiers de baseline cohérents avec la révision,
  inconnues visibles, aucun support non prouvé, aucune modification de
  comportement ou d’installation.

## Risks

- **Versions hôte inconnues :** risquerait de transformer une observation
  unique en engagement de support. Mitigation : épingler toute version
  réellement établie, sinon conserver `UNKNOWN`.
- **Pas de harness ou de SDK de test dans le module :** des fixtures doublant
  la logique donneraient une fausse assurance. Mitigation : vérifier la
  faisabilité avant tout seam ; ne pas ajouter de dépendance ; demander une
  décision si les critères ne peuvent être satisfaits.
- **Installateur écrit dans le profil personnel :** une validation peut
  écraser l’installation existante. Mitigation : ne pas exécuter `install.sh`;
  toute validation future exige un profil isolé et une approbation séparée.
- **Story 0193 et ADR-084 ont une provenance/version différente de l’état
  courant :** risque d’ériger l’intention historique en fait runtime.
  Mitigation : séparer intention, implémentation présente et comportement
  effectivement observé.
- **ADR-084 non suivie dans `main` :** lien canonique manquant pour les
  prochains lecteurs. Mitigation : le signaler dans la baseline et traiter
  sa publication comme action distincte, sans inclure le fichier local dans
  cette Story sans autorisation.

## Validation Checklist

- [ ] Le périmètre est limité à une baseline Claude; aucun adapter OpenCode,
      Core partagé, Gate ou contrat backend n’est modifié.
- [ ] La révision testée et toutes les versions connues sont épinglées.
- [ ] `UNKNOWN`, `NOT_EVALUATED` et résultat effectivement réussi sont
      distingués.
- [ ] Les comportements de hook ne sont pas présentés comme couverture wire
      complète ou dispatch confirmé.
- [ ] Les statuts de tâche DevLog, opération d’outil, trace et connectivité
      restent distincts.
- [ ] Les conclusions sur sessions et identifiants sont limitées à l’évidence
      disponible; aucune identité n’est inférée.
- [ ] Les tests locaux, s’ils sont réalisables, ne dupliquent pas la logique
      et n’ajoutent aucune dépendance.
- [ ] Le protocole manuel inclut versions, isolation, échec et nettoyage;
      aucune installation utilisateur n’est exécutée implicitement.
- [ ] L’installateur, le manifeste, la découverte, la mise à jour et le retrait
      sont évalués sans écraser la configuration existante.
- [ ] Les secrets, prompts, entrées/sorties brutes et identifiants sensibles
      sont absents des fixtures et artefacts.
- [ ] Tout correctif de production requis est séparé et attend une Story et
      une autorisation propres.
- [ ] Le rapport ne recommande pas plus d’un incrément suivant.

## Recommendation

**Ready for implementation**

Le propriétaire accepte qu’une absence de runtime Claude isolé ou d’outil de
test compatible soit consignée `UNKNOWN`/`NOT_EVALUATED`, sans bloquer la
baseline et sans produire de revendication de support. La publication de
l’ADR-084 dans `main` reste à traiter séparément. Cette recommandation est
technique uniquement : aucun code ne doit être modifié avant l’approbation
humaine explicite de ce plan. Les essais réels et installations exigent
toujours une autorisation distincte.

**Approbation humaine :** le propriétaire a approuvé ce plan le
26 novembre 2026. Cela ne démarre pas l’implémentation dans le cadre de la
demande courante ; l’autorisation explicite de commencer le code reste attendue.

## Approval Required

Implementation Plan completed.

Human approval received on 2026-11-26. Implementation was started only after
the user's separate explicit instruction on 2026-11-26.
