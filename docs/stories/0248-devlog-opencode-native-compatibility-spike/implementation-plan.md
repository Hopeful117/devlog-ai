# Implementation Plan

## Overview

La stratégie est un spike natif et isolé, centré sur un tuple OpenCode
effectivement identifié. Elle sépare l’observation serveur, la projection
session-scoped et la présentation Desktop, sans modifier Claude, DevLog Core,
les contrats MCP/REST ou le Preflight Gate.

Le dépôt ne contient ni SDK OpenCode épinglé, ni plugin natif, ni runtime
disposable déclaré. Le plan ne suppose donc pas que la baseline documentaire
`@opencode/plugin@2.4.0` corresponde à l’environnement à prendre en charge.
Avant tout code dépendant du SDK, il faut identifier le CLI (legacy ou V2),
Desktop, sidecar/runtime et SDK réellement ciblés; avant toute installation ou
exécution hôte, il faut l’autorisation runtime distincte, l’environnement isolé,
la sauvegarde et le retrait vérifiable.

La recommandation reste **Requires clarification** : le tuple runtime précis,
l’environnement jetable et la qualification comportementale du Preflight Gate
restent à établir. L’accès read-only au Gate est confirmé et l’emplacement du
package est clarifié. L’approbation de ce plan ne vaudra pas autorisation
d’expérience runtime.

### Résultats de la phase de clarification (inventaire read-only)

- Le CLI trouvé dans le `PATH` est `opencode 1.18.11` (famille legacy).
- OpenCode Desktop installé et actuellement actif rapporte la version `2.4.1`;
  le processus lance son CLI de service depuis
  `~/.config/ai.opencode.desktop/cli/2.4.1-x64/opencode-cli`, qui rapporte
  `opencode v2.4.1`. Ce pairing est une observation de l’installation présente,
  pas une qualification runtime du plugin.
- La configuration globale OpenCode déclare `@opencode/plugin` en version
  `2.4.0`. Cela établit la dépendance déclarée localement, mais pas encore que
  le Desktop/sidecar l’emploie comme SDK effectivement chargé. La version
  `opencode-pty 0.6.0` observée est un composant auxiliaire et n’est pas assimilée
  au sidecar du plugin.
- Système observé : Linux x86_64, Pop!_OS 24.04. La version Node et le SDK
  effectivement chargé restent à établir au besoin dans une phase autorisée.
- Le fichier local `~/.config/opencode/plugins/devlog-preflight-gate.js` a été
  lu sans le modifier ni l’exécuter. L’inspection confirme des hooks
  `execute.before`/`execute.after`, un état en mémoire borné à 100 sessions et
  un contrôle de handoff avant délégation à `explore`. Elle ne démontre pas que
  le Gate fonctionne sous le contrat V2, ni que sa classification d’achèvement
  couvre les tâches asynchrones; aucune conclusion runtime n’est tirée.
- Le propriétaire autorise la définition des critères d’un environnement
  jetable avant les essais et retient un profil dédié jetable : home/config
  séparé, aucun credential réel, sauvegarde préalable, installation limitée à ce
  profil, puis vérification et suppression des résidus. Aucun environnement n’est
  fourni ou créé dans cette phase. Le chemin `opencode-mod/devlog-companion/` est
  confirmé comme candidat de package pour le plan.

Le propriétaire retient comme cible candidate le tuple relevé : CLI legacy
1.18.11, Desktop/service CLI 2.4.1 et SDK déclaré localement 2.4.0. Le pairing
du SDK effectivement chargé et la compatibilité restent à prouver; ce choix ne
constitue pas une autorisation d’essai. Aucun plugin n’a été installé/chargé et
aucun test runtime ou exercice du Gate n’a été effectué.

## Planned Changes

### 1. Lever les préconditions de compatibilité avant de choisir le SDK

- **Composants :** CLI OpenCode, Desktop, sidecar/runtime, package/plugin SDK et
  configuration du Preflight Gate.
- **Changement prévu :** établir la provenance et les versions exactes à partir
  de métadonnées locales ou d’une vérification explicitement autorisée; séparer
  les versions CLI legacy et V2; comparer les interfaces effectivement présentes
  avec les surfaces décrites par la baseline `2.4.0`.
- **Raison :** l’API GUI est expérimentale dans la documentation consultée et
  les pages `dev` ne prouvent pas l’API du runtime installé.
- **Contrainte :** l’inventaire de versions read-only ci-dessus a été autorisé et
  réalisé. Aucune ouverture de Desktop, installation, chargement de plugin ou
  interaction comportementale avec le Gate n’a eu lieu. Aucun SDK en `latest` ni
  pairing déduit par ressemblance de numéros.
- **Gate d’arrêt :** si un profil isolé et une procédure de restauration ne
  peuvent être établis, conserver les critères runtime `NOT_EVALUATED` et ne pas
  installer. Si le Gate V2 est inefficace, ne pas le contourner; séparer son
  correctif dans une autre Story et bloquer la validation d’un workflow
  effectivement gouverné.

### 2. Fixer l’emplacement et le contrat de packaging du spike

- **Composant :** nouveau package natif OpenCode, indépendant du package Claude.
- **Changement prévu :** retenir `opencode-mod/devlog-companion/` comme
  emplacement du package, avec manifeste et versions épinglées, exports serveur
  et GUI conformes au SDK cible, lockfile et instructions isolées d’installation
  et de retrait.
- **Raison :** aucun emplacement ou contrat de distribution n’existe dans le
  dépôt. Le propriétaire confirme ce chemin candidat pour le package du spike.
- **Contrainte :** le packaging doit être confirmé lors de l’approbation du plan.
  Le package ne doit pas nécessiter Claude, un chargement
  runtime partagé, une migration de configuration utilisateur ou un framework
  générique.
- **Gate d’arrêt :** si l’hôte ne permet pas un profil/configuration jetable
  vérifiable, ne pas installer le package dans la configuration personnelle.

### 3. Construire l’observateur serveur minimal

- **Composant :** entrée plugin serveur du package OpenCode.
- **Changement prévu :** enregistrer seulement les hooks de tool/session et la
  capacité RPC requise par la version choisie; observer les appels MCP DevLog
  sans altérer leurs entrées, sorties, erreurs ou autorisation. Encadrer le
  bookkeeping par des limites de durée et de taille; intercepter les erreurs de
  l’observateur pour qu’elles n’affectent pas l’outil principal.
- **Raison :** le serveur est le point d’observation et de projection retenu par
  ADR-084; l’UI ne doit pas reconstruire l’activité ni appeler DevLog de sa
  propre initiative.
- **Contrainte :** un hook `before` est au plus une tentative observée. Un
  `after` absent ne signifie aucune issue terminale. Ne jamais réécrire un
  résultat pour y ajouter les métadonnées du Companion.

### 4. Implémenter identité, décodage et projection locale déterministe

- **Composant :** logique interne au package OpenCode; aucune extraction vers
  Shared Companion Core.
- **Changement prévu :** représenter séparément l’appel plateforme, l’opération
  MCP, la tâche AI DevLog et la connectivité; décoder défensivement la réponse
  structurée reçue, avec identifiant de tâche seulement si explicitement
  présent. Conserver les erreurs, états pending/incomplets et inconnus sans
  promotion en succès.
- **Raison :** un succès outil ne prouve pas l’achèvement d’une tâche DevLog, et
  les identifiants ne sont pas interchangeables.
- **Contrainte :** scope par instance serveur, projet/workspace, session,
  sous-session et appel lorsque l’hôte expose ces identités. Les chemins et IDs
  restent des sélecteurs, pas une autorisation.
- **Gate d’arrêt :** un champ inconnu, une réponse mal formée ou une corrélation
  ambiguë reste `UNKNOWN`/incomplète; aucun fallback par timestamp seul et
  aucune relation exacte appel↔trace sans contrat qui l’établit.

### 5. Ajouter les fixtures et tests déterministes du package

- **Composant :** tests du nouveau package.
- **Changement prévu :** tester le décodeur, les transitions de projection, les
  erreurs, les entrées mal formées, les IDs présents/absents, la concurrence,
  l’ordre inversé, le changement de session, la rétention bornée, la
  redaction et le non-effet sur les données d’outil.
- **Raison :** aucune suite de tests OpenCode native n’existe dans le dépôt;
  ces fonctions pures sont vérifiables sans présenter un faux harness UI comme
  une preuve runtime.
- **Contrainte :** réutiliser le runner réellement compatible avec le SDK et la
  version Node du package. Ne pas copier une fonction de production dans un
  test parallèle. Les tests unitaires ne qualifient ni le chargement du plugin,
  ni le RPC, ni le rendu Desktop.

### 6. Ajouter RPC et présentation OpenCode Desktop

- **Composants :** contrat RPC interne au package et entrée GUI Desktop.
- **Changement prévu :** échanger une projection bornée et assainie entre
  serveur et UI; présenter un panneau session et un indicateur de présence
  session-scoped (`ComposerPill` ou capacité native équivalente réellement
  disponible). Traiter erreurs RPC, changement de serveur, réponse tardive et
  changement de session sans réafficher un état d’un autre scope.
- **Raison :** Desktop est la cible principale ADR-084; `Panel`,
  `ComposerPill` et `gui-experimental` apparaissent dans les docs actuelles,
  mais leur présence est à confirmer pour le tuple sélectionné.
- **Contrainte :** l’UI n’obtient aucun credential, n’appelle pas de route trace,
  ne devient pas une seconde source d’état et se dégrade indépendamment du
  serveur.
- **Gate d’arrêt :** si le SDK cible n’offre pas les surfaces promises, consigner
  la capacité `UNSUPPORTED` ou `UNKNOWN` et demander une décision; ne pas
  remplacer silencieusement la cible Desktop par une UI TUI.

### 7. Valider le runtime et le comportement gouverné après autorisations

- **Composants :** package serveur, Desktop, MCP DevLog en lecture seule,
  Preflight Gate installé et profil temporaire.
- **Changement prévu :** suivre un protocole versionné : découverte/chargement,
  appels synthétiques ou read-only, before/after, erreur, absence de terminal,
  concurrence, sessions indépendantes, RPC et rendu interactif; vérifier
  installation, redémarrage, retrait et absence de résidus. Tester la présence
  du Gate sans le modifier ni le désactiver.
- **Raison :** aucune preuve statique ne suffit à confirmer les hooks, le
  pairing, l’isolation, l’UI ou la cohabitation.
- **Contrainte :** autorisation runtime explicite distincte, profil jetable,
  état initial et restauration vérifiables, aucune mutation DevLog, aucun
  credential réel, aucune collecte de prompts/payloads/secrets.
- **Issue Gate :** si le Gate ne fonctionne pas sous le contrat V2, l’expérience
  ne démontre pas un workflow gouverné. Un test de charge UI/observation isolé
  ne doit pas être présenté comme une validation de ce workflow.

### 8. Documenter la portée TUI et le résultat

- **Composant :** artefacts de Story 0248.
- **Changement prévu :** rapporter séparément statique, fixtures, runtime,
  combinaison de versions, comportement Desktop, coexistence Gate, packaging,
  retrait et limites TUI/SSH/remote.
- **Raison :** le serveur doit pouvoir être envisagé indépendamment de
  l’interface, mais l’acceptation principale du spike porte sur Desktop.
- **Contrainte :** aucune implémentation TUI dans ce spike; `UNKNOWN`,
  `BLOCKED` et `NOT_EVALUATED` restent explicites. Aucun résultat n’est assimilé
  à l’acceptation humaine de la Story.

## Files to Modify

- `docs/stories/0248-devlog-opencode-native-compatibility-spike/story.md` —
  statut des gates et liens vers les artefacts finaux; pas de changement de
  portée sans retour au gate d’approbation.
- Aucun fichier du Companion Claude, du backend, des contrats MCP/REST, du
  Preflight Gate ou de la configuration globale OpenCode.

## Files to Create

- `docs/stories/0248-devlog-opencode-native-compatibility-spike/implementation-plan.md`
  — présent plan.
- `docs/stories/0248-devlog-opencode-native-compatibility-spike/runtime-validation-protocol.md`
  — protocole avant expérience, après choix/version du tuple.
- `docs/stories/0248-devlog-opencode-native-compatibility-spike/implementation-report.md`
  — résultats, validation, limites et issue DevLog lifecycle.
- **Candidat conditionnel, chemin à approuver :**
  `opencode-mod/devlog-companion/` — package isolé, manifeste/lockfile,
  entrées serveur et GUI, RPC et tests. La liste précise dépend du SDK et des
  exports supportés par le tuple cible; ne pas créer ces fichiers avant les
  préconditions et le gate Implementation.
- Aucun fichier `integrations/companion-core/`, aucun partage de code exécuté
  avec Claude, aucun nouveau contrat MCP/REST et aucun artefact de correction
  du Gate dans cette Story.

## Dependencies

- OpenCode CLI exact : famille legacy ou V2, build/version.
- OpenCode Desktop, sidecar/runtime et plugin SDK effectivement chargés; preuve
  du pairing et source/version du package.
- Environnement isolé permettant une installation projet/package et un retrait
  vérifiables sans toucher au profil utilisateur.
- Autorisation humaine séparée pour l’inventaire runtime lorsque nécessaire,
  l’installation, le lancement de l’hôte et les essais de comportement.
- L’implémentation/configuration locale du Preflight Gate a été inspectée en
  lecture seule. Tout exercice comportemental demeure à faire sous autorisation
  runtime distincte, Gate actif et inchangé.
- SDK exact et environnement Node compatibles avec les exports serveur/GUI;
  versions fixées dans le manifeste et le lockfile, sans `latest`.
- Outil MCP DevLog read-only ou fixture simulée pour les tests; toute interaction
  avec un service réel doit avoir autorisation et scope dédiés.
- Pas de changement requis au backend, à la base de données, aux contrats DevLog
  ni au package Claude.

## Test Plan

### Tests déterministes du package

- Décodeur : JSON/enveloppe structurée valide, erreur MCP, `isError`,
  identifiant absent, invalide ou présent, réponse pending et incomplète.
- Projection : tentative distincte de dispatch/résultat; absence de terminal
  donne incomplet/inconnu; l’état d’une tâche DevLog ne suit pas mécaniquement
  le statut de l’outil OpenCode.
- Isolation/concurrence : IDs identiques entre sessions, appels simultanés,
  réponses hors ordre, sessions enfants, bascule de workspace, réponse RPC
  tardive, perte/reconnexion du serveur.
- Non-interférence : les hooks ne mutent jamais l’entrée ou le résultat
  principal; exceptions, timeout ou déconnexion de l’observateur ne changent
  pas l’exécution outil.
- Sécurité : limites d’historique, redaction, absence de credentials, prompts,
  arguments sensibles, sorties brutes ou payloads dans projection/logs.
- RPC : validation schema, erreurs déclarées, scope explicite, abonnement et
  désabonnement au démontage, absence de diffusion cross-session.

### Vérifications de build/package

Après création autorisée du package et selon les scripts réellement définis :

- `npm ci` avec lockfile, si npm est retenu par le SDK ciblé;
- typecheck/build du package serveur et de l’entrée GUI expérimentale;
- tests déterministes du package;
- `npm pack --dry-run` et inspection de l’archive pour confirmer les exports et
  dépendances embarqués.

Les commandes exactes doivent correspondre aux scripts du package et à la
version SDK sélectionnée; aucune commande ci-dessus n’a été exécutée. Les tests
Java/Maven ne sont pas applicables si le backend/MCP reste inchangé.

### Expériences runtime — uniquement après autorisation distincte

- enregistrer versions, provenance, pairing et empreintes du package testé;
- charger le plugin uniquement dans le profil temporaire, sous le Gate actif;
- observer une opération read-only/synthétique et enregistrer distinctement
  tentative, dispatch prouvé, résultat, erreur, interruption et absence de
  terminaison;
- vérifier réponses structurées DevLog avec statut outil et statut tâche
  affichés comme faits indépendants;
- démontrer deux sessions concurrentes sans fuite d’état, puis bascule,
  reconnexion et réponse tardive;
- rendre et interagir avec panneau Desktop et indicateur de présence sur le
  tuple précis;
- tester la cohabitation Gate sans déactivation; qualifier à part le workflow
  réellement gouverné;
- retirer le plugin, redémarrer le profil isolé et vérifier absence de fichiers
  ou configuration résiduels.

Toute expérience non exécutée ou bloquée reste `NOT_EVALUATED`/`BLOCKED`. Aucun
résultat de documentation, typecheck ou test unitaire ne remplace la preuve
runtime.

## Risks

| Risque | Conséquence | Réduction prévue |
|---|---|---|
| Mauvais tuple CLI/Desktop/sidecar/SDK | Plan et package peuvent viser une API non chargée en production. | N’épingler le SDK qu’après inventaire de versions autorisé; rapporter la provenance et ne soutenir que le tuple testé. |
| Hooks incomplets ou modifiables | Une observation peut être prise à tort pour un dispatch, ou interférer avec l’outil. | Tester les hooks ciblés; ne muter aucune entrée/sortie; erreur observateur best-effort; classifier absence de terminal comme inconnue. |
| Gate V2 inefficace ou source inaccessible | Fausse validation de workflow gouverné ou blocage d’acceptation. | Gate actif et inchangé; inspecter séparément; stopper la revendication gouvernée et déposer une Story corrective distincte si confirmé. |
| Fuite cross-session via état serveur/UI | Révélation de projet/activité et affichage d’un résultat périmé. | Stores explicitement keyed par scopes, tests de concurrence et changements de session, RPC limité, aucun broadcast non autorisé. |
| Réponse structurée confondue avec complétion DevLog | Présence `READY` trompeuse. | Décodage explicite du task ID/statut; 4 faits indépendants; unknown/pending/incomplete conservés. |
| Profil ou procédure de désinstallation non isolable | Modification irréversible de l’environnement utilisateur. | Ne pas installer; exiger profil jetable, sauvegarde, rollback et preuve de retrait avant les essais. |
| API GUI expérimentale absente dans la cible | Pas de panneau/pill malgré les docs actuelles. | Vérifier version/exports; arrêter et rapporter la limitation au lieu de remplacer silencieusement la cible. |
| Packaging partagé force une dépendance Claude | Régression ou couplage contre ADR-084. | Package OpenCode indépendant; analyse de packaging seulement; aucune extraction ni changement Claude. |
| Tests simulés donnent une fausse assurance | Le harness ne couvre pas le host réel. | Distinguer fixtures, intégration, rendu Desktop et lifecycle runtime dans le rapport et la matrice. |

**Clarifications et gates :**

1. L’inventaire read-only est autorisé et réalisé. Le propriétaire retient comme
   cible candidate le tuple (CLI legacy 1.18.11, Desktop/service CLI 2.4.1,
   dépendance locale SDK 2.4.0, Linux x86_64); le pairing SDK/sidecar et sa
   compatibilité restent à établir avant toute conclusion runtime.
2. Le Gate local a été inspecté sans modification. Son efficacité V2 reste
   `NOT_EVALUATED`; tout essai comportemental exige l’autorisation runtime
   distincte.
3. L’emplacement `opencode-mod/devlog-companion/` est confirmé pour le plan.
4. Les critères retenus sont un profil dédié jetable (home/config séparé,
   credentials réels exclus, sauvegarde préalable, installation confinée,
   vérification et suppression des résidus). Sa création/fourniture et son usage
   ne sont pas autorisés par cette clarification.
5. L’approbation explicite de ce plan reste requise avant implementation, et
   toute installation ou expérience runtime conserve son gate d’autorisation
   distinct.

## Validation Checklist

- [ ] Le tuple exact (CLI, Desktop, sidecar/runtime, SDK, OS) et sa provenance
      sont consignés; CLI legacy/V2 ne sont pas confondus.
- [ ] L’écart à la référence auditée 2.4.0 est fondé sur la version réelle, pas
      sur la documentation `dev`.
- [ ] Le chemin/package et la méthode d’installation jetable sont approuvés,
      épinglés et retirable sans effet sur Claude ou configuration utilisateur.
- [ ] L’observateur est passif, borné, best-effort et n’altère pas l’outil.
- [ ] Tentative, dispatch, fin d’outil, interruption et inconnu sont distincts.
- [ ] Le statut/ID de tâche DevLog est décodé seulement à partir de preuve
      explicite et séparé du statut OpenCode.
- [ ] Opérations concurrentes, réponses hors ordre et sessions/workspaces sont
      isolés; les résultats tardifs ne traversent pas de scope.
- [ ] Panneau Desktop et indicateur de présence sont rendus et interactifs sur
      le tuple épinglé.
- [ ] Le RPC reste borné, schema-validated, scoped et sans credential côté UI.
- [ ] Le Gate demeure actif et inchangé; toute inefficacité est un blocker de
      workflow gouverné, avec correction proposée hors de 0248.
- [ ] Tests unitaires/build/package couvrent les critères déterministes; leurs
      résultats ne sont pas présentés comme preuve runtime.
- [ ] Installation/retrait et état post-cleanup sont vérifiés dans le profil
      jetable après autorisation distincte.
- [ ] Le rapport conserve `UNKNOWN`, `NOT_EVALUATED`, `BLOCKED` et les limites
      TUI/remote; aucune compatibilité non testée n’est revendiquée.
- [ ] Aucun fichier Claude, backend, contrat MCP/REST, Gate ou framework
      générique n’a été modifié.

## Recommendation

### Requires clarification

L’inventaire et l’inspection read-only sont réalisés, le tuple candidat est
retenu, les critères d’isolation sont définis et l’emplacement du package est
confirmé. Le SDK réellement chargé et l’efficacité du Gate restent
`NOT_EVALUATED`; aucun environnement n’a été créé ou utilisé. L’approbation de
ce document ne remplacera pas l’autorisation distincte d’installation et
d’expérience runtime.

## Approval Required

Implementation Plan completed and explicitly approved by the human owner.

Implementation was explicitly authorized after plan approval and is recorded in
the linked Implementation Report. Installation and runtime experiments remain
blocked pending their own separate authorization.
