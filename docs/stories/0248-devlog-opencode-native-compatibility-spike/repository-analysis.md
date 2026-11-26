# Story 0248 — Repository Analysis

## Statut du workflow

**Repository Analysis terminée — attente d’approbation humaine.**

Cette analyse ne constitue ni une approbation de plan, ni une autorisation
d’installation, d’implémentation ou d’expérience runtime. L’instruction du
propriétaire « commence le workflow de story » autorise cette étape du workflow
uniquement. L’Implementation Plan ne peut commencer qu’après approbation
explicite de la présente version de l’analyse.

## Révision et état du dépôt

- Révision analysée : `f83fbd6cf260d7cdef1d74ee12b991d595b639d2`
  (`origin/main` lors de la vérification).
- Worktree : `/home/ludo/workspace/devlog-ai-story-0248`.
- Branche : `story/0248-opencode-native-compatibility-spike`, créée depuis
  `origin/main`; HEAD était égal à `origin/main` avant les artefacts de cette
  Story.
- État : ADR-084 et Story 0248 sont des fichiers non suivis préparés dans ce
  worktree; aucun autre changement local n’y a été observé.
- Worktree source `/home/ludo/workspace/devlog-ai` : détaché sur
  `7ecc1680…`, en retard sur `origin/main`, avec modifications préexistantes de
  `tools/devlog_mcp/runtime.py`, `tools/devlog_mcp/tests/test_runtime.py` et
  dossiers locaux Stories 0246/0247. Ils n’ont pas été touchés.

## Préflight DevLog et autres sources contextuelles

- `devlog_story_agent` appelé pour `projectSlug=devlog-ai`, sans `storyId`
  (aucun UUID DevLog 0248 vérifié), clé d’idempotence
  `story-0248-repository-analysis-devlog-context-2026-11-26`.
- Tâche/snapshot : `d2560e89-6170-4191-a65c-56067b8340d8`, statut
  `COMPLETED`, révision résolue `f83fbd6c…`.
- Fraîcheur : `PARTIALLY_FRESH / STALE`; révision de source cohérente, mais
  baseline de contexte antérieure (`7ecc1680…`), warnings de budget,
  enrichissement tronqué et ignoré; 60 éléments sélectionnés sur 1 134,
  contexte tronqué, confiance de synthèse `LOW`.
- Le résultat n’a pas livré le contenu d’ADR-084, de Story 0248, du module
  OpenCode ou des agents demandés. Il fournit seulement des rappels bornés sur
  Story 0225 et 0247, et ne détermine ni l’API OpenCode cible ni le Gate.
  Il sert à prioriser la vérification, pas à établir les faits actuels.
- `search_project_history` avec les termes « OpenCode plugin Companion desktop
  preflight gate » a renvoyé zéro correspondance. C’est une recherche lexicale
  bornée, pas une preuve d’absence d’historique.
- `resolve_engineering_story` pour le numéro 248 a répondu que la Story n’est
  pas enregistrée dans le projet DevLog. Aucun UUID n’a été inventé.
- **`DEVLOG_LIFECYCLE_ERROR`** — le `TOOLS.md` de ce worktree donne le slug
  `devlog-ai`, mais pas de base URL ni d’UUID projet; il n’existe pas de mapping
  exact chemin canonique requis par le workflow, et le script local
  `scripts/devlog-story.mjs` est absent. L’enregistrement n’a pas été tenté
  avec des valeurs devinées. Cet effet de bord best-effort n’empêche pas
  l’analyse.
- Vault : consultation sémantique tentée, mais l’index est en construction
  (`0/23` fichiers indexés). Aucun contenu de vault n’a été utilisé; l’accès
  manquant n’est pas bloquant.
- Documentation externe Context7 : les pages OpenCode courantes consultées
  décrivent des hooks serveur, RPC typé, contributions GUI (`Panel`,
  `ComposerPill`) et packaging GUI expérimental. Les sources pointent sur la
  branche `dev`, non sur le tag `v2.4.0`; elles sont des pistes documentaires,
  pas des preuves de compatibilité avec la version cible.

## Compréhension de la Story

Story 0248 demande une étude et un spike natif OpenCode isolé, orienté Desktop,
capable d’observer passivement les outils MCP DevLog, de corréler ce qui est
réellement observable, de décoder les réponses structurées et de présenter une
projection correctement isolée via le serveur et l’interface native.

Ses contraintes majeures sont cohérentes avec ADR-084 : ne modifier ni le
Companion Claude ni Core/contrats MCP/REST; ne pas extraire de Shared Companion
Core; ne pas ajouter d’authentification; ne pas simuler d’événements absents;
ne pas considérer une réussite OpenCode comme une réussite de tâche DevLog; ne
pas contourner le Preflight Gate. Une correction du Gate, si nécessaire, est
séparée et constitue un blocker pour valider un workflow gouverné.

La Story distingue correctement analyse, plan, autorisation runtime,
implémentation, rapport et acceptation. Le présent document satisfait
uniquement l’étape Repository Analysis.

## Propriété fonctionnelle et frontières

- Le plugin natif, s’il est autorisé ultérieurement, appartiendrait à un
  adaptateur client OpenCode. Aucun module de ce type n’existe dans l’arbre
  analysé.
- L’OpenCode MCP launcher et son serveur sont le chemin d’intégration DevLog
  existant; l’investigation
  `docs/investigations/devlog-mcp-opencode-chatgpt-oauth-stability.md` décrit
  un launcher stdio local extérieur au dépôt, mais elle ne certifie pas la
  configuration runtime actuellement installée.
- Java/Core reste l’autorité pour le scope projet, l’autorisation, le contexte,
  les identifiants et le statut de la tâche DevLog. Le plugin ne peut ni créer
  de faits métier ni autoriser une lecture de trace.
- Le transport MCP, l’identité technique OpenCode, une éventuelle délégation
  humaine et le credential fournisseur AI sont des frontières distinctes.
  L’interface ne doit recevoir ni bearer ni credential.
- L’agent de préflight et la gouvernance sont indépendants de la présence UI.
  Le dépôt contient des instructions d’agents OpenCode, mais pas le code du
  plugin/matcher exécuté dans le runtime OpenCode utilisateur.

## État actuel vérifié dans le dépôt

### Companion et surfaces OpenCode

- `opencode-mod/` est absent.
- Aucun manifeste ou verrou du dépôt ne déclare `@opencode/plugin`; aucune
  référence locale à `ComposerPill`, `gui-experimental` ou au RPC plugin
  serveur/GUI n’a été trouvée en dehors des documents préparés et d’ADR-084.
- `.opencode/` contient seulement `.gitignore`,
  `agent/devlog-readonly-pilot.md` et `agent/explore.md`. Ces fichiers
  décrivent des rôles de workflow; ils ne constituent pas une extension native
  serveur ou Desktop, ni un test de rendu.
- Il n’y a pas de dossier `docs/workflow/` ni de `story-template.md` à la
  localisation conventionnelle consultée. Les Stories récentes 0246/0247 et les
  instructions racine fournissent les conventions effectives.
- Le répertoire de plugin Claude reste `claude-mod/devlog-companion/`. Story
  0247 est une baseline statique; aucun résultat runtime Claude n’est disponible
  et son périmètre interdit toute modification de ce plugin dans 0248.

### Intégration DevLog existante et couverture

- `DevLogStoryAgentTool` (`mcp-server/.../tool/DevLogStoryAgentTool.java`)
  expose `devlog_story_agent` comme adaptateur MCP borné et read-only. Il
  requiert un idempotency key, utilise un principal authentifié, transmet une
  requête au client Core et sérialise le résultat structuré en JSON.
- L’outil implémente une soumission/polling bornés, conserve une identité de
  tâche pour reprise et enregistre l’observabilité best-effort. C’est une
  preuve du contrat serveur au commit analysé, pas une preuve de la manière
  dont OpenCode Desktop affiche ou termine l’appel.
- `DevLogStoryAgentToolTest` couvre l’adaptateur Java; les tests du module
  `mcp-server` et les évaluations Story 0210 couvrent des portions de l’intégration
  DevLog. Ils ne testent pas de plugin OpenCode, ses hooks UI/RPC, les sessions
  ou le Desktop.
- Story 0225 prévoit une analyse bornée du chemin OpenCode → MCP → Core → AI
  Engine → résultat et conserve les issues préflight-rejected, timeout et
  provider-incomplete séparées. Son statut et son périmètre ne constituent pas
  une autorisation de construire le plugin de cette Story.
- Le rapport 0210 documente des validations du protocole d’évaluation et une
  campagne OAuth partielle (13 `SUCCESS`, 1 `NOT_EVALUATED`, 1
  `INVALID_CAPTURE` pour le bras Story Agent). Cette preuve concerne l’évaluation
  du flux DevLog, pas une combinaison CLI/Desktop/plugin SDK.
- Écart de fraîcheur dans Story 0247 : son `implementation-report.md` conserve
  encore un état pré-merge (« merge non effectué », PR en attente), alors que le
  Git distant courant est `f83fbd6c`, le merge commit de la PR #248. Le commit
  distant fait autorité pour la livraison physique; le rapport est historique
  et n’a pas été corrigé dans cette Story.

### Audit OpenCode existant

- Aucun rapport autonome de compatibilité plugin/Desktop OpenCode n’a été
  trouvé parmi les artefacts canoniques du dépôt.
- Le résumé disponible est dans ADR-084 : audit statique `CONDITIONAL GO`,
  baseline `@opencode/plugin@2.4.0`, CLI V2 `2.4.0`, CLI legacy `1.18.11` et
  Desktop `2.4.1`. Le pairing Desktop/sidecar exact, RPC live, présentation,
  lecture de trace autorisée et compatibilité effective du runtime n’y sont pas
  établis.
- Les outils de documentation OpenCode actuels consultés ne correspondent pas
  à une version épinglée et peuvent avoir évolué depuis cette baseline.
  L’absence de manifeste local signifie qu’aucune différence installée/2.4.0
  ne peut être calculée à ce stade.

## Décisions et contraintes gouvernantes

- **ADR-084** : le texte local accepté est maintenant présent dans ce worktree
  comme artefact non suivi, mais pas encore publié sur `main`. Il prescrit les
  adaptateurs natifs, Desktop en cible principale, TUI secondaire, projection
  serveur indépendante de l’UI, isolation, observations honnêtes, distribution
  indépendante et preuves versionnées. Les chemins de plugin et de Core cités
  comme candidats ne sont pas des chemins approuvés.
- **ADR-067** : DevLog Core gouverne la capacité, le contexte, l’autorisation et
  l’acceptation; la couche AI reste probabiliste et non-trusted. Pas de framework
  générique ou de contexte reconstruit hors Core.
- **ADR-077/078** : une authentification de transport ne vaut pas identité
  humaine ou autorisation; l’agent OpenCode, l’agent DevLog et l’utilisateur
  sont distincts; l’interface/plugin ne reçoit pas le credential fournisseur.
- **ADR-081** : provenance d’erreur et politique de reprise sont structurées;
  pas de retry/rejeu implicite sur un résultat ambigu.
- **ADR-063/079** : préserver le scope et les contrats canoniques; distinguer
  sélection, projection, résultat fournisseur incomplet et issue finale.
- **Stories 0172/0213** : la gestion runtime MCP est bornée, isolée et
  diagnostiquée sans secret; les erreurs d’observabilité/Companion ne changent
  pas le résultat principal.
- **Story 0247** : aucune compatibilité runtime Claude n’est prouvée. La
  baseline interdit d’utiliser l’implémentation Claude comme preuve par analogie
  et interdit de la modifier dans cette Story.

## Preflight Gate — constat et blocker

ADR-084 rapporte un défaut de compatibilité source : le matcher `explore` du
Gate installé emploierait des alias d’arguments legacy alors que l’outil natif
`subagent` V2 utilise `agent`; son classifieur d’achèvement serait permissif.
ADR-084 qualifie cela de finding statique, pas de verdict live.

Le code/configuration du Gate installé n’est pas présent dans ce dépôt et sa
configuration OpenCode locale est extérieure au dépôt. Les fichiers
`.opencode/agent/*.md` observés sont des consignes du workflow, pas
l’implémentation du matcher. Il est donc impossible de confirmer ici si le Gate
fonctionne avec le contrat V2 réel. Une validation d’un workflow réellement
gouverné est bloquée jusqu’à inspection/test indépendant et autorisé du Gate.
Si le défaut est confirmé, sa correction exige une Story séparée; 0248 ne doit
ni la corriger ni le contourner.

## Dépendances, risques et informations manquantes

1. **Versions effectives inconnues** : aucun binaire/version CLI, Desktop,
   sidecar/runtime, SDK ou appairage n’a été vérifié. L’exécuter ou lancer
   l’hôte dépasserait le présent examen; toute vérification runtime attend une
   autorisation distincte.
2. **Installation isolée non établie** : pas de manifeste/plugin à charger, de
   profil jetable défini, ni de procédure vérifiée d’installation, de mise à
   jour et de retrait.
3. **Surfaces desktop expérimentales** : les docs actuelles montrent
   `Panel`, `ComposerPill`, RPC typé et un export GUI expérimental, mais sur la
   branche `dev`; API, flags, permissions, rendu et packaging de la cible
   installée restent inconnus.
4. **Couverture lifecycle partielle** : `execute.before` documente une
   tentative et peut modifier les entrées; `execute.after` peut modifier les
   résultats selon les docs actuelles. Le caractère passif doit être démontré
   par le prototype et ses tests, pas déduit de l’existence d’un hook.
   Couverture d’outils imbriqués, annulations, tâche de fond, disparition du
   serveur et absence d’événement terminal reste inconnue pour le runtime ciblé.
5. **Gate externe** : implémentation effective et contrat CLI V2 non observables
   dans le dépôt; blocker pour la validation du flux gouverné.
6. **Packaging partagé** : l’installateur Claude existant n’a pas de contrat de
   chargement partagé vérifié. Une étude de faisabilité est possible, mais une
   extraction Common Core ne l’est pas dans 0248.
7. **Runtime trace** : aucun droit de lecture de trace ne découle d’un RPC ou
   d’un task ID; aucune authentification nouvelle ou requête trace ne peut être
   supposée.
8. **Contexte DevLog** : la projection stale/tronquée n’apporte pas la
   connaissance historique attendue; conclusions ci-dessus reposent sur le
   dépôt à `f83fbd6c` et les textes locaux de l’ADR/Story, pas sur un insight
   DevLog validé.

## Tests et validation pertinents

- Aucun test, build, installation ni lancement OpenCode n’a été effectué dans
  cette étape d’analyse.
- Les tests Java de `DevLogStoryAgentTool` valident l’adaptateur MCP existant,
  pas le plugin natif.
- Les évaluations de Story 0210 valident des aspects de la chaîne DevLog, pas le
  Desktop, le SDK ni les événements de lifecycle OpenCode.
- Les commandes de tests ou d’intégration du futur plugin relèvent du plan
  d’implémentation après approbation de cette analyse. Les validations réelles
  nécessitent l’autorisation runtime séparée prévue par la Story.

## Conclusion et recommandation

**Analyse prête pour approbation; planification possible avec limites explicites.**
Le dépôt permet de définir les frontières et d’identifier un prototype
potentiel, mais ne permet pas de fixer une combinaison OpenCode supportée, de
garantir la présentation Desktop, de démontrer l’isolation ni de certifier le
Gate. Le plan ultérieur devra commencer par établir les versions exactes dans
un environnement autorisé, garder les surfaces non prouvées `UNKNOWN`, prévoir
l’installation/retrait isolés et traiter le Gate comme une validation
indépendante et bloquante pour tout workflow gouverné.

Aucun conflit n’est constaté entre la portée de la Story et ADR-084. Aucun
changement de code, contrat backend, configuration OpenCode, Companion Claude
ou Gate n’est recommandé par cette analyse.

## Vault Outcome

- Vault consulté : non; recherche sémantique indisponible pendant la construction
  de l’index (`0/23` fichiers indexés).
- Outcome pour cette analyse : **no vault action**.
- Rationale : les décisions d’architecture propres au projet et l’état présent
  sont établis par ADR-084, les Stories et le dépôt; aucune conclusion ne dépend
  d’un contexte transversal du vault.
