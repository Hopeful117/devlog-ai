# Story 0193 — DevLog Companion: Persistent Presence & UX V1

## Status

DESIGN APPROUVÉ — AWAITING IMPLEMENTATION AUTHORIZATION

---

## Problème

Le DevLog Companion prototype (plugin `devlog-companion@devlog` v0.1.1) est fonctionnellement opérationnel : il intercepte les appels MCP DevLog, enregistre les opérations, affiche un band AbovePrompt et un Context Inspector. Mais son expérience produit présente deux limitations majeures :

**1. Accessibilité.** L'utilisateur doit exécuter `/devlog-companion` à chaque nouvelle session pour ouvrir le panneau. Aucune présence n'est visible au démarrage. L'outil doit être pensé à voix haute pour exister.

**2. Contenu et identité.** Le panneau s'ouvre sur une liste d'activités techniques. Il n'y a pas de vue d'accueil synthétique, pas d'état visible de DevLog, pas d'identité visuelle reconnaissable. Le band AbovePrompt disparaît quand aucune opération n'a eu lieu, privant l'utilisateur de tout repère.

Ces deux limitations réduisent DevLog à un outil de diagnostic qu'il faut penser à consulter, plutôt qu'à une présence intégrée à l'environnement de travail.

**Besoin fonctionnel :**

> Lorsque j'ouvre Claude Desktop et commence à travailler, DevLog doit être accessible sans commande, visible sans effort, et m'indiquer immédiatement ce qu'il a fait récemment.

---

## Objectif fonctionnel

Transformer le plugin `devlog-companion@devlog` pour qu'il devienne une **présence numérique discrète, expressive et naturellement intégrée** à Claude Desktop :

1. **Présence persistante** — le Companion est accessible dès l'ouverture de session, sans commande répétée.
2. **Identité visuelle** — un avatar géométrique reconnaissable réagit aux états réels de DevLog.
3. **Vue d'accueil** — le panneau présente d'abord les informations les plus utiles (état, dernier contexte, sources).
4. **Navigation structurée** — trois espaces distincts : Now, Context, History.
5. **États vides soignés** — chaque état du cycle de vie est représenté clairement.

Ce Story modifie exclusivement le **plugin Claude Mods** (`register.tsx`). Aucun changement backend n'est requis.

---

## Governing Artifacts

- **Plugin Claude Mods** `devlog-companion@devlog` v0.1.1 — implémentation de référence à modifier
- Story 0185–0192 — chaîne MCP Activity Companion (fondations des données disponibles)
- Audit technique `docs/investigations/companion-integration-audit-2026-11-16.md` — état validé du système

**Aucun ADR backend n'est concerné.** Ce Story opère exclusivement dans la couche de présentation (Claude Mods plugin). Il ne modifie pas l'architecture Java Core, Python AI Engine, ni les contrats MCP.

---

## Périmètre

### Dans le périmètre

- Modification de `register.tsx` : auto-open, band always-on, avatar, 5 états, vue "Now", navigation 3 onglets
- Modification de `types/index.d.ts` : ajout du champ `view` dans `PluginState`
- Bump de version dans `.claude-plugin/plugin.json` (0.1.1 → 0.2.0)
- Tests manuels dans Claude Desktop (aucun framework de test disponible pour les mods)

### Hors périmètre

- Aucun changement backend (Java Core, Python AI Engine, MCP Server)
- Aucun nouveau endpoint REST ni contrat MCP
- Pas de MCP App (`companion-app.html`) — cette couche reste inchangée
- Pas de nouvelle vue Angular (frontend indépendant)
- Pas de polling actif — le plugin reste event-driven
- Qualité des réponses du DevLog Agent — hors scope de ce Story
- Animations Three.js ou moteur 3D — hors scope V1

---

## Architecture

### Fichiers modifiés

| Fichier | Nature | Description |
|---------|--------|-------------|
| `~/.claude/plugins/marketplaces/devlog/plugins/devlog-companion/hooks/register.tsx` | Modification principale | Auto-open, band, avatar, vue "Now", navigation |
| `~/.claude/plugins/marketplaces/devlog/plugins/devlog-companion/types/index.d.ts` | Extension | Champ `view` dans PluginState |
| `~/.claude/plugins/marketplaces/devlog/plugins/devlog-companion/.claude-plugin/plugin.json` | Bump version | 0.1.1 → 0.2.0 |

### Capacités Mods utilisées (toutes existantes, vérifiées)

| Capacité | Utilisation |
|---|---|
| `session.start` hook | Auto-open du panneau via `$.ui.open()` |
| `tool.call` hook | Interception MCP existante (inchangée) |
| `ui.render` → `AbovePrompt` | Band toujours visible, avatar + état |
| `ui.render` → `Pane` | Navigation 3 onglets + vue "Now" |
| `$.ui.open()` / `$.ui.close()` | Auto-open + toggle commande |
| `atom()` / `read()` / `update()` | État réactif persistent |
| `$.command.register()` | Commande `/devlog-companion` conservée |
| `Svg` component (desktop) | Avatar SVG dans band et pane |
| `Button` avec `onPress` | Navigation, Inspect, Back |
| `$.process.run()` | Fetch context trace (inchangé) |

### Limitation confirmée

Le band AbovePrompt est **display-only** : il n't supporte pas les interactions click pour ouvrir le panneau. L'ouverture automatique via `session.start` est le mécanisme principal d'accessibilité. La commande `/devlog-companion` reste disponible pour le toggle manuel.

---

## Modèle d'état

### Cinq états observables

| État | Critère de dérivation | Couleur | Animation |
|------|----------------------|---------|-----------|
| `IDLE` | Aucune op en cours ; dernière op terminée depuis > 10 min (ou aucune op cette session) | Vert (`--fill-success`) | Pulsation lente de l'anneau externe (2.4s, opacity 0.35↔0.7) |
| `WORKING` | Au moins une op avec `status: 'running'` | Ambre (`--fill-warning`) | Satellites en rotation orbitale (1.6s linéaire) |
| `READY` | Dernière op `status: 'done'`, terminée depuis < 10 min | Bleu (`--fill-accent`) | Flash de l'anneau (0.8s × 2) puis retour IDLE animation |
| `ERROR` | Dernière op `status: 'failed'` | Rouge (`--fill-danger`) | Statique, satellites atténués |
| `DISCONNECTED` | Détecté via échec de `$.process.run()` lors d'un fetch trace | Muted (`--text-muted`) | Atténué, anneau en tirets |

**Remarque :** `DISCONNECTED` ne peut pas être détecté proactivement dans un modèle event-driven sans polling. Il est déclenché lorsqu'une tentative de fetch context trace échoue avec une erreur réseau. En V1, l'état `DISCONNECTED` est transitoire (revient à `IDLE` ou `ERROR` après l'échec). Le band affiche un indicateur discret, pas une alerte persistante.

### Fonction de dérivation

```typescript
type CompanionState = 'IDLE' | 'WORKING' | 'READY' | 'ERROR' | 'DISCONNECTED'
const READY_WINDOW_MS = 10 * 60 * 1000

function deriveState(ops: Op[], now: number, disconnected: boolean): CompanionState {
  if (disconnected) return 'DISCONNECTED'
  if (ops.some(o => o.status === 'running')) return 'WORKING'
  const last = ops[0]
  if (!last) return 'IDLE'
  if (last.status === 'failed') return 'ERROR'
  if (last.status === 'done' && (now - last.startedAt) < READY_WINDOW_MS) return 'READY'
  return 'IDLE'
}
```

---

## Nouveaux atomes

```typescript
// Onglet actif du panneau
const activeView = atom(
  { plugin: 'devlog-companion', key: 'view' } as const,
  'now' as 'now' | 'context' | 'history',
  { shape: 'view-1' }
)

// État de déconnexion (déclenché par échec fetch)
const disconnected = atom(
  { plugin: 'devlog-companion', key: 'disconnected' } as const,
  false as boolean,
  { shape: 'disconnected-1' }
)
```

Atomes existants conservés sans modification : `ops`, `traceState`.

---

## Avatar SVG — identité visuelle

L'avatar est un nœud de graphe abstrait : cercle central + 3 satellites + lignes de connexion + anneau externe.

**Géométrie (viewBox 20×20, origine centre 10,10) :**

| Élément | Propriétés |
|---------|-----------|
| Cercle central | `cx=10 cy=10 r=3`, rempli |
| Anneau externe | `cx=10 cy=10 r=6.5`, stroke uniquement |
| Satellite haut | `cx=10 cy=3.5 r=1.4` |
| Satellite bas-droite | `cx=16.5 cy=13 r=1.4` |
| Satellite bas-gauche | `cx=3.5 cy=13 r=1.4` |
| Connexions | 3 lignes de r=6.5 vers chaque satellite, `stroke-width=0.8` |

**Sizes :** 20×20 dans le band AbovePrompt, 28×28 dans la vue "Now" du panneau.

**Accessibilité :** `prefers-reduced-motion` — les animations CSS doivent être conditionnées par `@media (prefers-reduced-motion: no-preference)`. Sans animation, l'avatar reste lisible grâce à la couleur seule.

---

## Structure du panneau

### Navigation (3 onglets)

```
[ Now ]  [ Context ]  [ History ]
```

L'onglet actif est persisté dans `activeView`. La navigation entre onglets ne réinitialise pas `traceState`.

### Onglet "Now" — vue d'accueil

**Sections :**

1. **En-tête statut** — avatar (28×28) + libellé état + badge (IDLE/WORKING/READY/ERROR/DISCONNECTED) + horodatage relatif

2. **Carte dernière opération** (si au moins une op) :
   - Projet (slug), outil (shortName), intent (tronqué), durée
   - Fraîcheur (si trace disponible dans `traceState`)
   - Lien "Inspecter le contexte →" (switch vers onglet "Context" + charge la trace si absente)

3. **Sources sélectionnées** (si `traceState.data.sources` disponible) :
   - Liste compacte : tag type + titre tronqué
   - Comptage : "N sélectionnées / M candidates"

4. **État vide** (aucune op cette session) :
   - Message informatif : "En attente d'une opération DevLog"
   - Aperçu de la dernière op de la session précédente (depuis `ops` atom) si disponible

### Onglet "Context" — inspecteur de contexte

Contenu actuel du Context Inspector (vue détail avec tabs overview/sources/context/diagnostics), accessible depuis "Now" via "Inspecter le contexte →".

**Changements UX :**
- Supprimer le bouton "← Back" (remplacé par la navigation par onglets)
- Conserver les 4 sous-onglets internes (overview, sources, context, diagnostics)
- Si aucune trace chargée : message "Sélectionnez une opération dans History pour inspecter son contexte."

### Onglet "History" — historique des opérations

Contenu actuel de la liste d'opérations. Cliquer une op charge sa trace dans `traceState` et switche vers l'onglet "Context".

**Changements UX :**
- Supprimer la navigation "click → detail view in-place" (remplacée par switch d'onglet)
- Conserver l'affichage des statuts et durées

---

## Comportements de session

### Session start

```typescript
on('session.start', async ($, e, next) => {
  const r = await next(e)
  await $.command.register({ name: 'devlog-companion', description: 'Show or hide DevLog Companion panel' })
  // Auto-open (nouveau)
  void $.ui.open({ id: PANE, title: 'DevLog Companion' })
  return r
})
```

### Session end / clear

Lors d'un `/clear` ou d'un `session.end` avec `reason === 'resume'` :
- Réinitialiser `activeView` → `'now'`
- Conserver `ops` (l'historique reste pertinent entre sessions pour la vue "Now")
- Réouvrir le panneau via `$.ui.open()` si `reason === 'clear'`

---

## Expérience cible validée

### Scénario 1 — Démarrage de session
- Claude Desktop s'ouvre, l'onglet Code est actif
- Le panneau DevLog Companion s'ouvre automatiquement sur l'onglet "Now"
- Le band AbovePrompt affiche l'avatar + "DevLog · disponible"
- Le panneau affiche l'état IDLE avec message d'attente ou dernière op de session précédente

### Scénario 2 — Opération en cours
- Claude interroge DevLog (`devlog_story_agent` ou `get_engineering_context`)
- Le band passe en état WORKING (satellites en rotation, couleur ambre)
- La vue "Now" affiche "Analyse en cours..." avec le nom de l'outil et l'intent

### Scénario 3 — Résultat disponible
- L'opération se termine avec succès
- Le band passe en état READY (flash bleu × 2)
- La vue "Now" affiche la carte de l'opération : projet, story, durée
- Le bouton "Inspecter le contexte →" charge la trace depuis le backend

### Scénario 4 — Inspection du contexte
- L'utilisateur clique "Inspecter le contexte →" dans "Now"
- Le panneau bascule sur l'onglet "Context" et charge la trace
- Pendant le chargement : spinner + "Chargement du contexte…"
- Après : sources, comptabilité, fraîcheur, diagnostics (inchangé)

### Scénario 5 — Erreur d'opération
- Une op DevLog échoue
- Le band passe en état ERROR (rouge statique)
- La vue "Now" affiche la carte en état erreur avec le code d'échec si disponible dans `traceState`

### Scénario 6 — Ergonomie
- Le Companion ne gêne pas la saisie, ne masque pas de contenu Claude Code
- L'utilisateur peut fermer le panneau via `/devlog-companion` ou le bouton × du panneau
- Le band reste visible même panneau fermé

---

## Critères d'acceptation

**AC1** — À l'ouverture d'une nouvelle session Claude Code, le panneau DevLog Companion s'ouvre automatiquement sans commande de l'utilisateur.

**AC2** — Le band AbovePrompt est visible dès l'ouverture de session, même en l'absence de toute opération DevLog.

**AC3** — Le band affiche l'avatar géométrique SVG dans au moins deux tailles de fenêtre (narrow / wide).

**AC4** — L'avatar reflète l'état dérivé de la liste des opérations : couleur et animation distinctes pour IDLE, WORKING, READY, ERROR.

**AC5** — L'onglet "Now" est l'onglet actif à l'ouverture du panneau.

**AC6** — L'onglet "Now" affiche un état vide informatif lorsqu'aucune opération n'a eu lieu cette session.

**AC7** — L'onglet "Now" affiche la carte de la dernière opération terminée (projet, outil, durée, horodatage relatif).

**AC8** — Le bouton "Inspecter le contexte →" dans "Now" charge la trace et bascule sur l'onglet "Context".

**AC9** — L'onglet "Context" affiche le Context Inspector existant (overview, sources, context, diagnostics) sans régression fonctionnelle.

**AC10** — L'onglet "History" affiche la liste des opérations existante sans régression fonctionnelle.

**AC11** — Cliquer une opération dans "History" charge sa trace et bascule sur l'onglet "Context".

**AC12** — La commande `/devlog-companion` conserve son comportement de toggle (ouvrir / fermer le panneau).

**AC13** — Les animations respectent `prefers-reduced-motion` : désactivées si `prefers-reduced-motion: reduce`.

**AC14** — Le band et le panneau fonctionnent correctement sur la surface `desktop` (Svg component disponible) et sur la surface `terminal` (fallback text/Ink).

**AC15** — L'état de l'onglet actif (`activeView`) est persisté via atom et survit à une fermeture/réouverture du panneau.

**AC16** — Aucune régression sur les données de Context Inspector existantes (sources, accounting, freshness, diagnostics).

---

## Tests et validation

### Protocole de validation manuelle (Claude Desktop)

Les mods Claude ne disposent pas de framework de test automatisé. La validation se fait par test manuel dans Claude Desktop sur une session réelle.

**T1 — Démarrage**
Ouvrir Claude Desktop → onglet Code → vérifier auto-open du panneau sur "Now".

**T2 — Band état IDLE**
Aucune op cette session → band visible avec avatar vert + "DevLog · disponible".

**T3 — Band état WORKING**
Déclencher `devlog_story_agent` ou `get_engineering_context` → band passe en WORKING (ambre, orbite).

**T4 — Band état READY**
Op terminée avec succès → band passe en READY (flash bleu), retour IDLE après 10 min.

**T5 — Vue "Now" après op**
Carte dernière op visible : projet, outil, intent (si disponible), durée.

**T6 — Inspect depuis "Now"**
Cliquer "Inspecter le contexte →" → switch sur onglet "Context" → trace chargée.

**T7 — Context Inspector sans régression**
Sources, overview, diagnostics identiques à v0.1.1.

**T8 — History sans régression**
Liste des ops, clic → switch Context.

**T9 — Toggle commande**
`/devlog-companion` ferme le panneau → re-exécuter → ouvre le panneau.

**T10 — Prefers-reduced-motion**
Simuler `prefers-reduced-motion: reduce` (DevTools) → avatar statique, pas d'animation.

**T11 — Surface terminal**
Vérifier le fallback Ink (sans Svg component) : band text + pane text.

---

## Contraintes techniques

| Contrainte | Raison |
|------------|--------|
| Pas de `setInterval` ni polling | Hooks Claude Mods sont event-driven uniquement |
| Pas de `requestAnimationFrame` libre | Animations via CSS `@keyframes` / SVG `<animate>` uniquement |
| SVG limité à des primitives | Pas de fonts externes, pas de `<image>`, pas de `<foreignObject>` |
| `$.process.run()` pour auth/REST | Mécanisme existant conservé pour le fetch trace |
| Band display-only (pas de click handler) | Limitation confirmée de Claude Mods AbovePrompt |
| Plugin installé hors repo | Fichiers dans `~/.claude/plugins/marketplaces/devlog/` — synchronisation manuelle nécessaire avec le marketplace |

---

## Dépendances

| Dépendance | Type | Disponibilité |
|------------|------|---------------|
| Plugin `devlog-companion@devlog` v0.1.1 | Existante — à modifier | Installé ✓ |
| API REST context-trace | Existante — inchangée | Story 0192 ✓ |
| Atom API (`atom`, `read`, `update`) | Existante | Vérifiée ✓ |
| `Svg` component (desktop) | Existante | Vérifiée ✓ |
| `$.ui.open()` dans `session.start` | Capacité vérifiée | context-optimizer ✓ |
| `AbovePrompt` unconditional render | Capacité vérifiée | Suppression du guard ✓ |

---

## Hors périmètre V1

- Clic sur le band AbovePrompt pour ouvrir/fermer le panneau (limitation Mods)
- Polling automatique de l'état du backend (event-driven uniquement)
- State `DISCONNECTED` proactif (détecté uniquement sur échec de fetch)
- Chargement automatique de la trace à la complétion d'une op (lazy only)
- Vue "DevLog Now" enrichie avec comptabilité et budget tokens (différé V2)
- Micro-animations sur les transitions de vue (différé V2)
- Publication sur le marketplace devlog (synchronisation manuelle V1)
