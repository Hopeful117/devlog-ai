# Story 0247 — Repository Analysis

## État du workflow

Repository Analysis approuvée explicitement par le propriétaire le
26 novembre 2026. Le gate suivant a depuis été franchi : Implementation Plan
approuvé explicitement le 26 novembre 2026, puis implémentation documentaire
démarrée sur instruction distincte du propriétaire. Cette analyse est un
artefact historique du workflow ; elle ne décrit pas son état courant.

Aucun changement de code de production du Companion, du backend ou du Gate
n’a été autorisé ou réalisé par cette Story.

## Révision et limites de provenance

- Révision examinée : `origin/main` à `7c577230d9a6651b411d77fa93e8c7a4bc994e12`,
  après fusion de la Story 0246.
- Worktree interactif : HEAD détaché `7ecc16806e3dea3c412f98139952ed0cfabb6f54`,
  en retard sur `origin/main`, avec les changements locaux non commis de Story
  0246 et l’ADR-084 non suivie. Aucun de ces changements n’a été nettoyé,
  déplacé, commité ou modifié par cette analyse.
- Comparaison ciblée : le répertoire `claude-mod/devlog-companion/` de
  `origin/main` ne diffère pas de la copie locale.
- L’ADR-084 citée par cette analyse existe seulement comme fichier local
  non suivi. Elle affirme avoir été acceptée le 25 novembre 2026 ; ce statut
  est traité comme information fournie par le propriétaire, pas comme preuve
  Git de sa publication. La Story 0246 fusionnée signale elle aussi que
  l’ADR-084 locale n’a pas été touchée.
- Le catalogue de `origin/main` se termine à Story 0246 ; le numéro 0247
  n’était pas utilisé au moment de la préparation de cette Story.

## DevLog et sources de contexte

**DEVLOG_CONTEXT_ERROR** — le Story Agent a été appelé avec une requête
ciblée et une clé d’idempotence stable. Il a retourné l’état terminal
`FAILED / INVALID_AI_TASK_RESULT` : « Affected file is not supported by
cited evidence: claude-mod/devlog-companion/ ». La projection annonçait
`PARTIALLY_FRESH / STALE`, la révision source `7c577230`, 60 éléments
sélectionnés sur 1127 et une troncature du contenu. Aucune synthèse
DevLog exploitable ni connaissance validée n’en est tirée ; aucune seconde
requête altérée n’a été envoyée.

`TOOLS.md` identifie le projet par le slug `devlog-ai`, mais ne fournit pas
le couple URL de base / UUID projet requis par l’intégration locale de
cycle de vie des Stories. L’enregistrement DevLog de la nouvelle Story n’a
donc pas été effectué. Ce manque n’est pas un gate et ne bloque pas l’analyse.

Sources faisant autorité pour l’état physique :

- `origin/main:claude-mod/devlog-companion/`
- `origin/main:docs/stories/0193-devlog-companion-persistent-presence-ux-v1/story.md`
- `origin/main:docs/stories/0185-*` à `0192-*`
- `origin/main:docs/stories/0225-devlog-opencode-end-to-end-audit/story.md`
- `origin/main:docs/investigations/companion-integration-audit-2026-11-16.md`
- `TOOLS.md` et `.github/workflows/quality.yml`
- la copie locale non suivie de `docs/decisions/ADR-084.md`, uniquement comme
  décision fournie par le propriétaire et sous réserve de sa publication.

Vault : non consulté. Les questions de baseline et les limites de provenance
sont documentées dans les sources canoniques du dépôt et dans l’ADR locale ;
aucun besoin transverse supplémentaire n’a été établi pour cette analyse.

## Faits établis dans le dépôt

1. `claude-mod/devlog-companion/` ne contient que cinq fichiers suivis :
   `hooks/register.tsx`, `hooks/hooks.json`, `types/index.d.ts`,
   `.claude-plugin/plugin.json` et `install.sh`. Aucun test, manifeste de
   dépendances, script de build ou protocole de validation Claude n’est présent
   dans ce répertoire.
2. Le manifeste annonce le plugin `devlog-companion` version **0.2.0**. La
   Story 0193 décrit la version **0.1.1**, l’augmentation 0.1.1 → 0.2.0 comme
   changement envisagé et conserve l’en-tête `DESIGN APPROUVÉ — AWAITING
   IMPLEMENTATION AUTHORIZATION`. Le code actuel comporte néanmoins les
   fonctionnalités décrites par cette Story. Cette contradiction de statut et
   version prouve que la Story historique n’est pas une baseline fiable à elle
   seule ; elle ne prouve ni la version hôte compatible ni l’acceptation
   runtime.
3. Le code actuel déclare les atomes d’opérations, de trace, de vue et de
   déconnexion (`register.tsx:27–30`), dérive cinq états (`42–49`), ouvre le
   panneau à `session.start` (`161–167`) et observe les appels DevLog via
   `tool.call` (`177–228`). Les tests doivent distinguer cette observation
   de hook d’un dispatch ou d’une couverture wire complète.
4. L’identité d’opération actuelle est construite par
   ``${shortName}-${Date.now()}`` (`register.tsx:177–190`). La copie locale
   d’ADR-084 signale le risque de collision et l’absence de scope explicite
   par session. La baseline doit capturer ces faits comme état à qualifier,
   sans les normaliser ni les corriger implicitement.
5. Le plugin rend un `AbovePrompt` et une `Pane` selon les API hôte, mais aucun
   résultat présent dans le dépôt ne démontre le comportement sur une version
   Claude Desktop/Code précise, la charge de ces contributions, leur isolation
   entre sessions ou la compatibilité du SDK/runtime.
6. `install.sh` copie `.claude-plugin`, `hooks` et `types` sous
   `$HOME/.claude/plugins/marketplaces/devlog/plugins/devlog-companion/`
   (`install.sh:4–9`). Il annonce ensuite le redémarrage de Claude Desktop.
   Il n’expose pas de mode sandbox, de sauvegarde, de procédure de retrait ou
   de vérification d’intégrité. Le lancer pendant cette analyse modifierait
   le profil utilisateur ; il n’a pas été exécuté.
7. Story 0193 définit de nombreux scénarios et un protocole manuel Claude
   Desktop, mais sa version référencée est ancienne et son propre en-tête
   contredit la présence actuelle du code. Les scénarios sont des exigences
   historiques, pas des observations actuelles.
8. L’audit `companion-integration-audit-2026-11-16.md` conclut sur les
   composants Activity Angular/backend et indique que son E2E n’a pas été
   exécuté. Il ne certifie pas le runtime natif Claude Companion et ne
   constitue pas la baseline demandée.
9. Les versions Claude/Claude Mods effectivement supportées, les étapes
   reproductibles de découverte et de retrait, ainsi que les mécanismes
   d’exécution isolée ne sont pas établis par les fichiers examinés.

## Interprétation étayée

Le premier incrément peut raisonnablement produire une **baseline d’évidence**
sans refactoriser le plugin : préciser le contrat observable, ajouter des
fixtures aux fonctions pures si le runtime de test le permet, et définir un
protocole manuel isolé pour les hooks et surfaces hôte. La baseline ne doit pas
convertir les hypothèses historiques en support déclaré.

L’absence de package/test runner dans le répertoire rend l’approche de tests
automatisés inconnue. Il faut examiner les outils effectivement disponibles
avant de choisir une extraction de fonctions ou d’introduire une dépendance.
L’ADR interdit de faire de cette baseline un prétexte pour extraire le Core
partagé ou modifier les adapters.

## Inconnues et décisions qui ne doivent pas être inventées

- Version(s) exacte(s) de Claude Desktop, Claude Code, Claude Mods et SDK à
  considérer comme cible de la baseline.
- Accès à un runtime Claude test dédié, isolable du profil utilisateur, et
  autorisation nécessaire pour tout essai réel.
- Nature de la baseline attendue : inventaire/contrat seulement, tests
  déterministes de logique pure, validation manuelle runtime, ou combinaison
  de ces éléments.
- Sens et portée exacts de « support » lorsque l’hôte, la version ou la
  contribution UI reste expérimentale.
- Publication de l’ADR-084 acceptée dans `main` avant l’approbation finale de
  la Story.

## Recommandation

Approuver cette analyse comme base de préparation d’un Implementation Plan
**uniquement si** le propriétaire accepte les inconnues ci-dessus comme limites
à résoudre dans le plan, et confirme que la copie locale de l’ADR-084 peut être
cité comme source de décision malgré son absence de `origin/main`.

Le plan devra rester borné à la baseline Claude, établir d’abord la
reproductibilité et l’isolation, séparer les tests locaux des preuves runtime,
et ne pas changer le plugin, le package, le Gate ou les contrats. Un essai réel
Claude, une installation utilisateur, l’ajout de dépendances ou l’extraction
partagée nécessiteront une autorisation explicite distincte.

**Décision de workflow :** `ANALYSIS_APPROVED`; l’Implementation Plan
est en attente de son approbation humaine distincte.
