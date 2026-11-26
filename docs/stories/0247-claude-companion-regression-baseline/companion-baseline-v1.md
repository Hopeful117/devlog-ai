# Claude Companion — baseline d’évidence v1

## Portée et statut

- Story : [0247](story.md)
- Décision de référence : ADR-084, acceptée par le propriétaire, mais présente
  uniquement comme fichier local non suivi dans cette copie de travail.
- Révision de référence : `origin/main` à
  `7c577230d9a6651b411d77fa93e8c7a4bc994e12` (26 novembre 2026).
- Les cinq fichiers de `claude-mod/devlog-companion/` examinés ici sont
  identiques à ceux de cette révision. HEAD de la copie de travail :
  `7ecc16806e3dea3c412f98139952ed0cfabb6f54`.
- Version déclarée du plugin : `0.2.0` (`.claude-plugin/plugin.json`).
- Versions Claude Desktop/Code, Claude Mods et SDK : **UNKNOWN**. Aucun
  runtime n’a été testé ; la version du plugin ne prouve pas la compatibilité
  d’une combinaison hôte/SDK.
- Aucun changement n’a été apporté au code du plugin.

### Sens des statuts

- `SUPPORTED` : protocole reproductible exécuté avec versions épinglées et
  résultat positif ; **aucune capacité n’atteint ce statut dans cette baseline**.
- `PARTIAL` : une partie du comportement est visible dans le code ou la
  configuration, mais le contrat runtime, l’isolation ou une partie du flux
  n’est pas prouvée.
- `UNSUPPORTED` : limitation explicitement établie par le contrat ou le code
  examiné ; ce statut n’est pas utilisé pour transformer une absence de test en
  preuve d’absence.
- `UNKNOWN` : la source disponible ne permet pas de conclure ou la validation
  runtime n’a pas été exécutée.
- `NOT_EVALUATED` : étape de validation explicitement non exécutée.

## Matrice de compatibilité et d’évidence

| Surface / comportement | Évidence statique à la révision de référence | Statut | Validation runtime |
|---|---|---|---|
| Découverte du plugin | `hooks/hooks.json` référence `./register.tsx`; le manifeste déclare `devlog-companion` v0.2.0. | `PARTIAL` | `UNKNOWN` — découverte par un hôte non vérifiée. |
| Installation | `install.sh` copie `.claude-plugin`, `hooks` et `types` vers `$HOME/.claude/plugins/marketplaces/devlog/plugins/devlog-companion/`. `mkdir -p` et `cp -r` opèrent directement sur cette destination. | `PARTIAL` | `NOT_EVALUATED` — installateur non exécuté ; destination isolée, sauvegarde et coexistence inconnues. |
| Mise à jour / retour arrière | L’installateur recopie les répertoires sans versionnement, sauvegarde ou procédure de rollback explicite. | `PARTIAL` | `NOT_EVALUATED` — aucune mise à jour testée. |
| Retrait | Aucun script ou procédure de désinstallation ne figure parmi les cinq fichiers du plugin. | `UNKNOWN` | `NOT_EVALUATED` — le retrait manuel et ses effets sur les données/hôte n’ont pas été testés. |
| Cycle `session.start` | Le hook appelle `next`, enregistre la commande, remet la vue à `now` et demande l’ouverture du panneau (`register.tsx:161–167`). | `PARTIAL` | `UNKNOWN` — événement, ordre, disponibilité UI et auto-ouverture non observés. |
| Toggle de commande | `command.run` inspecte les panneaux et ouvre/ferme `devlog-companion` (`169–175`). | `PARTIAL` | `UNKNOWN` — enregistrement et interaction non observés. |
| Observation DevLog | `tool.call` filtre le préfixe `mcp__devlog__`, crée une opération `running`, appelle `next`, puis classe le résultat/exception (`177–228`). | `PARTIAL` | `UNKNOWN` — ceci ne prouve ni dispatch wire complet, ni réception de tous les événements, ni sémantique de continuation du runtime. |
| États de présence | `deriveState` implémente `DISCONNECTED`, `WORKING`, `IDLE`, `ERROR`, `READY` et une fenêtre de 10 minutes, à partir de la liste et de `startedAt` (`42–50`). | `PARTIAL` | `NOT_EVALUATED` — aucun harness testable du module ni hôte disponible. L’horodatage est celui du démarrage pour calculer READY. |
| Concurrence et identité d’opération | Les entrées sont ajoutées en tête et limitées à 20 ; identifiant `${shortName}-${Date.now()}` (`177–190`). | `PARTIAL` | `UNKNOWN` — collisions d’appels identiques durant la même milliseconde et ordre de fin concurrent non évalués. |
| Isolation session/projet | Les atomes portent le nom du plugin ; les opérations peuvent inclure `projectId`, mais l’identité d’opération ne contient pas session/projet et aucun teardown n’est déclaré dans le hook manifest. | `UNKNOWN` | `NOT_EVALUATED` — isolement entre sessions, fenêtres, projets et reprise inconnu. |
| AbovePrompt desktop | Le rendu tente `Svg` hors terminal et produit un bandeau, avec fallback texte (`232–267`). | `PARTIAL` | `UNKNOWN` — chargement, taille, accessibilité et rendu non observés. |
| Pane et vues Now/Context/History | Une pane est rendue avec trois onglets et quatre sections de trace (`271–676`). | `PARTIAL` | `UNKNOWN` — interaction, persistance et navigation réelles non observées. |
| Fetch du Context Inspector | Le code lit un secret depuis un chemin de configuration local, passe des paramètres d’authentification en arguments à `curl`, puis appelle une route REST de trace (`295–335`). | `PARTIAL` | `NOT_EVALUATED` — aucune requête exécutée. **Risque ADR-084 à traiter séparément** : cette plomberie de credentials n’est pas une conception de référence et n’est pas validée par cette baseline. |
| Gestion des erreurs / déconnexion | Les résultats `isError` et exceptions marquent l’opération échouée ; certains échecs de fetch contenant des marqueurs textuels changent `isDisconn` (`192–226`, `329–334`). | `PARTIAL` | `UNKNOWN` — couverture de cas, exactitude de la classification réseau et non-propagation au host non vérifiées. |
| Surface terminal | Un fallback texte est présent lorsque `Svg` n’est pas exposé ou que `surface === 'terminal'`. | `PARTIAL` | `UNKNOWN` — hôte et primitives `Box`/`Text`/`Button` non testés. |
| Réduction des animations | Des règles SVG masquent les éléments animés pour `prefers-reduced-motion: reduce` (`117–118`, `133–134`). | `PARTIAL` | `UNKNOWN` — prise en compte effective par l’hôte non observée. |

## Divergences historiques

- Le manifeste actuel indique v0.2.0 ; Story 0193 décrit le prototype v0.1.1
  et garde l’en-tête « DESIGN APPROUVÉ — AWAITING IMPLEMENTATION
  AUTHORIZATION ». Cet en-tête historique n’est ni preuve de l’état courant,
  ni preuve de validation runtime ou d’acceptation.
- Story 0193 fournit des comportements souhaités et des tests manuels cibles,
  pas des résultats observés. Son affirmation qu’aucun framework de tests Mods
  n’était disponible est historique ; dans la copie actuelle, aucun
  `package.json`, test runner, lockfile, SDK ou test n’est présent dans le
  répertoire du plugin.
- L’ADR-084 identifie notamment la clé d’opération basée sur le temps, l’absence
  de scope explicite par session et le passage de credentials en arguments
  subprocess. Ces constats sont consignés, mais aucune correction n’est
  autorisée par cette Story.

## Conclusion de baseline

Cette version constitue une **baseline statique versionnée**, pas une
certification de support. Les seules conclusions positives sont les éléments
présents dans le code/configuration aux références indiquées. Toute assertion
sur Claude Desktop/Code, Claude Mods, rendu, installation réelle, isolation,
dispatch complet ou récupération reste `UNKNOWN` ou `NOT_EVALUATED`.

Le module n’a pas de test runner ni de SDK/runtime testable répertorié. Ajouter
un harnais ou extraire le reducer de `register.tsx` nécessiterait de prouver le
contrat de chargement du plugin ; cette preuve ne peut pas être obtenue ici sans
runtime/harness. Aucun test simulant une copie de `deriveState` n’a été créé,
afin d’éviter une fausse assurance.

**Prochaine étape recommandée (unique)** : une Story distincte pour qualifier
les versions et le packaging Claude dans un profil isolé, puis décider sur
preuves d’un harness compatible et des actions séparées concernant les risques
de sécurité/isolation relevés par l’ADR-084. Aucun de ces travaux n’est inclus
ou implicitement autorisé ici.
